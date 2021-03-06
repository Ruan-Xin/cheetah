package raft.core.imp;

import models.CheetahAddress;
import org.apache.log4j.Logger;
import raft.core.RaftConsensusService;
import raft.core.RaftCore;
import raft.core.server.RaftServer;
import raft.core.server.ServerNode;
import raft.protocol.RaftLog;
import raft.protocol.RaftNode;
import raft.protocol.request.*;
import raft.protocol.response.*;
import raft.utils.RaftUtils;
import utils.ParseUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author ruanxin
 * @create 2018-02-08
 * @desc
 */
public class RaftConsensusServiceImpl implements RaftConsensusService {

    private Logger logger = Logger.getLogger(RaftConsensusServiceImpl.class);

    private ReentrantLock syncLock = new ReentrantLock(true);
    //true:正在同步 , false:未同步或同步完毕
    private AtomicBoolean sync;
    private RaftNode raftNode;
    private RaftCore raftCore;

    public RaftConsensusServiceImpl (RaftNode raftNode, RaftCore raftCore) {
        this.sync = new AtomicBoolean(false);
        this.raftNode = raftNode;
        this.raftCore = raftCore;
    }

    public AddResponse appendEntries(AddRequest request) {
        raftNode.getLock().lock();
        try {
            RaftServer raftServer = raftNode.getRaftServer();
            AddResponse response = new AddResponse(raftServer.getServerId(),
                    raftNode.getCurrentTerm(),
                    false);
            if (request == null) {
                logger.debug("appendEntries, but request=null");
                return response;
            }
            logger.info("begin appendEntries from server=" + request.getServerId() +
            " ,local server=" + raftNode.getRaftServer().getServerId());
            if (request.getTerm() < raftNode.getCurrentTerm()) {
                logger.info("remote server term=" + request.getTerm() +
                " ,local server term=" + raftNode.getCurrentTerm() +
                " ,so return!");
                return response;
            }
            if (request.getTerm() > raftNode.getCurrentTerm()) {
                logger.info("remote server term=" + request.getTerm() +
                " ,local server term=" + raftNode.getCurrentTerm() +
                " ,so update more!");
                raftCore.updateMore(request.getTerm());
            }

            //update server list
            raftCore.setServerList(request.getServerList());

            if (raftNode.getLeaderId() == 0) {
                raftNode.setLeaderId(request.getLeaderId());
                logger.info("new leaderId:" + raftNode.getLeaderId());
            }
            if (raftNode.getLeaderId() != request.getLeaderId()) {
                logger.warn("another server declare it is leader:" + raftNode.getLeaderId() +
                " at term:" + raftNode.getCurrentTerm() + " ,now real leader is " + request.getLeaderId() +
                " and the term will plus one!");
                raftNode.setLeaderId(request.getLeaderId());
                raftCore.updateMore(request.getTerm() + 1);
                response.setSuccess(false);
                response.setTerm(request.getTerm() + 1);
                response.setServerId(raftServer.getServerId());
                return response;
            }

            if (request.getPrevLogIndex() > raftNode.getRaftLog().getLastLogIndex()) {
                //this node need sync data
                logger.info("Refuse,request's log index:" + request.getPrevLogIndex() +
                ",local server's log index:" + raftNode.getRaftLog().getLastLogIndex() +
                " this server need sync log!");
                response.setLastLogIndex(raftNode.getRaftLog().getLastLogIndex());
                raftCore.resetElectionTimer();
                return response;
            }
            if (request.getPrevLogTerm() != raftNode.getRaftLog().getLogEntryTerm(request.getPrevLogTerm())) {
                logger.warn("LogEntry term is wrong, leader prevLogIndex=" + request.getPrevLogIndex() +
                ",prevLogTerm=" + request.getPrevLogTerm() + " ,but local server prevLogIndex=" + request.getPrevLogIndex() +
                ",prevLogTerm=" + raftNode.getRaftLog().getLogEntryTerm(request.getPrevLogIndex()));
                //找到最初不一致的地方，然后覆盖掉
                response.setLastLogIndex(request.getPrevLogIndex() - 1);
                return response;
            }
            if (request.getLogEntries().size() == 0) {
                logger.info("heart beat request at term:" + request.getTerm() +
                " ,local host term:" + raftNode.getCurrentTerm() +
                " ,local serverId=" + raftServer.getServerId());
                response.setTerm(raftNode.getCurrentTerm());
                response.setServerId(raftServer.getServerId());
                response.setSuccess(true);
                response.setLastLogIndex(raftNode.getRaftLog().getLastLogIndex());
                //sync
                applyLogOnStateMachine(request);
                //reset election
                raftCore.resetElectionTimer();
                Map<Long, String> localServerList = raftCore.getServerList();
                Map<Long, ServerNode> localServerNode = raftCore.getServerNodeCache();
                RaftUtils.syncServerNodeAndServerList(localServerNode, localServerList,
                        raftServer.getServerId());
                return response;
            }

            response.setSuccess(true);
            List<RaftLog.LogEntry> entries = new ArrayList<>();

            for (RaftLog.LogEntry entry : request.getLogEntries()) {
                long index = entry.getIndex();
                if (raftNode.getRaftLog().getLastLogIndex() > index) {
                    if (raftNode.getRaftLog().getLogEntryTerm(index) == entry.getTerm()) {
                        continue;
                    }
                    //truncate sync leader and follower
                    long lastIndexKept = index - 1;
                    raftNode.getRaftLog().truncateSuffix(lastIndexKept);
                }
                entries.add(entry);
            }
            raftNode.getRaftLog().append(entries);
            response.setLastLogIndex(raftNode.getRaftLog().getLastLogIndex());

            applyLogOnStateMachine(request);
            logger.info("Append entries request from server:" + request.getServerId() + " in term:" +
            request.getTerm() + " (my term is " + raftNode.getCurrentTerm() + ") " +
            " ,entryCount=" + request.getLogEntries().size() + " result:" + response.isSuccess());
            return response;
        } finally {
            raftNode.getLock().unlock();
        }
    }

    @Override
    public GetLeaderResponse getLeader(GetLeaderRequest request) {
        logger.info("local serverId=" + raftNode.getRaftServer().getServerId() +
        " ,remote host=" + request.getRemoteHost());
        //get leader ip
        CheetahAddress cheetahAddress = getLeaderAddress();

        GetLeaderResponse response = new GetLeaderResponse(raftNode.getRaftServer().getServerId(),
                raftNode.getLeaderId() ,cheetahAddress);
        return response;
    }

    @Override
    public GetServerListResponse getServerList(GetServerListRequest request) {
        logger.info("local serverId=" + raftNode.getRaftServer().getServerId() +
        " ,remote host=" + request.getRemoteHost());
        //get leader ip
        CheetahAddress cheetahAddress = getLeaderAddress();
        GetServerListResponse response = new GetServerListResponse(raftCore.getServerList(),
                raftNode.getRaftServer().getServerId(), cheetahAddress);
        return response;
    }

    @Override
    public GetValueResponse getValue(GetValueRequest request) {
        logger.info("local serverId=" + raftNode.getRaftServer().getServerId() +
        " ,remote host=" + request.getRemoteHost());

        //get leader ip
        CheetahAddress cheetahAddress = getLeaderAddress();
        GetValueResponse response = new GetValueResponse(raftNode.getRaftServer().getServerId(), cheetahAddress);
        if (raftNode.getLeaderId() <= 0) {
            //have not election leader
            logger.info("there is no leader, local serverId=" + raftNode.getRaftServer().getServerId());
            response.setValue(null);
        } else if (raftNode.getRaftServer().getServerState() != RaftServer.NodeState.LEADER) {
            //redirect to leader
            logger.info("getValue redirect to leader=" + raftNode.getLeaderId());
            response = raftCore.getRedirectLeader(request);
        } else {
            byte[] result = raftCore.getValue(request.getKey());
            if (result == null || result.length == 0) {
                response.setValue(null);
            } else {
                String resStr = new String(result);
                response.setValue(resStr);
            }
        }
        return response;
    }
    @Override
    public RegisterServerResponse registerServer(RegisterServerRequest request) {
        logger.info("local serverId=" + raftNode.getRaftServer().getServerId());

        RegisterServerResponse response;
        if (raftNode.getLeaderId() <= 0) {
            //have not election leader
            logger.info("there is no leader, local serverId=" + raftNode.getRaftServer().getServerId());
            response = new RegisterServerResponse(raftNode.getRaftServer().getServerId());
            response.setServerList(raftCore.getServerList());
        } else if (raftNode.getRaftServer().getServerState() !=
                RaftServer.NodeState.LEADER) {
            //redirect to leader
            logger.info("registerServer redirect to leader=" + raftNode.getLeaderId());
            response = raftCore.registerRedirectLeader(request);
        } else {
            logger.info("new server node serverId=" +
                    ParseUtils.generateServerId(request.getNewHost(), request.getNewPort()) +
                    " register!");
            response = raftCore.newNodeRegister(request);
        }
        return response;
    }

    @Override
    public SetKVResponse setKV(SetKVRequest request) {
        logger.info("local serverId=" + raftNode.getRaftServer().getServerId());

        CheetahAddress cheetahAddress = getLeaderAddress();
        SetKVResponse response = new SetKVResponse(raftNode.getRaftServer().getServerId(), cheetahAddress);
        if (raftNode.getLeaderId() <= 0) {
            //have not election leader
            logger.info("there is no leader, local serverId=" + raftNode.getRaftServer().getServerId());
            response.setRespMessage("set fail!there is no leader!");
        } else if (raftNode.getRaftServer().getServerState() != RaftServer.NodeState.LEADER) {
            //redirect to leader
            logger.info("setKV redirect to leader=" + raftNode.getLeaderId());
            response = raftCore.setRedirectLeader(request);
        } else {
            boolean result = raftCore.logReplication(request.getSetCommand().getBytes());
            if (result) {
                response.setRespMessage("successful!");
            } else {
                response.setRespMessage("set fail!");
            }
        }
        return response;
    }

    private CheetahAddress getLeaderAddress() {
        String address = raftCore.getServerList().get(raftNode.getLeaderId());
        CheetahAddress cheetahAddress = ParseUtils.parseAddress(address);
        return cheetahAddress;
    }

    @Override
    public SyncLogEntryResponse syncLogEntry(SyncLogEntryRequest request) {
        SyncLogEntryResponse response = new SyncLogEntryResponse(raftNode.getRaftServer().getServerId(),
                RaftCore.SYNC_FAIL);
        syncLock.lock();
        try {
            if (sync.get()) {
                //数据正在同步
                logger.info("serverId=" + raftNode.getRaftServer().getServerId() +
                " log entry sync now!");
                response.setSyncStatus(RaftCore.SYNC_ING);
                return response;
            }
            //数据需要同步
            sync.set(true);
        } finally {
            syncLock.unlock();
        }
        try {
            logger.info("sync log entries info begin from serverId=" + request.getServerId());
            List<RaftLog.LogEntry> entries = request.getLogEntries();
            RaftLog.LogEntry firstNeedSyncEntry = entries.get(0);
            if (firstNeedSyncEntry.getIndex() != raftNode.getRaftLog().getLastLogIndex() + 1) {
                logger.warn("syncLogEntry sync entry can't match!");
                response.setLastLogIndex(raftNode.getRaftLog().getLastLogIndex());
                response.setSyncStatus(RaftCore.SYNC_FAIL);
                sync.set(false);
                return response;
            }
            raftNode.getRaftLog().append(request.getLogEntries());
            response.setLastLogIndex(raftNode.getRaftLog().getLastLogIndex());
            response.setSyncStatus(RaftCore.SYNC_SUCC);
            syncLogApplyLogOnStateMachine(request);
            logger.info("serverId=" + raftNode.getRaftServer().getServerId() +
                    " syncLogEntry successful!");
            sync.set(false);
            return response;
        } catch (Exception e) {
            logger.error("serverId=" + raftNode.getRaftServer().getServerId() +
            " sync log entry failed!", e);
            response.setSyncStatus(RaftCore.SYNC_FAIL);
            sync.set(false);
            return response;
        } finally {
            sync.set(false);
        }

    }

    @Override
    public TestGetValueResponse getLocalData(TestGetValueRequest request) {
        String key = request.getKey();
        byte[] data = raftNode.getStateMachine().get(key);
        if (data == null) {
            TestGetValueResponse response = new TestGetValueResponse(raftNode.getRaftServer().getServerId(),
                    null);
            return response;
        }
        String value = new String(data);
        TestGetValueResponse response = new TestGetValueResponse(raftNode.getRaftServer().getServerId(),
                value);
        return response;
    }


    /**
     * sync log entry (apply on state machine)
     */
    private void syncLogApplyLogOnStateMachine (SyncLogEntryRequest request) {
        logger.info("sync log entry begin to apply log on state machine, local server=" + raftNode.getRaftServer().getServerId());
        long newCommitIndex = Math.min(request.getLeaderCommit(),
                raftNode.getRaftLog().getCommitIndex());
        raftNode.getRaftLog().setCommitIndex(newCommitIndex);
        //apply on state machine
        RaftUtils.applyStateMachine(raftNode);
    }

    /**
     * append log entry (apply on state machine)
     */
    private void applyLogOnStateMachine(AddRequest request) {
        logger.info("begin to apply log on state machine, local server=" + raftNode.getRaftServer().getServerId());
        //can't longer than leader
        long newCommitIndex = Math.min(request.getLeaderCommit(),
                request.getPrevLogIndex() + request.getLogEntries().size());
        raftNode.getRaftLog().setCommitIndex(newCommitIndex);
        //apply on state machine
        RaftUtils.applyStateMachine(raftNode);
    }
}

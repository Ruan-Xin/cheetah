package raft.protocol.request;

import raft.model.BaseRequest;

import java.io.Serializable;

/**
 * @author ruanxin
 * @create 2018-02-08
 * @desc 请求投票的RPC
 */
public class VotedRequest extends BaseRequest implements Serializable {
    //机器id
    private long serverId;
    //候选人的任期号
    private int term;
    //请求选票的候选人的 Id
    private long candidateId;
    //候选人的最后日志条目的索引值
    private long lastLogIndex;
    //候选人最后日志条目的任期号
    private int lastLogTerm;

    public VotedRequest (int term, long candidateId, long lastLogIndex, int lastLogTerm) {
        this.term = term;
        this.candidateId = candidateId;
        this.lastLogIndex = lastLogIndex;
        this.lastLogTerm = lastLogTerm;
    }

    public void setAddress(String localHost, int localPort,
                           String remoteHost, int remotePort, long serverId) {
        this.localHost = localHost;
        this.localPort = localPort;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.serverId = serverId;
    }

    public int getTerm() {
        return term;
    }

    public void setTerm(int term) {
        this.term = term;
    }

    public long getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(long candidateId) {
        this.candidateId = candidateId;
    }

    public long getLastLogIndex() {
        return lastLogIndex;
    }

    public void setLastLogIndex(long lastLogIndex) {
        this.lastLogIndex = lastLogIndex;
    }

    public int getLastLogTerm() {
        return lastLogTerm;
    }

    public void setLastLogTerm(int lastLogTerm) {
        this.lastLogTerm = lastLogTerm;
    }
    public long getServerId() {
        return serverId;
    }

    public void setServerId(long serverId) {
        this.serverId = serverId;
    }
}

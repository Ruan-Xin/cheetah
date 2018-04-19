package raft.core;

import raft.protocol.request.CommandExecuteRequest;
import raft.protocol.request.GetLeaderRequest;
import raft.protocol.request.GetServerListRequest;
import raft.protocol.response.CommandExecuteResponse;
import raft.protocol.response.GetLeaderResponse;
import raft.protocol.response.GetServerListResponse;

/**
 * @author ruanxin
 * @create 2018-04-19
 * @desc raft client
 */
public interface RaftClientService {

    public GetLeaderResponse getLeader(GetLeaderRequest request);

    public GetServerListResponse getServerList(GetServerListRequest request);

    public CommandExecuteResponse commandExec (CommandExecuteRequest request);
}

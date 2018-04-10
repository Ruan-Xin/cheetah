package raft.core;

import raft.protocol.VotedRequest;
import raft.protocol.VotedResponse;

/**
 * @author ruanxin
 * @create 2018-04-10
 * @desc 一致性算法 async
 */
public interface RaftAsyncConsensusService {
    /**
     * leader election
     * @param request
     * @return
     */
    public VotedResponse leaderElection (VotedRequest request);
}

package raft.constants;

/**
 * @author ruanxin
 * @create 2018-03-01
 * @desc
 */
public class RaftOptions {

    //election timer random(1500, 2000)
    private int electionTimeOutMilliSec = 1500;
    private int electionTimeOutRandomMilliSec = 500;
    //heart beat 500
    private int heartbeatPeriodMilliseconds = 500;

    public int getElectionTimeOutMilliSec() {
        return electionTimeOutMilliSec;
    }

    public void setElectionTimeOutMilliSec(int electionTimeOutMilliSec) {
        this.electionTimeOutMilliSec = electionTimeOutMilliSec;
    }

    public int getElectionTimeOutRandomMilliSec() {
        return electionTimeOutRandomMilliSec;
    }

    public void setElectionTimeOutRandomMilliSec(int electionTimeOutRandomMilliSec) {
        this.electionTimeOutRandomMilliSec = electionTimeOutRandomMilliSec;
    }

    public int getHeartbeatPeriodMilliseconds() {
        return heartbeatPeriodMilliseconds;
    }

    public void setHeartbeatPeriodMilliseconds(int heartbeatPeriodMilliseconds) {
        this.heartbeatPeriodMilliseconds = heartbeatPeriodMilliseconds;
    }
}

package raft.core;

/**
 * @author ruanxin
 * @create 2018-04-23
 * @desc parse command
 */
public interface ParserGateWayService {

    /**
     * parse command
     */
    public String parse(String command);
}

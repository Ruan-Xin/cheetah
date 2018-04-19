package rpc.wrapper.connector;

import constants.Globle;
import rpc.client.AbstractClientRemoteExecutor;
import rpc.client.SimpleClientRemoteProxy;
import rpc.client.SyncClientRemoteExecutor;
import rpc.demo.rpc.provider.HelloRpcService;
import rpc.wrapper.RpcConnectorWrapper;

/**
 * @author ruanxin
 * @create 2018-04-16
 * @desc
 */
public class RpcServerSyncConnector extends RpcConnectorWrapper {

    public RpcServerSyncConnector(String host, int port) {
        super(host, port);
    }

    @Override
    public AbstractClientRemoteExecutor getClientRemoteExecutor() {
        SyncClientRemoteExecutor remoteExecutor = new SyncClientRemoteExecutor(connector);
        return remoteExecutor;
    }
}

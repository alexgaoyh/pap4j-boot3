package cn.net.pap.milvus;

import io.milvus.client.MilvusClient;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.RetryParam;

public class MilvusUtilss {

    /**
     * 创建链接
     *
     * @param host          192.168.1.115
     * @param port          19530
     * @param username      root
     * @param password      ""
     * @param maxRetryTimes 3
     * @return
     */
    public MilvusClient milvusClient(String host, Integer port, String username, String password, Integer maxRetryTimes) {
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .withAuthorization(username, password)
                .build();
        RetryParam retryParam = RetryParam.newBuilder()
                .withMaxRetryTimes(maxRetryTimes)
                .build();
        MilvusClient milvusClient = new MilvusServiceClient(connectParam).withRetry(retryParam);
        return milvusClient;
    }

}

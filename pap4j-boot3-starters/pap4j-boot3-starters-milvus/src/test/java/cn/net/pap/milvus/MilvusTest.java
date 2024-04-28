package cn.net.pap.milvus;

import io.milvus.client.MilvusClient;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.CheckHealthResponse;
import io.milvus.grpc.DataType;
import io.milvus.grpc.DescribeCollectionResponse;
import io.milvus.param.*;
import io.milvus.param.collection.*;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.DescCollResponseWrapper;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MilvusTest {

    private static final String COLLECTION_NAME = "USER";

    public MilvusClient milvusClient() {
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost("192.168.1.115")
                .withPort(19530)
                .withAuthorization("root", "")
                .build();
        RetryParam retryParam = RetryParam.newBuilder()
                .withMaxRetryTimes(3)
                .build();
        MilvusClient milvusClient = new MilvusServiceClient(connectParam).withRetry(retryParam);
        return milvusClient;
    }

    @Test
    public void test1_connect() {
        MilvusClient milvusClient = milvusClient();
        R<CheckHealthResponse> checkHealthResponseR = milvusClient.checkHealth();
        assertTrue(true == checkHealthResponseR.getData().getIsHealthy());
        milvusClient.close();
    }

    @Test
    public void test2_hasCollectionThenDescribeThenDrop() {
        MilvusClient milvusClient = milvusClient();
        R<Boolean> hasResponse = milvusClient.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .build());
        assertTrue(0 == hasResponse.getStatus());
        if (hasResponse.getData() == true) {
            R<DescribeCollectionResponse> describeResponse = milvusClient.describeCollection(DescribeCollectionParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .build());
            DescCollResponseWrapper describeWrapper = new DescCollResponseWrapper(describeResponse.getData());
            assertTrue(describeWrapper.getCollectionName().equals("USER"));


            R<RpcStatus> dropResponse = milvusClient.dropCollection(DropCollectionParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .build());
            assertTrue(0 == dropResponse.getStatus());
        }
        milvusClient.close();
    }

    @Test
    public void test3_createCollection() {
        MilvusClient milvusClient = milvusClient();

        FieldType fieldType1 = FieldType.newBuilder()
                .withName("id")
                .withDescription("user id")
                .withDataType(DataType.Int64)
                .withPrimaryKey(true)
                .withAutoID(true)
                .build();

        FieldType fieldType2 = FieldType.newBuilder()
                .withName("vector")
                .withDescription("vector embedding")
                .withDataType(DataType.FloatVector)
                .withDimension(64)
                .build();

        FieldType fieldType3 = FieldType.newBuilder()
                .withName("age")
                .withDescription("age")
                .withDataType(DataType.Int8)
                .build();

        CreateCollectionParam createCollectionReq = CreateCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withDescription("user info")
                .withShardsNum(2)
                .withEnableDynamicField(false)
                .addFieldType(fieldType1)
                .addFieldType(fieldType2)
                .addFieldType(fieldType3)
                .build();
        R<RpcStatus> response = milvusClient.withTimeout(3000, TimeUnit.MILLISECONDS)
                .createCollection(createCollectionReq);
        assertTrue(response.getStatus() == 0);
        milvusClient.close();
    }

    @Test
    public void test4_createIndex() {
        MilvusClient milvusClient = milvusClient();

        R<RpcStatus> vectorResponse = milvusClient.createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFieldName("vector")
                .withIndexName("vector_index")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.L2)
                .withExtraParam("{\"nlist\":128}")
                .withSyncMode(Boolean.TRUE)
                .build());
        assertTrue(vectorResponse.getStatus() == 0);


        R<RpcStatus> ageResponse = milvusClient.createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFieldName("age")
                .withIndexType(IndexType.STL_SORT)
                .withSyncMode(Boolean.TRUE)
                .build());
        assertTrue(ageResponse.getStatus() == 0);
        milvusClient.close();
    }

    @Test
    public void test5_loadCollectionThenRelease() {
        MilvusClient milvusClient = milvusClient();
        R<RpcStatus> loadResponse = milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .build());
        assertTrue(0 == loadResponse.getStatus());
        if (loadResponse.getStatus() == 0) {
            R<RpcStatus> releaseResponse = milvusClient.releaseCollection(ReleaseCollectionParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .build());
            assertTrue(0 == releaseResponse.getStatus());
        }
        milvusClient.close();
    }
}

package cn.net.pap.milvus;

import com.google.gson.JsonObject;
import io.milvus.client.MilvusClient;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.*;
import io.milvus.param.*;
import io.milvus.param.collection.*;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.param.partition.CreatePartitionParam;
import io.milvus.response.DescCollResponseWrapper;
import io.milvus.response.SearchResultsWrapper;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MilvusTest {

    private static final String COLLECTION_NAME = "USER";

    private static final String PARTITION_NAME = "alexgaoyh";

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
                .withDimension(16000)
                .build();

        FieldType fieldType3 = FieldType.newBuilder()
                .withName("age")
                .withDescription("age")
                .withDataType(DataType.Int8)
                .build();

        FieldType fieldType4 = FieldType.newBuilder()
                .withName("name")
                .withDescription("name")
                .withDataType(DataType.VarChar)
                .withMaxLength(1000)
                .build();

        CreateCollectionParam createCollectionReq = CreateCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withDescription("user info")
                .withShardsNum(2)
                .withEnableDynamicField(false)
                .addFieldType(fieldType1)
                .addFieldType(fieldType2)
                .addFieldType(fieldType3)
                .addFieldType(fieldType4)
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

    @Test
    public void test6_partitionCreate() {
        MilvusClient milvusClient = milvusClient();
        R<RpcStatus> createPartitionResponse = milvusClient.createPartition(CreatePartitionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withPartitionName(PARTITION_NAME)
                .build());

        assertTrue(0 == createPartitionResponse.getStatus());

        milvusClient.close();
    }

    @Test
    public void test7_insertRows() throws Exception {
        MilvusClient milvusClient = milvusClient();
        R<RpcStatus> loadResponse = milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .build());
        assertTrue(0 == loadResponse.getStatus());
        if (loadResponse.getStatus() == 0) {
            List<JsonObject> insertRowsList = insertRows();

            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withPartitionName(PARTITION_NAME)
                    .withRows(insertRowsList)
                    .build();

            R<MutationResult> insertResponse = milvusClient.insert(insertParam);

            assertTrue(0 == insertResponse.getStatus());

        }
        milvusClient.close();
    }

    @Test
    public void test7_search() throws Exception {
        Thread.sleep(1000);
        MilvusClient milvusClient = milvusClient();
        R<RpcStatus> loadResponse = milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .build());
        assertTrue(0 == loadResponse.getStatus());
        if (loadResponse.getStatus() == 0) {

            List<String> outFields = Collections.singletonList("name");
            List<List<Float>> vectors = new ArrayList<>();
            List<Float> vector = Taylor_Swift0_4550_Vector();
            vectors.add(vector);

            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withMetricType(MetricType.L2)
                    .withOutFields(outFields)
                    .withTopK(10)
                    .withVectors(vectors)
                    .withVectorFieldName("vector")
                    .withConsistencyLevel(ConsistencyLevelEnum.EVENTUALLY)
                    .build();

            R<SearchResults> response = milvusClient.search(searchParam);
            SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
            for (int i = 0; i < vectors.size(); ++i) {
                System.out.println("Search result of No." + i);
                List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(i);
                System.out.println(scores);
                System.out.println("Output field data for No." + i);
                System.out.println(wrapper.getFieldData("name", i));
            }

        }
        milvusClient.close();
    }

    /**
     * using PinsFaceRecognitionVectorTest.java to gene 105_classes_pins_dataset_vector.json file
     * @return
     */
    private List<com.google.gson.JsonObject> insertRows() throws Exception {
        List<com.google.gson.JsonObject> rowsData = new ArrayList<>();
        try {
            String basePath = "C:\\Users\\86181\\Desktop";
            basePath = basePath + File.separator + "vector";
            Stream<Path> topDirStream = Files.list(Paths.get(basePath));
            List<Path> topDirList = topDirStream.collect(Collectors.toList());
            for(Path path : topDirList) {
                String content = new String(java.nio.file.Files.readAllBytes(path));
                com.google.gson.JsonParser parser = new com.google.gson.JsonParser();
                com.google.gson.JsonArray jsonArray = parser.parse(content).getAsJsonArray();

                for (int i = 0; i < jsonArray.size(); i++) {
                    com.google.gson.JsonObject row = new com.google.gson.JsonObject();
                    row.addProperty("age", 35);

                    com.google.gson.JsonObject item = jsonArray.get(i).getAsJsonObject();
                    row.addProperty("name", item.get("picName").getAsString());

                    com.google.gson.JsonArray vectorArray = item.get("vector").getAsJsonArray();
                    com.google.gson.JsonArray vectorFloatArray = new com.google.gson.JsonArray();

                    for (com.google.gson.JsonElement element : vectorArray) {
                        float value = new BigDecimal(element.getAsString()).floatValue();
                        vectorFloatArray.add(value);
                    }
                    row.add("vector", vectorFloatArray);

                    if (vectorFloatArray.size() == 16000) {
                        rowsData.add(row);
                        if (i > 5) {
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rowsData;
    }

    private List<Float> Taylor_Swift0_4550_Vector() throws Exception {
        List<Float> floats = new ArrayList<>();
        try {
            String basePath = "C:\\Users\\86181\\Desktop";
            String content = new String(java.nio.file.Files.readAllBytes(Paths.get(basePath + File.separator + "Taylor_Swift0_4550_Vector.txt")));
            for (int i = 0; i < content.split(",").length; i++) {
                floats.add(Float.parseFloat(content.split(",")[i] + ""));
            }

        } catch (Exception e) {

        }
        return floats;
    }
}

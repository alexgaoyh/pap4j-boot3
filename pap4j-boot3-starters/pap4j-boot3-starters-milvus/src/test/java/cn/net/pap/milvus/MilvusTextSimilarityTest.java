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
import io.milvus.response.SearchResultsWrapper;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MilvusTextSimilarityTest {

    private static final Logger log = LoggerFactory.getLogger(MilvusTextSimilarityTest.class);

    private static final String COLLECTION_NAME = "TEXT";

    private static final String PARTITION_NAME = "alexgaoyh";

    public MilvusClient milvusClient() {
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost("192.168.1.77")
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
    public void insert() throws Exception {
        try {
            MilvusClient milvusClient = milvusClient();

            // step
            R<RpcStatus> dropResponse = milvusClient.dropCollection(DropCollectionParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .build());
            assertTrue(0 == dropResponse.getStatus());


            // step
            FieldType fieldType1 = FieldType.newBuilder()
                    .withName("name")
                    .withDescription("name")
                    .withDataType(DataType.VarChar)
                    .withPrimaryKey(true)
                    .withMaxLength(1000)
                    .build();
            FieldType fieldType2 = FieldType.newBuilder()
                    .withName("vector")
                    .withDescription("vector embedding")
                    .withDataType(DataType.FloatVector)
                    .withDimension(10000)
                    .build();
            CreateCollectionParam createCollectionReq = CreateCollectionParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withDescription("text info")
                    .withShardsNum(2)
                    .withEnableDynamicField(false)
                    .addFieldType(fieldType1)
                    .addFieldType(fieldType2)
                    .build();
            R<RpcStatus> response = milvusClient.withTimeout(3000, TimeUnit.MILLISECONDS)
                    .createCollection(createCollectionReq);
            assertTrue(response.getStatus() == 0);


            // step
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

            // step
            R<RpcStatus> createPartitionResponse = milvusClient.createPartition(CreatePartitionParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withPartitionName(PARTITION_NAME)
                    .build());
            assertTrue(0 == createPartitionResponse.getStatus());


            // step
            R<RpcStatus> loadResponse = milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .build());
            assertTrue(0 == loadResponse.getStatus());


            // step
            List<JsonObject> insertRowsList = insertRows();
            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withPartitionName(PARTITION_NAME)
                    .withRows(insertRowsList)
                    .build();
            R<MutationResult> insertResponse = milvusClient.insert(insertParam);
            assertTrue(0 == insertResponse.getStatus());

            milvusClient.close();
        } catch (Exception e) {
            if(e instanceof java.net.ConnectException) {
                log.warn("{}", e);
            } else {
                log.error("{}", e);
            }
        }
    }


    @Test
    public void search() throws Exception {
        try {
            MilvusClient milvusClient = milvusClient();

            List<String> outFields = Collections.singletonList("name");
            List<List<Float>> vectors = new ArrayList<>();
            List<Float> vector = searchVector();
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

            R<SearchResults> searchResponse = milvusClient.search(searchParam);
            SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResponse.getData().getResults());
            for (int i = 0; i < vectors.size(); ++i) {
                log.info("Search result of No.{}", i);
                List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(i);
                log.info("{}", scores);
                log.info("Output field data for No.{}", i);
                log.info("{}", wrapper.getFieldData("name", i));
            }
        } catch (Exception e) {
            if(e instanceof java.net.ConnectException) {
                log.warn("{}", e);
            } else {
                log.error("{}", e);
            }
        }
    }


    private List<JsonObject> insertRows() throws Exception {
        List<JsonObject> rowsData = new ArrayList<>();
        try {
            org.springframework.core.io.support.PathMatchingResourcePatternResolver resolver = new org.springframework.core.io.support.PathMatchingResourcePatternResolver();
            org.springframework.core.io.Resource[] resources = resolver.getResources("classpath*:/dir/*.jpg");

            for(org.springframework.core.io.Resource resource : resources) {
                File imageAbsPath = TestResourceUtil.getFile("/dir/" + resource.getFilename());
                float[] vector = convertImageToVector(imageAbsPath.getPath());
                String name = resource.getFilename();
                JsonObject row = new JsonObject();
                row.addProperty("name", name);
                row.add("vector", convert2(vector));
                rowsData.add(row);
                if(rowsData.size() > 1000) {
                    break;
                }
            }

        } catch (Exception e) {
            log.error("{}", e);
        }
        return rowsData;
    }

    private List<Float> searchVector() throws Exception {
        List<Float> floatList = new ArrayList<>();
        try {
            File file = TestResourceUtil.getFile("/dir/一.jpg");
            float[] floats = convertImageToVector(file.getPath());
            for (float value : floats) {
                floatList.add(value);
            }
        } catch (Exception e) {
            log.error("{}", e);
        }
        return floatList;
    }

    private List<Float> convert(float[] vector) {
        List<Float> floatList = new ArrayList<>();
        for (float value : vector) {
            floatList.add(value);
        }
        return floatList;
    }

    private com.google.gson.JsonArray convert2(float[] vector) {
        com.google.gson.JsonArray vectorFloatArray = new com.google.gson.JsonArray();

        for (float value : vector) {
            vectorFloatArray.add(value);
        }
        return vectorFloatArray;
    }

    private static float[] convertImageToVector(String imagePath) throws Exception {
        BufferedImage image = ImageIO.read(new File(imagePath));
        return convertImageToVector(image);
    }

    /**
     * 图像转向量表示
     *
     * @param image
     * @return
     */
    private static float[] convertImageToVector(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        float[] vector = new float[width * height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getRGB(x, y);

                vector[y * width + x] = (pixel == 0xFF000000) ? 1 : 0;
            }
        }

        return vector;
    }
}
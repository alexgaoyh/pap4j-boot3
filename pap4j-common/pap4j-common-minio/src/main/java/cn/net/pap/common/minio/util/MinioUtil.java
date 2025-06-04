package cn.net.pap.common.minio.util;

import io.minio.*;

import java.io.InputStream;

public class MinioUtil {

    public static final String ENDPOINT = "http://192.168.1.115:9000";
    public static final String ACCESS_KEY = "minioadmin";
    public static final String SECRET_KEY = "minioadmin";
    public static final String DEFAULT_BUCKET = "test";

    private static final MinioClient minioClient = MinioClient.builder().endpoint(ENDPOINT).credentials(ACCESS_KEY, SECRET_KEY).build();

    /**
     * 创建 Bucket（如果不存在）
     *
     * @param bucketName
     * @throws Exception
     */
    public static void createBucket(String bucketName) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

    /**
     * 上传文件
     *
     * @param bucketName
     * @param objectName
     * @param inputStream
     * @param size
     * @param contentType
     * @throws Exception
     */
    public static void upload(String bucketName, String objectName, InputStream inputStream, long size, String contentType) throws Exception {
        createBucket(bucketName);

        minioClient.putObject(PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(inputStream, size, -1).contentType(contentType).build());
    }

    /**
     * 下载文件
     *
     * @param bucketName
     * @param objectName
     * @return
     * @throws Exception
     */
    public static InputStream download(String bucketName, String objectName) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder().bucket(bucketName).object(objectName).build());
    }

    /**
     * 删除文件
     *
     * @param bucketName
     * @param objectName
     * @throws Exception
     */
    public static void delete(String bucketName, String objectName) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
    }

    /**
     * 重载方法：默认 bucket
     *
     * @param objectName
     * @return
     * @throws Exception
     */
    public static InputStream download(String objectName) throws Exception {
        return download(DEFAULT_BUCKET, objectName);
    }

    /**
     * 删除
     *
     * @param objectName
     * @throws Exception
     */
    public static void delete(String objectName) throws Exception {
        delete(DEFAULT_BUCKET, objectName);
    }

}

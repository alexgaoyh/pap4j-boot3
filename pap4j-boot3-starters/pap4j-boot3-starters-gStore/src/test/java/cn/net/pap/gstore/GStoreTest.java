package cn.net.pap.gstore;

import jgsc.GstoreConnector;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class GStoreTest {

    public static final String ip = "192.168.1.115";

    public static final Integer port = 9001;

    public static final String httpType = "ghttp";

    public static final String dbName = "kuangbiao";
    public static final String username = "root";
    public static final String password = "123456";

    // 请求格式 json
    public static final String format_json = "json";

    // 请求方式 GET
    public static final String request_type_get = "GET";

    // 请求方式 POST
    public static final String request_type_post = "POST";

    // SPARQL 语句，查询所有
    public static final String SPARQL_SELECT_ALL = "SELECT * WHERE { ?s ?p ?o }";

    // @Test
    public void queryAll() throws Exception {
        GstoreConnector gc = new GstoreConnector(ip, port, httpType, username, password);

        gc.load(dbName, null, request_type_get);

        String res = gc.query(dbName, format_json, SPARQL_SELECT_ALL, request_type_get);
        JSONObject queryJson = new JSONObject(res);
        assertTrue(queryJson.get("StatusCode").toString().equals("0"));

        gc.unload(dbName);

    }

    // @Test
    public void crud() throws Exception {
        String curdDBName = "test";
        // 在服务端执行 cd /home/alexgaoyh;  touch gStore-empty.nt 命令，生成一个空的文件，这样就可以进行 DB 创建
        String dbPathInServer = "/home/alexgaoyh/gStore-empty.nt";

        GstoreConnector gc = new GstoreConnector(ip, port, httpType, username, password);

        // 先删除DB，再创建DB
        String dropDB = gc.drop(curdDBName, false);
        System.out.println(dropDB);
        String buildDB = gc.build(curdDBName, dbPathInServer);
        System.out.println(buildDB);

        // 使用当前 DB
        gc.load(curdDBName, null, request_type_get);

        // 插入三元组数据
        String insertRes = gc.query(curdDBName, format_json, "insert data { " +
                "<人物/#张三> <好友> <人物/#李四>." +
                "<人物/#张三> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <人物>." +
                "<人物/#李四> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <人物>." +
                "<人物/#张三> <性别> \"男\"^^<http://www.w3.org/2001/XMLSchema#String>.\n" +
                "<人物/#张三> <年龄> \"28\"^^<http://www.w3.org/2001/XMLSchema#Int>.\n" +
                "}", request_type_get);
        System.out.println(insertRes);

        String updateRes = gc.query(curdDBName, format_json, "DELETE data {\n" +
                "  <人物/#张三> <性别> \"男\"^^<http://www.w3.org/2001/XMLSchema#String>.\n" +
                "}", request_type_get);
        System.out.println(updateRes);

        String insert2Res = gc.query(curdDBName, format_json, "insert data {\n" +
                "  <人物/#张三> <性别> \"女\"^^<http://www.w3.org/2001/XMLSchema#String>.\n" +
                "}", request_type_get);
        System.out.println(insert2Res);
    }

    // 事务测试
    @Test
    @org.junit.jupiter.api.Disabled("Requires local environment/dataset")
    public void transaction() throws Exception {
        String transactionDBName = "transaction";
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("gStore-empty", ".nt");
        try {
            String dbPathInServer = "/home/alexgaoyh/gStore-empty.nt";
            // 事务隔离级别，串行化
            String isoLevel = "3";
            GstoreConnector gc = new GstoreConnector(ip, port, httpType, username, password);

            // 先删除DB，再创建DB
            String dropDB = gc.drop(transactionDBName, false);
            System.out.println(dropDB);
            String buildDB = gc.build(transactionDBName, dbPathInServer);
            System.out.println(buildDB);

            // 使用当前 DB
            gc.load(transactionDBName, null, request_type_get);

            // 开启事务
            String begin = gc.begin(transactionDBName, isoLevel, request_type_get);
            JSONObject beginJson = new JSONObject(begin);
            System.out.println(begin);
            if(beginJson.get("StatusCode").toString().equals("0")) {
                String tId = beginJson.get("TID").toString();

                try {
                    // 插入三元组数据
                    String insertRes = gc.tquery(transactionDBName, tId, "insert data { " +
                            "<人物/#张三> <好友> <人物/#李四>." +
                            "<人物/#张三> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <人物>." +
                            "<人物/#李四> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <人物>." +
                            "<人物/#张三> <性别> \"男\"^^<http://www.w3.org/2001/XMLSchema#String>.\n" +
                            "<人物/#张三> <年龄> \"28\"^^<http://www.w3.org/2001/XMLSchema#Int>.\n" +
                            "}", request_type_get);
                    System.out.println(insertRes);

                    String insert2Res = gc.tquery(transactionDBName, tId, "insert data {\n" +
                            "  <人物/#张三> <性别> \"女\"^^<http://www.w3.org/2001/XMLSchema#String>.\n" +
                            "}", request_type_get);
                    System.out.println(insert2Res);

                    String updateRes = gc.tquery(transactionDBName, tId, "delete data {\n" +
                            "<人物/#张三> <性别> \"男\"^^<http://www.w3.org/2001/XMLSchema#String>." +
                            "}", request_type_get);
                    System.out.println(updateRes);

                    String commit = gc.commit(transactionDBName, tId);
                    System.out.println(commit);

                } catch (Exception e) {
                    String rollback = gc.rollback(transactionDBName, tId);
                    System.out.println(rollback);
                }
            }

            String checkpoint = gc.checkpoint(transactionDBName);
            System.out.println(checkpoint);

            String res = gc.query(transactionDBName, format_json, SPARQL_SELECT_ALL, request_type_get);
            System.out.println(res);
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }
}

package cn.net.pap.common.jsqlparser;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.sf.jsqlparser.parser.CCJSqlParserManager;
import org.junit.jupiter.api.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;

public class CRUDGeneratorUtilTest {

    private static final Logger log = LoggerFactory.getLogger(CRUDGeneratorUtilTest.class);

    /**
     * CRUD sql
     * @throws Exception
     */
    @Test
    public void test1() throws Exception {
        try {
            CCJSqlParserManager parserManager = new CCJSqlParserManager();

            Doris doris1 = new Doris(1L, "alex1", "alexgaoyh1");
            String insertSQL1 = CRUDGeneratorUtil.generateInsertSQL(doris1);
            log.info("Insert SQL 1: {}", parserManager.parse(new StringReader(insertSQL1)));

            Doris doris2 = new Doris(2L, "alex2", "alexgaoyh2");
            String insertSQL2 = CRUDGeneratorUtil.generateInsertSQL(doris2);
            log.info("Insert SQL 2: {}", parserManager.parse(new StringReader(insertSQL2)));

            doris2 = new Doris(2L, "alex22", "alexgaoyh22");
            String updateSQL2 = CRUDGeneratorUtil.generateUpdateSQL(doris2);
            log.info("Update SQL 2: {}", parserManager.parse(new StringReader(updateSQL2)));

            String deleteSQL1 = CRUDGeneratorUtil.generateDeleteSQL(doris1);
            log.info("Delete SQL 1: {}", parserManager.parse(new StringReader(deleteSQL1)));

            String selectSql = CRUDGeneratorUtil.generateSelectSQL(Doris.class);
            log.info("Select SQL: {}", parserManager.parse(new StringReader(selectSql)));

        } catch (Exception e) {
            log.error("Error occurred in test1: ", e);
        }
    }

    /**
     * Entity 实体类
     */
    class Doris {

        @JsonProperty("id")
        private Long id;

        @JsonProperty("dorisName")
        private String dorisName;

        @JsonProperty("dorisRemark")
        private String dorisRemark;

        public Doris(Long id, String dorisName, String dorisRemark) {
            this.id = id;
            this.dorisName = dorisName;
            this.dorisRemark = dorisRemark;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getDorisName() {
            return dorisName;
        }

        public void setDorisName(String dorisName) {
            this.dorisName = dorisName;
        }

        public String getDorisRemark() {
            return dorisRemark;
        }

        public void setDorisRemark(String dorisRemark) {
            this.dorisRemark = dorisRemark;
        }
    }

}

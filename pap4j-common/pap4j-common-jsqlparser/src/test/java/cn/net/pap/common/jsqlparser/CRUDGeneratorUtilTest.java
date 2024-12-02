package cn.net.pap.common.jsqlparser;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.sf.jsqlparser.parser.CCJSqlParserManager;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

public class CRUDGeneratorUtilTest {

    /**
     * CRUD sql
     * @throws Exception
     */
    @Test
    public void test1() throws Exception {
        try {
            CCJSqlParserManager parserManager = new CCJSqlParserManager();

            Doris doris1 = new Doris(1l, "alex1", "alexgaoyh1");
            String insertSQL1 = CRUDGeneratorUtil.generateInsertSQL(doris1);
            System.out.println(parserManager.parse(new StringReader(insertSQL1)));

            Doris doris2 = new Doris(2l, "alex2", "alexgaoyh2");
            String insertSQL2 = CRUDGeneratorUtil.generateInsertSQL(doris2);
            System.out.println(parserManager.parse(new StringReader(insertSQL2)));

            doris2 = new Doris(2l, "alex22", "alexgaoyh22");
            String updateSQL2 = CRUDGeneratorUtil.generateUpdateSQL(doris2);
            System.out.println(parserManager.parse(new StringReader(updateSQL2)));

            String deleteSQL1 = CRUDGeneratorUtil.generateDeleteSQL(doris1);
            System.out.println(parserManager.parse(new StringReader(deleteSQL1)));

            String selectSql = CRUDGeneratorUtil.generateSelectSQL(Doris.class);
            System.out.println(parserManager.parse(new StringReader(selectSql)));

        } catch (Exception e) {
            e.printStackTrace();
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

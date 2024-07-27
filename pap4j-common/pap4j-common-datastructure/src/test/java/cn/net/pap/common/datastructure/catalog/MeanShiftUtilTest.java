package cn.net.pap.common.datastructure.catalog;

import cn.net.pap.common.datastructure.meanShift.MeanShiftUtil;
import cn.net.pap.common.datastructure.meanShift.PointX;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * 聚类算法
 */
public class MeanShiftUtilTest {

    // 这个结果可以从 pap4j-common-pdf.ITextTest.java 中的 centerXTextList 获得。
    private static final String rectJSON= "";


    // 这个结果可以从 pap4j-common-pdf.ITextTest.java 中的 minWidth 获得。
    private static final BigDecimal minWidth = new BigDecimal(0);


    // 这个结果可以从 pap4j-common-pdf.ITextTest.java 中的 maxWidth 获得。
    private static final BigDecimal maxWidth = new BigDecimal(0);

    @Test
    public void test() throws Exception {
        if(!StringUtils.isBlank(rectJSON)) {
            ObjectMapper objectMapper = new ObjectMapper();
            List<PointX> pointXES = objectMapper.readValue(rectJSON, new TypeReference<List<PointX>>() {
            });
            List<List<PointX>> lists = MeanShiftUtil.meanShiftRemoveZero(pointXES, maxWidth.divide(new BigDecimal(2), 2, BigDecimal.ROUND_HALF_UP).doubleValue());
            for(List<PointX> groupList : lists) {
                for(PointX pointX : groupList) {
                    System.out.print(pointX.getInfo().get("text"));
                }
                System.out.println();
            }
        }
    }


}

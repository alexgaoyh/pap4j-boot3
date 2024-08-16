package cn.net.pap.common.datastructure.catalog;

import cn.net.pap.common.datastructure.meanShift.MeanShiftUtil;
import cn.net.pap.common.datastructure.meanShift.PointX;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
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
        } else {
            List<PointX> pointXList = new ArrayList<>();

            pointXList.add(new PointX(100, new HashMap<>(){{put("text", "100");}}));
            pointXList.add(new PointX(110, new HashMap<>(){{put("text", "110");}}));
            pointXList.add(new PointX(200, new HashMap<>(){{put("text", "200");}}));
            pointXList.add(new PointX(300, new HashMap<>(){{put("text", "300");}}));
            pointXList.add(new PointX(400, new HashMap<>(){{put("text", "400");}}));
            pointXList.add(new PointX(500, new HashMap<>(){{put("text", "500");}}));
            List<List<PointX>> lists = MeanShiftUtil.meanShiftRemoveZero(pointXList, new BigDecimal(100).divide(new BigDecimal(2), 2, BigDecimal.ROUND_HALF_UP).doubleValue());
            for(List<PointX> groupList : lists) {
                for(PointX pointX : groupList) {
                    System.out.print(pointX.getInfo().get("text"));
                }
                System.out.println();
            }
        }
    }


}

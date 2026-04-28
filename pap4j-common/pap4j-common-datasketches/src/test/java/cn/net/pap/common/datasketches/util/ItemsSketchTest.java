package cn.net.pap.common.datasketches.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.datasketches.quantiles.ItemsSketch;
import org.apache.datasketches.quantilescommon.ItemsSketchSortedView;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.Comparator;

public class ItemsSketchTest {
    private static final Logger log = LoggerFactory.getLogger(ItemsSketchTest.class);

    class TermStatDTO implements Serializable {

        public long globalTotalCount = 0;

        public long docCount = 0;
    }

    class TermStatComparator implements Comparator<TermStatDTO> {

        @Override
        public int compare(TermStatDTO o1, TermStatDTO o2) {
            long score1 = o1.globalTotalCount * o1.docCount;
            long score2 = o2.globalTotalCount * o2.docCount;
            return Long.compare(score1, score2);
        }
    }

    @Test
    public void test1() {
        ItemsSketch<TermStatDTO> sketch = ItemsSketch.getInstance(TermStatDTO.class, 32768, new TermStatComparator());

        for (int i = 0; i < 100000000; i++) {
            TermStatDTO termStatDTO = new TermStatDTO();
            termStatDTO.globalTotalCount += i;
            termStatDTO.docCount += i;
            sketch.update(termStatDTO);
        }

        try {
            ItemsSketchSortedView<TermStatDTO> sortedView = sketch.getSortedView();

            TermStatDTO top = sortedView.getMaxItem();
            if (top != null) {
                log.info("TOP:");
                log.info("{}", 
                        top.globalTotalCount + " : " + top.docCount
                );
            }

            int topN = 10;
            TermStatDTO[] retained = sortedView.getQuantiles();
            log.info("{}", "TOP " + topN + ":");
            for (int i = retained.length - 1, c = 0; i >= 0 && c < topN; i--, c++) {
                TermStatDTO t = retained[i];
                log.info("{}", 
                        t.globalTotalCount + " : " + t.docCount
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

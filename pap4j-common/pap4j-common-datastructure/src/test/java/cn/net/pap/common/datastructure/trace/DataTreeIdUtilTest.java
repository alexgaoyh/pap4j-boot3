package cn.net.pap.common.datastructure.trace;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DataTreeIdUtilTest {

    @Test
    void testGenerateRoot() {
        String root = DataTreeIdUtil.generateRoot();

        assertNotNull(root);
        assertEquals(DataTreeIdUtil.getNodeLength(), root.length());

        DataTreeIdUtil.TraceMeta meta = DataTreeIdUtil.parse(root);
        assertEquals(1, meta.level);
        assertEquals(1, meta.branch);
        assertNull(meta.parentTraceId);
    }

    // ================== 单步派生 ==================

    @Test
    void testNextStep() {
        String root = DataTreeIdUtil.generateRoot();
        String child = DataTreeIdUtil.nextStep(root);

        assertEquals(2 * DataTreeIdUtil.getNodeLength(), child.length());

        DataTreeIdUtil.TraceMeta meta = DataTreeIdUtil.parse(child);
        assertEquals(2, meta.level);
        assertEquals(1, meta.branch);
        assertEquals(root, meta.parentTraceId);
    }

    @Test
    void testMultipleNextSteps() {
        String trace = DataTreeIdUtil.generateRoot();

        for (int i = 2; i <= 5; i++) {
            trace = DataTreeIdUtil.nextStep(trace);
            DataTreeIdUtil.TraceMeta meta = DataTreeIdUtil.parse(trace);

            assertEquals(i, meta.level);
            assertEquals(1, meta.branch);
        }

        assertEquals(5 * DataTreeIdUtil.getNodeLength(), trace.length());
    }

    // ================== 多分支派生 ==================

    @Test
    void testDeriveBranches() {
        String root = DataTreeIdUtil.generateRoot();
        List<String> branches = DataTreeIdUtil.deriveBranches(root, 3);

        assertEquals(3, branches.size());

        for (int i = 0; i < branches.size(); i++) {
            String traceId = branches.get(i);
            DataTreeIdUtil.TraceMeta meta = DataTreeIdUtil.parse(traceId);

            assertEquals(2, meta.level);
            assertEquals(i + 1, meta.branch);
            assertEquals(root, meta.parentTraceId);
        }
    }

    // ================== 祖先追溯 ==================

    @Test
    void testTraceToRoot() {
        String root = DataTreeIdUtil.generateRoot();
        String level2 = DataTreeIdUtil.nextStep(root);
        String level3 = DataTreeIdUtil.nextStep(level2);

        List<String> path = DataTreeIdUtil.traceToRoot(level3);

        assertEquals(3, path.size());
        assertEquals(root, path.get(0));
        assertEquals(level2, path.get(1));
        assertEquals(level3, path.get(2));
    }

    @Test
    void testTraceToRootSingleNode() {
        String root = DataTreeIdUtil.generateRoot();
        List<String> path = DataTreeIdUtil.traceToRoot(root);

        assertEquals(1, path.size());
        assertEquals(root, path.get(0));
    }


}

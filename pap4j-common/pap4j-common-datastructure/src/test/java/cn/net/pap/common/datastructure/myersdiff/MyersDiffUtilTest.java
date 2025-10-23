package cn.net.pap.common.datastructure.myersdiff;

import org.junit.jupiter.api.Test;

import java.util.List;

public class MyersDiffUtilTest {

    @Test
    public void diffTest01() {
        String[] original = {"A", "B", "C", "D"};
        String[] modified = {"A", "C", "D", "E"};

        List<MyersDiffUtil.Edit> edits = MyersDiffUtil.diff(original, modified);
        for (MyersDiffUtil.Edit e : edits) {
            System.out.println(e);
        }
    }

    @Test
    public void diffTest02() {
        String[] original = {"A", "B", "C"};
        String[] modified = {"A", "B", "C"};

        List<MyersDiffUtil.Edit> edits = MyersDiffUtil.diff(original, modified);
        for (MyersDiffUtil.Edit e : edits) {
            System.out.println(e);
        }
    }

    @Test
    public void diffTest03() {
        String[] original = {"A", "B", "C", "D"};
        String[] modified = {"A", "C", "D"};

        List<MyersDiffUtil.Edit> edits = MyersDiffUtil.diff(original, modified);
        for (MyersDiffUtil.Edit e : edits) {
            System.out.println(e);
        }
    }

    @Test
    public void diffTest04() {
        String[] original = {"A", "C", "D"};
        String[] modified = {"A", "B", "C", "D"};

        List<MyersDiffUtil.Edit> edits = MyersDiffUtil.diff(original, modified);
        for (MyersDiffUtil.Edit e : edits) {
            System.out.println(e);
        }
    }

    @Test
    public void diffTest05() {
        String[] original = {"A", "B", "C"};
        String[] modified = {"A", "X", "C"};

        List<MyersDiffUtil.Edit> edits = MyersDiffUtil.diff(original, modified);
        for (MyersDiffUtil.Edit e : edits) {
            System.out.println(e);
        }
    }

    @Test
    public void diffTest06() {
        String[] original = {"A", "B", "C", "D"};
        String[] modified = {"A", "X", "C", "E"};

        List<MyersDiffUtil.Edit> edits = MyersDiffUtil.diff(original, modified);
        for (MyersDiffUtil.Edit e : edits) {
            System.out.println(e);
        }
    }

    @Test
    public void diffTest07() {
        String[] original = {"A", "B", "C"};
        String[] modified = {"X", "Y", "Z"};

        List<MyersDiffUtil.Edit> edits = MyersDiffUtil.diff(original, modified);
        for (MyersDiffUtil.Edit e : edits) {
            System.out.println(e);
        }
    }

    @Test
    public void diffTest08() {
        String[] original = {};
        String[] modified = {"A", "B", "C"};

        List<MyersDiffUtil.Edit> edits = MyersDiffUtil.diff(original, modified);
        for (MyersDiffUtil.Edit e : edits) {
            System.out.println(e);
        }
    }

    @Test
    public void diffTest09() {
        String[] original = {"A", "B", "C"};
        String[] modified = {};

        List<MyersDiffUtil.Edit> edits = MyersDiffUtil.diff(original, modified);
        for (MyersDiffUtil.Edit e : edits) {
            System.out.println(e);
        }
    }

    @Test
    public void diffTest10() {
        String[] original = {"B", "C", "D"};
        String[] modified = {"A", "B", "C", "D", "E"};

        List<MyersDiffUtil.Edit> edits = MyersDiffUtil.diff(original, modified);
        for (MyersDiffUtil.Edit e : edits) {
            System.out.println(e);
        }
    }


}

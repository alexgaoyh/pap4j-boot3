package cn.net.pap.common.excel.dto;

import java.io.*;
import java.util.List;
import java.util.Objects;

/**
 * 一个简单的三元组表示对象
 *
 * @param <T1>
 * @param <T2>
 * @param <T3>
 */
public class SimpleTriple<T1, T2, T3> implements Serializable {

    /**
     * 主语
     */
    private T1 s;

    /**
     * 谓语
     */
    private T2 p;

    /**
     * 宾语
     */
    private T3 o;

    public SimpleTriple() {
    }

    public SimpleTriple(T1 s, T2 p, T3 o) {
        this.s = s;
        this.p = p;
        this.o = o;
    }

    public T1 getS() {
        return s;
    }

    public void setS(T1 s) {
        this.s = s;
    }

    public T2 getP() {
        return p;
    }

    public void setP(T2 p) {
        this.p = p;
    }

    public T3 getO() {
        return o;
    }

    public void setO(T3 o) {
        this.o = o;
    }

    @Override
    public boolean equals(Object o1) {
        if (this == o1) return true;
        if (o1 == null || getClass() != o1.getClass()) return false;
        SimpleTriple<?, ?, ?> that = (SimpleTriple<?, ?, ?>) o1;
        return Objects.equals(s, that.s) && Objects.equals(p, that.p) && Objects.equals(o, that.o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(s, p, o);
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("SimpleTriple{");
        sb.append("s=").append(s);
        sb.append(", p=").append(p);
        sb.append(", o=").append(o);
        sb.append('}');
        return sb.toString();
    }

    /**
     * 转换为三元组格式：  <s> <p> <o> .
     * @return
     */
    public String toTriple() {
        final StringBuffer sb = new StringBuffer("");
        sb.append("<").append(convertSpecialCharsToUnicode(getS())).append("> ");
        sb.append("<").append(convertSpecialCharsToUnicode(getP())).append("> ");
        sb.append("<").append(convertSpecialCharsToUnicode(getO())).append("> ");
        sb.append('.');
        return sb.toString();
    }

    /**
     * 特殊字符转换，比如空格
     *
     * @param input
     * @return
     */
    public static String convertSpecialCharsToUnicode(Object input) {
        if (input == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.toString().length(); i++) {
            char c = input.toString().charAt(i);
            switch (c) {
                case ' ':
                    sb.append("\\u0020"); // 空格
                    break;
                case '\n':
                    sb.append("\\u000A"); // 换行符
                    break;
                case '\t':
                    sb.append("\\u0009"); // 制表符
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 写入文件，按行的三元组信息： <s> <p> <o> .
     *
     * @param fileNameAbsolutePath
     * @param simpleTripleList
     * @throws Exception
     */
    public void write2TripleFile(String fileNameAbsolutePath, List<SimpleTriple<T1, T2, T3>> simpleTripleList) throws Exception {
        FileOutputStream fos = new FileOutputStream(new File(fileNameAbsolutePath));
        OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
        BufferedWriter bw = new BufferedWriter(osw);

        for (SimpleTriple simpleTriple : simpleTripleList) {
            bw.write(simpleTriple.toTriple() + "\t\n");
        }
        bw.close();
        osw.close();
        fos.close();
    }

}

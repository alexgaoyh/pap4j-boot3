package cn.net.pap.example.javafx.comparator;

import java.nio.file.Path;
import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 一个与操作系统排序规则接近的自然排序比较器。 支持 String 和 Path 类型（自动比较文件名部分）。
 */
public class OSAlignedNaturalComparator<T> implements Comparator<T> {
    private static final Pattern PATTERN = Pattern.compile("(\\d+)|(\\D+)");
    private final Collator collator;

    public OSAlignedNaturalComparator() {
        this.collator = Collator.getInstance(Locale.getDefault());
        this.collator.setDecomposition(Collator.CANONICAL_DECOMPOSITION);
    }

    @Override
    public int compare(T o1, T o2) {
        if (o1 == null && o2 == null) return 0;
        if (o1 == null) return -1;
        if (o2 == null) return 1;

        String s1 = extractComparableString(o1);
        String s2 = extractComparableString(o2);

        if (s1.equals(s2)) return 0;

        // Linux/Unix 风格：隐藏文件优先（以 '.' 开头）
        boolean s1Dot = s1.startsWith(".");
        boolean s2Dot = s2.startsWith(".");
        if (s1Dot != s2Dot) return s1Dot ? -1 : 1;

        Matcher m1 = PATTERN.matcher(s1);
        Matcher m2 = PATTERN.matcher(s2);

        while (m1.find()) {
            if (!m2.find()) return 1;

            String part1 = m1.group();
            String part2 = m2.group();

            if (isDigit(part1.charAt(0)) && isDigit(part2.charAt(0))) {
                int cmp = compareNumerically(part1, part2);
                if (cmp != 0) return cmp;
            } else {
                int cmp = collator.compare(part1, part2);
                if (cmp != 0) return cmp;
            }
        }

        return m2.find() ? -1 : 0;
    }

    private String extractComparableString(T obj) {
        if (obj instanceof Path path) {
            Path fileName = path.getFileName();
            return fileName == null ? "" : fileName.toString();
        } else {
            return obj.toString();
        }
    }

    private int compareNumerically(String n1, String n2) {
        String v1 = trimLeadingZeros(n1);
        String v2 = trimLeadingZeros(n2);

        if (v1.length() != v2.length()) {
            return v1.length() - v2.length();
        }
        int cmp = v1.compareTo(v2);
        if (cmp != 0) return cmp;

        // 数值相等时，前导零多的排前
        return n2.length() - n1.length();
    }

    private String trimLeadingZeros(String s) {
        int i = 0;
        while (i < s.length() - 1 && s.charAt(i) == '0') i++;
        return s.substring(i);
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}

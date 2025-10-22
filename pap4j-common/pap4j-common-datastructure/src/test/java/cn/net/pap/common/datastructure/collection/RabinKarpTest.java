package cn.net.pap.common.datastructure.collection;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RabinKarpTest {

    // 基底与模（使用 long 保证不会溢出）
    private long base = 256l;
    private long mod = 1_000_000_007L;

    public long getBase() {
        return base;
    }

    public void setBase(long base) {
        this.base = base;
    }

    public long getMod() {
        return mod;
    }

    public void setMod(long mod) {
        this.mod = mod;
    }

    /**
     * 计算字符串 s 中长度为 len 的第一个窗口的哈希（多项式滚动哈希）
     */
    public long computeHash(String s, int len) {
        long h = 0;
        for (int i = 0; i < len; i++) {
            h = (h * base + (s.charAt(i))) % mod;
        }
        return h;
    }

    /**
     * 计算 base^(exp) % mod
     */
    public long modPow(long base, int exp) {
        long result = 1;
        long b = base % mod;
        while (exp > 0) {
            if ((exp & 1) == 1) result = (result * b) % mod;
            b = (b * b) % mod;
            exp >>= 1;
        }
        return result;
    }

    /**
     * 返回文本 text 中所有等于 pattern 的起始下标（允许重叠）
     */
    public List<Integer> searchAll(String text, String pattern) {
        List<Integer> matches = new ArrayList<>();
        if (pattern == null || text == null) return matches;
        int n = text.length();
        int m = pattern.length();
        if (m == 0) {
            // 约定：空模式匹配所有位置（可按需调整）
            for (int i = 0; i <= n; i++) matches.add(i);
            return matches;
        }
        if (m > n) return matches;

        long patHash = computeHash(pattern, m);
        long textHash = computeHash(text, m);
        long highPow = modPow(base, m - 1); // base^(m-1) % mod

        for (int i = 0; i <= n - m; i++) {
            if (patHash == textHash) {
                // 避免哈希冲突：确认字符串相等
                if (text.regionMatches(i, pattern, 0, m)) {
                    matches.add(i);
                }
            }
            // 滚动到下一个窗口
            if (i < n - m) {
                int leftChar = text.charAt(i);
                int rightChar = text.charAt(i + m);
                // newHash = ( (oldHash - leftChar*highPow) * base + rightChar ) % mod
                long removed = (leftChar * highPow) % mod;
                long cur = (textHash - removed) % mod;
                if (cur < 0) cur += mod;
                cur = (cur * base + rightChar) % mod;
                textHash = cur;
            }
        }
        return matches;
    }

    /**
     * 为了单元测试/逐步验证：返回 text 中每个长度 = windowLen 的窗口的哈希序列（从索引0开始）
     */
    public List<Long> slidingHashes(String text, int windowLen) {
        List<Long> hashes = new ArrayList<>();
        if (windowLen <= 0 || text == null || windowLen > text.length()) return hashes;
        int n = text.length();
        long h = computeHash(text, windowLen);
        hashes.add(h);
        long highPow = modPow(base, windowLen - 1);
        for (int i = 0; i < n - windowLen; i++) {
            int leftChar = text.charAt(i);
            int rightChar = text.charAt(i + windowLen);
            long removed = (leftChar * highPow) % mod;
            long cur = (h - removed) % mod;
            if (cur < 0) cur += mod;
            cur = (cur * base + rightChar) % mod;
            h = cur;
            hashes.add(h);
        }
        return hashes;
    }

    // 简单的查找第一个匹配（如果只需要第一个）
    public int indexOf(String text, String pattern) {
        List<Integer> all = searchAll(text, pattern);
        return all.isEmpty() ? -1 : all.get(0);
    }

    @Test
    void testSingleAndMultipleMatches() {
        RabinKarpTest rk = new RabinKarpTest();
        String text = "abracadabra";
        String pattern = "abra";

        List<Integer> matches = rk.searchAll(text, pattern);
        assertEquals(2, matches.size());
        assertEquals(List.of(0, 7), matches);
        assertEquals(0, rk.indexOf(text, pattern));
    }

}

package cn.net.pap.common.datastructure.chroniclemap.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机词语选择器工具类
 * 从文本文件中读取词语，支持随机返回指定数量的词语
 */
public class RandomWordSelector {

    // 存储所有词语的列表
    private static final List<String> WORD_LIST = new ArrayList<>();

    // 随机数生成器
    private static final Random RANDOM = ThreadLocalRandom.current();

    // 是否已初始化标志
    private static volatile boolean initialized = false;

    /**
     * 初始化方法，读取文件中的词语
     *
     * @param filePath 文件路径
     * @throws IOException 如果文件读取失败
     */
    public static synchronized void init(String filePath) throws IOException {
        if (initialized) {
            return; // 已经初始化过，直接返回
        }

        // 读取文件所有行
        List<String> lines = Files.readAllLines(Paths.get(filePath));

        // 过滤空行和去除首尾空格
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                WORD_LIST.add(trimmed);
            }
        }

        initialized = true;
        System.out.println("成功加载 " + WORD_LIST.size() + " 个词语");
    }

    /**
     * 随机获取指定数量的词语
     *
     * @param count 需要返回的词语数量
     * @return 随机词语列表
     * @throws IllegalStateException 如果未初始化
     */
    public static List<String> getRandomWords(int count) {
        if (!initialized) {
            throw new IllegalStateException("请先调用 init() 方法初始化");
        }

        if (count <= 0) {
            return Collections.emptyList();
        }

        // 如果请求的数量大于可用词语数量，返回所有词语的随机顺序
        if (count >= WORD_LIST.size()) {
            return getShuffledCopy();
        }

        // 使用洗牌算法的高效版本
        return selectRandomWords(count);
    }

    /**
     * 随机选择指定数量的词语
     *
     * @param count 需要选择的词语数量
     * @return 随机选择的词语列表
     */
    private static List<String> selectRandomWords(int count) {
        List<String> result = new ArrayList<>(count);

        // 使用 Fisher-Yates 洗牌算法的变体进行高效选择
        for (int i = 0; i < count; i++) {
            // 在剩余元素中随机选择一个
            int randomIndex = RANDOM.nextInt(WORD_LIST.size() - i) + i;

            // 将选择的元素交换到前面
            Collections.swap(WORD_LIST, i, randomIndex);

            // 将选择的元素添加到结果中
            result.add(WORD_LIST.get(i));
        }

        // 恢复原始列表的顺序（可选，如果需要保持原始顺序）
        // 如果不关心原始顺序，可以省略这一步
        for (int i = count - 1; i >= 0; i--) {
            Collections.swap(WORD_LIST, i, RANDOM.nextInt(WORD_LIST.size() - i) + i);
        }

        return result;
    }

    /**
     * 获取洗牌后的所有词语副本
     *
     * @return 洗牌后的词语列表
     */
    private static List<String> getShuffledCopy() {
        List<String> copy = new ArrayList<>(WORD_LIST);
        Collections.shuffle(copy, RANDOM);
        return copy;
    }

    /**
     * 获取所有词语的数量
     *
     * @return 词语总数
     */
    public static int getTotalWords() {
        return WORD_LIST.size();
    }

    /**
     * 重新加载文件
     *
     * @param filePath 文件路径
     * @throws IOException 如果文件读取失败
     */
    public static synchronized void reload(String filePath) throws IOException {
        WORD_LIST.clear();
        initialized = false;
        init(filePath);
    }

    /**
     * 清空所有词语
     */
    public static synchronized void clear() {
        WORD_LIST.clear();
        initialized = false;
    }
}

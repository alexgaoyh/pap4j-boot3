package cn.net.pap.common.datastructure.hanlp;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.dictionary.py.Pinyin;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class HanLPTest {

    private static final Logger log = LoggerFactory.getLogger(HanLPTest.class);

    /**
     * https://github.com/hankcs/HanLP/tree/1.x?tab=readme-ov-file#17-%E6%8B%BC%E9%9F%B3%E8%BD%AC%E6%8D%A2
     */
    @Test
    public void pinyinTest() {
        String text = "重载不是重任";
        List<Pinyin> pinyinList = HanLP.convertToPinyinList(text);
        
        StringBuilder sb = new StringBuilder("原文,");
        for (char c : text.toCharArray()) {
            sb.append(c).append(",");
        }
        log.info("{}", sb);

        sb = new StringBuilder("拼音（数字音调）,");
        for (Pinyin pinyin : pinyinList) {
            sb.append(pinyin).append(",");
        }
        log.info("{}", sb);

        sb = new StringBuilder("拼音（符号音调）,");
        for (Pinyin pinyin : pinyinList) {
            sb.append(pinyin.getPinyinWithToneMark()).append(",");
        }
        log.info("{}", sb);

        sb = new StringBuilder("拼音（无音调）,");
        for (Pinyin pinyin : pinyinList) {
            sb.append(pinyin.getPinyinWithoutTone()).append(",");
        }
        log.info("{}", sb);

        sb = new StringBuilder("声调,");
        for (Pinyin pinyin : pinyinList) {
            sb.append(pinyin.getTone()).append(",");
        }
        log.info("{}", sb);

        sb = new StringBuilder("声母,");
        for (Pinyin pinyin : pinyinList) {
            sb.append(pinyin.getShengmu()).append(",");
        }
        log.info("{}", sb);

        sb = new StringBuilder("韵母,");
        for (Pinyin pinyin : pinyinList) {
            sb.append(pinyin.getYunmu()).append(",");
        }
        log.info("{}", sb);

        sb = new StringBuilder("输入法头,");
        for (Pinyin pinyin : pinyinList) {
            sb.append(pinyin.getHead()).append(",");
        }
        log.info("{}", sb);
    }

}

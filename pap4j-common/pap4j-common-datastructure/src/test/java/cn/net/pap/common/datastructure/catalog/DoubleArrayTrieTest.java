package cn.net.pap.common.datastructure.catalog;

import cn.net.pap.common.datastructure.resource.TestResourceUtil;
import cn.net.pap.common.datastructure.trie.DoubleArrayTrie;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.util.*;

public class DoubleArrayTrieTest {

    @Test
    public void simpleTest() throws Exception {

        List<String> words = new ArrayList<String>();
        words.add("一举");
        words.add("一举成名");
        words.add("一举成名天下知");
        Collections.sort(words);

        Set<Character> charset = new HashSet<Character>();

        System.out.println("字典词条：" + words.size());

        {
            String infoCharsetValue = "";
            String infoCharsetCode = "";
            for (Character c : charset)
            {
                infoCharsetValue += c.charValue() + "    ";
                infoCharsetCode += (int)c.charValue() + " ";
            }
            infoCharsetValue += '\n';
            infoCharsetCode += '\n';
            System.out.print(infoCharsetValue);
            System.out.print(infoCharsetCode);
        }

        DoubleArrayTrie dat = new DoubleArrayTrie();
        System.out.println("是否错误: " + dat.build(words));
        List<Integer> integerList = dat.commonPrefixSearch("一举成名天下知");
        for (int index : integerList) {
            System.out.println(words.get(index));
        }

        int idx = dat.exactMatchSearch("一举成名天下知");
        System.out.println(words.get(idx));
    }

    @Test
    public void test() throws Exception {
        // dict.dict 每一行是一个词
        BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(new FileInputStream(new File(TestResourceUtil.getFile("dict.dict").toPath().toAbsolutePath().toString()))));
        String line;
        List<String> words = new ArrayList<String>();
        Set<Character> charset = new HashSet<Character>();
        while ((line = reader.readLine()) != null)
        {
            words.add(line);
            // 制作一份码表debug
            for (char c : line.toCharArray())
            {
                charset.add(c);
            }
        }
        reader.close();
        // 这个字典如果要加入新词必须按字典序，参考下面的代码
//        Collections.sort(words);
//        BufferedWriter writer = new BufferedWriter(new FileWriter("./data/sorted.dic", false));
//        for (String w : words)
//        {
//            writer.write(w);
//            writer.newLine();
//        }
        // 显示调用，需与使用字典序.
        Collections.sort(words);

        System.out.println("字典词条：" + words.size());

        {
            String infoCharsetValue = "";
            String infoCharsetCode = "";
            for (Character c : charset)
            {
                infoCharsetValue += c.charValue() + "    ";
                infoCharsetCode += (int)c.charValue() + " ";
            }
            infoCharsetValue += '\n';
            infoCharsetCode += '\n';
            System.out.print(infoCharsetValue);
            System.out.print(infoCharsetCode);
        }

        DoubleArrayTrie dat = new DoubleArrayTrie();
        System.out.println("是否错误: " + dat.build(words));
        System.out.println(dat);
        List<Integer> integerList = dat.commonPrefixSearch("一举成名天下知");
        for (int index : integerList)
        {
            System.out.println(words.get(index));
        }
    }
}

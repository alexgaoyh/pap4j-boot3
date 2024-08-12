package cn.net.pap.common.file.chinese;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class ChineseWordSorterUtil {

    /**
     * 按照字典序添加词语到不同文件
     * @param basePathDir
     * @param word
     * @return
     */
    public static final Boolean add(String basePathDir, String word) {
        String firstChar = "0";

        char[] chars = word.toCharArray();
        for (int i = 0; i < chars.length;) {
            String c = "";
            if (Character.isHighSurrogate(chars[i])) {
                firstChar = new String(Character.toChars(Character.toCodePoint(chars[i], chars[i + 1])));
            } else {
                firstChar = chars[i] + "";
            }
            break;
        }

        File file = new File(basePathDir + File.separator + firstChar + ".txt");
        if (!file.exists()) {
            try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(basePathDir + File.separator + firstChar + ".txt"),
                    Charset.forName("UTF-16"), StandardOpenOption.CREATE_NEW)) {
                writer.write(word);
            }catch (IOException e) {
                return false;
            }
            return true;
        } else {
            insertWordIntoSortedFile(basePathDir + File.separator + firstChar + ".txt", word);
        }

        return true;
    }


    private static boolean insertWordIntoSortedFile(String filePath, String newWord)  {
        File file = new File(filePath);
        StringBuilder fileContent = new StringBuilder();
        boolean wordInserted = false;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_16))) {
            String line;
            String previousLine = null;

            while ((line = reader.readLine()) != null) {
                if (!wordInserted) {
                    if (previousLine != null && previousLine.compareTo(newWord) < 0 && newWord.compareTo(line) < 0) {
                        fileContent.append(newWord).append(System.lineSeparator());
                        wordInserted = true;
                    } else if (previousLine == null && newWord.compareTo(line) < 0) {
                        fileContent.append(newWord).append(System.lineSeparator());
                        wordInserted = true;
                    }
                }

                fileContent.append(line).append(System.lineSeparator());
                previousLine = line;
            }

            if (!wordInserted && previousLine != null && previousLine.compareTo(newWord) < 0) {
                fileContent.append(newWord).append(System.lineSeparator());
            }
        } catch (IOException e) {
            return false;
        }

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_16))) {
            writer.write(fileContent.toString());
        } catch (IOException e) {
            return false;
        }

        return true;
    }
}

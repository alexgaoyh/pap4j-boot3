package cn.net.pap.common.pdf.unicode;

import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class UnicodeBlocks {

    public static class Block {
        public final int start;
        public final int end;
        public final String name;
        // 新增：记录支持该区块的字体列表
        private final List<String> supportedFonts;

        public Block(int s, int e, String n) {
            this.start = s;
            this.end = e;
            this.name = n;
            this.supportedFonts = new ArrayList<>();
        }

        public boolean contains(int codePoint) {
            return codePoint >= start && codePoint <= end;
        }

        // 新增：添加支持的字体
        public void addSupportedFont(String fontName) {
            if (!supportedFonts.contains(fontName)) {
                supportedFonts.add(fontName);
            }
        }

        // 新增：获取支持该区块的字体列表
        public List<String> getSupportedFonts() {
            return new ArrayList<>(supportedFonts);
        }

        // 新增：检查字体是否支持该区块
        public boolean isFontSupported(String fontName) {
            return supportedFonts.contains(fontName);
        }

        @Override
        public String toString() {
            return "Block{" +
                    "start=" + start +
                    ", end=" + end +
                    ", name='" + name + '\'' +
                    ", supportedFonts=" + supportedFonts +
                    '}';
        }
    }

    // 存储所有区块（按 start 排序）
    private final List<Block> blocks;

    public UnicodeBlocks(InputStream in) throws IOException {
        blocks = new ArrayList<>();
        loadBlocks(in);
        // 在构造函数中分析字体支持情况
        analyzeFontSupport();
    }

    private void loadBlocks(InputStream in) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                // 形如 "0000..007F; Basic Latin"
                String[] parts = line.split(";", 2);
                if (parts.length != 2) {
                    continue;
                }
                String rangePart = parts[0].trim();
                String namePart = parts[1].trim();
                String[] rangeTokens = rangePart.split("\\.\\.");
                if (rangeTokens.length != 2) {
                    continue;
                }
                int start = Integer.parseInt(rangeTokens[0], 16);
                int end = Integer.parseInt(rangeTokens[1], 16);
                Block b = new Block(start, end, namePart);
                blocks.add(b);
            }
        }
        // 可按 start 排序
        blocks.sort(Comparator.comparingInt(b -> b.start));
    }

    /**
     * 分析所有字体对所有区块的支持情况
     */
    private void analyzeFontSupport() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] fontNames = ge.getAvailableFontFamilyNames();

        System.out.printf("开始分析 %d 种字体对 %d 个Unicode区块的支持情况...%n",
                fontNames.length, blocks.size());

        for (String fontName : fontNames) {
            Font font = new Font(fontName, Font.PLAIN, 12);

            for (Block block : blocks) {
                if (checkBlockCoverage(font, block)) {
                    block.addSupportedFont(fontName);
                }
            }
        }

        System.out.println("字体支持分析完成！");
    }

    /**
     * 检查字体是否支持整个区块（抽样检查以提高性能）
     */
    private boolean checkBlockCoverage(Font font, Block block) {
        int blockSize = block.end - block.start + 1;

        // 对于小区块，检查所有字符
        if (blockSize <= 100) {
            for (int cp = block.start; cp <= block.end; cp++) {
                if (!font.canDisplay(cp)) {
                    return false;
                }
            }
            return true;
        }

        // 对于大区块，抽样检查（提高性能）
        int sampleCount = Math.min(50, blockSize / 20); // 抽样50个或5%的字符
        int step = Math.max(1, blockSize / sampleCount);

        for (int i = 0; i < blockSize; i += step) {
            int cp = block.start + i;
            if (cp > block.end) break;

            if (!font.canDisplay(cp)) {
                return false;
            }
        }

        // 额外检查开始和结束的字符
        return font.canDisplay(block.start) && font.canDisplay(block.end);
    }

    /**
     * 根据 codePoint 查找其所属区块名称
     */
    public String getBlockName(int codePoint) {
        Block block = findBlock(codePoint);
        return block != null ? block.name : null;
    }

    /**
     * 根据 codePoint 查找其所属区块
     */
    public Block findBlock(int codePoint) {
        int lo = 0, hi = blocks.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            Block b = blocks.get(mid);
            if (codePoint < b.start) {
                hi = mid - 1;
            } else if (codePoint > b.end) {
                lo = mid + 1;
            } else {
                return b;
            }
        }
        return null;
    }

    /**
     * 根据区块名称查找区块
     */
    public Block findBlock(String blockName) {
        return blocks.stream()
                .filter(b -> b.name.equals(blockName))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取所有区块
     */
    public List<Block> getAllBlocks() {
        return new ArrayList<>(blocks);
    }

    /**
     * 检查某个字体是否完整覆盖某个区块（使用已记录的结果）
     */
    public boolean checkFontCoverage(String fontName, String blockName) {
        Block block = findBlock(blockName);
        if (block == null) {
            throw new IllegalArgumentException("未知的区块: " + blockName);
        }
        return block.isFontSupported(fontName);
    }

    /**
     * 获取支持指定区块的所有字体
     */
    public List<String> getFontsSupportingBlock(String blockName) {
        Block block = findBlock(blockName);
        return block != null ? block.getSupportedFonts() : Collections.emptyList();
    }

    /**
     * 获取指定字体支持的所有区块名称
     */
    public List<String> getBlocksSupportedByFont(String fontName) {
        List<String> supportedBlocks = new ArrayList<>();
        for (Block block : blocks) {
            if (block.isFontSupported(fontName)) {
                supportedBlocks.add(block.name);
            }
        }
        return supportedBlocks;
    }

    /**
     * 检查所有字体和所有区块，输出结果（使用已记录的结果）
     */
    public void checkAllFontsAndBlocks() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] fontNames = ge.getAvailableFontFamilyNames();

        for (String fontName : fontNames) {
            List<String> supportedBlocks = getBlocksSupportedByFont(fontName);

            if (!supportedBlocks.isEmpty()) {
                System.out.printf("字体 [%s] 支持的区块：%s%n", fontName, supportedBlocks);
            } else {
                System.out.printf("字体 [%s] 没有完整支持任何区块%n", fontName);
            }
        }
    }

    /**
     * 打印区块的字符信息
     */
    public void printBlockCharacters(String blockName) {
        Block block = findBlock(blockName);
        if (block == null) {
            System.out.println("未找到区块: " + blockName);
            return;
        }

        System.out.printf("区块 '%s' 的字符范围: U+%04X - U+%04X (%d 个字符)%n",
                block.name, block.start, block.end, block.end - block.start + 1);
        System.out.printf("支持该区块的字体: %s%n", block.getSupportedFonts());

        int count = 0;
        for (int codePoint = block.start; codePoint <= block.end; codePoint++) {
            if (Character.isValidCodePoint(codePoint)) {
                char[] chars = Character.toChars(codePoint);
                System.out.printf("U+%04X: %s\t", codePoint, new String(chars));

                count++;
                if (count % 4 == 0) {
                    System.out.println();
                }
            }
        }

        if (count % 4 != 0) {
            System.out.println();
        }

        System.out.printf("%n总共打印了 %d 个字符%n", count);
    }

    /**
     * 生成支持情况报告
     */
    public void generateCoverageReport() {
        System.out.println("=== Unicode区块支持情况报告 ===");

        // 按支持字体数量排序
        List<Block> sortedBlocks = new ArrayList<>(blocks);
        sortedBlocks.sort((b1, b2) ->
                Integer.compare(b2.getSupportedFonts().size(), b1.getSupportedFonts().size()));

        for (Block block : sortedBlocks) {
            int supportedFonts = block.getSupportedFonts().size();
            System.out.printf("%s: %d种字体支持%n", block.name, supportedFonts);
        }

        // 统计信息
        long fullySupportedBlocks = sortedBlocks.stream()
                .filter(b -> !b.getSupportedFonts().isEmpty())
                .count();

        System.out.printf("%n统计信息: %d/%d 个区块有字体支持 (%.1f%%)%n",
                fullySupportedBlocks, blocks.size(),
                (fullySupportedBlocks * 100.0 / blocks.size()));
    }

}
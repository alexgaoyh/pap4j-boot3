package cn.net.pap.common.spider;

import cn.net.pap.common.spider.jsoup.HtmlTableToMarkdownConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HtmlTableToMarkdownConverter 全覆盖测试 —— 对应 table.html 的 35 个场景
 */
public class HtmlTableToMarkdownConverterTest {

    private static final Logger log = LoggerFactory.getLogger(HtmlTableToMarkdownConverterTest.class);

    // ================================================================
    // 场景 1: 基础标准表格
    // ================================================================
    @Test
    @DisplayName("场景1: 基础标准表格")
    public void testSimpleTable() {
        String html = """
                <table>
                  <tr><th>姓名</th><th>年龄</th></tr>
                  <tr><td>张三</td><td>25</td></tr>
                  <tr><td>李四</td><td>30</td></tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景1 基础表格:\n{}", md);

        String expected = """
                | 姓名 | 年龄 |
                | --- | --- |
                | 张三 | 25 |
                | 李四 | 30 |
                """;
        assertEquals(expected.trim(), md.trim());
    }

    // ================================================================
    // 场景 2: colspan 横向合并
    // ================================================================
    @Test
    @DisplayName("场景2: colspan 横向合并")
    public void testColspanTable() {
        String html = """
                <table>
                  <tr><th>项目</th><th colspan="2">数值范围</th></tr>
                  <tr><td>指标A</td><td>10</td><td>20</td></tr>
                  <tr><td>总计</td><td colspan="2">30</td></tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景2 colspan:\n{}", md);

        String expected = """
                | 项目 | 数值范围 | 数值范围 |
                | --- | --- | --- |
                | 指标A | 10 | 20 |
                | 总计 | 30 | 30 |
                """;
        assertEquals(expected.trim(), md.trim());
    }

    // ================================================================
    // 场景 3: rowspan 纵向合并
    // ================================================================
    @Test
    @DisplayName("场景3: rowspan 纵向合并")
    public void testRowspanTable() {
        String html = """
                <table>
                  <tr><th>部门</th><th>员工</th></tr>
                  <tr><td rowspan="2">研发部</td><td>张三</td></tr>
                  <tr><td>李四</td></tr>
                  <tr><td>销售部</td><td>王五</td></tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景3 rowspan:\n{}", md);

        String expected = """
                | 部门 | 员工 |
                | --- | --- |
                | 研发部 | 张三 |
                | 研发部 | 李四 |
                | 销售部 | 王五 |
                """;
        assertEquals(expected.trim(), md.trim());
    }

    // ================================================================
    // 场景 4: 混合跨行跨列与多级表头
    // ================================================================
    @Test
    @DisplayName("场景4: 混合跨行跨列与多级表头")
    public void testComplexSpanTable() {
        String html = """
                <table>
                  <tr><th rowspan="2">地区</th><th colspan="2">销售业绩</th></tr>
                  <tr><th>线上</th><th>线下</th></tr>
                  <tr><td rowspan="2">华东</td><td>100</td><td>200</td></tr>
                  <tr><td colspan="2">总计 300</td></tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景4 混合合并:\n{}", md);

        String expected = """
                | 地区 | 销售业绩/线上 | 销售业绩/线下 |
                | --- | --- | --- |
                | 华东 | 100 | 200 |
                | 华东 | 总计 300 | 总计 300 |
                """;
        assertEquals(expected.trim(), md.trim());
    }

    // ================================================================
    // 场景 5: rowspan + colspan 同时作用于同一个单元格
    // ================================================================
    @Test
    @DisplayName("场景5: rowspan + colspan 同时作用于同一个单元格")
    public void testRowspanAndColspanOnSameCell() {
        String html = """
                <table>
                  <tr><th>季度</th><th>线上</th><th>线下</th><th>海外</th></tr>
                  <tr><td>Q1</td><td>100</td><td>200</td><td>50</td></tr>
                  <tr><td>Q2</td><td rowspan="2" colspan="2">合并区</td><td>80</td></tr>
                  <tr><td>Q3</td><td>90</td></tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景5 rowspan+colspan 同单元格:\n{}", md);

        // 合并区 应平铺到 (row2,col1), (row2,col2), (row3,col1), (row3,col2)
        String expected = """
                | 季度 | 线上 | 线下 | 海外 |
                | --- | --- | --- | --- |
                | Q1 | 100 | 200 | 50 |
                | Q2 | 合并区 | 合并区 | 80 |
                | Q3 | 合并区 | 合并区 | 90 |
                """;
        assertEquals(expected.trim(), md.trim());
    }

    // ================================================================
    // 场景 6: rowspan + colspan 复杂网格交叉
    // ================================================================
    @Test
    @DisplayName("场景6: rowspan + colspan 复杂网格交叉")
    public void testComplexGridIntersection() {
        String html = """
                <table>
                  <tr><th colspan="2">科目</th><th>分数</th><th>排名</th></tr>
                  <tr><td rowspan="2">理科</td><td>数学</td><td>95</td><td rowspan="3">综合排名区</td></tr>
                  <tr><td>物理</td><td>88</td></tr>
                  <tr><td colspan="2">文科综合</td><td>82</td></tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景6 复杂网格交叉:\n{}", md);

        String expected = """
                | 科目 | 科目 | 分数 | 排名 |
                | --- | --- | --- | --- |
                | 理科 | 数学 | 95 | 综合排名区 |
                | 理科 | 物理 | 88 | 综合排名区 |
                | 文科综合 | 文科综合 | 82 | 综合排名区 |
                """;
        assertEquals(expected.trim(), md.trim());
    }

    // ================================================================
    // 场景 7: 表格嵌套（1层）
    // ================================================================
    @Test
    @DisplayName("场景7: 表格嵌套（1层）")
    public void testNestedTable() {
        String html = """
                <table>
                  <tr><th>一级表头A</th><th>一级表头B</th></tr>
                  <tr>
                    <td><table><tr><td>嵌套行1</td></tr><tr><td>嵌套行2</td></tr></table></td>
                    <td>外部单元格</td>
                  </tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景7 嵌套表格:\n{}", md);

        assertTrue(md.contains("一级表头A"));
        assertTrue(md.contains("嵌套行1 嵌套行2"));
        assertTrue(md.contains("外部单元格"));
    }

    // ================================================================
    // 场景 8: 多层嵌套表格（深度 > 1）
    // ================================================================
    @Test
    @DisplayName("场景8: 多层嵌套表格（深度 > 1）")
    public void testMultiLevelNestedTable() {
        String html = """
                <table>
                  <tr><th>外层表头</th></tr>
                  <tr>
                    <td>
                      <table>
                        <tr>
                          <td><table><tr><td>最深层</td></tr></table></td>
                          <td>中层单元格</td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景8 多层嵌套:\n{}", md);

        // 最外层表格结构完整，嵌套内容被平铺为纯文本
        assertTrue(md.contains("外层表头"));
        assertTrue(md.contains("最深层"));
        assertTrue(md.contains("中层单元格"));
        // 不应出现嵌套表格自身的 tr 干扰外层结构
        assertEquals(3, md.trim().split("\n").length); // header + separator + 1 data row
    }

    // ================================================================
    // 场景 9: 特殊符号与单元格内换行
    // ================================================================
    @Test
    @DisplayName("场景9: 特殊符号与单元格内换行")
    public void testSpecialCharacters() {
        String html = """
                <table>
                  <tr><th>名称</th><th>描述</th></tr>
                  <tr><td>商品A | 豪华版</td><td>第一行描述<br/>第二行描述<p>第三行段落</p></td></tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景9 特殊字符:\n{}", md);

        assertTrue(md.contains("商品A \\| 豪华版"));
        assertTrue(md.contains("第一行描述 <br> 第二行描述 <br> 第三行段落"));
    }

    // ================================================================
    // 场景 10: tfoot 在 tbody 之前定义（语义顺序重组）
    // ================================================================
    @Test
    @DisplayName("场景10: tfoot 在 tbody 之前定义")
    public void testTfootOrder() {
        String html = """
                <table>
                  <thead><tr><th>账目项目</th><th>收支金额</th></tr></thead>
                  <tfoot><tr><td>合计归档</td><td>1200 元</td></tr></tfoot>
                  <tbody>
                    <tr><td>购买书籍</td><td>200 元</td></tr>
                    <tr><td>购买硬件</td><td>1000 元</td></tr>
                  </tbody>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景10 tfoot 顺序:\n{}", md);

        String expected = """
                | 账目项目 | 收支金额 |
                | --- | --- |
                | 购买书籍 | 200 元 |
                | 购买硬件 | 1000 元 |
                | 合计归档 | 1200 元 |
                """;
        assertEquals(expected.trim(), md.trim());
    }

    // ================================================================
    // 场景 11: 多个 tbody 分组
    // ================================================================
    @Test
    @DisplayName("场景11: 多个 tbody 分组")
    public void testMultipleTbody() {
        String html = """
                <table>
                  <thead><tr><th>分组名</th><th>项目</th><th>金额</th></tr></thead>
                  <tbody>
                    <tr><td>收入</td><td>工资</td><td>8000</td></tr>
                    <tr><td>收入</td><td>奖金</td><td>2000</td></tr>
                  </tbody>
                  <tbody>
                    <tr><td>支出</td><td>房租</td><td>3000</td></tr>
                    <tr><td>支出</td><td>餐饮</td><td>1500</td></tr>
                  </tbody>
                  <tfoot><tr><td colspan="2">结余</td><td>5500</td></tr></tfoot>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景11 多个 tbody:\n{}", md);

        String expected = """
                | 分组名 | 项目 | 金额 |
                | --- | --- | --- |
                | 收入 | 工资 | 8000 |
                | 收入 | 奖金 | 2000 |
                | 支出 | 房租 | 3000 |
                | 支出 | 餐饮 | 1500 |
                | 结余 | 结余 | 5500 |
                """;
        assertEquals(expected.trim(), md.trim());
    }

    // ================================================================
    // 场景 12: 隐藏元素过滤 —— 全覆盖 hidden / display:none / visibility:hidden / class隐藏
    // ================================================================
    @Test
    @DisplayName("场景12: 隐藏元素过滤全覆盖")
    public void testHiddenElements() {
        String html = """
                <table>
                  <tr>
                    <th>产品名称</th>
                    <th>售价</th>
                    <th style="display:none">隐藏进货成本</th>
                    <th style="visibility:hidden">隐藏备注</th>
                  </tr>
                  <tr>
                    <td>办公椅</td>
                    <td>499 元</td>
                    <td style="display:none">150 元</td>
                    <td style="visibility:hidden">热销款</td>
                  </tr>
                  <tr hidden>
                    <td>已下架产品</td><td>0 元</td><td>0 元</td><td>-</td>
                  </tr>
                  <tr style="display:none">
                    <td>已停产产品</td><td>0 元</td><td>0 元</td><td>-</td>
                  </tr>
                  <tr>
                    <td>升降桌</td>
                    <td>1299 元</td>
                    <td hidden>400 元</td>
                    <td style="visibility:hidden">新品</td>
                  </tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景12 隐藏元素全覆盖:\n{}", md);

        // display:none 列被过滤、hidden 行被过滤、visibility:hidden 被过滤
        String expected = """
                | 产品名称 | 售价 |
                | --- | --- |
                | 办公椅 | 499 元 |
                | 升降桌 | 1299 元 |
                """;
        assertEquals(expected.trim(), md.trim());
    }

    // ================================================================
    // 场景 13: 视觉合并前向填充 —— 含 "同上" / "同前" / "-" / 空值
    // ================================================================
    @Test
    @DisplayName("场景13: 视觉合并——原样保留，不做填充")
    public void testVisualMerge() {
        String html = """
                <table>
                  <tr><th>主类别</th><th>子项目</th><th>数量</th></tr>
                  <tr><td>水果类</td><td>苹果</td><td>10 个</td></tr>
                  <tr><td></td><td>香蕉</td><td>5 根</td></tr>
                  <tr><td>同上</td><td>橘子</td><td>8 个</td></tr>
                  <tr><td>同前</td><td>葡萄</td><td>3 串</td></tr>
                  <tr><td>-</td><td>西瓜</td><td>1 个</td></tr>
                  <tr><td>蔬菜类</td><td>西红柿</td><td>2 斤</td></tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景13 视觉合并:\n{}", md);

        // 不做前向填充：空值、同上、同前、- 全部原样保留
        String expected = """
                | 主类别 | 子项目 | 数量 |
                | --- | --- | --- |
                | 水果类 | 苹果 | 10 个 |
                |  | 香蕉 | 5 根 |
                | 同上 | 橘子 | 8 个 |
                | 同前 | 葡萄 | 3 串 |
                | - | 西瓜 | 1 个 |
                | 蔬菜类 | 西红柿 | 2 斤 |
                """;
        assertEquals(expected.trim(), md.trim());
    }

    // ================================================================
    // 场景 14: 纯 td 表格（无任何 th）
    // ================================================================
    @Test
    @DisplayName("场景14: 纯 td 表格（无 th）—— 首行自动成为表头")
    public void testAllTdTable() {
        String html = """
                <table>
                  <tr><td>张三</td><td>25</td><td>北京</td></tr>
                  <tr><td>李四</td><td>30</td><td>上海</td></tr>
                  <tr><td>王五</td><td>28</td><td>广州</td></tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景14 纯td表格:\n{}", md);

        // 探测不到 th，默认第一行作为表头
        assertTrue(md.contains("张三"));
        assertTrue(md.contains("北京"));
        assertTrue(md.contains("广州"));
        // 首行应出现在分隔线上方
        String[] lines = md.trim().split("\n");
        assertTrue(lines[0].contains("张三"));
        assertTrue(lines[1].contains("---"));
        assertTrue(lines[2].contains("李四"));
    }

    // ================================================================
    // 场景 15: th scope="row" 行标题
    // ================================================================
    @Test
    @DisplayName("场景15: th scope=row 行标题")
    public void testThScopeRow() {
        String html = """
                <table>
                  <tr><th></th><th scope="col">Q1</th><th scope="col">Q2</th><th scope="col">Q3</th></tr>
                  <tr><th scope="row">线上</th><td>100</td><td>200</td><td>150</td></tr>
                  <tr><th scope="row">线下</th><td>300</td><td>250</td><td>280</td></tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景15 th scope=row:\n{}", md);

        // 第一行全是 th → 表头行；后续行混有 th+td → 数据行，th 保留为行标题
        String expected = """
                |  | Q1 | Q2 | Q3 |
                | --- | --- | --- | --- |
                | 线上 | 100 | 200 | 150 |
                | 线下 | 300 | 250 | 280 |
                """;
        assertEquals(expected.trim(), md.trim());
    }

    // ================================================================
    // 场景 16: 表头行中混入 td（辅助说明行）
    // ================================================================
    @Test
    @DisplayName("场景16: 表头行混入 td 辅助说明")
    public void testTdMixedInHeaderRow() {
        String html = """
                <table>
                  <tr><th>地区</th><th>销售额</th><th>同比增长</th></tr>
                  <tr><td colspan="3">(单位：万元 / 统计周期：2024年)</td></tr>
                  <tr><td>华东</td><td>5000</td><td>12%</td></tr>
                  <tr><td>华南</td><td>3200</td><td>8%</td></tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景16 表头混入td:\n{}", md);

        // 第二行含 td → 表头检测在 row 1 终止；headerRowCount = 1
        // 辅助说明行成为第一条数据行
        String expected = """
                | 地区 | 销售额 | 同比增长 |
                | --- | --- | --- |
                | (单位：万元 / 统计周期：2024年) | (单位：万元 / 统计周期：2024年) | (单位：万元 / 统计周期：2024年) |
                | 华东 | 5000 | 12% |
                | 华南 | 3200 | 8% |
                """;
        assertEquals(expected.trim(), md.trim());
    }

    // ================================================================
    // 场景 17: caption 表格标题
    // ================================================================
    @Test
    @DisplayName("场景17: caption 表格标题")
    public void testCaptionTable() {
        String html = """
                <table>
                  <caption>2024 年度各部门业绩汇总表</caption>
                  <tr><th>部门</th><th>年度营收</th><th>达成率</th></tr>
                  <tr><td>营销部</td><td>1200 万</td><td>105%</td></tr>
                  <tr><td>研发部</td><td>800 万</td><td>98%</td></tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景17 caption:\n{}", md);

        // 当前 caption 不纳入 Markdown 输出（GFM 表格无 caption 概念）
        // 但表格自身结构应保持完好
        assertTrue(md.contains("部门"));
        assertTrue(md.contains("营销部"));
        assertTrue(md.contains("研发部"));
        // caption 不应出现在输出中
        assertFalse(md.contains("2024 年度各部门业绩汇总表"));
    }

    // ================================================================
    // 场景 18: 单元格内 a 链接
    // ================================================================
    @Test
    @DisplayName("场景18: 单元格内 a 链接")
    public void testLinksInCells() {
        String html = """
                <table>
                  <tr><th>商品ID</th><th>商品名称</th><th>详情页</th></tr>
                  <tr><td>001</td><td><a href="/product/001">机械键盘 (青轴)</a></td><td><a href="/detail/001" target="_blank">查看详情</a></td></tr>
                  <tr><td>002</td><td><a href="/product/002" title="无线蓝牙鼠标">无线鼠标</a></td><td><a href="/detail/002">查看详情</a></td></tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景18 链接:\n{}", md);

        // 链接文本保留，href 被丢弃
        assertTrue(md.contains("机械键盘 (青轴)"));
        assertTrue(md.contains("查看详情"));
        assertTrue(md.contains("无线鼠标"));
        // href 不出现在 Markdown 中
        assertFalse(md.contains("/product/001"));
    }

    // ================================================================
    // 场景 19: 单元格内 img 图片
    // ================================================================
    @Test
    @DisplayName("场景19: 单元格内 img 图片")
    public void testImagesInCells() {
        String html = """
                <table>
                  <tr><th>序号</th><th>图标</th><th>状态</th></tr>
                  <tr><td>1</td><td><img src="https://via.placeholder.com/16" alt="正常" /> 正常</td><td>运行中</td></tr>
                  <tr><td>2</td><td><img src="https://via.placeholder.com/16" alt="警告" /> 警告</td><td>需要注意</td></tr>
                  <tr><td>3</td><td>纯文字标识</td><td>已停止</td></tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景19 图片:\n{}", md);

        // img 的 alt 文本不被 Jsoup.text() 提取（仅取文本节点），图片 URL 丢弃
        // 单元格内容 <img alt="正常" /> 正常 → text() 仅返回 "正常"
        assertTrue(md.contains("正常"));
        assertTrue(md.contains("警告"));
        assertTrue(md.contains("纯文字标识"));
        assertFalse(md.contains("placeholder.com"));
    }

    // ================================================================
    // 场景 20: &nbsp; 占位 vs 真正空单元格
    // ================================================================
    @Test
    @DisplayName("场景20: &nbsp; vs 空单元格")
    public void testNbspVsEmptyCells() {
        String html = """
                <table>
                  <tr><th>ID</th><th>描述</th><th>备注</th></tr>
                  <tr><td>1</td><td>有描述的项</td><td>&nbsp;</td></tr>
                  <tr><td>2</td><td></td><td>有备注</td></tr>
                  <tr><td>3</td><td>&nbsp;&nbsp;&nbsp;</td><td>&nbsp;</td></tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景20 nbsp vs 空:\n{}", md);

        // &nbsp; 被 Jsoup.text()→空格→trim()→空串，与真实空单元格无法区分
        // 这是已知局限；验证两行输出列数一致
        String[] lines = md.trim().split("\n");
        // 应至少有 header + separator + 3 data rows = 5 lines
        assertTrue(lines.length >= 5);
        // 每行应有 3 列（检查管道符数量：每行应有 4 个 |）
        for (String line : lines) {
            long pipeCount = line.chars().filter(c -> c == '|').count();
            assertEquals(4, pipeCount, "每行应有 4 个管道符: " + line);
        }
    }

    // ================================================================
    // 场景 21: 单元格内包含 ul / ol 列表
    // ================================================================
    @Test
    @DisplayName("场景21: 单元格内 ul/ol 列表")
    public void testListsInCells() {
        String html = """
                <table>
                  <tr><th>功能模块</th><th>核心能力</th><th>版本要求</th></tr>
                  <tr>
                    <td>用户系统</td>
                    <td><ul><li>注册 / 登录</li><li>OAuth 第三方接入</li><li>权限角色管理</li></ul></td>
                    <td>v2.0+</td>
                  </tr>
                  <tr>
                    <td>支付模块</td>
                    <td><ol><li>微信支付</li><li>支付宝</li><li>银联</li></ol></td>
                    <td>v2.1+</td>
                  </tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景21 列表:\n{}", md);

        // 列表项被 Jsoup.text() 合并为空格分隔的纯文本（丢失列表语义）
        assertTrue(md.contains("注册 / 登录"));
        assertTrue(md.contains("OAuth 第三方接入"));
        assertTrue(md.contains("微信支付"));
        // 每行应有 4 个管道符（3 列）
        for (String line : md.trim().split("\n")) {
            assertEquals(4, line.chars().filter(c -> c == '|').count());
        }
    }

    // ================================================================
    // 场景 22: 单元格内 code / pre 代码块
    // ================================================================
    @Test
    @DisplayName("场景22: 单元格内 code/pre 代码块")
    public void testCodeAndPreInCells() {
        String html = """
                <table>
                  <tr><th>参数名</th><th>类型</th><th>示例值</th><th>说明</th></tr>
                  <tr>
                    <td><code>pageSize</code></td>
                    <td><code>int</code></td>
                    <td><code>20</code></td>
                    <td>每页条数，范围 <code>[1, 100]</code></td>
                  </tr>
                  <tr>
                    <td><code>sortBy</code></td>
                    <td><code>String</code></td>
                    <td><code>"createdAt"</code></td>
                    <td><pre>可选值:\n  - createdAt\n  - updatedAt</pre></td>
                  </tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景22 code/pre:\n{}", md);

        // code 文本保留，pre 格式丢失但内容保留
        assertTrue(md.contains("pageSize"));
        assertTrue(md.contains("createdAt"));
        assertTrue(md.contains("updatedAt"));
    }

    // ================================================================
    // 场景 23: 单元格内联格式化 b / i / strong / em / span
    // ================================================================
    @Test
    @DisplayName("场景23: 内联格式化标签")
    public void testInlineFormatting() {
        String html = """
                <table>
                  <tr><th>项目</th><th>状态</th><th>说明</th></tr>
                  <tr><td><b>紧急需求</b></td><td><strong>进行中</strong></td><td><i>截止日期：2024-12-31</i></td></tr>
                  <tr><td><em>优化项</em></td><td><span style="color:green">已完成</span></td><td><b><i>无需关注</i></b></td></tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景23 内联格式化:\n{}", md);

        // 格式化标签被剥离，纯文本保留
        assertTrue(md.contains("紧急需求"));
        assertTrue(md.contains("进行中"));
        assertTrue(md.contains("截止日期：2024-12-31"));
        assertTrue(md.contains("优化项"));
        assertTrue(md.contains("已完成"));
        assertTrue(md.contains("无需关注"));
    }

    // ================================================================
    // 场景 24: 不规则行 —— 某行缺列（非 colspan 所致）
    // ================================================================
    @Test
    @DisplayName("场景24: 不规则行缺列")
    public void testMissingCells() {
        String html = """
                <table>
                  <tr><th>A</th><th>B</th><th>C</th><th>D</th></tr>
                  <tr><td>A1</td><td>B1</td><td>C1</td><td>D1</td></tr>
                  <tr><td>A2</td><td>B2</td></tr>
                  <tr><td colspan="2">A3跨2</td><td>B3</td><td>C3</td></tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景24 缺列:\n{}", md);

        // 短行末尾列置空，maxCols 保持 4
        String expected = """
                | A | B | C | D |
                | --- | --- | --- | --- |
                | A1 | B1 | C1 | D1 |
                | A2 | B2 |  |  |
                | A3跨2 | A3跨2 | B3 | C3 |
                """;
        assertEquals(expected.trim(), md.trim());
    }

    // ================================================================
    // 场景 25: colspan 越界 —— colspan 宣告值 > 剩余列数
    // ================================================================
    @Test
    @DisplayName("场景25: colspan 越界")
    public void testColspanOverflow() {
        String html = """
                <table>
                  <tr><th>名称</th><th>属性1</th><th>属性2</th></tr>
                  <tr><td>条目A</td><td colspan="3">超宽合并（colspan=3 但只剩2列空间）</td></tr>
                  <tr><td>条目B</td><td>X</td><td>Y</td></tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景25 colspan越界:\n{}", md);

        // colspan 超出列数时网格自动扩展，maxCols 增大
        String expected = """
                | 名称 | 属性1 | 属性2 |  |
                | --- | --- | --- | --- |
                | 条目A | 超宽合并（colspan=3 但只剩2列空间） | 超宽合并（colspan=3 但只剩2列空间） | 超宽合并（colspan=3 但只剩2列空间） |
                | 条目B | X | Y |  |
                """;
        assertEquals(expected.trim(), md.trim());
    }

    // ================================================================
    // 场景 26: rowspan / colspan 异常值（0 / 超大值）
    // ================================================================
    @Test
    @DisplayName("场景26: rowspan/colspan 异常值")
    public void testAbnormalSpanValues() {
        String html = """
                <table>
                  <tr><th>类别</th><th>子项</th><th>值</th></tr>
                  <tr><td rowspan="0">rowspan=0</td><td>子A</td><td>100</td></tr>
                  <tr><td>子B</td><td>200</td></tr>
                  <tr><td colspan="0">colspan=0</td><td>-</td><td>-</td></tr>
                  <tr><td rowspan="999">超大rowspan</td><td>测试1</td><td>X</td></tr>
                  <tr><td>测试2</td><td>Y</td></tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景26 异常span值:\n{}", md);

        // 0 值应被钳制为 1，避免数据丢失
        assertTrue(md.contains("rowspan=0"), "rowspan=0 的单元格内容应保留");
        assertTrue(md.contains("colspan=0"), "colspan=0 的单元格内容应保留");
        assertTrue(md.contains("超大rowspan"), "超大 rowspan 的单元格内容应保留");
    }

    // ================================================================
    // 场景 27: colgroup / col 列组定义
    // ================================================================
    @Test
    @DisplayName("场景27: colgroup/col 列组定义")
    public void testColgroupCol() {
        String html = """
                <table>
                  <colgroup>
                    <col style="width: 100px" />
                    <col style="width: 80px" />
                  </colgroup>
                  <tr><th>名称</th><th>状态</th></tr>
                  <tr><td>节点A</td><td>在线</td></tr>
                  <tr><td>节点B</td><td>离线</td></tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景27 colgroup:\n{}", md);

        // colgroup/col 不参与 grid 构建，表格结构应正常
        String expected = """
                | 名称 | 状态 |
                | --- | --- |
                | 节点A | 在线 |
                | 节点B | 离线 |
                """;
        assertEquals(expected.trim(), md.trim());
    }

    // ================================================================
    // 场景 28: table 自身 hidden 属性（整表隐藏）
    // ================================================================
    @Test
    @DisplayName("场景28: table 自身 hidden 属性")
    public void testTableHiddenAttribute() {
        String html = """
                <table hidden>
                  <tr><th>隐藏表头</th><th>隐藏列2</th></tr>
                  <tr><td>不可见数据</td><td>不可见数据</td></tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景28 table hidden:\n{}", md);

        assertEquals("", md, "整表 hidden 应返回空字符串");
    }

    // ================================================================
    // 场景 29: table 自身 display:none（整表隐藏）
    // ================================================================
    @Test
    @DisplayName("场景29: table 自身 style=display:none")
    public void testTableDisplayNone() {
        String html = """
                <table style="display:none">
                  <tr><th>隐藏表头</th><th>隐藏列2</th></tr>
                  <tr><td>不可见数据</td><td>不可见数据</td></tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景29 table display:none:\n{}", md);

        assertEquals("", md, "整表 display:none 应返回空字符串");
    }

    // ================================================================
    // 场景 30: 单元格内嵌 HTML 实体字符
    // ================================================================
    @Test
    @DisplayName("场景30: HTML 实体字符")
    public void testHtmlEntities() {
        String html = """
                <table>
                  <tr><th>符号名称</th><th>字符</th></tr>
                  <tr><td>与号</td><td>&amp;</td></tr>
                  <tr><td>小于/大于</td><td>&lt;div&gt;</td></tr>
                  <tr><td>版权</td><td>&copy; 2024</td></tr>
                  <tr><td>货币</td><td>&yen; 100 &euro; 20</td></tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景30 实体字符:\n{}", md);

        // Jsoup.text() 将实体解码为原始字符
        assertTrue(md.contains("&"));
        assertTrue(md.contains("<div>"));
        assertTrue(md.contains("© 2024"));
        assertTrue(md.contains("¥ 100"));
    }

    // ================================================================
    // 场景 31: 单元格仅包含空白字符
    // ================================================================
    @Test
    @DisplayName("场景31: 纯空白单元格")
    public void testWhitespaceOnlyCells() {
        String html = """
                <table>
                  <tr><th>编号</th><th>内容</th><th>状态</th></tr>
                  <tr><td>1</td><td>正常内容</td><td>   </td></tr>
                  <tr><td>2</td><td>\s\t\n</td><td>已确认</td></tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景31 空白单元格:\n{}", md);

        // 纯空白单元格 trim 后为空
        String expected = """
                | 编号 | 内容 | 状态 |
                | --- | --- | --- |
                | 1 | 正常内容 |  |
                | 2 |  | 已确认 |
                """;
        assertEquals(expected.trim(), md.trim());
    }

    // ================================================================
    // 场景 32: 斜线表头 —— 纯文本 "/" 分隔
    // ================================================================
    @Test
    @DisplayName("场景32: 斜线表头 —— 纯文本 / 分隔")
    public void testSlashHeaderPlainText() {
        String html = """
                <table>
                  <tr><th>科目 / 时间</th><th>上半年</th><th>下半年</th></tr>
                  <tr><td>收入</td><td>100</td><td>200</td></tr>
                  <tr><td>支出</td><td>80</td><td>150</td></tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景32 斜线表头纯文本:\n{}", md);

        // "/" 作为普通文本保留
        String expected = """
                | 科目 / 时间 | 上半年 | 下半年 |
                | --- | --- | --- |
                | 收入 | 100 | 200 |
                | 支出 | 80 | 150 |
                """;
        assertEquals(expected.trim(), md.trim());
    }

    // ================================================================
    // 场景 33: 斜线表头 —— CSS 对角线 + 文字对角定位
    // ================================================================
    @Test
    @DisplayName("场景33: 斜线表头 —— CSS 对角线定位")
    public void testSlashHeaderCssDiagonal() {
        String html = """
                <table>
                  <tr>
                    <th>
                      <span class="top-right">时间</span>
                      <span class="bottom-left">科目</span>
                    </th>
                    <th>上半年</th>
                    <th>下半年</th>
                  </tr>
                  <tr><td>收入</td><td>100</td><td>200</td></tr>
                  <tr><td>支出</td><td>80</td><td>150</td></tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景33 CSS斜线表头:\n{}", md);

        // 两个 span 的文字被 Jsoup.text() 合并为一行，空间关系丢失
        assertTrue(md.contains("时间"));
        assertTrue(md.contains("科目"));
        // 表格结构应正常
        assertTrue(md.contains("上半年"));
        assertTrue(md.contains("收入"));
    }

    // ================================================================
    // 场景 34: 斜线表头 —— 多级 "/"（三维信息）
    // ================================================================
    @Test
    @DisplayName("场景34: 斜线表头 —— 多级 / 分隔三维信息")
    public void testSlashHeaderMultiSlash() {
        String html = """
                <table>
                  <tr><th>部门 / 季度 / 指标</th><th>Q1</th><th>Q2</th><th>Q3</th><th>Q4</th></tr>
                  <tr><td>研发 / 营收 / 达成率</td><td>80%</td><td>90%</td><td>95%</td><td>100%</td></tr>
                  <tr><td>销售 / 营收 / 达成率</td><td>70%</td><td>85%</td><td>88%</td><td>92%</td></tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景34 多级斜线:\n{}", md);

        // 多级 "/" 作为普通文本保留
        assertTrue(md.contains("部门 / 季度 / 指标"));
        assertTrue(md.contains("研发 / 营收 / 达成率"));
        assertTrue(md.contains("销售 / 营收 / 达成率"));
    }

    // ================================================================
    // 场景 35: 斜线表头 —— CSS 双斜线三区对角
    // ================================================================
    @Test
    @DisplayName("场景35: 斜线表头 —— CSS 双斜线三区对角")
    public void testSlashHeaderCssDoubleDiagonal() {
        String html = """
                <table>
                  <tr>
                    <th>
                      <span class="top">月份</span>
                      <span class="mid">指标</span>
                      <span class="bottom">部门</span>
                    </th>
                    <th>1月</th>
                    <th>2月</th>
                    <th>3月</th>
                  </tr>
                  <tr><td>研发部</td><td>达标</td><td>超标</td><td>达标</td></tr>
                  <tr><td>销售部</td><td>未达标</td><td>达标</td><td>超标</td></tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("场景35 CSS双斜线:\n{}", md);

        // 三个 span 的文字被合并，空间关系丢失
        assertTrue(md.contains("月份"));
        assertTrue(md.contains("指标"));
        assertTrue(md.contains("部门"));
        assertTrue(md.contains("研发部"));
        assertTrue(md.contains("达标"));
    }

    // ================================================================
    // 边界用例: 空 / null / 无表格
    // ================================================================
    @Test
    @DisplayName("边界: null / 空字符串 / 无表格")
    public void testEmptyTable() {
        assertEquals("", HtmlTableToMarkdownConverter.convert(null));
        assertEquals("", HtmlTableToMarkdownConverter.convert("   "));
        assertEquals("", HtmlTableToMarkdownConverter.convert("<div>没有表格</div>"));
    }

    @Test
    @DisplayName("边界: style=visibility:hidden 在 table 上")
    public void testTableVisibilityHidden() {
        String html = """
                <table style="visibility:hidden">
                  <tr><th>列1</th></tr>
                  <tr><td>数据</td></tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        assertEquals("", md, "整表 visibility:hidden 应返回空字符串");
    }

    @Test
    @DisplayName("边界: 表格仅含 thead 无 body")
    public void testTableWithOnlyThead() {
        String html = """
                <table>
                  <thead><tr><th>A</th><th>B</th></tr></thead>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("仅 thead:\n{}", md);
        // 全为 th，headerRowCount = 1，data rows = 0
        assertTrue(md.contains("A"));
        assertTrue(md.contains("---"));
    }

    @Test
    @DisplayName("边界: colgroup 含 class 隐藏列（已知局限：不检测class）")
    public void testColgroupWithClassHidden() {
        String html = """
                <table>
                  <colgroup>
                    <col />
                    <col class="hidden-col" />
                  </colgroup>
                  <tr><th>名称</th><th class="hidden-col">密钥</th></tr>
                  <tr><td>节点A</td><td class="hidden-col">sk-xxxx</td></tr>
                </table>
                """;
        String md = HtmlTableToMarkdownConverter.convert(html);
        log.info("colgroup class隐藏:\n{}", md);

        // CSS class 级别的隐藏无法检测，class="hidden-col" 列仍会输出
        // 这是已知局限，验证不出错即可
        assertTrue(md.contains("名称"));
        // 当前行为：class 隐藏的列也出现在输出中（已知局限）
    }
}

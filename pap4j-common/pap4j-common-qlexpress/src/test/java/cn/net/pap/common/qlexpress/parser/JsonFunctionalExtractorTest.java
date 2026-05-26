package cn.net.pap.common.qlexpress.parser;

import cn.net.pap.common.qlexpress.parser.dto.FunctionalExtractionResultDTO;
import cn.net.pap.common.qlexpress.parser.dto.FunctionalExtractionRuleDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonFunctionalExtractorTest {

    private final JsonFunctionalExtractor extractor = new JsonFunctionalExtractor();

    @Test
    public void testFunctionalExtraction() {
        String jsonData = """
                {
                  "orderId": "ORD-123",
                  "amount": 500,
                  "user": {
                    "name": "zhangsan",
                    "level": "VIP"
                  },
                  "items": [
                    {"sku": "SKU-A", "price": 200},
                    {"sku": "SKU-B", "price": 300}
                  ]
                }
                """;

        List<FunctionalExtractionRuleDTO> rules = List.of(
                new FunctionalExtractionRuleDTO("id", "$.orderId"),
                new FunctionalExtractionRuleDTO("userName", "json.user.name"),
                new FunctionalExtractionRuleDTO("level", "json.user.level"),
                new FunctionalExtractionRuleDTO("isHighAmount", "json.amount > 300 ? 'YES' : 'NO'"),
                new FunctionalExtractionRuleDTO("firstItemSku", "$.items[0].sku"),
                new FunctionalExtractionRuleDTO("firstItemSkuViaFunc", "JSON_PATH(json, '$.items[0].sku')")
        );

        FunctionalExtractionResultDTO result = extractor.extract(jsonData, rules);
        Map<String, Object> fields = result.extractedFields();

        assertEquals("ORD-123", fields.get("id"));
        assertEquals("zhangsan", fields.get("userName"));
        assertEquals("VIP", fields.get("level"));
        assertEquals("YES", fields.get("isHighAmount"));
        assertEquals("SKU-A", fields.get("firstItemSku"));
        assertEquals("SKU-A", fields.get("firstItemSkuViaFunc"));
    }
}

package cn.net.pap.common.datastructure.comment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * 解析 实体类，返回 JSON 格式，含 comment 注释部分 javadoc.
 */
public class JsonWithCommentsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testEntityToJsonWithComments() throws Exception {
        File sourceFile = new File("D:\\ideaprojects\\pap4j-boot3\\pap4j-common\\pap4j-common-datastructure\\src\\main\\java\\cn\\net\\pap\\common\\datastructure\\catalog\\dto\\CatalogTreeDTO.java");

        CompilationUnit cu = StaticJavaParser.parse(sourceFile);
        TypeDeclaration<?> clazz = cu.getType(0);

        Set<String> processing = new HashSet<>();
        ObjectNode jsonNode = buildJsonForClass(clazz, sourceFile.getParentFile(), processing);
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode));

    }

    private ObjectNode buildJsonForClass(TypeDeclaration<?> clazz, File sourceDir, Set<String> processing) throws Exception {
        String className = clazz.getNameAsString();

        // 避免自关联无限递归
        if (processing.contains(className)) {
            return mapper.createObjectNode(); // 或者返回 null / 空对象
        }

        processing.add(className);

        ObjectNode rootNode = mapper.createObjectNode();
        ObjectNode commentNode = mapper.createObjectNode();

        for (FieldDeclaration field : clazz.getFields()) {
            String fieldName = field.getVariable(0).getNameAsString();
            field.getJavadoc().ifPresent(javadoc -> commentNode.put(fieldName, javadoc.getDescription().toText()));

            rootNode.set(fieldName, defaultValueForType(field.getVariable(0).getType(), sourceDir, processing));
        }

        rootNode.set("@comment", commentNode);
        processing.remove(className);
        return rootNode;
    }

    private JsonNode defaultValueForType(com.github.javaparser.ast.type.Type type, File sourceDir, Set<String> processing) throws Exception {
        String typeName = type.asString();

        switch (typeName) {
            case "String" -> {
                return mapper.getNodeFactory().textNode("");
            }
            case "int", "Integer", "short", "Short", "byte", "Byte", "long", "Long" -> {
                return mapper.getNodeFactory().numberNode(0);
            }
            case "float", "Float", "double", "Double" -> {
                return mapper.getNodeFactory().numberNode(0.0);
            }
            case "boolean", "Boolean" -> {
                return mapper.getNodeFactory().booleanNode(false);
            }
            case "LocalDate", "LocalDateTime", "Date" -> {
                return mapper.getNodeFactory().textNode("1970-01-01T00:00:00");
            }
            case "BigDecimal", "BigInteger" -> {
                return mapper.getNodeFactory().numberNode(0);
            }
            default -> {
                if (type.isClassOrInterfaceType()) {
                    ClassOrInterfaceType ciType = type.asClassOrInterfaceType();
                    String name = ciType.getNameAsString();

                    // 集合类型
                    if (name.equals("List") || name.equals("Set")) {
                        ArrayNode arrayNode = mapper.createArrayNode();
                        if (!ciType.getTypeArguments().isEmpty()) {
                            com.github.javaparser.ast.type.Type genericType = ciType.getTypeArguments().get().get(0);
                            arrayNode.add(defaultValueForType(genericType, sourceDir, processing));
                        }
                        return arrayNode;
                    } else if (name.equals("Map")) {
                        ObjectNode mapNode = mapper.createObjectNode();
                        if (!ciType.getTypeArguments().isEmpty()) {
                            com.github.javaparser.ast.type.Type valueType = ciType.getTypeArguments().get().get(1);
                            mapNode.set("key", defaultValueForType(valueType, sourceDir, processing));
                        }
                        return mapNode;
                    } else {
                        // 自定义对象类型
                        File classFile = new File(sourceDir, name + ".java");
                        if (classFile.exists()) {
                            CompilationUnit cu = StaticJavaParser.parse(classFile);
                            TypeDeclaration<?> clazz = cu.getType(0);
                            return buildJsonForClass(clazz, sourceDir, processing);
                        } else {
                            return mapper.getNodeFactory().textNode("");
                        }
                    }
                } else {
                    return mapper.getNodeFactory().textNode("");
                }
            }
        }
    }

}

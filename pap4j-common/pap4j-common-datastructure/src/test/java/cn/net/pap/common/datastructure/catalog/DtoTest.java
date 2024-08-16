package cn.net.pap.common.datastructure.catalog;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import cn.net.pap.common.datastructure.catalog.dto.CatalogDTO;
import cn.net.pap.common.datastructure.catalog.dto.CatalogTreeDTO;
import cn.net.pap.common.datastructure.fst.ValueLocationDTO;
import cn.net.pap.common.datastructure.meanShift.PointX;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DtoTest {

    @Test
    public void testDtoSettersAndGetters() throws Exception {
        Class<?> catalogDTOClass = CatalogDTO.class;
        dtoTest(catalogDTOClass, catalogDTOClass.getDeclaredConstructor().newInstance());

        Class<?> catalogTreeDTOClass = CatalogTreeDTO.class;
        dtoTest(catalogTreeDTOClass, catalogTreeDTOClass.getDeclaredConstructor().newInstance());

        Class<?> valueLocationDTOClass = ValueLocationDTO.class;
        dtoTest(valueLocationDTOClass, valueLocationDTOClass.getDeclaredConstructor().newInstance());

        Class<?> pointXClass = PointX.class;
        dtoTest(pointXClass, pointXClass.getDeclaredConstructor().newInstance());
    }

    private void dtoTest(Class<?> clazz, Object dtoInstance) throws Exception{

        String timestamp = "1";

        // 遍历所有方法
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().startsWith("set")) {
                // 获取属性名
                String propertyName = method.getName().substring(3);

                // 获取setter方法参数类型
                Class<?> parameterType = method.getParameterTypes()[0];
                Object testValue = null;

                // 根据参数类型设置值
                if (parameterType == String.class) {
                    testValue = "TestValue_" + timestamp;
                } else if (parameterType == int.class || parameterType == Integer.class) {
                    testValue = Integer.parseInt(timestamp); // 取时间戳的前8位作为整数
                } else if (parameterType == long.class || parameterType == Long.class) {
                    testValue = Long.parseLong(timestamp);
                } else if (parameterType == LocalDateTime.class) {
                    testValue = LocalDateTime.now();
                }else if (parameterType == double.class || parameterType == Double.class) {
                    testValue = Double.parseDouble(timestamp);
                }
                // 可以根据需要扩展更多类型的判断

                // 调用setter方法
                method.invoke(dtoInstance, testValue);

                // 查找对应的getter方法
                Method getterMethod = clazz.getMethod("get" + propertyName);

                // 获取属性值
                Object resultValue = getterMethod.invoke(dtoInstance);

                // 验证getter返回值是否与setter设置的值一致
                assertEquals(testValue, resultValue, "Getter and Setter for " + propertyName + " failed");
            }
        }
    }


}

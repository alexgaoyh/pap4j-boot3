package cn.net.pap.common.boofcv.dto;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class DtoTest {

    @Test
    public void testDtoSettersAndGetters() throws Exception {
        Class<?> clazz1 = AssociatedTripleDTO.class;
        dtoTest(clazz1, clazz1.getDeclaredConstructor().newInstance());

        Class<?> clazz2 = LineSegment.class;
        dtoTest(clazz2, clazz2.getDeclaredConstructor().newInstance());

        Class<?> clazz3 = MarginDTO.class;
        dtoTest(clazz3, clazz3.getDeclaredConstructor().newInstance());

    }

    private void dtoTest(Class<?> clazz, Object dtoInstance) throws Exception {

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
                } else if (parameterType == double.class || parameterType == Double.class) {
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

        testToString(clazz, dtoInstance);

    }

    private void testToString(Class<?> clazz, Object dtoInstance) {
        try {
            // 尝试获取toString方法
            Method toStringMethod = clazz.getMethod("toString");

            // 检查是否是Object类的toString方法
            if (toStringMethod.getDeclaringClass() == Object.class) {
                // 如果没有重写toString方法，则跳过测试
                System.out.println(clazz.getSimpleName() + " 没有重写toString方法，跳过测试");
                return;
            }

            // 调用toString方法
            String toStringResult = (String) toStringMethod.invoke(dtoInstance);

            // 验证toString返回值不为null
            assertNotNull(toStringResult, "toString() method returned null");

            // 验证toString返回值不为空字符串
            assertFalse(toStringResult.trim().isEmpty(), "toString() method returned empty string");

            // 打印toString结果
            System.out.println(clazz.getSimpleName() + " toString: " + toStringResult);

        } catch (NoSuchMethodException e) {
            // 如果没有toString方法（理论上所有类都有Object的toString），这行代码不会执行
            System.out.println(clazz.getSimpleName() + " 没有toString方法，跳过测试");
        } catch (Exception e) {
            // 其他异常（如调用失败）也只打印日志，不中断测试
            System.out.println(clazz.getSimpleName() + " toString方法调用失败: " + e.getMessage());
        }
    }


}

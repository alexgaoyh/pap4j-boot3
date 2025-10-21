package cn.net.pap.quartz.util;

import org.springframework.context.ApplicationContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bean方法反射调用工具类
 */
public class BeanMethodInvoker {

    private BeanMethodInvoker() {
        // 工具类，防止实例化
    }

    /**
     * 解析并执行方法调用字符串
     *
     * @param applicationContext Spring应用上下文
     * @param methodCall         方法调用字符串，格式：beanName.methodName(param1, param2, ...)
     * @return 方法执行结果
     */
    public static Object invokeMethodCall(ApplicationContext applicationContext, String methodCall) {
        if (applicationContext == null) {
            throw new IllegalArgumentException("ApplicationContext cannot be null");
        }
        if (methodCall == null || methodCall.trim().isEmpty()) {
            throw new IllegalArgumentException("Method call string cannot be null or empty");
        }

        try {
            // 解析方法调用字符串
            MethodCallInfo methodCallInfo = parseMethodCall(methodCall);

            // 获取Bean实例
            Object bean = applicationContext.getBean(methodCallInfo.getBeanName());
            if (bean == null) {
                throw new IllegalArgumentException("Bean not found: " + methodCallInfo.getBeanName());
            }

            // 执行方法
            return invokeMethod(bean, methodCallInfo.getMethodName(), methodCallInfo.getArguments());

        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke method call: " + methodCall, e);
        }
    }

    /**
     * 通过反射调用指定Bean的方法
     *
     * @param applicationContext Spring应用上下文
     * @param beanName           Bean名称
     * @param methodName         方法名
     * @param args               参数数组
     * @return 方法执行结果
     */
    public static Object invokeMethod(ApplicationContext applicationContext, String beanName, String methodName, Object... args) {
        if (applicationContext == null) {
            throw new IllegalArgumentException("ApplicationContext cannot be null");
        }

        try {
            Object bean = applicationContext.getBean(beanName);
            if (bean == null) {
                throw new IllegalArgumentException("Bean not found: " + beanName);
            }
            return invokeMethod(bean, methodName, args);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke method: " + beanName + "." + methodName, e);
        }
    }

    /**
     * 通过反射调用对象的方法
     *
     * @param target     目标对象
     * @param methodName 方法名
     * @param args       参数数组
     * @return 方法执行结果
     */
    public static Object invokeMethod(Object target, String methodName, Object... args) {
        if (target == null) {
            throw new IllegalArgumentException("Target object cannot be null");
        }
        if (methodName == null || methodName.trim().isEmpty()) {
            throw new IllegalArgumentException("Method name cannot be null or empty");
        }

        try {
            Class<?> targetClass = target.getClass();

            // 查找匹配的方法
            Method method = findMethod(targetClass, methodName, args);
            if (method == null) {
                throw new NoSuchMethodException("Method not found: " + methodName + " with parameters: " + Arrays.toString(args));
            }

            // 设置方法可访问
            method.setAccessible(true);

            // 转换参数类型
            Object[] convertedArgs = convertArguments(method, args);

            // 执行方法
            return method.invoke(target, convertedArgs);

        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to invoke method: " + methodName, e);
        }
    }

    /**
     * 查找匹配的方法
     */
    private static Method findMethod(Class<?> clazz, String methodName, Object[] args) {
        Method[] methods = clazz.getDeclaredMethods();

        // 先在当前类中查找
        for (Method method : methods) {
            if (method.getName().equals(methodName)) {
                Class<?>[] paramTypes = method.getParameterTypes();

                if (paramTypes.length == args.length) {
                    // 检查参数类型是否匹配
                    if (isParameterTypesMatch(paramTypes, args)) {
                        return method;
                    }
                }
            }
        }

        // 如果没有找到精确匹配的方法，尝试在父类和接口中查找
        return Arrays.stream(clazz.getMethods()).filter(m -> m.getName().equals(methodName) && m.getParameterCount() == args.length).findFirst().orElse(null);
    }

    /**
     * 检查参数类型是否匹配
     */
    private static boolean isParameterTypesMatch(Class<?>[] paramTypes, Object[] args) {
        for (int i = 0; i < paramTypes.length; i++) {
            if (args[i] != null && !isAssignable(paramTypes[i], args[i].getClass())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 类型转换检查
     */
    private static boolean isAssignable(Class<?> paramType, Class<?> argType) {
        if (paramType.isPrimitive()) {
            // 处理基本类型
            return (paramType == int.class && argType == Integer.class) || (paramType == long.class && argType == Long.class) || (paramType == double.class && argType == Double.class) || (paramType == float.class && argType == Float.class) || (paramType == boolean.class && argType == Boolean.class) || (paramType == char.class && argType == Character.class) || (paramType == byte.class && argType == Byte.class) || (paramType == short.class && argType == Short.class);
        }
        return paramType.isAssignableFrom(argType);
    }

    /**
     * 转换参数类型
     */
    private static Object[] convertArguments(Method method, Object[] args) {
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] convertedArgs = new Object[args.length];

        for (int i = 0; i < args.length; i++) {
            convertedArgs[i] = convertArgument(args[i], paramTypes[i]);
        }

        return convertedArgs;
    }

    /**
     * 转换单个参数
     */
    private static Object convertArgument(Object arg, Class<?> targetType) {
        if (arg == null) {
            // 对于基本类型，null值需要特殊处理
            if (targetType.isPrimitive()) {
                return getDefaultPrimitiveValue(targetType);
            }
            return null;
        }

        if (targetType.isInstance(arg)) {
            return arg;
        }

        // 处理字符串到基本类型的转换
        if (arg instanceof String) {
            String strArg = (String) arg;
            return convertStringToType(strArg, targetType);
        }

        return arg;
    }

    /**
     * 字符串到具体类型的转换
     */
    private static Object convertStringToType(String strArg, Class<?> targetType) {
        try {
            if (targetType == Long.class || targetType == long.class) {
                // 处理 "123L" 这种格式
                if (strArg.endsWith("L") || strArg.endsWith("l")) {
                    strArg = strArg.substring(0, strArg.length() - 1);
                }
                return Long.parseLong(strArg);
            } else if (targetType == Integer.class || targetType == int.class) {
                return Integer.parseInt(strArg);
            } else if (targetType == Double.class || targetType == double.class) {
                return Double.parseDouble(strArg);
            } else if (targetType == Float.class || targetType == float.class) {
                return Float.parseFloat(strArg);
            } else if (targetType == Boolean.class || targetType == boolean.class) {
                return Boolean.parseBoolean(strArg);
            } else if (targetType == String.class) {
                // 去除字符串引号
                if (strArg.startsWith("\"") && strArg.endsWith("\"")) {
                    return strArg.substring(1, strArg.length() - 1);
                }
                return strArg;
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cannot convert '" + strArg + "' to type " + targetType.getSimpleName(), e);
        }

        return strArg;
    }

    /**
     * 获取基本类型的默认值
     */
    private static Object getDefaultPrimitiveValue(Class<?> primitiveType) {
        if (primitiveType == int.class) return 0;
        if (primitiveType == long.class) return 0L;
        if (primitiveType == double.class) return 0.0;
        if (primitiveType == float.class) return 0.0f;
        if (primitiveType == boolean.class) return false;
        if (primitiveType == char.class) return '\0';
        if (primitiveType == byte.class) return (byte) 0;
        if (primitiveType == short.class) return (short) 0;
        return null;
    }

    /**
     * 解析方法调用字符串
     */
    private static MethodCallInfo parseMethodCall(String methodCall) {
        // 正则表达式匹配：beanName.methodName(params)
        Pattern pattern = Pattern.compile("^(\\w+)\\.(\\w+)\\(([^)]*)\\)$");
        Matcher matcher = pattern.matcher(methodCall.trim());

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid method call format: " + methodCall + ". Expected format: beanName.methodName(param1, param2, ...)");
        }

        String beanName = matcher.group(1);
        String methodName = matcher.group(2);
        String paramsStr = matcher.group(3).trim();

        Object[] arguments = parseParameters(paramsStr);

        return new MethodCallInfo(beanName, methodName, arguments);
    }

    /**
     * 解析参数字符串
     */
    private static Object[] parseParameters(String paramsStr) {
        if (paramsStr.isEmpty()) {
            return new Object[0];
        }

        // 简单的参数分割（适用于基本类型参数）
        String[] paramStrs = paramsStr.split("\\s*,\\s*");
        Object[] params = new Object[paramStrs.length];

        for (int i = 0; i < paramStrs.length; i++) {
            params[i] = parseParameter(paramStrs[i].trim());
        }

        return params;
    }

    /**
     * 解析单个参数
     */
    private static Object parseParameter(String paramStr) {
        // 处理Long类型（以L结尾）
        if (paramStr.matches("\\d+[Ll]")) {
            return Long.parseLong(paramStr.substring(0, paramStr.length() - 1));
        }
        // 处理整数
        else if (paramStr.matches("\\d+")) {
            return Integer.parseInt(paramStr);
        }
        // 处理浮点数
        else if (paramStr.matches("\\d+\\.\\d+")) {
            return Double.parseDouble(paramStr);
        }
        // 处理布尔值
        else if ("true".equalsIgnoreCase(paramStr) || "false".equalsIgnoreCase(paramStr)) {
            return Boolean.parseBoolean(paramStr);
        }
        // 处理字符串（带引号）
        else if (paramStr.startsWith("\"") && paramStr.endsWith("\"")) {
            return paramStr.substring(1, paramStr.length() - 1);
        }
        // 默认作为字符串处理
        else {
            return paramStr;
        }
    }

    /**
     * 方法调用信息封装类
     */
    private static class MethodCallInfo {
        private final String beanName;
        private final String methodName;
        private final Object[] arguments;

        public MethodCallInfo(String beanName, String methodName, Object[] arguments) {
            this.beanName = beanName;
            this.methodName = methodName;
            this.arguments = arguments;
        }

        public String getBeanName() {
            return beanName;
        }

        public String getMethodName() {
            return methodName;
        }

        public Object[] getArguments() {
            return arguments;
        }
    }
}
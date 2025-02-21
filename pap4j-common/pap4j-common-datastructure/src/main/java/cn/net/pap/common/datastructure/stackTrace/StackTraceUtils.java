package cn.net.pap.common.datastructure.stackTrace;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class StackTraceUtils {

    public static final List<String> DEFAULT_INCLUDES = Arrays.asList("cn.net.pap.");
    public static final List<String> DEFAULT_EXCLUDES = Arrays.asList("java.", "sun.", "javax.", "com.sun.", "org.");

    public static String printFilteredStackTraceStackWalker(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        // 添加异常类型和消息
        builder.append(throwable.getClass().getName()).append(": ").append(throwable.getMessage()).append("\n");
        StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        walker.forEach(frame -> {
            if (frame.getClassName().startsWith("cn.net.pap")) {
                builder.append(frame.getClassName() + ":" + frame.getMethodName() + ":" + frame.getLineNumber()).append("\n");
            }
        });
        return builder.toString();
    }

    public static String getCoreStackTrace(Throwable throwable) {
        return getCoreStackTrace(throwable, DEFAULT_INCLUDES, DEFAULT_EXCLUDES);
    }

    public static String getCoreStackTrace(Throwable throwable, List<String> includePrefixes, List<String> excludePrefixes) {
        Predicate<StackTraceElement> filter = element -> isIncluded(element, includePrefixes) && !isExcluded(element, excludePrefixes);
        return buildStackTrace(throwable, filter);
    }

    public static String getCoreStackTrace(Throwable throwable, Predicate<StackTraceElement> customFilter) {
        return buildStackTrace(throwable, customFilter);
    }

    public static String getCoreStackTrace(String stackTraceString) {
        List<String> stackTraceElementsStart = parseStackTraceStringStart(stackTraceString);
        List<StackTraceElement> stackTraceElements = parseStackTraceString(stackTraceString);
        Predicate<StackTraceElement> filter = element -> isIncluded(element, DEFAULT_INCLUDES) && !isExcluded(element, DEFAULT_EXCLUDES);
        return buildFilteredStackTraceWithStart(stackTraceElementsStart, buildFilteredStackTrace(stackTraceElements, filter));
    }

    public static String getCoreStackTrace(String stackTraceString, Predicate<StackTraceElement> customFilter) {
        List<String> stackTraceElementsStart = parseStackTraceStringStart(stackTraceString);
        List<StackTraceElement> stackTraceElements = parseStackTraceString(stackTraceString);
        return buildFilteredStackTraceWithStart(stackTraceElementsStart, buildFilteredStackTrace(stackTraceElements, customFilter));
    }

    public static String getFullStackTrace(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        String throwableString = throwable.toString();
        if(null != throwableString && !"".equals(throwableString)) {
            sb.append("start ").append(throwableString).append("\n");
        }
        // 获取当前异常的堆栈信息
        StackTraceElement[] elements = throwable.getStackTrace();
        for (StackTraceElement element : elements) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }
        // 处理嵌套的异常
        Throwable cause = throwable.getCause();
        if (cause != null) {
            sb.append("Caused by: ").append(cause.getClass().getName());
            if (cause.getMessage() != null) {
                sb.append(": ").append(cause.getMessage());
            }
            sb.append("\n");
            sb.append(getFullStackTrace(cause));
        }
        return sb.toString();
    }

    private static String buildStackTrace(Throwable throwable, Predicate<StackTraceElement> filter) {
        StringBuilder sb = new StringBuilder();
        Throwable current = throwable;

        while (current != null) {
            sb.append(current.getClass().getName()).append(": ").append(current.getMessage()).append("\n");

            List<StackTraceElement> filtered = Arrays.stream(current.getStackTrace()).filter(filter).collect(Collectors.toList());

            for (StackTraceElement element : filtered) {
                sb.append("\tat ").append(element.toString()).append("\n");
            }

            current = current.getCause();
            if (current != null) {
                sb.append("Caused by: ");
            }
        }

        return sb.toString();
    }

    private static boolean isIncluded(StackTraceElement element, List<String> includePrefixes) {
        if (includePrefixes == null || includePrefixes.isEmpty()) return true;
        return includePrefixes.stream().anyMatch(prefix -> element.getClassName().startsWith(prefix));
    }

    private static boolean isExcluded(StackTraceElement element, List<String> excludePrefixes) {
        if (excludePrefixes == null || excludePrefixes.isEmpty()) return false;
        return excludePrefixes.stream().anyMatch(prefix -> element.getClassName().startsWith(prefix));
    }

    private static List<String> parseStackTraceStringStart(String stackTraceString) {
        List<String> elements = new ArrayList<>();
        String[] lines = stackTraceString.split("\n");
        for (int idx = 0; idx < lines.length; idx++) {
            String line  =  lines[idx];
            if (line.trim().startsWith("start ")) {
                elements.add(line);
            }
        }
        return elements;
    }

    private static List<StackTraceElement> parseStackTraceString(String stackTraceString) {
        List<StackTraceElement> elements = new ArrayList<>();
        String[] lines = stackTraceString.split("\n");
        for (int idx = 0; idx < lines.length; idx++) {
            String line  =  lines[idx];
            if (line.trim().startsWith("at ")) {
                StackTraceElement element = parseStackTraceElement(line);
                elements.add(element);
            }
        }
        return elements;
    }

    private static StackTraceElement parseStackTraceElement(String line) {
        line = line.trim();
        if (!line.startsWith("at ")) {
            throw new IllegalArgumentException("Invalid stack trace line: " + line);
        }
        String className = "";
        String methodName = "";
        String fileName = "";
        int lineNumber = -1;

        String methodPart = line.substring(3).trim();
        int parenIndex = methodPart.indexOf('(');
        int braceIndex = methodPart.indexOf(')');

        if (parenIndex != -1 && braceIndex != -1) {
            String classAndMethod = methodPart.substring(0, parenIndex).trim();
            String location = methodPart.substring(parenIndex + 1, braceIndex).trim();

            int lastDotIndex = classAndMethod.lastIndexOf('.');
            if (lastDotIndex != -1) {
                className = classAndMethod.substring(0, lastDotIndex);
                methodName = classAndMethod.substring(lastDotIndex + 1);
            } else {
                className = "";
                methodName = classAndMethod;
            }

            String[] locationParts = location.split(":");
            if (locationParts.length == 2) {
                fileName = locationParts[0];
                try {
                    lineNumber = Integer.parseInt(locationParts[1]);
                } catch (NumberFormatException e) {
                    lineNumber = -1;
                }
            }
        }

        return new StackTraceElement(className, methodName, fileName, lineNumber);
    }

    private static String buildFilteredStackTrace(List<StackTraceElement> stackTraceElements, Predicate<StackTraceElement> filter) {
        StringBuilder sb = new StringBuilder();
        if(null != stackTraceElements && stackTraceElements.size() > 0) {
            stackTraceElements.stream().filter(filter).forEach(element -> sb.append("\tat ").append(element.toString()).append("\n"));
        }
        return sb.toString();
    }

    private static String buildFilteredStackTraceWithStart(List<String> stackTraceElementsStart, String end) {
        StringBuilder sb = new StringBuilder();
        if(null != stackTraceElementsStart && stackTraceElementsStart.size() > 0) {
            for(String start : stackTraceElementsStart) {
                sb.append(start.replace("start ", "")).append("\n");
            }
        }
        sb.append(end).append("\n");
        return sb.toString();
    }


}
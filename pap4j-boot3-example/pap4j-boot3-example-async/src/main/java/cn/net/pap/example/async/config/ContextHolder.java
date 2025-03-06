package cn.net.pap.example.async.config;

public class ContextHolder {

    private static final ThreadLocal<String> context = new ThreadLocal<>();

    public static void set(String value) {
        context.set(value);
    }

    public static String get() {
        return context.get();
    }

    public static void clear() {
        context.remove();
    }

}


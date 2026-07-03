package cn.net.pap.common.vips.jna;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

public interface LibVips extends Library {

    java.util.Map<String, Object> OPTIONS = java.util.Map.of(
            Library.OPTION_STRING_ENCODING, "UTF-8"
    );

    LibVips INSTANCE = loadVipsLibrary();

    static LibVips loadVipsLibrary() {
        String[] libNames = {"vips", "libvips-42", "vips-42"};
        UnsatisfiedLinkError lastError = null;
        for (String name : libNames) {
            try {
                return Native.load(name, LibVips.class, OPTIONS);
            } catch (UnsatisfiedLinkError e) {
                lastError = e;
            }
        }
        throw new UnsatisfiedLinkError(
                "无法加载 libvips 本地动态链接库。请确保系统已安装 libvips 且已将其 bin 目录配置到 PATH 或 LD_LIBRARY_PATH 环境变量中。详情: "
                        + (lastError != null ? lastError.getMessage() : "未知错误"));
    }

    /**
     * @see <a href="https://libvips.github.io/libvips/API/current/libvips-vips.html#vips-init">vips_init API</a>
     */
    int vips_init(String argv0);

    /**
     * @see <a href="https://libvips.github.io/libvips/API/current/libvips-vips.html#vips-shutdown">vips_shutdown API</a>
     */
    void vips_shutdown();

    /**
     * @see <a href="https://libvips.github.io/libvips/API/current/libvips-vips.html#vips-thread-shutdown">vips_thread_shutdown API</a>
     */
    void vips_thread_shutdown();

    /**
     * @see <a href="https://libvips.github.io/libvips/API/current/VipsImage.html#vips-image-new-from-file">vips_image_new_from_file API</a>
     */
    Pointer vips_image_new_from_file(String filename, Object... varargs);

    /**
     * 获取图像宽度。
     *
     * @param image 图像指针
     * @return 图像的宽度（像素）
     * @see <a href="https://libvips.github.io/libvips/API/current/VipsImage.html#vips-image-get-width">vips_image_get_width API</a>
     */
    int vips_image_get_width(Pointer image);

    /**
     * 获取图像高度。
     *
     * @param image 图像指针
     * @return 图像的高度（像素）
     * @see <a href="https://libvips.github.io/libvips/API/current/VipsImage.html#vips-image-get-height">vips_image_get_height API</a>
     */
    int vips_image_get_height(Pointer image);

    /**
     * 对图像进行裁剪操作。
     *
     * @param in 输入图像指针
     * @param out 接收裁剪后图像指针的引用
     * @param left 裁剪区域左上角横坐标
     * @param top 裁剪区域左上角纵坐标
     * @param width 裁剪宽度
     * @param height 裁剪高度
     * @param varargs 可变参数列表，以空（null）指针结尾
     * @return 成功返回 0，失败返回非 0
     * @see <a href="https://libvips.github.io/libvips/API/current/libvips-conversion.html#vips-crop">vips_crop API</a>
     */
    int vips_crop(Pointer in, PointerByReference out, int left, int top, int width, int height, Object... varargs);

    /**
     * 对图像进行重采样缩放操作。
     *
     * @param in 输入图像指针
     * @param out 接收缩放后图像指针的引用
     * @param scale 缩放比例因子
     * @param varargs 可变参数列表，以空（null）指针结尾
     * @return 成功返回 0，失败返回非 0
     * @see <a href="https://libvips.github.io/libvips/API/current/libvips-resample.html#vips-resize">vips_resize API</a>
     */
    int vips_resize(Pointer in, PointerByReference out, double scale, Object... varargs);

    /**
     * @see <a href="https://libvips.github.io/libvips/API/current/VipsImage.html#vips-image-write-to-file">vips_image_write_to_file API</a>
     */
    int vips_image_write_to_file(Pointer image, String name, Object... varargs);

    /**
     * 从内存缓冲区加载图像元数据，建立惰性加载流指针。
     *
     * @param buf 内存缓冲区指针
     * @param len 缓冲区长度
     * @param optionString 选项字符串
     * @param varargs 可变参数列表，以空（null）指针结尾
     * @return 图像指针
     * @see <a href="https://libvips.github.io/libvips/API/current/VipsImage.html#vips-image-new-from-buffer">vips_image_new_from_buffer API</a>
     */
    Pointer vips_image_new_from_buffer(Pointer buf, long len, String optionString, Object... varargs);

    /**
     * 将处理完成的图像惰性求值写出到内存缓冲区。
     *
     * @param image 图像指针
     * @param suffix 输出格式后缀
     * @param buf 接收输出指针 of 引用
     * @param size 接收输出大小 of 引用
     * @param varargs 可变参数列表，以空（null）指针结尾
     * @return 成功返回 0，失败返回非 0
     * @see <a href="https://libvips.github.io/libvips/API/current/VipsImage.html#vips-image-write-to-buffer">vips_image_write_to_buffer API</a>
     */
    int vips_image_write_to_buffer(Pointer image, String suffix, PointerByReference buf, LongByReference size, Object... varargs);

    /**
     * @see <a href="https://libvips.github.io/libvips/API/current/libvips-error.html#vips-error-buffer">vips_error_buffer API</a>
     */
    String vips_error_buffer();

    /**
     * @see <a href="https://libvips.github.io/libvips/API/current/libvips-error.html#vips-error-clear">vips_error_clear API</a>
     */
    void vips_error_clear();

    /**
     * GLib/GObject 底层依赖库接口。
     *
     * <p><b>为什么需要独立定义 GLib 接口并分别加载？</b></p>
     * <ul>
     *   <li><b>JNA 映射机制限制</b>：在 JNA 中，每个继承自 {@link Library} 的接口在执行 {@code Native.load} 时，
     *   都只与一个物理上的动态链接库文件（.dll 或 .so）绑定。该接口中声明的所有方法只能去这唯一一个库文件里查找。</li>
     *   <li><b>DLL 物理隔离</b>：在 Windows 等平台上，图像处理函数（如 {@code vips_*}）物理上存放在 {@code libvips-42.dll} 中；
     *   而释放内存的 {@code g_object_unref} 函数则物理上存放在 {@code libgobject-2.0-0.dll} 中，且 {@code libvips-42.dll} 的导出表中没有重新导出此符号。</li>
     *   <li><b>解决方案</b>：基于上述原因，我们无法通过单一的 {@code Native.load("vips", ...)} 加载包含 {@code g_object_unref} 的统一接口。
     *   因此，必须使用两个不同的接口（即外层的 {@code LibVips} 和内部的 {@code GLib}）分别映射并加载各自对应的 DLL 文件，从而在各自的命名空间中安全、清晰地调用相应函数。</li>
     * </ul>
     */
    interface GLib extends Library {
        GLib INSTANCE = loadLibrary();

        static GLib loadLibrary() {
            String[] libNames = {
                    "gobject-2.0",
                    "libgobject-2.0-0",
                    "libgobject-2.0",
                    "gobject"
            };
            UnsatisfiedLinkError lastError = null;
            for (String name : libNames) {
                try {
                    return Native.load(name, GLib.class, OPTIONS);
                } catch (UnsatisfiedLinkError e) {
                    lastError = e;
                }
            }
            throw new UnsatisfiedLinkError("无法加载 gobject 动态库以解析 g_object_unref 函数。详情: " + (lastError != null ? lastError.getMessage() : "未知错误"));
        }

        /**
         * 释放 GObject 指针引用
         *
         * @see <a href="https://docs.gtk.org/gobject/method.Object.unref.html">g_object_unref API</a>
         */
        void g_object_unref(Pointer object);
    }

    /**
     * GLib 核心基础依赖库接口，用于释放底层内存（g_free）。
     */
    interface GLibBase extends Library {
        GLibBase INSTANCE = loadLibrary();

        static GLibBase loadLibrary() {
            String[] libNames = {
                    "glib-2.0",
                    "libglib-2.0-0",
                    "libglib-2.0",
                    "glib"
            };
            UnsatisfiedLinkError lastError = null;
            for (String name : libNames) {
                try {
                    return Native.load(name, GLibBase.class, OPTIONS);
                } catch (UnsatisfiedLinkError e) {
                    lastError = e;
                }
            }
            throw new UnsatisfiedLinkError("无法加载 glib-2.0 基础动态库以解析 g_free 函数。详情: " + (lastError != null ? lastError.getMessage() : "未知错误"));
        }

        /**
         * 释放由 GLib 分配的内存
         *
         * @see <a href="https://docs.gtk.org/glib/func.free.html">g_free API</a>
         */
        void g_free(Pointer mem);

        /**
         * 设置 GLib 级别的环境变量，直接影响 g_get_tmp_dir() 等 native 行为。
         *
         * @param variable 环境变量名
         * @param value 环境变量值
         * @param overwrite 是否在已存在的情况下覆盖
         * @return 成功返回 true，失败返回 false
         * @see <a href="https://docs.gtk.org/glib/func.setenv.html">g_setenv API</a>
         */
        boolean g_setenv(String variable, String value, boolean overwrite);
    }
}

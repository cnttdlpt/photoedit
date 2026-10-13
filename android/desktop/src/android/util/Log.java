package android.util;

/**
 * Lớp tương thích {@code android.util.Log} cho bản máy tính Windows Desktop.
 */
public final class Log {
    private Log() {
    }

    public static int d(String tag, String msg) {
        return 0;
    }

    public static int w(String tag, String msg) {
        System.err.println("[WARN][" + tag + "] " + msg);
        return 0;
    }

    public static int e(String tag, String msg) {
        System.err.println("[ERROR][" + tag + "] " + msg);
        return 0;
    }

    public static int e(String tag, String msg, Throwable tr) {
        System.err.println("[ERROR][" + tag + "] " + msg);
        if (tr != null) {
            tr.printStackTrace(System.err);
        }
        return 0;
    }
}

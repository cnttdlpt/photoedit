package vn.iro.editor;

import android.util.Log;

/**
 * Bộ ghi log trung tâm phục vụ theo dõi luồng chạy và gỡ lỗi (debug).
 *
 * <p>Hướng dẫn sử dụng trong Android Studio (Logcat):
 * <ul>
 *     <li>Lọc theo từ khóa {@code tag:IRO_} để xem toàn bộ hoạt động của ứng dụng.</li>
 *     <li>{@code IRO_Engine}: Theo dõi thời gian xử lý pixel của {@link ColorEngine}.</li>
 *     <li>{@code IRO_AutoBalance}: Xem các chỉ số phân tích độ sáng (p5, median, p95), WB và độ bão hòa của {@link AutoBalancer}.</li>
 *     <li>{@code IRO_LUT}: Theo dõi quá trình đọc file {@code .cube} và nội suy màu 3D trong {@link CubeLut}.</li>
 *     <li>{@code IRO_Recipe}: Theo dõi quá trình ánh xạ thông số công thức Fuji sang {@link Settings}.</li>
 *     <li>{@code IRO_Frame}: Theo dõi quá trình vẽ 30 mẫu khung và thông tin EXIF trong {@link PhotoFrame}.</li>
 *     <li>{@code IRO_Editor}: Theo dõi trạng thái giao diện, Undo/Redo, mở ảnh và xuất ảnh trong {@link MainActivity}.</li>
 * </ul>
 *
 * <p>Lớp này tự động phát hiện môi trường chạy: nếu chạy trên thiết bị/máy ảo Android thì ghi vào
 * {@link android.util.Log}, còn nếu chạy kiểm thử độc lập trên máy tính (JVM test) thì không gây lỗi {@code Stub!}.
 */
public final class DebugLogger {

    public static final String TAG_ENGINE = "IRO_Engine";
    public static final String TAG_AUTOBALANCE = "IRO_AutoBalance";
    public static final String TAG_LUT = "IRO_LUT";
    public static final String TAG_RECIPE = "IRO_Recipe";
    public static final String TAG_FRAME = "IRO_Frame";
    public static final String TAG_EDITOR = "IRO_Editor";
    public static final String TAG_ONLINE = "IRO_Online";

    /** Bật/tắt ghi log debug toàn cục. Có thể đặt breakpoint tại các hàm bên dưới khi debug. */
    public static volatile boolean ENABLED = true;

    private DebugLogger() {
    }

    /**
     * Ghi log thông tin gỡ lỗi (Debug).
     *
     * @param tag     Nhãn phân loại module (ví dụ {@link #TAG_ENGINE}).
     * @param message Nội dung chi tiết cần theo dõi.
     */
    public static void d(String tag, String message) {
        if (!ENABLED) {
            return;
        }
        try {
            Log.d(tag, message);
        } catch (Throwable ignored) {
            // Khi chạy unit test bằng java.exe ngoài Android runtime, android.util.Log ném lỗi Stub!
            // Bỏ qua an toàn để bộ kiểm thử cốt lõi chạy bình thường.
        }
    }

    /**
     * Ghi log cảnh báo (Warning).
     *
     * @param tag     Nhãn phân loại module.
     * @param message Nội dung cảnh báo.
     */
    public static void w(String tag, String message) {
        if (!ENABLED) {
            return;
        }
        try {
            Log.w(tag, message);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Ghi log lỗi (Error) kèm ngoại lệ để dễ dàng truy vết stack trace trong Logcat.
     *
     * @param tag     Nhãn phân loại module.
     * @param message Mô tả ngữ cảnh xảy ra lỗi.
     * @param error   Đối tượng ngoại lệ (có thể null).
     */
    public static void e(String tag, String message, Throwable error) {
        if (!ENABLED) {
            return;
        }
        try {
            if (error != null) {
                Log.e(tag, message, error);
            } else {
                Log.e(tag, message);
            }
        } catch (Throwable ignored) {
        }
    }
}

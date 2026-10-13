package vn.iro.editor;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Phiên tạo và lưu đệm (cache) ảnh thu nhỏ (Thumbnail) chạy trên luồng nền.
 * Tương đương với {@code thumbnailWorker} và bộ nhớ đệm {@code thumbnails} trong {@code ios/IRO/App/EditorModel.swift}.
 *
 * <p>Đặc điểm kỹ thuật:
 * <ul>
 *     <li>Thu nhỏ bức ảnh đang mở về cạnh dài tối đa {@code 192 px} để render nhanh hàng chục công thức màu.</li>
 *     <li>Lưu đệm tối đa 64 Bitmap kết quả và 4 đối tượng {@link CubeLut} đã phân tích cú pháp trong {@link LruCache}.</li>
 *     <li>Với công thức chưa gắn LUT màu nền, hiển thị ảnh gốc kèm nhãn {@code "Chưa có LUT"} để người dùng phân biệt rõ.</li>
 *     <li>Tự động hủy các công việc tồn đọng khi đóng hộp thoại hoặc đổi ảnh mới ({@link #close()}).</li>
 * </ul>
 */
final class ThumbnailSession implements AutoCloseable {

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> thumbnails = new LruCache<>(64);
    /** Việc đọc và phân tích file .cube tốn nhiều thời gian hơn việc chạy ColorEngine trên ảnh 192px. */
    private final LruCache<String, CubeLut> luts = new LruCache<>(4);
    private final Map<String, List<Target>> waiting = new HashMap<>();
    private final Set<String> failed = new HashSet<>();
    final Bitmap original;
    private volatile boolean closed;

    private static final class Target {
        final ImageView image;
        final TextView label;

        Target(ImageView image, TextView label) {
            this.image = image;
            this.label = label;
        }

        void show(Bitmap bitmap, String message) {
            if (image != null && bitmap != null) {
                image.setImageBitmap(bitmap);
            }
            if (label != null) {
                label.setText(message);
            }
        }
    }

    /**
     * Khởi tạo phiên thumbnail từ ảnh preview hiện tại.
     */
    ThumbnailSession(Bitmap source) {
        if (source == null) {
            original = null;
            return;
        }
        float scale = Math.min(1f, 192f / Math.max(source.getWidth(), source.getHeight()));
        original = Bitmap.createScaledBitmap(
                source,
                Math.max(1, Math.round(source.getWidth() * scale)),
                Math.max(1, Math.round(source.getHeight() * scale)),
                true);
    }

    /**
     * Gắn yêu cầu tạo ảnh thu nhỏ cho một thẻ Recipe hoặc LUT.
     *
     * @param image    View hiển thị ảnh thu nhỏ.
     * @param label    View hiển thị trạng thái (có thể {@code null} nếu ở dải carousel dưới đáy).
     * @param key      Khóa định danh duy nhất cho bộ nhớ đệm.
     * @param settings Thông số hậu kỳ của Recipe.
     * @param file     File LUT {@code .cube} gắn với Film Simulation (nếu có).
     * @param direct   Đối tượng {@link CubeLut} đã nạp sẵn trong bộ nhớ (nếu có).
     */
    void bind(ImageView image, TextView label, String key, Settings settings, File file, CubeLut direct) {
        if (closed) {
            return;
        }
        Target target = new Target(image, label);
        if (original == null) {
            if (label != null) {
                label.setText("Mở ảnh để xem trước");
            }
            return;
        }
        final boolean hasLut = (file != null || direct != null);
        final String successMsg = hasLut ? "Đã gắn LUT" : "Đã áp thông số recipe";
        Bitmap cached = thumbnails.get(key);
        if (cached != null) {
            target.show(cached, successMsg);
            return;
        }
        if (failed.contains(key)) {
            target.show(original, "LUT lỗi · ảnh gốc");
            return;
        }

        // Hiển thị tạm ảnh gốc trong lúc luồng nền đang xử lý thông số / nội suy LUT
        target.show(original, "Đang tạo ảnh màu…");
        List<Target> existing = waiting.get(key);
        if (existing != null) {
            existing.add(target);
            return;
        }

        List<Target> targets = new ArrayList<>();
        targets.add(target);
        waiting.put(key, targets);
        Settings snapshot = settings.copy();

        worker.execute(() -> {
            if (closed) {
                return;
            }
            Bitmap result = null;
            try {
                CubeLut lut = direct;
                if (lut == null && file != null) {
                    String fileKey = file.getAbsolutePath();
                    lut = luts.get(fileKey);
                    if (lut == null) {
                        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                            lut = CubeLut.parse(r);
                        }
                        luts.put(fileKey, lut);
                    }
                }
                if (closed) {
                    return;
                }
                int w = original.getWidth();
                int h = original.getHeight();
                int[] input = new int[w * h];
                original.getPixels(input, 0, w, 0, 0, w, h);
                result = Bitmap.createBitmap(
                        ColorEngine.process(input, w, h, snapshot, lut),
                        w, h, Bitmap.Config.ARGB_8888);
            } catch (Exception | OutOfMemoryError ignored) {
                // Giữ thông báo lỗi trên từng mục thay vì bật Toast liên tục.
            }
            if (closed) {
                return;
            }
            final Bitmap rendered = result;
            main.post(() -> {
                if (closed) {
                    return;
                }
                List<Target> listeners = waiting.remove(key);
                if (rendered == null) {
                    failed.add(key);
                } else {
                    thumbnails.put(key, rendered);
                }
                if (listeners != null) {
                    for (Target listener : listeners) {
                        listener.show(
                                rendered == null ? original : rendered,
                                rendered == null ? "LUT lỗi · ảnh gốc" : successMsg);
                    }
                }
            });
        });
    }

    @Override
    public void close() {
        closed = true;
        worker.shutdownNow();
        main.removeCallbacksAndMessages(null);
        waiting.clear();
        thumbnails.evictAll();
    }
}

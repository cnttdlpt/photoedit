package vn.iro.editor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bộ tải và lưu đệm (Cache) ảnh mẫu mô phỏng màu phim từ <b>Fuji X Weekly</b> ({@code fujixweekly.com}).
 *
 * <p>Cơ chế hoạt động 3 tầng (Three-tier caching):
 * <ol>
 *     <li><b>Bộ nhớ RAM ({@link LruCache}):</b> Phản hồi tức thì {@code 0ms} khi cuộn danh sách 392 công thức hoặc lưới Màu phim.</li>
 *     <li><b>Tài nguyên đóng gói sẵn ({@code assets/fujixweekly/*.jpg}):</b> Hiển thị ngay lập tức ảnh mẫu thực tế từ Fuji X Weekly
 *         ngay cả khi thiết bị hoàn toàn ngoại tuyến (Offline).</li>
 *     <li><b>Tải trực tuyến & Lưu đệm đĩa ({@code cache/fujixweekly-samples/}):</b> Với các bài viết công thức cụ thể có
 *         {@link Recipe#sampleImageUrl} hoặc bộ sưu tập {@link Recipe#sampleImages}, tự động tải ngầm ảnh mẫu gốc từ CDN của
 *         Fuji X Weekly ({@code i0.wp.com} / {@code live.staticflickr.com}) và lưu xuống bộ nhớ đệm trên thiết bị.</li>
 * </ol>
 */
public final class FujiSampleLoader {

    private static final LruCache<String, Bitmap> MEMORY_CACHE = new LruCache<>(72);
    private static final ExecutorService IO_POOL = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Set<String> IN_FLIGHT = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private FujiSampleLoader() {
    }

    /**
     * Gắn ảnh mẫu mô phỏng Fuji X Weekly của một {@link Recipe} vào {@link ImageView}.
     *
     * @param context Context ứng dụng.
     * @param view    ImageView cần hiển thị ảnh mô phỏng.
     * @param badge   TextView trạng thái phụ (có thể {@code null}).
     * @param recipe  Công thức màu phim Fuji X Weekly.
     */
    public static void bindSample(final Context context,
                                  final ImageView view,
                                  final TextView badge,
                                  final Recipe recipe) {
        if (view == null || recipe == null) {
            return;
        }
        final String tagKey = "recipe:" + recipe.id;
        view.setTag(tagKey);

        // 1. Kiểm tra RAM cache theo ID công thức trước
        Bitmap exactCached = MEMORY_CACHE.get(tagKey);
        if (exactCached != null && !exactCached.isRecycled()) {
            view.setImageBitmap(exactCached);
            if (badge != null) {
                badge.setText("Ảnh mẫu FujiXWeekly · " + recipe.film);
            }
            return;
        }

        // 2. Kiểm tra file đóng gói riêng cho công thức này trong assets/fujixweekly/<id>.jpg
        final String exactAssetPath = "fujixweekly/" + recipe.id + ".jpg";
        Bitmap exactAssetBmp = decodeAssetThumb(context, exactAssetPath, 360);
        if (exactAssetBmp != null) {
            MEMORY_CACHE.put(tagKey, exactAssetBmp);
            view.setImageBitmap(exactAssetBmp);
            if (badge != null) {
                badge.setText("Ảnh mẫu FujiXWeekly · " + recipe.film);
            }
            return;
        }

        // 3. Hiển thị tức thì ảnh mẫu đại diện cùng dòng Film Simulation từ assets/fujixweekly/
        if (recipe.sampleAsset != null && !recipe.sampleAsset.isEmpty()) {
            String fallbackKey = "asset:" + recipe.sampleAsset;
            Bitmap fallbackBmp = MEMORY_CACHE.get(fallbackKey);
            if (fallbackBmp == null || fallbackBmp.isRecycled()) {
                fallbackBmp = decodeAssetThumb(context, recipe.sampleAsset, 360);
                if (fallbackBmp != null) {
                    MEMORY_CACHE.put(fallbackKey, fallbackBmp);
                }
            }
            if (fallbackBmp != null) {
                view.setImageBitmap(fallbackBmp);
                if (badge != null) {
                    badge.setText("Ảnh mẫu FujiXWeekly · " + recipe.film);
                }
            }
        }

        // 4. Nếu công thức có URL ảnh mẫu riêng trên fujixweekly.com, tải ngầm từ Disk Cache hoặc Mạng
        final String remoteUrl = recipe.sampleImageUrl;
        if (remoteUrl == null || remoteUrl.isEmpty()) {
            return;
        }
        bindUrl(context, view, badge, remoteUrl, tagKey, "Ảnh mẫu FujiXWeekly · " + recipe.film, 420);
    }

    /**
     * Gắn một URL ảnh mẫu cụ thể trong bộ sưu tập {@link Recipe#sampleImages} vào {@link ImageView}.
     */
    public static void bindGalleryUrl(final Context context,
                                      final ImageView view,
                                      final String imageUrl,
                                      final String fallbackAssetPath) {
        if (view == null) {
            return;
        }
        final String tagKey = "url:" + imageUrl;
        view.setTag(tagKey);

        Bitmap cached = MEMORY_CACHE.get(tagKey);
        if (cached != null && !cached.isRecycled()) {
            view.setImageBitmap(cached);
            return;
        }

        if (fallbackAssetPath != null && !fallbackAssetPath.isEmpty()) {
            Bitmap fallback = decodeAssetThumb(context, fallbackAssetPath, 480);
            if (fallback != null) {
                view.setImageBitmap(fallback);
            }
        }

        if (imageUrl != null && !imageUrl.isEmpty()) {
            bindUrl(context, view, null, imageUrl, tagKey, null, 640);
        }
    }

    private static void bindUrl(final Context context,
                                final ImageView view,
                                final TextView badge,
                                final String urlStr,
                                final String tagKey,
                                final String successText,
                                final int targetMaxEdge) {
        final Context appCtx = context.getApplicationContext();
        IO_POOL.execute(() -> {
            try {
                File cacheDir = new File(appCtx.getCacheDir(), "fujixweekly-samples");
                if (!cacheDir.isDirectory()) {
                    cacheDir.mkdirs();
                }
                File diskFile = new File(cacheDir, sha256Hex(urlStr) + ".img");
                if (!diskFile.isFile() || diskFile.length() < 256) {
                    if (!IN_FLIGHT.add(urlStr)) {
                        return;
                    }
                    try {
                        downloadToFile(urlStr, diskFile);
                    } finally {
                        IN_FLIGHT.remove(urlStr);
                    }
                }
                if (diskFile.isFile() && diskFile.length() >= 256) {
                    Bitmap decoded = decodeFileSampled(diskFile, targetMaxEdge);
                    if (decoded != null) {
                        MEMORY_CACHE.put(tagKey, decoded);
                        MAIN.post(() -> {
                            if (tagKey.equals(view.getTag())) {
                                view.setImageBitmap(decoded);
                                if (badge != null && successText != null) {
                                    badge.setText(successText);
                                }
                            }
                        });
                    }
                }
            } catch (Exception ignored) {
                // Giữ nguyên ảnh mẫu offline từ assets nếu không có kết nối mạng
            }
        });
    }

    /**
     * Đọc đồng bộ (trên luồng nền) ảnh mẫu chất lượng cao của một {@link Recipe} để nạp thẳng vào bàn dựng ảnh.
     */
    public static Bitmap loadSampleBitmapBlocking(Context context, Recipe recipe, int maxEdge) {
        if (recipe == null) {
            return null;
        }
        // 1. Thử tải bản độ phân giải cao từ URL hoặc Disk Cache trước
        if (recipe.sampleImageUrl != null && !recipe.sampleImageUrl.isEmpty()) {
            try {
                File cacheDir = new File(context.getCacheDir(), "fujixweekly-samples");
                if (!cacheDir.isDirectory()) {
                    cacheDir.mkdirs();
                }
                File diskFile = new File(cacheDir, sha256Hex(recipe.sampleImageUrl) + ".img");
                if (!diskFile.isFile() || diskFile.length() < 256) {
                    downloadToFile(recipe.sampleImageUrl, diskFile);
                }
                if (diskFile.isFile() && diskFile.length() >= 256) {
                    Bitmap b = decodeFileSampled(diskFile, maxEdge);
                    if (b != null) {
                        return b;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        // 2. Fallback về ảnh đóng gói sẵn trong assets/fujixweekly/
        String exactAsset = "fujixweekly/" + recipe.id + ".jpg";
        Bitmap fromExact = decodeAssetThumb(context, exactAsset, maxEdge);
        if (fromExact != null) {
            return fromExact;
        }
        if (recipe.sampleAsset != null && !recipe.sampleAsset.isEmpty()) {
            return decodeAssetThumb(context, recipe.sampleAsset, maxEdge);
        }
        return null;
    }

    public static Bitmap decodeAssetThumb(Context context, String assetPath, int maxEdge) {
        if (assetPath == null || assetPath.isEmpty()) {
            return null;
        }
        try (InputStream in = context.getAssets().open(assetPath)) {
            byte[] bytes = readAllBytes(in, 4 * 1024 * 1024);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            int sampleSize = 1;
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            while (longest / (sampleSize * 2) >= maxEdge) {
                sampleSize *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSize;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Bitmap decodeFileSampled(File file, int maxEdge) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (FileInputStream in = new FileInputStream(file)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            int sampleSize = 1;
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            while (longest / (sampleSize * 2) >= maxEdge) {
                sampleSize *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSize;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (FileInputStream in = new FileInputStream(file)) {
                return BitmapFactory.decodeStream(in, null, opts);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static void downloadToFile(String urlStr, File dest) throws Exception {
        URL url = new URL(urlStr);
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IllegalArgumentException("Chỉ hỗ trợ HTTPS");
        }
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(12_000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "IRO-Android-Studio/1.0");
        try {
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("HTTP " + conn.getResponseCode());
            }
            byte[] bytes = readAllBytes(conn.getInputStream(), 6 * 1024 * 1024);
            File tmp = new File(dest.getParentFile(), dest.getName() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(bytes);
            }
            tmp.renameTo(dest);
        } finally {
            conn.disconnect();
        }
    }

    private static byte[] readAllBytes(InputStream in, int maxBytes) throws Exception {
        try (InputStream stream = in;
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = stream.read(buf)) != -1) {
                if (out.size() + n > maxBytes) {
                    throw new IllegalStateException("Ảnh vượt giới hạn bộ nhớ đệm");
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (int i = 0; i < 16; i++) {
                sb.append(String.format("%02x", digest[i] & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(Math.abs(input.hashCode()));
        }
    }
}

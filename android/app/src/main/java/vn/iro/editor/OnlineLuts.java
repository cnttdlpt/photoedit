package vn.iro.editor;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * Dịch vụ tìm kiếm và tải tệp LUT 3D {@code .cube} từ kho GitHub công khai.
 * Chuyển đổi tương đương với {@code ios/IRO/Library/OnlineLUTService.swift}.
 *
 * <p>Các cơ chế bảo mật và kiểm tra toàn vẹn (để theo dõi và debug):
 * <ul>
 *     <li>Chỉ kết nối tới {@code https://api.github.com} và {@code https://raw.githubusercontent.com}.</li>
 *     <li>Tuyệt đối không gửi ảnh hoặc dữ liệu cá nhân lên mạng.</li>
 *     <li>Ghim theo mã SHA-1 của commit ({@code revision}) bất biến khi tải file.</li>
 *     <li>Tính toán mã băm Git blob SHA-1 ({@code "blob " + length + "\0" + content}) và đối chiếu với
 *         SHA-1 công bố trong cây thư mục Git trước khi lưu xuống bộ nhớ trong.</li>
 *     <li>Phân tích cú pháp thử qua {@link CubeLut#parse} trước khi đổi tên nguyên tử (atomic rename) sang file đích.</li>
 * </ul>
 */
final class OnlineLuts {

    /** Kho LUT mẫu mặc định (giấy phép MIT, thiết kế cho không gian màu sRGB / Standard). */
    static final String DEFAULT_REPO = "t0saki/lumix-original-looks";

    /** Giới hạn dung lượng tối đa cho mỗi file LUT (35 MB). */
    static final int MAX_LUT = 35 * 1024 * 1024;

    private OnlineLuts() {
    }

    /**
     * Chuẩn hóa và kiểm tra chuỗi {@code owner/repository} hoặc URL GitHub công khai.
     */
    static String repository(String input) throws IOException {
        if (input == null) {
            throw new IOException("Nhập owner/repository hoặc URL kho GitHub công khai.");
        }
        String s = input.trim()
                .replaceFirst("^https://github\\.com/", "")
                .replaceFirst("/+$", "");
        if (!s.matches("[A-Za-z0-9_-]{1,100}/[A-Za-z0-9_.-]{1,100}")
                || s.endsWith("/..")
                || s.endsWith("/.")) {
            throw new IOException("Nhập owner/repository hoặc URL kho GitHub công khai.");
        }
        return s;
    }

    /**
     * Tải dữ liệu nhị phân qua HTTPS với giới hạn dung lượng và thời gian chờ nghiêm ngặt (60 giây).
     */
    static byte[] fetch(String url, int limit) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL u = new URL(url);
            if (!"https".equals(u.getProtocol())
                    || !("api.github.com".equals(u.getHost()) || "raw.githubusercontent.com".equals(u.getHost()))) {
                throw new IOException("Nguồn tải không được hỗ trợ");
            }

            connection = (HttpURLConnection) u.openConnection();
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(20_000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "IRO-Android-LUTs");

            int status = connection.getResponseCode();
            if (status == 403 || status == 429) {
                throw new IOException("GitHub giới hạn lượt tải. Hãy thử lại sau.");
            }
            if (status != 200) {
                throw new IOException("Nguồn trả HTTP " + status);
            }
            if (connection.getContentLengthLong() > limit) {
                throw new IOException("File vượt giới hạn dung lượng");
            }

            try (InputStream in = connection.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                long deadline = System.nanoTime() + 60_000_000_000L;
                while ((n = in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedIOException("Đã hủy tải");
                    }
                    if (System.nanoTime() > deadline) {
                        throw new IOException("Nguồn tải quá chậm");
                    }
                    if (out.size() + n > limit) {
                        throw new IOException("File vượt giới hạn dung lượng");
                    }
                    out.write(buffer, 0, n);
                }
                return out.toByteArray();
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static JSONObject json(String url) throws Exception {
        return new JSONObject(new String(fetch(url, 8 * 1024 * 1024), StandardCharsets.UTF_8));
    }

    static String segment(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
    }

    static String path(String s) throws Exception {
        StringJoiner joined = new StringJoiner("/");
        for (String part : s.split("/")) {
            joined.add(segment(part));
        }
        return joined.toString();
    }

    /**
     * Liệt kê toàn bộ tệp {@code .cube} trong nhánh mặc định của kho GitHub công khai.
     */
    static JSONArray discover(String requested) throws Exception {
        String repo = repository(requested);
        String base = "https://api.github.com/repos/" + repo;
        DebugLogger.d(DebugLogger.TAG_ONLINE, "Đang truy vấn danh mục LUT từ kho: " + repo);

        JSONObject metadata = json(base);
        String branch = metadata.getString("default_branch");
        JSONObject commit = json(base + "/commits/" + segment(branch));
        String revision = commit.getString("sha");

        JSONObject tree = json(base + "/git/trees/" + revision + "?recursive=1");
        if (tree.optBoolean("truncated")) {
            throw new IOException("Kho quá lớn để liệt kê đầy đủ; hãy dùng kho nhỏ hơn.");
        }

        JSONArray nodes = tree.getJSONArray("tree");
        JSONArray result = new JSONArray();
        String license = "Chưa xác định";
        JSONObject info = metadata.optJSONObject("license");
        if (info != null) {
            license = info.optString("spdx_id", license);
        }

        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            String file = node.optString("path");
            String sha = node.optString("sha");
            if (!"blob".equals(node.optString("type"))
                    || !file.toLowerCase(Locale.ROOT).endsWith(".cube")
                    || node.optLong("size") > MAX_LUT
                    || !sha.matches("[0-9a-f]{40}")) {
                continue;
            }
            if (result.length() >= 1000) {
                throw new IOException("Kho có hơn 1000 LUT; dùng kho nhỏ hơn.");
            }

            JSONObject item = new JSONObject();
            item.put("repo", repo);
            item.put("path", file);
            item.put("name", file.substring(file.lastIndexOf('/') + 1).replaceFirst("(?i)\\.cube$", ""));
            item.put("sha", sha);
            item.put("revision", revision);
            item.put("bytes", node.optLong("size"));
            item.put("license", license);
            item.put("input", repo.equalsIgnoreCase(DEFAULT_REPO)
                    ? "sRGB / LUMIX Standard (theo tác giả)"
                    : "Chưa xác định — xem hướng dẫn nguồn");
            result.put(item);
        }
        return result;
    }

    /**
     * Tính mã băm Git blob SHA-1 chuẩn ({@code "blob <size>\0<data>"}).
     */
    static String blobHash(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        digest.update(("blob " + data.length + "\0").getBytes(StandardCharsets.US_ASCII));
        byte[] hash = digest.digest(data);
        StringBuilder b = new StringBuilder();
        for (byte v : hash) {
            b.append(String.format(Locale.ROOT, "%02x", v & 255));
        }
        return b.toString();
    }

    /**
     * Trả về đường dẫn lưu trữ cục bộ theo mã SHA-1 của file LUT.
     */
    static File localFile(File directory, JSONObject item) throws Exception {
        String hash = item.getString("sha");
        if (!hash.matches("[0-9a-f]{40}")) {
            throw new IOException("Mã file không hợp lệ");
        }
        return new File(directory, "online-" + hash + ".cube");
    }

    /**
     * Tải, kiểm tra SHA-1, kiểm tra định dạng 3D {@code .cube} và lưu nguyên tử xuống thư mục của ứng dụng.
     */
    static File download(File directory, JSONObject item) throws Exception {
        String repo = repository(item.getString("repo"));
        String revision = item.getString("revision");
        if (!revision.matches("[0-9a-f]{40}")) {
            throw new IOException("Phiên bản nguồn không hợp lệ");
        }

        File target = localFile(directory, item);
        if (target.isFile()) {
            return target;
        }

        String rawUrl = "https://raw.githubusercontent.com/" + repo + "/" + revision + "/" + path(item.getString("path"));
        byte[] data = fetch(rawUrl, MAX_LUT);

        // Kiểm tra toàn vẹn dữ liệu bằng Git blob SHA-1
        if (!blobHash(data).equals(item.getString("sha"))) {
            throw new IOException("Checksum LUT không khớp nguồn");
        }

        // Phân tích thử để đảm bảo là LUT 3D hợp lệ trước khi lưu
        CubeLut.parse(new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.UTF_8));

        File temp = File.createTempFile("lut-download-", ".tmp", directory);
        try {
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(data);
                out.getFD().sync();
            }
            if (!temp.renameTo(target)) {
                throw new IOException("Không lưu được LUT");
            }
        } finally {
            if (temp.exists()) {
                temp.delete();
            }
        }

        DebugLogger.d(DebugLogger.TAG_ONLINE, "Đã tải và xác thực LUT: " + item.optString("name"));
        return target;
    }
}

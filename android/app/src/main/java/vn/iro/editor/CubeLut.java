package vn.iro.editor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Reader;
import java.util.Arrays;

/**
 * Bộ đọc và nội suy bảng tra màu 3D LUT định dạng chuẩn Adobe {@code .cube}.
 * Chuyển đổi trực tiếp từ file {@code ios/IRO/Core/CubeLut.swift}.
 *
 * <p>Cấu trúc dữ liệu và thuật toán:
 * <ul>
 *     <li>Thứ tự lưu trữ điểm màu trong chuẩn {@code .cube}: kênh Đỏ (R) thay đổi nhanh nhất,
 *         tiếp theo là Xanh lá (G), và Xanh dương (B) thay đổi chậm nhất:
 *         {@code index = (rIndex + gIndex * size + bIndex * size * size) * 3}.</li>
 *     <li>Hỗ trợ kích thước lưới từ {@code 2} đến {@code 65} điểm mỗi chiều (tối đa {@code 65^3 = 274.625} điểm màu).</li>
 *     <li>Hỗ trợ miền giá trị đầu vào tùy chỉnh {@code DOMAIN_MIN} và {@code DOMAIN_MAX}.</li>
 *     <li>Sử dụng nội suy tam tuyến tính (trilinear interpolation) trên 8 đỉnh của hình lập phương con bao quanh điểm màu.</li>
 * </ul>
 */
public final class CubeLut {

    /** Kích thước mỗi chiều của lưới LUT 3D (2..65). */
    public final int size;

    /** Mảng phẳng chứa các bộ ba (R, G, B) kiểu float, độ dài = {@code size * size * size * 3}. */
    public final float[] values;

    /** Miền giá trị đầu vào nhỏ nhất [minR, minG, minB], mặc định [0, 0, 0]. */
    public final float[] min;

    /** Miền giá trị đầu vào lớn nhất [maxR, maxG, maxB], mặc định [1, 1, 1]. */
    public final float[] max;

    /** Tên hiển thị của LUT đọc từ thẻ {@code TITLE} trong file {@code .cube}. */
    public final String title;

    private CubeLut(int size, float[] values, float[] min, float[] max, String title) {
        this.size = size;
        this.values = values;
        this.min = min;
        this.max = max;
        this.title = title;
    }

    /**
     * Phân tích cú pháp luồng văn bản {@code .cube} thành đối tượng {@link CubeLut}.
     *
     * @param reader Luồng đọc văn bản UTF-8.
     * @return Đối tượng {@link CubeLut} đã kiểm tra toàn vẹn dữ liệu.
     * @throws IOException Nếu file sai định dạng, là LUT 1D, vượt 300.000 dòng hoặc thiếu điểm màu.
     */
    public static CubeLut parse(Reader reader) throws IOException {
        BufferedReader buffered = new BufferedReader(reader);
        int size = 0;
        int count = 0;
        int lineNumber = 0;
        float[] data = null;
        float[] lo = {0f, 0f, 0f};
        float[] hi = {1f, 1f, 1f};
        String title = "LUT tùy chỉnh";
        String line;

        try {
            while ((line = buffered.readLine()) != null) {
                lineNumber++;
                // Kiểm tra hủy luồng mỗi 1024 dòng (giống lineNumber & 1023 == 0 trên iOS)
                if ((lineNumber & 1023) == 0 && Thread.currentThread().isInterrupted()) {
                    throw new InterruptedIOException("Đã hủy đọc LUT");
                }
                if (lineNumber > 300_000) {
                    throw new IOException("LUT vượt giới hạn số dòng (300.000)");
                }

                // Loại bỏ phần ghi chú bắt đầu bằng dấu '#'
                int commentIndex = line.indexOf('#');
                if (commentIndex >= 0) {
                    line = line.substring(0, commentIndex);
                }
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split("\\s+");
                switch (parts[0]) {
                    case "TITLE":
                        title = line.substring(5).trim().replace("\"", "");
                        break;

                    case "LUT_1D_SIZE":
                        throw new IOException("Chỉ hỗ trợ LUT 3D .cube");

                    case "LUT_3D_SIZE":
                        if (data != null || parts.length != 2) {
                            throw new IOException("Kích thước LUT bị lặp hoặc sai");
                        }
                        size = Integer.parseInt(parts[1]);
                        if (size < 2 || size > 65) {
                            throw new IOException("LUT cần từ 2 đến 65 điểm mỗi chiều");
                        }
                        data = new float[size * size * size * 3];
                        break;

                    case "DOMAIN_MIN":
                        lo = parseTriple(parts);
                        break;

                    case "DOMAIN_MAX":
                        hi = parseTriple(parts);
                        break;

                    default:
                        if (parts.length != 3 || data == null || count + 3 > data.length) {
                            throw new IOException("Dữ liệu LUT không khớp kích thước ở dòng " + lineNumber);
                        }
                        for (String token : parts) {
                            float val = Float.parseFloat(token);
                            if (!Float.isFinite(val)) {
                                throw new IOException("Giá trị LUT không hữu hạn ở dòng " + lineNumber);
                            }
                            data[count++] = val;
                        }
                        break;
                }
            }
        } catch (NumberFormatException e) {
            throw new IOException("Số không hợp lệ ở dòng " + lineNumber, e);
        }

        if (data == null || count != data.length) {
            throw new IOException("LUT thiếu điểm màu");
        }
        for (int i = 0; i < 3; i++) {
            if (!Float.isFinite(lo[i]) || !Float.isFinite(hi[i]) || lo[i] >= hi[i]) {
                throw new IOException("Miền màu không hợp lệ");
            }
        }

        DebugLogger.d(DebugLogger.TAG_LUT, "Đã nạp LUT 3D '" + title + "' kích thước " + size + "^3");
        return new CubeLut(size, data, lo, hi, title);
    }

    private static float[] parseTriple(String[] parts) throws IOException {
        if (parts.length != 4) {
            throw new IOException("Miền LUT cần 3 giá trị");
        }
        return new float[]{
                Float.parseFloat(parts[1]),
                Float.parseFloat(parts[2]),
                Float.parseFloat(parts[3])
        };
    }

    /**
     * Lấy mẫu màu từ LUT 3D bằng thuật toán nội suy tam tuyến tính (trilinear interpolation).
     * Ghi kết quả trực tiếp vào mảng {@code out} do bên gọi cấp phát để tránh tạo rác bộ nhớ (GC) trong vòng lặp pixel.
     *
     * @param r   Giá trị kênh Đỏ đầu vào [0..1].
     * @param g   Giá trị kênh Xanh lá đầu vào [0..1].
     * @param b   Giá trị kênh Xanh dương đầu vào [0..1].
     * @param out Mảng độ dài 3 chứa kết quả [outR, outG, outB].
     */
    public void sample(double r, double g, double b, double[] out) {
        // Chuẩn hóa tọa độ (r, g, b) theo DOMAIN_MIN..DOMAIN_MAX về [0 .. size - 1]
        double vr = clamp((r - min[0]) / (max[0] - min[0])) * (size - 1);
        double vg = clamp((g - min[1]) / (max[1] - min[1])) * (size - 1);
        double vb = clamp((b - min[2]) / (max[2] - min[2])) * (size - 1);

        // Chỉ số nguyên của góc dưới-trái-trước trong ô lập phương con
        int ir = (int) vr;
        int ig = (int) vg;
        int ib = (int) vb;

        // Phần lẻ thập phân dùng làm trọng số nội suy dọc theo 3 trục
        double fr = vr - ir;
        double fg = vg - ig;
        double fb = vb - ib;

        Arrays.fill(out, 0.0);

        // Tổ hợp tuyến tính 8 đỉnh của hình lập phương con
        for (int z = 0; z < 2; z++) {
            for (int y = 0; y < 2; y++) {
                for (int x = 0; x < 2; x++) {
                    int idxR = Math.min(ir + x, size - 1);
                    int idxG = Math.min(ig + y, size - 1);
                    int idxB = Math.min(ib + z, size - 1);
                    int baseIndex = (idxR + idxG * size + idxB * size * size) * 3;

                    double weight = (x == 0 ? 1.0 - fr : fr)
                            * (y == 0 ? 1.0 - fg : fg)
                            * (z == 0 ? 1.0 - fb : fb);

                    for (int k = 0; k < 3; k++) {
                        out[k] += values[baseIndex + k] * weight;
                    }
                }
            }
        }
    }

    private static double clamp(double x) {
        return Math.max(0.0, Math.min(1.0, x));
    }

    /**
     * Xuất toàn bộ công thức màu hiện tại thành văn bản bảng tra màu chuẩn 3D LUT Adobe .cube.
     * Cho phép mang công thức màu sang DaVinci Resolve, Premiere Pro, Final Cut Pro, CapCut.
     *
     * @param s        Bộ thông số Settings đang hiệu chỉnh.
     * @param lut      LUT 3D đang chọn (nếu có).
     * @param cubeSize Kích thước lưới lập phương (thường là 33 hoặc 17).
     * @return Chuỗi nội dung định dạng .cube chuẩn quốc tế.
     */
    public static String bakeRecipeToCube(Settings s, CubeLut lut, int cubeSize) {
        int n = Math.max(2, Math.min(65, cubeSize));
        StringBuilder sb = new StringBuilder(n * n * n * 28 + 256);
        sb.append("# Created by IRO Studio 3.0\n");
        sb.append("TITLE \"IRO ").append(s.film != null ? s.film.replace("\"", "") : "Custom").append("\"\n");
        sb.append("LUT_3D_SIZE ").append(n).append("\n");
        sb.append("DOMAIN_MIN 0.0 0.0 0.0\n");
        sb.append("DOMAIN_MAX 1.0 1.0 1.0\n");

        int total = n * n * n;
        int[] inPixels = new int[total];
        int idx = 0;
        for (int b = 0; b < n; b++) {
            int bByte = Math.round((float) b / (n - 1) * 255.0f);
            for (int g = 0; g < n; g++) {
                int gByte = Math.round((float) g / (n - 1) * 255.0f);
                for (int r = 0; r < n; r++) {
                    int rByte = Math.round((float) r / (n - 1) * 255.0f);
                    inPixels[idx++] = 0xff000000 | (rByte << 16) | (gByte << 8) | bByte;
                }
            }
        }

        Settings bakeSettings = s.copy();
        bakeSettings.grain = 0.0;
        bakeSettings.sharpness = 0.0;
        bakeSettings.nr = -4.0;
        bakeSettings.swirlyBokeh = 0.0;

        int[] outPixels = ColorEngine.process(inPixels, n, n * n, bakeSettings, lut);

        for (int p : outPixels) {
            float outR = ((p >>> 16) & 255) / 255.0f;
            float outG = ((p >>> 8) & 255) / 255.0f;
            float outB = (p & 255) / 255.0f;
            sb.append(String.format(java.util.Locale.ROOT, "%.6f %.6f %.6f\n", outR, outG, outB));
        }

        return sb.toString();
    }
}

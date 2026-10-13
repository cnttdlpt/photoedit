package android.graphics;

/**
 * Lớp tương thích {@code android.graphics.Bitmap} tối giản cho bản máy tính Windows Desktop,
 * giúp các lớp lõi {@code AutoBalancer} và {@code LightCraftEngine} dùng chung 100% mã nguồn với Android.
 */
public final class Bitmap {

    public enum Config {
        ARGB_8888
    }

    private final int width;
    private final int height;
    private final int[] pixels;

    private Bitmap(int width, int height) {
        this.width = width;
        this.height = height;
        this.pixels = new int[Math.max(1, width * height)];
    }

    public static Bitmap createBitmap(int width, int height, Config config) {
        return new Bitmap(width, height);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void getPixels(int[] dst, int offset, int stride, int x, int y, int w, int h) {
        for (int row = 0; row < h; row++) {
            System.arraycopy(this.pixels, (y + row) * this.width + x, dst, offset + row * stride, w);
        }
    }

    public void setPixels(int[] src, int offset, int stride, int x, int y, int w, int h) {
        for (int row = 0; row < h; row++) {
            System.arraycopy(src, offset + row * stride, this.pixels, (y + row) * this.width + x, w);
        }
    }
}

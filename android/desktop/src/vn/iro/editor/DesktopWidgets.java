package vn.iro.editor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Bộ thành phần giao diện đồ họa chuyên dụng cho IRO Studio trên máy tính Windows:
 * <ul>
 *     <li>{@link HistogramPanel}: Biểu đồ RGB + Luminance thời gian thực.</li>
 *     <li>{@link ToneCurveWidget}: Đồ thị đường cong bậc ba đơn điệu {@link LightCraftEngine.MonotoneCurve} tương tác chuột.</li>
 *     <li>{@link ColorWheelWidget}: Bánh xe pha màu 3-Way OkLab Color Grading (Shadows / Midtones / Highlights).</li>
 *     <li>{@link ImageCanvasPanel}: Bàn dựng ảnh trung tâm hỗ trợ Zoom, Pan, Chia đôi Trước/Sau (Split View) và Khung EXIF.</li>
 *     <li>{@link DesktopFujiSampleLoader}: Bộ nạp ảnh mẫu Fuji X Weekly 3 tầng (RAM -> Resource đóng gói sẵn -> HTTPS Cache).</li>
 * </ul>
 */
public final class DesktopWidgets {

    public static final Color BG_DARK = new Color(0x11, 0x13, 0x16);
    public static final Color BG_PANEL = new Color(0x17, 0x1A, 0x1E);
    public static final Color BG_CARD = new Color(0x1F, 0x24, 0x2B);
    public static final Color BG_INPUT = new Color(0x26, 0x2C, 0x34);
    public static final Color BORDER_COLOR = new Color(0x2D, 0x33, 0x3D);
    public static final Color TEXT_PRIMARY = new Color(0xF1, 0xF3, 0xF4);
    public static final Color TEXT_SECONDARY = new Color(0x9A, 0xA0, 0xA6);
    public static final Color ACCENT_BLUE = new Color(0x8A, 0xB4, 0xF8);
    public static final Color ACCENT_GREEN = new Color(0x81, 0xC9, 0x95);
    public static final Color ACCENT_AMBER = new Color(0xFD, 0xD6, 0x63);

    public static final Font FONT_UI = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 15);
    public static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 11);
    public static final Font FONT_MONO = new Font("Consolas", Font.PLAIN, 12);

    private DesktopWidgets() {
    }

    /** Tạo nút bấm phẳng chuẩn Dark Studio. */
    public static JButton createStudioButton(String text, boolean accent) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BOLD);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (accent) {
            btn.setBackground(new Color(0x2A, 0x4B, 0x7C));
            btn.setForeground(new Color(0xE8, 0xF0, 0xFE));
            btn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ACCENT_BLUE, 1),
                    BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        } else {
            btn.setBackground(BG_CARD);
            btn.setForeground(TEXT_PRIMARY);
            btn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER_COLOR, 1),
                    BorderFactory.createEmptyBorder(6, 11, 6, 11)));
        }
        return btn;
    }

    // =========================================================================
    // 1. BIỂU ĐỒ RGB HISTOGRAM THỜI GIAN THỰC
    // =========================================================================
    public static final class HistogramPanel extends JPanel {
        private final int[] rHist = new int[256];
        private final int[] gHist = new int[256];
        private final int[] bHist = new int[256];
        private final int[] lHist = new int[256];
        private int maxCount = 1;

        public HistogramPanel() {
            setPreferredSize(new Dimension(330, 96));
            setBackground(BG_CARD);
            setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        }

        public void updateFromPixels(int[] pixels, int w, int h) {
            for (int i = 0; i < 256; i++) {
                rHist[i] = gHist[i] = bHist[i] = lHist[i] = 0;
            }
            if (pixels == null || pixels.length == 0) {
                maxCount = 1;
                repaint();
                return;
            }
            int step = Math.max(1, pixels.length / 25000);
            for (int i = 0; i < pixels.length; i += step) {
                int p = pixels[i];
                int r = (p >>> 16) & 255;
                int g = (p >>> 8) & 255;
                int b = p & 255;
                int l = Math.min(255, (r * 54 + g * 183 + b * 19) >>> 8);
                rHist[r]++;
                gHist[g]++;
                bHist[b]++;
                lHist[l]++;
            }
            int m = 1;
            for (int i = 2; i < 254; i++) {
                m = Math.max(m, Math.max(Math.max(rHist[i], gHist[i]), Math.max(bHist[i], lHist[i])));
            }
            maxCount = m;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();

            // Lưới chia 4 vùng (Shadows / Darks / Lights / Highlights)
            g2.setColor(new Color(255, 255, 255, 18));
            for (int i = 1; i <= 3; i++) {
                int gx = w * i / 4;
                g2.drawLine(gx, 0, gx, h);
            }
            g2.drawLine(0, h / 2, w, h / 2);

            drawChannel(g2, lHist, w, h, new Color(230, 234, 240, 45), new Color(230, 234, 240, 120));
            drawChannel(g2, rHist, w, h, new Color(255, 82, 82, 55), new Color(255, 100, 100, 190));
            drawChannel(g2, gHist, w, h, new Color(105, 240, 174, 50), new Color(105, 240, 174, 185));
            drawChannel(g2, bHist, w, h, new Color(68, 138, 255, 55), new Color(130, 177, 255, 195));

            g2.setFont(FONT_SMALL);
            g2.setColor(TEXT_SECONDARY);
            g2.drawString("RGB / Luma Histogram", 8, 14);
            g2.dispose();
        }

        private void drawChannel(Graphics2D g2, int[] hist, int w, int h, Color fill, Color stroke) {
            GeneralPath path = new GeneralPath();
            path.moveTo(0, h - 2);
            for (int i = 0; i < 256; i++) {
                float x = (float) i / 255f * (w - 4) + 2f;
                float norm = Math.min(1f, (float) Math.sqrt((double) hist[i] / maxCount));
                float y = (h - 4) - norm * (h - 22);
                path.lineTo(x, y);
            }
            path.lineTo(w - 2, h - 2);
            path.closePath();
            g2.setColor(fill);
            g2.fill(path);

            g2.setColor(stroke);
            g2.setStroke(new BasicStroke(1.3f));
            GeneralPath line = new GeneralPath();
            for (int i = 0; i < 256; i++) {
                float x = (float) i / 255f * (w - 4) + 2f;
                float norm = Math.min(1f, (float) Math.sqrt((double) hist[i] / maxCount));
                float y = (h - 4) - norm * (h - 22);
                if (i == 0) line.moveTo(x, y);
                else line.lineTo(x, y);
            }
            g2.draw(line);
        }
    }

    // =========================================================================
    // 2. ĐỒ THỊ TONE CURVE TƯƠNG TÁC CHUỘT (LIGHTCRAFT SPLINE)
    // =========================================================================
    public static final class ToneCurveWidget extends JComponent {
        private final LightCraftEngine.State state;
        private final Runnable onChanged;
        private int activeChannel = 0; // 0=RGB, 1=Red, 2=Green, 3=Blue
        private int draggingIndex = -1;

        public ToneCurveWidget(LightCraftEngine.State state, Runnable onChanged) {
            this.state = state;
            this.onChanged = onChanged;
            setPreferredSize(new Dimension(310, 230));
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    List<float[]> pts = activePoints();
                    float nx = toNormX(e.getX());
                    float ny = toNormY(e.getY());

                    int hit = findNearestPoint(pts, nx, ny, 0.06f);
                    if (SwingUtilities.isRightMouseButton(e)) {
                        if (hit > 0 && hit < pts.size() - 1) {
                            pts.remove(hit);
                            draggingIndex = -1;
                            repaint();
                            onChanged.run();
                        }
                        return;
                    }
                    if (hit >= 0) {
                        draggingIndex = hit;
                    } else if (pts.size() < 12) {
                        float[] added = new float[]{nx, ny};
                        pts.add(added);
                        pts.sort((a, b) -> Float.compare(a[0], b[0]));
                        draggingIndex = pts.indexOf(added);
                        repaint();
                        onChanged.run();
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (draggingIndex < 0) return;
                    List<float[]> pts = activePoints();
                    if (draggingIndex >= pts.size()) return;
                    float nx = toNormX(e.getX());
                    float ny = toNormY(e.getY());
                    if (draggingIndex == 0) {
                        nx = 0f;
                    } else if (draggingIndex == pts.size() - 1) {
                        nx = 1f;
                    } else {
                        float prevX = pts.get(draggingIndex - 1)[0] + 0.02f;
                        float nextX = pts.get(draggingIndex + 1)[0] - 0.02f;
                        nx = Math.max(prevX, Math.min(nextX, nx));
                    }
                    pts.get(draggingIndex)[0] = nx;
                    pts.get(draggingIndex)[1] = ny;
                    repaint();
                    onChanged.run();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    draggingIndex = -1;
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        public void setActiveChannel(int channel) {
            this.activeChannel = Math.max(0, Math.min(3, channel));
            repaint();
        }

        private List<float[]> activePoints() {
            switch (activeChannel) {
                case 1: return state.redCurve;
                case 2: return state.greenCurve;
                case 3: return state.blueCurve;
                default: return state.masterCurve;
            }
        }

        private float toNormX(int px) {
            int pad = 14;
            int w = Math.max(1, getWidth() - pad * 2);
            return Math.max(0f, Math.min(1f, (float) (px - pad) / w));
        }

        private float toNormY(int py) {
            int pad = 14;
            int h = Math.max(1, getHeight() - pad * 2);
            return Math.max(0f, Math.min(1f, 1f - (float) (py - pad) / h));
        }

        private int findNearestPoint(List<float[]> pts, float nx, float ny, float maxDist) {
            int best = -1;
            float bestD = maxDist * maxDist;
            for (int i = 0; i < pts.size(); i++) {
                float dx = pts.get(i)[0] - nx;
                float dy = pts.get(i)[1] - ny;
                float d2 = dx * dx + dy * dy;
                if (d2 <= bestD) {
                    bestD = d2;
                    best = i;
                }
            }
            return best;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int pad = 14;
            int cw = w - pad * 2;
            int ch = h - pad * 2;

            g2.setColor(BG_CARD);
            g2.fillRoundRect(0, 0, w, h, 10, 10);
            g2.setColor(BORDER_COLOR);
            g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);

            // Lưới 4x4 + đường chéo chuẩn
            g2.setColor(new Color(255, 255, 255, 22));
            for (int i = 1; i <= 3; i++) {
                int gx = pad + cw * i / 4;
                int gy = pad + ch * i / 4;
                g2.drawLine(gx, pad, gx, pad + ch);
                g2.drawLine(pad, gy, pad + cw, gy);
            }
            g2.setColor(new Color(255, 255, 255, 45));
            g2.drawLine(pad, pad + ch, pad + cw, pad);

            // Vẽ 3 kênh R, G, B mờ phía dưới và đường cong đang chọn nổi bật
            float[][] luts = LightCraftEngine.buildToneCurveLuts(state, 256);
            drawLutCurve(g2, luts[0], pad, cw, ch, new Color(255, 95, 95, activeChannel == 1 ? 240 : 95), activeChannel == 1 ? 2.4f : 1.2f);
            drawLutCurve(g2, luts[1], pad, cw, ch, new Color(105, 240, 174, activeChannel == 2 ? 240 : 95), activeChannel == 2 ? 2.4f : 1.2f);
            drawLutCurve(g2, luts[2], pad, cw, ch, new Color(110, 170, 255, activeChannel == 3 ? 240 : 95), activeChannel == 3 ? 2.4f : 1.2f);
            if (activeChannel == 0) {
                drawLutCurve(g2, luts[3], pad, cw, ch, new Color(245, 247, 250, 245), 2.4f);
            }

            // Vẽ các điểm điều khiển của kênh hiện tại
            List<float[]> pts = activePoints();
            Color ptColor = activeChannel == 1 ? new Color(255, 110, 110)
                    : activeChannel == 2 ? new Color(105, 240, 174)
                    : activeChannel == 3 ? new Color(120, 180, 255)
                    : Color.WHITE;
            for (float[] pt : pts) {
                int px = pad + Math.round(pt[0] * cw);
                int py = pad + Math.round((1f - pt[1]) * ch);
                g2.setColor(BG_DARK);
                g2.fillOval(px - 6, py - 6, 12, 12);
                g2.setColor(ptColor);
                g2.setStroke(new BasicStroke(2f));
                g2.drawOval(px - 5, py - 5, 10, 10);
            }

            g2.setFont(FONT_SMALL);
            g2.setColor(TEXT_SECONDARY);
            g2.drawString("Click: thêm/kéo điểm · Chuột phải: xóa điểm", pad + 4, h - 4);
            g2.dispose();
        }

        private void drawLutCurve(Graphics2D g2, float[] lut, int pad, int cw, int ch, Color color, float strokeWidth) {
            g2.setColor(color);
            g2.setStroke(new BasicStroke(strokeWidth));
            GeneralPath path = new GeneralPath();
            for (int i = 0; i < lut.length; i++) {
                float x = pad + (float) i / (lut.length - 1) * cw;
                float y = pad + (1f - lut[i]) * ch;
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            g2.draw(path);
        }
    }

    // =========================================================================
    // 3. BÁNH XE MÀU 3-WAY COLOR GRADING (OKLAB)
    // =========================================================================
    public static final class ColorWheelWidget extends JComponent {
        private final String title;
        private final LightCraftEngine.GradingWheel wheel;
        private final Runnable onChanged;
        private BufferedImage cachedWheel;
        private int cachedSize = -1;

        public ColorWheelWidget(String title, LightCraftEngine.GradingWheel wheel, Runnable onChanged) {
            this.title = title;
            this.wheel = wheel;
            this.onChanged = onChanged;
            setPreferredSize(new Dimension(102, 122));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.getClickCount() == 2 || SwingUtilities.isRightMouseButton(e)) {
                        wheel.hue = 0f;
                        wheel.sat = 0f;
                        repaint();
                        onChanged.run();
                    } else {
                        updateFromMouse(e.getX(), e.getY());
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    updateFromMouse(e.getX(), e.getY());
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        private void updateFromMouse(int mx, int my) {
            int w = getWidth();
            int radius = Math.min(w, getHeight() - 22) / 2 - 6;
            int cx = w / 2;
            int cy = radius + 6;
            float dx = mx - cx;
            float dy = my - cy;
            float dist = (float) Math.hypot(dx, dy);
            float normSat = Math.min(1f, dist / Math.max(1, radius));
            float angleDeg = (float) Math.toDegrees(Math.atan2(dy, dx));
            if (angleDeg < 0f) angleDeg += 360f;
            wheel.hue = angleDeg;
            wheel.sat = normSat * 100f;
            repaint();
            onChanged.run();
        }

        private BufferedImage getWheelImage(int diameter) {
            if (cachedWheel != null && cachedSize == diameter) {
                return cachedWheel;
            }
            cachedSize = diameter;
            cachedWheel = new BufferedImage(diameter, diameter, BufferedImage.TYPE_INT_ARGB);
            float r = diameter / 2f;
            for (int y = 0; y < diameter; y++) {
                for (int x = 0; x < diameter; x++) {
                    float dx = x - r + 0.5f;
                    float dy = y - r + 0.5f;
                    float dist = (float) Math.hypot(dx, dy);
                    if (dist <= r) {
                        float sat = Math.min(1f, dist / r);
                        float hue = (float) Math.toDegrees(Math.atan2(dy, dx));
                        if (hue < 0f) hue += 360f;
                        float[] rgb = LightCraftEngine.hsvToRgb(hue, sat * 0.85f, 0.92f);
                        int alpha = dist > r - 1.5f ? Math.round((r - dist) / 1.5f * 255f) : 255;
                        int ir = Math.round(rgb[0] * 255f);
                        int ig = Math.round(rgb[1] * 255f);
                        int ib = Math.round(rgb[2] * 255f);
                        cachedWheel.setRGB(x, y, (alpha << 24) | (ir << 16) | (ig << 8) | ib);
                    }
                }
            }
            return cachedWheel;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int radius = Math.min(w, getHeight() - 22) / 2 - 6;
            int diameter = Math.max(24, radius * 2);
            int cx = w / 2;
            int cy = radius + 6;

            g2.drawImage(getWheelImage(diameter), cx - radius, cy - radius, null);
            g2.setColor(BORDER_COLOR);
            g2.drawOval(cx - radius, cy - radius, diameter, diameter);

            // Chấm tâm điều khiển
            double rad = Math.toRadians(wheel.hue);
            float dist = (wheel.sat / 100f) * radius;
            int px = Math.round(cx + (float) Math.cos(rad) * dist);
            int py = Math.round(cy + (float) Math.sin(rad) * dist);
            g2.setColor(Color.BLACK);
            g2.fillOval(px - 6, py - 6, 12, 12);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(px - 5, py - 5, 10, 10);

            g2.setFont(FONT_SMALL);
            g2.setColor(TEXT_PRIMARY);
            FontMetrics fm = g2.getFontMetrics();
            String label = title + String.format(Locale.ROOT, " (%.0f%%)", wheel.sat);
            g2.drawString(label, Math.max(2, (w - fm.stringWidth(label)) / 2), cy + radius + 15);
            g2.dispose();
        }
    }

    // =========================================================================
    // 4. BÀN DỰNG ẢNH TRUNG TÂM (ZOOM / PAN / SPLIT BEFORE-AFTER / GALLERY FRAME)
    // =========================================================================
    public static final class ImageCanvasPanel extends JPanel {
        private BufferedImage originalImage;
        private BufferedImage processedImage;
        private boolean showOriginalOnly = false;
        private boolean splitView = false;
        private float splitRatio = 0.5f;
        private int frameMode = 0; // 0=None, 1=White EXIF, 2=Dark Gallery, 3=Polaroid
        private String filmLabel = "Original";
        private String recipeLabel = "";

        private double zoom = 1.0;
        private double panX = 0.0;
        private double panY = 0.0;
        private Point lastMouse;
        private boolean draggingSplit = false;

        public ImageCanvasPanel() {
            setBackground(new Color(0x0D, 0x0F, 0x12));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    lastMouse = e.getPoint();
                    if (e.getClickCount() == 2) {
                        resetView();
                        return;
                    }
                    if (splitView && Math.abs(e.getX() - getSplitScreenX()) < 18) {
                        draggingSplit = true;
                        setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                    } else {
                        draggingSplit = false;
                        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (lastMouse == null) return;
                    if (draggingSplit) {
                        int[] rect = getDrawRect();
                        if (rect[2] > 10) {
                            splitRatio = Math.max(0.05f, Math.min(0.95f, (float) (e.getX() - rect[0]) / rect[2]));
                            repaint();
                        }
                    } else {
                        panX += e.getX() - lastMouse.x;
                        panY += e.getY() - lastMouse.y;
                        lastMouse = e.getPoint();
                        repaint();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    draggingSplit = false;
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    double factor = e.getPreciseWheelRotation() < 0 ? 1.12 : (1.0 / 1.12);
                    zoom = Math.max(0.25, Math.min(5.0, zoom * factor));
                    repaint();
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            addMouseWheelListener(mouse);
        }

        public void setImages(BufferedImage original, BufferedImage processed, String film, String recipeName) {
            this.originalImage = original;
            this.processedImage = processed;
            this.filmLabel = film != null ? film : "Original";
            this.recipeLabel = recipeName != null ? recipeName : "";
            repaint();
        }

        public void setShowOriginalOnly(boolean showOriginalOnly) {
            this.showOriginalOnly = showOriginalOnly;
            repaint();
        }

        public void setSplitView(boolean splitView) {
            this.splitView = splitView;
            repaint();
        }

        public boolean isSplitView() {
            return splitView;
        }

        public void setFrameMode(int frameMode) {
            this.frameMode = frameMode;
            repaint();
        }

        public int getFrameMode() {
            return frameMode;
        }

        public void resetView() {
            zoom = 1.0;
            panX = 0.0;
            panY = 0.0;
            repaint();
        }

        private int[] getDrawRect() {
            BufferedImage img = processedImage != null ? processedImage : originalImage;
            if (img == null) return new int[]{0, 0, 0, 0};
            int cw = getWidth() - 48;
            int ch = getHeight() - 48;
            double scale = Math.min((double) cw / img.getWidth(), (double) ch / img.getHeight()) * zoom;
            int dw = Math.max(1, (int) Math.round(img.getWidth() * scale));
            int dh = Math.max(1, (int) Math.round(img.getHeight() * scale));
            int dx = (getWidth() - dw) / 2 + (int) Math.round(panX);
            int dy = (getHeight() - dh) / 2 + (int) Math.round(panY);
            return new int[]{dx, dy, dw, dh};
        }

        private int getSplitScreenX() {
            int[] r = getDrawRect();
            return r[0] + Math.round(r[2] * splitRatio);
        }

        /** Kết xuất ảnh hoàn chỉnh kèm khung viền triển lãm để xem hoặc lưu file. */
        public static BufferedImage renderFramedImage(BufferedImage src, int frameMode, String film, String recipe) {
            if (src == null || frameMode == 0) {
                return src;
            }
            int w = src.getWidth();
            int h = src.getHeight();
            int padSide = Math.max(24, Math.round(Math.min(w, h) * 0.055f));
            int padBottom = frameMode == 3
                    ? Math.max(76, Math.round(Math.min(w, h) * 0.16f))
                    : Math.max(54, Math.round(Math.min(w, h) * 0.10f));
            int outW = w + padSide * 2;
            int outH = h + padSide + padBottom;

            BufferedImage out = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = out.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            boolean dark = (frameMode == 2);
            g2.setColor(dark ? new Color(0x14, 0x15, 0x18) : new Color(0xFA, 0xF8, 0xF5));
            g2.fillRect(0, 0, outW, outH);

            g2.drawImage(src, padSide, padSide, null);

            // Chữ ký Fujifilm Film Simulation ở lề dưới
            g2.setColor(dark ? new Color(0xE8, 0xEA, 0xED) : new Color(0x20, 0x21, 0x24));
            int fontSize = Math.max(13, Math.round(Math.min(w, h) * 0.022f));
            g2.setFont(new Font("Segoe UI", Font.BOLD, fontSize));
            String title = (recipe != null && !recipe.isEmpty())
                    ? recipe + "  ·  " + film
                    : "FUJIFILM SIMULATION  ·  " + film;
            FontMetrics fm = g2.getFontMetrics();
            int ty = padSide + h + (padBottom + fm.getAscent()) / 2 - 4;
            g2.drawString(title, (outW - fm.stringWidth(title)) / 2, ty);
            g2.dispose();
            return out;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            BufferedImage active = showOriginalOnly ? originalImage : processedImage;
            if (active == null) {
                g2.setFont(FONT_TITLE);
                g2.setColor(TEXT_SECONDARY);
                String msg = "Kéo & thả ảnh vào đây hoặc nhấn 'Mở ảnh (Ctrl+O)'";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2, getHeight() / 2);
                g2.dispose();
                return;
            }

            BufferedImage framedActive = renderFramedImage(active, frameMode, filmLabel, recipeLabel);
            BufferedImage framedOrig = splitView && originalImage != null
                    ? renderFramedImage(originalImage, frameMode, "Ảnh gốc", "")
                    : null;

            int cw = getWidth() - 48;
            int ch = getHeight() - 48;
            double scale = Math.min((double) cw / framedActive.getWidth(), (double) ch / framedActive.getHeight()) * zoom;
            int dw = Math.max(1, (int) Math.round(framedActive.getWidth() * scale));
            int dh = Math.max(1, (int) Math.round(framedActive.getHeight() * scale));
            int dx = (getWidth() - dw) / 2 + (int) Math.round(panX);
            int dy = (getHeight() - dh) / 2 + (int) Math.round(panY);

            // Bóng đổ nhẹ dưới bức ảnh
            g2.setColor(new Color(0, 0, 0, 110));
            g2.fillRoundRect(dx + 4, dy + 6, dw, dh, 8, 8);

            if (splitView && framedOrig != null && !showOriginalOnly) {
                int splitX = dx + Math.round(dw * splitRatio);
                Shape oldClip = g2.getClip();
                g2.setClip(dx, dy, splitX - dx, dh);
                g2.drawImage(framedOrig, dx, dy, dw, dh, null);
                g2.setClip(splitX, dy, (dx + dw) - splitX, dh);
                g2.drawImage(framedActive, dx, dy, dw, dh, null);
                g2.setClip(oldClip);

                // Thanh gạt chia đôi Trước / Sau
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(splitX, dy, splitX, dy + dh);
                g2.setColor(BG_DARK);
                g2.fillOval(splitX - 13, dy + dh / 2 - 13, 26, 26);
                g2.setColor(ACCENT_BLUE);
                g2.drawOval(splitX - 13, dy + dh / 2 - 13, 26, 26);
                g2.setFont(FONT_BOLD);
                g2.drawString("↔", splitX - 6, dy + dh / 2 + 5);

                drawBadge(g2, "TRƯỚC (GỐC)", dx + 12, dy + 12);
                drawBadge(g2, "SAU (ĐÃ CHỈNH)", Math.min(dx + dw - 118, splitX + 12), dy + 12);
            } else {
                g2.drawImage(framedActive, dx, dy, dw, dh, null);
                if (showOriginalOnly) {
                    drawBadge(g2, "ĐANG XEM ẢNH GỐC", dx + 12, dy + 12);
                }
            }

            // Nhãn thông tin góc dưới bàn dựng
            String info = String.format(Locale.ROOT, "%dx%d px  ·  Zoom %.0f%%  ·  Film: %s%s",
                    active.getWidth(), active.getHeight(), zoom * 100.0, filmLabel,
                    recipeLabel.isEmpty() ? "" : ("  ·  " + recipeLabel));
            drawBadge(g2, info, 14, getHeight() - 36);
            g2.dispose();
        }

        private void drawBadge(Graphics2D g2, String text, int x, int y) {
            g2.setFont(FONT_SMALL);
            FontMetrics fm = g2.getFontMetrics();
            int bw = fm.stringWidth(text) + 16;
            int bh = 22;
            g2.setColor(new Color(17, 19, 22, 205));
            g2.fillRoundRect(x, y, bw, bh, 8, 8);
            g2.setColor(BORDER_COLOR);
            g2.drawRoundRect(x, y, bw, bh, 8, 8);
            g2.setColor(TEXT_PRIMARY);
            g2.drawString(text, x + 8, y + 15);
        }
    }

    // =========================================================================
    // 5. BỘ TẢI ẢNH MẪU FUJI X WEEKLY TRÊN WINDOWS DESKTOP
    // =========================================================================
    public static final class DesktopFujiSampleLoader {
        private static final Map<String, BufferedImage> MEM_CACHE = new ConcurrentHashMap<>();
        private static final ExecutorService IO_POOL = Executors.newFixedThreadPool(3, r -> {
            Thread t = new Thread(r, "FujiSampleDesktop");
            t.setDaemon(true);
            return t;
        });

        public interface Callback {
            void onLoaded(BufferedImage image);
        }

        public static BufferedImage getCachedForRecipe(Recipe r) {
            if (r == null) return null;
            String key = getRecipeKey(r);
            if (key == null) return null;
            BufferedImage mem = MEM_CACHE.get(key);
            if (mem != null) return mem;

            if (r.sampleAsset != null && !r.sampleAsset.isEmpty()) {
                BufferedImage bundled = loadResource("/" + r.sampleAsset);
                if (bundled != null) {
                    MEM_CACHE.put(key, bundled);
                    return bundled;
                }
            }
            return null;
        }

        public static void loadRecipeSampleAsync(Recipe r, Callback cb) {
            if (r == null) return;
            BufferedImage instant = getCachedForRecipe(r);
            if (instant != null) {
                cb.onLoaded(instant);
                return;
            }
            String url = r.sampleImageUrl;
            if (url == null || url.isEmpty()) return;
            String key = getRecipeKey(r);
            IO_POOL.submit(() -> {
                BufferedImage img = fetchOrDiskCache(url);
                if (img != null && key != null) {
                    MEM_CACHE.put(key, img);
                    SwingUtilities.invokeLater(() -> cb.onLoaded(img));
                }
            });
        }

        public static BufferedImage loadResource(String resourcePath) {
            try (InputStream in = DesktopFujiSampleLoader.class.getResourceAsStream(resourcePath)) {
                if (in != null) {
                    return ImageIO.read(in);
                }
            } catch (Exception ignored) {
            }
            // Fallback khi chạy trực tiếp từ thư mục gốc project
            File localAsset = new File("app/src/main/assets" + resourcePath);
            if (localAsset.isFile()) {
                try {
                    return ImageIO.read(localAsset);
                } catch (Exception ignored) {
                }
            }
            return null;
        }

        private static String getRecipeKey(Recipe r) {
            if (r.sampleAsset != null && !r.sampleAsset.isEmpty()) return "asset:" + r.sampleAsset;
            if (r.sampleImageUrl != null && !r.sampleImageUrl.isEmpty()) return "url:" + r.sampleImageUrl;
            return null;
        }

        private static BufferedImage fetchOrDiskCache(String urlStr) {
            try {
                File cacheDir = new File(System.getProperty("user.home"), ".iro-studio/fujixweekly-cache");
                if (!cacheDir.exists()) cacheDir.mkdirs();
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                byte[] digest = md.digest(urlStr.getBytes("UTF-8"));
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 10; i++) {
                    sb.append(String.format(Locale.ROOT, "%02x", digest[i] & 255));
                }
                File cachedFile = new File(cacheDir, "fxw-" + sb + ".jpg");
                if (cachedFile.isFile() && cachedFile.length() > 256) {
                    BufferedImage disk = ImageIO.read(cachedFile);
                    if (disk != null) return disk;
                }

                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("User-Agent", "IRO-Studio-Windows/1.0");
                if (conn.getResponseCode() == 200) {
                    try (InputStream in = conn.getInputStream();
                         FileOutputStream out = new FileOutputStream(cachedFile)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) != -1) {
                            out.write(buf, 0, n);
                        }
                    }
                    return ImageIO.read(cachedFile);
                }
            } catch (Exception ignored) {
            }
            return null;
        }
    }
}

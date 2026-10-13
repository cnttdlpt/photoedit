package vn.iro.editor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LinearGradientPaint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
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
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicScrollBarUI;

/**
 * Hệ thống giao diện <b>Adobe Photoshop CC 2025 / Camera Raw 17 (Adobe Spectrum Dark UI)</b>
 * cho phiên bản máy tính Windows Desktop:
 * <ul>
 *     <li>Bảng màu Adobe Spectrum Dark Charcoal ({@code #181818} Canvas, {@code #252525} Dock, {@code #2C2C2C} Toolbar, {@code #1473E6} Photoshop Blue).</li>
 *     <li>{@link SpectrumSlider}: Thanh trượt chuẩn Camera Raw 17 có dải màu Gradient (Temp/WB/HSL), vạch 0 trung tâm và nháy đúp để Reset.</li>
 *     <li>{@link SpectrumScrollBarUI}: Thanh cuộn tối giản 6px tiệp màu nền tối (loại bỏ hoàn toàn thanh cuộn trắng mặc định của Swing).</li>
 *     <li>{@link PhotoshopToolStrip}: Thanh công cụ dọc bên trái kiểu Photoshop CC 2025 kèm logo {@code Ps/Ir} xanh dương phát sáng.</li>
 *     <li>{@link ImageCanvasPanel}: Bàn dựng trung tâm có Tab tài liệu ({@code .RAF @ 100% RGB/8#}) và <b>Contextual Task Bar</b> nổi của Photoshop 2025.</li>
 * </ul>
 */
public final class DesktopWidgets {

    // Bảng màu chuẩn Adobe Photoshop CC 2025 (Spectrum Darkest / Dark Charcoal)
    public static final Color PS_CANVAS_BG = new Color(0x16, 0x16, 0x16);
    public static final Color PS_DARKEST = new Color(0x1D, 0x1D, 0x1D);
    public static final Color PS_PANEL = new Color(0x25, 0x25, 0x25);
    public static final Color PS_HEADER = new Color(0x2C, 0x2C, 0x2C);
    public static final Color PS_SURFACE = new Color(0x32, 0x32, 0x32);
    public static final Color PS_HOVER = new Color(0x3A, 0x3A, 0x3A);
    public static final Color PS_BORDER = new Color(0x38, 0x38, 0x38);
    public static final Color PS_DIVIDER = new Color(0x18, 0x18, 0x18);

    public static final Color PS_BLUE = new Color(0x14, 0x73, 0xE6);
    public static final Color PS_BLUE_LIGHT = new Color(0x37, 0x8E, 0xF0);
    public static final Color PS_LOGO_BG = new Color(0x00, 0x1E, 0x36);
    public static final Color PS_LOGO_FG = new Color(0x31, 0xA8, 0xFF);

    public static final Color TEXT_PRIMARY = new Color(0xE8, 0xE8, 0xE8);
    public static final Color TEXT_SECONDARY = new Color(0xA8, 0xA8, 0xA8);
    public static final Color TEXT_MUTED = new Color(0x78, 0x78, 0x78);
    public static final Color ACCENT_BLUE = PS_BLUE_LIGHT;
    public static final Color ACCENT_GREEN = new Color(0x2D, 0x9D, 0x78);
    public static final Color ACCENT_AMBER = new Color(0xE6, 0x86, 0x19);

    // Giữ bí danh tương thích
    public static final Color BG_DARK = PS_DARKEST;
    public static final Color BG_PANEL = PS_PANEL;
    public static final Color BG_CARD = PS_SURFACE;
    public static final Color BG_INPUT = new Color(0x1F, 0x1F, 0x1F);
    public static final Color BORDER_COLOR = PS_BORDER;

    public static final Font FONT_UI = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 12);
    public static final Font FONT_SECTION = new Font("Segoe UI", Font.BOLD, 11);
    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 11);
    public static final Font FONT_MONO = new Font("Consolas", Font.PLAIN, 11);

    private DesktopWidgets() {
    }

    /** Tạo nút bấm phẳng chuẩn Adobe Spectrum (Photoshop CC 2025). */
    public static JButton createStudioButton(String text, boolean primaryBlue) {
        return new SpectrumButton(text, primaryBlue, false);
    }

    public static JButton createCompactButton(String text, boolean primaryBlue) {
        return new SpectrumButton(text, primaryBlue, true);
    }

    /** Nút bấm vẽ tay theo phong cách Adobe Spectrum Dark (bo góc 4px, hover mượt, không viền trắng Swing). */
    public static final class SpectrumButton extends JButton {
        private final boolean primaryBlue;
        private final boolean compact;
        private boolean activeState = false;

        public SpectrumButton(String text, boolean primaryBlue, boolean compact) {
            super(text);
            this.primaryBlue = primaryBlue;
            this.compact = compact;
            setFont(compact ? FONT_SMALL : FONT_UI);
            setForeground(TEXT_PRIMARY);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setMargin(compact ? new Insets(3, 8, 3, 8) : new Insets(5, 11, 5, 11));
        }

        public void setActiveState(boolean active) {
            this.activeState = active;
            repaint();
        }

        public boolean isActiveState() {
            return activeState;
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            int w = fm.stringWidth(getText()) + (compact ? 18 : 24);
            int h = compact ? 24 : 28;
            return new Dimension(w, h);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            boolean pressed = getModel().isPressed();
            boolean rollover = getModel().isRollover();

            if (primaryBlue || activeState) {
                g2.setColor(pressed ? new Color(0x0D, 0x59, 0xB8) : (rollover ? PS_BLUE_LIGHT : PS_BLUE));
                g2.fillRoundRect(0, 0, w, h, 6, 6);
            } else {
                g2.setColor(pressed ? new Color(0x1C, 0x1C, 0x1C) : (rollover ? PS_HOVER : PS_SURFACE));
                g2.fillRoundRect(0, 0, w, h, 6, 6);
                g2.setColor(rollover ? new Color(0x52, 0x52, 0x52) : new Color(0x42, 0x42, 0x42));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 6, 6);
            }

            g2.setFont(getFont());
            g2.setColor((primaryBlue || activeState) ? Color.WHITE : getForeground());
            FontMetrics fm = g2.getFontMetrics();
            int tx = (w - fm.stringWidth(getText())) / 2;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(getText(), tx, ty);
            g2.dispose();
        }
    }

    /** Bọc JScrollPane với thanh cuộn tối chuẩn Photoshop CC 2025 (không còn viền trắng hay thanh cuộn cổ điển). */
    public static JScrollPane createDarkScrollPane(Component view) {
        JScrollPane sp = new JScrollPane(view);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setBackground(PS_PANEL);
        sp.getViewport().setBackground(PS_PANEL);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        JScrollBar vsb = sp.getVerticalScrollBar();
        vsb.setUnitIncrement(18);
        vsb.setPreferredSize(new Dimension(8, 0));
        vsb.setBackground(PS_PANEL);
        vsb.setUI(new SpectrumScrollBarUI());
        return sp;
    }

    public static final class SpectrumScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            this.thumbColor = new Color(0x50, 0x50, 0x50);
            this.trackColor = PS_PANEL;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zeroButton();
        }

        private static JButton zeroButton() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            g.setColor(PS_PANEL);
            g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isDragging ? PS_BLUE_LIGHT : (isThumbRollover() ? new Color(0x68, 0x68, 0x68) : new Color(0x4A, 0x4A, 0x4A)));
            g2.fillRoundRect(thumbBounds.x + 1, thumbBounds.y + 2, thumbBounds.width - 2, thumbBounds.height - 4, 6, 6);
            g2.dispose();
        }
    }

    // =========================================================================
    // THANH TRƯỢT CHUẨN ADOBE CAMERA RAW 17 / PHOTOSHOP CC 2025 (SPECTRUM SLIDER)
    // =========================================================================
    public static final class SpectrumSlider extends JComponent {
        public interface ValueListener {
            void onValueChanged(double value);
        }

        private final String label;
        private final double min;
        private final double max;
        private final double defaultValue;
        private final String format;
        private final ValueListener listener;
        private double value;
        private boolean hover = false;
        private boolean dragging = false;
        private Color trackLeftColor = null;
        private Color trackRightColor = null;

        public SpectrumSlider(String label, double min, double max, double defaultValue, String format, ValueListener listener) {
            this.label = label;
            this.min = min;
            this.max = max;
            this.defaultValue = defaultValue;
            this.value = defaultValue;
            this.format = format;
            this.listener = listener;
            setPreferredSize(new Dimension(310, 38));
            setMaximumSize(new Dimension(340, 38));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.getClickCount() == 2 || SwingUtilities.isRightMouseButton(e)) {
                        setValue(defaultValue, true);
                        return;
                    }
                    dragging = true;
                    updateFromX(e.getX());
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragging) {
                        updateFromX(e.getX());
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dragging = false;
                    repaint();
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        public SpectrumSlider withGradient(Color left, Color right) {
            this.trackLeftColor = left;
            this.trackRightColor = right;
            return this;
        }

        public double getValue() {
            return value;
        }

        public void setValue(double newVal, boolean notify) {
            double clamped = Math.max(min, Math.min(max, newVal));
            this.value = clamped;
            repaint();
            if (notify && listener != null) {
                listener.onValueChanged(clamped);
            }
        }

        private void updateFromX(int mouseX) {
            int trackX = 8;
            int trackW = Math.max(10, getWidth() - 16);
            double norm = Math.max(0.0, Math.min(1.0, (double) (mouseX - trackX) / trackW));
            double v = min + norm * (max - min);
            // Làm tròn theo bước hợp lý
            if (max - min <= 6.0) {
                v = Math.round(v * 20.0) / 20.0;
            } else if (max - min <= 20.0) {
                v = Math.round(v * 10.0) / 10.0;
            } else {
                v = Math.round(v);
            }
            setValue(v, true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            // Hàng trên: Tên thông số (trái) + Hộp giá trị kiểu Camera Raw (phải)
            g2.setFont(FONT_UI);
            g2.setColor(hover || dragging ? Color.WHITE : TEXT_PRIMARY);
            g2.drawString(label, 6, 14);

            String valStr = String.format(Locale.ROOT, format, value);
            g2.setFont(FONT_MONO);
            FontMetrics fm = g2.getFontMetrics();
            int valW = Math.max(44, fm.stringWidth(valStr) + 12);
            int valX = w - valW - 4;
            g2.setColor(new Color(0x1B, 0x1B, 0x1B));
            g2.fillRoundRect(valX, 1, valW, 17, 4, 4);
            g2.setColor(Math.abs(value - defaultValue) > 1e-3 ? PS_BLUE_LIGHT : new Color(0x38, 0x38, 0x38));
            g2.drawRoundRect(valX, 1, valW - 1, 16, 4, 4);
            g2.setColor(Math.abs(value - defaultValue) > 1e-3 ? PS_BLUE_LIGHT : TEXT_SECONDARY);
            g2.drawString(valStr, valX + (valW - fm.stringWidth(valStr)) / 2, 13);

            // Hàng dưới: Thanh trượt (Track 4px + Fill + Nút tròn)
            int trackX = 8;
            int trackW = Math.max(10, w - 16);
            int trackY = 26;
            int trackH = 4;

            if (trackLeftColor != null && trackRightColor != null) {
                g2.setPaint(new GradientPaint(trackX, trackY, trackLeftColor, trackX + trackW, trackY, trackRightColor));
                g2.fillRoundRect(trackX, trackY, trackW, trackH, 4, 4);
            } else {
                g2.setColor(new Color(0x17, 0x17, 0x17));
                g2.fillRoundRect(trackX, trackY, trackW, trackH, 4, 4);
                g2.setColor(new Color(0x38, 0x38, 0x38));
                g2.drawRoundRect(trackX, trackY, trackW - 1, trackH, 4, 4);

                // Tô màu xanh Photoshop từ mốc default (thường là 0) tới vị trí hiện tại
                double normVal = (value - min) / (max - min);
                double normZero = (defaultValue - min) / (max - min);
                int xVal = trackX + (int) Math.round(normVal * trackW);
                int xZero = trackX + (int) Math.round(normZero * trackW);
                int fillStart = Math.min(xVal, xZero);
                int fillWidth = Math.abs(xVal - xZero);
                if (fillWidth > 0) {
                    g2.setColor(PS_BLUE_LIGHT);
                    g2.fillRoundRect(fillStart, trackY, fillWidth, trackH, 2, 2);
                }
            }

            // Vạch giữa (nếu min < 0 < max)
            if (min < 0 && max > 0) {
                int xMid = trackX + (int) Math.round((-min / (max - min)) * trackW);
                g2.setColor(new Color(255, 255, 255, 70));
                g2.drawLine(xMid, trackY - 2, xMid, trackY + trackH + 2);
            }

            // Nút kéo (Thumb tròn kiểu Adobe Spectrum)
            double norm = (value - min) / (max - min);
            int thumbX = trackX + (int) Math.round(norm * trackW);
            int thumbY = trackY + trackH / 2;
            int r = dragging || hover ? 6 : 5;
            g2.setColor(new Color(0, 0, 0, 140));
            g2.fillOval(thumbX - r, thumbY - r + 1, r * 2, r * 2);
            g2.setColor(dragging ? PS_BLUE_LIGHT : new Color(0xE8, 0xE8, 0xE8));
            g2.fillOval(thumbX - r, thumbY - r, r * 2, r * 2);
            if (hover || dragging) {
                g2.setColor(PS_BLUE);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(thumbX - r, thumbY - r, r * 2, r * 2);
            }
            g2.dispose();
        }
    }

    // =========================================================================
    // THANH CÔNG CỤ DỌC BÊN TRÁI KIỂU PHOTOSHOP CC 2025 (LEFT TOOL STRIP)
    // =========================================================================
    public static final class PhotoshopToolStrip extends JPanel {
        public interface ToolAction {
            void onToolSelected(int index);
        }

        private int activeTool = 0;
        private final ToolAction callback;

        private static final String[][] TOOLS = {
                {"V", "Di chuyển & Kéo ảnh (Move / Pan Tool - V)"},
                {"Z", "Thu phóng bàn dựng (Zoom Loupe - Z)"},
                {"Y", "Chia đôi Trước / Sau (Split Comparison - Y)"},
                {"B", "Tự động cân bằng ánh sáng AI (Auto-Balance - B)"},
                {"O", "Mặt nạ Elip cục bộ (Radial Gradient Mask)"},
                {"G", "Mặt nạ Chuyển sắc (Linear Gradient Mask)"},
                {"A", "Mặt nạ Bầu trời AI (Sky Selection Mask)"},
                {"F", "Khung viền chữ ký Fujifilm EXIF (Frame Tool)"}
        };

        public PhotoshopToolStrip(ToolAction callback) {
            this.callback = callback;
            setPreferredSize(new Dimension(42, 600));
            setBackground(PS_PANEL);
            setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, PS_DIVIDER));

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int idx = (e.getY() - 12) / 38;
                    if (idx >= 0 && idx < TOOLS.length) {
                        activeTool = idx;
                        repaint();
                        if (callback != null) callback.onToolSelected(idx);
                    }
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    int idx = (e.getY() - 12) / 38;
                    if (idx >= 0 && idx < TOOLS.length) {
                        setToolTipText(TOOLS[idx][1]);
                    } else {
                        setToolTipText(null);
                    }
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        public void setActiveTool(int idx) {
            this.activeTool = idx;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            for (int i = 0; i < TOOLS.length; i++) {
                int y = 12 + i * 38;
                boolean selected = (i == activeTool);
                if (selected) {
                    g2.setColor(new Color(0x38, 0x38, 0x38));
                    g2.fillRoundRect(5, y, 32, 32, 6, 6);
                    g2.setColor(PS_BLUE_LIGHT);
                    g2.fillRoundRect(0, y + 6, 3, 20, 2, 2);
                }
                drawToolVectorIcon(g2, i, 21, y + 16, selected ? Color.WHITE : TEXT_SECONDARY);
            }

            // Ô màu Foreground / Background kinh điển ở cuối thanh công cụ Photoshop
            int sy = getHeight() - 72;
            if (sy > TOOLS.length * 38 + 24) {
                g2.setColor(new Color(0x38, 0x38, 0x38));
                g2.drawLine(8, sy - 10, 34, sy - 10);
                // Ô sau (Background swatch)
                g2.setColor(new Color(0x1E, 0x1E, 0x1E));
                g2.fillRect(16, sy + 8, 16, 16);
                g2.setColor(TEXT_SECONDARY);
                g2.drawRect(16, sy + 8, 16, 16);
                // Ô trước (Foreground swatch)
                g2.setColor(new Color(0xF0, 0xF0, 0xF0));
                g2.fillRect(9, sy, 16, 16);
                g2.setColor(PS_BORDER);
                g2.drawRect(9, sy, 16, 16);
            }
            g2.dispose();
        }

        private void drawToolVectorIcon(Graphics2D g2, int idx, int cx, int cy, Color c) {
            g2.setColor(c);
            g2.setStroke(new BasicStroke(1.6f));
            switch (idx) {
                case 0: // Move cross arrows
                    g2.drawLine(cx - 7, cy, cx + 7, cy);
                    g2.drawLine(cx, cy - 7, cx, cy + 7);
                    g2.drawLine(cx - 7, cy, cx - 4, cy - 3);
                    g2.drawLine(cx + 7, cy, cx + 4, cy - 3);
                    break;
                case 1: // Zoom magnifier
                    g2.drawOval(cx - 7, cy - 7, 10, 10);
                    g2.drawLine(cx + 2, cy + 2, cx + 7, cy + 7);
                    break;
                case 2: // Split Before/After
                    g2.drawRoundRect(cx - 8, cy - 7, 16, 14, 3, 3);
                    g2.drawLine(cx, cy - 8, cx, cy + 8);
                    break;
                case 3: // Auto-Balance Magic Sparkle
                    g2.drawLine(cx - 6, cy + 6, cx + 5, cy - 5);
                    g2.fillOval(cx + 3, cy - 7, 4, 4);
                    g2.drawLine(cx - 5, cy - 5, cx - 1, cy - 5);
                    g2.drawLine(cx - 3, cy - 7, cx - 3, cy - 3);
                    break;
                case 4: // Radial Mask
                    g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{3f, 2f}, 0));
                    g2.drawOval(cx - 8, cy - 6, 16, 12);
                    break;
                case 5: // Linear Gradient Mask
                    g2.drawRect(cx - 7, cy - 7, 14, 14);
                    g2.drawLine(cx - 7, cy - 1, cx + 7, cy - 1);
                    g2.drawLine(cx - 7, cy + 3, cx + 7, cy + 3);
                    break;
                case 6: // AI Sky / Subject
                    g2.drawArc(cx - 8, cy - 4, 10, 8, 0, 180);
                    g2.drawArc(cx - 2, cy - 6, 10, 10, 0, 180);
                    g2.drawLine(cx - 7, cy + 4, cx + 7, cy + 4);
                    break;
                default: // Frame Tool
                    g2.drawRect(cx - 8, cy - 7, 16, 14);
                    g2.drawRect(cx - 5, cy - 4, 10, 7);
                    break;
            }
        }
    }

    // =========================================================================
    // 1. BIỂU ĐỒ RGB HISTOGRAM CHUẨN ADOBE CAMERA RAW 17
    // =========================================================================
    public static final class HistogramPanel extends JPanel {
        private final int[] rHist = new int[256];
        private final int[] gHist = new int[256];
        private final int[] bHist = new int[256];
        private final int[] lHist = new int[256];
        private int maxCount = 1;
        private String metaSummary = "ISO Auto  ·  sRGB IEC61966-2.1  ·  8-bit";

        public HistogramPanel() {
            setPreferredSize(new Dimension(340, 118));
            setBackground(new Color(0x19, 0x19, 0x19));
            setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, PS_BORDER));
        }

        public void setMetaSummary(String text) {
            this.metaSummary = text != null ? text : "";
            repaint();
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
            int h = getHeight() - 22;

            // Lưới chia 5 vùng chuẩn Camera Raw (Blacks / Shadows / Exposure / Highlights / Whites)
            g2.setColor(new Color(255, 255, 255, 16));
            for (int i = 1; i <= 4; i++) {
                int gx = w * i / 5;
                g2.drawLine(gx, 0, gx, h);
            }
            g2.drawLine(0, h / 2, w, h / 2);

            drawChannel(g2, lHist, w, h, new Color(220, 220, 220, 38), new Color(220, 220, 220, 110));
            drawChannel(g2, rHist, w, h, new Color(235, 70, 70, 65), new Color(255, 95, 95, 210));
            drawChannel(g2, gHist, w, h, new Color(60, 215, 130, 58), new Color(80, 235, 150, 200));
            drawChannel(g2, bHist, w, h, new Color(55, 140, 255, 68), new Color(95, 170, 255, 215));

            // Thanh thông tin EXIF / Camera Raw dưới đáy Histogram
            g2.setColor(new Color(0x22, 0x22, 0x22));
            g2.fillRect(0, h, w, 22);
            g2.setColor(PS_BORDER);
            g2.drawLine(0, h, w, h);
            g2.setFont(FONT_MONO);
            g2.setColor(TEXT_SECONDARY);
            g2.drawString(metaSummary, 8, h + 15);
            g2.dispose();
        }

        private void drawChannel(Graphics2D g2, int[] hist, int w, int h, Color fill, Color stroke) {
            GeneralPath path = new GeneralPath();
            path.moveTo(0, h - 1);
            for (int i = 0; i < 256; i++) {
                float x = (float) i / 255f * (w - 4) + 2f;
                float norm = Math.min(1f, (float) Math.sqrt((double) hist[i] / maxCount));
                float y = (h - 3) - norm * (h - 12);
                path.lineTo(x, y);
            }
            path.lineTo(w - 2, h - 1);
            path.closePath();
            g2.setColor(fill);
            g2.fill(path);

            g2.setColor(stroke);
            g2.setStroke(new BasicStroke(1.2f));
            GeneralPath line = new GeneralPath();
            for (int i = 0; i < 256; i++) {
                float x = (float) i / 255f * (w - 4) + 2f;
                float norm = Math.min(1f, (float) Math.sqrt((double) hist[i] / maxCount));
                float y = (h - 3) - norm * (h - 12);
                if (i == 0) line.moveTo(x, y);
                else line.lineTo(x, y);
            }
            g2.draw(line);
        }
    }

    // =========================================================================
    // 2. ĐỒ THỊ TONE CURVE TƯƠNG TÁC CHUỘT (CAMERA RAW CURVE)
    // =========================================================================
    public static final class ToneCurveWidget extends JComponent {
        private final LightCraftEngine.State state;
        private final Runnable onChanged;
        private int activeChannel = 0; // 0=RGB, 1=Red, 2=Green, 3=Blue
        private int draggingIndex = -1;

        public ToneCurveWidget(LightCraftEngine.State state, Runnable onChanged) {
            this.state = state;
            this.onChanged = onChanged;
            setPreferredSize(new Dimension(310, 225));
            setMaximumSize(new Dimension(335, 225));
            setAlignmentX(Component.LEFT_ALIGNMENT);
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
            int pad = 12;
            int w = Math.max(1, getWidth() - pad * 2);
            return Math.max(0f, Math.min(1f, (float) (px - pad) / w));
        }

        private float toNormY(int py) {
            int pad = 12;
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
            int pad = 12;
            int cw = w - pad * 2;
            int ch = h - pad * 2;

            g2.setColor(new Color(0x1A, 0x1A, 0x1A));
            g2.fillRoundRect(0, 0, w, h, 6, 6);
            g2.setColor(PS_BORDER);
            g2.drawRoundRect(0, 0, w - 1, h - 1, 6, 6);

            g2.setColor(new Color(255, 255, 255, 20));
            for (int i = 1; i <= 3; i++) {
                int gx = pad + cw * i / 4;
                int gy = pad + ch * i / 4;
                g2.drawLine(gx, pad, gx, pad + ch);
                g2.drawLine(pad, gy, pad + cw, gy);
            }
            g2.setColor(new Color(255, 255, 255, 42));
            g2.drawLine(pad, pad + ch, pad + cw, pad);

            float[][] luts = LightCraftEngine.buildToneCurveLuts(state, 256);
            drawLutCurve(g2, luts[0], pad, cw, ch, new Color(255, 90, 90, activeChannel == 1 ? 245 : 85), activeChannel == 1 ? 2.2f : 1.1f);
            drawLutCurve(g2, luts[1], pad, cw, ch, new Color(80, 235, 150, activeChannel == 2 ? 245 : 85), activeChannel == 2 ? 2.2f : 1.1f);
            drawLutCurve(g2, luts[2], pad, cw, ch, new Color(80, 165, 255, activeChannel == 3 ? 245 : 85), activeChannel == 3 ? 2.2f : 1.1f);
            if (activeChannel == 0) {
                drawLutCurve(g2, luts[3], pad, cw, ch, new Color(240, 240, 240, 245), 2.2f);
            }

            List<float[]> pts = activePoints();
            Color ptColor = activeChannel == 1 ? new Color(255, 105, 105)
                    : activeChannel == 2 ? new Color(80, 235, 150)
                    : activeChannel == 3 ? new Color(95, 175, 255)
                    : Color.WHITE;
            for (float[] pt : pts) {
                int px = pad + Math.round(pt[0] * cw);
                int py = pad + Math.round((1f - pt[1]) * ch);
                g2.setColor(new Color(0x18, 0x18, 0x18));
                g2.fillOval(px - 5, py - 5, 10, 10);
                g2.setColor(ptColor);
                g2.setStroke(new BasicStroke(1.8f));
                g2.drawOval(px - 5, py - 5, 10, 10);
            }
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
    // 3. BÁNH XE MÀU 3-WAY COLOR GRADING (CAMERA RAW COLOR WHEELS)
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
            setPreferredSize(new Dimension(100, 118));
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
            int radius = Math.min(w, getHeight() - 22) / 2 - 5;
            int cx = w / 2;
            int cy = radius + 5;
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
                        float[] rgb = LightCraftEngine.hsvToRgb(hue, sat * 0.85f, 0.90f);
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
            int radius = Math.min(w, getHeight() - 22) / 2 - 5;
            int diameter = Math.max(24, radius * 2);
            int cx = w / 2;
            int cy = radius + 5;

            g2.drawImage(getWheelImage(diameter), cx - radius, cy - radius, null);
            g2.setColor(PS_BORDER);
            g2.drawOval(cx - radius, cy - radius, diameter, diameter);

            double rad = Math.toRadians(wheel.hue);
            float dist = (wheel.sat / 100f) * radius;
            int px = Math.round(cx + (float) Math.cos(rad) * dist);
            int py = Math.round(cy + (float) Math.sin(rad) * dist);
            g2.setColor(Color.BLACK);
            g2.fillOval(px - 5, py - 5, 10, 10);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.8f));
            g2.drawOval(px - 4, py - 4, 8, 8);

            g2.setFont(FONT_SMALL);
            g2.setColor(TEXT_SECONDARY);
            FontMetrics fm = g2.getFontMetrics();
            String label = title + String.format(Locale.ROOT, " %.0f%%", wheel.sat);
            g2.drawString(label, Math.max(2, (w - fm.stringWidth(label)) / 2), cy + radius + 15);
            g2.dispose();
        }
    }

    // =========================================================================
    // 4. BÀN DỰNG ẢNH TRUNG TÂM KIỂU PHOTOSHOP CC 2025 + CONTEXTUAL TASK BAR
    // =========================================================================
    public static final class ImageCanvasPanel extends JPanel {
        private BufferedImage originalImage;
        private BufferedImage processedImage;
        private boolean showOriginalOnly = false;
        private boolean splitView = false;
        private float splitRatio = 0.5f;
        private int frameMode = 0; // 0=None, 1=White EXIF, 2=Dark Gallery, 3=Polaroid
        private String filmLabel = "Classic Chrome";
        private String recipeLabel = "Golden Negative";
        private String docName = "Golden_Negative.RAF";

        private double zoom = 1.0;
        private double panX = 0.0;
        private double panY = 0.0;
        private Point lastMouse;
        private boolean draggingSplit = false;
        private Runnable onZoomChanged;

        public ImageCanvasPanel() {
            setBackground(PS_CANVAS_BG);
            setLayout(null);
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
                    if (onZoomChanged != null) onZoomChanged.run();
                    repaint();
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            addMouseWheelListener(mouse);
        }

        public void setOnZoomChanged(Runnable r) {
            this.onZoomChanged = r;
        }

        public double getZoom() {
            return zoom;
        }

        public void setZoom(double z) {
            this.zoom = Math.max(0.25, Math.min(5.0, z));
            if (onZoomChanged != null) onZoomChanged.run();
            repaint();
        }

        public void setDocumentName(String name) {
            if (name != null && !name.isEmpty()) {
                this.docName = name;
            }
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
            if (onZoomChanged != null) onZoomChanged.run();
            repaint();
        }

        private int[] getDrawRect() {
            BufferedImage img = processedImage != null ? processedImage : originalImage;
            if (img == null) return new int[]{0, 0, 0, 0};
            int topDocBar = 30;
            int bottomTaskBar = 58;
            int cw = getWidth() - 56;
            int ch = getHeight() - topDocBar - bottomTaskBar - 36;
            double scale = Math.min((double) cw / img.getWidth(), (double) ch / img.getHeight()) * zoom;
            int dw = Math.max(1, (int) Math.round(img.getWidth() * scale));
            int dh = Math.max(1, (int) Math.round(img.getHeight() * scale));
            int dx = (getWidth() - dw) / 2 + (int) Math.round(panX);
            int dy = topDocBar + (getHeight() - topDocBar - bottomTaskBar - dh) / 2 + (int) Math.round(panY);
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
            g2.setColor(dark ? new Color(0x12, 0x12, 0x14) : new Color(0xFA, 0xF8, 0xF5));
            g2.fillRect(0, 0, outW, outH);
            g2.drawImage(src, padSide, padSide, null);

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
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            int w = getWidth();
            int h = getHeight();

            // 1. Thanh Document Tab chuẩn Photoshop CC 2025 ở đỉnh vùng Canvas
            g2.setColor(new Color(0x20, 0x20, 0x20));
            g2.fillRect(0, 0, w, 28);
            g2.setColor(PS_DIVIDER);
            g2.drawLine(0, 27, w, 27);

            String tabText = String.format(Locale.ROOT, "  %s  @ %.0f%% (%s · RGB/8#)  ×  ",
                    docName, zoom * 100.0, recipeLabel.isEmpty() ? filmLabel : recipeLabel);
            g2.setFont(FONT_UI);
            FontMetrics tfm = g2.getFontMetrics();
            int tabW = tfm.stringWidth(tabText) + 16;
            g2.setColor(PS_CANVAS_BG);
            g2.fillRect(0, 0, tabW, 28);
            g2.setColor(PS_BLUE_LIGHT);
            g2.fillRect(0, 0, tabW, 2);
            g2.setColor(PS_DIVIDER);
            g2.drawLine(tabW, 0, tabW, 27);
            g2.setColor(TEXT_PRIMARY);
            g2.drawString(tabText, 6, 18);

            // Thước đo ngang & dọc mảnh (Photoshop Rulers Ctrl+R)
            drawPhotoshopRulers(g2, w, h, 28);

            BufferedImage active = showOriginalOnly ? originalImage : processedImage;
            if (active == null) {
                g2.setFont(FONT_TITLE);
                g2.setColor(TEXT_SECONDARY);
                String msg = "Kéo & thả ảnh vào bàn dựng hoặc nhấn Ctrl+O để mở ảnh";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2);
                g2.dispose();
                return;
            }

            BufferedImage framedActive = renderFramedImage(active, frameMode, filmLabel, recipeLabel);
            BufferedImage framedOrig = splitView && originalImage != null
                    ? renderFramedImage(originalImage, frameMode, "Before (Original)", "")
                    : null;

            int topDocBar = 44;
            int bottomTaskBar = 58;
            int cw = w - 64;
            int ch = h - topDocBar - bottomTaskBar - 24;
            double scale = Math.min((double) cw / framedActive.getWidth(), (double) ch / framedActive.getHeight()) * zoom;
            int dw = Math.max(1, (int) Math.round(framedActive.getWidth() * scale));
            int dh = Math.max(1, (int) Math.round(framedActive.getHeight() * scale));
            int dx = (w - dw) / 2 + (int) Math.round(panX);
            int dy = topDocBar + (h - topDocBar - bottomTaskBar - dh) / 2 + (int) Math.round(panY);

            // Bóng đổ Artboard Photoshop
            g2.setColor(new Color(0, 0, 0, 160));
            g2.fillRect(dx + 4, dy + 6, dw, dh);

            if (splitView && framedOrig != null && !showOriginalOnly) {
                int splitX = dx + Math.round(dw * splitRatio);
                Shape oldClip = g2.getClip();
                g2.setClip(dx, dy, splitX - dx, dh);
                g2.drawImage(framedOrig, dx, dy, dw, dh, null);
                g2.setClip(splitX, dy, (dx + dw) - splitX, dh);
                g2.drawImage(framedActive, dx, dy, dw, dh, null);
                g2.setClip(oldClip);

                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1.8f));
                g2.drawLine(splitX, dy, splitX, dy + dh);
                g2.setColor(PS_PANEL);
                g2.fillOval(splitX - 12, dy + dh / 2 - 12, 24, 24);
                g2.setColor(PS_BLUE_LIGHT);
                g2.drawOval(splitX - 12, dy + dh / 2 - 12, 24, 24);
                g2.setFont(FONT_BOLD);
                g2.setColor(Color.WHITE);
                g2.drawString("↔", splitX - 6, dy + dh / 2 + 4);

                drawBadge(g2, "BEFORE (RAW)", dx + 10, dy + 10);
                drawBadge(g2, "AFTER (" + (recipeLabel.isEmpty() ? filmLabel : recipeLabel) + ")", Math.min(dx + dw - 150, splitX + 10), dy + 10);
            } else {
                g2.drawImage(framedActive, dx, dy, dw, dh, null);
                if (showOriginalOnly) {
                    drawBadge(g2, "BEFORE · ẢNH GỐC", dx + 10, dy + 10);
                }
            }

            // Viền 1px quanh Artboard chuẩn Photoshop
            g2.setColor(new Color(0x0A, 0x0A, 0x0A));
            g2.drawRect(dx - 1, dy - 1, dw + 1, dh + 1);
            g2.dispose();
        }

        private void drawPhotoshopRulers(Graphics2D g2, int w, int h, int topY) {
            int rSize = 15;
            g2.setColor(new Color(0x22, 0x22, 0x22));
            g2.fillRect(0, topY, w, rSize);
            g2.fillRect(0, topY, rSize, h - topY);
            g2.setColor(new Color(0x32, 0x32, 0x32));
            g2.drawLine(0, topY + rSize, w, topY + rSize);
            g2.drawLine(rSize, topY, rSize, h);

            g2.setColor(new Color(0x6E, 0x6E, 0x6E));
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 9));
            for (int x = rSize + 20; x < w; x += 50) {
                g2.drawLine(x, topY + rSize - 5, x, topY + rSize);
                g2.drawString(String.valueOf(x - rSize), x + 2, topY + 10);
            }
            for (int x = rSize + 10; x < w; x += 10) {
                g2.drawLine(x, topY + rSize - 2, x, topY + rSize);
            }
            for (int y = topY + rSize + 20; y < h; y += 50) {
                g2.drawLine(rSize - 5, y, rSize, y);
            }
            for (int y = topY + rSize + 10; y < h; y += 10) {
                g2.drawLine(rSize - 2, y, rSize, y);
            }
        }

        private void drawBadge(Graphics2D g2, String text, int x, int y) {
            g2.setFont(FONT_SMALL);
            FontMetrics fm = g2.getFontMetrics();
            int bw = fm.stringWidth(text) + 14;
            int bh = 20;
            g2.setColor(new Color(20, 20, 20, 215));
            g2.fillRoundRect(x, y, bw, bh, 5, 5);
            g2.setColor(PS_BORDER);
            g2.drawRoundRect(x, y, bw, bh, 5, 5);
            g2.setColor(TEXT_PRIMARY);
            g2.drawString(text, x + 7, y + 14);
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

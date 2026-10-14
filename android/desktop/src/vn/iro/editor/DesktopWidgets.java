package vn.iro.editor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
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
 * Bộ thành phần giao diện đồng bộ 100% phong cách <b>Android Tablet Minimal Rounded Studio</b>
 * (bo tròn mềm mại 28px, thẻ trắng nổi trên nền xám sáng {@code #F6F7F9}, pill đen {@code #111318},
 * thước kẻ vạch {@link TickRulerWidget} giống {@code TickRulerView.java} của Android).
 */
public final class DesktopWidgets {

    // Bảng màu chuẩn Android Minimal Studio (đồng bộ từ StudioStyle.java & MainActivity.java)
    public static final Color APP_BG = new Color(0xF5, 0xF6, 0xF9);
    public static final Color CARD_BG = new Color(0xFF, 0xFF, 0xFF);
    public static final Color CARD_BORDER = new Color(0xE3, 0xE6, 0xEC);
    public static final Color SURFACE_SOFT = new Color(0xF1, 0xF3, 0xF7);
    public static final Color SURFACE_HOVER = new Color(0xE8, 0xEC, 0xF2);
    public static final Color SURFACE_WARM = new Color(0xF5, 0xEF, 0xE6);

    public static final Color INK_DARK = new Color(0x11, 0x13, 0x18);
    public static final Color INK_HOVER = new Color(0x26, 0x2A, 0x32);
    public static final Color TEXT_PRIMARY = new Color(0x16, 0x18, 0x1D);
    public static final Color TEXT_SECONDARY = new Color(0x63, 0x69, 0x75);
    public static final Color TEXT_MUTED = new Color(0x8C, 0x93, 0xA0);

    public static final Color ACCENT_BLUE = new Color(0x1A, 0x73, 0xE8);
    public static final Color ACCENT_GREEN = new Color(0x1E, 0x8E, 0x3E);
    public static final Color ACCENT_RED = new Color(0xD9, 0x30, 0x25);

    public static final Font FONT_UI = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 16);
    public static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 11);
    public static final Font FONT_SMALL_BOLD = new Font("Segoe UI", Font.BOLD, 11);
    public static final Font FONT_MONO = new Font("Consolas", Font.BOLD, 12);

    private DesktopWidgets() {
    }

    // =========================================================================
    // THẺ BO TRÒN NỔI (ROUNDED FLOATING CARD 28px GIỐNG ANDROID TABLET)
    // =========================================================================
    public static class RoundedCardPanel extends JPanel {
        private final int radius;
        private Color fillColor;
        private Color borderColor;

        public RoundedCardPanel(int radius, Color fillColor, Color borderColor) {
            this.radius = radius;
            this.fillColor = fillColor;
            this.borderColor = borderColor;
            setOpaque(false);
        }

        public void setCardColors(Color fill, Color border) {
            this.fillColor = fill;
            this.borderColor = border;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            if (fillColor != null) {
                g2.setColor(fillColor);
                g2.fillRoundRect(0, 0, w - 1, h - 1, radius, radius);
            }
            if (borderColor != null) {
                g2.setColor(borderColor);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, radius, radius);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // =========================================================================
    // NÚT VIÊN THUỐC BO TRÒN (ANDROID MATERIAL 3 PILL BUTTON)
    // =========================================================================
    public static final class PillButton extends JButton {
        private boolean activeDark;
        private boolean softFill;
        private int cornerRadius = 999;

        public PillButton(String text, boolean activeDark, boolean softFill) {
            super(text);
            this.activeDark = activeDark;
            this.softFill = softFill;
            setFont(FONT_BOLD);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setMargin(new Insets(6, 14, 6, 14));
        }

        public PillButton withRadius(int r) {
            this.cornerRadius = r;
            return this;
        }

        public void setActiveDark(boolean activeDark) {
            this.activeDark = activeDark;
            repaint();
        }

        public boolean isActiveDark() {
            return activeDark;
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            Insets ins = getMargin();
            int w = fm.stringWidth(getText()) + ins.left + ins.right + 8;
            int h = Math.max(32, fm.getHeight() + ins.top + ins.bottom);
            return new Dimension(w, h);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int r = Math.min(cornerRadius, h);
            boolean hover = getModel().isRollover();
            boolean pressed = getModel().isPressed();

            if (activeDark) {
                g2.setColor(pressed ? Color.BLACK : (hover ? INK_HOVER : INK_DARK));
                g2.fillRoundRect(0, 0, w - 1, h - 1, r, r);
            } else if (softFill) {
                g2.setColor(pressed ? CARD_BORDER : (hover ? SURFACE_HOVER : SURFACE_SOFT));
                g2.fillRoundRect(0, 0, w - 1, h - 1, r, r);
            } else {
                g2.setColor(hover ? SURFACE_SOFT : CARD_BG);
                g2.fillRoundRect(0, 0, w - 1, h - 1, r, r);
                g2.setColor(CARD_BORDER);
                g2.drawRoundRect(0, 0, w - 1, h - 1, r, r);
            }

            g2.setFont(getFont());
            g2.setColor(activeDark ? Color.WHITE : TEXT_PRIMARY);
            FontMetrics fm = g2.getFontMetrics();
            int tx = (w - fm.stringWidth(getText())) / 2;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(getText(), tx, ty);
            g2.dispose();
        }
    }

    /** Nút tròn icon giống nút Undo / Redo / Menu / Back trên Android Tablet. */
    public static final class CircleIconButton extends JButton {
        private final String iconType;
        private final int size;
        private boolean filledSoft;

        public CircleIconButton(String iconType, int size, boolean filledSoft, String tooltip) {
            this.iconType = iconType;
            this.size = size;
            this.filledSoft = filledSoft;
            setToolTipText(tooltip);
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(new Dimension(size, size));
            setMaximumSize(new Dimension(size, size));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            boolean hover = getModel().isRollover();

            g2.setColor(hover ? SURFACE_HOVER : (filledSoft ? SURFACE_SOFT : CARD_BG));
            g2.fillOval(1, 1, w - 3, h - 3);
            g2.setColor(CARD_BORDER);
            g2.drawOval(1, 1, w - 3, h - 3);

            int cx = w / 2;
            int cy = h / 2;
            g2.setColor(TEXT_PRIMARY);
            g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            switch (iconType) {
                case "open": // Dấu + hoặc thư mục mở ảnh
                    g2.drawLine(cx - 6, cy, cx + 6, cy);
                    g2.drawLine(cx, cy - 6, cx, cy + 6);
                    break;
                case "undo": // Mũi tên hoàn tác cong trái
                    g2.drawArc(cx - 6, cy - 4, 12, 10, 0, 180);
                    g2.drawLine(cx - 6, cy + 1, cx - 3, cy - 3);
                    g2.drawLine(cx - 6, cy + 1, cx - 2, cy + 3);
                    break;
                case "redo": // Mũi tên làm lại cong phải
                    g2.drawArc(cx - 6, cy - 4, 12, 10, 0, 180);
                    g2.drawLine(cx + 6, cy + 1, cx + 3, cy - 3);
                    g2.drawLine(cx + 6, cy + 1, cx + 2, cy + 3);
                    break;
                case "reset": // Vòng tròn đặt lại
                    g2.drawArc(cx - 5, cy - 5, 10, 10, 45, 280);
                    g2.drawLine(cx + 2, cy - 5, cx + 5, cy - 5);
                    g2.drawLine(cx + 5, cy - 5, cx + 5, cy - 2);
                    break;
                case "magic": // Đũa thần tự động cân bằng
                    g2.drawLine(cx - 6, cy + 6, cx + 5, cy - 5);
                    g2.fillOval(cx + 3, cy - 7, 3, 3);
                    g2.fillOval(cx - 4, cy - 5, 2, 2);
                    g2.fillOval(cx + 5, cy + 1, 2, 2);
                    break;
                default: // Ba chấm (...)
                    g2.fillOval(cx - 7, cy - 1, 3, 3);
                    g2.fillOval(cx - 1, cy - 1, 3, 3);
                    g2.fillOval(cx + 5, cy - 1, 3, 3);
                    break;
            }
            g2.dispose();
        }
    }

    /** Thanh cuộn mảnh bo tròn sáng màu chuẩn Android. */
    public static JScrollPane createMinimalScrollPane(Component view) {
        JScrollPane sp = new JScrollPane(view);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        JScrollBar vsb = sp.getVerticalScrollBar();
        vsb.setUnitIncrement(20);
        vsb.setPreferredSize(new Dimension(6, 0));
        vsb.setOpaque(false);
        vsb.setUI(new MinimalScrollBarUI());
        return sp;
    }

    public static final class MinimalScrollBarUI extends BasicScrollBarUI {
        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zeroBtn();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zeroBtn();
        }

        private static JButton zeroBtn() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            return b;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isDragging ? new Color(0x88, 0x90, 0x9E) : new Color(0xC6, 0xCC, 0xD6));
            g2.fillRoundRect(thumbBounds.x, thumbBounds.y + 2, thumbBounds.width, thumbBounds.height - 4, 6, 6);
            g2.dispose();
        }
    }

    // =========================================================================
    // THANH NAVIGATION RAIL BÊN TRÁI GIỐNG HỆT BẢN ANDROID TABLET
    // =========================================================================
    public static final class AndroidNavRail extends RoundedCardPanel {
        public interface NavListener {
            void onSectionSelected(int index);
            void onOpenPhotoClicked();
            void onAutoBalanceClicked();
        }

        private int activeIndex = 0; // 0=Tinh chỉnh, 1=Màu phim, 2=Khung ảnh, 3=LightCraft
        private final NavItem[] items = new NavItem[5];

        public AndroidNavRail(NavListener listener) {
            super(28, CARD_BG, CARD_BORDER);
            setPreferredSize(new Dimension(88, 600));
            setLayout(null);

            CircleIconButton openBtn = new CircleIconButton("open", 44, false, "Mở ảnh mới từ máy tính (Ctrl+O)");
            openBtn.setBounds(22, 18, 44, 44);
            openBtn.addActionListener(e -> listener.onOpenPhotoClicked());
            add(openBtn);

            String[][] navDefs = {
                    {"sliders", "Tinh chỉnh"},
                    {"star", "Màu phim"},
                    {"frame", "Khung ảnh"},
                    {"curve", "LightCraft"},
                    {"library", "Kho 392"}
            };

            for (int i = 0; i < navDefs.length; i++) {
                final int idx = i;
                items[i] = new NavItem(navDefs[i][0], navDefs[i][1], i == 4);
                items[i].addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (idx < 4) {
                            setActiveIndex(idx);
                        }
                        listener.onSectionSelected(idx);
                    }
                });
                add(items[i]);
            }
            items[0].setSelected(true);

            CircleIconButton autoBtn = new CircleIconButton("magic", 44, false, "Tự động cân bằng ảnh AI (Ctrl+B)");
            autoBtn.addActionListener(e -> listener.onAutoBalanceClicked());
            add(autoBtn);

            addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    layoutRail(openBtn, autoBtn);
                }
            });
        }

        private void layoutRail(JComponent openBtn, JComponent autoBtn) {
            int h = getHeight();
            openBtn.setBounds(22, 18, 44, 44);
            int totalNavH = 5 * 64;
            int startY = Math.max(82, (h - totalNavH) / 2);
            for (int i = 0; i < items.length; i++) {
                items[i].setBounds(6, startY + i * 64, 76, 58);
            }
            autoBtn.setBounds(22, Math.max(startY + totalNavH + 12, h - 76), 44, 44);
        }

        public void setActiveIndex(int index) {
            this.activeIndex = index;
            for (int i = 0; i < 4; i++) {
                items[i].setSelected(i == index);
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(FONT_SMALL);
            g2.setColor(TEXT_SECONDARY);
            String autoLbl = "Tự động";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(autoLbl, (getWidth() - fm.stringWidth(autoLbl)) / 2, getHeight() - 14);
            g2.dispose();
        }

        private static final class NavItem extends JComponent {
            private final String iconType;
            private final String label;
            private final boolean warmBadge;
            private boolean selected = false;
            private boolean hover = false;

            NavItem(String iconType, String label, boolean warmBadge) {
                this.iconType = iconType;
                this.label = label;
                this.warmBadge = warmBadge;
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                    @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                });
            }

            void setSelected(boolean s) {
                this.selected = s;
                repaint();
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int w = getWidth();
                int pillW = 54;
                int pillH = 34;
                int pillX = (w - pillW) / 2;
                int pillY = 2;

                if (selected) {
                    g2.setColor(INK_DARK);
                    g2.fillRoundRect(pillX, pillY, pillW, pillH, 34, 34);
                } else if (warmBadge) {
                    g2.setColor(hover ? new Color(0xEB, 0xE2, 0xD5) : SURFACE_WARM);
                    g2.fillRoundRect(pillX, pillY, pillW, pillH, 34, 34);
                    g2.setColor(new Color(0xE0, 0xD6, 0xC5));
                    g2.drawRoundRect(pillX, pillY, pillW - 1, pillH - 1, 34, 34);
                } else if (hover) {
                    g2.setColor(SURFACE_SOFT);
                    g2.fillRoundRect(pillX, pillY, pillW, pillH, 34, 34);
                }

                // Vẽ icon vector ở giữa viên thuốc
                int cx = w / 2;
                int cy = pillY + pillH / 2;
                g2.setColor(selected ? Color.WHITE : TEXT_PRIMARY);
                g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                switch (iconType) {
                    case "sliders":
                        g2.drawLine(cx - 7, cy - 4, cx + 7, cy - 4);
                        g2.drawLine(cx - 7, cy + 4, cx + 7, cy + 4);
                        g2.fillOval(cx - 3, cy - 7, 6, 6);
                        g2.fillOval(cx + 1, cy + 1, 6, 6);
                        break;
                    case "star":
                    case "library":
                        GeneralPath star = new GeneralPath();
                        star.moveTo(cx, cy - 7);
                        star.quadTo(cx, cy, cx + 7, cy);
                        star.quadTo(cx, cy, cx, cy + 7);
                        star.quadTo(cx, cy, cx - 7, cy);
                        star.quadTo(cx, cy, cx, cy - 7);
                        star.closePath();
                        g2.draw(star);
                        break;
                    case "frame":
                        g2.drawRoundRect(cx - 7, cy - 7, 14, 14, 3, 3);
                        g2.drawRect(cx - 4, cy - 4, 8, 8);
                        break;
                    case "curve":
                        g2.drawRoundRect(cx - 7, cy - 7, 14, 14, 4, 4);
                        g2.drawLine(cx - 5, cy + 5, cx + 5, cy - 5);
                        break;
                }

                g2.setFont(selected || warmBadge ? FONT_SMALL_BOLD : FONT_SMALL);
                g2.setColor(selected ? INK_DARK : TEXT_SECONDARY);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(label, (w - fm.stringWidth(label)) / 2, pillY + pillH + 16);
                g2.dispose();
            }
        }
    }

    // =========================================================================
    // THƯỚC KẺ VẠCH (TICK RULER WIDGET GIỐNG TICKRULERVIEW.JAVA TRÊN ANDROID)
    // =========================================================================
    public static final class TickRulerWidget extends JComponent {
        public interface ValueChangeListener {
            void onChanged(double newValue);
        }

        private double min = -3.0;
        private double max = 3.0;
        private double value = 0.0;
        private double defaultValue = 0.0;
        private ValueChangeListener listener;
        private int lastMouseX;

        public TickRulerWidget(ValueChangeListener listener) {
            this.listener = listener;
            setPreferredSize(new Dimension(350, 48));
            setMaximumSize(new Dimension(500, 48));
            setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.getClickCount() == 2 || SwingUtilities.isRightMouseButton(e)) {
                        setValue(defaultValue, true);
                        return;
                    }
                    lastMouseX = e.getX();
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    int dx = e.getX() - lastMouseX;
                    lastMouseX = e.getX();
                    double span = max - min;
                    double delta = (dx / (double) Math.max(80, getWidth() - 36)) * span;
                    setValue(value + delta, true);
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    double step = (max - min) / 40.0;
                    double dir = e.getPreciseWheelRotation() < 0 ? 1.0 : -1.0;
                    setValue(value + dir * step, true);
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            addMouseWheelListener(mouse);
        }

        public void configure(double min, double max, double defaultVal, double currentVal) {
            this.min = min;
            this.max = max;
            this.defaultValue = defaultVal;
            this.value = Math.max(min, Math.min(max, currentVal));
            repaint();
        }

        public void setValue(double v, boolean notify) {
            double clamped = Math.max(min, Math.min(max, v));
            if (max - min <= 6.0) {
                clamped = Math.round(clamped * 10.0) / 10.0;
            } else if (max - min <= 20.0) {
                clamped = Math.round(clamped * 2.0) / 2.0;
            } else {
                clamped = Math.round(clamped);
            }
            this.value = clamped;
            repaint();
            if (notify && listener != null) {
                listener.onChanged(clamped);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();

            // Nền viên thuốc xám nhạt giống hệt Android TickRulerView
            g2.setColor(SURFACE_SOFT);
            g2.fillRoundRect(0, 0, w - 1, h - 1, 18, 18);
            g2.setColor(CARD_BORDER);
            g2.drawRoundRect(0, 0, w - 1, h - 1, 18, 18);

            int pad = 18;
            int trackW = Math.max(20, w - pad * 2);
            int ticks = 40;
            int midY = h / 2 + 2;

            for (int i = 0; i <= ticks; i++) {
                int tx = pad + (i * trackW) / ticks;
                boolean major = (i % 5 == 0);
                int th = major ? 18 : 11;
                g2.setColor(major ? new Color(0x45, 0x4A, 0x54) : new Color(0xB0, 0xB6, 0xC2));
                g2.setStroke(new BasicStroke(major ? 1.5f : 1.0f));
                g2.drawLine(tx, midY - th / 2, tx, midY + th / 2);
            }

            // Con trỏ tam giác + vạch đen đậm tại vị trí giá trị hiện tại
            double norm = (value - min) / Math.max(1e-6, max - min);
            int px = pad + (int) Math.round(norm * trackW);
            g2.setColor(INK_DARK);
            g2.setStroke(new BasicStroke(2.4f));
            g2.drawLine(px, midY - 12, px, midY + 12);

            int[] triX = {px - 5, px + 5, px};
            int[] triY = {5, 5, 12};
            g2.fillPolygon(triX, triY, 3);
            g2.dispose();
        }
    }

    // =========================================================================
    // 1. BIỂU ĐỒ RGB HISTOGRAM BO TRÒN SÁNG MÀU GIỐNG ANDROID TABLET
    // =========================================================================
    public static final class HistogramPanel extends JPanel {
        private final int[] rHist = new int[256];
        private final int[] gHist = new int[256];
        private final int[] bHist = new int[256];
        private int maxCount = 1;
        private int avgR = 112, avgG = 126, avgB = 141;

        public HistogramPanel() {
            setPreferredSize(new Dimension(350, 96));
            setOpaque(false);
        }

        public void updateFromPixels(int[] pixels, int w, int h) {
            for (int i = 0; i < 256; i++) {
                rHist[i] = gHist[i] = bHist[i] = 0;
            }
            if (pixels == null || pixels.length == 0) {
                maxCount = 1;
                repaint();
                return;
            }
            long sr = 0, sg = 0, sb = 0, cnt = 0;
            int step = Math.max(1, pixels.length / 25000);
            for (int i = 0; i < pixels.length; i += step) {
                int p = pixels[i];
                int r = (p >>> 16) & 255;
                int g = (p >>> 8) & 255;
                int b = p & 255;
                rHist[r]++;
                gHist[g]++;
                bHist[b]++;
                sr += r;
                sg += g;
                sb += b;
                cnt++;
            }
            if (cnt > 0) {
                avgR = (int) (sr / cnt);
                avgG = (int) (sg / cnt);
                avgB = (int) (sb / cnt);
            }
            int m = 1;
            for (int i = 2; i < 254; i++) {
                m = Math.max(m, Math.max(rHist[i], Math.max(gHist[i], bHist[i])));
            }
            maxCount = m;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();

            g2.setColor(SURFACE_SOFT);
            g2.fillRoundRect(0, 0, w - 1, h - 1, 18, 18);
            g2.setColor(CARD_BORDER);
            g2.drawRoundRect(0, 0, w - 1, h - 1, 18, 18);

            // Lưới dọc mảnh
            g2.setColor(new Color(0, 0, 0, 14));
            for (int i = 1; i <= 3; i++) {
                int gx = w * i / 4;
                g2.drawLine(gx, 24, gx, h - 8);
            }

            drawChannel(g2, rHist, w, h, new Color(217, 48, 37, 28), new Color(217, 48, 37, 195));
            drawChannel(g2, gHist, w, h, new Color(30, 142, 62, 28), new Color(30, 142, 62, 195));
            drawChannel(g2, bHist, w, h, new Color(26, 115, 232, 28), new Color(26, 115, 232, 195));

            g2.setFont(FONT_SMALL_BOLD);
            g2.setColor(TEXT_SECONDARY);
            g2.drawString(String.format(Locale.ROOT, "Histogram · R %d  G %d  B %d", avgR, avgG, avgB), 12, 18);
            g2.setColor(INK_DARK);
            g2.drawString("RGB", w - 34, 18);
            g2.dispose();
        }

        private void drawChannel(Graphics2D g2, int[] hist, int w, int h, Color fill, Color stroke) {
            int padX = 10;
            int topY = 26;
            int botY = h - 8;
            GeneralPath path = new GeneralPath();
            path.moveTo(padX, botY);
            for (int i = 0; i < 256; i++) {
                float x = padX + (float) i / 255f * (w - padX * 2);
                float norm = Math.min(1f, (float) Math.sqrt((double) hist[i] / maxCount));
                float y = botY - norm * (botY - topY);
                path.lineTo(x, y);
            }
            path.lineTo(w - padX, botY);
            path.closePath();
            g2.setColor(fill);
            g2.fill(path);

            g2.setColor(stroke);
            g2.setStroke(new BasicStroke(1.5f));
            GeneralPath line = new GeneralPath();
            for (int i = 0; i < 256; i++) {
                float x = padX + (float) i / 255f * (w - padX * 2);
                float norm = Math.min(1f, (float) Math.sqrt((double) hist[i] / maxCount));
                float y = botY - norm * (botY - topY);
                if (i == 0) line.moveTo(x, y);
                else line.lineTo(x, y);
            }
            g2.draw(line);
        }
    }

    // =========================================================================
    // 2. ĐỒ THỊ TONE CURVE BO TRÒN SÁNG MÀU (LIGHTCRAFT SPLINE)
    // =========================================================================
    public static final class ToneCurveWidget extends JComponent {
        private final LightCraftEngine.State state;
        private final Runnable onChanged;
        private int activeChannel = 0;
        private int draggingIndex = -1;

        public ToneCurveWidget(LightCraftEngine.State state, Runnable onChanged) {
            this.state = state;
            this.onChanged = onChanged;
            setPreferredSize(new Dimension(340, 220));
            setMaximumSize(new Dimension(420, 220));
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
                    if (draggingIndex == 0) nx = 0f;
                    else if (draggingIndex == pts.size() - 1) nx = 1f;
                    else {
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

            g2.setColor(SURFACE_SOFT);
            g2.fillRoundRect(0, 0, w - 1, h - 1, 20, 20);
            g2.setColor(CARD_BORDER);
            g2.drawRoundRect(0, 0, w - 1, h - 1, 20, 20);

            g2.setColor(new Color(0, 0, 0, 18));
            for (int i = 1; i <= 3; i++) {
                int gx = pad + cw * i / 4;
                int gy = pad + ch * i / 4;
                g2.drawLine(gx, pad, gx, pad + ch);
                g2.drawLine(pad, gy, pad + cw, gy);
            }
            g2.setColor(new Color(0, 0, 0, 35));
            g2.drawLine(pad, pad + ch, pad + cw, pad);

            float[][] luts = LightCraftEngine.buildToneCurveLuts(state, 256);
            drawLutCurve(g2, luts[0], pad, cw, ch, new Color(217, 48, 37, activeChannel == 1 ? 240 : 90), activeChannel == 1 ? 2.4f : 1.2f);
            drawLutCurve(g2, luts[1], pad, cw, ch, new Color(30, 142, 62, activeChannel == 2 ? 240 : 90), activeChannel == 2 ? 2.4f : 1.2f);
            drawLutCurve(g2, luts[2], pad, cw, ch, new Color(26, 115, 232, activeChannel == 3 ? 240 : 90), activeChannel == 3 ? 2.4f : 1.2f);
            if (activeChannel == 0) {
                drawLutCurve(g2, luts[3], pad, cw, ch, INK_DARK, 2.4f);
            }

            List<float[]> pts = activePoints();
            Color ptColor = activeChannel == 1 ? ACCENT_RED
                    : activeChannel == 2 ? ACCENT_GREEN
                    : activeChannel == 3 ? ACCENT_BLUE
                    : INK_DARK;
            for (float[] pt : pts) {
                int px = pad + Math.round(pt[0] * cw);
                int py = pad + Math.round((1f - pt[1]) * ch);
                g2.setColor(Color.WHITE);
                g2.fillOval(px - 6, py - 6, 12, 12);
                g2.setColor(ptColor);
                g2.setStroke(new BasicStroke(2.2f));
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
            setPreferredSize(new Dimension(108, 126));
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
                        float[] rgb = LightCraftEngine.hsvToRgb(hue, sat * 0.85f, 0.94f);
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
            g2.setColor(CARD_BORDER);
            g2.drawOval(cx - radius, cy - radius, diameter, diameter);

            double rad = Math.toRadians(wheel.hue);
            float dist = (wheel.sat / 100f) * radius;
            int px = Math.round(cx + (float) Math.cos(rad) * dist);
            int py = Math.round(cy + (float) Math.sin(rad) * dist);
            g2.setColor(Color.WHITE);
            g2.fillOval(px - 6, py - 6, 12, 12);
            g2.setColor(INK_DARK);
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(px - 5, py - 5, 10, 10);

            g2.setFont(FONT_SMALL_BOLD);
            g2.setColor(TEXT_SECONDARY);
            FontMetrics fm = g2.getFontMetrics();
            String label = title + String.format(Locale.ROOT, " %.0f%%", wheel.sat);
            g2.drawString(label, Math.max(2, (w - fm.stringWidth(label)) / 2), cy + radius + 16);
            g2.dispose();
        }
    }

    // =========================================================================
    // 4. THẺ KHUNG ẢNH TRUNG TÂM BO TRÒN 28px GIỐNG ANDROID TABLET
    // =========================================================================
    public static final class ImageCanvasPanel extends RoundedCardPanel {
        private BufferedImage originalImage;
        private BufferedImage processedImage;
        private boolean showOriginalOnly = false;
        private boolean splitView = false;
        private float splitRatio = 0.5f;
        private int frameMode = 0;
        private String filmLabel = "Classic Chrome";
        private String recipeLabel = "Golden Negative";

        private double zoom = 1.0;
        private double panX = 0.0;
        private double panY = 0.0;
        private Point lastMouse;
        private boolean draggingSplit = false;
        private Runnable onViewChanged;

        public ImageCanvasPanel() {
            super(28, CARD_BG, CARD_BORDER);
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
                    if (onViewChanged != null) onViewChanged.run();
                    repaint();
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            addMouseWheelListener(mouse);
        }

        public void setOnViewChanged(Runnable r) {
            this.onViewChanged = r;
        }

        public double getZoom() {
            return zoom;
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
            if (onViewChanged != null) onViewChanged.run();
            repaint();
        }

        public int getFrameMode() {
            return frameMode;
        }

        public void resetView() {
            zoom = 1.0;
            panX = 0.0;
            panY = 0.0;
            if (onViewChanged != null) onViewChanged.run();
            repaint();
        }

        private int[] getDrawRect() {
            BufferedImage img = processedImage != null ? processedImage : originalImage;
            if (img == null) return new int[]{0, 0, 0, 0};
            int cw = getWidth() - 40;
            int ch = getHeight() - 40;
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

            int w = getWidth();
            int h = getHeight();
            Shape cardClip = new RoundRectangle2D.Float(4, 4, w - 8, h - 8, 24, 24);
            g2.setClip(cardClip);

            BufferedImage active = showOriginalOnly ? originalImage : processedImage;
            if (active == null) {
                g2.setFont(FONT_TITLE);
                g2.setColor(TEXT_SECONDARY);
                String msg = "Kéo & thả ảnh vào đây hoặc nhấn nút (+) bên trái";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2);
                g2.dispose();
                return;
            }

            BufferedImage framedActive = renderFramedImage(active, frameMode, filmLabel, recipeLabel);
            BufferedImage framedOrig = splitView && originalImage != null
                    ? renderFramedImage(originalImage, frameMode, "Ảnh gốc", "")
                    : null;

            int cw = w - 40;
            int ch = h - 40;
            double scale = Math.min((double) cw / framedActive.getWidth(), (double) ch / framedActive.getHeight()) * zoom;
            int dw = Math.max(1, (int) Math.round(framedActive.getWidth() * scale));
            int dh = Math.max(1, (int) Math.round(framedActive.getHeight() * scale));
            int dx = (w - dw) / 2 + (int) Math.round(panX);
            int dy = (h - dh) / 2 + (int) Math.round(panY);

            if (splitView && framedOrig != null && !showOriginalOnly) {
                int splitX = dx + Math.round(dw * splitRatio);
                g2.setClip(new Rectangle(dx, dy, splitX - dx, dh).intersection(cardClip.getBounds()));
                g2.drawImage(framedOrig, dx, dy, dw, dh, null);
                g2.setClip(new Rectangle(splitX, dy, (dx + dw) - splitX, dh).intersection(cardClip.getBounds()));
                g2.drawImage(framedActive, dx, dy, dw, dh, null);
                g2.setClip(cardClip);

                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(splitX, dy, splitX, dy + dh);
                g2.setColor(INK_DARK);
                g2.fillOval(splitX - 13, dy + dh / 2 - 13, 26, 26);
                g2.setColor(Color.WHITE);
                g2.drawOval(splitX - 13, dy + dh / 2 - 13, 26, 26);
                g2.setFont(FONT_BOLD);
                g2.drawString("↔", splitX - 6, dy + dh / 2 + 5);

                drawPillBadge(g2, "Trước (Gốc)", dx + 12, dy + 12);
                drawPillBadge(g2, "Sau (" + (recipeLabel.isEmpty() ? filmLabel : recipeLabel) + ")", Math.min(dx + dw - 140, splitX + 12), dy + 12);
            } else {
                g2.drawImage(framedActive, dx, dy, dw, dh, null);
                if (showOriginalOnly) {
                    drawPillBadge(g2, "Đang giữ xem Ảnh gốc", dx + 14, dy + 14);
                }
            }
            g2.dispose();
        }

        private void drawPillBadge(Graphics2D g2, String text, int x, int y) {
            g2.setFont(FONT_SMALL_BOLD);
            FontMetrics fm = g2.getFontMetrics();
            int bw = fm.stringWidth(text) + 18;
            int bh = 24;
            g2.setColor(new Color(17, 19, 24, 210));
            g2.fillRoundRect(x, y, bw, bh, 24, 24);
            g2.setColor(Color.WHITE);
            g2.drawString(text, x + 9, y + 16);
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

package vn.iro.editor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Ứng dụng máy tính <b>IRO Studio · Windows Desktop Edition</b>.
 *
 * <p>Sử dụng trực tiếp 100% lõi xử lý màu từ dự án Android:
 * <ul>
 *     <li>{@link ColorEngine}: Tuyến tính hóa sRGB, EV, WB Shift, 3D LUT {@link CubeLut}, Highlight/Shadow/DR,
 *         Color Chrome, Chrome FX Blue, Film Grain, NR, Sharpness & Clarity.</li>
 *     <li>{@link LightCraftEngine}: Đường cong bậc ba đơn điệu Fritsch–Carlson ({@link LightCraftEngine.MonotoneCurve}),
 *         3-Way OkLab Color Grading Wheels, 8-Band Color Mixer (HSL) và Local Masking (Radial, Linear, Sky, Subject).</li>
 *     <li>{@link AutoBalancer}: Phân tích phân vị độ chói p5/median/p95 và tự động cân bằng ảnh.</li>
 *     <li>{@link Recipe}: Kho 392 công thức màu phim Fuji X Weekly kèm 52 ảnh mẫu offline và tải ảnh mẫu trực tuyến.</li>
 *     <li>{@link OnlineLuts}: Duyệt và tải LUT {@code .cube} có kiểm tra SHA-1 từ GitHub.</li>
 * </ul>
 */
public final class IroDesktopApp extends JFrame {

    private static final String[] SAMPLE_ASSETS = {
            "fujixweekly/fxw_029_kodachrome-64.jpg",
            "fujixweekly/fxw_001_fujicolor-pro-400h.jpg",
            "fujixweekly/fxw_040_pacific-blues.jpg",
            "fujixweekly/fxw_031_kodak-portra-400-v2.jpg",
            "fujixweekly/fxw_043_reggies-portra.jpg",
            "fujixweekly/fxw_051_vintage-kodachrome.jpg",
            "fujixweekly/fxw_011_kodak-gold-200.jpg",
            "fujixweekly/fxw_024_cinestill-800t.jpg",
            "fujixweekly/fxw_047_vibrant-arizona.jpg",
            "fujixweekly/fxw_003_nostalgic-negative.jpg",
            "fujixweekly/fxw_006_eterna.jpg",
            "fujixweekly/fxw_108_reala-ace.jpg",
            "fujixweekly/fxw_013_kodak-tri-x-400.jpg"
    };

    private static final String[] FILM_FILTERS = {
            "Tất cả hệ Film (392)",
            "Classic Chrome",
            "Classic Negative",
            "Velvia",
            "Provia",
            "Astia",
            "Nostalgic Neg.",
            "Eterna",
            "Reala Ace",
            "Acros",
            "Monochrome",
            "Sepia",
            "PRO Neg. Hi",
            "PRO Neg. Std",
            "Eterna Bleach Bypass"
    };

    // Dữ liệu công thức & trạng thái chỉnh sửa
    private final List<Recipe> allRecipes = new ArrayList<>();
    private final DefaultListModel<Recipe> recipeListModel = new DefaultListModel<>();
    private JList<Recipe> recipeList;
    private JTextField searchField;
    private JComboBox<String> filmFilterCombo;
    private JLabel statusLabel;

    private Settings settings = new Settings();
    private final LightCraftEngine.State lightCraft = new LightCraftEngine.State();
    private CubeLut activeLut = null;
    private String activeRecipeName = "Kodachrome 64";
    private int currentSampleIndex = 0;

    // Lịch sử Undo / Redo
    private final List<Settings> undoStack = new ArrayList<>();
    private final List<Settings> redoStack = new ArrayList<>();

    // Ảnh gốc (full-res) & ảnh preview (tối ưu tốc độ kéo thanh trượt)
    private BufferedImage fullSourceImage;
    private BufferedImage previewSourceImage;
    private int[] previewSourcePixels;
    private BufferedImage currentRenderedPreview;

    // Thành phần giao diện chính
    private DesktopWidgets.ImageCanvasPanel canvasPanel;
    private DesktopWidgets.HistogramPanel histogramPanel;
    private DesktopWidgets.ToneCurveWidget toneCurveWidget;
    private DesktopWidgets.ColorWheelWidget shadowsWheelWidget;
    private DesktopWidgets.ColorWheelWidget midtonesWheelWidget;
    private DesktopWidgets.ColorWheelWidget highlightsWheelWidget;

    // Map đồng bộ ngược từ Settings -> Slider UI
    private final Map<String, SliderControl> settingSliders = new HashMap<>();
    private final AtomicBoolean syncingSliders = new AtomicBoolean(false);
    private JComboBox<String> drCombo;
    private int selectedMixerBand = 0;
    private SliderControl mixerHueSlider;
    private SliderControl mixerSatSlider;
    private SliderControl mixerLumSlider;

    // Luồng xử lý hậu kỳ bất đồng bộ
    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "IRO-Desktop-Render");
        t.setDaemon(true);
        return t;
    });
    private Future<?> activeRenderTask;

    private static final class SliderControl {
        final JSlider slider;
        final JLabel valueLabel;
        final double scale;
        final String format;

        SliderControl(JSlider slider, JLabel valueLabel, double scale, String format) {
            this.slider = slider;
            this.valueLabel = valueLabel;
            this.scale = scale;
            this.format = format;
        }

        void setValue(double v) {
            int raw = (int) Math.round(v * scale);
            slider.setValue(Math.max(slider.getMinimum(), Math.min(slider.getMaximum(), raw)));
            valueLabel.setText(String.format(Locale.ROOT, format, v));
        }
    }

    public IroDesktopApp() {
        super("IRO Studio · Fujifilm & LightCraft Photo Editor (Windows Desktop)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1460, 880);
        setMinimumSize(new Dimension(1120, 700));
        setLocationRelativeTo(null);
        getContentPane().setBackground(DesktopWidgets.BG_DARK);
        setLayout(new BorderLayout(0, 0));

        setWindowIcon();
        loadRecipesCatalog();

        add(buildTopToolbar(), BorderLayout.NORTH);
        add(buildLeftRecipeSidebar(), BorderLayout.WEST);
        add(buildCenterWorkspace(), BorderLayout.CENTER);
        add(buildRightInspector(), BorderLayout.EAST);
        add(buildBottomStatusBar(), BorderLayout.SOUTH);

        setupKeyboardShortcuts();
        setupDragAndDrop();

        // Mở sẵn ảnh mẫu Fuji X Weekly đóng gói sẵn cùng công thức tương ứng
        loadBundledSamplePhoto(0);
    }

    private void setWindowIcon() {
        BufferedImage icon = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = icon.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(0x18, 0x1B, 0x20));
        g2.fillRoundRect(2, 2, 60, 60, 16, 16);
        g2.setColor(DesktopWidgets.ACCENT_BLUE);
        g2.fillOval(12, 12, 26, 26);
        g2.setColor(new Color(0xEA, 0x43, 0x35));
        g2.fillOval(26, 14, 24, 24);
        g2.setColor(DesktopWidgets.ACCENT_GREEN);
        g2.fillOval(19, 25, 25, 25);
        g2.dispose();
        setIconImage(icon);
    }

    // =========================================================================
    // NẠP KHO 392 CÔNG THỨC FUJI X WEEKLY TỪ ASSETS/RECIPES.JSON
    // =========================================================================
    private void loadRecipesCatalog() {
        allRecipes.clear();
        try {
            InputStream in = IroDesktopApp.class.getResourceAsStream("/recipes.json");
            if (in == null) {
                File local = new File("app/src/main/assets/recipes.json");
                if (local.isFile()) {
                    in = new FileInputStream(local);
                }
            }
            if (in != null) {
                String text = readStreamUtf8(in).trim();
                JSONArray arr = text.startsWith("{")
                        ? new JSONObject(text).getJSONArray("recipes")
                        : new JSONArray(text);
                for (int i = 0; i < arr.length(); i++) {
                    try {
                        allRecipes.add(new Recipe(arr.getJSONObject(i)));
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            DebugLogger.e(DebugLogger.TAG_RECIPE, "Không nạp được recipes.json", e);
        }
    }

    private static String readStreamUtf8(InputStream in) throws Exception {
        try (InputStream stream = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = stream.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    // =========================================================================
    // THANH CÔNG CỤ TRÊN CÙNG (TOP STUDIO TOOLBAR)
    // =========================================================================
    private JComponent buildTopToolbar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(DesktopWidgets.BG_PANEL);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, DesktopWidgets.BORDER_COLOR),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)));

        // Cụm trái: Logo + Mở ảnh + Ảnh mẫu Fuji
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);

        JLabel brand = new JLabel("IRO STUDIO");
        brand.setFont(new Font("Segoe UI", Font.BOLD, 16));
        brand.setForeground(DesktopWidgets.ACCENT_BLUE);
        brand.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 10));
        left.add(brand);

        JButton openBtn = DesktopWidgets.createStudioButton("Mở ảnh... (Ctrl+O)", true);
        openBtn.addActionListener(e -> chooseAndOpenImage());
        left.add(openBtn);

        JButton sampleBtn = DesktopWidgets.createStudioButton("Đổi ảnh mẫu Fuji", false);
        sampleBtn.addActionListener(e -> {
            currentSampleIndex++;
            loadBundledSamplePhoto(currentSampleIndex);
        });
        left.add(sampleBtn);

        // Cụm giữa: Auto-Balance, Undo, Redo, Chia đôi Trước/Sau, Đặt lại
        JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        center.setOpaque(false);

        JButton autoBtn = DesktopWidgets.createStudioButton("Auto-Balance AI (Ctrl+B)", false);
        autoBtn.setForeground(DesktopWidgets.ACCENT_AMBER);
        autoBtn.addActionListener(e -> runAutoBalance());
        center.add(autoBtn);

        JButton undoBtn = DesktopWidgets.createStudioButton("< Hoàn tác", false);
        undoBtn.addActionListener(e -> performUndo());
        center.add(undoBtn);

        JButton redoBtn = DesktopWidgets.createStudioButton("Làm lại >", false);
        redoBtn.addActionListener(e -> performRedo());
        center.add(redoBtn);

        JButton splitBtn = DesktopWidgets.createStudioButton("Chia đôi Trước / Sau", false);
        splitBtn.addActionListener(e -> {
            boolean next = !canvasPanel.isSplitView();
            canvasPanel.setSplitView(next);
            splitBtn.setForeground(next ? DesktopWidgets.ACCENT_GREEN : DesktopWidgets.TEXT_PRIMARY);
        });
        center.add(splitBtn);

        JButton holdOrigBtn = DesktopWidgets.createStudioButton("Giữ xem Gốc (Space)", false);
        holdOrigBtn.getModel().addChangeListener(e ->
                canvasPanel.setShowOriginalOnly(holdOrigBtn.getModel().isPressed()));
        center.add(holdOrigBtn);

        JButton resetBtn = DesktopWidgets.createStudioButton("Đặt lại gốc", false);
        resetBtn.addActionListener(e -> resetAllAdjustments());
        center.add(resetBtn);

        // Cụm phải: Nạp LUT .cube, Kho LUT GitHub, Xuất ảnh
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        JButton lutBtn = DesktopWidgets.createStudioButton("Nạp LUT .cube", false);
        lutBtn.addActionListener(e -> chooseAndLoadCubeLut());
        right.add(lutBtn);

        JButton onlineLutBtn = DesktopWidgets.createStudioButton("Kho LUT GitHub", false);
        onlineLutBtn.addActionListener(e -> showOnlineLutDialog());
        right.add(onlineLutBtn);

        JButton exportBtn = DesktopWidgets.createStudioButton("Xuất ảnh (Ctrl+S)", true);
        exportBtn.addActionListener(e -> exportFullResolutionImage());
        right.add(exportBtn);

        bar.add(left, BorderLayout.WEST);
        bar.add(center, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // =========================================================================
    // CỘT TRÁI: KHO 392 CÔNG THỨC FUJI X WEEKLY & ẢNH MẪU MÔ PHỎNG
    // =========================================================================
    private JComponent buildLeftRecipeSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout(0, 8));
        sidebar.setPreferredSize(new Dimension(335, 600));
        sidebar.setBackground(DesktopWidgets.BG_PANEL);
        sidebar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, DesktopWidgets.BORDER_COLOR),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        // Đầu mục + ô tìm kiếm + lọc hệ Film
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);

        JLabel title = new JLabel("KHO 392 CÔNG THỨC FUJI X WEEKLY");
        title.setFont(DesktopWidgets.FONT_BOLD);
        title.setForeground(DesktopWidgets.ACCENT_BLUE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(6));

        searchField = new JTextField();
        searchField.setFont(DesktopWidgets.FONT_UI);
        searchField.setBackground(DesktopWidgets.BG_INPUT);
        searchField.setForeground(DesktopWidgets.TEXT_PRIMARY);
        searchField.setCaretColor(DesktopWidgets.TEXT_PRIMARY);
        searchField.setToolTipText("Tìm theo tên công thức (Kodachrome, Portra...) hoặc cảm biến (X-Trans IV, V...)");
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DesktopWidgets.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        searchField.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filterRecipeList(); }
            @Override public void removeUpdate(DocumentEvent e) { filterRecipeList(); }
            @Override public void changedUpdate(DocumentEvent e) { filterRecipeList(); }
        });
        header.add(searchField);
        header.add(Box.createVerticalStrut(6));

        filmFilterCombo = new JComboBox<>(FILM_FILTERS);
        filmFilterCombo.setFont(DesktopWidgets.FONT_UI);
        filmFilterCombo.setBackground(DesktopWidgets.BG_CARD);
        filmFilterCombo.setForeground(DesktopWidgets.TEXT_PRIMARY);
        filmFilterCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        filmFilterCombo.addActionListener(e -> filterRecipeList());
        header.add(filmFilterCombo);

        sidebar.add(header, BorderLayout.NORTH);

        // Danh sách công thức kèm ảnh mẫu Fuji X Weekly
        recipeList = new JList<>(recipeListModel);
        recipeList.setBackground(DesktopWidgets.BG_DARK);
        recipeList.setForeground(DesktopWidgets.TEXT_PRIMARY);
        recipeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        recipeList.setFixedCellHeight(72);
        recipeList.setCellRenderer(new RecipeCardRenderer());
        recipeList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Recipe selected = recipeList.getSelectedValue();
                if (selected == null) return;
                if (e.getClickCount() == 2) {
                    applyRecipe(selected, true);
                } else {
                    applyRecipe(selected, false);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(recipeList);
        scroll.setBorder(BorderFactory.createLineBorder(DesktopWidgets.BORDER_COLOR, 1));
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        sidebar.add(scroll, BorderLayout.CENTER);

        // Nút thao tác dưới danh sách
        JPanel footer = new JPanel(new GridLayout(2, 1, 0, 6));
        footer.setOpaque(false);

        JButton openSampleBtn = DesktopWidgets.createStudioButton("Mở ảnh mẫu Fuji của công thức này", true);
        openSampleBtn.addActionListener(e -> {
            Recipe selected = recipeList.getSelectedValue();
            if (selected != null) {
                applyRecipe(selected, true);
            }
        });
        footer.add(openSampleBtn);

        JButton detailBtn = DesktopWidgets.createStudioButton("Xem thông số gốc & bài viết FujiXWeekly", false);
        detailBtn.addActionListener(e -> {
            Recipe selected = recipeList.getSelectedValue();
            if (selected != null) {
                showRecipeDetailDialog(selected);
            }
        });
        footer.add(detailBtn);

        sidebar.add(footer, BorderLayout.SOUTH);

        filterRecipeList();
        return sidebar;
    }

    private void filterRecipeList() {
        String q = searchField != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        int filmIdx = filmFilterCombo != null ? filmFilterCombo.getSelectedIndex() : 0;
        String filmFilter = filmIdx > 0 ? FILM_FILTERS[filmIdx].toLowerCase(Locale.ROOT) : "";

        recipeListModel.clear();
        for (Recipe r : allRecipes) {
            if (!filmFilter.isEmpty() && !r.film.toLowerCase(Locale.ROOT).contains(filmFilter)) {
                continue;
            }
            if (!q.isEmpty()) {
                boolean match = r.name.toLowerCase(Locale.ROOT).contains(q)
                        || r.film.toLowerCase(Locale.ROOT).contains(q)
                        || r.sensors.toLowerCase(Locale.ROOT).contains(q);
                if (!match) continue;
            }
            recipeListModel.addElement(r);
        }
    }

    /** Renderer hiển thị thẻ công thức kèm ảnh mẫu từ Fuji X Weekly. */
    private final class RecipeCardRenderer extends JPanel implements ListCellRenderer<Recipe> {
        private final JLabel thumbLabel = new JLabel();
        private final JLabel nameLabel = new JLabel();
        private final JLabel metaLabel = new JLabel();
        private final JLabel sensorLabel = new JLabel();

        RecipeCardRenderer() {
            setLayout(new BorderLayout(10, 0));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, DesktopWidgets.BORDER_COLOR),
                    BorderFactory.createEmptyBorder(6, 8, 6, 8)));

            thumbLabel.setPreferredSize(new Dimension(78, 56));
            thumbLabel.setHorizontalAlignment(SwingConstants.CENTER);
            thumbLabel.setOpaque(true);
            thumbLabel.setBackground(DesktopWidgets.BG_CARD);
            thumbLabel.setBorder(BorderFactory.createLineBorder(DesktopWidgets.BORDER_COLOR, 1));
            add(thumbLabel, BorderLayout.WEST);

            JPanel textPanel = new JPanel();
            textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
            textPanel.setOpaque(false);

            nameLabel.setFont(DesktopWidgets.FONT_BOLD);
            nameLabel.setForeground(DesktopWidgets.TEXT_PRIMARY);
            metaLabel.setFont(DesktopWidgets.FONT_SMALL);
            metaLabel.setForeground(DesktopWidgets.ACCENT_BLUE);
            sensorLabel.setFont(DesktopWidgets.FONT_SMALL);
            sensorLabel.setForeground(DesktopWidgets.TEXT_SECONDARY);

            textPanel.add(nameLabel);
            textPanel.add(Box.createVerticalStrut(2));
            textPanel.add(metaLabel);
            textPanel.add(Box.createVerticalStrut(2));
            textPanel.add(sensorLabel);
            add(textPanel, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends Recipe> list, Recipe value, int index, boolean isSelected, boolean cellHasFocus) {
            setBackground(isSelected ? new Color(0x24, 0x34, 0x4D) : DesktopWidgets.BG_DARK);
            nameLabel.setText(value.name);
            metaLabel.setText("Film: " + value.film);
            sensorLabel.setText(value.sensors.isEmpty() ? "Fuji X Weekly" : value.sensors);

            BufferedImage thumb = DesktopWidgets.DesktopFujiSampleLoader.getCachedForRecipe(value);
            if (thumb != null) {
                thumbLabel.setIcon(new ImageIcon(scaleThumbnail(thumb, 76, 54)));
                thumbLabel.setText("");
            } else {
                thumbLabel.setIcon(null);
                thumbLabel.setForeground(DesktopWidgets.TEXT_SECONDARY);
                thumbLabel.setFont(DesktopWidgets.FONT_SMALL);
                thumbLabel.setText("Fuji");
                DesktopWidgets.DesktopFujiSampleLoader.loadRecipeSampleAsync(value, img -> list.repaint());
            }
            return this;
        }
    }

    private static final Map<BufferedImage, BufferedImage> THUMB_SCALED_CACHE = new HashMap<>();

    private static synchronized BufferedImage scaleThumbnail(BufferedImage src, int w, int h) {
        BufferedImage cached = THUMB_SCALED_CACHE.get(src);
        if (cached != null) return cached;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        double scale = Math.max((double) w / src.getWidth(), (double) h / src.getHeight());
        int sw = (int) Math.round(src.getWidth() * scale);
        int sh = (int) Math.round(src.getHeight() * scale);
        g2.drawImage(src, (w - sw) / 2, (h - sh) / 2, sw, sh, null);
        g2.dispose();
        THUMB_SCALED_CACHE.put(src, out);
        return out;
    }

    // =========================================================================
    // KHUNG GIỮA: BÀN DỰNG ẢNH + DẢI FILM SIMULATION NHANH
    // =========================================================================
    private JComponent buildCenterWorkspace() {
        JPanel workspace = new JPanel(new BorderLayout(0, 0));
        workspace.setBackground(DesktopWidgets.BG_DARK);

        canvasPanel = new DesktopWidgets.ImageCanvasPanel();
        workspace.add(canvasPanel, BorderLayout.CENTER);

        // Dải chọn nhanh Film Simulation ở đáy bàn dựng
        JPanel filmStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        filmStrip.setBackground(DesktopWidgets.BG_PANEL);
        filmStrip.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, DesktopWidgets.BORDER_COLOR));

        String[][] quickFilms = {
                {"Original", "Gốc (Original)"},
                {"Classic Chrome", "Classic Chrome"},
                {"Classic Negative", "Classic Neg."},
                {"Velvia", "Velvia"},
                {"Provia", "Provia"},
                {"Astia", "Astia"},
                {"Nostalgic Neg.", "Nostalgic Neg."},
                {"Eterna", "Eterna"},
                {"Reala Ace", "Reala Ace"},
                {"Acros", "Acros B&W"},
                {"Sepia", "Sepia"}
        };
        for (String[] pair : quickFilms) {
            String filmKey = pair[0];
            JButton b = DesktopWidgets.createStudioButton(pair[1], false);
            b.setFont(DesktopWidgets.FONT_SMALL);
            b.addActionListener(e -> applyQuickFilmSimulation(filmKey));
            filmStrip.add(b);
        }

        workspace.add(filmStrip, BorderLayout.SOUTH);
        return workspace;
    }

    // =========================================================================
    // CỘT PHẢI: BIỂU ĐỒ RGB HISTOGRAM & 4 TAB CHỈNH MÀU CHUYÊN SÂU
    // =========================================================================
    private JComponent buildRightInspector() {
        JPanel dock = new JPanel(new BorderLayout(0, 8));
        dock.setPreferredSize(new Dimension(380, 600));
        dock.setBackground(DesktopWidgets.BG_PANEL);
        dock.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, DesktopWidgets.BORDER_COLOR),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        histogramPanel = new DesktopWidgets.HistogramPanel();
        dock.add(histogramPanel, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(DesktopWidgets.FONT_BOLD);
        tabs.setBackground(DesktopWidgets.BG_PANEL);
        tabs.setForeground(DesktopWidgets.TEXT_PRIMARY);

        tabs.addTab("Màu Fuji", createScrollTab(buildFujiAdjustTab()));
        tabs.addTab("LightCraft Curve", createScrollTab(buildLightCraftCurveTab()));
        tabs.addTab("OkLab & HSL", createScrollTab(buildLightCraftGradingTab()));
        tabs.addTab("Mask & Khung", createScrollTab(buildMaskAndFrameTab()));

        dock.add(tabs, BorderLayout.CENTER);
        return dock;
    }

    private JScrollPane createScrollTab(JPanel content) {
        JScrollPane sp = new JScrollPane(content);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        sp.getViewport().setBackground(DesktopWidgets.BG_PANEL);
        return sp;
    }

    private JPanel buildFujiAdjustTab() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(DesktopWidgets.BG_PANEL);
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 12, 12));

        addSectionHeader(p, "ÁNH SÁNG & TƯƠNG PHẢN FUJIFILM");
        addSettingSlider(p, "exposure", "Phơi sáng (EV)", -3.0, 3.0, 100.0, "%+.2f EV", v -> settings.exposure = v);
        addSettingSlider(p, "highlight", "Vùng sáng (Highlight)", -2.0, 4.0, 10.0, "%+.1f", v -> settings.highlight = v);
        addSettingSlider(p, "shadow", "Vùng tối (Shadow)", -2.0, 4.0, 10.0, "%+.1f", v -> settings.shadow = v);

        // Dynamic Range DR100 / DR200 / DR400
        JPanel drRow = new JPanel(new BorderLayout(8, 0));
        drRow.setOpaque(false);
        drRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        drRow.setMaximumSize(new Dimension(330, 32));
        JLabel drLbl = new JLabel("Dynamic Range (DR)");
        drLbl.setFont(DesktopWidgets.FONT_UI);
        drLbl.setForeground(DesktopWidgets.TEXT_PRIMARY);
        drCombo = new JComboBox<>(new String[]{"DR100 (Chuẩn)", "DR200 (Nén sáng)", "DR400 (Cứu cháy sáng)"});
        drCombo.setBackground(DesktopWidgets.BG_CARD);
        drCombo.setForeground(DesktopWidgets.TEXT_PRIMARY);
        drCombo.addActionListener(e -> {
            if (syncingSliders.get()) return;
            int idx = drCombo.getSelectedIndex();
            settings.dr = idx == 2 ? 400 : (idx == 1 ? 200 : 100);
            scheduleRender();
        });
        drRow.add(drLbl, BorderLayout.WEST);
        drRow.add(drCombo, BorderLayout.EAST);
        p.add(drRow);
        p.add(Box.createVerticalStrut(10));

        addSectionHeader(p, "MÀU SẮC & CÂN BẰNG TRẮNG (WB SHIFT)");
        addSettingSlider(p, "color", "Độ đậm màu (Color)", -4.0, 4.0, 10.0, "%+.1f", v -> settings.color = v);
        addSettingSlider(p, "temperature", "Nhiệt độ màu (Temp)", -100.0, 100.0, 1.0, "%+.0f", v -> settings.temperature = v);
        addSettingSlider(p, "red", "Dịch WB Đỏ (Red Shift)", -9.0, 9.0, 10.0, "%+.1f", v -> settings.red = v);
        addSettingSlider(p, "blue", "Dịch WB Xanh (Blue Shift)", -9.0, 9.0, 10.0, "%+.1f", v -> settings.blue = v);
        addSettingSlider(p, "colorChrome", "Color Chrome Effect", 0.0, 2.0, 10.0, "%.1f", v -> settings.colorChrome = v);
        addSettingSlider(p, "chromeBlue", "Color Chrome FX Blue", 0.0, 2.0, 10.0, "%.1f", v -> settings.chromeBlue = v);

        addSectionHeader(p, "CHI TIẾT, HẠT PHIM & LUT 3D");
        addSettingSlider(p, "sharpness", "Độ sắc nét (Sharpness)", -4.0, 4.0, 10.0, "%+.1f", v -> settings.sharpness = v);
        addSettingSlider(p, "clarity", "Độ trong (Clarity)", -5.0, 5.0, 10.0, "%+.1f", v -> settings.clarity = v);
        addSettingSlider(p, "nr", "Khử nhiễu (High ISO NR)", -4.0, 4.0, 10.0, "%+.1f", v -> settings.nr = v);
        addSettingSlider(p, "grain", "Cường độ hạt phim (Grain)", 0.0, 30.0, 1.0, "%.0f", v -> settings.grain = v);
        addSettingSlider(p, "grainSize", "Kích thước hạt (Size)", 1.0, 2.0, 10.0, "%.1f", v -> settings.grainSize = v);
        addSettingSlider(p, "lutAmount", "Cường độ LUT 3D (%)", 0.0, 100.0, 1.0, "%.0f%%", v -> settings.lutAmount = v);

        return p;
    }

    private JPanel buildLightCraftCurveTab() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(DesktopWidgets.BG_PANEL);
        p.setBorder(BorderFactory.createEmptyBorder(8, 6, 12, 6));

        addSectionHeader(p, "12 PRESET PHÒNG TỐI LIGHTCRAFT");
        JPanel presetGrid = new JPanel(new GridLayout(0, 2, 6, 6));
        presetGrid.setOpaque(false);
        for (LightCraftEngine.BuiltinPreset preset : LightCraftEngine.PRESETS) {
            JButton btn = DesktopWidgets.createStudioButton(preset.name, false);
            btn.setFont(DesktopWidgets.FONT_SMALL);
            btn.setToolTipText(preset.subtitle);
            btn.addActionListener(e -> {
                pushUndoState();
                preset.applyTo(lightCraft, settings);
                syncSlidersFromState();
                toneCurveWidget.repaint();
                if (shadowsWheelWidget != null) {
                    shadowsWheelWidget.repaint();
                    midtonesWheelWidget.repaint();
                    highlightsWheelWidget.repaint();
                }
                scheduleRender();
                setStatus("Đã áp dụng Preset LightCraft: " + preset.name + " (" + preset.subtitle + ")");
            });
            presetGrid.add(btn);
        }
        p.add(presetGrid);
        p.add(Box.createVerticalStrut(12));

        addSectionHeader(p, "ĐƯỜNG CONG TONE CURVE (MONOTONE SPLINE)");
        JPanel chRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        chRow.setOpaque(false);
        ButtonGroup bg = new ButtonGroup();
        String[] chNames = {"RGB", "Đỏ (R)", "Lục (G)", "Lam (B)"};
        for (int i = 0; i < chNames.length; i++) {
            final int chIdx = i;
            JRadioButton rb = new JRadioButton(chNames[i], i == 0);
            rb.setOpaque(false);
            rb.setForeground(DesktopWidgets.TEXT_PRIMARY);
            rb.setFont(DesktopWidgets.FONT_SMALL);
            rb.addActionListener(e -> toneCurveWidget.setActiveChannel(chIdx));
            bg.add(rb);
            chRow.add(rb);
        }
        p.add(chRow);
        p.add(Box.createVerticalStrut(6));

        toneCurveWidget = new DesktopWidgets.ToneCurveWidget(lightCraft, this::scheduleRender);
        p.add(toneCurveWidget);
        p.add(Box.createVerticalStrut(10));

        addSectionHeader(p, "ĐƯỜNG CONG THAM SỐ 4 VÙNG (PARAMETRIC)");
        addRawSlider(p, "Shadows (Vùng tối sâu)", -100, 100, 0, "%+d", v -> {
            lightCraft.paramShadows = v;
            toneCurveWidget.repaint();
            scheduleRender();
        });
        addRawSlider(p, "Darks (Vùng trầm)", -100, 100, 0, "%+d", v -> {
            lightCraft.paramDarks = v;
            toneCurveWidget.repaint();
            scheduleRender();
        });
        addRawSlider(p, "Lights (Vùng sáng vừa)", -100, 100, 0, "%+d", v -> {
            lightCraft.paramLights = v;
            toneCurveWidget.repaint();
            scheduleRender();
        });
        addRawSlider(p, "Highlights (Đỉnh sáng)", -100, 100, 0, "%+d", v -> {
            lightCraft.paramHighlights = v;
            toneCurveWidget.repaint();
            scheduleRender();
        });

        JButton resetCurveBtn = DesktopWidgets.createStudioButton("Đặt lại Tone Curve", false);
        resetCurveBtn.addActionListener(e -> {
            lightCraft.resetCurves();
            toneCurveWidget.repaint();
            scheduleRender();
        });
        p.add(resetCurveBtn);
        return p;
    }

    private JPanel buildLightCraftGradingTab() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(DesktopWidgets.BG_PANEL);
        p.setBorder(BorderFactory.createEmptyBorder(8, 6, 12, 6));

        addSectionHeader(p, "BÁNH XE MÀU 3-WAY COLOR GRADING (OKLAB)");
        JPanel wheelsRow = new JPanel(new GridLayout(1, 3, 6, 0));
        wheelsRow.setOpaque(false);
        shadowsWheelWidget = new DesktopWidgets.ColorWheelWidget("Shadows", lightCraft.shadowsWheel, this::scheduleRender);
        midtonesWheelWidget = new DesktopWidgets.ColorWheelWidget("Midtones", lightCraft.midtonesWheel, this::scheduleRender);
        highlightsWheelWidget = new DesktopWidgets.ColorWheelWidget("Highlights", lightCraft.highlightsWheel, this::scheduleRender);
        wheelsRow.add(shadowsWheelWidget);
        wheelsRow.add(midtonesWheelWidget);
        wheelsRow.add(highlightsWheelWidget);
        p.add(wheelsRow);
        p.add(Box.createVerticalStrut(8));

        addRawSlider(p, "Shadows Luminance", -100, 100, 0, "%+d", v -> {
            lightCraft.shadowsWheel.lum = v;
            scheduleRender();
        });
        addRawSlider(p, "Midtones Luminance", -100, 100, 0, "%+d", v -> {
            lightCraft.midtonesWheel.lum = v;
            scheduleRender();
        });
        addRawSlider(p, "Highlights Luminance", -100, 100, 0, "%+d", v -> {
            lightCraft.highlightsWheel.lum = v;
            scheduleRender();
        });
        addRawSlider(p, "Grading Blending", 0, 100, 50, "%d%%", v -> {
            lightCraft.gradingBlending = v;
            scheduleRender();
        });
        addRawSlider(p, "Grading Balance", -100, 100, 0, "%+d", v -> {
            lightCraft.gradingBalance = v;
            scheduleRender();
        });

        p.add(Box.createVerticalStrut(10));
        addSectionHeader(p, "BỘ TRỘN MÀU 8 KÊNH (8-BAND OKLCH MIXER)");

        JPanel bandButtons = new JPanel(new GridLayout(2, 4, 4, 4));
        bandButtons.setOpaque(false);
        for (int i = 0; i < 8; i++) {
            final int bandIdx = i;
            JButton b = DesktopWidgets.createStudioButton(LightCraftEngine.MIXER_BAND_NAMES[i], false);
            b.setFont(DesktopWidgets.FONT_SMALL);
            b.setForeground(new Color(LightCraftEngine.MIXER_BAND_COLORS[i]));
            b.addActionListener(e -> {
                selectedMixerBand = bandIdx;
                syncingSliders.set(true);
                try {
                    mixerHueSlider.setValue(lightCraft.mixerHue[selectedMixerBand]);
                    mixerSatSlider.setValue(lightCraft.mixerSat[selectedMixerBand]);
                    mixerLumSlider.setValue(lightCraft.mixerLum[selectedMixerBand]);
                } finally {
                    syncingSliders.set(false);
                }
            });
            bandButtons.add(b);
        }
        p.add(bandButtons);
        p.add(Box.createVerticalStrut(6));

        mixerHueSlider = createSliderControl(p, "Hue (Đổi sắc độ dải màu)", -100, 100, 1.0, "%+.0f", v -> {
            lightCraft.mixerHue[selectedMixerBand] = (float) v;
            scheduleRender();
        });
        mixerSatSlider = createSliderControl(p, "Saturation (Độ bão hòa dải màu)", -100, 100, 1.0, "%+.0f", v -> {
            lightCraft.mixerSat[selectedMixerBand] = (float) v;
            scheduleRender();
        });
        mixerLumSlider = createSliderControl(p, "Luminance (Độ sáng dải màu)", -100, 100, 1.0, "%+.0f", v -> {
            lightCraft.mixerLum[selectedMixerBand] = (float) v;
            scheduleRender();
        });

        return p;
    }

    private JPanel buildMaskAndFrameTab() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(DesktopWidgets.BG_PANEL);
        p.setBorder(BorderFactory.createEmptyBorder(8, 6, 12, 6));

        addSectionHeader(p, "MẶT NẠ CỤC BỘ (LOCAL MASKING AI)");
        JComboBox<String> maskCombo = new JComboBox<>(new String[]{
                "Tắt Mask",
                "Radial Gradient (Elip chủ thể)",
                "Linear Gradient (Chuyển sắc)",
                "AI Bầu trời (Sky Heuristic)",
                "AI Chủ thể trung tâm (Subject)"
        });
        maskCombo.setBackground(DesktopWidgets.BG_CARD);
        maskCombo.setForeground(DesktopWidgets.TEXT_PRIMARY);
        maskCombo.setMaximumSize(new Dimension(400, 32));
        maskCombo.addActionListener(e -> {
            int idx = maskCombo.getSelectedIndex();
            lightCraft.maskMode = LightCraftEngine.MaskMode.values()[Math.max(0, Math.min(4, idx))];
            scheduleRender();
        });
        p.add(maskCombo);
        p.add(Box.createVerticalStrut(6));

        JCheckBox showOverlayCb = new JCheckBox("Hiển thị vùng phủ Mask màu đỏ");
        showOverlayCb.setOpaque(false);
        showOverlayCb.setForeground(DesktopWidgets.TEXT_PRIMARY);
        showOverlayCb.addActionListener(e -> {
            lightCraft.showMaskOverlay = showOverlayCb.isSelected();
            scheduleRender();
        });
        p.add(showOverlayCb);

        JCheckBox invertMaskCb = new JCheckBox("Đảo ngược vùng Mask (Invert)");
        invertMaskCb.setOpaque(false);
        invertMaskCb.setForeground(DesktopWidgets.TEXT_PRIMARY);
        invertMaskCb.addActionListener(e -> {
            lightCraft.maskInvert = invertMaskCb.isSelected();
            scheduleRender();
        });
        p.add(invertMaskCb);
        p.add(Box.createVerticalStrut(6));

        createSliderControl(p, "Mask Phơi sáng (EV)", -2.0, 2.0, 100.0, "%+.2f EV", v -> {
            lightCraft.maskExposure = (float) v;
            scheduleRender();
        });
        createSliderControl(p, "Mask Nhiệt độ màu", -100.0, 100.0, 1.0, "%+.0f", v -> {
            lightCraft.maskTemp = (float) v;
            scheduleRender();
        });
        createSliderControl(p, "Mask Độ tương phản (Clarity)", -100.0, 100.0, 1.0, "%+.0f", v -> {
            lightCraft.maskClarity = (float) v;
            scheduleRender();
        });
        createSliderControl(p, "Mask Độ bão hòa (Saturation)", -100.0, 100.0, 1.0, "%+.0f", v -> {
            lightCraft.maskSaturation = (float) v;
            scheduleRender();
        });

        p.add(Box.createVerticalStrut(14));
        addSectionHeader(p, "KHUNG VIỀN TRIỂN LÃM FUJIFILM EXIF");
        JComboBox<String> frameCombo = new JComboBox<>(new String[]{
                "Không khung viền",
                "Khung Trắng Gallery + Chữ ký Fujifilm",
                "Khung Đen Cinema + Chữ ký Fujifilm",
                "Khung Polaroid Cổ điển"
        });
        frameCombo.setBackground(DesktopWidgets.BG_CARD);
        frameCombo.setForeground(DesktopWidgets.TEXT_PRIMARY);
        frameCombo.setMaximumSize(new Dimension(400, 32));
        frameCombo.addActionListener(e -> canvasPanel.setFrameMode(frameCombo.getSelectedIndex()));
        p.add(frameCombo);

        p.add(Box.createVerticalStrut(14));
        addSectionHeader(p, "ĐỒNG BỘ JSON CÔNG THỨC (ANDROID / iOS / PC)");
        JPanel jsonBtns = new JPanel(new GridLayout(1, 2, 6, 0));
        jsonBtns.setOpaque(false);
        JButton importJsonBtn = DesktopWidgets.createStudioButton("Nhập JSON...", false);
        importJsonBtn.addActionListener(e -> importSettingsJson());
        JButton exportJsonBtn = DesktopWidgets.createStudioButton("Xuất JSON...", false);
        exportJsonBtn.addActionListener(e -> exportSettingsJson());
        jsonBtns.add(importJsonBtn);
        jsonBtns.add(exportJsonBtn);
        p.add(jsonBtns);

        return p;
    }

    private void addSectionHeader(JPanel parent, String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(DesktopWidgets.FONT_BOLD);
        lbl.setForeground(DesktopWidgets.ACCENT_BLUE);
        lbl.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(lbl);
    }

    private interface DoubleConsumer {
        void accept(double value);
    }

    private interface IntConsumer {
        void accept(int value);
    }

    private void addSettingSlider(JPanel parent, String key, String label, double min, double max, double scale, String fmt, DoubleConsumer consumer) {
        SliderControl ctrl = createSliderControl(parent, label, min, max, scale, fmt, v -> {
            consumer.accept(v);
            scheduleRender();
        });
        settingSliders.put(key, ctrl);
    }

    private SliderControl createSliderControl(JPanel parent, String label, double min, double max, double scale, String fmt, DoubleConsumer consumer) {
        JPanel wrap = new JPanel(new BorderLayout(4, 2));
        wrap.setOpaque(false);
        wrap.setBorder(BorderFactory.createEmptyBorder(2, 0, 6, 0));
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel nameLbl = new JLabel(label);
        nameLbl.setFont(DesktopWidgets.FONT_UI);
        nameLbl.setForeground(DesktopWidgets.TEXT_PRIMARY);
        JLabel valLbl = new JLabel(String.format(Locale.ROOT, fmt, 0.0));
        valLbl.setFont(DesktopWidgets.FONT_MONO);
        valLbl.setForeground(DesktopWidgets.ACCENT_BLUE);
        top.add(nameLbl, BorderLayout.WEST);
        top.add(valLbl, BorderLayout.EAST);

        int iMin = (int) Math.round(min * scale);
        int iMax = (int) Math.round(max * scale);
        int iInit = Math.max(iMin, Math.min(iMax, 0));
        JSlider slider = new JSlider(iMin, iMax, iInit);
        slider.setOpaque(false);
        slider.addChangeListener(e -> {
            double v = slider.getValue() / scale;
            valLbl.setText(String.format(Locale.ROOT, fmt, v));
            if (!syncingSliders.get()) {
                consumer.accept(v);
            }
        });

        wrap.add(top, BorderLayout.NORTH);
        wrap.add(slider, BorderLayout.CENTER);
        parent.add(wrap);
        return new SliderControl(slider, valLbl, scale, fmt);
    }

    private void addRawSlider(JPanel parent, String label, int min, int max, int init, String fmt, IntConsumer consumer) {
        createSliderControl(parent, label, min, max, 1.0, fmt.replace("%+d", "%+.0f").replace("%d", "%.0f"),
                v -> consumer.accept((int) Math.round(v))).setValue(init);
    }

    private JComponent buildBottomStatusBar() {
        JPanel status = new JPanel(new BorderLayout());
        status.setBackground(DesktopWidgets.BG_PANEL);
        status.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, DesktopWidgets.BORDER_COLOR),
                BorderFactory.createEmptyBorder(5, 14, 5, 14)));

        statusLabel = new JLabel("Sẵn sàng · Kho 392 công thức Fuji X Weekly & Lõi xử lý LightCraft đã kích hoạt.");
        statusLabel.setFont(DesktopWidgets.FONT_SMALL);
        statusLabel.setForeground(DesktopWidgets.TEXT_SECONDARY);
        status.add(statusLabel, BorderLayout.WEST);

        JLabel shortcutHint = new JLabel("Phím tắt: Ctrl+O Mở ảnh · Ctrl+S Xuất ảnh · Ctrl+B Auto-Balance · Ctrl+Z/Y Undo/Redo · Cuộn chuột Zoom");
        shortcutHint.setFont(DesktopWidgets.FONT_SMALL);
        shortcutHint.setForeground(DesktopWidgets.TEXT_SECONDARY);
        status.add(shortcutHint, BorderLayout.EAST);
        return status;
    }

    private void setStatus(String text) {
        if (statusLabel != null) {
            statusLabel.setText(text);
        }
    }

    // =========================================================================
    // NẠP ẢNH, ÁP DỤNG CÔNG THỨC & KẾT XUẤT HẬU KỲ ĐA LUỒNG
    // =========================================================================
    private void loadBundledSamplePhoto(int index) {
        List<Recipe> bundled = new ArrayList<>();
        for (Recipe r : allRecipes) {
            if (r.sampleAsset != null && !r.sampleAsset.isEmpty()) {
                bundled.add(r);
            }
        }
        if (!bundled.isEmpty()) {
            Recipe chosen = bundled.get(Math.abs(index) % bundled.size());
            BufferedImage img = DesktopWidgets.DesktopFujiSampleLoader.loadResource("/" + chosen.sampleAsset);
            if (img != null) {
                setSourceImage(img, "Ảnh mẫu FujiXWeekly · " + chosen.name);
                applyRecipe(chosen, false);
                return;
            }
        }
        BufferedImage fallback = createFallbackTestChart(1280, 854);
        setSourceImage(fallback, "Ảnh mẫu Studio");
    }

    private static BufferedImage createFallbackTestChart(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        for (int y = 0; y < h; y++) {
            float t = (float) y / h;
            g2.setColor(new Color(
                    Math.round(35 + 180 * (1f - t)),
                    Math.round(90 + 110 * (1f - Math.abs(t - 0.5f))),
                    Math.round(140 + 100 * t)));
            g2.drawLine(0, y, w, y);
        }
        g2.dispose();
        return img;
    }

    private void setSourceImage(BufferedImage img, String label) {
        if (img == null) return;
        this.fullSourceImage = toArgbBufferedImage(img);
        int[] dims = ExportSizing.dimensions(fullSourceImage.getWidth(), fullSourceImage.getHeight(), 1, false, 1_100_000L);
        this.previewSourceImage = scaleImage(fullSourceImage, dims[0], dims[1]);
        this.previewSourcePixels = previewSourceImage.getRGB(
                0, 0, previewSourceImage.getWidth(), previewSourceImage.getHeight(),
                null, 0, previewSourceImage.getWidth());
        canvasPanel.resetView();
        setStatus("Đã nạp: " + label + " (" + fullSourceImage.getWidth() + "x" + fullSourceImage.getHeight() + " px)");
        scheduleRender();
    }

    private static BufferedImage toArgbBufferedImage(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) {
            return src;
        }
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = copy.createGraphics();
        g2.drawImage(src, 0, 0, null);
        g2.dispose();
        return copy;
    }

    private static BufferedImage scaleImage(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(src, 0, 0, w, h, null);
        g2.dispose();
        return out;
    }

    private void applyRecipe(Recipe r, boolean alsoLoadSamplePhoto) {
        if (r == null) return;
        pushUndoState();
        List<String> notes = new ArrayList<>();
        Settings mapped = r.adjustments(notes);
        applyFilmCharacterBias(mapped, r.film);
        this.settings = mapped;
        this.activeRecipeName = r.name;
        syncSlidersFromState();

        if (alsoLoadSamplePhoto) {
            DesktopWidgets.DesktopFujiSampleLoader.loadRecipeSampleAsync(r, sampleImg -> {
                if (sampleImg != null) {
                    setSourceImage(sampleImg, "Ảnh mẫu FujiXWeekly · " + r.name);
                }
            });
        } else {
            scheduleRender();
            setStatus("Đã áp dụng công thức: " + r.name + " [" + r.film + "] · Nhấn đúp để mở ảnh mẫu FujiXWeekly.");
        }
    }

    /** Bổ sung sắc thái đặc trưng cho hệ Film Simulation khi chưa gắn LUT .cube ngoài. */
    private void applyFilmCharacterBias(Settings s, String film) {
        if (film == null) return;
        String f = film.toLowerCase(Locale.ROOT);
        if (f.contains("acros") || f.contains("monochrome") || f.contains("b&w")) {
            s.color = -4.0;
        } else if (f.contains("sepia")) {
            s.color = -3.2;
            s.temperature = Math.max(s.temperature, 28.0);
            s.red = Math.max(s.red, 2.5);
            s.blue = Math.min(s.blue, -3.0);
        } else if (f.contains("velvia")) {
            s.color = Math.min(4.0, s.color + 1.5);
        } else if (f.contains("classic chrome")) {
            s.color = Math.max(-4.0, s.color - 0.6);
            s.shadow = Math.min(4.0, s.shadow + 0.5);
        } else if (f.contains("eterna")) {
            s.color = Math.max(-4.0, s.color - 1.0);
            s.highlight = Math.max(-2.0, s.highlight - 0.8);
        }
    }

    private void applyQuickFilmSimulation(String filmName) {
        pushUndoState();
        Settings s = new Settings();
        s.film = filmName;
        applyFilmCharacterBias(s, filmName);
        this.settings = s;
        this.activeRecipeName = filmName;
        syncSlidersFromState();
        scheduleRender();
        setStatus("Đã chuyển hệ Film Simulation: " + filmName);
    }

    private void syncSlidersFromState() {
        syncingSliders.set(true);
        try {
            if (settingSliders.containsKey("exposure")) settingSliders.get("exposure").setValue(settings.exposure);
            if (settingSliders.containsKey("highlight")) settingSliders.get("highlight").setValue(settings.highlight);
            if (settingSliders.containsKey("shadow")) settingSliders.get("shadow").setValue(settings.shadow);
            if (settingSliders.containsKey("color")) settingSliders.get("color").setValue(settings.color);
            if (settingSliders.containsKey("temperature")) settingSliders.get("temperature").setValue(settings.temperature);
            if (settingSliders.containsKey("red")) settingSliders.get("red").setValue(settings.red);
            if (settingSliders.containsKey("blue")) settingSliders.get("blue").setValue(settings.blue);
            if (settingSliders.containsKey("colorChrome")) settingSliders.get("colorChrome").setValue(settings.colorChrome);
            if (settingSliders.containsKey("chromeBlue")) settingSliders.get("chromeBlue").setValue(settings.chromeBlue);
            if (settingSliders.containsKey("sharpness")) settingSliders.get("sharpness").setValue(settings.sharpness);
            if (settingSliders.containsKey("clarity")) settingSliders.get("clarity").setValue(settings.clarity);
            if (settingSliders.containsKey("nr")) settingSliders.get("nr").setValue(settings.nr);
            if (settingSliders.containsKey("grain")) settingSliders.get("grain").setValue(settings.grain);
            if (settingSliders.containsKey("grainSize")) settingSliders.get("grainSize").setValue(settings.grainSize);
            if (settingSliders.containsKey("lutAmount")) settingSliders.get("lutAmount").setValue(settings.lutAmount);
            if (drCombo != null) {
                drCombo.setSelectedIndex(settings.dr == 400 ? 2 : (settings.dr == 200 ? 1 : 0));
            }
        } finally {
            syncingSliders.set(false);
        }
    }

    private void scheduleRender() {
        if (previewSourcePixels == null || previewSourceImage == null) return;
        if (activeRenderTask != null) {
            activeRenderTask.cancel(true);
        }
        final int w = previewSourceImage.getWidth();
        final int h = previewSourceImage.getHeight();
        final int[] srcPx = previewSourcePixels;
        final Settings snapSettings = settings.copy();
        final CubeLut snapLut = activeLut;

        activeRenderTask = renderExecutor.submit(() -> {
            try {
                int[] step1 = ColorEngine.process(srcPx, w, h, snapSettings, snapLut);
                int[] step2 = LightCraftEngine.processPixels(step1, w, h, lightCraft);
                BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                out.setRGB(0, 0, w, h, step2, 0, w);
                SwingUtilities.invokeLater(() -> {
                    currentRenderedPreview = out;
                    canvasPanel.setImages(previewSourceImage, out, snapSettings.film, activeRecipeName);
                    histogramPanel.updateFromPixels(step2, w, h);
                });
            } catch (Exception ignored) {
            }
        });
    }

    // =========================================================================
    // CÁC TÍNH NĂNG TRÊN THANH CÔNG CỤ (AUTO-BALANCE, UNDO/REDO, LUT, EXPORT)
    // =========================================================================
    private void runAutoBalance() {
        if (previewSourcePixels == null || previewSourceImage == null) return;
        pushUndoState();
        settings = AutoBalancer.analyze(
                previewSourcePixels,
                previewSourceImage.getWidth(),
                previewSourceImage.getHeight(),
                settings);
        syncSlidersFromState();
        scheduleRender();
        setStatus(String.format(Locale.ROOT,
                "Auto-Balance AI hoàn tất: EV %+.1f · Highlight %+.1f · Shadow %+.1f · Temp %+.0f",
                settings.exposure, settings.highlight, settings.shadow, settings.temperature));
    }

    private void pushUndoState() {
        undoStack.add(settings.copy());
        if (undoStack.size() > 30) {
            undoStack.remove(0);
        }
        redoStack.clear();
    }

    private void performUndo() {
        if (undoStack.isEmpty()) {
            setStatus("Không còn bước Hoàn tác.");
            return;
        }
        redoStack.add(settings.copy());
        settings = undoStack.remove(undoStack.size() - 1);
        syncSlidersFromState();
        scheduleRender();
        setStatus("Đã hoàn tác (Undo).");
    }

    private void performRedo() {
        if (redoStack.isEmpty()) {
            setStatus("Không còn bước Làm lại.");
            return;
        }
        undoStack.add(settings.copy());
        settings = redoStack.remove(redoStack.size() - 1);
        syncSlidersFromState();
        scheduleRender();
        setStatus("Đã làm lại (Redo).");
    }

    private void resetAllAdjustments() {
        pushUndoState();
        settings = new Settings();
        lightCraft.resetAll();
        activeLut = null;
        activeRecipeName = "Original";
        syncSlidersFromState();
        if (toneCurveWidget != null) toneCurveWidget.repaint();
        if (shadowsWheelWidget != null) {
            shadowsWheelWidget.repaint();
            midtonesWheelWidget.repaint();
            highlightsWheelWidget.repaint();
        }
        scheduleRender();
        setStatus("Đã đặt lại toàn bộ thông số chỉnh sửa về ảnh gốc.");
    }

    private void chooseAndOpenImage() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Chọn bức ảnh để chỉnh sửa");
        fc.setFileFilter(new FileNameExtensionFilter("Tệp hình ảnh (JPG, PNG, BMP, WEBP)", "jpg", "jpeg", "png", "bmp", "webp"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            openImageFile(fc.getSelectedFile());
        }
    }

    private void openImageFile(File file) {
        try {
            BufferedImage img = ImageIO.read(file);
            if (img == null) {
                JOptionPane.showMessageDialog(this, "Không đọc được định dạng ảnh này.", "Lỗi mở ảnh", JOptionPane.ERROR_MESSAGE);
                return;
            }
            setSourceImage(img, file.getName());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi khi đọc ảnh: " + ex.getMessage(), "Lỗi mở ảnh", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void chooseAndLoadCubeLut() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Chọn tệp LUT 3D (.cube)");
        fc.setFileFilter(new FileNameExtensionFilter("Adobe 3D LUT (.cube)", "cube"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            loadCubeLutFile(fc.getSelectedFile());
        }
    }

    private void loadCubeLutFile(File file) {
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            activeLut = CubeLut.parse(reader);
            scheduleRender();
            setStatus("Đã nạp LUT 3D: " + activeLut.title + " (" + activeLut.size + "³) từ " + file.getName());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Không nạp được LUT .cube: " + ex.getMessage(), "Lỗi LUT", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showOnlineLutDialog() {
        String repo = JOptionPane.showInputDialog(this,
                "Nhập kho GitHub chứa tệp .cube (mặc định: " + OnlineLuts.DEFAULT_REPO + "):",
                OnlineLuts.DEFAULT_REPO);
        if (repo == null || repo.trim().isEmpty()) return;

        setStatus("Đang truy vấn danh sách LUT 3D từ GitHub: " + repo + "...");
        new Thread(() -> {
            try {
                JSONArray items = OnlineLuts.discover(repo.trim());
                if (items.length() == 0) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Không tìm thấy tệp .cube nào trong kho này."));
                    return;
                }
                String[] names = new String[items.length()];
                for (int i = 0; i < items.length(); i++) {
                    JSONObject it = items.getJSONObject(i);
                    names[i] = it.optString("name") + " (" + (it.optLong("bytes") / 1024) + " KB)";
                }
                SwingUtilities.invokeLater(() -> {
                    String picked = (String) JOptionPane.showInputDialog(
                            this,
                            "Chọn LUT 3D (.cube) để tải về và áp dụng:",
                            "Kho LUT 3D GitHub (" + items.length() + " tệp)",
                            JOptionPane.PLAIN_MESSAGE,
                            null,
                            names,
                            names[0]);
                    if (picked != null) {
                        for (int i = 0; i < names.length; i++) {
                            if (names[i].equals(picked)) {
                                downloadAndApplyOnlineLut(items, i);
                                break;
                            }
                        }
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        "Lỗi kết nối GitHub LUT: " + ex.getMessage(), "Lỗi tải LUT", JOptionPane.ERROR_MESSAGE));
            }
        }, "OnlineLutDiscover").start();
    }

    private void downloadAndApplyOnlineLut(JSONArray items, int index) {
        new Thread(() -> {
            try {
                JSONObject item = items.getJSONObject(index);
                File lutDir = new File(System.getProperty("user.home"), ".iro-studio/luts");
                if (!lutDir.exists()) lutDir.mkdirs();
                File downloaded = OnlineLuts.download(lutDir, item);
                SwingUtilities.invokeLater(() -> loadCubeLutFile(downloaded));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        "Lỗi tải tệp LUT: " + ex.getMessage(), "Lỗi tải LUT", JOptionPane.ERROR_MESSAGE));
            }
        }, "OnlineLutDownload").start();
    }

    private void exportFullResolutionImage() {
        if (fullSourceImage == null) return;
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Lưu bức ảnh đã chỉnh sửa");
        fc.setSelectedFile(new File("IRO-" + activeRecipeName.replaceAll("[^a-zA-Z0-9_-]", "_") + ".jpg"));
        fc.setFileFilter(new FileNameExtensionFilter("JPEG Image (.jpg)", "jpg", "jpeg"));
        fc.addChoosableFileFilter(new FileNameExtensionFilter("PNG Image (.png)", "png"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File outFile = fc.getSelectedFile();
        setStatus("Đang kết xuất ảnh chất lượng gốc (" + fullSourceImage.getWidth() + "x" + fullSourceImage.getHeight() + ")...");
        new Thread(() -> {
            try {
                int w = fullSourceImage.getWidth();
                int h = fullSourceImage.getHeight();
                int[] srcPx = fullSourceImage.getRGB(0, 0, w, h, null, 0, w);
                int[] step1 = ColorEngine.process(srcPx, w, h, settings.copy(), activeLut);
                int[] step2 = LightCraftEngine.processPixels(step1, w, h, lightCraft);

                BufferedImage rendered = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                rendered.setRGB(0, 0, w, h, step2, 0, w);
                BufferedImage finalImg = DesktopWidgets.ImageCanvasPanel.renderFramedImage(
                        rendered, canvasPanel.getFrameMode(), settings.film, activeRecipeName);

                String lower = outFile.getName().toLowerCase(Locale.ROOT);
                String format = lower.endsWith(".png") ? "png" : "jpg";
                ImageIO.write(finalImg, format, outFile);
                SwingUtilities.invokeLater(() -> {
                    setStatus("Đã xuất ảnh thành công: " + outFile.getAbsolutePath());
                    JOptionPane.showMessageDialog(this,
                            "Đã lưu ảnh thành công tại:\n" + outFile.getAbsolutePath(),
                            "Xuất ảnh hoàn tất", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        "Lỗi khi xuất ảnh: " + ex.getMessage(), "Lỗi xuất ảnh", JOptionPane.ERROR_MESSAGE));
            }
        }, "ExportWorker").start();
    }

    private void showRecipeDetailDialog(Recipe r) {
        JDialog dlg = new JDialog(this, r.name + " · Fuji X Weekly Recipe", true);
        dlg.setSize(620, 520);
        dlg.setLocationRelativeTo(this);
        dlg.getContentPane().setBackground(DesktopWidgets.BG_PANEL);
        dlg.setLayout(new BorderLayout(12, 12));

        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.setBackground(DesktopWidgets.BG_PANEL);
        top.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));

        BufferedImage sample = DesktopWidgets.DesktopFujiSampleLoader.getCachedForRecipe(r);
        if (sample != null) {
            JLabel imgLbl = new JLabel(new ImageIcon(scaleThumbnail(sample, 240, 165)));
            imgLbl.setBorder(BorderFactory.createLineBorder(DesktopWidgets.BORDER_COLOR, 1));
            top.add(imgLbl, BorderLayout.WEST);
        }

        JTextArea area = new JTextArea(r.description());
        area.setEditable(false);
        area.setFont(DesktopWidgets.FONT_MONO);
        area.setBackground(DesktopWidgets.BG_CARD);
        area.setForeground(DesktopWidgets.TEXT_PRIMARY);
        area.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JScrollPane sp = new JScrollPane(area);
        sp.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        bottom.setBackground(DesktopWidgets.BG_PANEL);

        if (r.sourceUrl != null && !r.sourceUrl.isEmpty()) {
            JButton webBtn = DesktopWidgets.createStudioButton("Mở bài gốc trên fujixweekly.com", false);
            webBtn.addActionListener(e -> {
                try {
                    Desktop.getDesktop().browse(new URI(r.sourceUrl));
                } catch (Exception ignored) {
                }
            });
            bottom.add(webBtn);
        }

        JButton loadSampleBtn = DesktopWidgets.createStudioButton("Mở ảnh mẫu này vào bàn dựng", true);
        loadSampleBtn.addActionListener(e -> {
            applyRecipe(r, true);
            dlg.dispose();
        });
        bottom.add(loadSampleBtn);

        JButton closeBtn = DesktopWidgets.createStudioButton("Đóng", false);
        closeBtn.addActionListener(e -> dlg.dispose());
        bottom.add(closeBtn);

        dlg.add(top, BorderLayout.NORTH);
        dlg.add(sp, BorderLayout.CENTER);
        dlg.add(bottom, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private void importSettingsJson() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Nhập tệp JSON thông số màu");
        fc.setFileFilter(new FileNameExtensionFilter("JSON Recipe / Settings (.json)", "json"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try (FileInputStream in = new FileInputStream(fc.getSelectedFile())) {
            JSONObject obj = new JSONObject(readStreamUtf8(in));
            pushUndoState();
            if (obj.has("rawSettings")) {
                Recipe r = new Recipe(obj);
                applyRecipe(r, false);
            } else {
                settings = SettingsCodec.decode(obj);
                syncSlidersFromState();
                scheduleRender();
            }
            setStatus("Đã nhập thông số JSON từ: " + fc.getSelectedFile().getName());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Tệp JSON không hợp lệ: " + ex.getMessage(), "Lỗi nhập JSON", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportSettingsJson() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Xuất tệp JSON thông số màu");
        fc.setSelectedFile(new File("iro-settings.json"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try (FileOutputStream out = new FileOutputStream(fc.getSelectedFile())) {
            JSONObject json = SettingsCodec.encode(settings);
            out.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
            setStatus("Đã xuất JSON: " + fc.getSelectedFile().getAbsolutePath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Không lưu được JSON: " + ex.getMessage(), "Lỗi xuất JSON", JOptionPane.ERROR_MESSAGE);
        }
    }

    // =========================================================================
    // PHÍM TẮT & KÉO-THẢ TỆP TRỰC TIẾP TỪ WINDOWS EXPLORER
    // =========================================================================
    private void setupKeyboardShortcuts() {
        JComponent root = getRootPane();
        bindKey(root, KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK), "open", this::chooseAndOpenImage);
        bindKey(root, KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK), "save", this::exportFullResolutionImage);
        bindKey(root, KeyStroke.getKeyStroke(KeyEvent.VK_B, KeyEvent.CTRL_DOWN_MASK), "autobalance", this::runAutoBalance);
        bindKey(root, KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK), "undo", this::performUndo);
        bindKey(root, KeyStroke.getKeyStroke(KeyEvent.VK_Y, KeyEvent.CTRL_DOWN_MASK), "redo", this::performRedo);
    }

    private static void bindKey(JComponent root, KeyStroke ks, String name, Runnable action) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, name);
        root.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void setupDragAndDrop() {
        setDropTarget(new DropTarget(this, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    Object data = dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (data instanceof List) {
                        List<File> files = (List<File>) data;
                        for (File f : files) {
                            String low = f.getName().toLowerCase(Locale.ROOT);
                            if (low.endsWith(".cube")) {
                                loadCubeLutFile(f);
                            } else if (low.endsWith(".jpg") || low.endsWith(".jpeg") || low.endsWith(".png")
                                    || low.endsWith(".bmp") || low.endsWith(".webp")) {
                                openImageFile(f);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }, true));
    }

    // =========================================================================
    // ĐIỂM KHỞI CHẠY CHÍNH TRÊN WINDOWS DESKTOP
    // =========================================================================
    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        String screenshotOut = null;
        for (int i = 0; i < args.length - 1; i++) {
            if ("--screenshot".equals(args[i])) {
                screenshotOut = args[i + 1];
            }
        }
        final String snapTarget = screenshotOut;

        SwingUtilities.invokeLater(() -> {
            IroDesktopApp app = new IroDesktopApp();
            app.setVisible(true);
            if (!app.recipeListModel.isEmpty()) {
                app.recipeList.setSelectedIndex(0);
            }

            if (snapTarget != null) {
                Timer timer = new Timer(1600, e -> {
                    try {
                        BufferedImage shot = new BufferedImage(app.getWidth(), app.getHeight(), BufferedImage.TYPE_INT_RGB);
                        Graphics2D g2 = shot.createGraphics();
                        app.paint(g2);
                        g2.dispose();
                        File out = new File(snapTarget);
                        if (out.getParentFile() != null) out.getParentFile().mkdirs();
                        ImageIO.write(shot, "png", out);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        System.exit(0);
                    }
                });
                timer.setRepeats(false);
                timer.start();
            }
        });
    }
}

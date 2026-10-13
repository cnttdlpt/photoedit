package vn.iro.editor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
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
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
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
 * Ứng dụng máy tính <b>IRO Studio CC 2025 · Windows Desktop Edition</b>.
 * Thiết kế UX/UI theo chuẩn <b>Adobe Photoshop CC 2025 / Camera Raw 17 (Adobe Spectrum Dark UI)</b>.
 */
public final class IroDesktopApp extends JFrame {

    private static final String[] FILM_FILTERS = {
            "Tất cả (392)",
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

    private final List<Recipe> allRecipes = new ArrayList<>();
    private final DefaultListModel<Recipe> recipeListModel = new DefaultListModel<>();
    private JList<Recipe> recipeList;
    private JTextField searchField;
    private int activeFilmFilterIndex = 0;
    private JButton filmFilterTriggerBtn;
    private JLabel statusLabel;
    private JLabel zoomStatusLabel;

    private Settings settings = new Settings();
    private final LightCraftEngine.State lightCraft = new LightCraftEngine.State();
    private CubeLut activeLut = null;
    private String activeRecipeName = "Golden Negative";
    private int currentSampleIndex = 0;

    private final List<Settings> undoStack = new ArrayList<>();
    private final List<Settings> redoStack = new ArrayList<>();

    private BufferedImage fullSourceImage;
    private BufferedImage previewSourceImage;
    private int[] previewSourcePixels;

    private DesktopWidgets.PhotoshopToolStrip toolStrip;
    private DesktopWidgets.ImageCanvasPanel canvasPanel;
    private DesktopWidgets.HistogramPanel histogramPanel;
    private DesktopWidgets.ToneCurveWidget toneCurveWidget;
    private DesktopWidgets.ColorWheelWidget shadowsWheelWidget;
    private DesktopWidgets.ColorWheelWidget midtonesWheelWidget;
    private DesktopWidgets.ColorWheelWidget highlightsWheelWidget;

    private CardLayout inspectorCards;
    private JPanel inspectorCardHost;
    private final DesktopWidgets.SpectrumButton[] inspectorTabBtns = new DesktopWidgets.SpectrumButton[4];

    private final Map<String, DesktopWidgets.SpectrumSlider> settingSliders = new HashMap<>();
    private final DesktopWidgets.SpectrumButton[] drButtons = new DesktopWidgets.SpectrumButton[3];
    private final AtomicBoolean syncingSliders = new AtomicBoolean(false);

    private int selectedMixerBand = 0;
    private final DesktopWidgets.SpectrumButton[] mixerBandBtns = new DesktopWidgets.SpectrumButton[8];
    private DesktopWidgets.SpectrumSlider mixerHueSlider;
    private DesktopWidgets.SpectrumSlider mixerSatSlider;
    private DesktopWidgets.SpectrumSlider mixerLumSlider;

    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "IRO-Desktop-Render");
        t.setDaemon(true);
        return t;
    });
    private Future<?> activeRenderTask;

    public IroDesktopApp() {
        super("Adobe Camera Raw / IRO Studio CC 2025 · Fujifilm & LightCraft Photo Editor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1480, 900);
        setMinimumSize(new Dimension(1160, 720));
        setLocationRelativeTo(null);
        getContentPane().setBackground(DesktopWidgets.PS_DARKEST);
        setLayout(new BorderLayout(0, 0));

        setWindowIcon();
        loadRecipesCatalog();

        add(buildPhotoshopTopChrome(), BorderLayout.NORTH);
        add(buildLeftDockArea(), BorderLayout.WEST);
        add(buildCenterArtboardArea(), BorderLayout.CENTER);
        add(buildRightCameraRawDock(), BorderLayout.EAST);
        add(buildPhotoshopStatusBar(), BorderLayout.SOUTH);

        setupKeyboardShortcuts();
        setupDragAndDrop();

        loadBundledSamplePhoto(0);
    }

    private void setWindowIcon() {
        BufferedImage icon = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = icon.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(DesktopWidgets.PS_LOGO_BG);
        g2.fillRoundRect(2, 2, 60, 60, 14, 14);
        g2.setColor(DesktopWidgets.PS_LOGO_FG);
        g2.setStroke(new java.awt.BasicStroke(2.5f));
        g2.drawRoundRect(2, 2, 59, 59, 14, 14);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 30));
        g2.drawString("Ir", 16, 43);
        g2.dispose();
        setIconImage(icon);
    }

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
    // 1. THANH MENU & OPTIONS BAR 2 TẦNG CHUẨN PHOTOSHOP CC 2025
    // =========================================================================
    private JComponent buildPhotoshopTopChrome() {
        JPanel chrome = new JPanel();
        chrome.setLayout(new BoxLayout(chrome, BoxLayout.Y_AXIS));
        chrome.setBackground(DesktopWidgets.PS_HEADER);

        // TẦNG 1: Application Menu Bar + Workspace Switcher (Photoshop CC 2025 Header)
        JPanel menuRow = new JPanel(new BorderLayout(10, 0));
        menuRow.setBackground(new Color(0x1F, 0x1F, 0x1F));
        menuRow.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, DesktopWidgets.PS_DIVIDER),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));

        JPanel menuLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        menuLeft.setOpaque(false);

        // Logo vuông [Ir] kiểu [Ps] Photoshop CC 2025
        JLabel irLogo = new JLabel("Ir", SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(DesktopWidgets.PS_LOGO_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.setColor(DesktopWidgets.PS_LOGO_FG);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                super.paintComponent(g2);
                g2.dispose();
            }
        };
        irLogo.setPreferredSize(new Dimension(26, 24));
        irLogo.setFont(new Font("Segoe UI", Font.BOLD, 13));
        irLogo.setForeground(DesktopWidgets.PS_LOGO_FG);
        menuLeft.add(irLogo);

        addTopMenuLabel(menuLeft, "Tệp (File)", this::chooseAndOpenImage);
        addTopMenuLabel(menuLeft, "Chỉnh sửa (Edit)", this::performUndo);
        addTopMenuLabel(menuLeft, "Hình ảnh (Auto AI)", this::runAutoBalance);
        addTopMenuLabel(menuLeft, "Lớp & Mask", () -> switchInspectorTab(3));
        addTopMenuLabel(menuLeft, "Bộ lọc (3D LUT)", this::chooseAndLoadCubeLut);
        addTopMenuLabel(menuLeft, "Chế độ xem (Split)", () -> canvasPanel.setSplitView(!canvasPanel.isSplitView()));

        JPanel menuRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        menuRight.setOpaque(false);

        JLabel wsTag = new JLabel("Workspace:");
        wsTag.setFont(DesktopWidgets.FONT_SMALL);
        wsTag.setForeground(DesktopWidgets.TEXT_MUTED);
        menuRight.add(wsTag);

        DesktopWidgets.SpectrumButton wsRaw = new DesktopWidgets.SpectrumButton("Camera Raw 17", false, true);
        wsRaw.setActiveState(true);
        wsRaw.addActionListener(e -> switchInspectorTab(0));
        menuRight.add(wsRaw);

        DesktopWidgets.SpectrumButton wsGrading = new DesktopWidgets.SpectrumButton("Color Grading", false, true);
        wsGrading.addActionListener(e -> switchInspectorTab(2));
        menuRight.add(wsGrading);

        JButton exportTopBtn = DesktopWidgets.createCompactButton("Xuất bản (Ctrl+S)", true);
        exportTopBtn.addActionListener(e -> exportFullResolutionImage());
        menuRight.add(exportTopBtn);

        menuRow.add(menuLeft, BorderLayout.WEST);
        menuRow.add(menuRight, BorderLayout.EAST);

        // TẦNG 2: Photoshop Tool Options Bar (#2C2C2C)
        JPanel optionsRow = new JPanel(new BorderLayout(8, 0));
        optionsRow.setBackground(DesktopWidgets.PS_HEADER);
        optionsRow.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, DesktopWidgets.PS_DIVIDER),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)));

        JPanel optLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        optLeft.setOpaque(false);

        optLeft.add(createOptionBarButton("Mở ảnh (Ctrl+O)", false, this::chooseAndOpenImage));
        optLeft.add(createOptionBarButton("Đổi ảnh mẫu Fuji", false, () -> {
            currentSampleIndex++;
            loadBundledSamplePhoto(currentSampleIndex);
        }));
        optLeft.add(createDividerLabel());
        optLeft.add(createOptionBarButton("Auto-Balance AI (Ctrl+B)", false, this::runAutoBalance));
        optLeft.add(createOptionBarButton("< Hoàn tác", false, this::performUndo));
        optLeft.add(createOptionBarButton("Làm lại >", false, this::performRedo));
        optLeft.add(createDividerLabel());

        DesktopWidgets.SpectrumButton splitToggle = new DesktopWidgets.SpectrumButton("Chia đôi Trước / Sau (Y)", false, true);
        splitToggle.addActionListener(e -> {
            boolean next = !canvasPanel.isSplitView();
            canvasPanel.setSplitView(next);
            splitToggle.setActiveState(next);
        });
        optLeft.add(splitToggle);

        JButton holdOrigBtn = DesktopWidgets.createCompactButton("Giữ xem Gốc (Space)", false);
        holdOrigBtn.getModel().addChangeListener(e ->
                canvasPanel.setShowOriginalOnly(holdOrigBtn.getModel().isPressed()));
        optLeft.add(holdOrigBtn);

        optLeft.add(createOptionBarButton("Đặt lại toàn bộ", false, this::resetAllAdjustments));

        JPanel optRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        optRight.setOpaque(false);
        optRight.add(createOptionBarButton("Nạp LUT .cube", false, this::chooseAndLoadCubeLut));
        optRight.add(createOptionBarButton("Kho LUT GitHub", false, this::showOnlineLutDialog));
        optRight.add(createOptionBarButton("Nhập JSON", false, this::importSettingsJson));
        optRight.add(createOptionBarButton("Xuất JSON", false, this::exportSettingsJson));

        optionsRow.add(optLeft, BorderLayout.WEST);
        optionsRow.add(optRight, BorderLayout.EAST);

        chrome.add(menuRow);
        chrome.add(optionsRow);
        return chrome;
    }

    private void addTopMenuLabel(JPanel parent, String text, Runnable action) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(DesktopWidgets.FONT_UI);
        lbl.setForeground(DesktopWidgets.TEXT_SECONDARY);
        lbl.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        lbl.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                lbl.setForeground( Color.WHITE);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                lbl.setForeground(DesktopWidgets.TEXT_SECONDARY);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                action.run();
            }
        });
        parent.add(lbl);
    }

    private static JButton createOptionBarButton(String text, boolean blue, Runnable r) {
        JButton b = DesktopWidgets.createCompactButton(text, blue);
        b.addActionListener(e -> r.run());
        return b;
    }

    private static JLabel createDividerLabel() {
        JLabel l = new JLabel("|");
        l.setForeground(new Color(0x45, 0x45, 0x45));
        l.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 3));
        return l;
    }

    // =========================================================================
    // 2. CỘT TRÁI: PHOTOSHOP TOOL STRIP (42px) + PRESETS / FUJI X WEEKLY DOCK (300px)
    // =========================================================================
    private JComponent buildLeftDockArea() {
        JPanel leftWrap = new JPanel(new BorderLayout(0, 0));
        leftWrap.setBackground(DesktopWidgets.PS_PANEL);

        toolStrip = new DesktopWidgets.PhotoshopToolStrip(idx -> {
            switch (idx) {
                case 0: // Move / Pan
                    canvasPanel.setSplitView(false);
                    break;
                case 1: // Zoom 150% / 100%
                    canvasPanel.setZoom(canvasPanel.getZoom() < 1.4 ? 1.5 : 1.0);
                    break;
                case 2: // Split View
                    canvasPanel.setSplitView(!canvasPanel.isSplitView());
                    break;
                case 3: // Auto-Balance AI
                    runAutoBalance();
                    break;
                case 4: // Radial Mask
                    lightCraft.maskMode = LightCraftEngine.MaskMode.RADIAL;
                    switchInspectorTab(3);
                    scheduleRender();
                    break;
                case 5: // Linear Mask
                    lightCraft.maskMode = LightCraftEngine.MaskMode.LINEAR;
                    switchInspectorTab(3);
                    scheduleRender();
                    break;
                case 6: // AI Sky Mask
                    lightCraft.maskMode = LightCraftEngine.MaskMode.SKY;
                    switchInspectorTab(3);
                    scheduleRender();
                    break;
                case 7: // Frame Tool
                    int nextFrame = (canvasPanel.getFrameMode() + 1) % 4;
                    canvasPanel.setFrameMode(nextFrame);
                    switchInspectorTab(3);
                    break;
                default:
                    break;
            }
        });
        leftWrap.add(toolStrip, BorderLayout.WEST);

        // Panel Presets & Fuji X Weekly
        JPanel presetDock = new JPanel(new BorderLayout(0, 6));
        presetDock.setPreferredSize(new Dimension(305, 600));
        presetDock.setBackground(DesktopWidgets.PS_PANEL);
        presetDock.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, DesktopWidgets.PS_DIVIDER));

        JPanel dockHeader = new JPanel();
        dockHeader.setLayout(new BoxLayout(dockHeader, BoxLayout.Y_AXIS));
        dockHeader.setBackground(DesktopWidgets.PS_PANEL);
        dockHeader.setBorder(BorderFactory.createEmptyBorder(8, 10, 6, 10));

        // Tab Header kiểu Photoshop Panel
        JLabel tabTitle = new JLabel("•  PRESETS · FUJI X WEEKLY (392)");
        tabTitle.setFont(DesktopWidgets.FONT_SECTION);
        tabTitle.setForeground(DesktopWidgets.TEXT_PRIMARY);
        tabTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        dockHeader.add(tabTitle);
        dockHeader.add(Box.createVerticalStrut(8));

        // Ô tìm kiếm tối chuẩn Spectrum
        searchField = new JTextField();
        searchField.setFont(DesktopWidgets.FONT_UI);
        searchField.setBackground(DesktopWidgets.BG_INPUT);
        searchField.setForeground(DesktopWidgets.TEXT_PRIMARY);
        searchField.setCaretColor(DesktopWidgets.PS_BLUE_LIGHT);
        searchField.setToolTipText("Tìm kiếm trong 392 công thức Fuji X Weekly (tên màu, cảm biến X-Trans...)");
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DesktopWidgets.PS_BORDER, 1),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        searchField.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchField.setMaximumSize(new Dimension(300, 28));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filterRecipeList(); }
            @Override public void removeUpdate(DocumentEvent e) { filterRecipeList(); }
            @Override public void changedUpdate(DocumentEvent e) { filterRecipeList(); }
        });
        dockHeader.add(searchField);
        dockHeader.add(Box.createVerticalStrut(6));

        // Nút chọn hệ Film bằng Popup Menu tối (thay thế hoàn toàn JComboBox viền trắng của Swing)
        filmFilterTriggerBtn = DesktopWidgets.createCompactButton("Hệ Film: Tất cả (392)  •", false);
        filmFilterTriggerBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        filmFilterTriggerBtn.setMaximumSize(new Dimension(300, 26));
        filmFilterTriggerBtn.addActionListener(e -> showDarkFilmFilterMenu());
        dockHeader.add(filmFilterTriggerBtn);

        presetDock.add(dockHeader, BorderLayout.NORTH);

        // Danh sách công thức Fuji X Weekly
        recipeList = new JList<>(recipeListModel);
        recipeList.setBackground(DesktopWidgets.PS_DARKEST);
        recipeList.setForeground(DesktopWidgets.TEXT_PRIMARY);
        recipeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        recipeList.setFixedCellHeight(68);
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

        JScrollPane scroll = DesktopWidgets.createDarkScrollPane(recipeList);
        scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, DesktopWidgets.PS_BORDER));
        presetDock.add(scroll, BorderLayout.CENTER);

        // Nút thao tác dưới đáy Presets Dock
        JPanel footer = new JPanel(new GridLayout(2, 1, 0, 5));
        footer.setBackground(DesktopWidgets.PS_PANEL);
        footer.setBorder(BorderFactory.createEmptyBorder(6, 10, 8, 10));

        JButton openSampleBtn = DesktopWidgets.createStudioButton("Mở ảnh mẫu FujiXWeekly của công thức này", true);
        openSampleBtn.addActionListener(e -> {
            Recipe selected = recipeList.getSelectedValue();
            if (selected != null) applyRecipe(selected, true);
        });
        footer.add(openSampleBtn);

        JButton detailBtn = DesktopWidgets.createStudioButton("Thông số gốc & bài viết fujixweekly.com", false);
        detailBtn.addActionListener(e -> {
            Recipe selected = recipeList.getSelectedValue();
            if (selected != null) showRecipeDetailDialog(selected);
        });
        footer.add(detailBtn);

        presetDock.add(footer, BorderLayout.SOUTH);
        leftWrap.add(presetDock, BorderLayout.CENTER);

        filterRecipeList();
        return leftWrap;
    }

    private void showDarkFilmFilterMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(DesktopWidgets.PS_SURFACE);
        menu.setBorder(BorderFactory.createLineBorder(DesktopWidgets.PS_BORDER, 1));
        for (int i = 0; i < FILM_FILTERS.length; i++) {
            final int idx = i;
            JMenuItem item = new JMenuItem(FILM_FILTERS[i]);
            item.setFont(DesktopWidgets.FONT_UI);
            item.setBackground(idx == activeFilmFilterIndex ? DesktopWidgets.PS_BLUE : DesktopWidgets.PS_SURFACE);
            item.setForeground(DesktopWidgets.TEXT_PRIMARY);
            item.addActionListener(e -> {
                activeFilmFilterIndex = idx;
                filmFilterTriggerBtn.setText("Hệ Film: " + FILM_FILTERS[idx] + "  •");
                filterRecipeList();
            });
            menu.add(item);
        }
        menu.show(filmFilterTriggerBtn, 0, filmFilterTriggerBtn.getHeight());
    }

    private void filterRecipeList() {
        String q = searchField != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        String filmFilter = activeFilmFilterIndex > 0 ? FILM_FILTERS[activeFilmFilterIndex].toLowerCase(Locale.ROOT) : "";

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

    private final class RecipeCardRenderer extends JPanel implements ListCellRenderer<Recipe> {
        private final JLabel thumbLabel = new JLabel();
        private final JLabel nameLabel = new JLabel();
        private final JLabel metaLabel = new JLabel();
        private final JLabel sensorLabel = new JLabel();
        private boolean selectedCell = false;

        RecipeCardRenderer() {
            setLayout(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

            thumbLabel.setPreferredSize(new Dimension(74, 52));
            thumbLabel.setHorizontalAlignment(SwingConstants.CENTER);
            thumbLabel.setOpaque(true);
            thumbLabel.setBackground(DesktopWidgets.PS_SURFACE);
            thumbLabel.setBorder(BorderFactory.createLineBorder(DesktopWidgets.PS_BORDER, 1));
            add(thumbLabel, BorderLayout.WEST);

            JPanel textPanel = new JPanel();
            textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
            textPanel.setOpaque(false);

            nameLabel.setFont(DesktopWidgets.FONT_BOLD);
            nameLabel.setForeground(DesktopWidgets.TEXT_PRIMARY);
            metaLabel.setFont(DesktopWidgets.FONT_SMALL);
            metaLabel.setForeground(DesktopWidgets.PS_BLUE_LIGHT);
            sensorLabel.setFont(DesktopWidgets.FONT_SMALL);
            sensorLabel.setForeground(DesktopWidgets.TEXT_MUTED);

            textPanel.add(nameLabel);
            textPanel.add(Box.createVerticalStrut(2));
            textPanel.add(metaLabel);
            textPanel.add(Box.createVerticalStrut(2));
            textPanel.add(sensorLabel);
            add(textPanel, BorderLayout.CENTER);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(new Color(0x28, 0x28, 0x28));
            g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            if (selectedCell) {
                g.setColor(DesktopWidgets.PS_BLUE_LIGHT);
                g.fillRect(0, 0, 3, getHeight());
            }
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends Recipe> list, Recipe value, int index, boolean isSelected, boolean cellHasFocus) {
            this.selectedCell = isSelected;
            setBackground(isSelected ? new Color(0x2B, 0x35, 0x42) : DesktopWidgets.PS_DARKEST);
            nameLabel.setText(value.name);
            metaLabel.setText(value.film);
            sensorLabel.setText(value.sensors.isEmpty() ? "Fuji X Weekly" : value.sensors);

            BufferedImage thumb = DesktopWidgets.DesktopFujiSampleLoader.getCachedForRecipe(value);
            if (thumb != null) {
                thumbLabel.setIcon(new ImageIcon(scaleThumbnail(thumb, 72, 50)));
                thumbLabel.setText("");
            } else {
                thumbLabel.setIcon(null);
                thumbLabel.setForeground(DesktopWidgets.TEXT_MUTED);
                thumbLabel.setFont(DesktopWidgets.FONT_SMALL);
                thumbLabel.setText("RAW");
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
    // 3. KHUNG GIỮA: ARTBOARD PHOTOSHOP CC 2025 + CONTEXTUAL TASK BAR NỔI
    // =========================================================================
    private JComponent buildCenterArtboardArea() {
        JPanel workspace = new JPanel(new BorderLayout(0, 0));
        workspace.setBackground(DesktopWidgets.PS_CANVAS_BG);

        canvasPanel = new DesktopWidgets.ImageCanvasPanel();
        canvasPanel.setOnZoomChanged(() -> {
            if (zoomStatusLabel != null) {
                zoomStatusLabel.setText(String.format(Locale.ROOT, " %.0f%% ", canvasPanel.getZoom() * 100.0));
            }
        });
        workspace.add(canvasPanel, BorderLayout.CENTER);

        // Thanh Contextual Task Bar đặc trưng của Photoshop CC 2025 ở đáy vùng Canvas
        JPanel bottomBarWrap = new JPanel(new BorderLayout(0, 0));
        bottomBarWrap.setBackground(DesktopWidgets.PS_CANVAS_BG);
        bottomBarWrap.setBorder(BorderFactory.createEmptyBorder(6, 14, 10, 14));

        JPanel contextualBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 5)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x25, 0x25, 0x25));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(new Color(0x3E, 0x3E, 0x3E));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
            }
        };
        contextualBar.setOpaque(false);

        JLabel ctxTitle = new JLabel("Contextual Task Bar:");
        ctxTitle.setFont(DesktopWidgets.FONT_BOLD);
        ctxTitle.setForeground(DesktopWidgets.TEXT_SECONDARY);
        contextualBar.add(ctxTitle);

        String[][] quickFilms = {
                {"Original", "Gốc"},
                {"Classic Chrome", "Classic Chrome"},
                {"Classic Negative", "Classic Neg."},
                {"Velvia", "Velvia"},
                {"Provia", "Provia"},
                {"Astia", "Astia"},
                {"Nostalgic Neg.", "Nostalgic Neg."},
                {"Eterna", "Eterna"},
                {"Reala Ace", "Reala Ace"},
                {"Acros", "Acros B&W"}
        };
        for (String[] pair : quickFilms) {
            final String filmKey = pair[0];
            JButton b = DesktopWidgets.createCompactButton(pair[1], false);
            b.addActionListener(e -> applyQuickFilmSimulation(filmKey));
            contextualBar.add(b);
        }

        contextualBar.add(createDividerLabel());
        JButton fitBtn = DesktopWidgets.createCompactButton("Fit 100%", false);
        fitBtn.addActionListener(e -> canvasPanel.resetView());
        contextualBar.add(fitBtn);

        bottomBarWrap.add(contextualBar, BorderLayout.CENTER);
        workspace.add(bottomBarWrap, BorderLayout.SOUTH);
        return workspace;
    }

    // =========================================================================
    // 4. CỘT PHẢI: CAMERA RAW 17 INSPECTOR DOCK (HISTOGRAM + 4 TAB TỐI SPECTRUM)
    // =========================================================================
    private JComponent buildRightCameraRawDock() {
        JPanel dock = new JPanel(new BorderLayout(0, 0));
        dock.setPreferredSize(new Dimension(368, 600));
        dock.setBackground(DesktopWidgets.PS_PANEL);
        dock.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, DesktopWidgets.PS_DIVIDER));

        // 1. Khung Histogram + EXIF Telemetry trên đỉnh
        JPanel topStack = new JPanel(new BorderLayout(0, 0));
        topStack.setBackground(DesktopWidgets.PS_PANEL);
        histogramPanel = new DesktopWidgets.HistogramPanel();
        topStack.add(histogramPanel, BorderLayout.NORTH);

        // 2. Thanh Tab tùy chỉnh kiểu Adobe Spectrum (thay thế hoàn toàn JTabbedPane viền trắng của Swing)
        JPanel tabStrip = new JPanel(new GridLayout(1, 4, 4, 0));
        tabStrip.setBackground(DesktopWidgets.PS_HEADER);
        tabStrip.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, DesktopWidgets.PS_BORDER),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));

        String[] tabNames = {"Cơ bản", "Curve", "Color Wheel", "Mask & FX"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            inspectorTabBtns[i] = new DesktopWidgets.SpectrumButton(tabNames[i], false, true);
            inspectorTabBtns[i].addActionListener(e -> switchInspectorTab(idx));
            tabStrip.add(inspectorTabBtns[i]);
        }
        inspectorTabBtns[0].setActiveState(true);
        topStack.add(tabStrip, BorderLayout.SOUTH);

        dock.add(topStack, BorderLayout.NORTH);

        // 3. Nội dung 4 Tab trong CardLayout
        inspectorCards = new CardLayout();
        inspectorCardHost = new JPanel(inspectorCards);
        inspectorCardHost.setBackground(DesktopWidgets.PS_PANEL);

        inspectorCardHost.add(DesktopWidgets.createDarkScrollPane(buildFujiAdjustTab()), "tab0");
        inspectorCardHost.add(DesktopWidgets.createDarkScrollPane(buildLightCraftCurveTab()), "tab1");
        inspectorCardHost.add(DesktopWidgets.createDarkScrollPane(buildLightCraftGradingTab()), "tab2");
        inspectorCardHost.add(DesktopWidgets.createDarkScrollPane(buildMaskAndFrameTab()), "tab3");

        dock.add(inspectorCardHost, BorderLayout.CENTER);
        return dock;
    }

    private void switchInspectorTab(int index) {
        if (inspectorCards == null || inspectorCardHost == null) return;
        for (int i = 0; i < inspectorTabBtns.length; i++) {
            if (inspectorTabBtns[i] != null) {
                inspectorTabBtns[i].setActiveState(i == index);
            }
        }
        inspectorCards.show(inspectorCardHost, "tab" + index);
    }

    private JPanel buildFujiAdjustTab() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(DesktopWidgets.PS_PANEL);
        p.setBorder(BorderFactory.createEmptyBorder(8, 12, 14, 12));

        addAccordionHeader(p, "•  ÁNH SÁNG (LIGHT & DYNAMIC RANGE)");
        addSettingSlider(p, "exposure", "Exposure (Phơi sáng)", -3.0, 3.0, 0.0, "%+.2f EV", v -> settings.exposure = v);
        addSettingSlider(p, "highlight", "Highlights (Vùng sáng)", -2.0, 4.0, 0.0, "%+.1f", v -> settings.highlight = v);
        addSettingSlider(p, "shadow", "Shadows (Vùng tối)", -2.0, 4.0, 0.0, "%+.1f", v -> settings.shadow = v);

        // Bộ chọn Dynamic Range bằng 3 nút phẳng (thay cho JComboBox trắng)
        JPanel drRow = new JPanel(new GridLayout(1, 3, 6, 0));
        drRow.setOpaque(false);
        drRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        drRow.setMaximumSize(new Dimension(330, 26));
        String[] drLabels = {"DR100", "DR200", "DR400"};
        int[] drValues = {100, 200, 400};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            drButtons[i] = new DesktopWidgets.SpectrumButton(drLabels[i], false, true);
            if (i == 0) drButtons[i].setActiveState(true);
            drButtons[i].addActionListener(e -> {
                settings.dr = drValues[idx];
                for (int k = 0; k < 3; k++) drButtons[k].setActiveState(k == idx);
                scheduleRender();
            });
            drRow.add(drButtons[i]);
        }
        p.add(Box.createVerticalStrut(4));
        p.add(drRow);
        p.add(Box.createVerticalStrut(10));

        addAccordionHeader(p, "•  MÀU SẮC & CÂN BẰNG TRẮNG (COLOR & WB)");
        addSettingSlider(p, "temperature", "Temp (Nhiệt độ màu)", -100.0, 100.0, 0.0, "%+.0f", v -> settings.temperature = v)
                .withGradient(new Color(0x28, 0x68, 0xC8), new Color(0xD8, 0x8A, 0x28));
        addSettingSlider(p, "red", "WB Red Shift", -9.0, 9.0, 0.0, "%+.1f", v -> settings.red = v)
                .withGradient(new Color(0x24, 0x8A, 0x8A), new Color(0xC8, 0x3E, 0x3E));
        addSettingSlider(p, "blue", "WB Blue Shift", -9.0, 9.0, 0.0, "%+.1f", v -> settings.blue = v)
                .withGradient(new Color(0xB8, 0x98, 0x28), new Color(0x32, 0x68, 0xD8));
        addSettingSlider(p, "color", "Color (Độ đậm màu Fuji)", -4.0, 4.0, 0.0, "%+.1f", v -> settings.color = v)
                .withGradient(new Color(0x55, 0x55, 0x55), new Color(0xE0, 0x48, 0x78));
        addSettingSlider(p, "colorChrome", "Color Chrome Effect", 0.0, 2.0, 0.0, "%.1f", v -> settings.colorChrome = v);
        addSettingSlider(p, "chromeBlue", "Color Chrome FX Blue", 0.0, 2.0, 0.0, "%.1f", v -> settings.chromeBlue = v);

        p.add(Box.createVerticalStrut(8));
        addAccordionHeader(p, "•  CHI TIẾT, HẠT PHIM & 3D LUT (DETAIL & FX)");
        addSettingSlider(p, "clarity", "Clarity (Độ trong cục bộ)", -5.0, 5.0, 0.0, "%+.1f", v -> settings.clarity = v);
        addSettingSlider(p, "sharpness", "Sharpness (Độ sắc nét)", -4.0, 4.0, 0.0, "%+.1f", v -> settings.sharpness = v);
        addSettingSlider(p, "nr", "Noise Reduction (Khử nhiễu)", -4.0, 4.0, -4.0, "%+.1f", v -> settings.nr = v);
        addSettingSlider(p, "grain", "Grain Amount (Hạt phim)", 0.0, 30.0, 0.0, "%.0f", v -> settings.grain = v);
        addSettingSlider(p, "grainSize", "Grain Size (Cỡ hạt)", 1.0, 2.0, 1.0, "%.1f", v -> settings.grainSize = v);
        addSettingSlider(p, "lutAmount", "3D LUT Intensity (%)", 0.0, 100.0, 100.0, "%.0f%%", v -> settings.lutAmount = v);

        return p;
    }

    private JPanel buildLightCraftCurveTab() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(DesktopWidgets.PS_PANEL);
        p.setBorder(BorderFactory.createEmptyBorder(8, 12, 14, 12));

        addAccordionHeader(p, "•  ĐƯỜNG CONG TONE CURVE (PARAMETRIC & POINT)");

        JPanel chRow = new JPanel(new GridLayout(1, 4, 4, 0));
        chRow.setOpaque(false);
        chRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        chRow.setMaximumSize(new Dimension(330, 26));
        String[] chNames = {"RGB", "Red", "Green", "Blue"};
        DesktopWidgets.SpectrumButton[] chBtns = new DesktopWidgets.SpectrumButton[4];
        for (int i = 0; i < 4; i++) {
            final int chIdx = i;
            chBtns[i] = new DesktopWidgets.SpectrumButton(chNames[i], false, true);
            if (i == 0) chBtns[i].setActiveState(true);
            chBtns[i].addActionListener(e -> {
                for (int k = 0; k < 4; k++) chBtns[k].setActiveState(k == chIdx);
                toneCurveWidget.setActiveChannel(chIdx);
            });
            chRow.add(chBtns[i]);
        }
        p.add(chRow);
        p.add(Box.createVerticalStrut(6));

        toneCurveWidget = new DesktopWidgets.ToneCurveWidget(lightCraft, this::scheduleRender);
        p.add(toneCurveWidget);
        p.add(Box.createVerticalStrut(8));

        addAccordionHeader(p, "•  PARAMETRIC REGION SLIDERS");
        p.add(new DesktopWidgets.SpectrumSlider("Highlights (Đỉnh sáng)", -100, 100, 0, "%+.0f", v -> {
            lightCraft.paramHighlights = (float) v;
            toneCurveWidget.repaint();
            scheduleRender();
        }));
        p.add(new DesktopWidgets.SpectrumSlider("Lights (Vùng sáng vừa)", -100, 100, 0, "%+.0f", v -> {
            lightCraft.paramLights = (float) v;
            toneCurveWidget.repaint();
            scheduleRender();
        }));
        p.add(new DesktopWidgets.SpectrumSlider("Darks (Vùng trầm)", -100, 100, 0, "%+.0f", v -> {
            lightCraft.paramDarks = (float) v;
            toneCurveWidget.repaint();
            scheduleRender();
        }));
        p.add(new DesktopWidgets.SpectrumSlider("Shadows (Vùng tối sâu)", -100, 100, 0, "%+.0f", v -> {
            lightCraft.paramShadows = (float) v;
            toneCurveWidget.repaint();
            scheduleRender();
        }));

        p.add(Box.createVerticalStrut(8));
        addAccordionHeader(p, "•  12 PRESET PHÒNG TỐI LIGHTCRAFT");
        JPanel presetGrid = new JPanel(new GridLayout(0, 2, 5, 5));
        presetGrid.setOpaque(false);
        presetGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        presetGrid.setMaximumSize(new Dimension(330, 190));
        for (LightCraftEngine.BuiltinPreset preset : LightCraftEngine.PRESETS) {
            JButton btn = DesktopWidgets.createCompactButton(preset.name, false);
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
        return p;
    }

    private JPanel buildLightCraftGradingTab() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(DesktopWidgets.PS_PANEL);
        p.setBorder(BorderFactory.createEmptyBorder(8, 12, 14, 12));

        addAccordionHeader(p, "•  3-WAY COLOR GRADING WHEELS (OKLAB)");
        JPanel wheelsRow = new JPanel(new GridLayout(1, 3, 4, 0));
        wheelsRow.setOpaque(false);
        wheelsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        wheelsRow.setMaximumSize(new Dimension(330, 122));
        shadowsWheelWidget = new DesktopWidgets.ColorWheelWidget("Shadows", lightCraft.shadowsWheel, this::scheduleRender);
        midtonesWheelWidget = new DesktopWidgets.ColorWheelWidget("Midtones", lightCraft.midtonesWheel, this::scheduleRender);
        highlightsWheelWidget = new DesktopWidgets.ColorWheelWidget("Highlights", lightCraft.highlightsWheel, this::scheduleRender);
        wheelsRow.add(shadowsWheelWidget);
        wheelsRow.add(midtonesWheelWidget);
        wheelsRow.add(highlightsWheelWidget);
        p.add(wheelsRow);
        p.add(Box.createVerticalStrut(6));

        p.add(new DesktopWidgets.SpectrumSlider("Shadows Luminance", -100, 100, 0, "%+.0f", v -> {
            lightCraft.shadowsWheel.lum = (float) v;
            scheduleRender();
        }));
        p.add(new DesktopWidgets.SpectrumSlider("Midtones Luminance", -100, 100, 0, "%+.0f", v -> {
            lightCraft.midtonesWheel.lum = (float) v;
            scheduleRender();
        }));
        p.add(new DesktopWidgets.SpectrumSlider("Highlights Luminance", -100, 100, 0, "%+.0f", v -> {
            lightCraft.highlightsWheel.lum = (float) v;
            scheduleRender();
        }));
        p.add(new DesktopWidgets.SpectrumSlider("Blending (Độ hòa trộn)", 0, 100, 50, "%.0f%%", v -> {
            lightCraft.gradingBlending = (float) v;
            scheduleRender();
        }));
        p.add(new DesktopWidgets.SpectrumSlider("Balance (Cân bằng vùng)", -100, 100, 0, "%+.0f", v -> {
            lightCraft.gradingBalance = (float) v;
            scheduleRender();
        }));

        p.add(Box.createVerticalStrut(8));
        addAccordionHeader(p, "•  COLOR MIXER 8 KÊNH (8-BAND OKLCH HSL)");

        JPanel bandButtons = new JPanel(new GridLayout(2, 4, 4, 4));
        bandButtons.setOpaque(false);
        bandButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
        bandButtons.setMaximumSize(new Dimension(330, 56));
        for (int i = 0; i < 8; i++) {
            final int bandIdx = i;
            mixerBandBtns[i] = new DesktopWidgets.SpectrumButton(LightCraftEngine.MIXER_BAND_NAMES[i], false, true);
            mixerBandBtns[i].setForeground(new Color(LightCraftEngine.MIXER_BAND_COLORS[i]));
            if (i == 0) mixerBandBtns[i].setActiveState(true);
            mixerBandBtns[i].addActionListener(e -> {
                selectedMixerBand = bandIdx;
                for (int k = 0; k < 8; k++) mixerBandBtns[k].setActiveState(k == bandIdx);
                syncingSliders.set(true);
                try {
                    mixerHueSlider.setValue(lightCraft.mixerHue[selectedMixerBand], false);
                    mixerSatSlider.setValue(lightCraft.mixerSat[selectedMixerBand], false);
                    mixerLumSlider.setValue(lightCraft.mixerLum[selectedMixerBand], false);
                } finally {
                    syncingSliders.set(false);
                }
            });
            bandButtons.add(mixerBandBtns[i]);
        }
        p.add(bandButtons);
        p.add(Box.createVerticalStrut(6));

        mixerHueSlider = new DesktopWidgets.SpectrumSlider("Hue Shift (Đổi sắc độ)", -100, 100, 0, "%+.0f", v -> {
            if (!syncingSliders.get()) {
                lightCraft.mixerHue[selectedMixerBand] = (float) v;
                scheduleRender();
            }
        });
        mixerSatSlider = new DesktopWidgets.SpectrumSlider("Saturation (Độ bão hòa)", -100, 100, 0, "%+.0f", v -> {
            if (!syncingSliders.get()) {
                lightCraft.mixerSat[selectedMixerBand] = (float) v;
                scheduleRender();
            }
        });
        mixerLumSlider = new DesktopWidgets.SpectrumSlider("Luminance (Độ sáng kênh)", -100, 100, 0, "%+.0f", v -> {
            if (!syncingSliders.get()) {
                lightCraft.mixerLum[selectedMixerBand] = (float) v;
                scheduleRender();
            }
        });
        p.add(mixerHueSlider);
        p.add(mixerSatSlider);
        p.add(mixerLumSlider);

        return p;
    }

    private JPanel buildMaskAndFrameTab() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(DesktopWidgets.PS_PANEL);
        p.setBorder(BorderFactory.createEmptyBorder(8, 12, 14, 12));

        addAccordionHeader(p, "•  MẶT NẠ CỤC BỘ AI (LOCAL MASKING)");
        JPanel maskGrid = new JPanel(new GridLayout(0, 2, 5, 5));
        maskGrid.setOpaque(false);
        maskGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        maskGrid.setMaximumSize(new Dimension(330, 88));

        LightCraftEngine.MaskMode[] modes = LightCraftEngine.MaskMode.values();
        DesktopWidgets.SpectrumButton[] maskBtns = new DesktopWidgets.SpectrumButton[modes.length];
        for (int i = 0; i < modes.length; i++) {
            final int idx = i;
            maskBtns[i] = new DesktopWidgets.SpectrumButton(modes[i].label, false, true);
            if (i == 0) maskBtns[i].setActiveState(true);
            maskBtns[i].addActionListener(e -> {
                for (int k = 0; k < modes.length; k++) maskBtns[k].setActiveState(k == idx);
                lightCraft.maskMode = modes[idx];
                scheduleRender();
            });
            maskGrid.add(maskBtns[i]);
        }
        p.add(maskGrid);
        p.add(Box.createVerticalStrut(6));

        JCheckBox showOverlayCb = new JCheckBox("Hiển thị lớp phủ Mask màu đỏ (Overlay)");
        showOverlayCb.setOpaque(false);
        showOverlayCb.setFont(DesktopWidgets.FONT_UI);
        showOverlayCb.setForeground(DesktopWidgets.TEXT_PRIMARY);
        showOverlayCb.setAlignmentX(Component.LEFT_ALIGNMENT);
        showOverlayCb.addActionListener(e -> {
            lightCraft.showMaskOverlay = showOverlayCb.isSelected();
            scheduleRender();
        });
        p.add(showOverlayCb);

        JCheckBox invertMaskCb = new JCheckBox("Đảo ngược vùng chọn Mask (Invert)");
        invertMaskCb.setOpaque(false);
        invertMaskCb.setFont(DesktopWidgets.FONT_UI);
        invertMaskCb.setForeground(DesktopWidgets.TEXT_PRIMARY);
        invertMaskCb.setAlignmentX(Component.LEFT_ALIGNMENT);
        invertMaskCb.addActionListener(e -> {
            lightCraft.maskInvert = invertMaskCb.isSelected();
            scheduleRender();
        });
        p.add(invertMaskCb);
        p.add(Box.createVerticalStrut(6));

        p.add(new DesktopWidgets.SpectrumSlider("Mask Exposure (EV)", -2.0, 2.0, 0.0, "%+.2f EV", v -> {
            lightCraft.maskExposure = (float) v;
            scheduleRender();
        }));
        p.add(new DesktopWidgets.SpectrumSlider("Mask Temperature", -100.0, 100.0, 0.0, "%+.0f", v -> {
            lightCraft.maskTemp = (float) v;
            scheduleRender();
        }));
        p.add(new DesktopWidgets.SpectrumSlider("Mask Clarity", -100.0, 100.0, 0.0, "%+.0f", v -> {
            lightCraft.maskClarity = (float) v;
            scheduleRender();
        }));
        p.add(new DesktopWidgets.SpectrumSlider("Mask Saturation", -100.0, 100.0, 0.0, "%+.0f", v -> {
            lightCraft.maskSaturation = (float) v;
            scheduleRender();
        }));

        p.add(Box.createVerticalStrut(10));
        addAccordionHeader(p, "•  KHUNG TRIỂN LÃM FUJIFILM EXIF (FRAME)");
        String[] frameNames = {"Không khung", "Khung Trắng EXIF", "Khung Đen Cinema", "Polaroid Cổ điển"};
        JPanel frameGrid = new JPanel(new GridLayout(2, 2, 5, 5));
        frameGrid.setOpaque(false);
        frameGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        frameGrid.setMaximumSize(new Dimension(330, 58));
        DesktopWidgets.SpectrumButton[] frameBtns = new DesktopWidgets.SpectrumButton[4];
        for (int i = 0; i < 4; i++) {
            final int fIdx = i;
            frameBtns[i] = new DesktopWidgets.SpectrumButton(frameNames[i], false, true);
            if (i == 0) frameBtns[i].setActiveState(true);
            frameBtns[i].addActionListener(e -> {
                for (int k = 0; k < 4; k++) frameBtns[k].setActiveState(k == fIdx);
                canvasPanel.setFrameMode(fIdx);
            });
            frameGrid.add(frameBtns[i]);
        }
        p.add(frameGrid);
        return p;
    }

    private void addAccordionHeader(JPanel parent, String text) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(0x2C, 0x2C, 0x2C));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, DesktopWidgets.PS_BORDER),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(340, 24));

        JLabel lbl = new JLabel(text);
        lbl.setFont(DesktopWidgets.FONT_SECTION);
        lbl.setForeground(DesktopWidgets.TEXT_PRIMARY);
        header.add(lbl, BorderLayout.WEST);

        parent.add(header);
        parent.add(Box.createVerticalStrut(4));
    }

    private DesktopWidgets.SpectrumSlider addSettingSlider(
            JPanel parent, String key, String label, double min, double max, double defVal, String fmt,
            DesktopWidgets.SpectrumSlider.ValueListener consumer) {
        DesktopWidgets.SpectrumSlider slider = new DesktopWidgets.SpectrumSlider(label, min, max, defVal, fmt, v -> {
            if (!syncingSliders.get()) {
                consumer.onValueChanged(v);
                scheduleRender();
            }
        });
        parent.add(slider);
        settingSliders.put(key, slider);
        return slider;
    }

    // =========================================================================
    // 5. THANH TRẠNG THÁI DƯỚI CÙNG CHUẨN PHOTOSHOP CC 2025
    // =========================================================================
    private JComponent buildPhotoshopStatusBar() {
        JPanel status = new JPanel(new BorderLayout(10, 0));
        status.setBackground(new Color(0x1F, 0x1F, 0x1F));
        status.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, DesktopWidgets.PS_DIVIDER),
                BorderFactory.createEmptyBorder(3, 10, 3, 10)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);

        zoomStatusLabel = new JLabel(" 100% ");
        zoomStatusLabel.setFont(DesktopWidgets.FONT_MONO);
        zoomStatusLabel.setForeground(DesktopWidgets.TEXT_PRIMARY);
        zoomStatusLabel.setOpaque(true);
        zoomStatusLabel.setBackground(new Color(0x14, 0x14, 0x14));
        zoomStatusLabel.setBorder(BorderFactory.createLineBorder(DesktopWidgets.PS_BORDER, 1));
        left.add(zoomStatusLabel);

        statusLabel = new JLabel("Doc: sRGB IEC61966-2.1 (8-bit)  ·  392 Fuji X Weekly Recipes  ·  LightCraft OkLab Pipeline");
        statusLabel.setFont(DesktopWidgets.FONT_SMALL);
        statusLabel.setForeground(DesktopWidgets.TEXT_SECONDARY);
        left.add(statusLabel);

        JLabel right = new JLabel("Nháy đúp vào thanh trượt bất kỳ để Reset về 0  ·  Cuộn chuột: Zoom  ·  Kéo-thả ảnh/LUT trực tiếp");
        right.setFont(DesktopWidgets.FONT_SMALL);
        right.setForeground(DesktopWidgets.TEXT_MUTED);

        status.add(left, BorderLayout.WEST);
        status.add(right, BorderLayout.EAST);
        return status;
    }

    private void setStatus(String text) {
        if (statusLabel != null) {
            statusLabel.setText(text);
        }
    }

    // =========================================================================
    // NẠP ẢNH, ÁP DỤNG CÔNG THỨC & KẾT XUẤT ĐA LUỒNG
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
                canvasPanel.setDocumentName(chosen.name.replace(' ', '_') + ".RAF");
                setSourceImage(img, "Ảnh mẫu FujiXWeekly · " + chosen.name);
                applyRecipe(chosen, false);
                return;
            }
        }
        BufferedImage fallback = createFallbackTestChart(1280, 854);
        setSourceImage(fallback, "Studio_Sample.RAF");
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
        setStatus("Đang mở: " + label + " (" + fullSourceImage.getWidth() + "x" + fullSourceImage.getHeight() + " px · 8-bit sRGB)");
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
                    canvasPanel.setDocumentName(r.name.replace(' ', '_') + ".RAF");
                    setSourceImage(sampleImg, "Ảnh mẫu FujiXWeekly · " + r.name);
                }
            });
        } else {
            scheduleRender();
            setStatus("Preset: " + r.name + "  ·  Film Simulation: " + r.film + "  ·  Cảm biến: " + r.sensors);
        }
    }

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
        setStatus("Film Simulation: " + filmName);
    }

    private void syncSlidersFromState() {
        syncingSliders.set(true);
        try {
            if (settingSliders.containsKey("exposure")) settingSliders.get("exposure").setValue(settings.exposure, false);
            if (settingSliders.containsKey("highlight")) settingSliders.get("highlight").setValue(settings.highlight, false);
            if (settingSliders.containsKey("shadow")) settingSliders.get("shadow").setValue(settings.shadow, false);
            if (settingSliders.containsKey("color")) settingSliders.get("color").setValue(settings.color, false);
            if (settingSliders.containsKey("temperature")) settingSliders.get("temperature").setValue(settings.temperature, false);
            if (settingSliders.containsKey("red")) settingSliders.get("red").setValue(settings.red, false);
            if (settingSliders.containsKey("blue")) settingSliders.get("blue").setValue(settings.blue, false);
            if (settingSliders.containsKey("colorChrome")) settingSliders.get("colorChrome").setValue(settings.colorChrome, false);
            if (settingSliders.containsKey("chromeBlue")) settingSliders.get("chromeBlue").setValue(settings.chromeBlue, false);
            if (settingSliders.containsKey("sharpness")) settingSliders.get("sharpness").setValue(settings.sharpness, false);
            if (settingSliders.containsKey("clarity")) settingSliders.get("clarity").setValue(settings.clarity, false);
            if (settingSliders.containsKey("nr")) settingSliders.get("nr").setValue(settings.nr, false);
            if (settingSliders.containsKey("grain")) settingSliders.get("grain").setValue(settings.grain, false);
            if (settingSliders.containsKey("grainSize")) settingSliders.get("grainSize").setValue(settings.grainSize, false);
            if (settingSliders.containsKey("lutAmount")) settingSliders.get("lutAmount").setValue(settings.lutAmount, false);

            int drIdx = settings.dr == 400 ? 2 : (settings.dr == 200 ? 1 : 0);
            for (int k = 0; k < 3; k++) {
                if (drButtons[k] != null) drButtons[k].setActiveState(k == drIdx);
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
                    canvasPanel.setImages(previewSourceImage, out, snapSettings.film, activeRecipeName);
                    histogramPanel.setMetaSummary(String.format(Locale.ROOT,
                            "DR%d  ·  EV %+.1f  ·  %s  ·  %dx%d",
                            snapSettings.dr, snapSettings.exposure, snapSettings.film,
                            fullSourceImage != null ? fullSourceImage.getWidth() : w,
                            fullSourceImage != null ? fullSourceImage.getHeight() : h));
                    histogramPanel.updateFromPixels(step2, w, h);
                });
            } catch (Exception ignored) {
            }
        });
    }

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
            setStatus("Không còn bước Hoàn tác (Undo).");
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
            setStatus("Không còn bước Làm lại (Redo).");
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
        setStatus("Đã đặt lại toàn bộ thông số chỉnh sửa về nguyên bản.");
    }

    private void chooseAndOpenImage() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Mở tệp hình ảnh vào bàn dựng");
        fc.setFileFilter(new FileNameExtensionFilter("Hình ảnh (JPG, PNG, BMP, WEBP)", "jpg", "jpeg", "png", "bmp", "webp"));
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
            canvasPanel.setDocumentName(file.getName());
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
        dlg.getContentPane().setBackground(DesktopWidgets.PS_PANEL);
        dlg.setLayout(new BorderLayout(12, 12));

        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.setBackground(DesktopWidgets.PS_PANEL);
        top.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));

        BufferedImage sample = DesktopWidgets.DesktopFujiSampleLoader.getCachedForRecipe(r);
        if (sample != null) {
            JLabel imgLbl = new JLabel(new ImageIcon(scaleThumbnail(sample, 240, 165)));
            imgLbl.setBorder(BorderFactory.createLineBorder(DesktopWidgets.PS_BORDER, 1));
            top.add(imgLbl, BorderLayout.WEST);
        }

        JTextArea area = new JTextArea(r.description());
        area.setEditable(false);
        area.setFont(DesktopWidgets.FONT_MONO);
        area.setBackground(DesktopWidgets.PS_DARKEST);
        area.setForeground(DesktopWidgets.TEXT_PRIMARY);
        area.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JScrollPane sp = DesktopWidgets.createDarkScrollPane(area);
        sp.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        bottom.setBackground(DesktopWidgets.PS_PANEL);

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

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        String screenshotOut = null;
        int initialTab = 0;
        for (int i = 0; i < args.length; i++) {
            if ("--screenshot".equals(args[i]) && i + 1 < args.length) {
                screenshotOut = args[++i];
            } else if ("--tab".equals(args[i]) && i + 1 < args.length) {
                initialTab = Integer.parseInt(args[++i]);
            }
        }
        final String snapTarget = screenshotOut;
        final int tabToShow = initialTab;

        SwingUtilities.invokeLater(() -> {
            IroDesktopApp app = new IroDesktopApp();
            app.setVisible(true);
            if (!app.recipeListModel.isEmpty()) {
                app.recipeList.setSelectedIndex(0);
            }
            if (tabToShow > 0) {
                app.switchInspectorTab(tabToShow);
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

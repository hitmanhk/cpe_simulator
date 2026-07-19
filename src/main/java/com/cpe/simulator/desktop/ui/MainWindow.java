package com.cpe.simulator.desktop.ui;

import com.cpe.simulator.common.AppConfig;
import com.cpe.simulator.desktop.bridge.EngineController;
import com.cpe.simulator.desktop.bridge.UiEventBridge;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main window: modern top bar (app identity + dynamic-island status capsule +
 * engine controls + theme toggle), glassmorphic icon sidebar, and an
 * animated central view stack.
 */
public class MainWindow {

    /** Implemented by DesktopApp to switch between light and dark themes. */
    public interface ThemeController {
        void toggle();
        boolean isDark();
    }

    private final BorderPane root = new BorderPane();
    private final EngineController engineController = new EngineController();
    private final UiEventBridge eventBridge = new UiEventBridge();
    private final ThemeController themeController;

    private final StatusIsland statusIsland = new StatusIsland();
    private final Button startButton = new Button("启动模拟器");
    private final Button stopButton = new Button("停止");
    private final Button themeButton = new Button();

    private final StackPane viewStack = new StackPane();
    private final List<AbstractView> views;
    private final SearchBar searchBar = new SearchBar();
    private AbstractView currentView;
    private final java.util.Map<AbstractView, String> viewNames = new java.util.LinkedHashMap<>();

    private DashboardView dashboardView;
    private DevicesView devicesView;
    private MonitorView monitorView;
    private LogsView logsView;
    private ProtocolView protocolView;
    private ConfigView configView;

    private final AtomicBoolean exitStopInProgress = new AtomicBoolean(false);

    public MainWindow(ThemeController themeController) {
        this.themeController = themeController;
        root.getStyleClass().add("main-root");

        dashboardView = new DashboardView(engineController, eventBridge);
        devicesView = new DevicesView(engineController, eventBridge, this);
        monitorView = new MonitorView(engineController, eventBridge);
        logsView = new LogsView(engineController, eventBridge);
        protocolView = new ProtocolView(engineController, eventBridge);
        configView = new ConfigView(engineController, eventBridge);
        views = List.of(dashboardView, devicesView, monitorView, logsView, protocolView, configView);
        dashboardView.setSummaryListener(statusIsland::setSummary);
        viewNames.put(dashboardView, "仪表盘");
        viewNames.put(devicesView, "设备管理");
        viewNames.put(monitorView, "消息监视");
        viewNames.put(logsView, "日志");
        viewNames.put(protocolView, "协议指南");
        viewNames.put(configView, "配置");
        searchBar.setSearchHandler(this::handleSearch);

        root.setTop(buildTopBar());
        root.setLeft(buildSidebar());
        root.setCenter(viewStack);
        root.setBottom(searchBar);
        BorderPane.setMargin(viewStack, new Insets(4, 10, 10, 4));

        showView(dashboardView);
        wireEngineState();
        updateControls(EngineController.EngineState.STOPPED);
        eventBridge.start();
        devicesView.startPolling(); // 设备池常驻刷新（引擎未启动也可新增设备）
    }

    public BorderPane getRoot() {
        return root;
    }

    public EngineController getEngineController() {
        return engineController;
    }

    /** 把配置页当前表单同步为控制器待启动配置（新增设备/厂商前调用）。 */
    public void syncPendingConfig() {
        try {
            engineController.setPendingConfig(configView.buildConfig());
        } catch (IllegalArgumentException ignored) {
            // 表单暂未完成编辑时保持上一份配置
        }
    }

    public UiEventBridge getEventBridge() {
        return eventBridge;
    }

    /** Opens the devices view, optionally focused on a specific device. */
    public void showDeviceDetail(String deviceId) {
        showView(devicesView);
        devicesView.focusDevice(deviceId);
    }

    // ---------------- 顶部栏：品牌 + 灵动岛 + 控制 ----------------

    private HBox buildTopBar() {
        Node brand = buildBrand();

        startButton.getStyleClass().add("accent");
        startButton.setGraphic(new FontIcon(Feather.PLAY));
        startButton.setTooltip(new javafx.scene.control.Tooltip(
                "启动基础设施：开启回连监听与 XMPP 引擎（不自动上线设备）。"
                        + "设备请在设备管理页勾选后点「启动设备」"));
        startButton.setOnAction(e -> startEngine());
        stopButton.getStyleClass().add("danger");
        stopButton.setGraphic(new FontIcon(Feather.SQUARE));
        stopButton.setOnAction(e -> engineController.stop());
        stopButton.setDisable(true);

        updateThemeButtonIcon();
        themeButton.getStyleClass().add("button-icon");
        themeButton.setOnAction(e -> {
            themeController.toggle();
            updateThemeButtonIcon();
        });

        Region leftSpacer = new Region();
        Region rightSpacer = new Region();
        HBox.setHgrow(leftSpacer, Priority.ALWAYS);
        HBox.setHgrow(rightSpacer, Priority.ALWAYS);

        HBox bar = new HBox(12, brand, leftSpacer, statusIsland, rightSpacer,
                startButton, stopButton, themeButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("top-bar");
        return bar;
    }

    private Node buildBrand() {
        Label title = new Label("ACS 设备模拟器");
        title.getStyleClass().add("app-title");
        HBox brand = new HBox(10);
        brand.setAlignment(Pos.CENTER_LEFT);
        Image logo = AppIcons.load(32);
        if (logo != null) {
            brand.getChildren().add(new ImageView(logo));
        }
        brand.getChildren().add(title);
        return brand;
    }

    private void updateThemeButtonIcon() {
        themeButton.setGraphic(new FontIcon(themeController.isDark() ? Feather.SUN : Feather.MOON));
    }

    // ---------------- 侧边导航 ----------------

    private VBox buildSidebar() {
        ToggleGroup group = new ToggleGroup();
        VBox nav = new VBox(4);
        nav.getStyleClass().add("sidebar");
        nav.setPrefWidth(168);

        nav.getChildren().addAll(
                navButton("仪表盘", Feather.ACTIVITY, dashboardView, group, true),
                navButton("设备管理", Feather.SERVER, devicesView, group, false),
                navButton("消息监视", Feather.RADIO, monitorView, group, false),
                navButton("日志", Feather.TERMINAL, logsView, group, false),
                navButton("协议指南", Feather.BOOK_OPEN, protocolView, group, false),
                navButton("配置", Feather.SETTINGS, configView, group, false));
        return nav;
    }

    private ToggleButton navButton(String text, Feather icon, AbstractView view,
                                   ToggleGroup group, boolean selected) {
        ToggleButton button = new ToggleButton(text);
        button.setGraphic(new FontIcon(icon));
        button.setToggleGroup(group);
        button.setSelected(selected);
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("nav-button");
        button.setOnAction(e -> {
            if (!button.isSelected()) {
                button.setSelected(true); // 始终保持一个选中
            }
            showView(view);
        });
        return button;
    }

    // ---------------- 页面切换动画 ----------------

    private void showView(AbstractView view) {
        if (!viewStack.getChildren().isEmpty()
                && viewStack.getChildren().get(0) == view) {
            return;
        }
        currentView = view;
        viewStack.getChildren().setAll(view);
        view.setOpacity(0);
        view.setTranslateY(14);
        FadeTransition fade = new FadeTransition(Duration.millis(240), view);
        fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(240), view);
        slide.setToY(0);
        new ParallelTransition(fade, slide).play();
        if (searchBar.isSearchVisible()) {
            searchBar.refire();
        }
    }

    // ---------------- 全局搜索（Ctrl+F） ----------------

    /** 安装场景级快捷键：Ctrl+F 打开搜索条，Esc 关闭。由 DesktopApp 调用。 */
    public void installShortcuts(javafx.scene.Scene scene) {
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == javafx.scene.input.KeyCode.F) {
                if (searchBar.isSearchVisible()) {
                    searchBar.hide();
                } else {
                    searchBar.showAndFocus();
                }
                event.consume();
            } else if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE
                    && searchBar.isSearchVisible()) {
                searchBar.hide();
                event.consume();
            }
        });
    }

    private void handleSearch(String query, SearchOptions options, boolean crossPage) {
        if (query == null || query.isBlank()) {
            views.forEach(v -> {
                if (v instanceof ViewSearcher searcher) {
                    searcher.clearSearch();
                }
            });
            searchBar.setCountText("");
            searchBar.getCrossPageResults().getChildren().clear();
            return;
        }
        if (crossPage) {
            searchAcrossPages(query, options);
        } else {
            // 单页搜索：仅作用于当前页，清理其他页的搜索状态
            searchBar.getCrossPageResults().getChildren().clear();
            int count = 0;
            for (AbstractView view : views) {
                if (!(view instanceof ViewSearcher searcher)) {
                    continue;
                }
                if (view == currentView) {
                    count = searcher.applySearch(query, options);
                } else {
                    searcher.clearSearch();
                }
            }
            searchBar.setCountText(currentView instanceof ViewSearcher
                    ? count + " 项匹配" : "当前页面不支持搜索");
        }
    }

    /** 跨页面搜索：所有支持搜索的页面同时过滤，结果区显示各页命中数并可点击跳转。 */
    private void searchAcrossPages(String query, SearchOptions options) {
        HBox results = searchBar.getCrossPageResults();
        results.getChildren().clear();
        int total = 0;
        for (AbstractView view : views) {
            if (!(view instanceof ViewSearcher searcher)) {
                continue;
            }
            int count = searcher.applySearch(query, options);
            total += count;
            if (count > 0) {
                Label hit = new Label(viewNames.get(view) + " " + count);
                hit.getStyleClass().add("search-hit");
                hit.setOnMouseClicked(e -> showView(view));
                results.getChildren().add(hit);
            }
        }
        searchBar.setCountText("共 " + total + " 项");
    }

    // ---------------- 引擎启停与状态联动 ----------------

    private void startEngine() {
        AppConfig config;
        try {
            config = configView.buildConfig();
        } catch (IllegalArgumentException e) {
            new Alert(Alert.AlertType.WARNING, "配置无效：" + e.getMessage()).showAndWait();
            return;
        }
        String error = EngineController.validate(config);
        if (error != null) {
            new Alert(Alert.AlertType.WARNING, "配置校验失败：" + error).showAndWait();
            return;
        }
        engineController.start(config);
    }

    private void wireEngineState() {
        engineController.addStateListener((state, error) ->
                Platform.runLater(() -> onEngineState(state, error)));
    }

    private void onEngineState(EngineController.EngineState state, String error) {
        statusIsland.setState(state);
        updateControls(state);
        if (state == EngineController.EngineState.RUNNING) {
            views.forEach(AbstractView::onEngineStarted);
        } else if (state == EngineController.EngineState.STOPPED) {
            views.forEach(AbstractView::onEngineStopped);
        }
        if (state == EngineController.EngineState.FAILED && error != null) {
            new Alert(Alert.AlertType.ERROR, "引擎启动失败：\n" + error).show();
        }
        if (exitStopInProgress.get() && state == EngineController.EngineState.STOPPED) {
            Platform.exit();
        }
    }

    private void updateControls(EngineController.EngineState state) {
        switch (state) {
            case STOPPED -> {
                startButton.setDisable(false);
                stopButton.setDisable(true);
                configView.setEditingEnabled(true);
            }
            case STARTING -> {
                startButton.setDisable(true);
                stopButton.setDisable(true);
                configView.setEditingEnabled(false);
            }
            case RUNNING -> {
                startButton.setDisable(true);
                stopButton.setDisable(false);
                configView.setEditingEnabled(false);
            }
            case STOPPING -> {
                startButton.setDisable(true);
                stopButton.setDisable(true);
            }
            case FAILED -> {
                startButton.setDisable(false);
                stopButton.setDisable(true);
                configView.setEditingEnabled(true);
            }
        }
    }

    /** Stops timers and event subscriptions. Idempotent. */
    public void dispose() {
        eventBridge.stop();
        views.forEach(AbstractView::dispose);
    }

    /**
     * Called when the window closes. If the engine is active, initiates an
     * asynchronous stop and defers platform exit until STOPPED.
     *
     * @return true if exit is deferred waiting for engine stop
     */
    public boolean requestEngineStopForExit() {
        EngineController.EngineState state = engineController.getState();
        switch (state) {
            case RUNNING, FAILED -> {
                exitStopInProgress.set(true);
                engineController.stop();
                scheduleExitFallback();
                return true;
            }
            case STARTING, STOPPING -> {
                exitStopInProgress.set(true);
                scheduleExitFallback();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void scheduleExitFallback() {
        Thread exitFallback = new Thread(() -> {
            try {
                Thread.sleep(45_000);
            } catch (InterruptedException ignored) {
            }
            Platform.exit();
        });
        exitFallback.setDaemon(true);
        exitFallback.start();
    }
}

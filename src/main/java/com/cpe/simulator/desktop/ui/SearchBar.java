package com.cpe.simulator.desktop.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

/**
 * Global search bar toggled with Ctrl+F. Emits search requests with the
 * current query, options and cross-page flag; displays per-scope results.
 */
public class SearchBar extends HBox {

    public interface SearchHandler {
        void onSearch(String query, SearchOptions options, boolean crossPage);
    }

    private final TextField queryField = new TextField();
    private final ToggleButton caseButton = new ToggleButton("Aa");
    private final ToggleButton wordButton = new ToggleButton("全字");
    private final ToggleButton crossPageButton = new ToggleButton("跨页面");
    private final Label countLabel = new Label("");
    private final HBox crossPageResults = new HBox(10);

    private SearchHandler handler;

    public SearchBar() {
        super(8);
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(8, 16, 8, 16));
        getStyleClass().add("search-bar");

        queryField.setPromptText("搜索当前页面…（Ctrl+F）");
        queryField.setPrefWidth(280);

        caseButton.setTooltip(new Tooltip("区分大小写"));
        caseButton.getStyleClass().add("search-toggle");
        wordButton.setTooltip(new Tooltip("全字匹配"));
        wordButton.getStyleClass().add("search-toggle");
        crossPageButton.setTooltip(new Tooltip("在所有页面中搜索（默认关闭）"));
        crossPageButton.getStyleClass().add("search-toggle");

        countLabel.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 12px;");
        crossPageResults.setAlignment(Pos.CENTER_LEFT);

        Button closeButton = new Button("✕");
        closeButton.getStyleClass().add("search-close");
        closeButton.setTooltip(new Tooltip("关闭搜索（Esc）"));
        closeButton.setOnAction(e -> hide());

        getChildren().addAll(new Label("🔍"), queryField, caseButton, wordButton,
                crossPageButton, countLabel, crossPageResults, closeButton);

        queryField.textProperty().addListener((obs, o, n) -> fireSearch());
        caseButton.selectedProperty().addListener((obs, o, n) -> fireSearch());
        wordButton.selectedProperty().addListener((obs, o, n) -> fireSearch());
        crossPageButton.selectedProperty().addListener((obs, o, n) -> fireSearch());

        setVisible(false);
        setManaged(false);
    }

    public void setSearchHandler(SearchHandler handler) {
        this.handler = handler;
    }

    public void showAndFocus() {
        setVisible(true);
        setManaged(true);
        queryField.requestFocus();
        queryField.selectAll();
        fireSearch();
    }

    public void hide() {
        setVisible(false);
        setManaged(false);
        queryField.clear();
        countLabel.setText("");
        crossPageResults.getChildren().clear();
        if (handler != null) {
            handler.onSearch("", SearchOptions.DEFAULT, false);
        }
    }

    public boolean isSearchVisible() {
        return isVisible();
    }

    /** 重新以当前输入触发一次搜索（页面切换后保持过滤状态）。 */
    public void refire() {
        fireSearch();
    }

    public void setCountText(String text) {
        countLabel.setText(text);
    }

    /** 跨页面结果区：由 MainWindow 填充「页面名(N)」可点击标签。 */
    public HBox getCrossPageResults() {
        return crossPageResults;
    }

    private void fireSearch() {
        if (handler != null) {
            handler.onSearch(queryField.getText(),
                    new SearchOptions(caseButton.isSelected(), wordButton.isSelected()),
                    crossPageButton.isSelected());
        }
    }
}

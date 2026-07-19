package com.cpe.simulator.desktop.ui;

import com.cpe.simulator.engine.VendorProfile;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

/**
 * Add/edit vendor dialog. Vendor carries Manufacturer, OUI, ProductClass and
 * connection-request credentials; these are single-sourced from the vendor
 * and never repeated per device.
 */
public class VendorDialog extends Dialog<VendorProfile> {

    public VendorDialog(VendorProfile existing) {
        boolean editing = existing != null;
        setTitle(editing ? "编辑厂商" : "新增厂商");
        setHeaderText(editing
                ? "修改厂商参数（仅影响后续新建设备；已有设备的 DeviceId 不变）"
                : "厂商参数将应用于其下全部设备（Manufacturer/OUI/ProductClass 构成 DeviceId）");

        TextField nameField = new TextField(editing ? existing.name() : "");
        nameField.setPromptText("如：华为");
        if (editing) {
            nameField.setDisable(true);
        }
        TextField manufacturerField = new TextField(editing ? existing.manufacturer() : "");
        manufacturerField.setPromptText("如：Huawei");
        TextField ouiField = new TextField(editing ? existing.oui() : "");
        ouiField.setPromptText("6 位十六进制，如 48A9D2");
        TextField productClassField = new TextField(editing ? existing.productClass() : "");
        productClassField.setPromptText("如：HG8245");
        TextField connUserField = new TextField(
                editing && existing.connReqUser() != null ? existing.connReqUser() : "");
        TextField connPassField = new TextField(
                editing && existing.connReqPassword() != null ? existing.connReqPassword() : "");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.addRow(0, new Label("厂商名称 *"), nameField);
        grid.addRow(1, new Label("Manufacturer *"), manufacturerField);
        grid.addRow(2, new Label("OUI *"), ouiField);
        grid.addRow(3, new Label("ProductClass *"), productClassField);
        grid.addRow(4, new Label("回连用户名"), connUserField);
        grid.addRow(5, new Label("回连密码"), connPassField);
        Label hint = new Label("回连凭据留空 = 该厂商设备回连不启用认证（回退全局配置）；"
                + "OUI 与 ProductClass 用于生成设备 DeviceId");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 11px;");
        grid.add(hint, 0, 6, 2, 1);

        getDialogPane().setContent(grid);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().lookupButton(ButtonType.OK).setDisable(!editing);
        if (!editing) {
            nameField.textProperty().addListener((obs, o, n) ->
                    getDialogPane().lookupButton(ButtonType.OK).setDisable(n.isBlank()));
        }

        setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            String name = nameField.getText().trim();
            String manufacturer = manufacturerField.getText().trim();
            String oui = ouiField.getText().trim();
            String productClass = productClassField.getText().trim();
            if (name.isEmpty() || manufacturer.isEmpty() || oui.isEmpty() || productClass.isEmpty()) {
                return null;
            }
            String user = connUserField.getText().trim();
            String pass = connPassField.getText();
            return new VendorProfile(name, manufacturer, oui, productClass,
                    user.isEmpty() ? null : user, pass.isEmpty() ? null : pass);
        });
    }

    /** OUI 格式校验（6 位十六进制）；由调用方在确定后提示。 */
    public static boolean isValidOui(String oui) {
        return oui != null && oui.matches("[0-9A-Fa-f]{6}");
    }
}

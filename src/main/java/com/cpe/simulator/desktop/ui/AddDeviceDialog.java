package com.cpe.simulator.desktop.ui;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Add-device dialog: pick a vendor and a count, optionally override per-device
 * DeviceInfo values (vendor-carried fields — Manufacturer/OUI/ProductClass/
 * connection-request credentials — are intentionally NOT repeated here).
 */
public class AddDeviceDialog extends Dialog<AddDeviceDialog.Request> {

    /** deviceInfoOverrides uses root-relative paths (e.g. DeviceInfo.SoftwareVersion). */
    public record Request(String vendorName, int count, Map<String, String> deviceInfoOverrides) {
    }

    public AddDeviceDialog(List<String> vendorNames, boolean tr181Model) {
        setTitle("新增设备");
        setHeaderText("新建设备仅创建（未启动状态），不会上报；"
                + "需点击「启动设备」后才开始上报事件或 XMPP 建联");

        ComboBox<String> vendorCombo = new ComboBox<>();
        vendorCombo.getItems().setAll(vendorNames);
        if (!vendorNames.isEmpty()) {
            vendorCombo.getSelectionModel().select(0);
        }
        TextField countField = new TextField("10");

        GridPane base = new GridPane();
        base.setHgap(12);
        base.setVgap(10);
        base.setPadding(new Insets(16));
        base.addRow(0, new Label("所属厂商"), vendorCombo);
        base.addRow(1, new Label("数量"), countField);

        GridPane overrides = new GridPane();
        overrides.setHgap(12);
        overrides.setVgap(8);
        overrides.setPadding(new Insets(10));
        TextField swField = new TextField();
        TextField hwField = new TextField();
        TextField addSwField = new TextField();
        TextField addHwField = new TextField();
        TextField provField = new TextField();
        int row = 0;
        overrides.addRow(row++, new Label("软件版本"), swField);
        overrides.addRow(row++, new Label("硬件版本"), hwField);
        if (tr181Model) {
            overrides.addRow(row++, new Label("附加软件版本"), addSwField);
            overrides.addRow(row++, new Label("附加硬件版本"), addHwField);
        }
        overrides.addRow(row, new Label("ProvisioningCode"), provField);

        TitledPane overridePane = new TitledPane("DeviceInfo 覆盖（可选，留空使用模型默认值）", overrides);
        overridePane.setExpanded(false);

        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(8, base, overridePane);
        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        setResultConverter(button -> {
            if (button != ButtonType.OK || vendorCombo.getValue() == null) {
                return null;
            }
            final int count;
            try {
                count = Integer.parseInt(countField.getText().trim());
                if (count < 1 || count > 100_000) {
                    return null;
                }
            } catch (NumberFormatException e) {
                return null;
            }
            Map<String, String> map = new LinkedHashMap<>();
            putIfNotBlank(map, "DeviceInfo.SoftwareVersion", swField.getText());
            putIfNotBlank(map, "DeviceInfo.HardwareVersion", hwField.getText());
            if (tr181Model) {
                putIfNotBlank(map, "DeviceInfo.AdditionalSoftwareVersion", addSwField.getText());
                putIfNotBlank(map, "DeviceInfo.AdditionalHardwareVersion", addHwField.getText());
            }
            putIfNotBlank(map, "DeviceInfo.ProvisioningCode", provField.getText());
            return new Request(vendorCombo.getValue(), count, map);
        });
    }

    private static void putIfNotBlank(Map<String, String> map, String path, String value) {
        if (value != null && !value.isBlank()) {
            map.put(path, value.trim());
        }
    }
}

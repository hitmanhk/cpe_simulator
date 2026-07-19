package com.cpe.simulator.desktop.ui;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Batch DeviceInfo editor: applies non-blank fields to all checked devices
 * (vendor-carried fields excluded — those stay vendor-managed).
 */
public class BatchConfigDialog extends Dialog<Map<String, String>> {

    public BatchConfigDialog(int deviceCount, boolean tr181Model) {
        setTitle("批量配置 DeviceInfo");
        setHeaderText("对勾选的 " + deviceCount + " 台设备写入以下字段（留空的字段跳过）");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));

        TextField swField = new TextField();
        TextField hwField = new TextField();
        TextField addSwField = new TextField();
        TextField addHwField = new TextField();
        TextField provField = new TextField();
        int row = 0;
        grid.addRow(row++, new Label("软件版本"), swField);
        grid.addRow(row++, new Label("硬件版本"), hwField);
        if (tr181Model) {
            grid.addRow(row++, new Label("附加软件版本"), addSwField);
            grid.addRow(row++, new Label("附加硬件版本"), addHwField);
        }
        grid.addRow(row, new Label("ProvisioningCode"), provField);

        getDialogPane().setContent(grid);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        setResultConverter(button -> {
            if (button != ButtonType.OK) {
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
            return map.isEmpty() ? null : map;
        });
    }

    private static void putIfNotBlank(Map<String, String> map, String path, String value) {
        if (value != null && !value.isBlank()) {
            map.put(path, value.trim());
        }
    }
}

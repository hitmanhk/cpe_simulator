package com.cpe.simulator.desktop.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;

/**
 * Hand-drawn protocol sequence diagram (no external libs): lifelines with
 * role-colored headers, directional message arrows with notes, and
 * start/end markers. Read-only; layout is computed once from the flow data.
 */
public class SequenceDiagram extends VBox {

    /** A participant in the flow, e.g. CPE or a server with its protocol role. */
    public record Lifeline(String name, String role, String color) {
    }

    public enum Kind {
        /** Normal message arrow. */
        MESSAGE,
        /** Highlighted arrow marking the beginning of the flow. */
        START,
        /** Highlighted arrow marking the end of the flow. */
        END,
        /** Section divider rendered as a full-width band (no arrow). */
        SECTION
    }

    /** One step: arrow from lifelines[from] to lifelines[to], or a SECTION band. */
    public record FlowMessage(int from, int to, String label, String note, Kind kind) {
        public static FlowMessage section(String title) {
            return new FlowMessage(-1, -1, title, "", Kind.SECTION);
        }
    }

    private static final double LIFELINE_WIDTH = 230;
    private static final double HEADER_HEIGHT = 54;
    private static final double ROW_MESSAGE = 58;
    private static final double ROW_MESSAGE_NOTE = 74;
    private static final double ROW_SECTION = 34;
    private static final double PADDING_X = 30;
    private static final double PADDING_BOTTOM = 26;

    public SequenceDiagram(List<Lifeline> lifelines, List<FlowMessage> messages) {
        setPadding(new Insets(18, 10, 10, 10));
        setSpacing(0);

        double width = PADDING_X * 2 + LIFELINE_WIDTH * lifelines.size();
        // 保护图形最小宽度：窄视口下由外层 ScrollPane 出横向滚动条，
        // 而不是压缩图形导致生命线与消息箭头错位
        setMinWidth(width);
        double[] centers = new double[lifelines.size()];
        for (int i = 0; i < lifelines.size(); i++) {
            centers[i] = PADDING_X + LIFELINE_WIDTH * i + LIFELINE_WIDTH / 2;
        }

        getChildren().add(buildHeader(lifelines, centers, width));

        double totalHeight = HEADER_HEIGHT + PADDING_BOTTOM;
        for (FlowMessage m : messages) {
            totalHeight += m.kind() == Kind.SECTION ? ROW_SECTION
                    : (m.note() == null || m.note().isBlank() ? ROW_MESSAGE : ROW_MESSAGE_NOTE);
        }

        Pane canvas = new Pane();
        canvas.setPrefSize(width, totalHeight);
        canvas.setMinHeight(totalHeight);

        // 生命线虚线
        for (int i = 0; i < lifelines.size(); i++) {
            Line line = new Line(centers[i], 0, centers[i], totalHeight);
            line.getStrokeDashArray().addAll(6.0, 6.0);
            line.setStroke(Color.web("#9aa4b2"));
            line.setStrokeWidth(1.2);
            canvas.getChildren().add(line);
        }

        double y = 10;
        for (FlowMessage m : messages) {
            if (m.kind() == Kind.SECTION) {
                canvas.getChildren().add(buildSection(m.label(), width, y));
                y += ROW_SECTION;
                continue;
            }
            boolean hasNote = m.note() != null && !m.note().isBlank();
            double rowHeight = hasNote ? ROW_MESSAGE_NOTE : ROW_MESSAGE;
            canvas.getChildren().add(buildMessage(m, centers, y, rowHeight));
            y += rowHeight;
        }

        getChildren().add(canvas);
    }

    private HBox buildHeader(List<Lifeline> lifelines, double[] centers, double width) {
        HBox header = new HBox();
        header.setPrefWidth(width);
        for (int i = 0; i < lifelines.size(); i++) {
            Lifeline lifeline = lifelines.get(i);
            Label name = new Label(lifeline.name());
            name.setFont(Font.font("System", FontWeight.BOLD, 13));
            name.setStyle("-fx-text-fill: white;");
            Label role = new Label(lifeline.role());
            role.setStyle("-fx-text-fill: rgba(255,255,255,0.82); -fx-font-size: 11px;");
            VBox box = new VBox(2, name, role);
            box.setAlignment(Pos.CENTER);
            box.setPrefHeight(HEADER_HEIGHT - 12);
            box.setMaxWidth(150);
            box.setStyle("-fx-background-color: " + lifeline.color()
                    + "; -fx-background-radius: 10; -fx-padding: 6 14 6 14;");
            HBox cell = new HBox(box);
            cell.setAlignment(Pos.CENTER);
            cell.setPrefWidth(LIFELINE_WIDTH);
            header.getChildren().add(cell);
        }
        return header;
    }

    private Group buildSection(String title, double width, double y) {
        Group group = new Group();
        javafx.scene.shape.Rectangle band = new javafx.scene.shape.Rectangle(
                PADDING_X - 10, y + 5, width - PADDING_X * 2 + 20, ROW_SECTION - 12);
        band.setArcWidth(8);
        band.setArcHeight(8);
        band.setFill(Color.web("#8b95a5", 0.18));
        Label label = new Label(title);
        label.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 11px; -fx-font-weight: bold;");
        label.setLayoutX(PADDING_X);
        label.setLayoutY(y + 8);
        group.getChildren().addAll(band, label);
        return group;
    }

    private Group buildMessage(FlowMessage m, double[] centers, double y, double rowHeight) {
        Group group = new Group();
        double x1 = centers[m.from()];
        double x2 = centers[m.to()];
        double arrowY = y + (m.note() == null || m.note().isBlank() ? 22 : 26);
        boolean self = m.from() == m.to();

        Color color = switch (m.kind()) {
            case START -> Color.web("#1a7f37");
            case END -> Color.web("#cf222e");
            default -> Color.web("#4a5568");
        };

        if (self) {
            // 自消息：小折线环
            double w = 46;
            javafx.scene.shape.Polyline loop = new javafx.scene.shape.Polyline(
                    x1, arrowY - 8, x1 + w, arrowY - 8, x1 + w, arrowY + 8, x1, arrowY + 8);
            loop.setStroke(color);
            loop.setStrokeWidth(1.6);
            group.getChildren().add(loop);
        } else {
            Line line = new Line(x1, arrowY, x2, arrowY);
            line.setStroke(color);
            line.setStrokeWidth(m.kind() == Kind.MESSAGE ? 1.6 : 2.4);
            group.getChildren().add(line);

            Polygon head = new Polygon();
            double dir = x2 > x1 ? 1 : -1;
            head.getPoints().addAll(
                    x2, arrowY,
                    x2 - dir * 10, arrowY - 5,
                    x2 - dir * 10, arrowY + 5);
            head.setFill(color);
            group.getChildren().add(head);
        }

        // 消息名 + 方向标记
        String direction = directionTag(m);
        Label label = new Label(direction + m.label());
        label.setFont(Font.font("System", FontWeight.BOLD, 12));
        label.setStyle("-fx-text-fill: " + toHex(color) + ";");
        double labelX = self ? x1 + 8 : Math.min(x1, x2) + Math.abs(x2 - x1) / 2 - labelWidth(label) / 2;
        label.setLayoutX(Math.max(PADDING_X - 12, labelX));
        label.setLayoutY(arrowY - 24);
        if (m.note() != null && !m.note().isBlank()) {
            label.setTooltip(new Tooltip(m.note()));
        }
        group.getChildren().add(label);

        // 开始/结束徽标
        if (m.kind() == Kind.START || m.kind() == Kind.END) {
            Label badge = new Label(m.kind() == Kind.START ? "开始" : "结束");
            badge.setStyle("-fx-background-color: " + toHex(color)
                    + "; -fx-text-fill: white; -fx-font-size: 10px; -fx-font-weight: bold;"
                    + "-fx-background-radius: 8; -fx-padding: 2 8 2 8;");
            badge.setLayoutX(self ? x1 + 10 : (x1 + x2) / 2 + 12);
            badge.setLayoutY(arrowY - 10);
            group.getChildren().add(badge);
        }

        // 说明小字
        if (m.note() != null && !m.note().isBlank()) {
            Label note = new Label(m.note());
            note.setStyle("-fx-text-fill: -color-fg-muted; -fx-font-size: 11px;");
            note.setLayoutX(Math.max(PADDING_X - 12, labelX));
            note.setLayoutY(arrowY + 6);
            note.setMaxWidth(Math.abs(x2 - x1) + 140);
            group.getChildren().add(note);
        }
        return group;
    }

    /** 方向标注：根据起终点判断消息走向。 */
    private String directionTag(FlowMessage m) {
        if (m.from() == m.to()) {
            return "⟳ ";
        }
        return m.to() > m.from() ? "" : "";
    }

    private double labelWidth(Label label) {
        return label.getText().length() * 7.5;
    }

    private static String toHex(Color color) {
        return String.format("#%02x%02x%02x",
                (int) (color.getRed() * 255), (int) (color.getGreen() * 255), (int) (color.getBlue() * 255));
    }
}

package com.cpe.simulator.cli;

public class ProgressBar {

    private static final int BAR_WIDTH = 40;

    private String lastStage = "";
    private int lastPercent = -1;

    public void update(String stage, int current, int total) {
        int percent = total > 0 ? (int) ((current * 100L) / total) : 0;
        if (stage.equals(lastStage) && percent == lastPercent) {
            return;
        }
        lastStage = stage;
        lastPercent = percent;

        int filled = (int) ((percent / 100.0) * BAR_WIDTH);
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < BAR_WIDTH; i++) {
            if (i < filled) {
                bar.append('=');
            } else if (i == filled) {
                bar.append('>');
            } else {
                bar.append(' ');
            }
        }
        bar.append("]");

        System.out.printf("\r%-20s %s %3d%% (%d/%d)", stage, bar, percent, current, total);
        if (percent >= 100) {
            System.out.println();
        }
        System.out.flush();
    }

    public void complete(String message) {
        System.out.println("\r" + message);
        System.out.flush();
    }
}

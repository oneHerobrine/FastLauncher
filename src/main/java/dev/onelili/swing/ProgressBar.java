package dev.onelili.swing;

import lombok.NonNull;

import javax.swing.*;
import java.awt.*;

public class ProgressBar {
    private final JFrame frame;
    private final JProgressBar progressBar;
    private final JLabel statusLabel;
    private final JLabel countLabel;
    private final int total;

    public ProgressBar(long totalBytes) {
        total = (int) (totalBytes / 1024 / 1024);
        frame = new JFrame("下载进度");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 150);
        frame.setLayout(new BorderLayout(10, 10));
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        progressBar = new JProgressBar(0, total);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        progressBar.setForeground(new Color(0, 122, 204));
        progressBar.setSize(300, 5);

        statusLabel = new JLabel("下载文件中...", JLabel.CENTER);

        countLabel = new JLabel(String.format("0MB / %dMB 完成", total), JLabel.CENTER);
        countLabel.setForeground(Color.GRAY);

        mainPanel.add(statusLabel, BorderLayout.NORTH);
        mainPanel.add(progressBar, BorderLayout.CENTER);
        mainPanel.add(countLabel, BorderLayout.SOUTH);

        frame.add(mainPanel);
        frame.setVisible(true);
    }

    public void update(long totalBytes) {
        int total = (int) (totalBytes / 1024 / 1024);
        int progress = (int) ((total * 100.0) / this.total);
        progressBar.setValue(total);
        countLabel.setText(String.format("%dMB / %dMB 完成", total, this.total));

        progressBar.setString(String.format("%d%%", progress));
    }

    public void close() {
        SwingUtilities.invokeLater(() -> {
            if (frame != null) {
                frame.dispose();
            }
        });
    }

    public void setStatus(@NonNull String status) {
        statusLabel.setText(status);
    }
}

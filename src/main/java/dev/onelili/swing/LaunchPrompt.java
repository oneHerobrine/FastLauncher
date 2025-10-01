package dev.onelili.swing;

import javax.swing.*;
import java.awt.*;

public class LaunchPrompt {
    private final JFrame frame;

    public LaunchPrompt() {
        frame = new JFrame("启动提示");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(400, 150);
        frame.setLayout(new BorderLayout(10, 10));
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel promptLabel = new JLabel("游戏即将启动!");

        mainPanel.add(promptLabel, BorderLayout.CENTER);

        frame.add(mainPanel);
        frame.setVisible(true);
    }

    public void close() {
        SwingUtilities.invokeLater(() -> {
            if (frame != null) {
                frame.dispose();
            }
        });
    }
}

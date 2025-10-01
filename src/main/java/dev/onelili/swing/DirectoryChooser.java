package dev.onelili.swing;

import lombok.NonNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.util.function.Consumer;

public class DirectoryChooser extends JFrame {
    private final JRadioButton defaultDirRadio;
    private final JRadioButton currentDirRadio;
    private final JRadioButton customDirRadio;
    private final JTextField customDirField;
    private final JButton browseButton;
    private final JButton confirmButton;
    private final Consumer<File> consumer;

    private File dir;

    public DirectoryChooser(@NonNull Consumer<File> consumer) {
        this.consumer = consumer;

        setTitle("选择数据存放目录");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        defaultDirRadio = new JRadioButton("默认目录 (C:\\ProgramData\\)", true);
        currentDirRadio = new JRadioButton("当前目录(" + new File(System.getProperty("user.dir")).getAbsolutePath() + ")");
        customDirRadio = new JRadioButton("自定义目录");

        ButtonGroup group = new ButtonGroup();
        group.add(defaultDirRadio);
        group.add(currentDirRadio);
        group.add(customDirRadio);

        customDirField = new JTextField(25);
        customDirField.setEnabled(false);
        browseButton = new JButton("浏览...");
        browseButton.setEnabled(false);

        confirmButton = new JButton("确定");

        dir = new File("C:/ProgramData/FastLauncher/");

        setLayout(new BorderLayout(10, 10));

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel radioPanel = new JPanel();
        radioPanel.setLayout(new BoxLayout(radioPanel, BoxLayout.Y_AXIS));
        radioPanel.setBorder(BorderFactory.createTitledBorder("选择目录类型"));

        radioPanel.add(defaultDirRadio);
        radioPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        radioPanel.add(currentDirRadio);
        radioPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        radioPanel.add(customDirRadio);

        JPanel customDirPanel = new JPanel(new BorderLayout(5, 0));
        customDirPanel.setBorder(BorderFactory.createTitledBorder("自定义目录"));
        customDirPanel.add(customDirField, BorderLayout.CENTER);
        customDirPanel.add(browseButton, BorderLayout.EAST);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(confirmButton);

        mainPanel.add(radioPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(customDirPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 15)));
        mainPanel.add(buttonPanel);

        add(mainPanel, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(null);

        ActionListener radioListener = ev -> {
            boolean customSelected = customDirRadio.isSelected();
            customDirField.setEnabled(customSelected);
            browseButton.setEnabled(customSelected);

            if (defaultDirRadio.isSelected()) {
                dir = new File("C:/ProgramData/FastLauncher/");
            } else if (currentDirRadio.isSelected()) {
                dir = new File(System.getProperty("user.dir"), "FastLauncher/");
            }
        };

        defaultDirRadio.addActionListener(radioListener);
        currentDirRadio.addActionListener(radioListener);
        customDirRadio.addActionListener(radioListener);

        browseButton.addActionListener(ev -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fileChooser.setDialogTitle("选择目录");

            int result = fileChooser.showOpenDialog(DirectoryChooser.this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedDir = fileChooser.getSelectedFile();
                customDirField.setText(selectedDir.getAbsolutePath());
                dir = selectedDir;
                File fastLauncherDir = new File("C:/ProgramData/FastLauncher/");
                fastLauncherDir.mkdirs();
                File indexFile = new File(fastLauncherDir, "launcher.index");
                try {
                    indexFile.createNewFile();
                    try(FileWriter writer = new FileWriter(indexFile)) {
                        writer.write(dir.getAbsolutePath());
                    }
                } catch(Exception ignored) {}
            }
        });

        confirmButton.addActionListener(ev -> {
            if (customDirRadio.isSelected()) {
                String customPath = customDirField.getText().trim();
                if (customPath.isEmpty()) {
                    JOptionPane.showMessageDialog(DirectoryChooser.this,
                            "请选择自定义目录", "警告", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                dir = new File(customPath);
            }

            consumer.accept(dir);

            dispose();
        });

        setVisible(true);
    }
}

package dev.onelili.swing;

import dev.onelili.util.AccountUtils;
import dev.onelili.util.AuthData;
import fr.litarvan.openauth.model.response.AuthResponse;
import lombok.NonNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.function.Consumer;

public class LoginWindow extends JFrame {
    private final JRadioButton guestRadio;
    private final JRadioButton registerRadio;
    private final JRadioButton loginRadio;
    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final JButton confirmButton;

    private int loginType;

    public LoginWindow(@NonNull Consumer<AuthData> consumer) {

        setTitle("用户登录");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        guestRadio = new JRadioButton("游客登录", true);
        registerRadio = new JRadioButton("注册账号");
        loginRadio = new JRadioButton("已有用户登录");

        ButtonGroup group = new ButtonGroup();
        group.add(guestRadio);
        group.add(registerRadio);
        group.add(loginRadio);

        usernameField = new JTextField(20);
        passwordField = new JPasswordField(20);

        usernameField.setEnabled(false);
        passwordField.setEnabled(false);

        confirmButton = new JButton("确定");

        updateInputFields();

        setLayout(new BorderLayout(10, 10));

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel typePanel = new JPanel();
        typePanel.setLayout(new BoxLayout(typePanel, BoxLayout.Y_AXIS));
        typePanel.setBorder(BorderFactory.createTitledBorder("登录类型"));

        typePanel.add(guestRadio);
        typePanel.add(Box.createRigidArea(new Dimension(0, 5)));
        typePanel.add(registerRadio);
        typePanel.add(Box.createRigidArea(new Dimension(0, 5)));
        typePanel.add(loginRadio);

        JPanel inputPanel = new JPanel(new GridBagLayout());
        inputPanel.setBorder(BorderFactory.createTitledBorder("账号信息"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        inputPanel.add(new JLabel("账号:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        inputPanel.add(usernameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        inputPanel.add(new JLabel("密码:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        inputPanel.add(passwordField, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(confirmButton);

        mainPanel.add(typePanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(inputPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 15)));
        mainPanel.add(buttonPanel);

        add(mainPanel, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(null);

        ActionListener radioListener = ev -> updateInputFields();

        guestRadio.addActionListener(radioListener);
        registerRadio.addActionListener(radioListener);
        loginRadio.addActionListener(radioListener);

        confirmButton.addActionListener(ev -> {
            switch(loginType) {
                case 0: {
                    consumer.accept(AccountUtils.generate());
                    dispose();
                    break;
                }
                case 1: {
                    try {
                        AuthData authData = AccountUtils.register(usernameField.getText(), new String(passwordField.getPassword()));
                        consumer.accept(authData);
                        dispose();
                    } catch(Exception e) {
                        JOptionPane.showMessageDialog(null, "无法注册该账号: " + e.getMessage(), "提示", JOptionPane.INFORMATION_MESSAGE);
                    }
                    break;
                }
                case 2: {
                    try {
                        AuthData authData = AuthData
                                .builder()
                                .username(usernameField.getText())
                                .password(new String(passwordField.getPassword()))
                                .build();
                        AuthResponse authResponse = AccountUtils.login(authData);
                        authData.setResponse(authResponse);
                        AccountUtils.setLoginWith(authData.getUsername(), authData.getPassword());
                        consumer.accept(authData);
                        dispose();
                    } catch(Exception e) {
                        JOptionPane.showMessageDialog(null, "无法登录账号: " + e.getMessage(), "提示", JOptionPane.INFORMATION_MESSAGE);
                    }
                    break;
                }
            }
        });

        setVisible(true);
    }

    private void updateInputFields() {
        loginType = guestRadio.isSelected() ? 0 : (registerRadio.isSelected() ? 1 : 2);
        boolean inputEnabled = registerRadio.isSelected() || loginRadio.isSelected();
        usernameField.setEnabled(inputEnabled);
        passwordField.setEnabled(inputEnabled);

        if (!inputEnabled) {
            usernameField.setText("");
            passwordField.setText("");
        }
    }
}

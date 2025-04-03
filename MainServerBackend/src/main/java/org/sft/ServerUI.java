package org.sft;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.util.regex.*;

@SpringBootApplication
public class ServerUI {

    private static JLabel statusLabel;
    private static JButton copyButton;
    private static String tunnelUrl = "";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ServerUI::createUI);
    }

    private static void createUI() {
        JFrame frame = new JFrame("Super File Transfer Server");
        frame.setSize(400, 250);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        JButton startButton = new JButton("Start Server");
        copyButton = new JButton("Copy URL");
        copyButton.setEnabled(false);  // Initially disabled

        statusLabel = new JLabel("Click 'Start Server' to begin.", SwingConstants.CENTER);

        startButton.addActionListener(e -> startServer());
        copyButton.addActionListener(e -> copyToClipboard(tunnelUrl));

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(startButton);
        buttonPanel.add(copyButton);

        frame.add(buttonPanel, BorderLayout.NORTH);
        frame.add(statusLabel, BorderLayout.CENTER);

        frame.setVisible(true);
    }

    private static void startServer() {
        Thread serverThread = new Thread(() -> {
            SpringApplication.run(ServerUI.class);
        });

        serverThread.setDaemon(true);
        serverThread.start();

        startCloudflared();
    }

    private static void startCloudflared() {
        killCloudflared();

        Thread tunnelThread = new Thread(() -> {
            try {
                String userHome = System.getProperty("user.home");
                String cloudflaredPath = userHome + "/cloudflared.exe";

                ProcessBuilder processBuilder = new ProcessBuilder(cloudflaredPath, "tunnel", "--url", "http://localhost:9090");
                processBuilder.redirectErrorStream(true);
                Process process = processBuilder.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    Pattern pattern = Pattern.compile("(https://[a-zA-Z0-9.-]+\\.trycloudflare\\.com)");
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                        Matcher matcher = pattern.matcher(line);
                        if (matcher.find()) {
                            tunnelUrl = matcher.group(1);
                            SwingUtilities.invokeLater(() -> {
                                statusLabel.setText("Cloudflared URL: " + tunnelUrl);
                                copyButton.setEnabled(true);
                            });
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        tunnelThread.setDaemon(true);
        tunnelThread.start();
    }

    private static void killCloudflared() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("taskkill", "/F", "/IM", "cloudflared.exe");
            processBuilder.directory(new File("C:/Users/agile"));
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }
        } catch (IOException e) {
            System.out.println("Failed to terminate Cloudflared. It may not be running.");
        }
    }

    private static void copyToClipboard(String text) {
        if (!text.isEmpty()) {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable transferable = new StringSelection(text);
            clipboard.setContents(transferable, null);
            JOptionPane.showMessageDialog(null, "URL copied to clipboard!", "Success", JOptionPane.INFORMATION_MESSAGE);
        }
    }
}

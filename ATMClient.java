
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

public class ATMClient extends JFrame {
    private JButton connectBtn, disconnectBtn, sendBtn;
    private JTextField commandField;
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean connected = false;

    public ATMClient() {
        initializeUI();
    }

    private void initializeUI() {
        setTitle("ATM Client");
        setSize(400, 200);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new FlowLayout());

        // 命令输入组件
        add(new JLabel("Enter Command:"));
        commandField = new JTextField(20);
        add(commandField);

        // 功能按钮
        sendBtn = new JButton("Send Command");
        connectBtn = new JButton("Connect");
        disconnectBtn = new JButton("Disconnect");
        disconnectBtn.setEnabled(false);

        add(sendBtn);
        add(connectBtn);
        add(disconnectBtn);

        // 事件监听
        connectBtn.addActionListener(e -> connectServer());
        disconnectBtn.addActionListener(e -> disconnectServer());
        sendBtn.addActionListener(e -> sendCommand());
    }

    private void connectServer() {
        try {
            clientSocket = new Socket("192.168.149.10", 2525);
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            
            connected = true;
            connectBtn.setEnabled(false);
            disconnectBtn.setEnabled(true);
            JOptionPane.showMessageDialog(this, "Connected successfully!");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Connection failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void disconnectServer() {
        try {
            if (connected) {
                out.println("BYE");
                String response = in.readLine();
                if ("BYE".equals(response)) {
                    JOptionPane.showMessageDialog(this, "Disconnected successfully");
                }
                clientSocket.close();
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Disconnect error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            connected = false;
            connectBtn.setEnabled(true);
            disconnectBtn.setEnabled(false);
        }
    }

    private void sendCommand() {
        if (!connected) {
            JOptionPane.showMessageDialog(this, "Not connected!", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String command = commandField.getText().trim();
        if (command.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Command cannot be empty!", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            out.println(command);
            String response = in.readLine();
            handleResponse(command, response);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Communication error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            disconnectServer();
        }
    }

    private void handleResponse(String command, String response) {
        if (command.startsWith("BALA") && response.startsWith("AMNT:")) {
            String balance = response.split(":")[1];
            JOptionPane.showMessageDialog(this, "Balance: " + balance);
        } else if (command.startsWith("WDRA")) {
            showDialog("Withdrawal", response.equals("525 OK!"));
        } else if (command.startsWith("HELO") || command.startsWith("PASS")) {
            handleAuthResponse(response);
        } else if (command.startsWith("BYE")) {
            if ("BYE".equals(response)) disconnectServer();
        } else {
            JOptionPane.showMessageDialog(this, response);
        }
    }

    private void handleAuthResponse(String response) {
        if ("500 AUTH REQUIRE".equals(response)) {
            JOptionPane.showMessageDialog(this, "Authentication required");
        } else if ("525 OK!".equals(response)) {
            JOptionPane.showMessageDialog(this, "Login successful");
        } else {
            JOptionPane.showMessageDialog(this, "Login failed");
        }
    }

    private void showDialog(String title, boolean success) {
        JOptionPane.showMessageDialog(this, 
            success ? "Operation successful" : "Operation failed",
            title, 
            success ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ATMClient().setVisible(true));
    }
}

//javac -cp "json-20250107.jar;." ATM_Server.java
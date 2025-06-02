package chatapp;
import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class ChatApp extends JFrame {
    private static final class UIConstants {
        static final Color PRIMARY_COLOR = new Color(0, 153, 255);
        static final Color SECONDARY_COLOR = new Color(52, 73, 94);
        static final Color SENT_BG = new Color(0, 123, 255);
        static final Color RECEIVED_BG = new Color(230, 230, 230);
        static final Color SENT_FG = Color.WHITE;
        static final Color RECEIVED_FG = Color.BLACK;
        static final Font PRIMARY_FONT = new Font("Arial", Font.PLAIN, 14);
        static final Font TITLE_FONT = new Font("Arial", Font.BOLD, 16);
        static final int SERVER_PORT = 5000;
    }

    private static final Set<PrintWriter> writers = Collections.synchronizedSet(new HashSet<>());
    private static final Map<PrintWriter, String> clientNames = Collections.synchronizedMap(new HashMap<>());

    private JList<String> messageList;
    private DefaultListModel<String> messageModel;
    private JTextField textField;
    private JTextField usernameField;
    private JLabel statusLabel;
    private JList<String> userList; // Lista para mostrar usuarios conectados
    private DefaultListModel<String> userModel; // Modelo para la lista de usuarios
    private PrintWriter out;
    private BufferedReader in;
    private boolean isServer;
    private String username;
    private String serverAddress;
    private volatile boolean isTyping;
    private Timer typingTimer;

    public ChatApp(boolean isServer, String serverAddress, String username) {
        this.isServer = isServer;
        this.serverAddress = serverAddress;
        this.username = username;
        setTitle(isServer ? "Chat - Servidor" : "Chat - Cliente");
        setLayout(new BorderLayout());

        // Panel de encabezado
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(UIConstants.SECONDARY_COLOR);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JLabel titleLabel = new JLabel("ChatApp", SwingConstants.LEFT);
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(UIConstants.TITLE_FONT);
        headerPanel.add(titleLabel, BorderLayout.WEST);
        add(headerPanel, BorderLayout.NORTH);

        // Panel central con mensajes y lista de usuarios
        JPanel centerPanel = new JPanel(new BorderLayout());
        messageModel = new DefaultListModel<>();
        messageList = new JList<>(messageModel);
        messageList.setCellRenderer(new MessageCellRenderer());
        messageList.setBackground(new Color(236, 240, 241));
        JScrollPane messageScrollPane = new JScrollPane(messageList);
        messageScrollPane.setBorder(BorderFactory.createEmptyBorder());
        centerPanel.add(messageScrollPane, BorderLayout.CENTER);

        // Lista de usuarios conectados (visible solo en el servidor)
        if (isServer) {
            userModel = new DefaultListModel<>();
            userList = new JList<>(userModel);
            userList.setFont(UIConstants.PRIMARY_FONT);
            userList.setBackground(new Color(236, 240, 241));
            JScrollPane userScrollPane = new JScrollPane(userList);
            userScrollPane.setPreferredSize(new Dimension(150, 0));
            userScrollPane.setBorder(BorderFactory.createTitledBorder("Usuarios Conectados"));
            centerPanel.add(userScrollPane, BorderLayout.EAST);
        }

        add(centerPanel, BorderLayout.CENTER);

        // Panel inferior
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(new Color(245, 245, 245));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Panel de usuario
        JPanel userPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        userPanel.setBackground(new Color(245, 245, 245));
        usernameField = new JTextField(10);
        usernameField.setFont(UIConstants.PRIMARY_FONT);
        usernameField.setText(username);
        usernameField.setEditable(false);
        userPanel.add(new JLabel("Nombre:"));
        userPanel.add(usernameField);
        if (isServer) {
            userPanel.setVisible(false);
        }
        bottomPanel.add(userPanel, BorderLayout.NORTH);

        // Panel de entrada
        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        inputPanel.setBackground(new Color(245, 245, 245));
        textField = new JTextField();
        textField.setFont(UIConstants.PRIMARY_FONT);
        textField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(150, 150, 150), 1, true),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        textField.addActionListener(e -> sendMessage());
        textField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && e.isControlDown()) {
                    sendMessage();
                }
            }

            @Override
            public void keyTyped(KeyEvent e) {
                if (!isTyping && out != null) {
                    isTyping = true;
                    out.println("/typing:" + username);
                }
            }
        });
        inputPanel.add(textField, BorderLayout.CENTER);

        JButton sendButton = new JButton("Enviar");
        sendButton.setFont(new Font("Arial", Font.BOLD, 12));
        sendButton.setBackground(UIConstants.PRIMARY_COLOR);
        sendButton.setForeground(Color.WHITE);
        sendButton.setFocusPainted(false);
        sendButton.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
        sendButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sendButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                sendButton.setBackground(new Color(0, 120, 200));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                sendButton.setBackground(UIConstants.PRIMARY_COLOR);
            }
        });
        sendButton.addActionListener(e -> sendMessage());
        inputPanel.add(sendButton, BorderLayout.EAST);

        bottomPanel.add(inputPanel, BorderLayout.CENTER);

        statusLabel = new JLabel("Iniciando...");
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(400, 500));
        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        // Iniciar servidor o cliente
        if (isServer) {
            startServer();
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    usernameField.setText("Servidor");
                    startClient();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        } else {
            startClient();
        }

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeConnection();
            }
        });
    }

    private void sendMessage() {
        String message = textField.getText().trim();
        if (!message.isEmpty() && out != null) {
            out.println(username + ": " + message);
            textField.setText("");
            // Notificar que el usuario dejó de escribir al enviar el mensaje
            isTyping = false;
            out.println("/stopTyping");
        }
    }

    private void appendMessage(String message, boolean isSent) {
        SwingUtilities.invokeLater(() -> {
            messageModel.addElement((isSent ? "sent:" : "received:") + message);
            messageList.ensureIndexIsVisible(messageModel.size() - 1);
        });
    }

    private void updateStatus(String status, boolean isError) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(status);
            statusLabel.setForeground(isError ? Color.RED.darker() : new Color(39, 174, 96));
        });
    }

    private void updateUserList() {
        if (isServer && userModel != null) {
            SwingUtilities.invokeLater(() -> {
                userModel.clear();
                synchronized (clientNames) {
                    for (String name : clientNames.values()) {
                        userModel.addElement(name);
                    }
                }
            });
        }
    }

    private void startServer() {
        new Thread(() -> {
            try (ServerSocket listener = new ServerSocket(UIConstants.SERVER_PORT)) {
                String ip = getLocalIPAddress();
                updateStatus("Servidor iniciado en " + ip + ":" + UIConstants.SERVER_PORT, false);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                            "Servidor iniciado.\nDirección IP: " + ip + "\nPuerto: " + UIConstants.SERVER_PORT,
                            "Información del Servidor",
                            JOptionPane.INFORMATION_MESSAGE);
                });
                while (!Thread.currentThread().isInterrupted()) {
                    new Handler(listener.accept()).start();
                }
            } catch (IOException e) {
                updateStatus("Error en el servidor: " + e.getMessage(), true);
            }
        }).start();
    }

    private String getLocalIPAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private class Handler extends Thread {
        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;

        public Handler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                synchronized (writers) {
                    writers.add(out);
                }
                // Registrar el nombre del cliente
                String clientName = in.readLine();
                synchronized (clientNames) {
                    clientNames.put(out, clientName);
                }
                updateUserList(); // Actualizar la lista de usuarios al conectar

                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("/typing:")) {
                        String typingUser = message.substring("/typing:".length());
                        synchronized (writers) {
                            for (PrintWriter writer : writers) {
                                if (writer != out) {
                                    writer.println("/typing:" + typingUser);
                                }
                            }
                        }
                    } else if (message.equals("/stopTyping")) {
                        synchronized (writers) {
                            for (PrintWriter writer : writers) {
                                if (writer != out) {
                                    writer.println("/stopTyping");
                                }
                            }
                        }
                    } else {
                        synchronized (writers) {
                            for (PrintWriter writer : writers) {
                                writer.println(message);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                updateStatus("Error con cliente: " + e.getMessage(), true);
            } finally {
                if (out != null) {
                    synchronized (writers) {
                        writers.remove(out);
                    }
                    synchronized (clientNames) {
                        clientNames.remove(out);
                    }
                    updateUserList(); // Actualizar la lista de usuarios al desconectar
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    updateStatus("Error al cerrar socket: " + e.getMessage(), true);
                }
            }
        }
    }

    private void startClient() {
        try {
            usernameField.setEditable(false);
            if (username.isEmpty()) {
                username = "Anónimo";
                usernameField.setText(username);
            }
            Socket socket = new Socket(serverAddress, UIConstants.SERVER_PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            updateStatus("Conectado a " + serverAddress + ":" + UIConstants.SERVER_PORT, false);

            // Enviar nombre inicial al servidor
            out.println(username);

            new Thread(() -> {
                try {
                    String message;
                    while ((message = in.readLine()) != null) {
                        if (message.startsWith("/typing:")) {
                            if (typingTimer != null) {
                                typingTimer.stop();
                            }
                            String typingUser = message.substring("/typing:".length());
                            appendMessage(typingUser + " está escribiendo...", false);
                            // No iniciamos el temporizador para mantener el mensaje
                        } else if (message.equals("/stopTyping")) {
                            removeTypingNotification();
                        } else {
                            appendMessage(message, false);
                        }
                    }
                } catch (IOException e) {
                    updateStatus("Desconectado: " + e.getMessage(), true);
                }
            }).start();
        } catch (IOException e) {
            updateStatus("Error al conectar: " + e.getMessage(), true);
        }
    }

    private void removeTypingNotification() {
        SwingUtilities.invokeLater(() -> {
            for (int i = messageModel.size() - 1; i >= 0; i--) {
                if (messageModel.get(i).startsWith("received:") &&
                        messageModel.get(i).endsWith(" está escribiendo...")) {
                    messageModel.remove(i);
                }
            }
        });
    }

    private void closeConnection() {
        try {
            if (out != null) {
                out.close();
            }
            if (in != null) {
                in.close();
            }
        } catch (IOException ignored) {
        }
    }

    private static class MessageCellRenderer extends JPanel implements ListCellRenderer<String> {
        private JLabel messageLabel;
        private JLabel timestampLabel;

        public MessageCellRenderer() {
            setLayout(new BorderLayout(5, 5));
            setOpaque(true);
            messageLabel = new JLabel();
            messageLabel.setFont(UIConstants.PRIMARY_FONT);
            messageLabel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
            timestampLabel = new JLabel();
            timestampLabel.setFont(new Font("Arial", Font.PLAIN, 10));
            timestampLabel.setForeground(Color.GRAY);
            add(messageLabel, BorderLayout.CENTER);
            add(timestampLabel, BorderLayout.SOUTH);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            boolean sent = value.startsWith("sent:");
            String text = value.substring(sent ? 5 : 9);
            messageLabel.setText("<html>" + text.replaceAll("\n", "<br>") + "</html>");
            timestampLabel.setText(new SimpleDateFormat("HH:mm").format(new Date()));
            if (sent) {
                setBackground(UIConstants.SENT_BG);
                messageLabel.setForeground(UIConstants.SENT_FG);
                messageLabel.setHorizontalAlignment(SwingConstants.RIGHT);
                timestampLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            } else {
                setBackground(UIConstants.RECEIVED_BG);
                messageLabel.setForeground(UIConstants.RECEIVED_FG);
                messageLabel.setHorizontalAlignment(SwingConstants.LEFT);
                timestampLabel.setHorizontalAlignment(SwingConstants.LEFT);
            }
            setBorder(BorderFactory.createEmptyBorder(5, sent ? 50 : 10, 5, sent ? 10 : 50));
            return this;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String[] options = {"Servidor", "Cliente"};
            int choice = JOptionPane.showOptionDialog(null,
                    "¿Desea iniciar como servidor o cliente?",
                    "Modo de Chat",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[0]);

            if (choice == 0) {
                new ChatApp(true, null, "Servidor");
            } else if (choice == 1) {
                String username = JOptionPane.showInputDialog(null, "Ingrese su nombre:", "Nombre de Usuario");
                if (username == null || username.trim().isEmpty()) {
                    username = "Anónimo";
                }
                String ip = JOptionPane.showInputDialog(null, "Ingrese la dirección IP del servidor:", "127.0.0.1");
                if (ip != null && !ip.trim().isEmpty()) {
                    new ChatApp(false, ip.trim(), username.trim());
                }
            }
        });
    }
}
package org.example.clientsevermsgexample;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerView implements Initializable {
    private static final int PORT = 6666;

    @FXML
    private AnchorPane ap_main;

    @FXML
    private Button button_send;

    @FXML
    private TextField tf_message;

    @FXML
    private ScrollPane sp_main;

    @FXML
    private VBox vbox_messages;

    private final ConcurrentHashMap<Integer, DataOutputStream> clients = new ConcurrentHashMap<>();
    private final AtomicInteger clientIds = new AtomicInteger();
    private volatile ServerSocket serverSocket;
    private volatile boolean running;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        button_send.setOnAction(event -> sendServerMessage());
        tf_message.setOnAction(event -> sendServerMessage());

        ap_main.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.getWindow().setOnCloseRequest(event -> stopServer());
            }
        });

        addMessage("Server: starting on localhost:" + PORT);
        startServer();
    }

    private void sendServerMessage() {
        String text = tf_message.getText().trim();
        if (text.isEmpty()) {
            return;
        }

        broadcast("Server: " + text);
        tf_message.clear();
    }

    private void startServer() {
        if (running) {
            return;
        }

        running = true;
        Thread serverThread = new Thread(() -> {
            try (ServerSocket listener = new ServerSocket(PORT)) {
                serverSocket = listener;
                addMessage("Server: listening on localhost:" + PORT);

                while (running) {
                    Socket clientSocket = listener.accept();
                    int clientId = clientIds.incrementAndGet();

                    addMessage("Server: client " + clientId + " connected");
                    Thread clientThread = new Thread(
                            () -> handleClient(clientId, clientSocket),
                            "chat-client-" + clientId
                    );
                    clientThread.setDaemon(true);
                    clientThread.start();
                }
            } catch (SocketException | EOFException exception) {
                if (running) {
                    addMessage("Server error: " + exception.getMessage());
                }
            } catch (IOException exception) {
                addMessage("Server error: " + exception.getMessage());
            } finally {
                running = false;
            }
        }, "chat-server");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void handleClient(int clientId, Socket clientSocket) {
        try (Socket socket = clientSocket;
             DataInputStream input = new DataInputStream(socket.getInputStream());
             DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {

            clients.put(clientId, output);

            while (running) {
                String message = input.readUTF();
                if ("exit".equalsIgnoreCase(message.trim())) {
                    break;
                }
                broadcast("Client " + clientId + ": " + message);
            }
        } catch (EOFException | SocketException exception) {
            addMessage("Server: client " + clientId + " disconnected");
        } catch (IOException exception) {
            addMessage("Server error for client " + clientId + ": " + exception.getMessage());
        } finally {
            clients.remove(clientId);
            addMessage("Server: client " + clientId + " disconnected");
        }
    }

    private void broadcast(String message) {
        addMessage(message);

        for (DataOutputStream output : clients.values()) {
            try {
                synchronized (output) {
                    output.writeUTF(message);
                    output.flush();
                }
            } catch (IOException exception) {
                addMessage("Server error while broadcasting: " + exception.getMessage());
            }
        }
    }

    private void stopServer() {
        running = false;

        for (DataOutputStream output : clients.values()) {
            try {
                output.close();
            } catch (IOException ignored) {
            }
        }
        clients.clear();

        ServerSocket listener = serverSocket;
        if (listener != null && !listener.isClosed()) {
            try {
                listener.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void addMessage(String message) {
        Platform.runLater(() -> {
            Label messageLabel = new Label(message);
            messageLabel.setWrapText(true);
            messageLabel.setMaxWidth(390);
            vbox_messages.getChildren().add(messageLabel);
            sp_main.setVvalue(1.0);
        });
    }
}

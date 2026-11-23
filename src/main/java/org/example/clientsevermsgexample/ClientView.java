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
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.util.ResourceBundle;

public class ClientView implements Initializable {
    private static final String HOST = "localhost";
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

    private Socket socket;
    private DataOutputStream output;
    private volatile boolean connected;
    private volatile boolean running;
    private String userName = "Client";

    public void setUserName(String userName) {
        this.userName = userName;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        button_send.setOnAction(event -> sendMessage());
        tf_message.setOnAction(event -> sendMessage());

        ap_main.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.getWindow().setOnCloseRequest(event -> disconnect());
            }
        });
    }

    public void connect() {
        if (connected) {
            return;
        }

        Thread connectionThread = new Thread(() -> {
            try {
                socket = new Socket(HOST, PORT);
                output = new DataOutputStream(socket.getOutputStream());
                DataInputStream input = new DataInputStream(socket.getInputStream());
                connected = true;
                running = true;

                addMessage(userName + ": connected to " + HOST + ":" + PORT);

                while (running) {
                    String message = input.readUTF();
                    addMessage(message);
                }
            } catch (EOFException | SocketException exception) {
                if (running) {
                    addMessage("Connection closed");
                }
            } catch (IOException exception) {
                addMessage("Unable to connect: " + exception.getMessage());
            } finally {
                connected = false;
                running = false;
            }
        }, userName + "-connection");
        connectionThread.setDaemon(true);
        connectionThread.start();
    }

    private void sendMessage() {
        String text = tf_message.getText().trim();
        if (text.isEmpty()) {
            return;
        }

        if (!connected || output == null) {
            addMessage("Not connected to the server");
            return;
        }

        try {
            synchronized (output) {
                output.writeUTF(userName + ": " + text);
                output.flush();
            }
            tf_message.clear();
        } catch (IOException exception) {
            addMessage("Unable to send message: " + exception.getMessage());
        }
    }

    private void disconnect() {
        running = false;
        connected = false;

        try {
            if (output != null) {
                output.writeUTF("exit");
                output.flush();
                output.close();
            }
        } catch (IOException ignored) {
        }

        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
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

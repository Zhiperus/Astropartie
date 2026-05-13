package org.astropanty.net;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;

public class GameClient {
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private Socket socket;

    // Using volatile to ensure JavaFX thread sees updates immediately
    public volatile GameStatePayload latestState = new GameStatePayload();
    public volatile int myPlayerId = -1;
    public volatile boolean connected = false;
    public volatile String playerName = "Player";

    private final ConcurrentLinkedQueue<String> pendingChatMessages = new ConcurrentLinkedQueue<>();

    public void connect(String ip, int port, String playerName) {
        this.playerName = playerName;
        new Thread(() -> {
            try {
                socket = new Socket(ip, port);
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());

                // Read assigned player ID
                myPlayerId = in.readInt();
                connected = true;
                System.out.println("Connected as Player " + myPlayerId + " (" + playerName + ")");

                // Send player name to server
                out.writeObject(playerName);
                out.flush();

                // Receiver Loop
                while (true) {
                    GameStatePayload state = (GameStatePayload) in.readObject();
                    if (state != null) {
                        latestState = state;
                    }
                }
            } catch (Exception e) {
                System.err.println("Disconnected from server: " + e.getMessage());
                connected = false;
            }
        }).start();
    }

    public void queueChatMessage(String message) {
        if (message != null && !message.isBlank()) {
            pendingChatMessages.offer(message);
        }
    }

    public void sendInput(InputPayload input) {
        if (!connected || out == null)
            return;
        // Attach any pending chat message
        input.chatMessage = pendingChatMessages.poll();
        try {
            out.reset();
            out.writeObject(input);
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void disconnect() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        connected = false;
    }
}

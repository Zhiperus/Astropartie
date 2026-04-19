package org.astropanty.net;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class GameClient {
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private Socket socket;

    // Using volatile to ensure JavaFX thread sees updates immediately
    public volatile GameStatePayload latestState = new GameStatePayload();
    public volatile int myPlayerId = -1;
    public volatile boolean connected = false;

    public void connect(String ip, int port) {
        new Thread(() -> {
            try {
                socket = new Socket(ip, port);
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());

                // Read assigned player ID
                myPlayerId = in.readInt();
                connected = true;
                System.out.println("Connected as Player " + myPlayerId);

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

    public void sendInput(InputPayload input) {
        if (!connected || out == null)
            return;
        try {
            out.reset();
            out.writeObject(input);
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

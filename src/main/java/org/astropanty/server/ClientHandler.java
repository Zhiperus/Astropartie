package org.astropanty.server;

import org.astropanty.net.GameStatePayload;
import org.astropanty.net.InputPayload;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final GameServer server;
    private final int playerId;

    private ObjectInputStream in;
    private ObjectOutputStream out;

    // Server loop reads this directly
    public volatile InputPayload latestInput = null;

    public ClientHandler(Socket socket, GameServer server, int playerId) {
        this.socket = socket;
        this.server = server;
        this.playerId = playerId;
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            // First thing: tell the client what ID they are
            out.writeInt(playerId);
            out.flush();
        } catch (Exception e) {
            System.err.println("Failed to setup streams for player " + playerId);
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                // Blocking read - waits for client input payload
                InputPayload input = (InputPayload) in.readObject();
                if (input != null && input.playerId == this.playerId) {
                    this.latestInput = input;
                }
            }
        } catch (Exception e) {
            System.out.println("Player " + playerId + " disconnected.");
        }
    }

    public void sendState(GameStatePayload state) {
        try {
            out.reset(); // Crucial for repeated objects
            out.writeObject(state);
            out.flush();
        } catch (Exception e) {
            // Error handling ignored for brevity
        }
    }
}

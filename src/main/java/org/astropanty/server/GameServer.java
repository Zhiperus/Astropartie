package org.astropanty.server;

import org.astropanty.net.GameStatePayload;
import org.astropanty.data.MapLayouts;
import org.astropanty.ui.game.entities.Wall;

import javafx.application.Platform;
import javafx.geometry.Rectangle2D;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class GameServer {
    private static final int PORT = 8080;
    private static final int MAX_PLAYERS = 4;

    // Server states
    public GameStatePayload currentState = new GameStatePayload();
    public CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();

    // We keep track of standard ship speed and bounds
    private double[] speed = { 5, 5, 5, 5 };
    private double[] xPos = { 100, 800, 100, 800 };
    private double[] yPos = { 100, 400, 400, 100 };
    private double[] rotation = { 0, 0, 0, 0 };
    public int[] healths = { 100, 100, 100, 100 };

    // Cooldown management
    private long[] lastShot = new long[4];
    private boolean[] connected = new boolean[4];
    private int[] shipTypes = new int[4];

    // Projectile management
    class ServerBullet {
        double x, y, rotation;
        int owner;
        boolean active = false;
    }

    private ServerBullet[] bullets = new ServerBullet[20];

    // Keep track of map bounding logic
    private List<Wall> walls;

    public static final int WINDOW_WIDTH = 960;
    public static final int WINDOW_HEIGHT = 540;

    public static void main(String[] args) {
        Platform.startup(() -> {
            new GameServer().start();
        });
    }

    public void start() {
        walls = MapLayouts.getMap1Wall();
        for (int i = 0; i < 20; i++)
            bullets[i] = new ServerBullet();

        new Thread(this::serverGameLoop).start();

        System.out.println("Starting GameServer on port " + PORT + "...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (clients.size() < MAX_PLAYERS) {
                Socket socket = serverSocket.accept();
                int playerId = clients.size();
                connected[playerId] = true;
                System.out.println("Player " + playerId + " connected from " + socket.getInetAddress());

                ClientHandler handler = new ClientHandler(socket, this, playerId);
                clients.add(handler);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void serverGameLoop() {
        while (true) {
            updatePhysics();
            broadcastState();

            try {
                Thread.sleep(16); // ~60 ticks per second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void updatePhysics() {
        long currentNanoTime = System.nanoTime();

        for (int i = 0; i < clients.size(); i++) {
            ClientHandler handler = clients.get(i);
            if (handler.latestInput == null)
                continue;

            shipTypes[i] = handler.latestInput.shipType;

            // Health > 0 required to move or shoot
            if (healths[i] > 0) {
                // Apply rotation
                if (handler.latestInput.right)
                    rotation[i] = (rotation[i] + 3) % 360;
                if (handler.latestInput.left)
                    rotation[i] = (rotation[i] - 3 + 360) % 360;

                // Apply forward movement
                if (handler.latestInput.forward) {
                    xPos[i] += Math.sin(Math.toRadians(rotation[i])) * speed[i];
                    yPos[i] -= Math.cos(Math.toRadians(rotation[i])) * speed[i];
                }

                // Apply shooting
                if (handler.latestInput.shoot && (currentNanoTime - lastShot[i] > 100_000_000L)) {
                    for (ServerBullet b : bullets) {
                        if (!b.active) {
                            b.active = true;
                            b.owner = i;
                            b.x = xPos[i] + 13;
                            b.y = yPos[i] + 13;
                            b.rotation = rotation[i];
                            lastShot[i] = currentNanoTime;
                            break;
                        }
                    }
                }
            }

            // Adjust positions against map layouts (walls)
            adjustCollisions(i);

            // Screen wrapping identical to local logic
            if (xPos[i] > WINDOW_WIDTH)
                xPos[i] = -50;
            else if (xPos[i] < -50)
                xPos[i] = WINDOW_WIDTH;

            if (yPos[i] > WINDOW_HEIGHT)
                yPos[i] = -50;
            else if (yPos[i] < -50)
                yPos[i] = WINDOW_HEIGHT;

            // Update output payload state
            currentState.shipXs[i] = xPos[i];
            currentState.shipYs[i] = yPos[i];
            currentState.rotations[i] = rotation[i];
            currentState.healths[i] = healths[i];
            currentState.connected[i] = connected[i];
            currentState.shipTypes[i] = shipTypes[i];
        }

        // Projectile loop
        for (int i = 0; i < 20; i++) {
            ServerBullet b = bullets[i];
            if (!b.active) {
                currentState.projActive[i] = false;
                continue;
            }

            // Movement
            b.x += Math.sin(Math.toRadians(b.rotation)) * 10;
            b.y -= Math.cos(Math.toRadians(b.rotation)) * 10;

            if (b.x < 0 || b.x > WINDOW_WIDTH || b.y < 0 || b.y > WINDOW_HEIGHT) {
                b.active = false;
            }

            Rectangle2D bHitbox = new Rectangle2D(b.x, b.y, 10, 10);
            for (Wall w : walls) {
                if (w.getBounds().intersects(bHitbox))
                    b.active = false;
            }

            if (b.active) {
                for (int p = 0; p < clients.size(); p++) {
                    if (p == b.owner || healths[p] <= 0 || !connected[p])
                        continue;
                    Rectangle2D shipBox = new Rectangle2D(xPos[p], yPos[p], 33, 42);
                    if (shipBox.intersects(bHitbox)) {
                        healths[p] = Math.max(0, healths[p] - 10);
                        b.active = false;
                    }
                }
            }
            currentState.projXs[i] = b.x;
            currentState.projYs[i] = b.y;
            currentState.projActive[i] = b.active;
        }
    }

    private void adjustCollisions(int i) {
        Rectangle2D shipBounds = new Rectangle2D(xPos[i], yPos[i], 33, 42);
        for (Wall wall : walls) {
            Rectangle2D wallBounds = wall.getBounds();
            if (shipBounds.intersects(wallBounds)) {
                if (shipBounds.getMinX() < wallBounds.getMinX())
                    xPos[i] = wallBounds.getMinX() - 33;
                else if (shipBounds.getMaxX() > wallBounds.getMaxX())
                    xPos[i] = wallBounds.getMaxX();

                if (shipBounds.getMinY() < wallBounds.getMinY())
                    yPos[i] = wallBounds.getMinY() - 42;
                else if (shipBounds.getMaxY() > wallBounds.getMaxY())
                    yPos[i] = wallBounds.getMaxY();

                shipBounds = new Rectangle2D(xPos[i], yPos[i], 33, 42);
            }
        }
    }

    private void broadcastState() {
        for (ClientHandler client : clients) {
            client.sendState(currentState);
        }
    }
}

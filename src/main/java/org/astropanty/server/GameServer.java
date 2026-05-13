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

public class GameServer {
    private static final int PORT = 8080;
    private static final int MAX_PLAYERS = 4;

    // Game phases
    public static final int PHASE_WAITING = 0;
    public static final int PHASE_COUNTDOWN = 1;
    public static final int PHASE_PLAYING = 2;
    public static final int PHASE_GAMEOVER = 3;

    // Server state
    public GameStatePayload currentState = new GameStatePayload();

    // Fixed-size client array — index = playerId
    private final ClientHandler[] clients = new ClientHandler[MAX_PLAYERS];

    private double[] speed = { 5, 5, 5, 5 };
    private double[] xPos = { 100, 800, 100, 800 };
    private double[] yPos = { 100, 400, 400, 100 };
    private double[] rotation = { 0, 0, 0, 0 };
    public int[] healths = { 0, 0, 0, 0 }; // 0 until round starts

    // Spawn positions (reset each round)
    private static final double[] SPAWN_X = { 100, 800, 100, 800 };
    private static final double[] SPAWN_Y = { 100, 400, 400, 100 };

    private long[] lastShot = new long[4];
    private boolean[] connected = new boolean[4];
    private int[] shipTypes = new int[4];
    private String[] playerNames = new String[4];

    // Flag: was this player part of the lobby when the game started?
    private boolean[] activeInRound = new boolean[4];

    // Chat
    private String[] chatHistory = new String[5];

    // Bullet management
    class ServerBullet {
        double x, y, rotation;
        int owner;
        boolean active = false;
    }

    private ServerBullet[] bullets = new ServerBullet[20];

    // Map
    private List<Wall> walls;

    // Phase tracking
    private int gamePhase = PHASE_WAITING;
    private long phaseEndMs = 0; // System.currentTimeMillis() when this phase ends
    private int winnerId = -1;

    public static final int WINDOW_WIDTH = 960;
    public static final int WINDOW_HEIGHT = 540;

    // -------------------------------------------------------------------------
    public static void main(String[] args) {
        Platform.startup(() -> new GameServer().start());
    }

    public void start() {
        walls = MapLayouts.getMap1Wall();
        for (int i = 0; i < 20; i++)
            bullets[i] = new ServerBullet();

        new Thread(this::serverGameLoop).start();

        System.out.println("Starting GameServer on port " + PORT + "...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                int playerId = findFreeSlot();
                if (playerId == -1) {
                    System.out.println("Server full, rejecting connection.");
                    socket.close();
                    continue;
                }
                connected[playerId] = true;
                System.out.println("Player " + playerId + " connected from " + socket.getInetAddress());

                ClientHandler handler = new ClientHandler(socket, this, playerId);
                clients[playerId] = handler;
                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int findFreeSlot() {
        for (int i = 0; i < MAX_PLAYERS; i++) {
            if (!connected[i])
                return i;
        }
        return -1;
    }

    /** Called by ClientHandler when a client disconnects. */
    public synchronized void handleDisconnect(int playerId) {
        connected[playerId] = false;
        healths[playerId] = 0;
        activeInRound[playerId] = false;
        clients[playerId] = null;
        playerNames[playerId] = null;
        System.out.println("Player " + playerId + " slot freed.");
    }

    // -------------------------------------------------------------------------
    private void serverGameLoop() {
        while (true) {
            updatePhysics();
            broadcastState();
            try {
                Thread.sleep(16);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void pushChatMessage(String message) {
        for (int i = chatHistory.length - 1; i > 0; i--)
            chatHistory[i] = chatHistory[i - 1];
        chatHistory[0] = message;
    }

    // -------------------------------------------------------------------------
    private synchronized void updatePhysics() {
        long now = System.currentTimeMillis();
        int connectedCount = countConnected();
        int activeCount = countActivePlayers();

        // --- Phase transitions ---
        switch (gamePhase) {
            case PHASE_WAITING:
                if (connectedCount >= 2) {
                    gamePhase = PHASE_COUNTDOWN;
                    phaseEndMs = now + 10_000; // 10-second lobby countdown
                    System.out.println("Countdown started.");
                }
                break;

            case PHASE_COUNTDOWN:
                if (connectedCount < 2) {
                    gamePhase = PHASE_WAITING;
                    phaseEndMs = 0;
                    System.out.println("Not enough players, back to waiting.");
                } else if (now >= phaseEndMs) {
                    startRound();
                }
                break;

            case PHASE_PLAYING:
                if (activeCount <= 1) {
                    winnerId = findLastActivePlayer();
                    gamePhase = PHASE_GAMEOVER;
                    phaseEndMs = now + 5_000; // 5-second win screen
                    System.out.println("Game over. Winner: " + winnerId);
                }
                break;

            case PHASE_GAMEOVER:
                if (now >= phaseEndMs) {
                    resetToLobby();
                }
                break;
        }

        // --- Sync connection/health state for ALL slots (including disconnected) ---
        for (int i = 0; i < MAX_PLAYERS; i++) {
            currentState.connected[i] = connected[i];
            currentState.healths[i] = healths[i];
        }

        // --- Per-client input processing ---
        for (int i = 0; i < MAX_PLAYERS; i++) {
            ClientHandler handler = clients[i];
            if (handler == null || !connected[i])
                continue;

            // Sync player name
            if (handler.playerName != null)
                playerNames[i] = handler.playerName;

            // Process chat
            String chatMsg;
            while ((chatMsg = handler.pendingChatMessages.poll()) != null) {
                String formatted = "[" + playerNames[i] + "]: " + chatMsg;
                pushChatMessage(formatted);
                System.out.println("CHAT " + formatted);
            }

            if (handler.latestInput == null)
                continue;

            shipTypes[i] = handler.latestInput.shipType;

            // Only alive, active-in-round players can move/shoot
            if (healths[i] > 0 && activeInRound[i]) {
                if (handler.latestInput.right)
                    rotation[i] = (rotation[i] + 3) % 360;
                if (handler.latestInput.left)
                    rotation[i] = (rotation[i] - 3 + 360) % 360;
                if (handler.latestInput.forward) {
                    xPos[i] += Math.sin(Math.toRadians(rotation[i])) * speed[i];
                    yPos[i] -= Math.cos(Math.toRadians(rotation[i])) * speed[i];
                }

                // Shooting only allowed during PLAYING phase
                boolean canShoot = gamePhase == PHASE_PLAYING
                        && handler.latestInput.shoot
                        && (System.nanoTime() - lastShot[i] > 100_000_000L);
                if (canShoot) {
                    for (ServerBullet b : bullets) {
                        if (!b.active) {
                            b.active = true;
                            b.owner = i;
                            b.x = xPos[i] + 13;
                            b.y = yPos[i] + 13;
                            b.rotation = rotation[i];
                            lastShot[i] = System.nanoTime();
                            break;
                        }
                    }
                }
            }

            // Wall + screen wrap
            adjustCollisions(i);
            if (xPos[i] > WINDOW_WIDTH)
                xPos[i] = -50;
            else if (xPos[i] < -50)
                xPos[i] = WINDOW_WIDTH;
            if (yPos[i] > WINDOW_HEIGHT)
                yPos[i] = -50;
            else if (yPos[i] < -50)
                yPos[i] = WINDOW_HEIGHT;

            currentState.shipXs[i] = xPos[i];
            currentState.shipYs[i] = yPos[i];
            currentState.rotations[i] = rotation[i];
            currentState.healths[i] = healths[i];
            currentState.connected[i] = connected[i];
            currentState.shipTypes[i] = shipTypes[i];
        }

        // Bullet physics (only during PLAYING)
        for (int i = 0; i < 20; i++) {
            ServerBullet b = bullets[i];
            if (!b.active) {
                currentState.projActive[i] = false;
                continue;
            }

            b.x += Math.sin(Math.toRadians(b.rotation)) * 10;
            b.y -= Math.cos(Math.toRadians(b.rotation)) * 10;

            if (b.x < 0 || b.x > WINDOW_WIDTH || b.y < 0 || b.y > WINDOW_HEIGHT)
                b.active = false;

            Rectangle2D bHit = new Rectangle2D(b.x, b.y, 10, 10);
            for (Wall w : walls)
                if (w.getBounds().intersects(bHit))
                    b.active = false;

            if (b.active && gamePhase == PHASE_PLAYING) {
                for (int p = 0; p < MAX_PLAYERS; p++) {
                    if (p == b.owner || healths[p] <= 0 || !connected[p] || !activeInRound[p])
                        continue;
                    Rectangle2D shipBox = new Rectangle2D(xPos[p], yPos[p], 33, 42);
                    if (shipBox.intersects(bHit)) {
                        healths[p] = Math.max(0, healths[p] - 10);
                        b.active = false;
                    }
                }
            }

            currentState.projXs[i] = b.x;
            currentState.projYs[i] = b.y;
            currentState.projActive[i] = b.active;
        }

        // Sync phase info into payload
        currentState.gamePhase = gamePhase;
        currentState.phaseTimeRemaining = Math.max(0, phaseEndMs - now);
        currentState.winnerId = winnerId;
        currentState.playerNames = playerNames.clone();
        currentState.recentChats = chatHistory.clone();
    }

    /** Begin the actual match round — everyone connected in lobby spawns in. */
    private void startRound() {
        // Clear all bullets
        for (ServerBullet b : bullets)
            b.active = false;

        for (int i = 0; i < MAX_PLAYERS; i++) {
            if (connected[i]) {
                activeInRound[i] = true;
                healths[i] = 100;
                xPos[i] = SPAWN_X[i];
                yPos[i] = SPAWN_Y[i];
                rotation[i] = 0;
            }
        }
        gamePhase = PHASE_PLAYING;
        winnerId = -1;
        System.out.println("Round started!");
    }

    /** Called after win screen — puts everyone back in the lobby. */
    private void resetToLobby() {
        for (int i = 0; i < MAX_PLAYERS; i++) {
            activeInRound[i] = false;
            healths[i] = 0;
        }
        for (ServerBullet b : bullets)
            b.active = false;
        gamePhase = PHASE_WAITING;
        winnerId = -1;
        phaseEndMs = 0;
        System.out.println("Back to lobby.");
    }

    private int countConnected() {
        int c = 0;
        for (boolean b : connected)
            if (b)
                c++;
        return c;
    }

    private int countActivePlayers() {
        int c = 0;
        for (int i = 0; i < MAX_PLAYERS; i++)
            if (connected[i] && activeInRound[i] && healths[i] > 0)
                c++;
        return c;
    }

    private int findLastActivePlayer() {
        for (int i = 0; i < MAX_PLAYERS; i++)
            if (connected[i] && activeInRound[i] && healths[i] > 0)
                return i;
        return -1; // draw
    }

    private void adjustCollisions(int i) {
        Rectangle2D shipBounds = new Rectangle2D(xPos[i], yPos[i], 33, 42);
        for (Wall wall : walls) {
            Rectangle2D wb = wall.getBounds();
            if (shipBounds.intersects(wb)) {
                if (shipBounds.getMinX() < wb.getMinX())
                    xPos[i] = wb.getMinX() - 33;
                else if (shipBounds.getMaxX() > wb.getMaxX())
                    xPos[i] = wb.getMaxX();
                if (shipBounds.getMinY() < wb.getMinY())
                    yPos[i] = wb.getMinY() - 42;
                else if (shipBounds.getMaxY() > wb.getMaxY())
                    yPos[i] = wb.getMaxY();
                shipBounds = new Rectangle2D(xPos[i], yPos[i], 33, 42);
            }
        }
    }

    private void broadcastState() {
        for (int i = 0; i < MAX_PLAYERS; i++) {
            ClientHandler c = clients[i];
            if (c != null)
                c.sendState(currentState);
        }
    }
}

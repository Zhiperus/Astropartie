package org.astropanty.ui.game.logic;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.astropanty.net.GameClient;
import org.astropanty.net.GameStatePayload;
import org.astropanty.net.InputPayload;
import org.astropanty.ui.game.entities.Ship;
import org.astropanty.ui.game.entities.Wall;
import org.astropanty.ui.game.screens.GameProper;
import org.astropanty.ui.navigation.ScreenController;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class NetworkGameTimer extends AnimationTimer {
    private final GraphicsContext gc;
    private final Scene scene;
    private final Ship[] ships;
    private final List<Wall> walls;
    private final GameClient client;
    private final Set<KeyCode> activeKeys = new HashSet<>();
    private final int[] currentShipTypes = { -1, -1, -1, -1 };
    private final long startTime;

    private final ScreenController screenController;
    private final Runnable navigateToMenu;
    private final int selectedShipId;

    // Chat state
    private boolean isChatting = false;
    private boolean openingSlashHeld = false;
    private String lastKeyPressedTextAppended = "";
    private StringBuilder currentChatInput = new StringBuilder();

    public NetworkGameTimer(GraphicsContext gc, Scene scene, Ship[] ships, List<Wall> walls, Runnable navigateToMenu,
            ScreenController screenController, GameClient client, int selectedShipId) {
        this.startTime = System.nanoTime();
        this.gc = gc;
        this.scene = scene;
        this.ships = ships;
        this.walls = walls;
        this.client = client;
        this.selectedShipId = selectedShipId;
        this.setupKeyHandlers();
        this.screenController = screenController;
        this.navigateToMenu = navigateToMenu;
    }

    private void renderWalls() {
        for (Wall wall : walls) {
            wall.render(gc);
        }
    }

    public void startRace() {
        // Disabled for network architecture
    }

    private void setupKeyHandlers() {
        scene.setOnKeyPressed(this::handleKeyPressed);
        scene.setOnKeyTyped(this::handleKeyTyped);
        scene.setOnKeyReleased(e -> {
            activeKeys.remove(e.getCode());
            if (e.getCode() == KeyCode.SLASH) {
                openingSlashHeld = false;
            }
        });
    }

    private void handleKeyPressed(KeyEvent e) {
        if (isChatting) {
            if (e.getCode() == KeyCode.ENTER && !e.isConsumed()) {
                // Send the chat message on first ENTER press only (ignore key-repeat)
                if (!e.isShiftDown()) {
                    String msg = currentChatInput.toString().trim();
                    if (!msg.isEmpty()) {
                        client.queueChatMessage(msg);
                    }
                    currentChatInput.setLength(0);
                    isChatting = false;
                    lastKeyPressedTextAppended = "";
                    activeKeys.remove(KeyCode.ENTER);
                }
            } else if (e.getCode() == KeyCode.ESCAPE) {
                currentChatInput.setLength(0);
                isChatting = false;
                lastKeyPressedTextAppended = "";
            } else if (e.getCode() == KeyCode.BACK_SPACE) {
                if (currentChatInput.length() > 0) {
                    currentChatInput.deleteCharAt(currentChatInput.length() - 1);
                }
                lastKeyPressedTextAppended = "";
            } else if (e.getCode() == KeyCode.SLASH && openingSlashHeld) {
                lastKeyPressedTextAppended = "";
            } else {
                lastKeyPressedTextAppended = appendPrintableText(e.getText());
            }
            e.consume();
        } else {
            if (e.getCode() == KeyCode.SLASH) {
                isChatting = true;
                openingSlashHeld = true;
                lastKeyPressedTextAppended = "";
                currentChatInput.setLength(0);
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                client.disconnect();
                this.stop();
                Platform.runLater(navigateToMenu);
                e.consume();
            } else {
                activeKeys.add(e.getCode());
            }
        }
    }

    private void handleKeyTyped(KeyEvent e) {
        if (!isChatting) {
            return;
        }

        String typed = e.getCharacter();
        if (typed == null || typed.isEmpty()) {
            e.consume();
            return;
        }

        if (openingSlashHeld && "/".equals(typed)) {
            e.consume();
            return;
        }

        if (typed.equals(lastKeyPressedTextAppended)) {
            lastKeyPressedTextAppended = "";
            e.consume();
            return;
        }

        appendPrintableText(typed);
        lastKeyPressedTextAppended = "";
        e.consume();
    }

    private String appendPrintableText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder appended = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (!Character.isISOControl(ch)) {
                currentChatInput.append(ch);
                appended.append(ch);
            }
        }
        return appended.toString();
    }

    private void renderHealthBar(Ship ship, double x, double y, double width, int healthOverride) {
        gc.setStroke(Color.WHITE);
        gc.strokeRect(x, y, width, 32);
        gc.setFill(Color.RED);
        gc.fillRect(x, y + 1, healthOverride, 30);
    }

    private void renderPhaseOverlay(GameStatePayload state) {
        if (state == null) return;
        int phase = state.gamePhase;

        if (phase == 0) { // WAITING_LOBBY
            int connected = 0;
            for (boolean c : state.connected) if (c) connected++;
            drawCenteredBanner(Color.color(0,0,0,0.65), Color.CYAN, FontWeight.BOLD, 40,
                    "WAITING IN LOBBY (" + connected + "/2)",
                    Color.LIGHTGRAY, 18, "Waiting for another player to join...");

        } else if (phase == 1) { // COUNTDOWN
            long secs = (state.phaseTimeRemaining / 1000) + 1;
            drawCenteredBanner(Color.color(0,0,0,0.65), Color.YELLOW, FontWeight.BOLD, 52,
                    "MATCH STARTING IN " + secs,
                    Color.LIGHTGRAY, 18, "Get ready!");

        } else if (phase == 2) { // PLAYING — show YOU DIED if local player is dead
            if (client.myPlayerId != -1 && state.healths[client.myPlayerId] <= 0) {
                drawCenteredBanner(Color.color(0,0,0,0.6), Color.RED, FontWeight.BOLD, 56,
                        "YOU DIED",
                        Color.LIGHTGRAY, 16, "Press / to chat or wait for the match to end");
            }

        } else if (phase == 3) { // GAME_OVER
            String winnerLine;
            if (state.winnerId == -1) {
                winnerLine = "DRAW!";
            } else {
                String name = (state.playerNames != null && state.playerNames[state.winnerId] != null)
                        ? state.playerNames[state.winnerId] : "Player " + (state.winnerId + 1);
                winnerLine = "WINNER: " + name;
            }
            drawCenteredBanner(Color.color(0,0,0,0.7), Color.GOLD, FontWeight.BOLD, 52,
                    winnerLine,
                    Color.LIGHTGRAY, 18, "Next match starting soon...");
        }
    }

    private void drawCenteredBanner(Color bgColor, Color titleColor, FontWeight titleWeight,
            double titleSize, String title, Color subtitleColor, double subtitleSize, String subtitle) {
        double w = GameProper.WINDOW_WIDTH;
        double h = GameProper.WINDOW_HEIGHT;
        gc.setFill(bgColor);
        gc.fillRect(0, h / 2.0 - 60, w, 120);

        gc.setFont(Font.font("Orbitron", titleWeight, titleSize));
        gc.setFill(titleColor);
        // Approximate centering: Orbitron glyph width ~0.6x font size
        double tx = (w - titleSize * 0.6 * title.length()) / 2.0;
        gc.fillText(title, tx, h / 2.0 + 10);

        gc.setFont(Font.font("Orbitron", subtitleSize));
        gc.setFill(subtitleColor);
        double sx = (w - subtitleSize * subtitle.length() * 0.55) / 2.0;
        gc.fillText(subtitle, sx, h / 2.0 + 10 + titleSize * 0.7);
    }

    private void renderChatLog(GameStatePayload state) {
        gc.save();
        gc.setGlobalAlpha(1.0);
        gc.setFont(Font.font("Monospaced", FontWeight.NORMAL, 13));

        double chatX = 10;
        double chatBaseY = GameProper.WINDOW_HEIGHT - 20;

        // Draw semi-transparent background for chat area
        if (isChatting || hasChatMessages(state)) {
            int msgCount = countChatMessages(state) + (isChatting ? 1 : 0);
            double bgHeight = msgCount * 18 + 10;
            gc.setFill(Color.color(0, 0, 0, 0.5));
            gc.fillRect(chatX - 4, chatBaseY - bgHeight + 8, 560, bgHeight + 8);
        }

        // Render newest server message closest to the input line.
        if (state != null && state.recentChats != null) {
            int line = 0;
            double messageBaseY = chatBaseY - (isChatting ? 22 : 4);
            for (int i = 0; i < state.recentChats.length; i++) {
                String chat = state.recentChats[i];
                if (chat != null && !chat.isBlank()) {
                    gc.setFill(Color.WHITE);
                    gc.fillText(chat, chatX, messageBaseY - (line * 18));
                    line++;
                }
            }
        }

        // Render current typing input
        if (isChatting) {
            gc.setFill(Color.CYAN);
            gc.fillText("> " + currentChatInput.toString() + "_", chatX, chatBaseY);
        }
        gc.restore();
    }

    private boolean hasChatMessages(GameStatePayload state) {
        if (state == null || state.recentChats == null)
            return false;
        for (String s : state.recentChats) {
            if (s != null && !s.isBlank())
                return true;
        }
        return false;
    }

    private int countChatMessages(GameStatePayload state) {
        if (state == null || state.recentChats == null)
            return 0;
        int count = 0;
        for (String s : state.recentChats) {
            if (s != null && !s.isBlank())
                count++;
        }
        return count;
    }

    @Override
    public void handle(long currentNanoTime) {
        gc.setFont(Font.font("Orbitron", 20));
        long currentSecond = (System.nanoTime() - this.startTime) / 1_000_000_000;

        gc.clearRect(0, 0, GameProper.WINDOW_WIDTH, GameProper.WINDOW_HEIGHT);
        renderWalls();

        GameStatePayload state = client.latestState;
        if (state != null) {
            for (int i = 0; i < 4; i++) {
                if (!state.connected[i])
                    continue;

                if (state.shipTypes[i] != currentShipTypes[i]) {
                    org.astropanty.data.ShipRepository.ShipAttributes attrs = org.astropanty.data.ShipRepository
                            .getShipAttributes(state.shipTypes[i]);
                    ships[i].setImage(new javafx.scene.image.Image(attrs.getShipImagePath(), 33, 42, false, false));
                    currentShipTypes[i] = state.shipTypes[i];
                }

                ships[i].setXPos(state.shipXs[i]);
                ships[i].setYPos(state.shipYs[i]);
                ships[i].setRotation(state.rotations[i]);
            }
        }

        // Only send movement input when NOT chatting and alive
        if (client.myPlayerId != -1) {
            boolean fwd = false, left = false, right = false, shoot = false;
            if (!isChatting && (state == null || state.healths[client.myPlayerId] > 0)) {
                fwd = activeKeys.contains(KeyCode.W) || activeKeys.contains(KeyCode.UP);
                left = activeKeys.contains(KeyCode.A) || activeKeys.contains(KeyCode.LEFT);
                right = activeKeys.contains(KeyCode.D) || activeKeys.contains(KeyCode.RIGHT);
                shoot = activeKeys.contains(KeyCode.SPACE) || activeKeys.contains(KeyCode.ENTER);
            }
            client.sendInput(new InputPayload(client.myPlayerId, selectedShipId, fwd, left, right, shoot));
        }

        // Render ships
        if (state != null) {
            for (int i = 0; i < 4; i++) {
                if (state.connected[i] && state.healths[i] > 0) {
                    ships[i].render(gc);
                }
            }
        } else {
            for (Ship s : ships)
                s.render(gc);
        }

        if (state != null) {
            gc.setFill(Color.YELLOW);
            for (int i = 0; i < 20; i++) {
                if (state.projActive[i]) {
                    gc.fillOval(state.projXs[i], state.projYs[i], 10, 10);
                }
            }

            int[] xOffsets = { 20, 260, 500, 740 };
            gc.setFont(Font.font("Orbitron", 12));
            for (int i = 0; i < 4; i++) {
                if (state.connected[i]) {
                    // Render player name above health bar
                    String name = (state.playerNames != null && state.playerNames[i] != null)
                            ? state.playerNames[i]
                            : "Player " + (i + 1);
                    gc.setFill(Color.WHITE);
                    gc.fillText(name, xOffsets[i], 16);
                    renderHealthBar(ships[i], xOffsets[i], 20, 100, state.healths[i]);
                }
            }
        }

        // Render phase overlay (lobby / countdown / you died / winner)
        renderPhaseOverlay(state);

        // Render chat log and input
        renderChatLog(state);
    }
}

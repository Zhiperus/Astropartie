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
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

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
        scene.setOnKeyPressed(e -> activeKeys.add(e.getCode()));
        scene.setOnKeyReleased(e -> activeKeys.remove(e.getCode()));
    }

    public void checkWinner() {
        if ((System.nanoTime() - this.startTime) / 1_000_000_000 > 119) {
            this.stop();
            Platform.runLater(navigateToMenu);
        }
    }

    private void renderHealthBar(Ship ship, double x, double y, double width, int healthOverride) {
        gc.setStroke(Color.WHITE);
        gc.strokeRect(x, y, width, 32);
        gc.setFill(Color.RED);
        gc.fillRect(x, y + 1, healthOverride, 30);
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

        if (client.myPlayerId != -1) {
            boolean fwd = activeKeys.contains(KeyCode.W) || activeKeys.contains(KeyCode.UP);
            boolean left = activeKeys.contains(KeyCode.A) || activeKeys.contains(KeyCode.LEFT);
            boolean right = activeKeys.contains(KeyCode.D) || activeKeys.contains(KeyCode.RIGHT);
            boolean shoot = activeKeys.contains(KeyCode.SPACE) || activeKeys.contains(KeyCode.ENTER);
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
            for (int i = 0; i < 4; i++) {
                if (state.connected[i]) {
                    renderHealthBar(ships[i], xOffsets[i], 20, 100, state.healths[i]);
                }
            }
        }
        gc.strokeText(currentSecond / 60 + " : " + ((currentSecond % 59 < 10) ? "0" : "") + currentSecond % 59,
                (GameProper.WINDOW_WIDTH / 2) - 22, 45);

        checkWinner();
    }
}

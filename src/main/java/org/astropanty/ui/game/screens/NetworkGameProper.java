package org.astropanty.ui.game.screens;

import java.util.List;

import org.astropanty.App;
import org.astropanty.data.MapLayouts;
import org.astropanty.data.ShipRepository;
import org.astropanty.net.GameClient;
import org.astropanty.ui.game.entities.Ship;
import org.astropanty.ui.game.entities.Wall;
import org.astropanty.ui.game.logic.NetworkGameTimer;
import org.astropanty.ui.navigation.Screen;
import org.astropanty.ui.navigation.ScreenController;

import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;

public class NetworkGameProper implements Screen {

    private Scene scene;
    private Group root;
    private Canvas canvas;

    public final static int WINDOW_WIDTH = App.WIDTH;
    public final static int WINDOW_HEIGHT = App.HEIGHT;

    private final ScreenController screenController;
    private final Runnable navigateToMenu;
    private final int selectedShipId;

    public NetworkGameProper(Runnable navigateToMenu, ScreenController screenController, int selectedShipId) {
        this.screenController = screenController;
        this.navigateToMenu = navigateToMenu;
        this.selectedShipId = selectedShipId;
    }

    @Override
    public Scene content() {
        this.root = new Group();
        this.scene = getBackgroundWithContent(root); // starry star background
        this.canvas = new Canvas(App.WIDTH, App.HEIGHT);
        this.root.getChildren().add(this.canvas);

        GraphicsContext gc = this.canvas.getGraphicsContext2D();

        GameClient client = new GameClient();
        client.connect("localhost", 8080);

        ShipRepository.ShipAttributes attrs = ShipRepository.getShipAttributes(0);

        Ship[] ships = new Ship[4];
        for (int i = 0; i < 4; i++) {
            ships[i] = new Ship(WINDOW_WIDTH * 0.10, WINDOW_HEIGHT * 0.20, "Player " + (i + 1),
                    new Image(attrs.getShipImagePath(), 33, 42, false, false),
                    attrs.getShipSpeed(), attrs.getBulletSpeed(), attrs.getBulletDamage(),
                    attrs.getBulletImagePath());
        }

        // Multiplayer strictly uses Map1 for now
        List<Wall> selectedMap = MapLayouts.getMap1Wall();

        NetworkGameTimer gameTimer = new NetworkGameTimer(gc, scene, ships, selectedMap, navigateToMenu,
                screenController, client, selectedShipId);
        gameTimer.start();
        gameTimer.startRace();

        return this.scene;
    }
}

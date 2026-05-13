package org.astropanty;

import java.io.IOException;

import org.astropanty.ui.game.Game;
import org.astropanty.ui.home.Home;
import org.astropanty.ui.navigation.Screen;
import org.astropanty.ui.navigation.ScreenController;

import javafx.application.Application;
import javafx.stage.Stage;

public class App extends Application {
    public static final int WIDTH = 960;
    public static final int HEIGHT = 540;

    private ScreenController screenController;

    private Screen homeScreen;
    private Screen gameScreen;

    @Override
    public void start(Stage stage) throws IOException {
        screenController = new ScreenController(stage);

        homeScreen = new Home(() -> screenController.navigate(gameScreen), screenController);
        gameScreen = new Game(() -> screenController.navigate(homeScreen), screenController);

        stage.setTitle("Pulsar");
        screenController.navigate(homeScreen);
    }

    public static void main(String[] args) {
        launch();
    }
}
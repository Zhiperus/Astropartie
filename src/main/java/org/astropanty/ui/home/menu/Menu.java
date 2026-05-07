package org.astropanty.ui.home.menu;

import org.astropanty.ui.components.Button;
import org.astropanty.ui.navigation.Screen;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

public class Menu implements Screen {
    private final Runnable navigateToGame;
    private final Runnable navigateToAbout;
    private final Runnable navigateToCredits;
    private final Runnable exitGame;

    public Menu(Runnable navigateToGame, Runnable navigateToAbout, Runnable navigateToCredits, Runnable exitGame) {
        this.navigateToGame = navigateToGame;
        this.navigateToAbout = navigateToAbout;
        this.navigateToCredits = navigateToCredits;
        this.exitGame = exitGame;
    }

    @Override
    public Scene content() {
        // Title
        Text title = new Text("Pulsar");
        title.setStyle("-fx-font-size: 48px; -fx-font-weight: bold; -fx-fill: white;");
        title.setTextAlignment(TextAlignment.CENTER);
        title.setFont(Font.font("Orbitron", 48)); // Space-style font

        // Buttons
        Button playButton = new Button("Play Game", navigateToGame);
        Button aboutButton = new Button("About", navigateToAbout);
        Button creditsButton = new Button("Credits", navigateToCredits);
        Button exitButton = new Button("Exit", exitGame);

        // Layout for buttons
        VBox layout = new VBox(20, title, playButton, aboutButton, creditsButton, exitButton);
        layout.setAlignment(Pos.CENTER);

        return getBackgroundWithContent(layout);
    }
}
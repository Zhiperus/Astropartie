package org.astropanty.ui.home.credits;

import org.astropanty.ui.components.Button;
import org.astropanty.ui.navigation.Screen;

import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

public class Credits implements Screen {
    private final Runnable navigateToMenu;

    public Credits(Runnable navigateToMenu) {
        this.navigateToMenu = navigateToMenu;
    }

    @Override
    public Scene content() {
        Text title = new Text("Credits");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-alignment: center; -fx-fill: white;");

        Text creditsText = new Text(
                "Game Design: Bossing Inc.\n" +
                        "Programming: Iorie Alen Chua\n\t\t\t\tEunel Jacob Joyosa\n\t\t\t\tRoberto Neil Santos\n\t\t\t\tHan Caleb Castro\n\t\t\t\tPaul Hadley Fababeir\n" +
                        "Art and Graphics: Eunel Jacob Joyosa\n" +
                        "Inspired by: Astro Duel Franchise\n\n" +
                        "Thank you for playing our game!");
        creditsText.setStyle("-fx-font-size: 16px; -fx-text-alignment: center; -fx-fill: white;");

        Button backButton = new Button("Back to Menu", navigateToMenu);

        VBox layout = new VBox(20, title, creditsText, backButton);
        layout.setStyle("-fx-alignment: center; -fx-padding: 50;");

        return getBackgroundWithContent(layout);
    }

}

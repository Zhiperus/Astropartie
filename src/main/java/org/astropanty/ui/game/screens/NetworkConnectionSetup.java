package org.astropanty.ui.game.screens;

import java.util.function.BiConsumer;

import org.astropanty.ui.components.Button;
import org.astropanty.ui.navigation.Screen;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

public class NetworkConnectionSetup implements Screen {
    private final Runnable navigateToMenu;
    private final BiConsumer<String, String> onProceed; // (playerName, ipAddress)

    public NetworkConnectionSetup(Runnable navigateToMenu, BiConsumer<String, String> onProceed) {
        this.navigateToMenu = navigateToMenu;
        this.onProceed = onProceed;
    }

    @Override
    public Scene content() {
        // Title
        Text title = new Text("Online Multiplayer");
        title.setStyle("-fx-font-size: 32px; -fx-font-weight: bold; -fx-fill: white;");
        title.setFont(Font.font("Orbitron", 32));

        // Player Name Field
        Text nameLabel = new Text("Player Name");
        nameLabel.setStyle("-fx-font-size: 16px; -fx-fill: #cccccc;");
        TextField nameField = new TextField();
        nameField.setPromptText("Enter your name...");
        nameField.setMaxWidth(300);
        nameField.setStyle(
                "-fx-background-color: #1a1a2e; -fx-text-fill: white; -fx-prompt-text-fill: #666666; " +
                        "-fx-border-color: #4a4a6a; -fx-border-radius: 5; -fx-background-radius: 5; " +
                        "-fx-padding: 8; -fx-font-size: 14px;");
        VBox nameBox = new VBox(8, nameLabel, nameField);
        nameBox.setAlignment(Pos.CENTER);

        // Server IP Field
        Text ipLabel = new Text("Server IP Address");
        ipLabel.setStyle("-fx-font-size: 16px; -fx-fill: #cccccc;");
        TextField ipField = new TextField("localhost");
        ipField.setPromptText("e.g. 192.168.1.5");
        ipField.setMaxWidth(300);
        ipField.setStyle(
                "-fx-background-color: #1a1a2e; -fx-text-fill: white; -fx-prompt-text-fill: #666666; " +
                        "-fx-border-color: #4a4a6a; -fx-border-radius: 5; -fx-background-radius: 5; " +
                        "-fx-padding: 8; -fx-font-size: 14px;");
        VBox ipBox = new VBox(8, ipLabel, ipField);
        ipBox.setAlignment(Pos.CENTER);

        // Error text (initially hidden)
        Text errorText = new Text();
        errorText.setStyle("-fx-font-size: 14px; -fx-fill: #ff4444;");

        // Action Buttons
        Button backButton = new Button("Back", navigateToMenu);
        Button proceedButton = new Button("Next", () -> {
            String name = nameField.getText().trim();
            String ip = ipField.getText().trim();
            if (name.isEmpty()) {
                errorText.setText("Please enter a player name.");
                return;
            }
            if (ip.isEmpty()) {
                errorText.setText("Please enter a server IP address.");
                return;
            }
            onProceed.accept(name, ip);
        });

        HBox actionButtons = new HBox(20, backButton, proceedButton);
        actionButtons.setAlignment(Pos.CENTER);

        VBox mainLayout = new VBox(30, title, nameBox, ipBox, errorText, actionButtons);
        mainLayout.setPadding(new Insets(40));
        mainLayout.setAlignment(Pos.CENTER);

        return getBackgroundWithContent(mainLayout);
    }
}

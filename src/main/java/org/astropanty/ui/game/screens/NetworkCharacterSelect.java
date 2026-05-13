package org.astropanty.ui.game.screens;

import java.util.List;
import java.util.function.Consumer;

import org.astropanty.data.ShipRepository;
import org.astropanty.ui.components.Button;
import org.astropanty.ui.navigation.Screen;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

public class NetworkCharacterSelect implements Screen {
    private final Runnable navigateToMenu;
    private final Consumer<Integer> connectToLobby;
    private final String playerName;
    private final String ipAddress;
    private int selectedSelection = -1;
    private final ImageView mySelection = new ImageView();

    public NetworkCharacterSelect(Runnable navigateToMenu, Consumer<Integer> connectToLobby,
            String playerName, String ipAddress) {
        this.navigateToMenu = navigateToMenu;
        this.connectToLobby = connectToLobby;
        this.playerName = playerName;
        this.ipAddress = ipAddress;

        mySelection.setFitWidth(100);
        mySelection.setFitHeight(100);
        mySelection.setPreserveRatio(false);
        mySelection.setSmooth(false);
        mySelection.setImage(null);
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    @Override
    public Scene content() {
        Text title = new Text("Select Your Multiplayer Ship");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-fill: white;");

        Text nameDisplay = new Text("Playing as: " + playerName + " | Server: " + ipAddress);
        nameDisplay.setStyle("-fx-font-size: 14px; -fx-fill: #aaaaaa;");

        HBox shipSelection = createShipSelectionButtons();

        Text playerTitle = new Text("My Ship");
        playerTitle.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-fill: white;");
        VBox playerPanel = new VBox(20, playerTitle, mySelection);
        playerPanel.setAlignment(Pos.CENTER);
        playerPanel.setPrefSize(400, 400);
        playerPanel.setStyle("-fx-border-color: white; -fx-padding: 10;");

        Button backButton = new Button("Back to Menu", navigateToMenu);
        Button startButton = new Button("Connect", () -> {
            if (selectedSelection != -1) {
                connectToLobby.accept(selectedSelection);
            }
        });
        HBox actionButtons = new HBox(20, backButton, startButton);
        actionButtons.setAlignment(Pos.CENTER);

        VBox mainLayout = new VBox(30, title, nameDisplay, playerPanel, shipSelection, actionButtons);
        mainLayout.setPadding(new Insets(20));
        mainLayout.setAlignment(Pos.CENTER);

        return getBackgroundWithContent(mainLayout);
    }

    private HBox createShipSelectionButtons() {
        HBox shipButtons = new HBox(20);
        shipButtons.setAlignment(Pos.CENTER);

        List<String> shipImages = ShipRepository.getAllShipImages();
        for (int i = 0; i < shipImages.size(); i++) {
            int shipId = i;
            String shipImagePath = shipImages.get(shipId);

            ImageView shipImageView = new ImageView(new Image(shipImagePath));
            shipImageView.setFitWidth(50);
            shipImageView.setFitHeight(50);
            shipImageView.setPreserveRatio(true);

            javafx.scene.control.Button shipButton = new javafx.scene.control.Button();
            shipButton.setGraphic(shipImageView);
            shipButton.setStyle("-fx-background-color: transparent; -fx-border-width: 0;");

            shipButton.setOnAction(event -> {
                mySelection.setImage(new Image(shipImagePath));
                selectedSelection = shipId;
            });
            shipButtons.getChildren().add(shipButton);
        }

        return shipButtons;
    }
}


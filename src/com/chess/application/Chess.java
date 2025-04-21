package com.chess.application;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.stage.Stage;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

public class Chess extends Application {

	private SettingsController controller;
	private static final Logger LOG = Logger.getLogger(Chess.class.getName());

	public void getSettingsScreen(Stage stage) {
		try {
			System.out.println("Looking for FXML...");
			var fxmlUrl = Chess.class.getClassLoader().getResource("com/chess/resources/SettingsFrame.fxml");
			if (fxmlUrl == null) {
				System.err.println("FXML file not found!");
				return;
			} else {
				System.out.println("FXML found: " + fxmlUrl);
			}

			FXMLLoader loadFrame = new FXMLLoader(fxmlUrl);
			Parent mainRoot = loadFrame.load();
			System.out.println("FXML loaded");

			Scene scene = new Scene(mainRoot, 550, 630);

			System.out.println("Looking for CSS...");
			var cssUrl = Chess.class.getClassLoader().getResource("com/chess/resources/application.css");
			if (cssUrl != null) {
				System.out.println("CSS found: " + cssUrl);
				scene.getStylesheets().add(cssUrl.toExternalForm());
			} else {
				System.err.println("CSS file not found!");
			}

			if (stage == null) {
				stage = new Stage();
				stage.setMinWidth(566);
				stage.setMinHeight(669);
			}

			stage.setScene(scene);
			stage.setTitle("Online Chess System");

			System.out.println("Getting controller...");
			controller = loadFrame.getController();
			controller.setStage(stage);
			controller.setMainAccess(this);

			scene.setFill(Color.TRANSPARENT);

			System.out.println("Loading icon...");
			try {
				Image icon = new Image("com/chess/resources/img/pawn_b.png");
				stage.getIcons().add(icon);
				System.out.println("Icon loaded");
			} catch (Exception e) {
				System.err.println("Icon failed to load: " + e.getMessage());
			}

			stage.show();
			System.out.println("Stage shown");

		} catch (Exception e) {
			System.err.println("Error while loading UI:");
			e.printStackTrace();
		}
	}

	@Override
	public void start(Stage stage) {
		System.out.println("Starting Chess App...");
		try {
			getSettingsScreen(stage);
		} catch (Exception e) {
			System.err.println("Exception during start():");
			e.printStackTrace();
		}
	}

	@Override
	public void stop() throws Exception {
		super.stop();
		if (controller != null) {
			controller.handleExit(null);
		}
		Platform.exit();
		System.exit(0);
	}

	public static void main(String[] args) {
		launch(args);
	}
}

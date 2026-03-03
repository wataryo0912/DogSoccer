package ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import model.Club;
import service.ExcelLoaderService;

/**
 * 仕様書の「スケジュール確認画面」に対応するシンプルな画面。
 */
public class ScheduleScreenView extends VBox {

    public ScheduleScreenView(MainApp app) {
        setStyle("-fx-background-color:#08081a;");
        setSpacing(0);
        getChildren().add(buildHeader(app));

        ScheduleView sv = new ScheduleView(MainApp.season, MainApp.playerClub.getName());
        VBox.setVgrow(sv, Priority.ALWAYS);
        getChildren().add(sv);
    }

    private HBox buildHeader(MainApp app) {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(12, 16, 12, 16));
        bar.setStyle("-fx-background-color:#0d0d22;"
            + "-fx-border-color:rgba(255,255,255,0.08);-fx-border-width:0 0 1 0;");

        Club club = MainApp.playerClub;

        ImageView emblem = new ImageView();
        emblem.setFitWidth(34);
        emblem.setFitHeight(34);
        emblem.setPreserveRatio(true);
        String emblemUrl = ExcelLoaderService.toImageURL(club.getUniformImageFile());
        if (emblemUrl != null) {
            try {
                emblem.setImage(new Image(emblemUrl));
            } catch (Exception ignored) {
                emblem.setImage(null);
            }
        }

        Label title = new Label("📋 スケジュール確認");
        title.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#ffffff;");

        Label clubLabel = new Label(club.getName());
        clubLabel.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#ffffff;");

        Label rankLabel = new Label("順位: " + MainApp.leagueManager.getRankText(club));
        rankLabel.setStyle("-fx-font-size:11px;-fx-text-fill:#ffd700;-fx-cursor:hand;");
        rankLabel.setOnMouseClicked(e -> app.showStandingsView());

        Label sub = new Label(MainApp.season.getWeekTitle());
        sub.setStyle("-fx-font-size:12px;-fx-text-fill:rgba(255,255,255,0.65);");

        VBox infoBox = new VBox(2, title, clubLabel, rankLabel, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button rankBtn = new Button("順位表");
        rankBtn.setStyle("-fx-background-color:#1f2a46;-fx-text-fill:#ffffff;"
            + "-fx-font-size:11px;-fx-font-weight:bold;-fx-background-radius:8;"
            + "-fx-padding:5 12;-fx-cursor:hand;");
        rankBtn.setOnAction(e -> app.showStandingsView());

        Button backBtn = new Button("メインメニュー");
        backBtn.setStyle("-fx-background-color:#2d5a2f;-fx-text-fill:#ffffff;"
            + "-fx-font-size:11px;-fx-font-weight:bold;-fx-background-radius:8;"
            + "-fx-padding:5 12;-fx-cursor:hand;");
        backBtn.setOnAction(e -> app.showMainMenu());

        bar.getChildren().addAll(emblem, infoBox, spacer, rankBtn, backBtn);
        return bar;
    }
}

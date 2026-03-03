package ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import model.Player;
import model.Player.PlayerRole;
import service.ExcelLoaderService;

import java.util.List;

/**
 * チームメンバー一覧画面（専用ビュー）
 */
public class MemberListView extends VBox {

    private final MainApp app;

    public MemberListView(MainApp app) {
        this.app = app;
        setStyle("-fx-background-color:#08081a;");
        setSpacing(0);
        getChildren().addAll(buildHeader(), buildListArea());
    }

    private HBox buildHeader() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(12, 16, 12, 16));
        bar.setStyle("-fx-background-color:#0d0d22;"
            + "-fx-border-color:rgba(255,255,255,0.08);-fx-border-width:0 0 1 0;");

        Label title = new Label("📋 チームメンバー一覧");
        title.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#ffffff;");

        Label club = new Label(MainApp.playerClub.getName());
        club.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#ffffff;");

        Label rank = new Label("順位: " + MainApp.leagueManager.getRankText(MainApp.playerClub));
        rank.setStyle("-fx-font-size:11px;-fx-text-fill:#ffd700;-fx-cursor:hand;");
        rank.setOnMouseClicked(e -> app.showStandingsView());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button clubBtn = navBtn("クラブ管理へ", app::showClubView);
        Button backBtn = navBtn("メインメニュー", app::showMainMenu);

        bar.getChildren().addAll(title, club, rank, spacer, clubBtn, backBtn);
        return bar;
    }

    private ScrollPane buildListArea() {
        VBox list = new VBox(12);
        list.setPadding(new Insets(16));
        list.setStyle("-fx-background-color:#08081a;");

        for (PlayerRole role : PlayerRole.values()) {
            List<Player> players = MainApp.playerClub.getSquad().stream()
                .filter(p -> p.getRole() == role)
                .toList();
            if (!players.isEmpty()) {
                list.getChildren().add(buildRoleSection(role, players));
            }
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background:#08081a;-fx-background-color:#08081a;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return scroll;
    }

    private VBox buildRoleSection(PlayerRole role, List<Player> players) {
        VBox section = new VBox(8);

        Label head = new Label(role.label + " (" + players.size() + "名)");
        head.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:" + roleColor(role) + ";");
        section.getChildren().add(head);

        for (Player p : players) {
            section.getChildren().add(buildPlayerRow(p));
        }
        return section;
    }

    private VBox buildPlayerRow(Player p) {
        VBox row = new VBox(6);
        row.setPadding(new Insets(10, 12, 10, 12));
        row.setStyle("-fx-background-color:#16213E;-fx-background-radius:8;"
            + "-fx-border-color:rgba(255,255,255,0.06);-fx-border-radius:8;");

        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);

        ImageView iv = new ImageView();
        iv.setFitWidth(42);
        iv.setFitHeight(42);
        iv.setPreserveRatio(true);
        String imgUrl = ExcelLoaderService.toImageURL(p.getImageFile());
        if (imgUrl != null) {
            try {
                iv.setImage(new Image(imgUrl));
            } catch (Exception ignored) {
                iv.setImage(null);
            }
        }

        VBox meta = new VBox(2);
        Label name = new Label("#" + p.getShirtNumber() + "  " + p.getFullName());
        name.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#ffffff;");
        Label info = new Label(String.format(
            "%s  OVR:%d  年齢:%d  ロール:%s",
            p.getPosition(), p.getOverall(), p.getAge(), p.getRole().label));
        info.setStyle("-fx-font-size:11px;-fx-text-fill:rgba(255,255,255,0.72);");
        meta.getChildren().addAll(name, info);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label mood = new Label("気分: " + p.getEmotion().getMood().name());
        mood.setStyle("-fx-font-size:10px;-fx-text-fill:#ffd700;");

        top.getChildren().addAll(iv, meta, spacer, mood);

        HBox emotion = new HBox(8,
            statBar("自信", p.getEmotion().getConfidence(), "#4a7aff"),
            statBar("不満", p.getEmotion().getFrustration(), "#ff6b6b"),
            statBar("忠誠", p.getEmotion().getLoyalty(), "#ffd700"),
            statBar("対抗", p.getEmotion().getRivalry(), "#ff9a44")
        );
        emotion.setAlignment(Pos.CENTER_LEFT);

        row.getChildren().addAll(top, emotion);
        return row;
    }

    private VBox statBar(String name, int value, String color) {
        VBox box = new VBox(2);
        Label label = new Label(name + " " + value);
        label.setStyle("-fx-font-size:10px;-fx-text-fill:rgba(255,255,255,0.72);");

        ProgressBar pb = new ProgressBar(Math.max(0.0, Math.min(1.0, value / 100.0)));
        pb.setPrefWidth(120);
        pb.setPrefHeight(6);
        pb.setStyle("-fx-accent:" + color + ";");

        box.getChildren().addAll(label, pb);
        return box;
    }

    private String roleColor(PlayerRole role) {
        return switch (role) {
            case REGISTERED -> "#44ff88";
            case BENCH -> "#ffd700";
            case INACTIVE -> "#ff6666";
            case ACADEMY -> "#44aaff";
            case RETIRED -> "#aaaaaa";
        };
    }

    private Button navBtn(String text, Runnable action) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color:#1f2a46;-fx-text-fill:#ffffff;"
            + "-fx-font-size:11px;-fx-font-weight:bold;-fx-background-radius:8;"
            + "-fx-padding:5 12;-fx-cursor:hand;");
        btn.setOnAction(e -> action.run());
        return btn;
    }
}


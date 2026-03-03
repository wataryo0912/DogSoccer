package ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * 人事画面（専用ビュー）
 * スカウト・移籍機能への入口を提供する。
 */
public class PersonnelView extends VBox {

    private final MainApp app;

    public PersonnelView(MainApp app) {
        this.app = app;
        setStyle("-fx-background-color:#08081a;");
        setSpacing(0);
        getChildren().addAll(buildHeader(), buildBody());
    }

    private HBox buildHeader() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(12, 16, 12, 16));
        bar.setStyle("-fx-background-color:#0d0d22;"
            + "-fx-border-color:rgba(255,255,255,0.08);-fx-border-width:0 0 1 0;");

        int week = MainApp.season.getCurrentWeek();
        int month = ((week - 1) / 4) + 1;
        int weekInMonth = ((week - 1) % 4) + 1;

        Label title = new Label("👥 人事");
        title.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#ffffff;");

        Label date = new Label(String.format(
            "ゲーム内日付: %d年目:%d月:%d週",
            MainApp.season.getCurrentSeason(), month, weekInMonth));
        date.setStyle("-fx-font-size:12px;-fx-text-fill:rgba(255,255,255,0.7);");

        Label rank = new Label("順位: " + MainApp.leagueManager.getRankText(MainApp.playerClub));
        rank.setStyle("-fx-font-size:11px;-fx-text-fill:#ffd700;-fx-cursor:hand;");
        rank.setOnMouseClicked(e -> app.showStandingsView());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button scheduleBtn = navBtn("次週のスケジュール", app::showScheduleView);
        Button trainingBtn = navBtn("練習", app::showTrainingView);
        Button backBtn = navBtn("メインメニュー", app::showMainMenu);

        bar.getChildren().addAll(title, date, rank, spacer, scheduleBtn, trainingBtn, backBtn);
        return bar;
    }

    private VBox buildBody() {
        VBox body = new VBox(16);
        body.setPadding(new Insets(20));
        VBox.setVgrow(body, Priority.ALWAYS);

        boolean transferOpen = MainApp.season.isTransferWindowOpen();
        Label status = new Label(transferOpen
            ? "✅ 移籍ウィンドウ開放中: 獲得・放出が可能です"
            : "⏳ 移籍ウィンドウ外: 現在は閲覧・準備のみ可能です");
        status.setStyle("-fx-font-size:13px;-fx-text-fill:"
            + (transferOpen ? "#77dd77" : "#ffcc66") + ";");

        VBox card = new VBox(10);
        card.setPadding(new Insets(16));
        card.setStyle("-fx-background-color:#16213E;-fx-background-radius:10;"
            + "-fx-border-color:rgba(255,255,255,0.08);-fx-border-radius:10;");

        Label cardTitle = new Label("🔄 移籍・スカウト");
        cardTitle.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#ffffff;");
        Label desc = new Label(
            "選手の獲得・売却、下部組織の昇格申請、キャプテン変更を実行します。");
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size:12px;-fx-text-fill:rgba(255,255,255,0.75);");

        Button openScoutBtn = new Button("移籍・スカウト画面を開く");
        openScoutBtn.setStyle("-fx-background-color:#4a7a35;-fx-text-fill:#ffffff;"
            + "-fx-font-size:12px;-fx-font-weight:bold;-fx-padding:8 14;"
            + "-fx-background-radius:7;-fx-cursor:hand;");
        openScoutBtn.setOnAction(e -> app.setCenterView(new ScoutView(app)));

        card.getChildren().addAll(cardTitle, desc, openScoutBtn);
        body.getChildren().addAll(status, card);
        return body;
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


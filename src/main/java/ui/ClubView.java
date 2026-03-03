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
 * クラブ管理画面（専用ビュー）
 * チームメンバー確認・編成・スケジュール確認へのハブ。
 */
public class ClubView extends VBox {

    private final MainApp app;

    public ClubView(MainApp app) {
        this.app = app;
        setStyle("-fx-background-color:#08081a;");
        setSpacing(0);
        getChildren().addAll(buildHeader(), buildContent());
    }

    private HBox buildHeader() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(12, 16, 12, 16));
        bar.setStyle("-fx-background-color:#0d0d22;"
            + "-fx-border-color:rgba(255,255,255,0.08);-fx-border-width:0 0 1 0;");

        Label title = new Label("🏛️ クラブ管理");
        title.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#ffffff;");

        Label clubLabel = new Label(MainApp.playerClub.getName());
        clubLabel.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#ffffff;");

        Label rankLabel = new Label("順位: " + MainApp.leagueManager.getRankText(MainApp.playerClub));
        rankLabel.setStyle("-fx-font-size:11px;-fx-text-fill:#ffd700;-fx-cursor:hand;");
        rankLabel.setOnMouseClicked(e -> app.showStandingsView());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button scheduleBtn = navBtn("次週のスケジュール", app::showScheduleView);
        Button backBtn = navBtn("メインメニュー", app::showMainMenu);

        bar.getChildren().addAll(title, clubLabel, rankLabel, spacer, scheduleBtn, backBtn);
        return bar;
    }

    private VBox buildContent() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(20));
        VBox.setVgrow(root, Priority.ALWAYS);

        root.getChildren().addAll(
            actionCard(
                "📋 チームメンバー一覧",
                "全選手（REGISTERED / BENCH / INACTIVE / ACADEMY）の詳細を確認します。",
                "チームメンバー一覧へ",
                app::showMemberListView,
                "#3a7bd5"
            ),
            actionCard(
                "🧠 編成・移籍リスト",
                "編成画面でスカッド構成の確認と移籍導線を利用します。",
                "編成画面へ",
                app::showSquadView,
                "#5cab6f"
            ),
            actionCard(
                "👥 人事（スカウト）",
                "スカウト・売却・下部組織昇格などの人事操作を行います。",
                "人事画面へ",
                app::showPersonnelView,
                "#f08a24"
            ),
            actionCard(
                "💰 財務",
                "予算・週給の確認と給与処理を行います。",
                "財務画面へ",
                () -> app.setCenterView(new FinanceView(app)),
                "#8a7df0"
            )
        );
        return root;
    }

    private VBox actionCard(String title, String desc, String btnText, Runnable action, String color) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color:#16213E;-fx-background-radius:10;"
            + "-fx-border-color:rgba(255,255,255,0.08);-fx-border-radius:10;");

        Label t = new Label(title);
        t.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#ffffff;");
        Label d = new Label(desc);
        d.setWrapText(true);
        d.setStyle("-fx-font-size:12px;-fx-text-fill:rgba(255,255,255,0.75);");

        Button b = new Button(btnText);
        b.setStyle("-fx-background-color:" + color + ";-fx-text-fill:#ffffff;"
            + "-fx-font-size:12px;-fx-font-weight:bold;-fx-padding:8 14;"
            + "-fx-background-radius:7;-fx-cursor:hand;");
        b.setOnAction(e -> action.run());

        card.getChildren().addAll(t, d, b);
        return card;
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


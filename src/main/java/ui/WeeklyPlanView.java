package ui;

// ※ SeasonEndView は同じ ui パッケージなので import 不要
import javafx.animation.*;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.util.Duration;
import model.*;
import model.SeasonManager.*;
import service.*;

import java.util.List;

/**
 * 週次計画ビュー
 * ・試合週は自動で試合画面に遷移
 * ・右側にシーズンスケジュール常時表示
 */
public class WeeklyPlanView extends VBox {

    /** 新シーズン開始時（week=1）に一度だけ実行されるシーズン開始処理 */
    private void onNewSeasonStart(int newSeason) {
        // ── 転生チェック（新シーズンで転生対象が来たか） ──
        java.util.List<service.RetirementService.ReincarnationResult> reincarnations =
            MainApp.retirementService.processReincarnations(MainApp.allClubs, newSeason);
        if (!reincarnations.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (service.RetirementService.ReincarnationResult r : reincarnations) {
                sb.append(r.toMessage()).append("\n\n");
            }
            MainApp.gameDataService.saveSquad(MainApp.playerClub);
            javafx.application.Platform.runLater(() ->
                showInfo("✨ 転生！", sb.toString().trim()));
        }

        // ── 引退チェック（今シーズン引退表明する選手） ──
        java.util.List<service.RetirementService.RetirementAnnouncement> announcements =
            MainApp.retirementService.checkRetirements(MainApp.allClubs, newSeason);
        if (!announcements.isEmpty()) {
            // 自クラブの引退表明を先に、他クラブは後ろにまとめる
            java.util.List<service.RetirementService.RetirementAnnouncement> mine =
                announcements.stream()
                    .filter(a -> a.club().getName().equals(MainApp.playerClub.getName()))
                    .toList();
            java.util.List<service.RetirementService.RetirementAnnouncement> others =
                announcements.stream()
                    .filter(a -> !a.club().getName().equals(MainApp.playerClub.getName()))
                    .toList();

            if (!mine.isEmpty()) {
                String myMsg = mine.stream()
                    .map(service.RetirementService.RetirementAnnouncement::toMessage)
                    .reduce("", (a, b) -> a + b + "\n\n").trim();
                javafx.application.Platform.runLater(() ->
                    showInfo("🐾 引退表明（あなたのクラブ）", myMsg));
            }
            if (!others.isEmpty()) {
                String otherMsg = "他クラブでも引退表明がありました:\n\n"
                    + others.stream()
                        .map(service.RetirementService.RetirementAnnouncement::toShortMessage)
                        .reduce("", (a, b) -> a + "• " + b + "\n").trim();
                javafx.application.Platform.runLater(() ->
                    showInfo("🐾 引退表明（リーグ全体）", otherMsg));
            }
        }
    }

    private final MainApp       app;
    private final SeasonManager season;

    private Label  weekLabel, monthLabel, budgetLabel, standingLabel;
    private ProgressBar seasonBar;
    private Label  seasonBarLabel;
    private VBox   actionPanel, resultPanel;
    private Label  resultText;
    private Button advanceBtn;
    private FlowPane calendarPane;
    private VBox   eventPanel;        // 週次イベント表示パネル
    private Label  eventTitleLabel;
    private Label  eventDescLabel;
    private Label  eventEffectLabel;

    public WeeklyPlanView(MainApp app, SeasonManager season) {
        // シーズン開始フックを登録（引退チェック・転生処理）
        season.setOnSeasonStartHook(this::onNewSeasonStart);
        this.app    = app;
        this.season = season;
        setStyle("-fx-background-color:#06060f;");
        setSpacing(0);
        getChildren().addAll(buildTopBar(), buildMainArea(), buildCalendar());
        refresh();
    }

    // ─────────────────────────────────────────────────────────
    // トップバー
    // ─────────────────────────────────────────────────────────
    private HBox buildTopBar() {
        HBox bar = new HBox(16);
        bar.setStyle("-fx-background-color:#0a0a1a;-fx-padding:12 24;"
            + "-fx-border-color:rgba(255,200,0,0.12);-fx-border-width:0 0 1 0;");
        bar.setAlignment(Pos.CENTER_LEFT);

        weekLabel = new Label();
        weekLabel.setStyle("-fx-font-size:17px;-fx-font-weight:bold;-fx-text-fill:#fff;");
        monthLabel = new Label();
        monthLabel.setStyle("-fx-font-size:11px;-fx-text-fill:rgba(255,255,255,0.4);");
        VBox weekBox = new VBox(2, weekLabel, monthLabel);

        VBox barBox = new VBox(4);
        barBox.setPrefWidth(280);
        seasonBarLabel = new Label();
        seasonBarLabel.setStyle("-fx-font-size:10px;-fx-text-fill:rgba(255,255,255,0.3);");
        seasonBar = new ProgressBar(0);
        seasonBar.setPrefWidth(280); seasonBar.setPrefHeight(7);
        seasonBar.setStyle("-fx-accent:#4a7a35;");
        barBox.getChildren().addAll(seasonBarLabel, seasonBar);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        standingLabel = new Label();
        standingLabel.setStyle("-fx-font-size:12px;-fx-text-fill:#ffd700;-fx-cursor:hand;");
        standingLabel.setOnMouseClicked(e -> app.showStandingsView());
        budgetLabel = new Label();
        budgetLabel.setStyle("-fx-font-size:12px;-fx-text-fill:#a8dadc;");

        bar.getChildren().addAll(weekBox, barBox, spacer, standingLabel, budgetLabel);
        return bar;
    }

    // ─────────────────────────────────────────────────────────
    // メインエリア（左: アクション、右: スケジュール）
    // ─────────────────────────────────────────────────────────
    private HBox buildMainArea() {
        HBox area = new HBox(0);
        area.setPrefHeight(400);
        area.getChildren().addAll(buildActionPanel(), buildSchedulePanel());
        HBox.setHgrow(area.getChildren().get(1), Priority.ALWAYS);
        return area;
    }

    private VBox buildActionPanel() {
        VBox panel = new VBox(8);
        panel.setPrefWidth(280);
        panel.setMinWidth(280);
        panel.setStyle("-fx-background-color:#0d0d22;-fx-padding:20;"
            + "-fx-border-color:rgba(255,255,255,0.06);-fx-border-width:0 1 0 0;");

        Label title = new Label("メニュー");
        title.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:rgba(255,255,255,0.55);");

        // 試合週バナー（試合週のみ表示、index=1 で updateMatchWeekBanner から参照）
        Label matchWeekBanner = new Label();
        matchWeekBanner.setStyle("""
            -fx-background-color:#1a3020;-fx-text-fill:#4a7a35;
            -fx-font-size:12px;-fx-font-weight:bold;
            -fx-padding:8 12;-fx-background-radius:6;
            -fx-border-color:#4a7a35;-fx-border-width:1;-fx-border-radius:6;
        """);
        matchWeekBanner.setWrapText(true);
        matchWeekBanner.setManaged(false);
        matchWeekBanner.setVisible(false);

        // ── ナビゲーションボタン（仕様書通り 6ボタン）──────────
        Button scheduleBtn  = makeNavBtn("📅  次週のスケジュール",
            app::showScheduleView);
        Button trainingNavBtn = makeNavBtn("🏋️  練習",
            app::showTrainingView);
        Button personnelBtn = makeNavBtn("👥  人事",
            app::showPersonnelView);
        Button formationBtn = makeNavBtn("📋  編成",
            app::showSquadView);
        Button clubBtn      = makeNavBtn("🏠  クラブ管理",
            app::showClubView);

        // ── 週次イベントパネル ───────────────────────────────
        eventPanel = new VBox(4);
        eventPanel.setStyle("-fx-background-color:#0a0a1a;-fx-padding:10 12;"
            + "-fx-background-radius:7;"
            + "-fx-border-color:rgba(255,200,0,0.15);-fx-border-width:1;-fx-border-radius:7;");
        eventTitleLabel = new Label("─ 今週のイベント ─");
        eventTitleLabel.setStyle("-fx-font-size:11px;-fx-font-weight:bold;"
            + "-fx-text-fill:rgba(255,200,0,0.6);");
        eventDescLabel = new Label("");
        eventDescLabel.setStyle("-fx-font-size:11px;-fx-text-fill:rgba(255,255,255,0.55);"
            + "-fx-wrap-text:true;");
        eventDescLabel.setWrapText(true);
        eventEffectLabel = new Label("");
        eventEffectLabel.setStyle("-fx-font-size:11px;-fx-font-weight:bold;-fx-text-fill:#ffdd44;");
        eventPanel.getChildren().addAll(eventTitleLabel, eventDescLabel, eventEffectLabel);
        eventPanel.setVisible(true);

        // ── 日程進行ボタン ────────────────────────────────────
        advanceBtn = new Button("📅  日程進行");
        advanceBtn.setMaxWidth(Double.MAX_VALUE);
        advanceBtn.setStyle("-fx-background-color:#4a7a35;-fx-text-fill:white;"
            + "-fx-font-size:13px;-fx-font-weight:bold;"
            + "-fx-padding:11 18;-fx-background-radius:8;-fx-cursor:hand;");
        advanceBtn.setOnAction(e -> executeAdvance());

        resultPanel = new VBox(8);
        resultPanel.setVisible(false);
        resultText = new Label();
        resultText.setStyle("-fx-font-size:11px;-fx-text-fill:rgba(255,255,255,0.7);-fx-wrap-text:true;");
        resultText.setWrapText(true);
        resultPanel.getChildren().add(resultText);

        Region sp = new Region();
        VBox.setVgrow(sp, Priority.ALWAYS);
        panel.getChildren().addAll(title, matchWeekBanner, scheduleBtn, trainingNavBtn,
            personnelBtn, formationBtn, clubBtn, sp, eventPanel, resultPanel, advanceBtn);
        actionPanel = panel;
        return panel;
    }

    /** ナビゲーション用ボタン（画面遷移のみ、週を進めない） */
    private Button makeNavBtn(String text, Runnable action) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        String base = "-fx-background-color:rgba(255,255,255,0.04);-fx-text-fill:rgba(255,255,255,0.65);"
            + "-fx-font-size:13px;-fx-padding:10 14;-fx-background-radius:8;"
            + "-fx-border-color:rgba(255,255,255,0.1);-fx-border-width:1;-fx-border-radius:8;"
            + "-fx-cursor:hand;-fx-alignment:CENTER-LEFT;";
        String hover = "-fx-background-color:rgba(255,255,255,0.1);-fx-text-fill:#fff;"
            + "-fx-font-size:13px;-fx-padding:10 14;-fx-background-radius:8;"
            + "-fx-border-color:rgba(255,255,255,0.22);-fx-border-width:1;-fx-border-radius:8;"
            + "-fx-cursor:hand;-fx-alignment:CENTER-LEFT;";
        btn.setStyle(base);
        btn.setOnAction(e -> action.run());
        btn.setOnMouseEntered(ev -> btn.setStyle(hover));
        btn.setOnMouseExited(ev  -> btn.setStyle(base));
        return btn;
    }

    private VBox buildSchedulePanel() {
        VBox panel = new VBox();
        panel.setStyle("-fx-background-color:#08081a;");
        ScheduleView sv = new ScheduleView(season, MainApp.playerClub.getName());
        VBox.setVgrow(sv, Priority.ALWAYS);
        panel.getChildren().add(sv);
        VBox.setVgrow(panel, Priority.ALWAYS);
        return panel;
    }

    // ─────────────────────────────────────────────────────────
    // カレンダー
    // ─────────────────────────────────────────────────────────
    private VBox buildCalendar() {
        VBox outer = new VBox(6);
        outer.setStyle("-fx-background-color:#050510;-fx-padding:12 20;"
            + "-fx-border-color:rgba(255,255,255,0.06);-fx-border-width:1 0 0 0;");
        outer.setPrefHeight(100);

        HBox monthRow = new HBox(0);
        for (String mn : new String[]{"1月","2月","3月","4月","5月","6月","7月","8月","9月","10月","11月","12月"}) {
            Label ml = new Label(mn);
            ml.setStyle("-fx-font-size:9px;-fx-text-fill:rgba(255,255,255,0.2);"
                + "-fx-min-width:68;-fx-alignment:CENTER;");
            monthRow.getChildren().add(ml);
        }

        calendarPane = new FlowPane();
        calendarPane.setHgap(3); calendarPane.setVgap(3);
        refreshCalendar();

        ScrollPane scroll = new ScrollPane(calendarPane);
        scroll.setStyle("-fx-background:#050510;-fx-background-color:#050510;");
        scroll.setFitToWidth(true); scroll.setPrefHeight(60);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        outer.getChildren().addAll(monthRow, scroll);
        return outer;
    }

    private void refreshCalendar() {
        if (calendarPane == null) return;
        calendarPane.getChildren().clear();
        int cur = season.getCurrentWeek();
        for (int w = 1; w <= SeasonManager.TOTAL_WEEKS; w++) {
            final int week = w;
            WeekRecord rec = season.getRecordForWeek(week);
            boolean isCurrent  = week == cur;
            boolean isFuture   = week > cur;
            boolean isMatchWk  = season.getSchedule().stream().anyMatch(s -> s.getWeek() == week);

            Rectangle cell = new Rectangle(13, 13);
            cell.setArcWidth(3); cell.setArcHeight(3);
            if (isCurrent) {
                cell.setFill(Color.WHITE);
                cell.setEffect(new javafx.scene.effect.Glow(0.8));
            } else if (isFuture) {
                cell.setFill(isMatchWk ? Color.web("#1a3020") : Color.web("#1a1a2e"));
                cell.setStroke(isMatchWk ? Color.web("#4a7a35") : Color.web("#333355"));
                cell.setStrokeWidth(1);
            } else if (rec == null) {
                cell.setFill(Color.web("#333344"));
            } else {
                cell.setFill(switch (rec.action) {
                    case TRAINING -> Color.web("#3a7bd5");
                    case MATCH    -> Color.web("#4a7a35");
                    case TRANSFER -> Color.web("#8b4513");
                    default       -> Color.web("#333344");
                });
            }
            if (SeasonManager.TRANSFER_WINDOWS.contains(week) && !isCurrent) {
                cell.setStroke(Color.web("#8b4513")); cell.setStrokeWidth(1);
            }
            calendarPane.getChildren().add(cell);
        }
    }

    // ─────────────────────────────────────────────────────────
    // 週進行ロジック
    // ─────────────────────────────────────────────────────────
    private void executeAdvance() {
        // 試合週: 「日程進行」が試合のトリガー（自動遷移は廃止）
        if (season.isMatchWeek()) {
            handleMatchWeekIfNeeded();
            return;
        }
        // 移籍ウィンドウ中: 移籍か練習かを選択
        if (season.isTransferWindowOpen()) {
            Alert dlg = new Alert(Alert.AlertType.CONFIRMATION);
            dlg.setTitle("週の行動を選択");
            dlg.setHeaderText("🔄 移籍ウィンドウ期間中です");
            dlg.setContentText("今週は移籍活動と練習どちらを実施しますか？");
            ButtonType transferType = new ButtonType("🔄 移籍活動へ", ButtonBar.ButtonData.YES);
            ButtonType trainingType = new ButtonType("🏋️ 練習する",  ButtonBar.ButtonData.NO);
            dlg.getButtonTypes().setAll(transferType, trainingType);
            dlg.showAndWait().ifPresent(choice -> {
                if (choice == transferType) openTransferView();
                else                        runTrainingAndAdvance();
            });
            return;
        }
        // 通常週: 練習 + 週次イベント + 週進行
        runTrainingAndAdvance();
    }

    /** 練習を実行して週を進める（通常週・移籍ウィンドウ練習選択時） */
    private void runTrainingAndAdvance() {
        String result = executeTraining();
        MainApp.financeService.processWeeklySalaries(MainApp.playerClub);
        MainApp.gameDataService.saveClub(MainApp.playerClub);

        java.util.Set<Integer> playedIds = new java.util.HashSet<>();
        MainApp.playerClub.getRegistered().forEach(p -> playedIds.add(p.getId()));

        WeeklyEvent event = MainApp.weeklyEventService.generateAndApply(
            MainApp.playerClub,
            season.getCurrentWeek(),
            season.getCurrentSeason(),
            playedIds,
            false
        );
        showWeeklyEvent(event);
        MainApp.gameDataService.saveSquad(MainApp.playerClub);

        MainApp.leagueAIService.processAllClubs(MainApp.allClubs, season.getCurrentWeek());
        season.advanceWeek(Action.TRAINING, result, MainApp.playerClub.getBudget());

        java.util.List<String> promotions =
            MainApp.academyService.processPromotions(MainApp.playerClub);
        if (!promotions.isEmpty()) {
            MainApp.gameDataService.saveSquad(MainApp.playerClub);
            String promotionMsg = String.join("\n", promotions);
            javafx.application.Platform.runLater(() -> showInfo("🎉 昇格", promotionMsg));
        }

        if (season.isSeasonOver()) { showSeasonEnd(); return; }
        refresh();
        showResult(result);
    }

    private String executeTraining() {
        int growCount = 0;
        StringBuilder sb = new StringBuilder();
        for (Player p : MainApp.playerClub.getSquad()) {
            String r = MainApp.trainingService.trainPlayer(p);
            if (r.contains("成長")) {
                growCount++;
                sb.append("✨ ").append(r).append("\n");
            }
        }

        // 仲良し度ポイント加算（練習で全員が一緒）
        java.util.List<String> friendMsgs =
            MainApp.friendshipManager.recordTraining(MainApp.playerClub.getSquad());
        for (String msg : friendMsgs) {
            sb.append(msg).append("\n");
        }

        MainApp.gameDataService.saveSquad(MainApp.playerClub);
        final int finalCount = growCount;
        String result = String.format("練習完了: %d名が成長", finalCount)
            + (finalCount > 0 ? "\n" + sb : "");
        if (!friendMsgs.isEmpty()) {
            result += "\n" + String.join("\n", friendMsgs);
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────
    // 試合週処理（日程進行ボタンから呼ばれる）
    // ─────────────────────────────────────────────────────────
    public void handleMatchWeekIfNeeded() {
        ScheduledMatch sm = season.getMatchForCurrentWeek();
        if (sm == null) return;

        String opponentName = sm.getOpponentName(MainApp.playerClub.getName());
        Club opp = MainApp.allClubs.stream()
            .filter(c -> c.getName().equals(opponentName)).findFirst().orElse(null);
        if (opp == null) return;

        // バナー更新
        updateMatchWeekBanner(sm);

        // 試合画面を開く
        MatchView mv = new MatchView(MainApp.app);
        mv.setScheduledMatch(opp);
        // ラムダ内で参照する変数はeffectively finalにする
        final ScheduledMatch finalSm  = sm;
        final Club           finalOpp = opp;

        mv.setMatchResultCallback(events -> {
            MatchEvent last   = events.get(events.size() - 1);
            // ホームスコアとアウェイスコアをfinal変数で取得（ラムダ内での再代入を避ける）
            final int rawHome = last.getHomeScore();
            final int rawAway = last.getAwayScore();
            // プレイヤークラブ視点のスコアに変換
            final boolean playerIsHome = finalSm.isPlayerHome(MainApp.playerClub.getName());
            final int playerGoals   = playerIsHome ? rawHome : rawAway;
            final int opponentGoals = playerIsHome ? rawAway : rawHome;

            finalSm.recordResult(rawHome, rawAway);
            MainApp.gameDataService.saveMatchResult(MainApp.playerClub, finalOpp, events);
            MainApp.app.updateHeaderLabels();

            MainApp.financeService.processWeeklySalaries(MainApp.playerClub);
            MainApp.gameDataService.saveClub(MainApp.playerClub);

            final String outcome = playerGoals > opponentGoals ? "勝利"
                                 : playerGoals == opponentGoals ? "引分" : "敗北";
            final String result  = String.format("⚽ %s %d-%d %s [%s]",
                MainApp.playerClub.getName(), playerGoals, opponentGoals,
                finalOpp.getName(), outcome);
            showPostMatchDialog(events, finalOpp.getName(), playerGoals, opponentGoals, () -> {
                season.advanceWeek(Action.MATCH, result, MainApp.playerClub.getBudget());
                if (season.isSeasonOver()) showSeasonEnd();
                else MainApp.app.showWeeklyView();
            });
        });
        MainApp.app.setCenterView(mv);
        // スケジュール試合は画面表示後に自動開始
        javafx.application.Platform.runLater(() -> mv.startMatch(finalOpp));
    }

    private void showPostMatchDialog(List<MatchEvent> events, String opponentName,
                                     int playerGoals, int opponentGoals,
                                     Runnable onClose) {
        if (events == null || events.isEmpty()) {
            if (onClose != null) onClose.run();
            return;
        }

        MatchEvent last = events.get(events.size() - 1);
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("試合結果");
        dialog.setHeaderText("🏁 試合終了");
        ButtonType backBtn = new ButtonType("週画面へ戻る", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(backBtn);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        String outcome = playerGoals > opponentGoals ? "勝利"
            : playerGoals == opponentGoals ? "引き分け" : "敗北";
        VBox summary = new VBox(8,
            new Label("対戦: " + MainApp.playerClub.getName() + " vs " + opponentName),
            new Label("スコア: " + playerGoals + " - " + opponentGoals),
            new Label("結果: " + outcome)
        );
        summary.setPadding(new Insets(12));
        tabs.getTabs().add(new Tab("結果", summary));

        GridPane stats = new GridPane();
        stats.setHgap(10);
        stats.setVgap(6);
        stats.setPadding(new Insets(12));
        int[] shots = last.getShots();
        int[] shotsOn = last.getShotsOn();
        int[] corners = last.getCorners();
        int[] fouls = last.getFouls();
        int homePoss = last.getHomePoss();
        int awayPoss = 100 - homePoss;
        addStatRow(stats, 0, "シュート", shots[0], shots[1], "#4a7a35", "#00c8ff");
        addStatRow(stats, 1, "枠内", shotsOn[0], shotsOn[1], "#4a7a35", "#00c8ff");
        addStatRow(stats, 2, "CK", corners[0], corners[1], "#4a7a35", "#00c8ff");
        addStatRow(stats, 3, "反則", fouls[0], fouls[1], "#4a7a35", "#00c8ff");
        addStatRow(stats, 4, "ポゼッション", homePoss, awayPoss, "#4a7a35", "#00c8ff");
        tabs.getTabs().add(new Tab("スタッツ", stats));

        ListView<String> eventList = new ListView<>();
        for (MatchEvent e : events) {
            if (e.getType() == MatchEvent.Type.PASS || e.getType() == MatchEvent.Type.PRESS) continue;
            eventList.getItems().add(String.format("%2d' %s", e.getMinute(), e.getMessage()));
        }
        tabs.getTabs().add(new Tab("イベント", eventList));

        dialog.getDialogPane().setContent(tabs);
        dialog.getDialogPane().setPrefSize(680, 500);
        final boolean[] closed = { false };
        Runnable closeOnce = () -> {
            if (closed[0]) return;
            closed[0] = true;
            if (onClose != null) onClose.run();
        };

        javafx.application.Platform.runLater(() -> {
            try {
                // アニメーション処理中から呼ばれても、次Pulseでモーダル表示する
                dialog.showAndWait();
                closeOnce.run();
            } catch (IllegalStateException ex) {
                // まれにレイアウト/アニメーション中と判定される環境向けフォールバック
                dialog.setOnHidden(e -> closeOnce.run());
                javafx.application.Platform.runLater(dialog::show);
            }
        });
    }

    private void addStatRow(GridPane g, int row, String name, int home, int away,
                            String homeColor, String awayColor) {
        Label left = new Label(String.valueOf(home));
        Label mid = new Label(name);
        Label right = new Label(String.valueOf(away));
        left.setMinWidth(80);
        right.setMinWidth(80);
        left.setStyle("-fx-font-weight:bold;-fx-text-fill:" + homeColor + ";");
        right.setStyle("-fx-font-weight:bold;-fx-text-fill:" + awayColor + ";");
        g.add(left, 0, row);
        g.add(mid, 1, row);
        g.add(right, 2, row);
    }

    private void updateMatchWeekBanner(ScheduledMatch sm) {
        // actionPanel内のbanner（インデックス1）を更新
        if (actionPanel.getChildren().size() > 1) {
            var banner = actionPanel.getChildren().get(1);
            if (banner instanceof Label lbl) {
                String opName = sm.getOpponentName(MainApp.playerClub.getName());
                String home   = sm.isPlayerHome(MainApp.playerClub.getName()) ? "ホーム" : "アウェイ";
                lbl.setText("⚽ 今週は試合週！\nvs " + opName + "（" + home + "）");
                lbl.setVisible(true);
                lbl.setManaged(true);
            }
        }
    }

    private void openTransferView() {
        ScoutView sv = new ScoutView(MainApp.app);
        sv.setWeeklyCallback(() -> {
            MainApp.financeService.processWeeklySalaries(MainApp.playerClub);
            MainApp.gameDataService.saveClub(MainApp.playerClub);
            season.advanceWeek(Action.TRANSFER, "移籍ウィンドウ完了", MainApp.playerClub.getBudget());
            if (season.isSeasonOver()) showSeasonEnd();
            else MainApp.app.showWeeklyView();
        }, true);
        MainApp.app.setCenterView(sv);
    }

    /** 週次イベントをイベントパネルに表示 */
    private void showWeeklyEvent(WeeklyEvent event) {
        if (event == null || eventTitleLabel == null) return;
        eventTitleLabel.setText(event.getTitle());
        eventDescLabel.setText(event.getDescription());

        // 効果テキストと色
        String effectText = "";
        String effectColor = "#ffdd44";
        if (event.getEffectType() != WeeklyEvent.EffectType.NONE) {
            String target = event.getEffectTarget();
            int    val    = event.getEffectValue();
            String who    = event.getTargetPlayer() != null
                ? event.getTargetPlayer().getFullName() : "全選手";

            if (event.getEffectType() == WeeklyEvent.EffectType.BUDGET_CHANGE) {
                effectText  = String.format("💰 クラブ予算  %+,d 円", val);
                effectColor = val > 0 ? "#44ff88" : "#ff6666";
            } else {
                effectText  = String.format("%s  %s  %s%d",
                    who, target,
                    val > 0 ? "▲+" : "▼", Math.abs(val));
                effectColor = val > 0 ? "#44ff88" : "#ff6666";
            }
        }
        eventEffectLabel.setText(effectText);
        eventEffectLabel.setStyle("-fx-font-size:11px;-fx-font-weight:bold;"
            + "-fx-text-fill:" + effectColor + ";-fx-padding:4 0 0 0;");

        // パネルをアニメーションで強調
        if (eventPanel != null) {
            javafx.animation.FadeTransition ft =
                new javafx.animation.FadeTransition(
                    javafx.util.Duration.millis(300), eventPanel);
            ft.setFromValue(0.4); ft.setToValue(1.0); ft.play();
        }
        // ヘッダーラベルを更新
        MainApp.app.updateHeaderLabels();
    }

    private void showResult(String result) {
        resultPanel.setVisible(true);
        resultPanel.getChildren().clear();
        Label t = new Label("✅ 第" + (season.getCurrentWeek()-1) + "週 完了");
        t.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#4a7a35;");
        resultText.setText(result);
        resultPanel.getChildren().addAll(t, resultText);
        FadeTransition ft = new FadeTransition(Duration.millis(300), resultPanel);
        ft.setFromValue(0); ft.setToValue(1); ft.play();
    }

    private void showSeasonEnd() {
        // ── シーズン終了処理 ──────────────────────────────────
        // 1. 引退確定処理（表明済み選手を RETIRED ロールへ）
        java.util.List<Player> retired =
            MainApp.retirementService.processRetirements(MainApp.allClubs);
        if (!retired.isEmpty()) {
            MainApp.gameDataService.saveSquad(MainApp.playerClub);
        }

        // 2. SeasonEndView へ遷移（引退カード込み）
        SeasonEndView endView = new SeasonEndView(app, season, MainApp.leagueManager);
        MainApp.app.setCenterView(endView);
    }

    public void refresh() {
        Club club = MainApp.playerClub;
        weekLabel.setText("🗓️  " + season.getWeekTitle());
        monthLabel.setText("第" + season.getCurrentSeason() + "シーズン  残り"
            + season.getRemainingWeeks() + "週");
        String se = switch (season.getCurrentSeason_()) {
            case SPRING -> "🌸"; case SUMMER -> "☀️"; case AUTUMN -> "🍂"; default -> "❄️";
        };
        seasonBarLabel.setText(se + " シーズン進捗");
        seasonBar.setProgress(season.getProgress());
        budgetLabel.setText("💰 ¥" + String.format("%,d", club.getBudget()));
        standingLabel.setText(String.format("🏆 %dpt  %dW-%dD-%dL",
            club.getPoints(), club.getWins(), club.getDraws(), club.getLosses()));
        // 試合週バナー更新（日程進行ボタンで試合を開始する方式に変更）
        ScheduledMatch sm = season.getMatchForCurrentWeek();
        if (sm != null) updateMatchWeekBanner(sm);

        refreshCalendar();
    }

    private void showInfo(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(msg);
        a.showAndWait();
    }
}

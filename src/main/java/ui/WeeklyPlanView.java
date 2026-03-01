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
    private Button trainingBtn, matchBtn, transferBtn;

    private Action selectedAction = null;
    private String pendingResult  = null;
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
        standingLabel.setStyle("-fx-font-size:12px;-fx-text-fill:#ffd700;");
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
        VBox panel = new VBox(10);
        panel.setPrefWidth(280);
        panel.setMinWidth(280);
        panel.setStyle("-fx-background-color:#0d0d22;-fx-padding:20;"
            + "-fx-border-color:rgba(255,255,255,0.06);-fx-border-width:0 1 0 0;");

        Label title = new Label("今週のアクション");
        title.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:rgba(255,255,255,0.55);");

        // 試合週バナー（試合週のみ表示）
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

        trainingBtn = makeActionBtn("🏋️  練習", "#3a7bd5",
            "全選手を練習\n若い選手は能力値UP！", Action.TRAINING);
        matchBtn    = makeActionBtn("⚽  試合",  "#4a7a35",
            "スケジュールにない特別試合\n（親善試合）", Action.MATCH);
        transferBtn = makeActionBtn("🔄  移籍",  "#8b4513",
            "移籍ウィンドウ期間のみ有効\n冬(1-4週) 夏(27-30週)", Action.TRANSFER);

        advanceBtn = new Button("次の週へ進む →");
        advanceBtn.setMaxWidth(Double.MAX_VALUE);
        advanceBtn.setDisable(true);
        advanceBtn.setStyle("-fx-background-color:#333;-fx-text-fill:#777;"
            + "-fx-font-size:13px;-fx-font-weight:bold;"
            + "-fx-padding:11 18;-fx-background-radius:8;");
        advanceBtn.setOnAction(e -> executeAdvance());

        resultPanel = new VBox(8);
        resultPanel.setVisible(false);
        resultText = new Label();
        resultText.setStyle("-fx-font-size:11px;-fx-text-fill:rgba(255,255,255,0.7);-fx-wrap-text:true;");
        resultText.setWrapText(true);
        resultPanel.getChildren().add(resultText);

        Region sp = new Region();
        VBox.setVgrow(sp, Priority.ALWAYS);
        panel.getChildren().addAll(title, matchWeekBanner, trainingBtn, matchBtn,
            transferBtn, sp, resultPanel, advanceBtn);
        actionPanel = panel;
        return panel;
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

    private Button makeActionBtn(String text, String color, String tooltip, Action action) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        String base = String.format(
            "-fx-background-color:%s22;-fx-text-fill:%s;"
            + "-fx-font-size:14px;-fx-font-weight:bold;"
            + "-fx-padding:12 14;-fx-background-radius:9;"
            + "-fx-border-color:%s44;-fx-border-width:1;-fx-border-radius:9;"
            + "-fx-cursor:hand;-fx-alignment:CENTER-LEFT;", color, color, color);
        String sel = String.format(
            "-fx-background-color:%s;-fx-text-fill:white;"
            + "-fx-font-size:14px;-fx-font-weight:bold;"
            + "-fx-padding:12 14;-fx-background-radius:9;"
            + "-fx-border-color:%s;-fx-border-width:2;-fx-border-radius:9;"
            + "-fx-cursor:hand;-fx-alignment:CENTER-LEFT;", color, color);
        btn.setStyle(base);
        btn.setTooltip(new Tooltip(tooltip));
        btn.setOnAction(e -> {
            if (action == Action.MATCH) {
                openMatchSelector(); return;
            }
            if (action == Action.TRANSFER && !season.canDoAction(action)) {
                showInfo("⚠️ 移籍ウィンドウ外",
                    "移籍は冬(第1〜4週)と夏(第27〜30週)のみ可能です。");
                return;
            }
            selectAction(action, btn, base, sel);
        });
        btn.setOnMouseEntered(ev -> { if (selectedAction != action) btn.setStyle(base.replace(color+"22", color+"44")); });
        btn.setOnMouseExited(ev ->  { if (selectedAction != action) btn.setStyle(base); });
        return btn;
    }

    private void selectAction(Action action, Button btn, String base, String sel) {
        resetActionButtons();
        selectedAction = action;
        btn.setStyle(sel);
        advanceBtn.setDisable(false);
        advanceBtn.setStyle("-fx-background-color:#4a7a35;-fx-text-fill:white;"
            + "-fx-font-size:13px;-fx-font-weight:bold;"
            + "-fx-padding:11 18;-fx-background-radius:8;-fx-cursor:hand;");
    }

    private void resetActionButtons() {
        selectedAction = null; pendingResult = null;
        advanceBtn.setDisable(true);
        advanceBtn.setStyle("-fx-background-color:#333;-fx-text-fill:#777;"
            + "-fx-font-size:13px;-fx-font-weight:bold;"
            + "-fx-padding:11 18;-fx-background-radius:8;");
    }

    private void openMatchSelector() {
        Dialog<String> dlg = new Dialog<>();
        dlg.setTitle("⚽ 親善試合");
        dlg.setHeaderText("対戦相手を選んでください（スケジュール外）");
        ButtonType ok = new ButtonType("試合開始！", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        ComboBox<String> combo = new ComboBox<>();
        MainApp.allClubs.stream()
            .filter(c -> !c.getName().equals(MainApp.playerClub.getName()))
            .forEach(c -> combo.getItems().add(c.getName() + "  (OVR "
                + String.format("%.0f", c.getAverageOverall()) + ")"));
        if (!combo.getItems().isEmpty()) combo.setValue(combo.getItems().get(0));
        VBox content = new VBox(10, new Label("対戦相手:"), combo);
        content.setPadding(new Insets(16));
        dlg.getDialogPane().setContent(content);
        dlg.setResultConverter(bt -> bt == ok ? combo.getValue() : null);
        dlg.showAndWait().ifPresent(sel -> {
            if (sel != null) {
                selectedAction = Action.MATCH;
                pendingResult  = sel.split("  ")[0];
                advanceBtn.setDisable(false);
                advanceBtn.setStyle("-fx-background-color:#4a7a35;-fx-text-fill:white;"
                    + "-fx-font-size:13px;-fx-font-weight:bold;"
                    + "-fx-padding:11 18;-fx-background-radius:8;-fx-cursor:hand;");
            }
        });
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
        if (selectedAction == null) return;
        String result;
        switch (selectedAction) {
            case TRAINING  -> result = executeTraining();
            case MATCH     -> result = executeMatch(pendingResult);
            case TRANSFER  -> { openTransferView(); return; }
            default        -> result = "─";
        }
        MainApp.financeService.processWeeklySalaries(MainApp.playerClub);
        MainApp.gameDataService.saveClub(MainApp.playerClub);

        // ── 週次イベントを生成・適用 ──
        WeeklyEvent event = MainApp.weeklyEventService.generateAndApply(
            MainApp.playerClub,
            season.getCurrentWeek(),
            season.getCurrentSeason()
        );
        showWeeklyEvent(event);
        MainApp.gameDataService.saveSquad(MainApp.playerClub); // ステータス変動をDB保存

        season.advanceWeek(selectedAction, result, MainApp.playerClub.getBudget());

        // ── 翌週昇格の確定処理 ────────────────────────────
        java.util.List<String> promotions =
            MainApp.academyService.processPromotions(MainApp.playerClub);
        if (!promotions.isEmpty()) {
            MainApp.gameDataService.saveSquad(MainApp.playerClub);
            String promotionMsg = String.join("\n", promotions);
            javafx.application.Platform.runLater(() -> showInfo("🎉 昇格", promotionMsg));
        }

        if (season.isSeasonOver()) { showSeasonEnd(); return; }
        resetActionButtons();
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

    private String executeMatch(String opponentName) {
        if (opponentName == null) return "試合未実施";
        Club opp = MainApp.allClubs.stream()
            .filter(c -> c.getName().equals(opponentName)).findFirst().orElse(null);
        if (opp == null) return "対戦相手不明";

        // 試合はMatchViewに遷移して実施
        MatchView mv = new MatchView(MainApp.app);
        // finalOppはラムダ外で宣言したeffectively final変数
        final Club friendlyOpp = opp;
        mv.setMatchResultCallback(events -> {
            MatchEvent last         = events.get(events.size() - 1);
            final int  finalHome    = last.getHomeScore();
            final int  finalAway    = last.getAwayScore();
            final String matchResult = String.format("vs %s  %d-%d",
                friendlyOpp.getName(), finalHome, finalAway);

            MainApp.gameDataService.saveMatchResult(MainApp.playerClub, friendlyOpp, events);
            MainApp.app.updateHeaderLabels();
            MainApp.financeService.processWeeklySalaries(MainApp.playerClub);
            MainApp.gameDataService.saveClub(MainApp.playerClub);
            season.advanceWeek(Action.MATCH, matchResult, MainApp.playerClub.getBudget());
            MainApp.app.showWeeklyView();
        });
        MainApp.app.setCenterView(mv);
        mv.startMatch(opp);
        return "試合中...";  // callbackで上書きされる
    }

    // ─────────────────────────────────────────────────────────
    // 試合週自動処理（SeasonManagerから呼ばれる）
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

            season.advanceWeek(Action.MATCH, result, MainApp.playerClub.getBudget());
            if (season.isSeasonOver()) showSeasonEnd();
            else MainApp.app.showWeeklyView();
        });
        MainApp.app.setCenterView(mv);
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
        transferBtn.setOpacity(season.isTransferWindowOpen() ? 1.0 : 0.4);

        refreshCalendar();

        // 試合週なら自動で試合画面へ
        if (season.isMatchWeek()) {
            javafx.application.Platform.runLater(this::handleMatchWeekIfNeeded);
        }
    }

    private void showInfo(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(msg);
        a.showAndWait();
    }
}

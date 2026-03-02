package ui;

import javafx.animation.*;
import javafx.geometry.*;
import javafx.scene.canvas.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.util.Duration;
import model.*;
import model.MatchEvent.Type;
import service.ExcelLoaderService;
import service.MatchSimulator;

import java.util.List;

/**
 * 試合画面
 * ・ピッチに選手（背番号）を表示
 * ・必殺技発動時にカットインアニメーション表示
 */
public class MatchView extends javafx.scene.layout.StackPane {

    private final MainApp app;

    // クラブカラー（初期値は後でplayerClubから上書き）
    private String homeColor = "#4a7a35";
    private String awayColor = "#00c8ff";
    private Color  HOME_C    = Color.web(homeColor);
    private Color  AWAY_C    = Color.web(awayColor);

    // スコアボード
    private Label homeScoreLabel, awayScoreLabel, awayNameLabel, matchTimeLabel;
    private ProgressBar timeBar;

    // ポゼッション
    private Rectangle possHomeBar, possAwayBar;
    private Label possHomeLabel, possAwayLabel;

    // ピッチ
    private Canvas pitchCanvas;
    private double ballX = 0.5, ballY = 0.5;

    // 選手位置
    private List<MatchSimulator.PlayerPosition> playerPositions = List.of();

    // スタッツ
    private Label[] shotLabels   = new Label[2];
    private Label[] shotOnLabels = new Label[2];
    private Label[] cornerLabels = new Label[2];
    private Label[] foulLabels   = new Label[2];

    // イベントログ
    private VBox eventLogBox;
    private ScrollPane eventScroll;

    // 結果パネル
    private VBox resultPanel;
    private Label resultTitle, resultScore, resultWinner;

    // カットインオーバーレイ（必殺技演出用）
    private StackPane cutinOverlay;
    private Label    cutinMoveName;
    private Label    cutinDesc;
    private ImageView cutinPlayerImg;
    private ImageView cutinMoveImg;
    private HBox     cutinBox;

    // アニメーション
    private Timeline   timeline;
    private List<MatchEvent> events;
    private int        eventIndex;
    private AnimationTimer ballAnimTimer;
    private double targetBallX = 0.5, targetBallY = 0.5;
    private boolean callbackFired = false;

    // コントロール
    private ComboBox<String> speedCombo;
    private Button simulateBtn;

    // 対戦クラブ（外からセットする場合）
    private Club forcedOpponent = null;
    private boolean autoStart   = false;

    public MatchView(MainApp app) {
        this.app = app;
        setStyle("-fx-background-color:#06060f;");
        buildUI();
    }

    /** シーズンスケジュールから自動起動する際にセット */
    public void setScheduledMatch(Club opponent) {
        this.forcedOpponent = opponent;
        this.autoStart      = true;
    }

    private void buildUI() {
        homeColor = MainApp.playerClub.getColor();
        HOME_C    = Color.web(homeColor);

        // コンテンツVBox
        VBox content = new VBox(0);
        content.setStyle("-fx-background-color:#06060f;");
        content.getChildren().addAll(
            buildHeader(),
            buildScoreboard(),
            buildMiddle(),
            buildEventLog(),
            buildResultPanel()
        );
        VBox.setVgrow(content, Priority.ALWAYS);

        // カットインオーバーレイ（StackPane最上位）
        cutinOverlay = buildCutinOverlay();
        cutinOverlay.setMouseTransparent(true);
        cutinOverlay.setVisible(false);
        // StackPaneに重ねる: content + overlay
        getChildren().addAll(content, cutinOverlay);
        StackPane.setAlignment(cutinOverlay, javafx.geometry.Pos.CENTER);

        if (autoStart && forcedOpponent != null) {
            javafx.application.Platform.runLater(() -> startMatch(forcedOpponent));
        }
    }

    // ─────────────────────────────────────────────────────────
    // ヘッダー（対戦相手選択）
    // ─────────────────────────────────────────────────────────
    private HBox buildHeader() {
        HBox bar = new HBox(12);
        bar.setStyle("-fx-background-color:#0d0d22;-fx-padding:14 20;"
            + "-fx-border-color:rgba(255,255,255,0.06);-fx-border-width:0 0 1 0;");
        bar.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("⚽  MATCH");
        title.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#ffcc00;");

        Label vsLabel = new Label("対戦相手:");
        vsLabel.setStyle("-fx-text-fill:rgba(255,255,255,0.45);-fx-font-size:12px;");

        ComboBox<String> opponentCombo = new ComboBox<>();
        opponentCombo.setStyle("-fx-background-color:#1a1a2e;-fx-text-fill:white;-fx-pref-width:180;");
        MainApp.allClubs.stream()
            .filter(c -> c != MainApp.playerClub)
            .forEach(c -> opponentCombo.getItems().add(c.getName()));
        opponentCombo.getSelectionModel().selectFirst();

        speedCombo = new ComboBox<>();
        speedCombo.setStyle("-fx-background-color:#1a1a2e;-fx-text-fill:white;");
        speedCombo.getItems().addAll("超速 (30ms)", "速い (100ms)", "普通 (250ms)", "遅い (500ms)");
        speedCombo.getSelectionModel().select(1);

        simulateBtn = new Button("▶  試合開始");
        simulateBtn.setStyle("-fx-background-color:" + homeColor + ";-fx-text-fill:white;"
            + "-fx-font-size:13px;-fx-font-weight:bold;"
            + "-fx-padding:9 20;-fx-background-radius:8;-fx-cursor:hand;");
        simulateBtn.setOnAction(e -> {
            String name = opponentCombo.getValue();
            if (name == null) return;
            Club opp = MainApp.allClubs.stream()
                .filter(c -> c.getName().equals(name)).findFirst().orElse(null);
            if (opp != null) startMatch(opp);
        });

        bar.getChildren().addAll(title, new Separator(Orientation.VERTICAL),
            vsLabel, opponentCombo, speedCombo, simulateBtn);

        // forcedOpponent があれば相手を固定して操作不可
        if (forcedOpponent != null) {
            opponentCombo.setValue(forcedOpponent.getName());
            opponentCombo.setDisable(true);
        }
        return bar;
    }

    // ─────────────────────────────────────────────────────────
    // スコアボード
    // ─────────────────────────────────────────────────────────
    private HBox buildScoreboard() {
        HBox board = new HBox();
        board.setStyle("-fx-background-color:#0a0a1a;-fx-padding:12 20;");
        board.setAlignment(Pos.CENTER);

        // ホーム
        Label homeLabel = new Label(MainApp.playerClub.getName());
        homeLabel.setStyle("-fx-font-size:13px;-fx-text-fill:" + homeColor + ";-fx-font-weight:bold;");
        homeScoreLabel = new Label("0");
        homeScoreLabel.setStyle("-fx-font-size:40px;-fx-font-weight:bold;-fx-text-fill:#fff;"
            + "-fx-min-width:60;-fx-alignment:CENTER;");
        VBox homeBox = new VBox(2, homeLabel, homeScoreLabel);
        homeBox.setAlignment(Pos.CENTER);

        // 中央
        Label sep = new Label(":");
        sep.setStyle("-fx-font-size:36px;-fx-font-weight:bold;-fx-text-fill:rgba(255,255,255,0.3);"
            + "-fx-padding:0 16;");
        VBox centerBox = new VBox(4);
        centerBox.setAlignment(Pos.CENTER);
        matchTimeLabel = new Label("0'");
        matchTimeLabel.setStyle("-fx-font-size:11px;-fx-text-fill:rgba(255,255,255,0.4);");
        timeBar = new ProgressBar(0);
        timeBar.setPrefWidth(120); timeBar.setPrefHeight(4);
        timeBar.setStyle("-fx-accent:" + homeColor + ";");

        // ポゼッション
        possHomeLabel = new Label("50%");
        possHomeLabel.setStyle("-fx-font-size:10px;-fx-text-fill:" + homeColor + ";");
        possHomeBar   = new Rectangle(60, 6);
        possHomeBar.setFill(Color.web(homeColor));
        possAwayBar   = new Rectangle(60, 6);
        possAwayBar.setFill(Color.web(awayColor));
        possAwayLabel = new Label("50%");
        possAwayLabel.setStyle("-fx-font-size:10px;-fx-text-fill:" + awayColor + ";");
        HBox possRow = new HBox(2, possHomeLabel, possHomeBar, possAwayBar, possAwayLabel);
        possRow.setAlignment(Pos.CENTER);

        centerBox.getChildren().addAll(matchTimeLabel, timeBar, sep, possRow);

        // アウェイ（後でセット）
        awayScoreLabel = new Label("0");
        awayScoreLabel.setStyle("-fx-font-size:40px;-fx-font-weight:bold;-fx-text-fill:#fff;"
            + "-fx-min-width:60;-fx-alignment:CENTER;");
        awayNameLabel = new Label("─");
        awayNameLabel.setStyle("-fx-font-size:13px;-fx-text-fill:" + awayColor + ";-fx-font-weight:bold;");
        awayNameLabel.setId("awayNameLabel");
        VBox awayBox = new VBox(2, awayNameLabel, awayScoreLabel);
        awayBox.setAlignment(Pos.CENTER);

        Region sp1 = new Region(); HBox.setHgrow(sp1, Priority.ALWAYS);
        Region sp2 = new Region(); HBox.setHgrow(sp2, Priority.ALWAYS);
        board.getChildren().addAll(sp1, homeBox, centerBox, awayBox, sp2);
        return board;
    }

    // ─────────────────────────────────────────────────────────
    // 中段（ピッチ ＋ スタッツ）
    // ─────────────────────────────────────────────────────────
    private HBox buildMiddle() {
        HBox middle = new HBox(0);
        middle.setPrefHeight(300);

        pitchCanvas = new Canvas(480, 290);
        drawPitch(null);

        VBox pitchBox = new VBox(pitchCanvas);
        pitchBox.setStyle("-fx-background-color:#0b1a0b;-fx-padding:8;");
        pitchBox.setAlignment(Pos.CENTER);
        HBox.setHgrow(pitchBox, Priority.SOMETIMES);

        VBox stats = buildStatsPanel();
        HBox.setHgrow(stats, Priority.ALWAYS);

        middle.getChildren().addAll(pitchBox, stats);
        return middle;
    }

    private VBox buildStatsPanel() {
        VBox panel = new VBox(8);
        panel.setStyle("-fx-background-color:#0d0d22;-fx-padding:16;");
        panel.setMinWidth(200);

        String[][] statDefs = {{"シュート","shot"},{"枠内","shoton"},{"CK","corner"},{"反則","foul"}};
        for (int i = 0; i < 4; i++) {
            Label name = new Label(statDefs[i][0]);
            name.setStyle("-fx-font-size:10px;-fx-text-fill:rgba(255,255,255,0.3);");

            shotLabels[0]   = new Label("0"); shotLabels[1]   = new Label("0");
            shotOnLabels[0] = new Label("0"); shotOnLabels[1] = new Label("0");
            cornerLabels[0] = new Label("0"); cornerLabels[1] = new Label("0");
            foulLabels[0]   = new Label("0"); foulLabels[1]   = new Label("0");
        }

        for (int i = 0; i < 4; i++) {
            Label nm = new Label(statDefs[i][0]);
            nm.setStyle("-fx-font-size:10px;-fx-text-fill:rgba(255,255,255,0.3);-fx-min-width:40;");
            Label[] arr = i==0?shotLabels : i==1?shotOnLabels : i==2?cornerLabels : foulLabels;
            arr[0].setStyle("-fx-font-size:12px;-fx-text-fill:" + homeColor + ";-fx-min-width:24;-fx-alignment:CENTER-RIGHT;");
            arr[1].setStyle("-fx-font-size:12px;-fx-text-fill:" + awayColor + ";-fx-min-width:24;-fx-alignment:CENTER-LEFT;");
            HBox row = new HBox(6, arr[0], nm, arr[1]);
            row.setAlignment(Pos.CENTER);
            panel.getChildren().add(row);
        }
        return panel;
    }

    // ─────────────────────────────────────────────────────────
    // ピッチ描画（ボール ＋ 選手背番号）
    // ─────────────────────────────────────────────────────────
    private void drawPitch(MatchEvent evt) {
        GraphicsContext gc = pitchCanvas.getGraphicsContext2D();
        double W = pitchCanvas.getWidth(), H = pitchCanvas.getHeight();

        // ── 芝生 ─────────────────────────────────────────────
        gc.setFill(Color.web("#1a3a1a"));
        gc.fillRect(0, 0, W, H);

        // 縦縞
        gc.setFill(Color.web("#1e401e"));
        for (int i = 0; i < 6; i++) {
            if (i % 2 == 0) gc.fillRect(i * W/6, 0, W/6, H);
        }

        // ── ライン ────────────────────────────────────────────
        gc.setStroke(Color.web("rgba(255,255,255,0.5)"));
        gc.setLineWidth(1.5);
        gc.strokeRect(4, 4, W-8, H-8);                   // 外枠
        gc.strokeLine(W/2, 4, W/2, H-4);                 // センターライン
        gc.strokeOval(W/2-35, H/2-35, 70, 70);           // センターサークル
        gc.strokeRect(4, H/2-50, 55, 100);               // 左ペナ
        gc.strokeRect(W-59, H/2-50, 55, 100);            // 右ペナ
        gc.strokeRect(4, H/2-22, 22, 44);                // 左ゴールエリア
        gc.strokeRect(W-26, H/2-22, 22, 44);             // 右ゴールエリア

        // ── 選手（背番号） ────────────────────────────────────
        if (evt != null) {
            List<MatchSimulator.PlayerPosition> positions = MainApp.matchSimulator.getPositions(evt);
            for (MatchSimulator.PlayerPosition pp : positions) {
                double px = pp.x() * W;
                double py = pp.y() * H;

                // 点を描画
                Color playerColor = pp.isHome() ? HOME_C : AWAY_C;
                gc.setFill(playerColor.deriveColor(0, 1, 0.5, 0.85));
                gc.fillOval(px-10, py-10, 20, 20);
                gc.setStroke(playerColor);
                gc.setLineWidth(1.5);
                gc.strokeOval(px-10, py-10, 20, 20);

                // 背番号
                gc.setFill(Color.WHITE);
                gc.setFont(Font.font("Arial", FontWeight.BOLD, 9));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.setTextBaseline(VPos.CENTER);
                String numStr = pp.shirtNumber() == 0 ? "?" : String.valueOf(pp.shirtNumber());
                gc.fillText(numStr, px, py);
            }
        }

        // ── ボール ────────────────────────────────────────────
        double bx = ballX * W, by = ballY * H;
        // 影
        gc.setFill(Color.web("rgba(0,0,0,0.4)"));
        gc.fillOval(bx-7, by-3, 14, 7);
        // 本体
        RadialGradient ballGrad = new RadialGradient(
            0, 0, bx-3, by-4, 9, false, CycleMethod.NO_CYCLE,
            new Stop(0, Color.WHITE), new Stop(1, Color.web("#cccccc")));
        gc.setFill(ballGrad);
        gc.fillOval(bx-8, by-8, 16, 16);
        gc.setStroke(Color.web("#888888")); gc.setLineWidth(0.8);
        gc.strokeOval(bx-8, by-8, 16, 16);
    }

    // ─────────────────────────────────────────────────────────
    // イベントログ
    // ─────────────────────────────────────────────────────────
    private VBox buildEventLog() {
        VBox box = new VBox();
        box.setStyle("-fx-background-color:#050510;");
        box.setPrefHeight(130);

        eventLogBox = new VBox(3);
        eventLogBox.setStyle("-fx-padding:8 12;");
        eventScroll = new ScrollPane(eventLogBox);
        eventScroll.setStyle("-fx-background:#050510;-fx-background-color:#050510;");
        eventScroll.setFitToWidth(true);
        eventScroll.setPrefHeight(130);
        box.getChildren().add(eventScroll);
        VBox.setVgrow(eventScroll, Priority.ALWAYS);
        return box;
    }

    private void addEventLog(MatchEvent e) {
        String color = switch (e.getType()) {
            case GOAL, SPECIAL_MOVE -> "#ffd700";
            case SAVE               -> awayColor;
            case FOUL               -> "#ff6b6b";
            case HALFTIME, FULLTIME -> "#aaa";
            default -> e.isHome() ? homeColor : awayColor;
        };
        String icon = switch (e.getType()) {
            case GOAL         -> "⚽";
            case SPECIAL_MOVE -> "🌟";
            case SAVE         -> "🧤";
            case FOUL         -> "🟨";
            case HALFTIME     -> "🔔";
            case FULLTIME     -> "🏁";
            case MISS         -> "😢";
            default           -> "▸";
        };
        Label lbl = new Label(String.format("%s %2d'  %s", icon, e.getMinute(), e.getMessage()));
        lbl.setStyle("-fx-font-size:11px;-fx-text-fill:" + color + ";-fx-wrap-text:true;");
        lbl.setWrapText(true);
        javafx.application.Platform.runLater(() -> {
            eventLogBox.getChildren().add(0, lbl);
            eventScroll.setVvalue(0);
        });
    }

    // ─────────────────────────────────────────────────────────
    // 結果パネル
    // ─────────────────────────────────────────────────────────
    private VBox buildResultPanel() {
        resultPanel = new VBox(8);
        resultPanel.setStyle("-fx-background-color:#0a0a1a;-fx-padding:16 24;"
            + "-fx-border-color:rgba(255,255,255,0.06);-fx-border-width:1 0 0 0;");
        resultPanel.setAlignment(Pos.CENTER);
        resultPanel.setVisible(false);
        resultPanel.setManaged(false);

        resultTitle = new Label(); resultTitle.setStyle("-fx-font-size:16px;-fx-font-weight:bold;");
        resultScore = new Label(); resultScore.setStyle("-fx-font-size:36px;-fx-font-weight:bold;-fx-text-fill:#fff;");
        resultWinner = new Label(); resultWinner.setStyle("-fx-font-size:13px;-fx-text-fill:#aaa;");
        resultPanel.getChildren().addAll(resultTitle, resultScore, resultWinner);
        return resultPanel;
    }

    // ─────────────────────────────────────────────────────────
    // 必殺技カットインオーバーレイ
    // ─────────────────────────────────────────────────────────
    private StackPane buildCutinOverlay() {
        // 背景（半透明ブラック）
        Rectangle bg = new Rectangle(1280, 900);
        bg.setFill(Color.web("rgba(0,0,0,0.72)"));
        // StackPaneのサイズに追従
        StackPane.setAlignment(bg, javafx.geometry.Pos.TOP_LEFT);

        // カットインボックス（斜め帯風）
        cutinBox = new HBox(16);
        cutinBox.setStyle("""
            -fx-background-color:linear-gradient(to right, #000000cc, #111122ee, #000000cc);
            -fx-padding:20 40;
            -fx-border-color:#ffd700;
            -fx-border-width:2 0;
        """);
        cutinBox.setAlignment(Pos.CENTER_LEFT);
        cutinBox.setMaxWidth(600);
        cutinBox.setMaxHeight(140);

        // 選手画像
        cutinPlayerImg = new ImageView();
        cutinPlayerImg.setFitHeight(100);
        cutinPlayerImg.setFitWidth(90);
        cutinPlayerImg.setPreserveRatio(true);

        // 必殺技演出画像
        cutinMoveImg = new ImageView();
        cutinMoveImg.setFitHeight(80);
        cutinMoveImg.setFitWidth(80);
        cutinMoveImg.setPreserveRatio(true);

        // テキスト
        cutinMoveName = new Label();
        cutinMoveName.setStyle("""
            -fx-font-size:26px;-fx-font-weight:bold;-fx-text-fill:#ffd700;
            -fx-effect:dropshadow(gaussian,black,8,0.8,0,0);
        """);
        cutinDesc = new Label();
        cutinDesc.setStyle("-fx-font-size:13px;-fx-text-fill:rgba(255,255,255,0.8);"
            + "-fx-wrap-text:true;-fx-max-width:280;");
        cutinDesc.setWrapText(true);
        VBox textBox = new VBox(6, cutinMoveName, cutinDesc);
        textBox.setAlignment(Pos.CENTER_LEFT);

        cutinBox.getChildren().addAll(cutinPlayerImg, cutinMoveImg, textBox);

        StackPane overlay = new StackPane(bg, cutinBox);
        overlay.setAlignment(Pos.CENTER);
        return overlay;
    }

    /**
     * 必殺技カットインを表示して自動で消える
     */
    private void showCutin(Player player, SpecialMove move) {
        // 選手画像をロード
        String playerUrl = ExcelLoaderService.toImageURL(player.getImageFile());
        if (playerUrl != null) {
            try { cutinPlayerImg.setImage(new Image(playerUrl)); }
            catch (Exception ignored) { cutinPlayerImg.setImage(null); }
        } else {
            cutinPlayerImg.setImage(null);
        }

        // 必殺技画像をロード
        String moveUrl = ExcelLoaderService.toImageURL(move.getImageFile());
        if (moveUrl != null) {
            try { cutinMoveImg.setImage(new Image(moveUrl)); }
            catch (Exception ignored) { cutinMoveImg.setImage(null); }
        } else {
            cutinMoveImg.setImage(null);
        }

        cutinMoveName.setText("【" + move.getName() + "】");
        cutinDesc.setText(player.getFullName() + "\n" + move.getDescription());

        // スライドイン → 少し待つ → フェードアウト
        cutinOverlay.setVisible(true);
        cutinOverlay.setOpacity(0);
        cutinBox.setTranslateX(-500);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(150), cutinOverlay);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        TranslateTransition slideIn = new TranslateTransition(Duration.millis(200), cutinBox);
        slideIn.setFromX(-500);
        slideIn.setToX(0);
        slideIn.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition enter = new ParallelTransition(fadeIn, slideIn);

        PauseTransition hold = new PauseTransition(Duration.millis(1400));

        FadeTransition exit = new FadeTransition(Duration.millis(300), cutinOverlay);
        exit.setFromValue(1); exit.setToValue(0);
        exit.setOnFinished(ev -> cutinOverlay.setVisible(false));

        new SequentialTransition(enter, hold, exit).play();
    }

    // ─────────────────────────────────────────────────────────
    // 試合開始・アニメーション
    // ─────────────────────────────────────────────────────────
    public void startMatch(Club opponent) {
        if (timeline != null) { timeline.stop(); }

        // 相手のカラーを取得
        awayColor = opponent.getColor();
        AWAY_C    = Color.web(awayColor);

        // スコアリセット
        homeScoreLabel.setText("0");
        awayScoreLabel.setText("0");
        matchTimeLabel.setText("0'");
        timeBar.setProgress(0);
        eventLogBox.getChildren().clear();
        resultPanel.setVisible(false);
        resultPanel.setManaged(false);
        callbackFired = false;
        simulateBtn.setDisable(true);

        // アウェイチーム名を更新
        if (awayNameLabel != null) {
            awayNameLabel.setStyle(
                "-fx-font-size:13px;-fx-text-fill:" + awayColor + ";-fx-font-weight:bold;");
            awayNameLabel.setText(opponent.getName());
        }

        // シミュレーション実行
        events = MainApp.matchSimulator.simulateEvents(MainApp.playerClub, opponent);
        eventIndex = 0;

        int[] delays = {30, 100, 250, 500};
        int speedIdx = speedCombo.getSelectionModel().getSelectedIndex();
        int delay = delays[Math.max(0, Math.min(speedIdx, 3))];

        // ボール滑走アニメーション
        ballAnimTimer = new AnimationTimer() {
            @Override public void handle(long now) {
                ballX += (targetBallX - ballX) * 0.15;
                ballY += (targetBallY - ballY) * 0.15;
                drawPitch(eventIndex < events.size() ? events.get(eventIndex) : null);
            }
        };
        ballAnimTimer.start();

        timeline = new Timeline(new KeyFrame(Duration.millis(delay), e -> processNextEvent()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void processNextEvent() {
        if (eventIndex >= events.size()) {
            timeline.stop();
            ballAnimTimer.stop();
            simulateBtn.setDisable(false);
            return;
        }

        MatchEvent evt = events.get(eventIndex++);
        targetBallX = evt.getBallX();
        targetBallY = evt.getBallY();
        playerPositions = MainApp.matchSimulator.getPositions(evt);

        // スコア更新
        homeScoreLabel.setText(String.valueOf(evt.getHomeScore()));
        awayScoreLabel.setText(String.valueOf(evt.getAwayScore()));
        matchTimeLabel.setText(evt.getMinute() + "'");
        timeBar.setProgress(evt.getMinute() / 90.0);

        // ポゼッション更新
        double homePct = evt.getHomePoss() / 100.0;
        possHomeBar.setWidth(homePct * 120);
        possAwayBar.setWidth((1 - homePct) * 120);
        possHomeLabel.setText(evt.getHomePoss() + "%");
        possAwayLabel.setText((100 - evt.getHomePoss()) + "%");

        // スタッツ更新
        int[] shots = evt.getShots(), shotsOn = evt.getShotsOn();
        int[] corners = evt.getCorners(), fouls = evt.getFouls();
        shotLabels[0].setText(String.valueOf(shots[0]));
        shotLabels[1].setText(String.valueOf(shots[1]));
        shotOnLabels[0].setText(String.valueOf(shotsOn[0]));
        shotOnLabels[1].setText(String.valueOf(shotsOn[1]));
        cornerLabels[0].setText(String.valueOf(corners[0]));
        cornerLabels[1].setText(String.valueOf(corners[1]));
        foulLabels[0].setText(String.valueOf(fouls[0]));
        foulLabels[1].setText(String.valueOf(fouls[1]));

        // 必殺技カットイン
        if (evt.getType() == Type.SPECIAL_MOVE && evt.getTriggerPlayer() != null) {
            timeline.pause();
            showCutin(evt.getTriggerPlayer(), evt.getSpecialMove());
            // カットイン終了後（約2秒）に再開
            new Timeline(new KeyFrame(Duration.millis(2000), x -> timeline.play())).play();
        }

        // イベントログ（重要イベントのみ）
        if (evt.getType() != Type.PASS && evt.getType() != Type.PRESS) {
            addEventLog(evt);
        }

        // 試合終了
        if (evt.getType() == Type.FULLTIME) {
            timeline.stop();
            ballAnimTimer.stop();
            simulateBtn.setDisable(false);
            showResult(evt);
            // WeeklyPlanViewからの試合は、コールバック側で結果ダイアログを表示
            if (matchResultCallback != null) {
                fireMatchResultCallback();
            }
        }
    }

    private void showResult(MatchEvent last) {
        int hg = last.getHomeScore(), ag = last.getAwayScore();
        String outcome = hg > ag ? "勝利！🎉" : hg == ag ? "引き分け" : "敗北…";
        String color   = hg > ag ? homeColor   : hg == ag ? "#ffd700"  : "#cc2200";
        resultTitle.setText(outcome);
        resultTitle.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:" + color + ";");
        resultScore.setText(hg + "  :  " + ag);
        resultWinner.setText(MainApp.playerClub.getName() + "  vs  ");

        resultPanel.setVisible(true);
        resultPanel.setManaged(true);
        FadeTransition ft = new FadeTransition(Duration.millis(600), resultPanel);
        ft.setFromValue(0); ft.setToValue(1); ft.play();
    }

    // ── WeeklyPlanViewへの結果コールバック ─────────────────────
    private java.util.function.Consumer<List<MatchEvent>> matchResultCallback;
    public void setMatchResultCallback(java.util.function.Consumer<List<MatchEvent>> cb) {
        this.matchResultCallback = cb;
    }

    private void fireMatchResultCallback() {
        if (callbackFired) return;
        callbackFired = true;
        if (matchResultCallback != null) {
            matchResultCallback.accept(events);
        }
    }
}

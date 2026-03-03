package ui;

import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.RotateEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ZoomEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import model.League;
import model.Player;
import model.SeasonManager;
import service.ExcelLoaderService;

import java.net.URL;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 基本画面（メインメニュー）。
 * 仕様書の「メインメニュー」シートに合わせ、
 * 下部メニューボタン + 右上スケジュール一覧 + 自動生成トレーニング風景を提供する。
 */
public class MainMenuView extends BorderPane {

    private final MainApp app;
    private final List<TraineeActor> trainees = new ArrayList<>();
    private final Random random = new Random();
    private AnimationTimer trainingTimer;
    private long lastNanos = 0L;
    private double animSeconds = 0.0;
    private final Map<BodyType, Image> actorSprites = new EnumMap<>(BodyType.class);
    private final Map<String, Image> playerSpriteCache = new java.util.HashMap<>();

    // 練習コースの目印（コーン位置）
    private static final double[][] DRILL_POINTS = {
        {0.13, 0.30}, {0.28, 0.68}, {0.48, 0.32}, {0.66, 0.68}, {0.84, 0.34}
    };

    public MainMenuView(MainApp app) {
        this.app = app;
        setStyle("-fx-background-color:#070713;");
        setPadding(new Insets(18));
        // この画面上ではズーム系ジェスチャを無効化
        addEventFilter(ZoomEvent.ANY, e -> e.consume());
        addEventFilter(RotateEvent.ANY, e -> e.consume());
        addEventFilter(ScrollEvent.ANY, e -> {
            if (e.isControlDown() || e.isDirect() || e.getTouchCount() > 0) e.consume();
        });
        setTop(buildClubHeader());
        setCenter(buildGeneratedTrainingPane());
        setBottom(buildBottomActions());
        setFocusTraversable(false);

        // 画面を離れたら描画タイマー停止
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null && newScene == null) stopTrainingAnimation();
        });
    }

    private HBox buildClubHeader() {
        HBox box = new HBox(14);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(12, 14, 12, 14));
        box.setStyle("-fx-background-color:#0f1024;-fx-background-radius:12;"
            + "-fx-border-color:rgba(255,255,255,0.08);-fx-border-radius:12;");

        String uUrl = ExcelLoaderService.toImageURL(MainApp.playerClub.getUniformImageFile());
        if (uUrl != null) {
            try {
                javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(new Image(uUrl));
                iv.setFitHeight(52);
                iv.setPreserveRatio(true);
                box.getChildren().add(iv);
            } catch (Exception ignored) {}
        }

        VBox text = new VBox(2);
        Label club = new Label(MainApp.playerClub.getName());
        club.setStyle("-fx-font-size:20px;-fx-font-weight:bold;-fx-text-fill:" + MainApp.playerClub.getColor() + ";");

        League league = MainApp.leagueManager.getLeagueOf(MainApp.playerClub);
        String rank = MainApp.leagueManager.getRankText(MainApp.playerClub);
        String leagueText = league == null ? "所属リーグ不明" : league.getTier().getDisplayName();
        Label info = new Label("順位: " + rank + "  |  " + leagueText);
        info.setStyle("-fx-font-size:12px;-fx-text-fill:rgba(255,255,255,0.75);-fx-cursor:hand;");
        info.setOnMouseClicked(e -> app.showStandingsView());
        text.getChildren().addAll(club, info);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button scheduleBtn = new Button("📋 スケジュール一覧");
        scheduleBtn.setStyle("-fx-background-color:#1b1b35;-fx-text-fill:#ffffff;"
            + "-fx-font-size:11px;-fx-font-weight:bold;-fx-background-radius:8;"
            + "-fx-padding:4 10;-fx-cursor:hand;");
        scheduleBtn.setOnAction(e -> app.showScheduleView());

        SeasonManager season = MainApp.season;
        int weekInMonth = ((season.getCurrentWeek() - 1) % 4) + 1;
        Label date = new Label(String.format("ゲーム内日付: %d年目  %d月 第%d週",
            season.getCurrentSeason(), season.getCurrentMonth(), weekInMonth));
        date.setStyle("-fx-font-size:12px;-fx-text-fill:#b6d0ff;");

        box.getChildren().addAll(text, spacer, scheduleBtn, date);
        return box;
    }

    private StackPane buildGeneratedTrainingPane() {
        StackPane area = new StackPane();
        area.setStyle("-fx-background-color:#0b0c1a;-fx-background-radius:12;"
            + "-fx-border-color:rgba(255,255,255,0.06);-fx-border-radius:12;");
        BorderPane.setMargin(area, new Insets(14, 0, 14, 0));

        Canvas canvas = new Canvas(1100, 430);
        // Canvasをレイアウト計算対象から外し、親子サイズの循環参照を避ける。
        canvas.setManaged(false);
        area.widthProperty().addListener((obs, ov, nv) ->
            canvas.setWidth(Math.max(1.0, nv.doubleValue())));
        area.heightProperty().addListener((obs, ov, nv) ->
            canvas.setHeight(Math.max(1.0, nv.doubleValue())));
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(area.widthProperty());
        clip.heightProperty().bind(area.heightProperty());
        area.setClip(clip);
        // 中央演出は操作不要。クリック系の当たり判定を持たせない
        canvas.setMouseTransparent(true);
        area.setMouseTransparent(false);

        Label title = new Label("AUTO TRAINING SCENE");
        title.setStyle("-fx-font-size:22px;-fx-font-weight:bold;"
            + "-fx-text-fill:rgba(255,255,255,0.76);");
        StackPane.setAlignment(title, Pos.TOP_CENTER);
        StackPane.setMargin(title, new Insets(16, 0, 0, 0));

        Label subtitle = new Label("スカッド選手の練習風景を自動生成（全身風 + キック動作）");
        subtitle.setStyle("-fx-font-size:12px;-fx-text-fill:rgba(255,255,255,0.72);");
        StackPane.setAlignment(subtitle, Pos.TOP_CENTER);
        StackPane.setMargin(subtitle, new Insets(44, 0, 0, 0));

        area.getChildren().addAll(canvas, title, subtitle);

        // レイアウト確定後にアニメーション開始（canvas が 0→実寸に変わるズーム防止）
        javafx.application.Platform.runLater(() -> {
            canvas.setWidth(Math.max(1.0, area.getWidth()));
            canvas.setHeight(Math.max(1.0, area.getHeight()));
            initTrainees();
            startTrainingAnimation(canvas);
        });
        return area;
    }

    private VBox buildBottomActions() {
        VBox panel = new VBox(8);
        panel.setPadding(new Insets(12));
        panel.setStyle("-fx-background-color:#0f1024;-fx-background-radius:12;"
            + "-fx-border-color:rgba(255,255,255,0.08);-fx-border-radius:12;");
        panel.setMinHeight(120);

        Label title = new Label("メインメニュー");
        title.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#ffffff;");

        FlowPane buttons = new FlowPane();
        buttons.setHgap(10);
        buttons.setVgap(10);
        buttons.setPrefWrapLength(1040);
        buttons.getChildren().addAll(
            makeMenuButton("📅  次週のスケジュール", "#8a7df0", app::showScheduleView),
            makeMenuButton("🏋️  練習", "#3a7bd5", app::showTrainingView),
            makeMenuButton("🧩  人事", "#f08a24", app::showPersonnelView),
            makeMenuButton("🧠  編成", "#5cab6f", app::showSquadView),
            makeMenuButton("🏛️  クラブ管理", "#b189ff", app::showClubView),
            makeMenuButton("⏭️  日程進行", "#4a7a35", app::showWeeklyView)
        );

        panel.getChildren().addAll(title, buttons);
        return panel;
    }

    private Button makeMenuButton(String text, String color, Runnable action) {
        Button btn = new Button(text);
        btn.setPrefWidth(190);
        btn.setPrefHeight(44);
        btn.setAlignment(Pos.CENTER);
        btn.setStyle("-fx-background-color:" + color + "22;"
            + "-fx-text-fill:#ffffff;-fx-font-size:14px;-fx-font-weight:bold;"
            + "-fx-padding:10 12;-fx-background-radius:10;-fx-cursor:hand;"
            + "-fx-border-color:" + color + "66;-fx-border-radius:10;-fx-border-width:1;");
        btn.setOnAction(e -> action.run());
        return btn;
    }

    // ── 自動生成トレーニングアニメーション ─────────────────────
    private void initTrainees() {
        trainees.clear();
        List<Player> src = MainApp.playerClub.getRegistered().isEmpty()
            ? MainApp.playerClub.getSquad()
            : MainApp.playerClub.getRegistered();
        int limit = Math.min(8, src.size());
        for (int i = 0; i < limit; i++) {
            Player p = src.get(i);
            TraineeActor a = new TraineeActor();
            String uniform = p.getUniformName().isBlank() ? p.getName() : p.getUniformName();
            a.uniformName = uniform;
            a.name = shortName(uniform);
            a.bodyType = classifyBodyType(p.getBreed());
            a.scale = bodyScale(a.bodyType);
            a.sprite = loadPlayerBodySprite(uniform);
            if (a.sprite == null) a.sprite = loadBodySprite(a.bodyType);
            a.speed = (65 + random.nextDouble() * 34) / a.scale;
            a.tint = Color.hsb((i * 41) % 360, 0.62, 0.95);
            a.kickCooldown = 0.5 + random.nextDouble() * 0.8;
            trainees.add(a);
        }
    }

    private BodyType classifyBodyType(String breed) {
        if (breed == null) return BodyType.MEDIUM;
        String b = breed.toLowerCase();
        // 小型犬
        if (b.contains("チワワ") || b.contains("ポメ") || b.contains("マルチーズ")
            || b.contains("シーズー") || b.contains("ダックス")
            || b.contains("pomeranian") || b.contains("chihuahua")
            || b.contains("maltese") || b.contains("dachshund")
            || b.contains("toy")) {
            return BodyType.SMALL;
        }
        // 大型犬
        if (b.contains("セントバーナード") || b.contains("マスティフ")
            || b.contains("秋田") || b.contains("シェパード")
            || b.contains("ゴールデン") || b.contains("バーナード")
            || b.contains("mastiff") || b.contains("shepherd")
            || b.contains("retriever") || b.contains("st. bernard")) {
            return BodyType.LARGE;
        }
        return BodyType.MEDIUM;
    }

    private double bodyScale(BodyType t) {
        return switch (t) {
            case SMALL -> 0.84;
            case MEDIUM -> 1.00;
            case LARGE -> 1.20;
        };
    }

    private String shortName(String s) {
        if (s == null || s.isBlank()) return "DOG";
        return s.length() <= 6 ? s : s.substring(0, 6);
    }

    private Image loadBodySprite(BodyType type) {
        if (actorSprites.containsKey(type)) return actorSprites.get(type);
        String file = switch (type) {
            case SMALL -> "/images/training/actor_small.png";
            case MEDIUM -> "/images/training/actor_medium.png";
            case LARGE -> "/images/training/actor_large.png";
        };
        try {
            URL url = MainMenuView.class.getResource(file);
            if (url != null) {
                Image img = new Image(url.toExternalForm(), true);
                actorSprites.put(type, img);
                return img;
            }
        } catch (Exception ignored) {}
        actorSprites.put(type, null);
        return null;
    }

    private Image loadPlayerBodySprite(String uniformName) {
        if (uniformName == null || uniformName.isBlank()) return null;
        String key = uniformName.trim().toUpperCase();
        if (playerSpriteCache.containsKey(key)) return playerSpriteCache.get(key);

        String lower = key.toLowerCase();
        String[] candidates = {
            "/images/training/players/" + lower + ".png",
            "/images/training/players/" + key + ".png",
            "/images/training/players/actor_" + lower + ".png",
            "/images/training/players/full_" + lower + ".png"
        };
        for (String c : candidates) {
            try {
                URL url = MainMenuView.class.getResource(c);
                if (url != null) {
                    Image img = new Image(url.toExternalForm(), true);
                    playerSpriteCache.put(key, img);
                    return img;
                }
            } catch (Exception ignored) {}
        }
        playerSpriteCache.put(key, null);
        return null;
    }

    private void startTrainingAnimation(Canvas canvas) {
        stopTrainingAnimation();
        lastNanos = 0L;
        animSeconds = 0.0;

        trainingTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastNanos == 0L) {
                    lastNanos = now;
                    return;
                }
                // 端末負荷を抑えるため、約45fps上限で描画
                if (now - lastNanos < 22_000_000L) return;
                double dt = Math.min(0.05, (now - lastNanos) / 1_000_000_000.0);
                lastNanos = now;
                animSeconds += dt;

                updateTrainees(dt, canvas.getWidth(), canvas.getHeight());
                drawTrainingScene(canvas.getGraphicsContext2D(), canvas.getWidth(), canvas.getHeight());
            }
        };
        trainingTimer.start();
    }

    private void stopTrainingAnimation() {
        if (trainingTimer != null) {
            trainingTimer.stop();
            trainingTimer = null;
        }
    }

    private void updateTrainees(double dt, double w, double h) {
        if (w < 30 || h < 30 || trainees.isEmpty()) return;

        for (int i = 0; i < trainees.size(); i++) {
            TraineeActor a = trainees.get(i);
            if (!a.ready) {
                double lane = (i + 1.0) / (trainees.size() + 1.0);
                a.x = w * 0.08 + random.nextDouble() * 28;
                a.y = h * (0.24 + lane * 0.54);
                a.dirX = 1.0;
                a.dirY = 0.0;
                assignNextTarget(a, w, h);
                a.ballX = a.x + 12;
                a.ballY = a.y + 26;
                a.ready = true;
            }

            // 目標点へ移動
            double dx = a.targetX - a.x;
            double dy = a.targetY - a.y;
            double dist = Math.hypot(dx, dy);
            if (dist < 8) {
                assignNextTarget(a, w, h);
            } else {
                double step = Math.min(a.speed * dt, dist);
                a.dirX = dx / dist;
                a.dirY = dy / dist;
                a.x += a.dirX * step;
                a.y += a.dirY * step;
            }

            // キック間隔管理
            a.kickCooldown -= dt;
            if (a.kickCooldown <= 0 && dist > 44 && a.kickPhase <= 0) {
                a.kickPhase = 0.26; // キック動作中
                a.kickCooldown = 1.1 + random.nextDouble() * 1.3;
                a.ballVx = a.dirX * (135 + random.nextDouble() * 45);
                a.ballVy = a.dirY * (95 + random.nextDouble() * 35);
            } else if (a.kickPhase > 0) {
                a.kickPhase -= dt;
            }

            // ボール更新
            a.ballX += a.ballVx * dt;
            a.ballY += a.ballVy * dt;
            a.ballVx *= Math.pow(0.80, dt * 10.0);
            a.ballVy *= Math.pow(0.80, dt * 10.0);

            double footX = a.x + a.dirX * 12;
            double footY = a.y + 26;
            if (a.kickPhase <= 0) {
                // 通常時は足元へ戻る
                a.ballX += (footX - a.ballX) * 0.18;
                a.ballY += (footY - a.ballY) * 0.18;
            }

            // 枠外防止
            a.ballX = clamp(a.ballX, 20, w - 20);
            a.ballY = clamp(a.ballY, h * 0.22, h - 16);
        }
    }

    private void assignNextTarget(TraineeActor a, double w, double h) {
        int p = random.nextInt(DRILL_POINTS.length);
        double tx = w * DRILL_POINTS[p][0] + random.nextDouble() * 22 - 11;
        double ty = h * DRILL_POINTS[p][1] + random.nextDouble() * 18 - 9;
        a.targetX = clamp(tx, 28, w - 28);
        a.targetY = clamp(ty, h * 0.22, h - 26);
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private void drawTrainingScene(GraphicsContext gc, double w, double h) {
        if (w < 30 || h < 30) return;

        // 空
        LinearGradient sky = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#12243a")), new Stop(1, Color.web("#0a1421")));
        gc.setFill(sky);
        gc.fillRect(0, 0, w, h);

        // ピッチ
        gc.setFill(Color.web("#173b22"));
        gc.fillRect(0, h * 0.35, w, h * 0.65);
        gc.setFill(Color.web("rgba(255,255,255,0.05)"));
        for (int i = 0; i < 10; i++) {
            if (i % 2 == 0) gc.fillRect(i * w / 10.0, h * 0.35, w / 10.0, h * 0.65);
        }

        gc.setStroke(Color.web("rgba(255,255,255,0.26)"));
        gc.setLineWidth(2.0);
        gc.strokeRect(16, h * 0.43, w - 32, h * 0.49);
        gc.strokeOval(w * 0.43, h * 0.52, w * 0.14, h * 0.18);

        // コーン
        for (double[] pt : DRILL_POINTS) {
            double x = w * pt[0];
            double y = h * pt[1];
            gc.setFill(Color.web("#f1901c"));
            gc.fillRoundRect(x - 7, y - 5, 14, 10, 4, 4);
            gc.setFill(Color.web("rgba(255,255,255,0.58)"));
            gc.fillOval(x - 1.5, y - 1.5, 3, 3);
        }

        for (int i = 0; i < trainees.size(); i++) {
            drawActor(gc, trainees.get(i), i);
        }
    }

    private void drawActor(GraphicsContext gc, TraineeActor a, int index) {
        double s = a.scale;
        double headR = 12 * s;
        double torsoW = 24 * s;
        double torsoH = 32 * s;
        double legLen = 19 * s;
        double armLen = 10 * s;

        // 影
        gc.setFill(Color.web("rgba(0,0,0,0.35)"));
        gc.fillOval(a.x - 15 * s, a.y + 30 * s, 30 * s, 8 * s);

        // 全身スプライトがあれば優先描画（高品質素材運用）
        if (a.sprite != null && !a.sprite.isError()) {
            double bob = Math.sin(animSeconds * 6.0 + index) * (1.2 * s);
            double sw = 56 * s;
            double sh = 84 * s;
            gc.drawImage(a.sprite, a.x - sw * 0.5, a.y - sh * 0.82 + bob, sw, sh);

            // スプライト時もキック感を出すため、前足ラインだけ重ねる
            double hipY2 = a.y + 22 * s;
            gc.setStroke(Color.web("#f5e6d0"));
            gc.setLineWidth(2.6 * s);
            if (a.kickPhase > 0) {
                double k = 1.0 - (a.kickPhase / 0.26);
                double ex = a.x + a.dirX * (10 * s + (18 * s) * k);
                double ey = hipY2 + a.dirY * (4 * s + (11 * s) * k);
                gc.strokeLine(a.x + 3 * s, hipY2, ex, ey);
            }
            drawBall(gc, a.ballX, a.ballY, Math.hypot(a.ballVx, a.ballVy), s);

            gc.setFont(Font.font("Arial", FontWeight.BOLD, Math.max(9, 11 * s)));
            gc.setFill(Color.WHITE);
            gc.fillText(a.name, a.x - 18 * s, a.y + 48 * s);
            return;
        }

        // 頭
        double hx = a.x;
        double hy = a.y - 22 * s;
        gc.setFill(Color.web("#e9d8c2"));
        gc.fillOval(hx - headR, hy - headR, headR * 2, headR * 2);
        gc.setStroke(Color.web("#ffffff"));
        gc.setLineWidth(1.2);
        gc.strokeOval(hx - headR, hy - headR, headR * 2, headR * 2);

        // 耳（体格で形状を変える）
        gc.setFill(Color.web("#e2cdb4"));
        switch (a.bodyType) {
            case SMALL -> {
                gc.fillOval(hx - headR * 1.2, hy - headR * 1.2, 6 * s, 10 * s);
                gc.fillOval(hx + headR * 0.8, hy - headR * 1.2, 6 * s, 10 * s);
            }
            case MEDIUM -> {
                gc.fillOval(hx - headR * 1.1, hy - headR * 0.9, 7 * s, 9 * s);
                gc.fillOval(hx + headR * 0.7, hy - headR * 0.9, 7 * s, 9 * s);
            }
            case LARGE -> {
                gc.fillRoundRect(hx - headR * 1.05, hy - headR * 0.45, 7 * s, 11 * s, 3, 3);
                gc.fillRoundRect(hx + headR * 0.65, hy - headR * 0.45, 7 * s, 11 * s, 3, 3);
            }
        }

        // 胴体（ユニフォーム）
        double bx = a.x - torsoW * 0.5;
        double by = a.y - 8 * s;
        gc.setFill(Color.web("#2a7a43"));
        gc.fillRoundRect(bx, by, torsoW, torsoH, 8 * s, 8 * s);
        gc.setStroke(Color.web("#cce8cf"));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(bx, by, torsoW, torsoH, 8 * s, 8 * s);

        // 背番号
        gc.setFill(Color.web("rgba(255,255,255,0.92)"));
        gc.setFont(Font.font("Arial", FontWeight.BOLD, Math.max(8, 10 * s)));
        gc.fillText(String.valueOf((index % 11) + 1), a.x - 3 * s, a.y + 11 * s);

        // 腕（ランニング）
        double armSwing = Math.sin(animSeconds * 8.0 + index) * 4.0 * s;
        gc.setStroke(Color.web("#f5e6d0"));
        gc.setLineWidth(2.2 * s);
        gc.strokeLine(a.x - 10 * s, a.y, a.x - (10 * s + armLen), a.y + 9 * s + armSwing);
        gc.strokeLine(a.x + 10 * s, a.y, a.x + (10 * s + armLen), a.y + 9 * s - armSwing);

        // 脚（キック動作）
        double hipY = a.y + 22 * s;
        double walk = Math.sin(animSeconds * 7.0 + index) * 4.0 * s;
        gc.setStroke(Color.web("#f5e6d0"));
        gc.setLineWidth(2.8 * s);

        if (a.kickPhase > 0) {
            double k = 1.0 - (a.kickPhase / 0.26); // 0→1
            double ex = a.x + a.dirX * (10 * s + (16 * s) * k);
            double ey = hipY + a.dirY * (4 * s + (10 * s) * k);

            // 支持脚
            gc.strokeLine(a.x - 4 * s, hipY, a.x - 6 * s, hipY + legLen);
            // キック脚
            gc.strokeLine(a.x + 3 * s, hipY, ex, ey);
        } else {
            gc.strokeLine(a.x - 4 * s, hipY, a.x - 8 * s, hipY + legLen + walk);
            gc.strokeLine(a.x + 4 * s, hipY, a.x + 8 * s, hipY + legLen - walk);
        }

        // ボール
        drawBall(gc, a.ballX, a.ballY, Math.hypot(a.ballVx, a.ballVy), s);

        // 名前
        gc.setFont(Font.font("Arial", FontWeight.BOLD, Math.max(9, 11 * s)));
        gc.setFill(Color.WHITE);
        gc.fillText(a.name, a.x - 18 * s, a.y + 48 * s);
    }

    private void drawBall(GraphicsContext gc, double x, double y, double speed, double s) {
        if (speed > 25) {
            gc.setStroke(Color.web("rgba(255,255,255,0.25)"));
            gc.setLineWidth(1.2);
            gc.strokeArc(x - 9 * s, y - 9 * s, 18 * s, 18 * s, 200, 120, ArcType.OPEN);
        }
        gc.setFill(Color.WHITE);
        gc.fillOval(x - 4.6 * s, y - 4.6 * s, 9.2 * s, 9.2 * s);
        gc.setStroke(Color.web("#7a7f8a"));
        gc.setLineWidth(0.7);
        gc.strokeOval(x - 4.6 * s, y - 4.6 * s, 9.2 * s, 9.2 * s);
    }

    private enum BodyType { SMALL, MEDIUM, LARGE }

    private static final class TraineeActor {
        String uniformName;
        String name;
        BodyType bodyType;
        Image sprite;
        Color tint;
        double scale;
        double x;
        double y;
        double targetX;
        double targetY;
        double speed;
        double dirX;
        double dirY;
        double ballX;
        double ballY;
        double ballVx;
        double ballVy;
        double kickCooldown;
        double kickPhase;
        boolean ready;
    }
}

package ui;

import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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
import model.Club;
import model.League;
import model.Player;
import service.ExcelLoaderService;
import service.TrainingService;

import java.net.URL;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 練習メニュー画面（仕様書 5. 練習画面）
 * 上部ヘッダー + 中央練習背景 + 下部メニュー構成。
 */
public class TrainingView extends BorderPane {

    private final MainApp app;
    private final Map<TrainingService.Menu, Button> menuButtons =
        new EnumMap<>(TrainingService.Menu.class);
    private final Map<TrainingService.Menu, String> menuColors =
        new EnumMap<>(TrainingService.Menu.class);

    private Label selectedMenuLabel;
    private Label previewLabel;
    private TrainingScenePane trainingScenePane;

    public TrainingView(MainApp app) {
        this.app = app;
        setStyle("-fx-background-color:#08081a;");
        setTop(buildHeader());
        setCenter(buildBackgroundArea());
        setBottom(buildBottomMenuPanel());
        refreshSelection();

        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null && trainingScenePane != null) {
                trainingScenePane.stop();
            } else if (newScene != null && trainingScenePane != null) {
                trainingScenePane.start();
            }
        });
    }

    private HBox buildHeader() {
        Club club = MainApp.playerClub;
        HBox bar = new HBox(14);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color:#0d0d22;-fx-padding:14 20;"
            + "-fx-border-color:rgba(255,255,255,0.08);-fx-border-width:0 0 1 0;");

        String emblemUrl = ExcelLoaderService.toImageURL(club.getUniformImageFile());
        if (emblemUrl != null) {
            try {
                ImageView iv = new ImageView(new Image(emblemUrl, 46, 46, true, true));
                iv.setFitWidth(42);
                iv.setFitHeight(42);
                iv.setPreserveRatio(true);
                bar.getChildren().add(iv);
            } catch (Exception ignored) {}
        }

        VBox clubInfo = new VBox(2);
        Label clubName = new Label(club.getName());
        clubName.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:" + club.getColor() + ";");

        League league = MainApp.leagueManager.getLeagueOf(club);
        String tier = (league == null) ? "所属リーグ不明" : league.getTier().getDisplayName();
        String rank = MainApp.leagueManager.getRankText(club);
        Label rankLabel = new Label("順位: " + rank + "  |  " + tier);
        rankLabel.setStyle("-fx-font-size:12px;-fx-text-fill:#ffd700;-fx-cursor:hand;");
        rankLabel.setOnMouseClicked(e -> app.showStandingsView());
        clubInfo.getChildren().addAll(clubName, rankLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button scheduleBtn = smallButton("📅 次週のスケジュール");
        scheduleBtn.setOnAction(e -> app.showScheduleView());

        Button mainBtn = smallButton("🏠 メインメニュー");
        mainBtn.setOnAction(e -> app.showMainMenu());

        bar.getChildren().addAll(clubInfo, spacer, scheduleBtn, mainBtn);
        return bar;
    }

    private StackPane buildBackgroundArea() {
        StackPane area = new StackPane();
        area.setStyle("-fx-background-color:#0b0c1a;");
        area.setPadding(new Insets(14, 20, 10, 20));

        trainingScenePane = new TrainingScenePane();

        Label sceneTitle = new Label("AUTO TRAINING GROUND");
        sceneTitle.setStyle("-fx-font-size:20px;-fx-font-weight:bold;"
            + "-fx-text-fill:rgba(255,255,255,0.82);");
        StackPane.setAlignment(sceneTitle, Pos.TOP_LEFT);
        StackPane.setMargin(sceneTitle, new Insets(14, 0, 0, 14));

        Label sceneSub = new Label("選手が練習している様子（仕様準拠）");
        sceneSub.setStyle("-fx-font-size:11px;-fx-text-fill:rgba(255,255,255,0.7);");
        StackPane.setAlignment(sceneSub, Pos.TOP_LEFT);
        StackPane.setMargin(sceneSub, new Insets(42, 0, 0, 14));

        area.getChildren().addAll(trainingScenePane, sceneTitle, sceneSub);
        return area;
    }

    private VBox buildBottomMenuPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(12, 20, 16, 20));
        panel.setStyle("-fx-background-color:#0f1024;"
            + "-fx-border-color:rgba(255,255,255,0.08);-fx-border-width:1 0 0 0;");

        Label title = new Label("🏋️ 練習メニュー");
        title.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#ffffff;");

        selectedMenuLabel = new Label();
        selectedMenuLabel.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#9be3ff;");

        FlowPane menuGrid = new FlowPane();
        menuGrid.setHgap(10);
        menuGrid.setVgap(10);
        menuGrid.setPrefWrapLength(980);

        addMenuButton(menuGrid, TrainingService.Menu.SHOOT,
            "⚽ シュートトレーニング", "シュート能力向上", "#d14a4a");
        addMenuButton(menuGrid, TrainingService.Menu.PASS,
            "🎯 パストレーニング", "パス能力向上", "#2c7fb8");
        addMenuButton(menuGrid, TrainingService.Menu.DRIBBLE,
            "💨 ドリブルトレーニング", "ドリブル能力向上", "#7a5cff");
        addMenuButton(menuGrid, TrainingService.Menu.RUN,
            "🏃 ラントレーニング", "ドッグランへお出かけ（スタミナ向上）", "#28a745");
        addMenuButton(menuGrid, TrainingService.Menu.PHYSICAL,
            "💪 フィジカルトレーニング", "フィジカル能力向上", "#ff8a00");
        addMenuButton(menuGrid, TrainingService.Menu.IQ,
            "🧠 IQトレーニング", "芸を覚える（soccer_iq向上）", "#00a8a8");
        addMenuButton(menuGrid, TrainingService.Menu.REST,
            "😴 休息", "お昼寝（コンディション回復）", "#6666aa");

        previewLabel = new Label();
        previewLabel.setWrapText(true);
        previewLabel.setStyle("-fx-font-size:12px;-fx-text-fill:rgba(255,255,255,0.75);");

        panel.getChildren().addAll(title, selectedMenuLabel, menuGrid, previewLabel);
        return panel;
    }

    private void addMenuButton(FlowPane pane,
                               TrainingService.Menu menu,
                               String title,
                               String desc,
                               String color) {
        menuColors.put(menu, color);
        Button btn = new Button(title + "\n" + desc);
        btn.setPrefWidth(280);
        btn.setPrefHeight(62);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setOnAction(e -> {
            MainApp.trainingService.setSelectedMenu(menu);
            refreshSelection();
        });
        menuButtons.put(menu, btn);
        pane.getChildren().add(btn);
    }

    private void refreshSelection() {
        TrainingService.Menu selected = MainApp.trainingService.getSelectedMenu();
        selectedMenuLabel.setText("現在の選択: " + selected.label());
        previewLabel.setText("次回の日程進行では「" + selected.label()
            + "」が適用されます。TrainingService経由で能力値更新を行います。");

        menuButtons.forEach((menu, btn) ->
            btn.setStyle(menuButtonStyle(menuColors.get(menu), menu == selected)));
    }

    private String menuButtonStyle(String color, boolean active) {
        if (active) {
            return "-fx-background-color:" + color + "44;"
                + "-fx-text-fill:#ffffff;-fx-font-size:12px;-fx-font-weight:bold;"
                + "-fx-padding:8 12;-fx-background-radius:10;-fx-cursor:hand;"
                + "-fx-border-color:" + color + ";-fx-border-width:1.5;-fx-border-radius:10;";
        }
        return "-fx-background-color:rgba(255,255,255,0.05);"
            + "-fx-text-fill:rgba(255,255,255,0.72);-fx-font-size:12px;"
            + "-fx-padding:8 12;-fx-background-radius:10;-fx-cursor:hand;"
            + "-fx-border-color:rgba(255,255,255,0.12);-fx-border-width:1;-fx-border-radius:10;";
    }

    private Button smallButton(String text) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color:#1b1b35;-fx-text-fill:#ffffff;"
            + "-fx-font-size:11px;-fx-font-weight:bold;-fx-background-radius:8;"
            + "-fx-padding:5 12;-fx-cursor:hand;");
        return btn;
    }

    private static final class TrainingScenePane extends StackPane {
        private static final double[][] DRILL_POINTS = {
            {0.12, 0.28}, {0.28, 0.64}, {0.47, 0.30}, {0.66, 0.66}, {0.84, 0.34}
        };

        private final Canvas canvas = new Canvas(1200, 420);
        private final Random random = new Random();
        private final List<Runner> runners = new ArrayList<>();
        private Image actorImage;
        private AnimationTimer timer;
        private long lastNanos = 0L;
        private double animSeconds = 0.0;

        private TrainingScenePane() {
            setStyle("-fx-background-color:#0b0c1a;-fx-background-radius:12;"
                + "-fx-border-color:rgba(255,255,255,0.08);-fx-border-radius:12;");

            canvas.setManaged(false);
            widthProperty().addListener((obs, ov, nv) -> canvas.setWidth(Math.max(1.0, nv.doubleValue())));
            heightProperty().addListener((obs, ov, nv) -> canvas.setHeight(Math.max(1.0, nv.doubleValue())));

            Rectangle clip = new Rectangle();
            clip.widthProperty().bind(widthProperty());
            clip.heightProperty().bind(heightProperty());
            setClip(clip);

            getChildren().add(canvas);
            loadActorImage();
            initRunners();
            start();
        }

        private void loadActorImage() {
            try {
                URL url = TrainingScenePane.class.getResource("/images/training/actor_medium.png");
                if (url != null) actorImage = new Image(url.toExternalForm(), true);
            } catch (Exception ignored) {}
        }

        private void initRunners() {
            runners.clear();
            List<Player> src = MainApp.playerClub.getRegistered().isEmpty()
                ? MainApp.playerClub.getSquad()
                : MainApp.playerClub.getRegistered();
            int limit = Math.min(8, src.size());
            for (int i = 0; i < limit; i++) {
                Player p = src.get(i);
                Runner r = new Runner();
                String uniform = p.getUniformName().isBlank() ? p.getName() : p.getUniformName();
                r.name = uniform.length() <= 6 ? uniform : uniform.substring(0, 6);
                r.speed = 68 + random.nextDouble() * 32;
                r.kickCooldown = 0.7 + random.nextDouble() * 0.9;
                runners.add(r);
            }
        }

        private void start() {
            if (timer != null) return;
            timer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    if (lastNanos == 0L) {
                        lastNanos = now;
                        return;
                    }
                    if (now - lastNanos < 22_000_000L) return;
                    double dt = Math.min(0.05, (now - lastNanos) / 1_000_000_000.0);
                    lastNanos = now;
                    animSeconds += dt;

                    update(dt, canvas.getWidth(), canvas.getHeight());
                    draw(canvas.getGraphicsContext2D(), canvas.getWidth(), canvas.getHeight());
                }
            };
            timer.start();
        }

        private void stop() {
            if (timer != null) {
                timer.stop();
                timer = null;
            }
            lastNanos = 0L;
        }

        private void update(double dt, double w, double h) {
            if (w < 30 || h < 30 || runners.isEmpty()) return;

            for (int i = 0; i < runners.size(); i++) {
                Runner r = runners.get(i);
                if (!r.ready) {
                    double lane = (i + 1.0) / (runners.size() + 1.0);
                    r.x = w * 0.08 + random.nextDouble() * 32;
                    r.y = h * (0.24 + lane * 0.52);
                    r.dirX = 1.0;
                    r.dirY = 0.0;
                    assignNextTarget(r, w, h);
                    r.ballX = r.x + 12;
                    r.ballY = r.y + 24;
                    r.ready = true;
                }

                double dx = r.targetX - r.x;
                double dy = r.targetY - r.y;
                double dist = Math.hypot(dx, dy);
                if (dist < 8) {
                    assignNextTarget(r, w, h);
                } else {
                    double step = Math.min(r.speed * dt, dist);
                    r.dirX = dx / dist;
                    r.dirY = dy / dist;
                    r.x += r.dirX * step;
                    r.y += r.dirY * step;
                }

                r.kickCooldown -= dt;
                if (r.kickCooldown <= 0 && dist > 44 && r.kickPhase <= 0) {
                    r.kickPhase = 0.22;
                    r.kickCooldown = 1.1 + random.nextDouble() * 1.2;
                    r.ballVx = r.dirX * (135 + random.nextDouble() * 45);
                    r.ballVy = r.dirY * (90 + random.nextDouble() * 35);
                } else if (r.kickPhase > 0) {
                    r.kickPhase -= dt;
                }

                r.ballX += r.ballVx * dt;
                r.ballY += r.ballVy * dt;
                r.ballVx *= Math.pow(0.80, dt * 10.0);
                r.ballVy *= Math.pow(0.80, dt * 10.0);

                double footX = r.x + r.dirX * 12;
                double footY = r.y + 24;
                if (r.kickPhase <= 0) {
                    r.ballX += (footX - r.ballX) * 0.18;
                    r.ballY += (footY - r.ballY) * 0.18;
                }

                r.ballX = clamp(r.ballX, 20, w - 20);
                r.ballY = clamp(r.ballY, h * 0.20, h - 16);
            }
        }

        private void assignNextTarget(Runner r, double w, double h) {
            int p = random.nextInt(DRILL_POINTS.length);
            double tx = w * DRILL_POINTS[p][0] + random.nextDouble() * 22 - 11;
            double ty = h * DRILL_POINTS[p][1] + random.nextDouble() * 18 - 9;
            r.targetX = clamp(tx, 28, w - 28);
            r.targetY = clamp(ty, h * 0.22, h - 26);
        }

        private void draw(GraphicsContext gc, double w, double h) {
            if (w < 30 || h < 30) return;

            LinearGradient sky = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#12243a")), new Stop(1, Color.web("#0a1421")));
            gc.setFill(sky);
            gc.fillRect(0, 0, w, h);

            gc.setFill(Color.web("#173b22"));
            gc.fillRect(0, h * 0.34, w, h * 0.66);
            gc.setFill(Color.web("rgba(255,255,255,0.05)"));
            for (int i = 0; i < 10; i++) {
                if (i % 2 == 0) gc.fillRect(i * w / 10.0, h * 0.34, w / 10.0, h * 0.66);
            }

            gc.setStroke(Color.web("rgba(255,255,255,0.26)"));
            gc.setLineWidth(2.0);
            gc.strokeRect(16, h * 0.43, w - 32, h * 0.49);
            gc.strokeOval(w * 0.43, h * 0.52, w * 0.14, h * 0.18);

            for (double[] pt : DRILL_POINTS) {
                double x = w * pt[0];
                double y = h * pt[1];
                gc.setFill(Color.web("#f1901c"));
                gc.fillRoundRect(x - 7, y - 5, 14, 10, 4, 4);
                gc.setFill(Color.web("rgba(255,255,255,0.58)"));
                gc.fillOval(x - 1.5, y - 1.5, 3, 3);
            }

            for (int i = 0; i < runners.size(); i++) {
                drawRunner(gc, runners.get(i), i);
            }
        }

        private void drawRunner(GraphicsContext gc, Runner r, int index) {
            double s = 1.0;
            gc.setFill(Color.web("rgba(0,0,0,0.35)"));
            gc.fillOval(r.x - 15 * s, r.y + 28 * s, 30 * s, 8 * s);

            if (actorImage != null && !actorImage.isError()) {
                double bob = Math.sin(animSeconds * 6.0 + index) * 1.2;
                double sw = 56 * s;
                double sh = 84 * s;
                gc.drawImage(actorImage, r.x - sw * 0.5, r.y - sh * 0.82 + bob, sw, sh);
            } else {
                gc.setFill(Color.web("#e9d8c2"));
                gc.fillOval(r.x - 11, r.y - 34, 22, 22);
                gc.setFill(Color.web("#2a7a43"));
                gc.fillRoundRect(r.x - 12, r.y - 8, 24, 32, 8, 8);
            }

            gc.setFont(Font.font("Arial", FontWeight.BOLD, 11));
            gc.setFill(Color.WHITE);
            gc.fillText(r.name, r.x - 18, r.y + 46);

            drawBall(gc, r.ballX, r.ballY, Math.hypot(r.ballVx, r.ballVy));
        }

        private void drawBall(GraphicsContext gc, double x, double y, double speed) {
            if (speed > 25) {
                gc.setStroke(Color.web("rgba(255,255,255,0.25)"));
                gc.setLineWidth(1.2);
                gc.strokeArc(x - 9, y - 9, 18, 18, 200, 120, ArcType.OPEN);
            }
            gc.setFill(Color.WHITE);
            gc.fillOval(x - 4.6, y - 4.6, 9.2, 9.2);
            gc.setStroke(Color.web("#7a7f8a"));
            gc.setLineWidth(0.7);
            gc.strokeOval(x - 4.6, y - 4.6, 9.2, 9.2);
        }

        private double clamp(double v, double min, double max) {
            return Math.max(min, Math.min(max, v));
        }

        private static final class Runner {
            String name;
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
}

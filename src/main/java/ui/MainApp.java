package ui;

import db.DatabaseManager;
import db.PlayerDao;
import db.SpecialMoveDao;
import db.ImageDao;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import model.*;
import service.*;

import java.util.ArrayList;
import java.util.List;

public class MainApp extends Application {

    public static Club         playerClub;
    public static List<Club>   allClubs      = new ArrayList<>();
    public static LeagueManager leagueManager = new LeagueManager();

    public static MatchSimulator     matchSimulator  = new MatchSimulator();
    public static ScoutService       scoutService    = new ScoutService();
    public static TrainingService    trainingService = new TrainingService();
    public static FinanceService     financeService  = new FinanceService();
    public static GameDataService    gameDataService = new GameDataService();
    public static ExcelLoaderService excelLoader     = new ExcelLoaderService();
    public static SpecialMoveDao     specialMoveDao  = new SpecialMoveDao();
    public static ImageDao           imageDao        = new ImageDao();
    public static SeasonManager      season          = new SeasonManager();
    public static WeeklyEventService weeklyEventService = new WeeklyEventService();
    public static model.FriendshipManager friendshipManager = new model.FriendshipManager();
    public static service.AcademyService    academyService    = new service.AcademyService();
    public static service.RetirementService retirementService = new service.RetirementService();
    public static LeagueAIService leagueAIService = new LeagueAIService();

    public static MainApp app;
    private Stage        primaryStage;
    private BorderPane   root;
    private Label        budgetLabel, pointsLabel;

    @Override
    public void start(Stage stage) {
        app = this;
        primaryStage = stage;
        DatabaseManager.getInstance().initializeDatabase();
        initGameData();

        root = new BorderPane();
        root.setStyle("-fx-background-color:#06060f;");
        root.setTop(buildHeader());
        root.setLeft(buildSideMenu());
        showWeeklyView();

        Scene scene = new Scene(root, 1280, 820);
        stage.setTitle("⚽ クラブ・ぶん助 SOCCER MANAGER 🐾");
        stage.setScene(scene);
        stage.show();
    }

    @Override public void stop() { DatabaseManager.getInstance().close(); }

    // ─────────────────────────────────────────────────────────
    // 初期化
    // ─────────────────────────────────────────────────────────
    private void initGameData() {
        allClubs.clear();

        // ── TIER1: 犬リーグ 1部（強豪 8チーム / index 0-7）──
        allClubs.add(makeClub("ボーダー・ストーム",       "#00c8ff","ボーダーコリー",       "4-3-3",   1_000_000_000L, 5_500_000L));
        allClubs.add(makeClub("ジャーマン・エリート",     "#2d5016","ジャーマンシェパード", "4-2-3-1",   950_000_000L, 5_000_000L));
        allClubs.add(makeClub("アルプス・ジャイアンツ",   "#CC0000","セントバーナード",     "5-3-2",     900_000_000L, 5_200_000L));
        allClubs.add(makeClub("グレイ・フラッシュ",       "#888888","グレーハウンド",       "4-5-1",     880_000_000L, 4_800_000L));
        allClubs.add(makeClub("秋田武士団",               "#8B0000","秋田犬",               "5-4-1",     860_000_000L, 4_600_000L));
        allClubs.add(makeClub("パリ・エレガント",         "#ff69b4","プードル",             "4-2-3-1",   850_000_000L, 4_500_000L));
        allClubs.add(makeClub("ロイヤル・マスティフ",     "#4B0082","マスティフ",           "5-3-2",     840_000_000L, 4_400_000L));
        allClubs.add(makeClub("ノルディック・ウルフ",     "#005577","シベリアンハスキー",   "4-3-3",     820_000_000L, 4_300_000L));

        // ── TIER2: 犬リーグ 2部（中堅 8チーム / index 8-15）── playerClubはここ ──
        Club bunsuke = new Club("クラブ・ぶん助", 800_000_000L, 5_000_000L);
        bunsuke.setColor("#4a7a35"); bunsuke.setBreed("フレンチブルドック"); bunsuke.setFormation("4-3-3");
        allClubs.add(bunsuke);
        allClubs.add(makeClub("ゴールデン・サンシャイン", "#DAA520","ゴールデンレトリバー","4-4-2",     780_000_000L, 4_200_000L));
        allClubs.add(makeClub("侍ドッグスFC",             "#cc4400","柴犬",                 "4-3-3",     760_000_000L, 4_100_000L));
        allClubs.add(makeClub("ロンドン・ウォール",       "#8B7355","ブルマスティフ",       "5-3-2",     740_000_000L, 3_900_000L));
        allClubs.add(makeClub("アイリッシュ・ローバー",   "#009A44","アイリッシュセッター", "4-4-2",     720_000_000L, 3_800_000L));
        allClubs.add(makeClub("ダックスフント・ユナイテッド","#8B4513","ダックスフント",    "4-3-3",     700_000_000L, 3_700_000L));
        allClubs.add(makeClub("スパニエル・シティ",       "#1E90FF","スパニエル",           "4-2-3-1",   690_000_000L, 3_600_000L));
        allClubs.add(makeClub("山陰ヤマイヌFC",           "#556B2F","山陰犬",               "3-5-2",     680_000_000L, 3_500_000L));

        // ── TIER3: 犬リーグ 3部（育成 8チーム / index 16-23）──
        allClubs.add(makeClub("チワワ・ファイターズ",     "#FF6347","チワワ",               "4-4-2",     500_000_000L, 2_500_000L));
        allClubs.add(makeClub("ポメラニアン・スパークス", "#FFA500","ポメラニアン",         "4-3-3",     490_000_000L, 2_400_000L));
        allClubs.add(makeClub("シーズー・ドリームズ",     "#DDA0DD","シーズー",             "4-5-1",     480_000_000L, 2_300_000L));
        allClubs.add(makeClub("マルチーズ・エンジェルス", "#F0F0F0","マルチーズ",           "4-4-2",     470_000_000L, 2_200_000L));
        allClubs.add(makeClub("トイプー・アカデミー",     "#D2691E","トイプードル",         "3-4-3",     460_000_000L, 2_100_000L));
        allClubs.add(makeClub("四国犬ブレイブス",         "#8B6914","四国犬",               "5-4-1",     450_000_000L, 2_000_000L));
        allClubs.add(makeClub("琉球バウワウFC",           "#20B2AA","琉球犬",               "4-3-3",     440_000_000L, 1_900_000L));
        allClubs.add(makeClub("北海道スノーフォックス",   "#87CEEB","北海道犬",             "5-3-2",     430_000_000L, 1_800_000L));

        seedRivalPlayers();
        excelLoader.loadFromCSV(allClubs);
        gameDataService.initIfEmpty(allClubs);

        playerClub = allClubs.stream()
            .filter(c -> c.getName().equals("クラブ・ぶん助"))
            .findFirst().orElse(allClubs.get(8));
        leagueAIService.setPlayerClubName(playerClub.getName());

        // 必殺技をDBからロードしてPlayerにセット
        try {
            specialMoveDao.seedDefaultMoves(playerClub.getSquad());
            specialMoveDao.loadSpecialMovesForSquad(playerClub.getSquad());
        } catch (Exception ex) {
            System.err.println("[SpecialMove] ロードエラー: " + ex.getMessage());
        }

        // リーグ初期化（24チームを3部に振り分け）
        leagueManager.initialize(allClubs);

        // スケジュール生成
        season.initSchedule(allClubs, playerClub.getName());
        leagueAIService.processSeasonStart(allClubs);
        // 仲良し度をDBからロード
        friendshipManager.loadFromDB();
        // 下部組織選手をシード（初回のみ）
        academyService.seedAcademyPlayers(playerClub);
        System.out.println("[Main] 初期化完了: " + playerClub.getName()
            + " (TIER2 / " + playerClub.getSquad().size() + "名)");
    }

    private Club makeClub(String name, String color, String breed,
                          String formation, long budget, long salaryBudget) {
        Club c = new Club(name, budget, salaryBudget);
        c.setColor(color); c.setBreed(breed); c.setFormation(formation);
        return c;
    }

    private void seedRivalPlayers() {
        List<Club> rivals = allClubs.subList(1, allClubs.size());
        String[][] seeds = {
            {"ソニック","ボーダーコリー","FW","92"}, {"キング","ジャーマンシェパード","MF","90"},
            {"タンク","セントバーナード","DF","89"},  {"フラッシュ","グレーハウンド","FW","91"},
            {"サムライ","秋田犬","DF","88"},          {"ゴールド","ゴールデンレトリバー","MF","85"},
            {"スモウ","ブルマスティフ","DF","83"},    {"トリクシー","プードル","MF","85"},
            {"ロケット","柴犬","FW","87"},
        };
        for (int i = 0; i < rivals.size() && i < seeds.length; i++) {
            Club club = rivals.get(i);
            String[] s = seeds[i];
            int ovr = Integer.parseInt(s[3]);
            Player.Position pos = PlayerDao.mapPosition(s[2]);
            for (int j = 0; j < 11; j++) {
                String nm = j == 0 ? s[0] : s[0] + (j+1);
                int o2 = Math.max(60, ovr - j*2);
                Player p = new Player(nm, 4+j%5, "犬", s[1], pos,
                    o2, Math.min(99,o2+5), (long)(o2*8000), (long)(o2*o2*10000L), 3);
                p.setShirtNumber(j+1); p.setUniformName(nm.toUpperCase());
                club.getSquad().add(p);
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // ヘッダー
    // ─────────────────────────────────────────────────────────
    private HBox buildHeader() {
        HBox bar = new HBox(14);
        bar.setStyle("-fx-background-color:#0a0a1a;-fx-padding:10 22;"
            + "-fx-border-color:rgba(255,200,0,0.12);-fx-border-width:0 0 1 0;");
        bar.setAlignment(Pos.CENTER_LEFT);

        String uUrl = ExcelLoaderService.toImageURL(playerClub.getUniformImageFile());
        if (uUrl != null) {
            try {
                ImageView iv = new ImageView(new Image(uUrl));
                iv.setFitHeight(36); iv.setPreserveRatio(true);
                bar.getChildren().add(iv);
            } catch (Exception ignored) {}
        }

        Label title = new Label("🐾 " + playerClub.getName());
        title.setStyle("-fx-font-size:17px;-fx-font-weight:bold;-fx-text-fill:" + playerClub.getColor() + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        pointsLabel = new Label();
        pointsLabel.setStyle("-fx-font-size:12px;-fx-text-fill:#ffd700;");
        budgetLabel = new Label();
        budgetLabel.setStyle("-fx-font-size:12px;-fx-text-fill:#a8dadc;");

        Label dbBtn = new Label("💾");
        dbBtn.setStyle("-fx-font-size:11px;-fx-text-fill:rgba(255,255,255,0.2);-fx-cursor:hand;");
        dbBtn.setOnMouseClicked(e -> showDBInfo());

        updateHeaderLabels();
        bar.getChildren().addAll(title, spacer, pointsLabel, budgetLabel, dbBtn);
        return bar;
    }

    public void updateHeaderLabels() {
        if (budgetLabel == null || playerClub == null) return;
        budgetLabel.setText("💰 ¥" + String.format("%,d", playerClub.getBudget()));
        // リーグ順位を取得してポイントラベルに表示
        model.League league = leagueManager.getLeagueOf(playerClub);
        String leagueInfo = league != null
            ? "  [" + league.getTier().getDisplayName() + "  " + leagueManager.getRankText(playerClub) + "]"
            : "";
        pointsLabel.setText(String.format("🏆 %dpt  %dW-%dD-%dL%s",
            playerClub.getPoints(), playerClub.getWins(),
            playerClub.getDraws(), playerClub.getLosses(), leagueInfo));
    }

    private void showDBInfo() {
        List<String> m = gameDataService.getRecentMatches(8);
        StringBuilder sb = new StringBuilder(gameDataService.getDBStats()).append("\n\n── 直近試合 ──\n");
        if (m.isEmpty()) sb.append("（なし）\n");
        else m.forEach(r -> sb.append(r).append("\n"));
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("DB情報"); a.setHeaderText("soccer_manager.db");
        a.setContentText(sb.toString());
        a.getDialogPane().setPrefWidth(580);
        a.showAndWait();
    }

    // ─────────────────────────────────────────────────────────
    // サイドメニュー
    // ─────────────────────────────────────────────────────────
    private VBox buildSideMenu() {
        VBox menu = new VBox(3);
        menu.setStyle("-fx-background-color:#0a0a1a;-fx-padding:18 6;"
            + "-fx-border-color:rgba(255,255,255,0.06);-fx-border-width:0 1 0 0;");
        menu.setPrefWidth(150);
        Label t = new Label("MENU");
        t.setStyle("-fx-font-size:9px;-fx-text-fill:rgba(255,255,255,0.18);"
            + "-fx-padding:0 12 10 12;");
        menu.getChildren().add(t);

        for (String[] item : new String[][]{
            {"📅","シーズン"},{"👥","スカッド"},{"⚽","試合"},{"🔍","スカウト"},{"💰","財務"},{"🤝","仲良し度"}
        }) {
            Button btn = menuBtn(item[0] + "  " + item[1]);
            btn.setOnAction(e -> {
                switch (item[1]) {
                    case "シーズン" -> showWeeklyView();
                    case "スカッド" -> setCenterView(new SquadView(this));
                    case "試合"    -> setCenterView(new MatchView(this));
                    case "スカウト"-> setCenterView(new ScoutView(this));
                    case "財務"    -> setCenterView(new FinanceView(this));
                    case "仲良し度" -> setCenterView(
                        new FriendshipView(friendshipManager, playerClub.getSquad()));
                }
            });
            menu.getChildren().add(btn);
        }
        return menu;
    }

    private Button menuBtn(String text) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        String b = "-fx-background-color:transparent;-fx-text-fill:rgba(255,255,255,0.45);"
            + "-fx-font-size:12px;-fx-padding:9 14;-fx-alignment:CENTER-LEFT;"
            + "-fx-cursor:hand;-fx-background-radius:7;";
        String h = "-fx-background-color:rgba(255,255,255,0.06);-fx-text-fill:#fff;"
            + "-fx-font-size:12px;-fx-padding:9 14;-fx-alignment:CENTER-LEFT;"
            + "-fx-cursor:hand;-fx-background-radius:7;";
        btn.setStyle(b);
        btn.setOnMouseEntered(ev -> btn.setStyle(h));
        btn.setOnMouseExited(ev  -> btn.setStyle(b));
        return btn;
    }

    // ─────────────────────────────────────────────────────────
    // 画面遷移
    // ─────────────────────────────────────────────────────────
    public void showWeeklyView() {
        WeeklyPlanView wv = new WeeklyPlanView(this, season);
        root.setCenter(wv);
    }

    public void setCenterView(javafx.scene.Node view) {
        root.setCenter(view);
    }

    public void showScoutViewForWeek(SeasonManager season, SeasonManager.Action action) {
        ScoutView sv = new ScoutView(this);
        sv.setWeeklyCallback(() -> {
            financeService.processWeeklySalaries(playerClub);
            gameDataService.saveClub(playerClub);
            season.advanceWeek(action, "移籍ウィンドウ完了", playerClub.getBudget());
            showWeeklyView();
        }, true);
        root.setCenter(sv);
    }

    public static void main(String[] args) { launch(args); }
}

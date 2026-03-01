package ui;

import javafx.animation.*;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import model.*;
import model.League.*;

import java.util.List;

/**
 * シーズン終了画面
 *
 * 【表示内容】
 *   1. シーズン成績サマリ（自チーム）
 *   2. 所属リーグの最終順位表
 *   3. 昇格・降格の結果アナウンス
 *   4. 次シーズンの選択肢
 *      ① 同じクラブで続ける
 *      ② 別クラブへ移籍（監督として就任）
 */
public class SeasonEndView extends StackPane {

    private final MainApp      app;
    private final SeasonManager season;
    private final LeagueManager leagueManager;
    private final Club          playerClub;

    // 昇降格結果（processPromotionRelegation後に格納）
    private List<String> promotionMessages;

    public SeasonEndView(MainApp app, SeasonManager season, LeagueManager leagueManager) {
        this.app           = app;
        this.season        = season;
        this.leagueManager = leagueManager;
        this.playerClub    = MainApp.playerClub;

        // 昇降格処理を実行
        promotionMessages = leagueManager.processPromotionRelegation();

        setStyle("-fx-background-color:#06060f;");
        getChildren().add(buildContent());

        // フェードイン
        setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(400), this);
        ft.setFromValue(0); ft.setToValue(1); ft.play();
    }

    // ── メインコンテンツ ────────────────────────────────────
    private ScrollPane buildContent() {
        VBox root = new VBox(20);
        root.setPadding(new Insets(30, 40, 40, 40));
        root.setStyle("-fx-background-color:#06060f;");

        root.getChildren().addAll(
            buildSeasonBanner(),
            buildMyResultCard(),
            buildLeagueStandingsCard(),
            buildPromotionCard(),
            buildRetirementCard(),
            buildNextSeasonCard()
        );

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color:#06060f;-fx-background:#06060f;");
        return scroll;
    }

    // ── ① シーズン終了バナー ────────────────────────────────
    private VBox buildSeasonBanner() {
        VBox box = new VBox(6);
        box.setAlignment(Pos.CENTER);

        League myLeague = leagueManager.getLeagueOf(playerClub);
        int rank = myLeague != null ? myLeague.getRankOf(playerClub) : -1;

        Label title = new Label("🏆  第 " + season.getCurrentSeason() + " シーズン  終了！");
        title.setStyle("-fx-font-size:28px;-fx-font-weight:bold;-fx-text-fill:#FFD700;");

        String rankEmoji = rank == 1 ? "🥇" : rank == 2 ? "🥈" : rank == 3 ? "🥉" : "📊";
        Label subtitle = new Label(rankEmoji + "  最終順位: "
            + (rank > 0 ? rank + "位" : "—")
            + "  /  " + (myLeague != null ? myLeague.getTier().getDisplayName() : ""));
        subtitle.setStyle("-fx-font-size:16px;-fx-text-fill:#AAAACC;");

        box.getChildren().addAll(title, subtitle);
        return box;
    }

    // ── ② 自チーム成績カード ────────────────────────────────
    private VBox buildMyResultCard() {
        VBox card = card("📋  " + playerClub.getName() + "  ―  シーズン成績");

        HBox stats = new HBox(20);
        stats.setAlignment(Pos.CENTER_LEFT);
        stats.setPadding(new Insets(10, 0, 4, 0));

        stats.getChildren().addAll(
            statBox("勝利", String.valueOf(playerClub.getWins()),    "#44FF88"),
            statBox("引分", String.valueOf(playerClub.getDraws()),   "#FFDD44"),
            statBox("敗戦", String.valueOf(playerClub.getLosses()),  "#FF6666"),
            statBox("勝点", String.valueOf(playerClub.getPoints()),  "#FFD700"),
            statBox("予算", "¥" + String.format("%,d", playerClub.getBudget()), "#44AAFF")
        );

        // 順位コメント
        League myLeague = leagueManager.getLeagueOf(playerClub);
        int rank = myLeague != null ? myLeague.getRankOf(playerClub) : -1;
        String comment = buildRankComment(rank, myLeague);
        Label commentLbl = new Label(comment);
        commentLbl.setStyle("-fx-font-size:13px;-fx-text-fill:#AAAACC;-fx-padding:8 0 0 0;");
        commentLbl.setWrapText(true);

        card.getChildren().addAll(stats, commentLbl);
        return card;
    }

    private String buildRankComment(int rank, League league) {
        if (league == null) return "";
        boolean promoted  = leagueManager.getLeague(league.getTier()) != null
            && league.getPromotionCandidates().stream()
               .anyMatch(c -> c.getName().equals(playerClub.getName()));
        boolean relegated = league.getRelegationCandidates().stream()
               .anyMatch(c -> c.getName().equals(playerClub.getName()));

        if (promoted)  return "🎉 おめでとうございます！来季は上のリーグで戦います！";
        if (relegated) return "😢 惜しくも降格…。来季は下のリーグから再出発です。";
        if (rank <= 3) return "👏 上位フィニッシュ！素晴らしいシーズンでした！";
        return "来季こそ、さらなる高みを目指しましょう！";
    }

    // ── ③ リーグ順位表カード ────────────────────────────────
    private VBox buildLeagueStandingsCard() {
        League myLeague = leagueManager.getLeagueOf(playerClub);
        String title = myLeague != null
            ? "📊  " + myLeague.getTier().getDisplayName() + "  最終順位表"
            : "📊  最終順位表";
        VBox card = card(title);

        if (myLeague == null) {
            card.getChildren().add(label("データなし", "#AAAACC", 12));
            return card;
        }

        // ヘッダー行
        card.getChildren().add(standingHeaderRow());

        List<Standing> standings = myLeague.getSortedStandings();
        for (int i = 0; i < standings.size(); i++) {
            Standing s   = standings.get(i);
            boolean isMe = s.club.getName().equals(playerClub.getName());
            boolean up   = i < League.PROMOTION_SLOTS && myLeague.getTier() != Tier.TIER1;
            boolean down = i >= standings.size() - League.RELEGATION_SLOTS
                        && myLeague.getTier() != Tier.TIER3;
            card.getChildren().add(standingRow(i + 1, s, isMe, up, down));
        }

        // 凡例
        HBox legend = new HBox(16);
        legend.setPadding(new Insets(8, 0, 0, 0));
        legend.getChildren().addAll(
            legendDot("#4466FF", "昇格圏"),
            legendDot("#FF4444", "降格圏"),
            legendDot("#FFD700", "あなた")
        );
        card.getChildren().add(legend);
        return card;
    }

    private HBox standingHeaderRow() {
        HBox row = new HBox();
        row.setPadding(new Insets(4, 8, 4, 8));
        row.setStyle("-fx-background-color:#0F2540;-fx-background-radius:4;");
        String[] cols = {"順位", "クラブ名", "試", "勝", "分", "負", "得", "失", "差", "勝点"};
        double[] widths = {36, 180, 36, 36, 36, 36, 36, 36, 46, 46};
        for (int i = 0; i < cols.length; i++) {
            Label lbl = new Label(cols[i]);
            lbl.setPrefWidth(widths[i]);
            lbl.setStyle("-fx-font-size:10px;-fx-font-weight:bold;-fx-text-fill:#AAAACC;");
            lbl.setAlignment(i == 1 ? Pos.CENTER_LEFT : Pos.CENTER);
            row.getChildren().add(lbl);
        }
        return row;
    }

    private HBox standingRow(int rank, Standing s, boolean isMe, boolean up, boolean down) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(5, 8, 5, 8));

        String bg = isMe ? "#1A3A00" : (rank % 2 == 0 ? "#111128" : "#0D0D1A");
        row.setStyle("-fx-background-color:" + bg + ";-fx-background-radius:3;");

        String rankColor = up ? "#4466FF" : (down ? "#FF4444" : "#AAAACC");
        String nameColor = isMe ? "#FFD700" : "#FFFFFF";
        String rankStr   = (up ? "⬆" : down ? "⬇" : "") + rank;

        double[] widths = {36, 180, 36, 36, 36, 36, 36, 36, 46, 46};
        Object[] vals   = {rankStr, s.club.getName(),
            s.played, s.wins, s.draws, s.losses,
            s.goalsFor, s.goalsAgainst, (s.getGD() >= 0 ? "+" : "") + s.getGD(),
            s.getPoints()};

        for (int i = 0; i < vals.length; i++) {
            Label lbl = new Label(vals[i].toString());
            lbl.setPrefWidth(widths[i]);
            lbl.setStyle("-fx-font-size:11px;-fx-text-fill:"
                + (i == 0 ? rankColor : (i == 1 ? nameColor : "#DDDDDD"))
                + ";" + (i == 9 ? "-fx-font-weight:bold;" : "")
                + (isMe && i == 1 ? "-fx-font-weight:bold;" : ""));
            lbl.setAlignment(i == 1 ? Pos.CENTER_LEFT : Pos.CENTER);
            row.getChildren().add(lbl);
        }
        return row;
    }

    // ── ④ 昇降格アナウンスカード ───────────────────────────
    private VBox buildPromotionCard() {
        VBox card = card("🔀  昇格 / 降格  ―  全リーグ結果");
        if (promotionMessages.isEmpty()) {
            card.getChildren().add(label("移動なし（順位変動なし）", "#AAAACC", 12));
        } else {
            for (String msg : promotionMessages) {
                Label lbl = new Label(msg);
                boolean up  = msg.startsWith("⬆");
                lbl.setStyle("-fx-font-size:12px;-fx-text-fill:"
                    + (up ? "#44FF88" : "#FF6666") + ";-fx-padding:2 0;");
                card.getChildren().add(lbl);
            }
        }
        return card;
    }

    // ── ⑤ 次シーズン選択カード ─────────────────────────────
    // ── 引退表明カード ──────────────────────────────────────
    private VBox buildRetirementCard() {
        VBox card = card("🐾  今シーズン限りで引退する選手");

        // 全クラブの引退表明選手を収集
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (model.Club c : ui.MainApp.allClubs) {
            for (model.Player p : c.getSquad()) {
                if (p.isRetirementAnnounced()) {
                    String myClub = c.getName().equals(ui.MainApp.playerClub.getName())
                        ? " 🟡（あなたのクラブ）" : "";
                    lines.add(String.format(
                        "🏷️  %s（%d歳 / %s / %s）  [%s%s]  転生予定 第%dシーズン",
                        p.getFullName(), p.getAge(), p.getBreed(),
                        p.getPosition(), c.getName(), myClub,
                        p.getReincarnationSeason()));
                }
            }
        }

        if (lines.isEmpty()) {
            card.getChildren().add(label("今シーズン引退表明した選手はいません", "#AAAACC", 12));
        } else {
            for (String line : lines) {
                Label l = label(line, "#FFD700", 12);
                l.setWrapText(true);
                card.getChildren().add(l);
            }
            Label note = label(
                "* Retired players are removed from squads after season processing."
                + " Reincarnated players join academy at the start of the next season.",
                "#AAAACC", 11);
            note.setWrapText(true);
            card.getChildren().add(note);
        }
        return card;
    }

    private VBox buildNextSeasonCard() {
        VBox card = card("🎯  次シーズン  ―  どうしますか？");

        Label question = new Label("監督として、次はどのクラブで戦いますか？");
        question.setStyle("-fx-font-size:13px;-fx-text-fill:#FFFFFF;-fx-padding:0 0 12 0;");
        card.getChildren().add(question);

        HBox btnRow = new HBox(16);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        // ① 同じクラブで続ける
        Button stayBtn = actionButton(
            "🐾  " + playerClub.getName() + " で続ける",
            "#1A4A2E", "#44FF88");
        stayBtn.setOnAction(e -> handleStay());

        // ② 別クラブへ移籍
        Button moveBtn = actionButton(
            "🚀  別クラブへ移籍する",
            "#1A2A4A", "#44AAFF");
        moveBtn.setOnAction(e -> showClubSelector(card, btnRow));

        btnRow.getChildren().addAll(stayBtn, moveBtn);
        card.getChildren().add(btnRow);
        return card;
    }

    // ── 別クラブ選択UI ──────────────────────────────────────
    private void showClubSelector(VBox card, HBox btnRow) {
        // 既に選択UIがあれば除去
        card.getChildren().removeIf(n -> n.getId() != null && n.getId().equals("clubSelector"));

        VBox selector = new VBox(10);
        selector.setId("clubSelector");
        selector.setStyle("-fx-background-color:#0F1A2A;-fx-background-radius:8;"
            + "-fx-padding:16;-fx-border-color:#2A2A4A;-fx-border-radius:8;");

        Label pickLabel = new Label("就任するクラブを選んでください（全" +
            leagueManager.getAllLeagues().stream().mapToInt(League::size).sum() + "チーム）");
        pickLabel.setStyle("-fx-font-size:12px;-fx-text-fill:#AAAACC;-fx-padding:0 0 8 0;");
        selector.getChildren().add(pickLabel);

        // リーグ別に表示
        for (League league : leagueManager.getAllLeagues()) {
            Label tierLabel = new Label(league.getTier().getDisplayName());
            tierLabel.setStyle("-fx-font-size:11px;-fx-font-weight:bold;-fx-text-fill:#FFD700;"
                + "-fx-padding:6 0 4 0;");
            selector.getChildren().add(tierLabel);

            HBox clubRow = new HBox(8);
            for (Club c : league.getClubs()) {
                boolean isCurrentClub = c.getName().equals(playerClub.getName());
                Button btn = new Button(c.getName());
                btn.setStyle("-fx-background-color:" + (isCurrentClub ? "#2A2A2A" : "#16213E")
                    + ";-fx-text-fill:" + (isCurrentClub ? "#555555" : "#FFFFFF")
                    + ";-fx-font-size:11px;-fx-padding:6 12;"
                    + "-fx-background-radius:6;-fx-cursor:" + (isCurrentClub ? "default" : "hand") + ";");
                if (!isCurrentClub) {
                    btn.setOnMouseEntered(ev -> btn.setStyle(
                        "-fx-background-color:#1A3A5A;-fx-text-fill:#44AAFF;"
                        + "-fx-font-size:11px;-fx-padding:6 12;-fx-background-radius:6;-fx-cursor:hand;"));
                    btn.setOnMouseExited(ev -> btn.setStyle(
                        "-fx-background-color:#16213E;-fx-text-fill:#FFFFFF;"
                        + "-fx-font-size:11px;-fx-padding:6 12;-fx-background-radius:6;-fx-cursor:hand;"));
                    final Club targetClub = c;
                    btn.setOnAction(ev -> confirmTransfer(targetClub, league));
                } else {
                    btn.setDisable(true);
                }
                clubRow.getChildren().add(btn);
            }
            selector.getChildren().add(clubRow);
        }

        card.getChildren().add(selector);
    }

    // ── 移籍確認ダイアログ ──────────────────────────────────
    private void confirmTransfer(Club targetClub, League targetLeague) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("監督移籍の確認");
        confirm.setHeaderText("🚀  " + targetClub.getName() + " に就任しますか？");
        confirm.setContentText(
            "リーグ: " + targetLeague.getTier().getDisplayName() + "\n"
            + "予算:  ¥" + String.format("%,d", targetClub.getBudget()) + "\n"
            + "選手:  " + targetClub.getSquad().size() + "名\n\n"
            + "※ " + playerClub.getName() + " の選手はそのまま残ります。\n"
            + "　 新クラブのスカッドを引き継いで指揮します。"
        );
        confirm.getButtonTypes().setAll(
            new ButtonType("就任する", ButtonBar.ButtonData.OK_DONE),
            new ButtonType("やめる",   ButtonBar.ButtonData.CANCEL_CLOSE)
        );
        confirm.showAndWait().ifPresent(bt -> {
            if (bt.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                handleTransferTo(targetClub);
            }
        });
    }

    // ── アクション ──────────────────────────────────────────

    /** 同じクラブで続ける */
    private void handleStay() {
        // 選手を1歳加齢
        playerClub.getSquad().forEach(Player::ageing);
        MainApp.gameDataService.saveSquad(playerClub);
        // 成績リセット
        playerClub.resetRecord();
        // 新シーズン開始
        season.startNewSeason(MainApp.allClubs, playerClub.getName());
        MainApp.app.showWeeklyView();
    }

    /** 別クラブへ移籍 */
    private void handleTransferTo(Club newClub) {
        // 旧クラブ選手を加齢・保存
        playerClub.getSquad().forEach(Player::ageing);
        MainApp.gameDataService.saveSquad(playerClub);
        playerClub.resetRecord();

        // 新クラブを playerClub に変更
        MainApp.playerClub = newClub;
        newClub.resetRecord();
        MainApp.gameDataService.saveClub(newClub);

        // 必殺技をロード
        try {
            MainApp.specialMoveDao.loadSpecialMovesForSquad(newClub.getSquad());
        } catch (Exception ex) {
            System.err.println("[Transfer] 必殺技ロードエラー: " + ex.getMessage());
        }

        // 新シーズン開始
        season.startNewSeason(MainApp.allClubs, newClub.getName());
        MainApp.app.updateHeaderLabels();

        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.setTitle("就任完了！");
        info.setHeaderText("🎉 " + newClub.getName() + " の監督に就任しました！");
        info.setContentText("選手: " + newClub.getSquad().size() + "名\n"
            + "予算: ¥" + String.format("%,d", newClub.getBudget()) + "\n"
            + "所属: " + leagueManager.getLeague(leagueManager.getLeagueOf(newClub).getTier())
                        .getTier().getDisplayName()
            + "\n\n新しいシーズンを始めましょう！");
        info.showAndWait();
        MainApp.app.showWeeklyView();
    }

    // ── ヘルパー ────────────────────────────────────────────

    private VBox card(String titleText) {
        VBox card = new VBox(8);
        card.setStyle("-fx-background-color:#16213E;-fx-background-radius:10;"
            + "-fx-padding:18 20 18 20;");
        Label title = new Label(titleText);
        title.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#FFD700;"
            + "-fx-padding:0 0 6 0;");

        // 区切り線
        Rectangle sep = new Rectangle();
        sep.setHeight(1);
        sep.setFill(Color.web("#2A2A4A"));
        sep.widthProperty().bind(card.widthProperty().subtract(40));

        card.getChildren().addAll(title, sep);
        return card;
    }

    private VBox statBox(String labelText, String value, String color) {
        VBox box = new VBox(2);
        box.setAlignment(Pos.CENTER);
        box.setStyle("-fx-background-color:#0D0D1A;-fx-background-radius:6;-fx-padding:8 14;");
        Label val = new Label(value);
        val.setStyle("-fx-font-size:20px;-fx-font-weight:bold;-fx-text-fill:" + color + ";");
        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-font-size:10px;-fx-text-fill:#AAAACC;");
        box.getChildren().addAll(val, lbl);
        return box;
    }

    private Label label(String text, String color, int size) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size:" + size + "px;-fx-text-fill:" + color + ";");
        return lbl;
    }

    private Button actionButton(String text, String bg, String fg) {
        Button btn = new Button(text);
        String base = "-fx-background-color:" + bg + ";-fx-text-fill:" + fg
            + ";-fx-font-size:13px;-fx-font-weight:bold;"
            + "-fx-padding:12 24;-fx-background-radius:8;-fx-cursor:hand;";
        String hover = "-fx-background-color:" + bg.replace("1A", "2A") + ";-fx-text-fill:" + fg
            + ";-fx-font-size:13px;-fx-font-weight:bold;"
            + "-fx-padding:12 24;-fx-background-radius:8;-fx-cursor:hand;"
            + "-fx-effect:dropshadow(gaussian," + fg + ",6,0.3,0,0);";
        btn.setStyle(base);
        btn.setOnMouseEntered(e -> btn.setStyle(hover));
        btn.setOnMouseExited(e  -> btn.setStyle(base));
        return btn;
    }

    private HBox legendDot(String color, String text) {
        HBox box = new HBox(5);
        box.setAlignment(Pos.CENTER_LEFT);
        Rectangle dot = new Rectangle(10, 10);
        dot.setFill(Color.web(color));
        dot.setArcWidth(3); dot.setArcHeight(3);
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size:10px;-fx-text-fill:#AAAACC;");
        box.getChildren().addAll(dot, lbl);
        return box;
    }

    // wrapText対応のHBox（FlowPaneの代替）
    private static class WrapHBox extends HBox {
        public void setWrapText(boolean b) {} // ダミー（FlowPaneに置き換え推奨）
    }
}

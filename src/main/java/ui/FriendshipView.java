package ui;

import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import model.*;

import java.util.*;

/**
 * 仲良し度ビュー
 *
 * SquadView の下部タブ / サイドパネルとして組み込む。
 * 選択した選手の仲良し度ランキングと、
 * チーム全体の相性マトリクスを表示する。
 */
public class FriendshipView extends VBox {

    private final FriendshipManager fm;
    private final List<Player>      squad;

    public FriendshipView(FriendshipManager fm, List<Player> squad) {
        this.fm    = fm;
        this.squad = squad;

        setSpacing(14);
        setPadding(new Insets(0, 0, 20, 0));
        setStyle("-fx-background-color:#06060f;");

        getChildren().addAll(
            buildChemistryBar(),
            buildTopPairsPanel(),
            buildPlayerSelector()
        );
    }

    // ── チーム全体相性バー ──────────────────────────────────
    private VBox buildChemistryBar() {
        VBox box = new VBox(6);
        box.setPadding(new Insets(10, 0, 0, 0));

        double chemistry = fm.getTeamChemistry(squad);   // 0.0 〜 3.0
        double pct       = chemistry / 3.0;
        String label     = chemistry >= 2.5 ? "❤️ 絆MAX"
                         : chemistry >= 1.5 ? "💛 良好"
                         : chemistry >= 0.5 ? "🐾 発展中"
                         : "🔲 まだこれから";

        Label title = new Label("🤝  チーム相性  " + label
            + String.format("  (%.1f / 3.0)", chemistry));
        title.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#FFD700;");

        ProgressBar bar = new ProgressBar(pct);
        bar.setPrefWidth(Double.MAX_VALUE);
        bar.setPrefHeight(12);
        String barColor = chemistry >= 2.5 ? "#FF6699"
                        : chemistry >= 1.5 ? "#FFD700"
                        : "#44AAFF";
        bar.setStyle("-fx-accent:" + barColor + ";");

        box.getChildren().addAll(title, bar);
        return box;
    }

    // ── 仲良しトップペア一覧 ───────────────────────────────
    private VBox buildTopPairsPanel() {
        VBox card = card("🏆  仲良しトップペア");

        List<Friendship> top = fm.getAllSorted();
        if (top.isEmpty()) {
            card.getChildren().add(
                smallLabel("まだ仲良しペアはいません。試合や練習を重ねましょう！", "#AAAACC"));
            return card;
        }

        // 最大10ペア表示
        int limit = Math.min(10, top.size());
        for (int i = 0; i < limit; i++) {
            Friendship f = top.get(i);
            // IDから選手を引く
            Player a = findById(f.getPlayerIdA());
            Player b = findById(f.getPlayerIdB());
            if (a == null || b == null) continue;

            card.getChildren().add(buildPairRow(i + 1, a, b, f));
        }
        return card;
    }

    // ── 選手別仲良し度セレクター ───────────────────────────
    private VBox buildPlayerSelector() {
        VBox card = card("👤  選手別 仲良し度");

        ComboBox<String> combo = new ComboBox<>();
        combo.setPromptText("選手を選んでください");
        combo.setPrefWidth(260);
        combo.setStyle("-fx-background-color:#16213E;-fx-text-fill:#FFFFFF;"
            + "-fx-font-size:12px;");

        squad.stream()
             .sorted(Comparator.comparingInt(Player::getShirtNumber))
             .forEach(p -> combo.getItems().add(
                 p.getShirtNumber() + "  " + p.getFullName()
                 + "  [" + p.getPosition() + "]"));

        VBox resultBox = new VBox(6);
        combo.setOnAction(e -> {
            String selected = combo.getValue();
            if (selected == null) return;
            int shirtNo = Integer.parseInt(selected.split("  ")[0].trim());
            Player target = squad.stream()
                .filter(p -> p.getShirtNumber() == shirtNo)
                .findFirst().orElse(null);
            resultBox.getChildren().clear();
            if (target != null) buildPlayerFriendships(target, resultBox);
        });

        card.getChildren().addAll(combo, resultBox);
        return card;
    }

    private void buildPlayerFriendships(Player target, VBox resultBox) {
        List<Friendship> list = fm.getFriendshipsOf(target);

        if (list.isEmpty()) {
            resultBox.getChildren().add(
                smallLabel(target.getFullName() + " にはまだ仲良しの仲間がいません", "#AAAACC"));
            return;
        }

        // ヘッダー
        Label header = new Label(target.getFullName() + " の仲良しランキング");
        header.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#FFD700;"
            + "-fx-padding:4 0 2 0;");
        resultBox.getChildren().add(header);

        for (int i = 0; i < list.size(); i++) {
            Friendship f   = list.get(i);
            int        pid = f.getPartnerId(target.getId());
            Player     partner = findById(pid);
            if (partner == null) continue;
            resultBox.getChildren().add(buildPairRow(i + 1, target, partner, f));
        }

        // ボーナス説明
        resultBox.getChildren().add(buildBonusLegend());
    }

    // ── ペア行 ──────────────────────────────────────────────
    private HBox buildPairRow(int rank, Player a, Player b, Friendship f) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 10, 6, 10));

        boolean alt = rank % 2 == 0;
        row.setStyle("-fx-background-color:" + (alt ? "#111128" : "#0D0D1A")
            + ";-fx-background-radius:5;");

        // 順位
        Label rankLbl = new Label(rank + ".");
        rankLbl.setMinWidth(24);
        rankLbl.setStyle("-fx-font-size:10px;-fx-text-fill:#AAAACC;");

        // レベルバッジ
        Label levelBadge = buildLevelBadge(f.getLevel());

        // 選手名ペア
        Label names = new Label(a.getFullName() + "  ↔  " + b.getFullName());
        names.setStyle("-fx-font-size:12px;-fx-text-fill:#FFFFFF;-fx-font-weight:bold;");

        // ポイント
        Label pts = new Label("(" + f.getPoints() + "pt)");
        pts.setStyle("-fx-font-size:10px;-fx-text-fill:#AAAACC;");

        // 犬種・ポジション同一バッジ
        HBox tags = new HBox(4);
        if (a.getBreed() != null && a.getBreed().equals(b.getBreed())) {
            tags.getChildren().add(tag("🐕 同犬種", "#1A3A4A", "#44AAFF"));
        }
        if (a.getPosition() == b.getPosition()) {
            tags.getChildren().add(tag("同ポジ", "#1A3A1A", "#44FF88"));
        }

        // ボーナス表示
        Label bonus = buildBonusLabel(f);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        row.getChildren().addAll(rankLbl, levelBadge, names, pts, tags, spacer, bonus);
        return row;
    }

    // ── レベルバッジ ─────────────────────────────────────────
    private Label buildLevelBadge(Friendship.Level level) {
        String bg = switch (level) {
            case LARGE  -> "#4A0020";
            case MEDIUM -> "#3A3A00";
            case SMALL  -> "#0A2A0A";
            default     -> "#1A1A1A";
        };
        String fg = switch (level) {
            case LARGE  -> "#FF6699";
            case MEDIUM -> "#FFD700";
            case SMALL  -> "#44FF88";
            default     -> "#AAAACC";
        };
        Label lbl = new Label(level.label);
        lbl.setStyle("-fx-font-size:11px;-fx-font-weight:bold;-fx-text-fill:" + fg
            + ";-fx-background-color:" + bg + ";-fx-background-radius:4;"
            + "-fx-padding:2 6;");
        lbl.setMinWidth(54);
        return lbl;
    }

    // ── ボーナスラベル ─────────────────────────────────────
    private Label buildBonusLabel(Friendship f) {
        Map<String, Integer> bonus = f.getMatchBonus();
        if (bonus.isEmpty()) return new Label("");
        StringBuilder sb = new StringBuilder("試合ボーナス: ");
        bonus.forEach((k, v) -> sb.append(k).append("+").append(v).append(" "));
        Label lbl = new Label(sb.toString().trim());
        lbl.setStyle("-fx-font-size:10px;-fx-text-fill:#44FF88;");
        return lbl;
    }

    // ── 凡例 ──────────────────────────────────────────────
    private VBox buildBonusLegend() {
        VBox box = new VBox(3);
        box.setStyle("-fx-background-color:#0F1A2A;-fx-background-radius:6;-fx-padding:8 12;");
        box.getChildren().add(smallLabel("── 仲良し度ボーナス（試合中・同フォーメーション時）──", "#FFD700"));
        box.getChildren().add(smallLabel("🐾 小  →  speed +2 / stamina +2", "#44FF88"));
        box.getChildren().add(smallLabel("💛 中  →  speed +4 / stamina +4 / shooting +2 / passing +2", "#FFD700"));
        box.getChildren().add(smallLabel("❤️ 大  →  全能力値 +5", "#FF6699"));
        box.getChildren().add(smallLabel("🐕 同じ犬種どうしはポイントが貯まりやすい (+2/回)", "#44AAFF"));
        box.getChildren().add(smallLabel("同ポジどうしも若干貯まりやすい (+1/回)", "#AAAACC"));
        return box;
    }

    // ── ユーティリティ ──────────────────────────────────────
    private VBox card(String titleText) {
        VBox card = new VBox(8);
        card.setStyle("-fx-background-color:#16213E;-fx-background-radius:10;"
            + "-fx-padding:14 16 14 16;");
        Label title = new Label(titleText);
        title.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#FFD700;"
            + "-fx-padding:0 0 4 0;");
        Rectangle sep = new Rectangle();
        sep.setHeight(1); sep.setFill(Color.web("#2A2A4A"));
        sep.widthProperty().bind(card.widthProperty().subtract(32));
        card.getChildren().addAll(title, sep);
        return card;
    }

    private Label smallLabel(String text, String color) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size:11px;-fx-text-fill:" + color + ";");
        lbl.setWrapText(true);
        return lbl;
    }

    private Label tag(String text, String bg, String fg) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size:9px;-fx-text-fill:" + fg
            + ";-fx-background-color:" + bg + ";-fx-background-radius:3;-fx-padding:1 4;");
        return lbl;
    }

    private Player findById(int id) {
        return squad.stream()
            .filter(p -> p.getId() == id)
            .findFirst().orElse(null);
    }
}

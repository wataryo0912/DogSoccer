package ui.component;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import model.EmotionState;
import model.EmotionState.Mood;
import model.Player;

/**
 * 選手の感情パラメータを表示するUIパネル
 *
 * 選手詳細タブ・チームメンバ一覧のサイドパネルで使用する。
 *
 * 使い方:
 *   EmotionPanel panel = new EmotionPanel(player);
 *   detailPane.getChildren().add(panel);
 *
 *   // 週次更新後に再描画
 *   panel.refresh(player);
 */
public class EmotionPanel extends VBox {

    private static final int BAR_WIDTH = 180;

    public EmotionPanel(Player player) {
        setSpacing(8);
        setStyle("-fx-background-color:#0d0d22;-fx-padding:14;"
            + "-fx-background-radius:8;"
            + "-fx-border-color:rgba(255,255,255,0.08);-fx-border-radius:8;-fx-border-width:1;");
        refresh(player);
    }

    /** 選手の感情状態を再描画する */
    public void refresh(Player player) {
        getChildren().clear();
        EmotionState e = player.getEmotion();

        // ── セクションタイトル ────────────────────────────────
        Label title = new Label("💬  感情ステータス");
        title.setStyle("-fx-font-size:12px;-fx-font-weight:bold;"
            + "-fx-text-fill:rgba(255,255,255,0.6);-fx-padding:0 0 6 0;");
        getChildren().add(title);

        // ── Mood バッジ ───────────────────────────────────────
        getChildren().add(buildMoodBadge(e.getMood(), e.isInZone()));

        // ── パラメータバー群 ──────────────────────────────────
        getChildren().add(buildBar("自信",    e.getConfidence(),
            "#4a7aff", "ゴール/勝利で上昇。≥90でゾーン状態（全能力+15%）"));
        getChildren().add(buildBar("不満",    e.getFrustration(),
            "#ff6b6b", "出場機会不足で蓄積。≥70で練習態度悪化イベント"));
        getChildren().add(buildBar("忠誠心",  e.getLoyalty(),
            "#ffd700", "在籍年数・勝利で上昇。移籍オファーへの抵抗感"));
        getChildren().add(buildBar("ライバル心",e.getRivalry(),
            "#ff9a44", "接触プレーで蓄積。≥80でライバルイベント発生"));

        // ── 連続ベンチ警告 ────────────────────────────────────
        if (e.getBenchedWeeks() >= 2) {
            Label bench = new Label("⚠️  " + e.getBenchedWeeks() + "週連続不出場");
            bench.setStyle("-fx-font-size:10px;-fx-text-fill:#ff9a44;"
                + "-fx-padding:4 8;-fx-background-color:rgba(255,154,68,0.12);"
                + "-fx-background-radius:4;");
            getChildren().add(bench);
        }

        // ── 犬種特性バッジ ────────────────────────────────────
        getChildren().add(buildBreedBadge(player));
    }

    // ─────────────────────────────────────────────────────────
    // パーツ生成
    // ─────────────────────────────────────────────────────────

    /** Mood バッジ（色付き） */
    private HBox buildMoodBadge(Mood mood, boolean inZone) {
        HBox box = new HBox(6);
        box.setAlignment(Pos.CENTER_LEFT);

        String label, bg, fg;
        if (inZone) {
            label = "⚡  ZONE状態";
            bg = "rgba(255,220,0,0.18)"; fg = "#ffe566";
        } else {
            label = switch (mood) {
                case HAPPY  -> "😊  好調";
                case NORMAL -> "😐  普通";
                case DOWN   -> "😞  不調";
                case SLUMP  -> "😰  スランプ";
            };
            bg = switch (mood) {
                case HAPPY  -> "rgba(74,255,136,0.15)";
                case NORMAL -> "rgba(255,255,255,0.08)";
                case DOWN   -> "rgba(255,154,68,0.15)";
                case SLUMP  -> "rgba(255,107,107,0.20)";
            };
            fg = switch (mood) {
                case HAPPY  -> "#44ff88";
                case NORMAL -> "#aaaaaa";
                case DOWN   -> "#ff9a44";
                case SLUMP  -> "#ff6b6b";
            };
        }

        Label moodLabel = new Label(label);
        moodLabel.setStyle("-fx-font-size:11px;-fx-font-weight:bold;"
            + "-fx-text-fill:" + fg + ";"
            + "-fx-padding:4 10;"
            + "-fx-background-color:" + bg + ";"
            + "-fx-background-radius:12;");

        // mood の能力補正を表示
        String mult = inZone ? "+15%" : switch (mood) {
            case HAPPY  -> "+10%";
            case NORMAL -> "±0%";
            case DOWN   -> "−10%";
            case SLUMP  -> "−20%";
        };
        Label multLabel = new Label("能力 " + mult);
        multLabel.setStyle("-fx-font-size:10px;-fx-text-fill:rgba(255,255,255,0.4);");

        box.getChildren().addAll(moodLabel, multLabel);
        return box;
    }

    /** パラメータバー（ラベル + プログレスバー + 数値） */
    private HBox buildBar(String name, int value, String color, String tooltip) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        // ラベル
        Label lbl = new Label(name);
        lbl.setPrefWidth(62);
        lbl.setStyle("-fx-font-size:10px;-fx-text-fill:rgba(255,255,255,0.55);");

        // バー背景
        StackPane barBg = new StackPane();
        barBg.setPrefSize(BAR_WIDTH, 7);
        barBg.setMaxSize(BAR_WIDTH, 7);

        Rectangle bg = new Rectangle(BAR_WIDTH, 7);
        bg.setArcWidth(4); bg.setArcHeight(4);
        bg.setFill(Color.web("rgba(255,255,255,0.08)"));

        Rectangle fill = new Rectangle((int)(BAR_WIDTH * value / 100.0), 7);
        fill.setArcWidth(4); fill.setArcHeight(4);
        fill.setFill(Color.web(color));
        StackPane.setAlignment(fill, Pos.CENTER_LEFT);

        barBg.getChildren().addAll(bg, fill);

        // 数値
        Label num = new Label(String.valueOf(value));
        num.setPrefWidth(28);
        num.setStyle("-fx-font-size:10px;-fx-text-fill:" + color
            + ";-fx-font-weight:bold;-fx-alignment:center-right;");

        row.getChildren().addAll(lbl, barBg, num);
        Tooltip.install(row, new Tooltip(tooltip));
        return row;
    }

    /** 犬種特性バッジ（アクティブな特性のみ表示） */
    private HBox buildBreedBadge(Player player) {
        HBox box = new HBox(5);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setStyle("-fx-padding:4 0 0 0;");

        var trait = player.getBreedTrait();
        if (trait.perfectionist) box.getChildren().add(badge("完璧主義", "#a78bfa"));
        if (trait.moodSwing)     box.getChildren().add(badge("気分屋",   "#fbbf24"));
        if (trait.loyal)         box.getChildren().add(badge("忠誠",     "#34d399"));
        if (trait.physical)      box.getChildren().add(badge("フィジカル","#f87171"));
        if (trait.intelligent)   box.getChildren().add(badge("知性",     "#60a5fa"));
        if (trait.guardian)      box.getChildren().add(badge("守護",     "#6ee7b7"));

        return box;
    }

    private Label badge(String text, String color) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size:9px;-fx-text-fill:" + color + ";"
            + "-fx-padding:2 6;"
            + "-fx-background-color:" + color + "22;"
            + "-fx-border-color:" + color + "66;"
            + "-fx-border-width:1;-fx-border-radius:8;-fx-background-radius:8;");
        return l;
    }
}

package ui.component;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import model.SpecialMove;
import ui.util.ImageManager;

/**
 * 必殺技カードコンポーネント
 *
 * 必殺技習得イベント画面・選手詳細の必殺技欄で使用。
 * 試合中の演出にも流用可能。
 *
 * 使い方:
 *   SpecialMoveCardView card = new SpecialMoveCardView(move, SpecialMoveCardView.Size.MEDIUM);
 *   someLayout.getChildren().add(card);
 */
public class SpecialMoveCardView extends VBox {

    public enum Size {
        SMALL(160, 90),    // 選手詳細内の小表示
        MEDIUM(320, 180),  // イベント画面・習得画面
        LARGE(480, 270);   // 試合中の演出オーバーレイ

        public final double w, h;
        Size(double w, double h) { this.w = w; this.h = h; }
    }

    public SpecialMoveCardView(SpecialMove move, Size size) {
        setAlignment(Pos.CENTER);
        setSpacing(6);

        // ── 必殺技エフェクト画像ボックス ────────────────────────
        // move.getImageFile() → 例: "move_bunsuke.png"
        StackPane imgBox = ImageManager.moveEffect(
            move.getImageFile(),
            move.getName(),
            size.w, size.h
        );

        // ── 技名ラベル ──────────────────────────────────────────
        Label nameLabel = new Label("⚡ " + move.getName());
        nameLabel.setStyle(
            "-fx-font-size:13px;"
            + "-fx-font-weight:bold;"
            + "-fx-text-fill:#ffe066;"
        );

        // ── タイプ・パワーバッジ ────────────────────────────────
        String typeIcon = switch (move.getMoveType()) {
            case "SHOOT"   -> "🔥 シュート";
            case "DRIBBLE" -> "💨 ドリブル";
            case "TACKLE"  -> "💥 タックル";
            case "SAVE"    -> "🛡 セーブ";
            case "SPIRIT"  -> "✨ 精神";
            default        -> move.getMoveType();
        };

        Label typeLabel = new Label(typeIcon + "  PWR " + move.getPower());
        typeLabel.setStyle(
            "-fx-font-size:11px;"
            + "-fx-text-fill:rgba(255,255,255,0.6);"
        );

        // SMALLはタイプラベルのみ
        if (size == Size.SMALL) {
            getChildren().addAll(imgBox, nameLabel);
        } else {
            getChildren().addAll(imgBox, nameLabel, typeLabel);
        }
    }
}

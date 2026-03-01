package ui.component;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import model.Player;
import ui.util.ImageManager;

/**
 * 選手カードコンポーネント
 *
 * 選手詳細画面・チームメンバ一覧で使用する選手カード。
 * ImageManager経由で画像を表示し、未配置の場合は
 * プレースホルダーを自動表示する。
 *
 * 使い方:
 *   PlayerCardView card = new PlayerCardView(player, PlayerCardView.Size.MEDIUM);
 *   someLayout.getChildren().add(card);
 */
public class PlayerCardView extends VBox {

    public enum Size {
        SMALL(80, 100, 9, 10),      // チームメンバ一覧のアイコン
        MEDIUM(160, 200, 11, 13),   // 選手詳細タブ
        LARGE(220, 275, 13, 15);    // 選手詳細画面メイン

        public final double w, h;
        public final int nameSize, overallSize;

        Size(double w, double h, int nameSize, int overallSize) {
            this.w = w; this.h = h;
            this.nameSize = nameSize; this.overallSize = overallSize;
        }
    }

    private final Player player;
    private final Size   size;

    public PlayerCardView(Player player, Size size) {
        this.player = player;
        this.size   = size;
        setAlignment(Pos.CENTER);
        setSpacing(4);
        build();
    }

    private void build() {
        // ── 画像ボックス ────────────────────────────────────────
        // player.getImageFile() → 例: "player_1.png"
        // 画像がなければ自動でプレースホルダー表示
        StackPane imgBox = ImageManager.playerCard(
            player.getImageFile(),
            player.getFullName(),
            size.w, size.h
        );

        // slump状態なら赤い点を右上に表示
        if (player.getMood() != null &&
                player.getMood().toString().equals("slump")) {
            StackPane.setAlignment(buildSlumpDot(), javafx.geometry.Pos.TOP_RIGHT);
            imgBox.getChildren().add(buildSlumpDot());
        }

        // ── 名前ラベル ──────────────────────────────────────────
        Label nameLabel = new Label(player.getFullName());
        nameLabel.setStyle(
            "-fx-font-size:" + size.nameSize + "px;"
            + "-fx-text-fill:#ffffff;"
            + "-fx-font-weight:bold;"
        );

        // ── Overall ────────────────────────────────────────────
        Label overallLabel = new Label("OVR " + player.getOverall());
        overallLabel.setStyle(
            "-fx-font-size:" + size.overallSize + "px;"
            + "-fx-text-fill:#ffd700;"
            + "-fx-font-weight:bold;"
        );

        // ── ポジション・犬種バッジ ──────────────────────────────
        Label badgeLabel = new Label(player.getPosition() + " · " + breedJp(player.getBreed()));
        badgeLabel.setStyle(
            "-fx-font-size:" + (size.nameSize - 2) + "px;"
            + "-fx-text-fill:rgba(255,255,255,0.5);"
        );

        if (size == Size.SMALL) {
            // SMALLは名前のみ
            getChildren().addAll(imgBox, nameLabel);
        } else {
            getChildren().addAll(imgBox, nameLabel, overallLabel, badgeLabel);
        }
    }

    /** slump状態を示す赤い点 */
    private javafx.scene.shape.Circle buildSlumpDot() {
        javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(5);
        dot.setFill(Color.web("#ff4444"));
        dot.setStroke(Color.web("#0d0d22"));
        dot.setStrokeWidth(1.5);
        return dot;
    }

    /** 犬種の英語名を日本語に変換 */
    private String breedJp(String breed) {
        return switch (breed.toLowerCase()) {
            case "shiba"         -> "柴犬";
            case "bulldog"       -> "ブルドッグ";
            case "saint_bernard" -> "セントバーナード";
            case "akita"         -> "秋田犬";
            case "border_collie" -> "ボーダーコリー";
            case "husky"         -> "ハスキー";
            case "poodle"        -> "プードル";
            case "beagle"        -> "ビーグル";
            case "dachshund"     -> "ダックスフンド";
            case "golden"        -> "ゴールデン";
            default              -> breed;
        };
    }
}

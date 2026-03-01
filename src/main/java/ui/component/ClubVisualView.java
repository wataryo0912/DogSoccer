package ui.component;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import model.Club;
import ui.util.ImageManager;

/**
 * クラブビジュアルコンポーネント
 *
 * エンブレムとユニフォームをまとめて扱うコンポーネント群。
 * 順位表・移籍画面・クラブ詳細画面で使用する。
 */
public class ClubVisualView {

    // ════════════════════════════════════════════════════════════
    // エンブレム
    // ════════════════════════════════════════════════════════════

    /**
     * クラブエンブレム（小: 順位表・リスト用）
     *
     * 使い方:
     *   StackPane emb = ClubVisualView.emblemSmall(club);
     *   hbox.getChildren().add(emb);
     */
    public static StackPane emblemSmall(Club club) {
        return ImageManager.emblem(
            emblemFileName(club.getName()),
            club.getName(),
            48
        );
    }

    /**
     * クラブエンブレム（中: クラブ詳細・移籍画面用）
     */
    public static StackPane emblemMedium(Club club) {
        return ImageManager.emblem(
            emblemFileName(club.getName()),
            club.getName(),
            96
        );
    }

    /**
     * クラブエンブレム（大: クラブ詳細メイン表示）
     */
    public static StackPane emblemLarge(Club club) {
        return ImageManager.emblem(
            emblemFileName(club.getName()),
            club.getName(),
            144
        );
    }

    // ════════════════════════════════════════════════════════════
    // ユニフォーム
    // ════════════════════════════════════════════════════════════

    /**
     * ユニフォーム（選手詳細・移籍画面用）
     *
     * 使い方:
     *   StackPane uni = ClubVisualView.uniform(club);
     *   hbox.getChildren().add(uni);
     */
    public static StackPane uniform(Club club) {
        return ImageManager.uniform(
            uniformFileName(club.getPhilosophy()),
            club.getName()
        );
    }

    // ════════════════════════════════════════════════════════════
    // クラブ名称 + エンブレム の組み合わせパーツ
    // ════════════════════════════════════════════════════════════

    /**
     * エンブレム + クラブ名の横並びHBox（順位表の行など）
     *
     * 使い方:
     *   HBox row = ClubVisualView.emblemWithName(club);
     *   tableRow.getChildren().add(row);
     */
    public static HBox emblemWithName(Club club) {
        HBox hbox = new HBox(8);
        hbox.setAlignment(Pos.CENTER_LEFT);

        StackPane emb = emblemSmall(club);

        Label nameLabel = new Label(club.getName());
        nameLabel.setStyle(
            "-fx-font-size:13px;"
            + "-fx-text-fill:#ffffff;"
            + "-fx-font-weight:bold;"
        );

        hbox.getChildren().addAll(emb, nameLabel);
        return hbox;
    }

    // ════════════════════════════════════════════════════════════
    // ファイル名解決（命名規則）
    // ════════════════════════════════════════════════════════════

    /**
     * クラブ名からエンブレムファイル名を生成
     * 例: "老犬カンフーズ" → "emblem_老犬カンフーズ.png"
     *
     * ※ DALL-Eで生成したファイルをこの命名規則で保存してください
     *   （README.md の「ファイル命名規則」を参照）
     */
    public static String emblemFileName(String clubName) {
        return "emblem_" + clubName + ".png";
    }

    /**
     * クラブ思想からユニフォームファイル名を生成
     * 例: "DEFENSIVE_INTELLECT" → "uniform_defensive.png"
     *
     * ※ 思想カテゴリ別に1ファイルを共用します
     */
    public static String uniformFileName(String philosophy) {
        if (philosophy == null) return "uniform_default.png";
        String p = philosophy.toLowerCase();
        if (p.contains("defensive"))  return "uniform_defensive.png";
        if (p.contains("youth"))      return "uniform_youth.png";
        if (p.contains("galactico"))  return "uniform_galactico.png";
        if (p.contains("physical"))   return "uniform_physical.png";
        if (p.contains("data") || p.contains("moneyball")) return "uniform_data.png";
        return "uniform_default.png";
    }
}

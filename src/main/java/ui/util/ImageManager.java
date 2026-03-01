package ui.util;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.control.Label;
import javafx.geometry.Pos;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 画像管理ユーティリティ
 *
 * 役割:
 *   - resources/images/ 以下の画像ファイルを読み込む
 *   - 画像が未配置（ファイルなし）の場合はプレースホルダーを表示する
 *   - 一度読み込んだ画像はキャッシュして再利用する
 *
 * 画像の配置先:
 *   src/main/resources/images/players/    選手画像
 *   src/main/resources/images/moves/      必殺技エフェクト画像
 *   src/main/resources/images/emblems/    クラブエンブレム
 *   src/main/resources/images/uniforms/   ユニフォーム
 *
 * 使い方:
 *   // 選手画像（160×200）
 *   StackPane box = ImageManager.playerCard("player_1.png", "ぶん助");
 *
 *   // 必殺技画像（320×180）
 *   StackPane box = ImageManager.moveEffect("move_bunsuke.png", "流星ブルアタック");
 *
 *   // エンブレム（80×80）
 *   StackPane box = ImageManager.emblem("emblem_老犬カンフーズ.png", "老犬カンフーズ");
 *
 *   // ユニフォーム（120×160）
 *   StackPane box = ImageManager.uniform("uniform_defensive.png", "守備型");
 */
public class ImageManager {

    // ── キャッシュ ──────────────────────────────────────────────
    private static final Map<String, Image> cache = new HashMap<>();

    // ── リソースのルートパス ────────────────────────────────────
    private static final String BASE_PATH      = "/images/";
    private static final String PLAYER_PATH    = BASE_PATH + "players/";
    private static final String MOVE_PATH      = BASE_PATH + "moves/";
    private static final String EMBLEM_PATH    = BASE_PATH + "emblems/";
    private static final String UNIFORM_PATH   = BASE_PATH + "uniforms/";

    // ── プレースホルダーのカラー定義 ───────────────────────────
    private static final String PH_PLAYER_BG  = "#1a1a2e";
    private static final String PH_MOVE_BG    = "#0d1b2a";
    private static final String PH_EMBLEM_BG  = "#12121f";
    private static final String PH_UNIFORM_BG = "#1e1e30";
    private static final String PH_BORDER     = "rgba(255,255,255,0.15)";
    private static final String PH_TEXT_COLOR = "rgba(255,255,255,0.35)";

    // ════════════════════════════════════════════════════════════
    // 公開API: 各種画像ボックス生成
    // ════════════════════════════════════════════════════════════

    /**
     * 選手カード画像ボックス（160×200）
     *
     * @param fileName  例: "player_1.png"
     * @param playerName プレースホルダーに表示する選手名
     */
    public static StackPane playerCard(String fileName, String playerName) {
        return buildBox(
            PLAYER_PATH + fileName,
            160, 200,
            PH_PLAYER_BG,
            "🐾\n" + playerName,
            8
        );
    }

    /**
     * 選手カード画像ボックス（サイズ指定版）
     */
    public static StackPane playerCard(String fileName, String playerName, double w, double h) {
        return buildBox(PLAYER_PATH + fileName, w, h, PH_PLAYER_BG, "🐾\n" + playerName, 8);
    }

    /**
     * 必殺技エフェクト画像ボックス（320×180）
     *
     * @param fileName  例: "move_bunsuke.png"
     * @param moveName  プレースホルダーに表示する技名
     */
    public static StackPane moveEffect(String fileName, String moveName) {
        return buildBox(
            MOVE_PATH + fileName,
            320, 180,
            PH_MOVE_BG,
            "⚡\n" + moveName,
            8
        );
    }

    /**
     * 必殺技エフェクト画像ボックス（サイズ指定版）
     */
    public static StackPane moveEffect(String fileName, String moveName, double w, double h) {
        return buildBox(MOVE_PATH + fileName, w, h, PH_MOVE_BG, "⚡\n" + moveName, 8);
    }

    /**
     * クラブエンブレム画像ボックス（80×80）
     *
     * @param fileName  例: "emblem_老犬カンフーズ.png"
     * @param clubName  プレースホルダーに表示するクラブ名（先頭2文字）
     */
    public static StackPane emblem(String fileName, String clubName) {
        String shortName = clubName.length() >= 2 ? clubName.substring(0, 2) : clubName;
        return buildBox(
            EMBLEM_PATH + fileName,
            80, 80,
            PH_EMBLEM_BG,
            shortName,
            40
        );
    }

    /**
     * クラブエンブレム画像ボックス（サイズ指定版）
     */
    public static StackPane emblem(String fileName, String clubName, double size) {
        String shortName = clubName.length() >= 2 ? clubName.substring(0, 2) : clubName;
        return buildBox(EMBLEM_PATH + fileName, size, size, PH_EMBLEM_BG, shortName, (int)(size * 0.5));
    }

    /**
     * ユニフォーム画像ボックス（120×160）
     *
     * @param fileName  例: "uniform_defensive.png"
     * @param label     プレースホルダーに表示するラベル
     */
    public static StackPane uniform(String fileName, String label) {
        return buildBox(
            UNIFORM_PATH + fileName,
            120, 160,
            PH_UNIFORM_BG,
            "👕\n" + label,
            8
        );
    }

    // ════════════════════════════════════════════════════════════
    // 内部実装: 共通ボックス生成ロジック
    // ════════════════════════════════════════════════════════════

    /**
     * 画像またはプレースホルダーを返す共通メソッド
     *
     * @param resourcePath  クラスパス上のフルパス
     * @param w             ボックス幅
     * @param h             ボックス高さ
     * @param bgColor       プレースホルダー背景色
     * @param phText        プレースホルダーテキスト
     * @param fontSize      プレースホルダーフォントサイズ
     */
    private static StackPane buildBox(
            String resourcePath, double w, double h,
            String bgColor, String phText, int fontSize) {

        StackPane pane = new StackPane();
        pane.setPrefSize(w, h);
        pane.setMinSize(w, h);
        pane.setMaxSize(w, h);

        Image image = loadImage(resourcePath);

        if (image != null) {
            // ── 画像あり: ImageViewで表示 ──────────────────────
            ImageView iv = new ImageView(image);
            iv.setFitWidth(w);
            iv.setFitHeight(h);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            pane.getChildren().add(iv);

        } else {
            // ── 画像なし: プレースホルダーを表示 ─────────────────
            // 背景
            Rectangle bg = new Rectangle(w, h);
            bg.setArcWidth(8);
            bg.setArcHeight(8);
            bg.setFill(Color.web(bgColor));
            bg.setStroke(Color.web("rgba(255,255,255,0.12)"));
            bg.setStrokeWidth(1);

            // テキスト
            Label ph = new Label(phText);
            ph.setStyle(
                "-fx-font-size:" + fontSize + "px;"
                + "-fx-text-fill:" + PH_TEXT_COLOR + ";"
                + "-fx-text-alignment:center;"
                + "-fx-alignment:center;"
            );
            ph.setWrapText(true);
            ph.setMaxWidth(w - 12);

            pane.getChildren().addAll(bg, ph);
            pane.setAlignment(Pos.CENTER);
        }

        return pane;
    }

    // ════════════════════════════════════════════════════════════
    // 内部実装: 画像読み込み（キャッシュ付き）
    // ════════════════════════════════════════════════════════════

    /**
     * クラスパスから画像を読み込む。
     * ファイルが存在しない場合は null を返す（例外は出さない）。
     */
    private static Image loadImage(String resourcePath) {
        // キャッシュ確認
        if (cache.containsKey(resourcePath)) {
            return cache.get(resourcePath); // null（=未存在）もキャッシュする
        }

        // クラスパスからストリームを取得
        InputStream is = ImageManager.class.getResourceAsStream(resourcePath);
        if (is == null) {
            // ファイルが存在しない → nullをキャッシュして次回の検索を省略
            cache.put(resourcePath, null);
            return null;
        }

        try {
            Image img = new Image(is);
            if (img.isError()) {
                // 画像読み込みエラー
                cache.put(resourcePath, null);
                return null;
            }
            cache.put(resourcePath, img);
            return img;
        } catch (Exception e) {
            cache.put(resourcePath, null);
            return null;
        }
    }

    /**
     * キャッシュをクリア（デバッグ・テスト用）
     */
    public static void clearCache() {
        cache.clear();
    }

    /**
     * 指定ファイルが既に配置されているか確認する
     */
    public static boolean exists(String category, String fileName) {
        String path = switch (category) {
            case "player"  -> PLAYER_PATH  + fileName;
            case "move"    -> MOVE_PATH    + fileName;
            case "emblem"  -> EMBLEM_PATH  + fileName;
            case "uniform" -> UNIFORM_PATH + fileName;
            default        -> BASE_PATH    + fileName;
        };
        return ImageManager.class.getResourceAsStream(path) != null;
    }
}

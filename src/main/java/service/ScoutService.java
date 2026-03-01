package service;

import model.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * スカウトサービス（犬選手版）
 */
public class ScoutService {

    private Random random = new Random();

    private static final String[] DOG_NAMES = {
        "ハチ", "クロ", "シロ", "コタロウ", "マロン", "ポチ", "ゴン",
        "リキ", "タイガ", "ゼウス", "アポロ", "マックス", "ルーク", "レオ",
        "カイ", "リュウ", "ハク", "ショウ", "ケン", "リン",
        "ベア", "ウルフ", "バロン", "デューク", "プリンス",
        "チョコ", "キャラメル", "モカ", "クッキー", "ナッツ"
    };

    private static final String[][] BREEDS_BY_POSITION = {
        // GK: 大型で安定感のある犬種
        {"セントバーナード", "ブルマスティフ", "ニューファンドランド", "グレートデン"},
        // DF: フィジカルの強い犬種
        {"ジャーマンシェパード", "ロットワイラー", "ドーベルマン", "秋田犬", "北海道犬"},
        // MF: バランス型の犬種
        {"ボーダーコリー", "柴犬", "ウェルシュコーギー", "オーストラリアンシェパード", "甲斐犬"},
        // FW: スピード・機動力のある犬種
        {"グレーハウンド", "ウィペット", "ジャック・ラッセル", "チワワ", "ダルメシアン"}
    };

    private static final String[] RARITY_COMMENTS = {
        "⭐ 地元のリーグで活躍中の犬",
        "⭐⭐ 隣町のクラブで評判の犬",
        "⭐⭐⭐ 海外スカウトが注目している犬"
    };

    public List<Player> scout(long budget) {
        List<Player> found = new ArrayList<>();
        int count = random.nextInt(3) + 2; // 2〜4匹発見
        for (int i = 0; i < count; i++) {
            found.add(generateDogPlayer(budget));
        }
        return found;
    }

    private Player generateDogPlayer(long budget) {
        String name = DOG_NAMES[random.nextInt(DOG_NAMES.length)];
        int age = 2 + random.nextInt(10); // 犬なので2〜11歳

        Player.Position[] positions = Player.Position.values();
        Player.Position pos = positions[random.nextInt(positions.length)];

        // ポジションに合った犬種
        String[] breedsForPos = BREEDS_BY_POSITION[pos.ordinal()];
        String breed = breedsForPos[random.nextInt(breedsForPos.length)];

        // 予算に応じた能力値
        int overall;
        if (budget > 800_000_000L)      overall = 78 + random.nextInt(14); // 78〜91
        else if (budget > 400_000_000L) overall = 68 + random.nextInt(16); // 68〜83
        else                            overall = 55 + random.nextInt(18); // 55〜72

        int potential  = Math.min(99, overall + random.nextInt(12));
        long salary    = (long)(overall * 8_000 + random.nextInt(200_000));
        long marketVal = (long)(overall * overall * 12_000L);

        Player p = new Player(name, age, "犬", breed, pos, overall, potential, salary, marketVal, 3);

        // ポジションに特化したスタッツ
        int spd, sht, pas, def, stm;
        switch (pos) {
            case GK -> { spd=45+random.nextInt(20); sht=40+random.nextInt(20); pas=50+random.nextInt(25);
                         def=overall+random.nextInt(8)-3; stm=75+random.nextInt(20); }
            case DF -> { spd=60+random.nextInt(25); sht=45+random.nextInt(20); pas=55+random.nextInt(25);
                         def=overall+random.nextInt(8)-3; stm=75+random.nextInt(20); }
            case MF -> { spd=65+random.nextInt(25); sht=60+random.nextInt(20); pas=overall+random.nextInt(8)-3;
                         def=55+random.nextInt(25); stm=70+random.nextInt(25); }
            default -> { spd=overall+random.nextInt(10)-3; sht=overall+random.nextInt(10)-3;
                         pas=60+random.nextInt(25); def=45+random.nextInt(20); stm=70+random.nextInt(20); }
        }
        p.setStats(clamp(spd), clamp(sht), clamp(pas), clamp(def), clamp(stm));
        p.setSpirit(60 + random.nextInt(40));
        return p;
    }

    private int clamp(int v) { return Math.max(1, Math.min(99, v)); }
}

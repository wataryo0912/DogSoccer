package model;

/**
 * 犬種ごとの感情特性テーブル
 *
 * 設計書の「犬種別感情傾向」をコードで表現する。
 * Player.getBreed() の文字列を渡して特性を取得する。
 *
 * 使い方:
 *   BreedTrait trait = BreedTrait.of(player.getBreed());
 *   boolean isShiba = trait.isShiba();
 */
public enum BreedTrait {

    //                           完璧主義 気分屋  忠誠心  フィジカル  知性 守護
    SHIBA         ("柴犬",          false, true,  false, false,    false, false),
    AKITA         ("秋田犬",         false, false, true,  false,    false, false),
    BORDER_COLLIE ("ボーダーコリー", true,  false, false, false,    true,  false),
    GERMAN_SHEPHERD("ジャーマン",    false, false, true,  false,    false, false),
    BULLDOG       ("ブルドッグ",     false, false, false, true,     false, false),
    BOXER         ("ボクサー",       false, false, false, true,     false, false),
    GOLDEN        ("ゴールデン",     false, false, false, false,    false, true),
    DOBERMAN      ("ドーベルマン",   false, false, false, false,    false, false),
    FRENCH_BULL   ("フレンチブルドッグ", false, true, false, false, false, false),
    HUSKY         ("ハスキー",       false, false, false, true,     false, false),
    POODLE        ("プードル",       true,  false, false, false,    true,  false),
    BEAGLE        ("ビーグル",       false, false, false, false,    false, false),
    DACHSHUND     ("ダックスフンド", false, true,  false, false,    false, false),
    SAINT_BERNARD ("セントバーナード",false, false, true,  true,    false, true),
    LABRADOR      ("ラブラドール",   false, false, false, false,    false, true),
    DALMATIAN     ("ダルメシアン",   false, false, false, true,     false, false),
    CORGI         ("コーギー",       false, false, true,  false,    false, false),
    HOKKAIDO      ("北海道犬",       false, false, true,  true,     false, false),
    UNKNOWN       ("不明",           false, false, false, false,    false, false);

    // ── 特性フラグ ────────────────────────────────────────────
    public final String breedName;
    /** 完璧主義 → 戦術理解が低いと frustration が急増 */
    public final boolean perfectionist;
    /** 気分屋  → mood が週30%確率でランダム変動（柴犬・F.ブルドッグ） */
    public final boolean moodSwing;
    /** 忠誠心強 → loyalty の基本値が高く、transfer offer を断りやすい */
    public final boolean loyal;
    /** フィジカル → contact foul 時の rivalry 増加量が2倍（ブルドッグ系） */
    public final boolean physical;
    /** 知性型    → soccer_iq 相当の判断力ボーナス（将来の戦術実装用） */
    public final boolean intelligent;
    /** 守護型    → team_harmony の下限を保護（ゴールデン系） */
    public final boolean guardian;

    BreedTrait(String name, boolean perf, boolean mood,
               boolean loyal, boolean phys, boolean intel, boolean guard) {
        this.breedName    = name;
        this.perfectionist= perf;
        this.moodSwing    = mood;
        this.loyal        = loyal;
        this.physical     = phys;
        this.intelligent  = intel;
        this.guardian     = guard;
    }

    /** breed 文字列から特性を返す（マッチしない場合は UNKNOWN） */
    public static BreedTrait of(String breed) {
        if (breed == null) return UNKNOWN;
        String lower = breed.toLowerCase()
            .replace("　", "")
            .replace(" ", "");

        for (BreedTrait t : values()) {
            if (lower.contains(t.breedName
                    .toLowerCase()
                    .replace("　", "")
                    .replace(" ", ""))) {
                return t;
            }
        }
        // 部分一致のフォールバック
        if (lower.contains("柴"))       return SHIBA;
        if (lower.contains("秋田"))     return AKITA;
        if (lower.contains("ボーダー")) return BORDER_COLLIE;
        if (lower.contains("ジャーマン") || lower.contains("シェパード")) return GERMAN_SHEPHERD;
        if (lower.contains("ブルドッグ") && lower.contains("フレンチ"))  return FRENCH_BULL;
        if (lower.contains("ブルドッグ")) return BULLDOG;
        if (lower.contains("ボクサー")) return BOXER;
        if (lower.contains("ゴールデン")) return GOLDEN;
        if (lower.contains("ドーベルマン")) return DOBERMAN;
        if (lower.contains("ハスキー")) return HUSKY;
        if (lower.contains("プードル")) return POODLE;
        if (lower.contains("ビーグル")) return BEAGLE;
        if (lower.contains("ダックス")) return DACHSHUND;
        if (lower.contains("セントバーナード")) return SAINT_BERNARD;
        if (lower.contains("コーギー")) return CORGI;

        return UNKNOWN;
    }

    // ── 便利メソッド ──────────────────────────────────────────

    public boolean isShiba()       { return this == SHIBA; }
    public boolean isBulldog()     { return this == BULLDOG || this == FRENCH_BULL; }
    public boolean isGolden()      { return this == GOLDEN || this == LABRADOR; }
    public boolean isFrenchBull()  { return this == FRENCH_BULL; }
    public boolean isDoberman()    { return this == DOBERMAN; }
    public boolean isGermanShepherd() { return this == GERMAN_SHEPHERD; }

    /**
     * 他犬種との相性を返す
     * @return  1=相性良し(chemistry+8)  0=普通  -1=相性悪し(harmony-8)
     */
    public int compatibilityWith(BreedTrait other) {
        // ◎ 相性良し
        if (this == GERMAN_SHEPHERD && other == BOXER)   return +1;
        if (this == BOXER && other == GERMAN_SHEPHERD)   return +1;
        if (this == SHIBA && other == GOLDEN)            return +1;
        if (this == GOLDEN && other == SHIBA)            return +1;
        if (this == POODLE && other == DACHSHUND)        return +1;
        if (this == DACHSHUND && other == POODLE)        return +1;
        if (this == SAINT_BERNARD && other == CORGI)     return +1;
        if (this == CORGI && other == SAINT_BERNARD)     return +1;

        // × 相性悪し
        if (this == BORDER_COLLIE && other == BULLDOG)   return -1;
        if (this == BULLDOG && other == BORDER_COLLIE)   return -1;
        if (this == DOBERMAN && other == SHIBA)          return -1;
        if (this == SHIBA && other == DOBERMAN)          return -1;
        if (this == HUSKY && other == DACHSHUND)         return -1;
        if (this == DACHSHUND && other == HUSKY)         return -1;

        return 0;
    }

    @Override
    public String toString() { return breedName; }
}

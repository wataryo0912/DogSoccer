package model;

/**
 * 犬選手クラス（感情システム統合版）
 *
 * 既存フィールドはすべてそのまま維持。
 * EmotionState フィールドを1つ追加するだけで感情システムが有効になる。
 *
 * 変更点（既存コードからの差分）:
 *   1. import なし（同パッケージのため不要）
 *   2. フィールド追加: private EmotionState emotion
 *   3. コンストラクタ内: emotion = new EmotionState(); を追加
 *   4. Getter/Setter追加: getEmotion() / setEmotion()
 *   5. getSpirit() の定義はそのまま維持（DB互換性のため）
 *   6. toString() に感情情報を追加
 *
 * ── 既存コードへの影響 ─────────────────────────────────────
 *   ・既存の spirit フィールドはそのまま残す
 *   ・PlayerCardView の isSlump() は spirit <= 40 を使っているが、
 *     今後は emotion.getMood() == Mood.SLUMP に移行を推奨
 *   ・DB保存は EmotionStateDao（別ファイル）で行う
 */
public class Player extends Person {

    public enum Position { GK, DF, MF, FW }

    public enum PlayerRole {
        REGISTERED("登録"),
        BENCH     ("ベンチ"),
        INACTIVE  ("スカッド外"),
        ACADEMY   ("下部組織"),
        RETIRED   ("引退");

        public final String label;
        PlayerRole(String label) { this.label = label; }
    }

    // ── DB主キー ─────────────────────────────────────────────
    private int id;

    // ── Excelから来るフィールド ───────────────────────────────
    private String lastName;
    private String breed;
    private String dominantFoot;
    private String uniformName;
    private int    shirtNumber;
    private boolean captain;
    private String imageFile;
    private SpecialMove specialMove;
    private PlayerRole  role                  = PlayerRole.REGISTERED;
    private boolean     pendingPromotion       = false;
    private boolean     retirementAnnounced    = false;
    private int         reincarnationSeason    = -1;
    private String      formerClubName         = "";

    // ── ゲームデータ ─────────────────────────────────────────
    private Position position;
    private int overall, potential;
    private long salary, marketValue;
    private int contractYears;
    private int speed, shooting, passing, defending, stamina, spirit;

    // ── 感情システム（NEW） ────────────────────────────────────
    /** 感情パラメータ一式。コンストラクタで初期化される。 */
    private EmotionState emotion = new EmotionState();

    /** キャッシュされた犬種特性（毎回 BreedTrait.of() を呼ばないよう） */
    private transient BreedTrait breedTraitCache = null;

    private static final String[] RANDOM_BREEDS = {
        "柴犬","秋田犬","北海道犬","ゴールデン","ラブラドール",
        "ボーダーコリー","ジャーマン","ダルメシアン","ハスキー","コーギー"
    };

    // ── コンストラクタ ───────────────────────────────────────

    public Player(String name, int age, String nationality,
                  Position pos, int overall, int potential,
                  long salary, long mv, int contract) {
        this(name, age, nationality,
             RANDOM_BREEDS[new java.util.Random().nextInt(RANDOM_BREEDS.length)],
             pos, overall, potential, salary, mv, contract);
    }

    public Player(String name, int age, String nationality, String breed,
                  Position pos, int overall, int potential,
                  long salary, long mv, int contract) {
        super(name, age, nationality);
        this.breed         = breed;
        this.position      = pos;
        this.overall       = overall;
        this.potential     = potential;
        this.salary        = salary;
        this.marketValue   = mv;
        this.contractYears = contract;
        this.lastName      = "";
        this.uniformName   = name.toUpperCase();
        this.shirtNumber   = 0;
        this.dominantFoot  = "右";
        this.captain       = false;
        this.imageFile     = "";
        this.spirit        = 75;

        // 感情システム初期値（犬種に応じた初期 loyalty を設定）
        this.emotion = new EmotionState();
        initEmotionByBreed();

        autoStats();
    }

    /**
     * 犬種に応じた感情パラメータの初期値を設定
     * コンストラクタ・DB復元後どちらからも呼べる
     */
    private void initEmotionByBreed() {
        BreedTrait trait = getBreedTrait();
        if (trait.loyal) {
            emotion.setLoyalty(65); // 忠誠心型は高め
        }
        if (trait.moodSwing) {
            emotion.setConfidence(55); // 気分屋は confidence が不安定
        }
        if (trait.perfectionist) {
            emotion.setConfidence(70); // 完璧主義者は高い自信を持つ
        }
    }

    // ── 犬種特性アクセス ─────────────────────────────────────

    /**
     * この選手の犬種特性を返す（キャッシュあり）
     * MatchSimulator・WeeklyEventService から呼ぶ
     */
    public BreedTrait getBreedTrait() {
        if (breedTraitCache == null) {
            breedTraitCache = BreedTrait.of(breed);
        }
        return breedTraitCache;
    }

    // ── 能力値（感情補正あり） ───────────────────────────────

    /**
     * 感情補正を含めた実効的なシュート力
     * MatchSimulator でこちらを使うことを推奨
     */
    public int getEffectiveShooting() {
        return applyEmotionBonus(shooting + (int)(shooting * emotion.getShootingBonus()));
    }

    /**
     * 感情補正を含めた実効的な能力値（全般）
     * stat: 元の能力値（speed, passing 等）
     */
    public int getEffectiveStat(int stat) {
        return applyEmotionBonus((int)(stat * (1.0 + emotion.getStatMultiplier())));
    }

    private int applyEmotionBonus(int value) {
        return Math.max(1, Math.min(99, value));
    }

    // ── 既存メソッド（変更なし） ─────────────────────────────

    private void autoStats() {
        java.util.Random r = new java.util.Random();
        int b = overall;
        switch (position) {
            case FW -> { shooting=cl(b+r.nextInt(10)-2); speed=cl(b+r.nextInt(8)-2);
                         passing=cl(b-r.nextInt(12));    defending=cl(b-r.nextInt(20));
                         stamina=cl(b+r.nextInt(6)-3); }
            case MF -> { passing=cl(b+r.nextInt(10)-2);  stamina=cl(b+r.nextInt(8)-2);
                         shooting=cl(b-r.nextInt(12));   defending=cl(b-r.nextInt(10));
                         speed=cl(b+r.nextInt(6)-3); }
            case DF -> { defending=cl(b+r.nextInt(10)-2);stamina=cl(b+r.nextInt(8)-2);
                         passing=cl(b-r.nextInt(10));    shooting=cl(b-r.nextInt(22));
                         speed=cl(b+r.nextInt(6)-3); }
            case GK -> { defending=cl(b+r.nextInt(10)-2);stamina=cl(b+r.nextInt(6)-2);
                         passing=cl(b-r.nextInt(15));    shooting=cl(b-r.nextInt(32));
                         speed=cl(b-r.nextInt(12)); }
        }
    }
    private int cl(int v) { return Math.max(1, Math.min(99, v)); }

    public void grow(int amount) {
        if (overall < potential) { overall = Math.min(potential, overall + amount); autoStats(); }
    }

    @Override public void ageing() {
        super.ageing();
        if (age > 33) { overall = Math.max(1, overall - 1); autoStats(); }
        contractYears--;
        // 高齢化による loyalty 微増（長くいる選手は愛着が増す）
        if (age > 30) emotion.setLoyalty(Math.min(100, emotion.getLoyalty() + 2));
    }

    public void setStats(int spd, int sht, int pas, int def, int stm) {
        speed=spd; shooting=sht; passing=pas; defending=def; stamina=stm;
    }

    public String getFullName() {
        return (lastName != null && !lastName.isEmpty()) ? lastName + " " + name : name;
    }

    public String getUniformLabel() {
        String uname = (uniformName != null && !uniformName.isEmpty()) ? uniformName : name;
        return "#" + shirtNumber + "  " + uname;
    }

    // ── Getters ──────────────────────────────────────────────
    public int        getId()                    { return id; }
    public String     getLastName()              { return lastName; }
    public String     getBreed()                 { return breed; }
    public String     getDominantFoot()          { return dominantFoot; }
    public String     getUniformName()           { return uniformName; }
    public int        getShirtNumber()           { return shirtNumber; }
    public boolean    isCaptain()                { return captain; }
    public PlayerRole getRole()                  { return role; }
    public boolean    isPendingPromotion()        { return pendingPromotion; }
    public boolean    isRetirementAnnounced()    { return retirementAnnounced; }
    public int        getReincarnationSeason()   { return reincarnationSeason; }
    public String     getFormerClubName()        { return formerClubName; }
    public String     getImageFile()             { return imageFile; }
    public Position   getPosition()              { return position; }
    public int        getOverall()               { return overall; }
    public int        getPotential()             { return potential; }
    public long       getSalary()                { return salary; }
    public long       getMarketValue()           { return marketValue; }
    public int        getContractYears()         { return contractYears; }
    public int        getSpeed()                 { return speed; }
    public int        getShooting()              { return shooting; }
    public int        getPassing()               { return passing; }
    public int        getDefending()             { return defending; }
    public int        getStamina()               { return stamina; }
    public int        getSpirit()                { return spirit; } // DB互換維持
    public EmotionState getEmotion()             { return emotion; }

    // ── Setters ──────────────────────────────────────────────
    public void setId(int id)                       { this.id = id; }
    public void setLastName(String v)               { this.lastName = v != null ? v : ""; }
    public void setBreed(String v)                  { this.breed = v; breedTraitCache = null; }
    public void setDominantFoot(String v)           { this.dominantFoot = v; }
    public void setUniformName(String v)            { this.uniformName = v != null ? v : ""; }
    public void setShirtNumber(int v)               { this.shirtNumber = v; }
    public void setCaptain(boolean v)               { this.captain = v; }
    public void setRole(PlayerRole v)               { this.role = v != null ? v : PlayerRole.REGISTERED; }
    public void setPendingPromotion(boolean v)      { this.pendingPromotion = v; }
    public void setRetirementAnnounced(boolean v)   { this.retirementAnnounced = v; }
    public void setReincarnationSeason(int v)       { this.reincarnationSeason = v; }
    public void setFormerClubName(String v)         { this.formerClubName = v != null ? v : ""; }
    public void setImageFile(String v)              { this.imageFile = v != null ? v : ""; }
    public void setSalary(long v)                   { this.salary = v; }
    public void setMarketValue(long v)              { this.marketValue = v; }
    public void setContractYears(int v)             { this.contractYears = v; }
    public void setSpeed    (int v) { this.speed     = Math.max(1, Math.min(99, v)); }
    public void setShooting (int v) { this.shooting  = Math.max(1, Math.min(99, v)); }
    public void setPassing  (int v) { this.passing   = Math.max(1, Math.min(99, v)); }
    public void setDefending(int v) { this.defending = Math.max(1, Math.min(99, v)); }
    public void setStamina  (int v) { this.stamina   = Math.max(1, Math.min(99, v)); }
    public void setSpirit   (int v) { this.spirit    = Math.max(1, Math.min(99, v)); }
    public void setEmotion(EmotionState v) { this.emotion = v != null ? v : new EmotionState(); }

    public SpecialMove getSpecialMove()           { return specialMove; }
    public void setSpecialMove(SpecialMove v)     { this.specialMove = v; }
    public boolean hasSpecialMove()               { return specialMove != null; }

    /** ユニフォーム画像ファイル名（ClubVisualView から呼ばれる） */
    public String getUniformImageFile()           { return ""; } // Club側で管理

    @Override public String toString() {
        return String.format("%s[%s] %s (%s) OVR:%d #%d %s",
            captain ? "©" : "", position, getFullName(), breed,
            overall, shirtNumber, emotion);
    }
}

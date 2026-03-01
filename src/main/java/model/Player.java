package model;

/**
 * 犬選手クラス
 *
 * 【画像・データ紐づけ設計】
 *   Excelの「画像データ」列 → image_file フィールド → DB players.image_file
 *   実ファイルパス: resources/<image_file>
 *   UI表示: ExcelLoaderService.toImageURL(imageFile) でJavaFX Image用URLに変換
 */
public class Player extends Person {

    public enum Position { GK, DF, MF, FW }

    /**
     * スカッド内での役割区分
     *
     *  REGISTERED  試合登録メンバー（主力・控え）最大25名
     *  BENCH       スカッド外ベンチ要員（練習参加・試合出場不可）
     *  INACTIVE    スカッド外（怪我・ペナルティ等）
     *  ACADEMY     下部組織（育成専用・昇格申請で翌週 REGISTERED へ）
     */
    public enum PlayerRole {
        REGISTERED("登録"),
        BENCH     ("ベンチ"),
        INACTIVE  ("スカッド外"),
        ACADEMY   ("下部組織"),
        RETIRED   ("引退");            // 引退済み（スカッドから除外）

        public final String label;
        PlayerRole(String label) { this.label = label; }
    }

    // ── DB主キー ─────────────────────────────────────────────
    private int id;

    // ── Excelから来るフィールド（画像含む） ──────────────────
    private String lastName;       // 選手姓   例: 本田
    private String breed;          // 犬種     例: フレンチブルドック
    private String dominantFoot;   // 利き足   右/左
    private String uniformName;    // ユニ名   例: BUNSUKE
    private int    shirtNumber;    // 背番号   例: 518
    private boolean captain;       // キャプテン
    private String imageFile;      // 画像ファイル名 例: Gemini_Generated_Image_xxx.png
    private SpecialMove specialMove;  // 必殺技（nullの場合は未習得）
    private PlayerRole  role              = PlayerRole.REGISTERED;
    private boolean     pendingPromotion      = false;  // 翌週 REGISTERED 昇格フラグ
    private boolean     retirementAnnounced   = false;  // シーズン開始時に引退表明済み
    private int         reincarnationSeason   = -1;     // 転生予定シーズン（-1=未設定）
    private String      formerClubName        = "";     // 転生時のゆかりクラブ名

    // ── ゲームデータ ─────────────────────────────────────────
    private Position position;
    private int overall, potential;
    private long salary, marketValue;
    private int contractYears;
    private int speed, shooting, passing, defending, stamina, spirit;

    private static final String[] RANDOM_BREEDS = {
        "柴犬","秋田犬","北海道犬","ゴールデン","ラブラドール",
        "ボーダーコリー","ジャーマン","ダルメシアン","ハスキー","コーギー"
    };

    // ── コンストラクタ ───────────────────────────────────────

    /** ランダム犬種（旧クラブ用） */
    public Player(String name, int age, String nationality,
                  Position pos, int overall, int potential,
                  long salary, long mv, int contract) {
        this(name, age, nationality,
             RANDOM_BREEDS[new java.util.Random().nextInt(RANDOM_BREEDS.length)],
             pos, overall, potential, salary, mv, contract);
    }

    /** 犬種指定（CSV/DB読み込み） */
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
        // デフォルト値
        this.lastName      = "";
        this.uniformName   = name.toUpperCase();
        this.shirtNumber   = 0;
        this.dominantFoot  = "右";
        this.captain       = false;
        this.imageFile     = "";
        this.spirit        = 75;
        autoStats();
    }

    // ── 能力値自動生成 ────────────────────────────────────────
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
    }

    /** DBからリストア時に全能力値を直接セット */
    public void setStats(int spd, int sht, int pas, int def, int stm) {
        speed=spd; shooting=sht; passing=pas; defending=def; stamina=stm;
    }

    /** 表示用フルネーム */
    public String getFullName() {
        return (lastName != null && !lastName.isEmpty()) ? lastName + " " + name : name;
    }

    /** 選手カード用ラベル（背番号＋ユニ名） */
    public String getUniformLabel() {
        String uname = (uniformName != null && !uniformName.isEmpty()) ? uniformName : name;
        return "#" + shirtNumber + "  " + uname;
    }

    // ── Getters ──────────────────────────────────────────────
    public int      getId()             { return id; }
    public String   getLastName()       { return lastName; }
    public String   getBreed()          { return breed; }
    public String   getDominantFoot()   { return dominantFoot; }
    public String   getUniformName()    { return uniformName; }
    public int      getShirtNumber()    { return shirtNumber; }
    public boolean  isCaptain()         { return captain; }
    public PlayerRole getRole()          { return role; }
    public boolean  isPendingPromotion()      { return pendingPromotion; }
    public boolean  isRetirementAnnounced()   { return retirementAnnounced; }
    public int      getReincarnationSeason()  { return reincarnationSeason; }
    public String   getFormerClubName()       { return formerClubName; }
    public String   getImageFile()      { return imageFile; }
    public Position getPosition()       { return position; }
    public int      getOverall()        { return overall; }
    public int      getPotential()      { return potential; }
    public long     getSalary()         { return salary; }
    public long     getMarketValue()    { return marketValue; }
    public int      getContractYears()  { return contractYears; }
    public int      getSpeed()          { return speed; }
    public int      getShooting()       { return shooting; }
    public int      getPassing()        { return passing; }
    public int      getDefending()      { return defending; }
    public int      getStamina()        { return stamina; }
    public int      getSpirit()         { return spirit; }

    // ── Setters ──────────────────────────────────────────────
    public void setId(int id)                 { this.id = id; }
    public void setLastName(String v)         { this.lastName = v != null ? v : ""; }
    public void setBreed(String v)            { this.breed = v; }
    public void setDominantFoot(String v)     { this.dominantFoot = v; }
    public void setUniformName(String v)      { this.uniformName = v != null ? v : ""; }
    public void setShirtNumber(int v)         { this.shirtNumber = v; }
    public void setCaptain(boolean v)         { this.captain = v; }
    public void setRole(PlayerRole v)         { this.role = v != null ? v : PlayerRole.REGISTERED; }
    public void setPendingPromotion(boolean v)         { this.pendingPromotion = v; }
    public void setRetirementAnnounced(boolean v)      { this.retirementAnnounced = v; }
    public void setReincarnationSeason(int v)          { this.reincarnationSeason = v; }
    public void setFormerClubName(String v)            { this.formerClubName = v != null ? v : ""; }
    public void setImageFile(String v)        { this.imageFile = v != null ? v : ""; }
    public void setSalary(long v)             { this.salary = v; }
    public void setMarketValue(long v)        { this.marketValue = v; }
    public void setContractYears(int v)       { this.contractYears = v; }
    public void setSpeed    (int v) { this.speed     = Math.max(1, Math.min(99, v)); }
    public void setShooting (int v) { this.shooting  = Math.max(1, Math.min(99, v)); }
    public void setPassing  (int v) { this.passing   = Math.max(1, Math.min(99, v)); }
    public void setDefending(int v) { this.defending = Math.max(1, Math.min(99, v)); }
    public void setStamina  (int v) { this.stamina   = Math.max(1, Math.min(99, v)); }
    public void setSpirit   (int v) { this.spirit    = Math.max(1, Math.min(99, v)); }

    public SpecialMove getSpecialMove() { return specialMove; }
    public void setSpecialMove(SpecialMove v) { this.specialMove = v; }
    public boolean hasSpecialMove() { return specialMove != null; }

    @Override public String toString() {
        return String.format("%s[%s] %s (%s) OVR:%d #%d%s",
            captain?"©":"", position, getFullName(), breed, overall, shirtNumber,
            imageFile.isEmpty()?"":" 📷");
    }
}

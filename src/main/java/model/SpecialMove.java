package model;

/**
 * 選手の必殺技
 *
 * 【DB設計】
 *   special_moves テーブル（id, name, description, image_file, power, move_type）
 *   players_special_moves テーブル（player_id FK, special_move_id FK）
 *   → 1選手に1必殺技を紐づけ（将来複数対応も可能な中間テーブル設計）
 *
 * 【発動条件】
 *   試合中にGOAL/CHANCEイベントが発生した際、発動者の必殺技を30%の確率で発動
 *
 * 【カットイン表示】
 *   発動時: 選手画像(imageFile) + 必殺技画像(moveImageFile) + 技名テキスト
 *   を画面中央にスライドインアニメーションで表示
 */
public class SpecialMove {

    public enum MoveType {
        SHOOT,    // シュート系（FW向け）
        DRIBBLE,  // ドリブル系（MF/FW向け）
        TACKLE,   // タックル系（DF向け）
        SAVE,     // セーブ系（GK向け）
        SPIRIT    // 根性系（全ポジション）
    }

    private int    id;
    private String name;          // 技名 例: 「流星一蹴」
    private String description;   // 説明 例: 「全速力で駆け込み渾身の一蹴！」
    private String imageFile;     // 必殺技演出画像ファイル名（resources/以下）
    private int    power;         // 威力 1〜100（ゴール確率に加算）
    private MoveType moveType;

    public SpecialMove(String name, String description, String imageFile,
                       int power, MoveType moveType) {
        this.name        = name;
        this.description = description;
        this.imageFile   = imageFile;
        this.power       = power;
        this.moveType    = moveType;
    }

    // ── Getters ──────────────────────────────────────────────
    public int      getId()          { return id; }
    public String   getName()        { return name; }
    public String   getDescription() { return description; }
    public String   getImageFile()   { return imageFile; }
    public int      getPower()       { return power; }
    public MoveType getMoveType()    { return moveType; }

    // ── Setters ──────────────────────────────────────────────
    public void setId(int id)               { this.id = id; }
    public void setName(String v)           { this.name = v; }
    public void setDescription(String v)    { this.description = v; }
    public void setImageFile(String v)      { this.imageFile = v != null ? v : ""; }
    public void setPower(int v)             { this.power = v; }
    public void setMoveType(MoveType v)     { this.moveType = v; }

    @Override
    public String toString() {
        return String.format("【%s】%s (威力:%d)", name, description, power);
    }
}

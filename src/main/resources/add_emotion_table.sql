-- 感情システム用テーブル追加
-- 既存の players テーブルはそのまま維持し、別テーブルで管理する

CREATE TABLE IF NOT EXISTS player_emotions (
    player_id     INT          NOT NULL,
    confidence    INT          NOT NULL DEFAULT 60,
    frustration   INT          NOT NULL DEFAULT 0,
    loyalty       INT          NOT NULL DEFAULT 50,
    rivalry       INT          NOT NULL DEFAULT 0,
    mood          VARCHAR(10)  NOT NULL DEFAULT 'NORMAL',
    benched_weeks INT          NOT NULL DEFAULT 0,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                  ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (player_id),
    CONSTRAINT fk_emotion_player
        FOREIGN KEY (player_id) REFERENCES players(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='選手感情パラメータ（EmotionState の永続化）';

-- 確認用
SELECT 'player_emotions テーブル作成完了' AS status;

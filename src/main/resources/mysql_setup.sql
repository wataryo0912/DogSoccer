-- ============================================================
-- Soccer Manager MySQL セットアップ
-- 実行: mysql -u root -p < resources/mysql_setup.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS soccer_manager
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE soccer_manager;

-- 専用ユーザー作成
CREATE USER IF NOT EXISTS 'soccer_user'@'localhost' IDENTIFIED BY 'soccer_pass';
GRANT ALL PRIVILEGES ON soccer_manager.* TO 'soccer_user'@'localhost';
FLUSH PRIVILEGES;

-- ============================================================
-- テーブルはアプリ起動時に自動生成されます (DatabaseManager.java)
-- 以下は運用・確認用クエリ集
-- ============================================================

-- ■ 選手一覧（必殺技付き）
SELECT
    p.shirt_number  AS 背番号,
    p.uniform_name  AS ユニ名,
    p.last_name     AS 名前,
    p.breed         AS 犬種,
    p.position      AS ポジション,
    p.overall       AS OVR,
    p.image_file    AS 選手画像,
    sm.name         AS 必殺技,
    sm.image_file   AS 必殺技画像,
    sm.power        AS 威力
FROM players p
LEFT JOIN players_special_moves psm ON p.id = psm.player_id
LEFT JOIN special_moves sm ON psm.special_move_id = sm.id
WHERE p.club_id = (SELECT id FROM clubs WHERE name = 'クラブ・ぶん助')
ORDER BY p.shirt_number;

-- ■ 画像マスター一覧
SELECT image_type, file_name, description FROM images ORDER BY image_type, file_name;

-- ■ 未使用画像の検出
SELECT i.file_name, i.image_type
FROM images i
WHERE i.file_name NOT IN
    (SELECT DISTINCT image_file FROM players  WHERE image_file  != '')
  AND i.file_name NOT IN
    (SELECT DISTINCT uniform_image FROM clubs WHERE uniform_image != '')
  AND i.file_name NOT IN
    (SELECT DISTINCT image_file FROM special_moves WHERE image_file != '');

-- ============================================================
-- 新選手追加テンプレート
-- ============================================================
-- Step1: 画像マスターに登録
-- INSERT INTO images (file_name, image_type, description)
-- VALUES ('player_new.png', 'PLAYER', '新選手の写真');
--
-- Step2: 選手を登録（club_id は clubs テーブルで確認）
-- INSERT INTO players
--   (club_id, name, last_name, breed, position, overall, potential,
--    salary, market_value, contract_years,
--    uniform_name, shirt_number, dominant_foot, is_captain, image_file)
-- VALUES
--   (1, '文助', '犬', 'フレンチブルドック', 'FW', 80, 90,
--    600000, 100000000, 3,
--    'NEWDOG', 99, '右', 0, 'player_new.png');

-- ============================================================
-- 新チーム追加テンプレート
-- ============================================================
-- INSERT INTO images (file_name, image_type, description)
-- VALUES ('uniform_new.png', 'UNIFORM', '新チームユニフォーム');
--
-- INSERT INTO clubs
--   (name, color, formation, breed, uniform_image, budget, weekly_salary_budget)
-- VALUES
--   ('新チームFC', '#cc0000', '4-3-3', '秋田犬', 'uniform_new.png',
--    800000000, 5000000);

-- ============================================================
-- 新必殺技追加テンプレート
-- ============================================================
-- Step1: 演出画像を登録
-- INSERT INTO images (file_name, image_type, description)
-- VALUES ('move_new.png', 'SPECIAL_MOVE', '新必殺技の演出');
--
-- Step2: 必殺技を登録
-- INSERT INTO special_moves (name, description, image_file, power, move_type)
-- VALUES ('疾風怒濤', '嵐のような連続ドリブル！', 'move_new.png', 85, 'DRIBBLE');
--
-- Step3: 選手に紐づけ（uniform_name で指定）
-- INSERT INTO players_special_moves (player_id, special_move_id)
-- SELECT p.id, sm.id
-- FROM players p, special_moves sm
-- WHERE p.uniform_name = 'BUNSUKE' AND sm.name = '疾風怒濤'
-- ON DUPLICATE KEY UPDATE special_move_id = sm.id;

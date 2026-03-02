USE soccer_manager;

START TRANSACTION;

-- 1) Register images
INSERT INTO images (file_name, image_type, description) VALUES
('img_bunsuke.png',   'PLAYER', 'Club Bunsuke BUNSUKE'),
('img_buru.png',      'PLAYER', 'Club Bunsuke BURU'),
('img_kintaro.png',   'PLAYER', 'Club Bunsuke KINTARO'),
('img_tomejiro.png',  'PLAYER', 'Club Bunsuke TOMEJIRO'),
('img_kikujiro.png',  'PLAYER', 'Club Bunsuke KIKUJIRO'),
('img_takeshi.png',   'PLAYER', 'Club Bunsuke TAKESHI'),
('img_sebastian.png', 'PLAYER', 'Club Bunsuke SEBASTIAN'),
('img_tanaka.png',    'PLAYER', 'Club Bunsuke TANAKA'),
('img_hanao.png',     'PLAYER', 'Club Bunsuke HANAO'),
('img_anbe.png',      'PLAYER', 'Club Bunsuke ANBE'),
('img_gaston.png',    'PLAYER', 'Club Bunsuke GASTON')
ON DUPLICATE KEY UPDATE
  image_type  = VALUES(image_type),
  description = VALUES(description);

-- 2) Update players.image_file
UPDATE players p
SET p.image_file = CASE p.uniform_name
  WHEN 'BUNSUKE'   THEN 'img_bunsuke.png'
  WHEN 'BURU'      THEN 'img_buru.png'
  WHEN 'KINTARO'   THEN 'img_kintaro.png'
  WHEN 'TOMEJIRO'  THEN 'img_tomejiro.png'
  WHEN 'KIKUJIRO'  THEN 'img_kikujiro.png'
  WHEN 'TAKESHI'   THEN 'img_takeshi.png'
  WHEN 'SEBASTIAN' THEN 'img_sebastian.png'
  WHEN 'TANAKA'    THEN 'img_tanaka.png'
  WHEN 'HANAO'     THEN 'img_hanao.png'
  WHEN 'ANBE'      THEN 'img_anbe.png'
  WHEN 'GASTON'    THEN 'img_gaston.png'
  ELSE p.image_file
END
WHERE p.uniform_name IN (
  'BUNSUKE','BURU','KINTARO','TOMEJIRO','KIKUJIRO',
  'TAKESHI','SEBASTIAN','TANAKA','HANAO','ANBE','GASTON'
);

COMMIT;

-- Verify
SELECT
  p.id,
  p.uniform_name,
  p.image_file
FROM players p
ORDER BY p.shirt_number;

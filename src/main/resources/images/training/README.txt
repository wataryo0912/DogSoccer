High-quality full-body actor sprites (optional)

If you place these files, Main Menu training scene uses them automatically:
- actor_small.png
- actor_medium.png
- actor_large.png

Per-player sprite (higher priority than body-type defaults):
Place files under:
- src/main/resources/images/training/players/

Accepted names:
- <uniform_name lower>.png   (e.g. takeshi.png, hanao.png, anbe.png)
- <uniform_name>.png         (e.g. TAKESHI.png)
- actor_<uniform_name lower>.png
- full_<uniform_name lower>.png

Recommended:
- Transparent PNG
- Facing right (side/front hybrid)
- Height around 900px (app scales down automatically)

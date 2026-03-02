package service;

import db.ClubDao;
import db.PlayerDao;
import model.Club;
import model.Player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.net.URL;
import java.util.*;

/**
 * Excel → CSV → DB → メモリ 連携サービス
 *
 * 【画像とデータの紐づけ設計（Excelと同じ考え方）】
 *
 *  Excel                          Java / DB
 *  ─────────────────────────────────────────────────────
 *  選手シート.画像データ  ──→  Player.imageFile
 *                                  ↓
 *                          DB: players.image_file
 *                                  ↓
 *                          resources/<image_file>  (実ファイル)
 *                                  ↓
 *                          ExcelLoaderService.toImageURL()
 *                                  ↓
 *                          JavaFX: new Image(url)
 *
 *  チームシート.ユニフォーム ──→ Club.uniformImageFile
 *                                  ↓
 *                          DB: clubs.uniform_image
 *                                  ↓
 *                          resources/<uniform_image>  (実ファイル)
 *
 *  resources/players.csv : Excelを起動前にPythonで変換したCSV
 *  resources/teams.csv   : 同上
 */
public class ExcelLoaderService {

    private static final String RESOURCES = "resources/";
    private static final String DATA_DIR = "data/";

    private final ClubDao   clubDao   = new ClubDao();
    private final PlayerDao playerDao = new PlayerDao();

    // ─────────────────────────────────────────────────────────
    // PUBLIC: CSV → DB + メモリへロード
    // ─────────────────────────────────────────────────────────

    /**
     * players.csv / teams.csv を読み込んで DB に投入し、
     * allClubs のメモリ状態も更新する。
     * 既にDBに同じ選手・チームがあればスキップ（冪等）。
     */
    public void loadFromCSV(List<Club> allClubs) {
        String playersCSV = resolveDataCsvPath("players.csv");
        String teamsCSV   = resolveDataCsvPath("teams.csv");

        if (playersCSV == null || teamsCSV == null) {
            System.out.println("[Excel] CSVが見つかりません: players.csv / teams.csv");
            return;
        }

        try {
            Map<String, Club> clubByName = upsertTeams(teamsCSV, allClubs);
            upsertPlayers(playersCSV, clubByName, allClubs);
            System.out.println("[Excel] Excelデータのロード完了");
        } catch (Exception e) {
            System.err.println("[Excel] ロードエラー: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────
    // PRIVATE: チーム処理
    // ─────────────────────────────────────────────────────────

    private Map<String, Club> upsertTeams(String csvPath, List<Club> allClubs)
            throws Exception {

        Map<String, Club> map = new HashMap<>();
        List<Map<String,String>> rows = readCSV(csvPath);

        for (Map<String,String> row : rows) {
            String teamName    = row.getOrDefault("team_name", "").trim();
            String uniformImg  = row.getOrDefault("uniform_image", "").trim();
            if (teamName.isEmpty()) continue;

            // メモリのリストから既存クラブを探す
            Club club = allClubs.stream()
                .filter(c -> c.getName().equals(teamName))
                .findFirst().orElse(null);

            if (club == null) {
                // Excelにあって initialデータにないクラブ → 新規作成
                club = new Club(teamName, 500_000_000L, 3_000_000L);
                club.setColor("#4a7a35");  // クラブ・ぶん助カラー
                allClubs.add(club);
            }

            // 画像ファイル紐づけ（ユニフォーム画像）
            if (!uniformImg.isEmpty()) {
                club.setUniformImageFile(uniformImg);
            }

            // DB upsert
            try {
                int clubId = clubDao.upsert(club);
                club.setId(clubId);
            } catch (SQLException ex) {
                System.err.println("[Excel] クラブDB保存エラー: " + ex.getMessage());
            }
            map.put(teamName, club);
        }
        return map;
    }

    // ─────────────────────────────────────────────────────────
    // PRIVATE: 選手処理
    // ─────────────────────────────────────────────────────────

    private void upsertPlayers(String csvPath, Map<String, Club> clubByName, List<Club> allClubs)
            throws Exception {

        List<Map<String,String>> rows = readCSV(csvPath);

        for (Map<String,String> row : rows) {
            String lastName    = row.getOrDefault("last_name",   "").trim();
            String firstName   = row.getOrDefault("first_name",  "").trim();
            String breed       = row.getOrDefault("breed",       "犬").trim();
            String foot        = row.getOrDefault("dominant_foot","右").trim();
            String uniformName = row.getOrDefault("uniform_name","").trim();
            int    shirtNum    = parseInt(row.get("shirt_number"), 0);
            int    age         = parseInt(row.get("age"),          4);
            String posStr      = row.getOrDefault("position",    "MF").trim();
            String teamName    = row.getOrDefault("team_name",   "").trim();
            boolean captain    = "1".equals(row.getOrDefault("is_captain","0").trim());
            String  imageFile  = row.getOrDefault("image_file",  "").trim(); // ← 画像紐づけキー

            if (firstName.isEmpty() && lastName.isEmpty()) continue;

            Player.Position pos = PlayerDao.mapPosition(posStr);
            int overall   = calcOverall(age, shirtNum);
            int potential = Math.min(99, overall + new Random().nextInt(10));
            long salary   = (long)(overall * 8_000);
            long mv       = (long)(overall * overall * 10_000L);

            Player p = new Player(
                firstName, age, "犬", breed, pos,
                overall, potential, salary, mv, 3
            );
            p.setLastName    (lastName);
            p.setUniformName (uniformName.isEmpty() ? firstName.toUpperCase() : uniformName);
            p.setShirtNumber (shirtNum);
            p.setDominantFoot(foot);
            p.setCaptain     (captain);
            p.setImageFile   (imageFile);  // ← Excelの画像データ列をそのままセット
            p.setSpirit      (75 + new Random().nextInt(20));

            // 所属クラブを解決
            Club club = clubByName.getOrDefault(teamName,
                allClubs.stream().filter(c -> c.getName().equals(teamName)).findFirst().orElse(null));
            int clubId = resolveClubId(club);

            // 同じ背番号の選手がいればスキップ（冪等）
            try {
                boolean exists = !playerDao.findByClubId(clubId).isEmpty()
                    && playerDao.findByClubId(clubId).stream()
                         .anyMatch(ep -> ep.getShirtNumber() == shirtNum
                                     && ep.getUniformName().equals(p.getUniformName()));
                if (!exists) {
                    playerDao.insert(p, clubId);
                    System.out.printf("[Excel] 登録: #%d %s%s (%s) img=%s%n",
                        shirtNum, lastName, firstName, breed,
                        imageFile.isEmpty() ? "なし" : imageFile);
                }
                // メモリのスカッドにも追加
                if (club != null) {
                    boolean inSquad = club.getSquad().stream()
                        .anyMatch(s -> s.getShirtNumber() == shirtNum);
                    if (!inSquad) club.getSquad().add(p);
                }
            } catch (SQLException ex) {
                System.err.println("[Excel] 選手DB保存エラー: " + ex.getMessage());
            }
        }
    }

    private int resolveClubId(Club club) {
        if (club == null) return -1;
        if (club.getId() > 0) return club.getId();
        try { return clubDao.findIdByName(club.getName()); }
        catch (SQLException e) { return -1; }
    }

    // ─────────────────────────────────────────────────────────
    // PUBLIC STATIC: 画像ファイル解決ユーティリティ
    // ─────────────────────────────────────────────────────────

    /**
     * 画像ファイル名 → JavaFX Image 用 URL に変換。
     * Excelの「画像データ」列の値をそのまま渡してよい。
     *
     * 例: "Gemini_Generated_Image_xxx.png"
     *  → "file:resources/Gemini_Generated_Image_xxx.png"
     *
     * ファイルが存在しない場合は null を返す（呼び出し側でデフォルト画像を使う）。
     */
    public static String toImageURL(String imageFile) {
        if (imageFile == null || imageFile.isBlank()) return null;
        String file = imageFile.trim();

        // 既にURLならそのまま返す
        if (file.startsWith("file:") || file.startsWith("http://") || file.startsWith("https://")) {
            return file;
        }

        // 絶対/相対パスが直接指定されている場合
        File direct = new File(file);
        if (direct.exists()) {
            return direct.toURI().toString();
        }

        // プロジェクト実行時・Maven実行時の代表的な探索先
        String[] dirs = {
            "src/main/resources/images/players/",
            "src/main/resources/images/uniforms/",
            "src/main/resources/images/moves/",
            "src/main/resources/images/emblems/",
            "src/main/resources/",
            "target/classes/images/players/",
            "target/classes/images/uniforms/",
            "target/classes/images/moves/",
            "target/classes/images/emblems/",
            "target/classes/",
            "resources/images/players/",
            "resources/images/uniforms/",
            "resources/images/moves/",
            "resources/images/emblems/",
            "resources/",
            ""
        };
        for (String d : dirs) {
            File f = new File(d + file);
            if (f.exists()) return f.toURI().toString();
        }

        // クラスパス探索（jar実行も考慮）
        String normalized = file.startsWith("/") ? file.substring(1) : file;
        String[] cpCandidates = {
            "/" + normalized,
            "/images/players/" + normalized,
            "/images/uniforms/" + normalized,
            "/images/moves/" + normalized,
            "/images/emblems/" + normalized
        };
        for (String cp : cpCandidates) {
            URL u = ExcelLoaderService.class.getResource(cp);
            if (u != null) return u.toExternalForm();
        }
        return null;
    }

    /** 画像が存在するか確認 */
    public static boolean imageExists(String imageFile) {
        return toImageURL(imageFile) != null;
    }

    // ─────────────────────────────────────────────────────────
    // PRIVATE: ヘルパー
    // ─────────────────────────────────────────────────────────

    private List<Map<String,String>> readCSV(String path) throws IOException {
        List<Map<String,String>> result = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8))) {
            String headerLine = br.readLine();
            if (headerLine == null) return result;
            String[] headers = headerLine.split(",", -1);

            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] vals = line.split(",", -1);
                Map<String,String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    row.put(headers[i].trim(), i < vals.length ? vals[i].trim() : "");
                }
                result.add(row);
            }
        }
        return result;
    }

    private int parseInt(String s, int def) {
        if (s == null || s.isBlank()) return def;
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return def; }
    }

    /**
     * 年齢・背番号から総合値を推定。
     * 将来的にExcelに「能力値」列を追加すれば直接読むように拡張できる。
     */
    private int calcOverall(int age, int shirtNum) {
        int base = switch (shirtNum) {
            case 1 -> 82;
            default -> shirtNum <= 9  ? 78
                     : shirtNum <= 20 ? 74
                     : shirtNum <= 50 ? 71
                     : 68;
        };
        // 年齢補正（犬換算: 4-6歳がピーク）
        base += switch (age / 2) {
            case 0 -> -5;
            case 1 -> 0;
            case 2 -> 3;
            case 3 -> 1;
            default -> -6;
        };
        // 518番は特例：エース
        if (shirtNum == 518) base = 90;

        return Math.max(55, Math.min(90, base));
    }

    private String resolveDataCsvPath(String fileName) {
        String[] candidates = {
            "src/main/resources/" + DATA_DIR + fileName,
            "target/classes/" + DATA_DIR + fileName,
            RESOURCES + fileName,            // 旧配置互換
            RESOURCES + DATA_DIR + fileName, // 旧配置+data
            fileName
        };
        for (String c : candidates) {
            if (new File(c).exists()) return c;
        }
        return null;
    }
}

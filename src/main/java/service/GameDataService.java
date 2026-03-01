package service;

import db.*;
import model.WeeklyEvent;
import model.*;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * ゲームデータとDBを橋渡しするサービス
 *
 * 全DBアクセスはここ経由で行い、UI層からDAOを直接呼ばない設計。
 */
public class GameDataService {

    private final DatabaseManager db          = DatabaseManager.getInstance();
    private final ClubDao         clubDao     = new ClubDao();
    private final PlayerDao       playerDao   = new PlayerDao();
    private final MatchDao        matchDao    = new MatchDao();
    private final TransferDao     transferDao = new TransferDao();
    private final ImageDao        imageDao    = new ImageDao();
    private final WeeklyEventDao  weeklyEventDao = new WeeklyEventDao();

    // ── 初期化 ───────────────────────────────────────────────
    /**
     * DBが空なら初期データを投入、あればDBからロード。
     * ExcelLoaderService.loadFromCSV() の後に呼ぶ。
     */
    public void initIfEmpty(List<Club> allClubs) {
        try {
            // 画像マスターを登録（resources/の既知画像）
            imageDao.seedKnownImages();

            List<Club> existing = clubDao.findAll();
            if (existing.isEmpty()) {
                System.out.println("[DB] 初期データを投入します...");
                for (Club club : allClubs) {
                    int clubId = clubDao.upsert(club);
                    club.setId(clubId);
                    for (Player p : club.getSquad()) {
                        playerDao.insert(p, clubId);
                    }
                }
            } else {
                System.out.println("[DB] DBからロードします...");
                loadFromDB(allClubs);
            }
        } catch (SQLException e) {
            System.err.println("[DB] initIfEmpty エラー: " + e.getMessage());
        }
    }

    public void loadFromDB(List<Club> allClubs) throws SQLException {
        for (Club club : allClubs) {
            Club dbClub = clubDao.findByName(club.getName());
            if (dbClub == null) {
                int newId = clubDao.upsert(club);
                club.setId(newId);
                for (Player p : club.getSquad()) {
                    playerDao.insert(p, newId);
                }
            } else {
                club.setId(dbClub.getId());
                club.setBudget(dbClub.getBudget());
                // DBの成績を復元
                for (int i = 0; i < dbClub.getWins();   i++) club.recordResult(1, 0);
                for (int i = 0; i < dbClub.getDraws();  i++) club.recordResult(0, 0);
                for (int i = 0; i < dbClub.getLosses(); i++) club.recordResult(0, 1);

                // 選手をDBからロード
                List<Player> dbPlayers = playerDao.findByClubId(club.getId());
                if (!dbPlayers.isEmpty()) {
                    club.getSquad().clear();
                    club.getSquad().addAll(dbPlayers);
                }
            }
        }
    }

    // ── 保存 ─────────────────────────────────────────────────
    public void saveAllClubs(List<Club> allClubs) {
        allClubs.forEach(this::saveClub);
    }

    public void saveClub(Club club) {
        try {
            clubDao.upsert(club);
        } catch (SQLException e) {
            System.err.println("[DB] saveClub エラー: " + e.getMessage());
        }
    }

    public void savePlayer(Player player, Club club) {
        try {
            playerDao.upsert(player, club.getId());
        } catch (SQLException e) {
            System.err.println("[DB] savePlayer エラー: " + e.getMessage());
        }
    }

    public void saveSquad(Club club) {
        try {
            for (Player p : club.getSquad()) {
                playerDao.upsert(p, club.getId());
            }
        } catch (SQLException e) {
            System.err.println("[DB] saveSquad エラー: " + e.getMessage());
        }
    }

    /**
     * 試合結果を保存し、クラブ成績を更新する。
     * ラムダ外で呼ぶこと（effectively-final 制約がないメソッド）。
     */
    public void saveMatchResult(Club homeClub, Club awayClub, List<MatchEvent> events) {
        if (events == null || events.isEmpty()) return;

        MatchEvent last = events.get(events.size() - 1);
        final int  hg   = last.getHomeScore();
        final int  ag   = last.getAwayScore();

        try {
            // ── 1. クラブIDの確定 ─────────────────────────────
            int homeId = homeClub.getId();
            int awayId = awayClub.getId();
            if (homeId <= 0) homeId = clubDao.findIdByName(homeClub.getName());
            if (awayId <= 0) awayId = clubDao.findIdByName(awayClub.getName());

            // ── 2. 試合レコード保存 ───────────────────────────
            int matchId = matchDao.insertMatch(homeId, awayId, hg, ag);

            // ── 3. スタッツ保存 ───────────────────────────────
            int[]  shots   = last.getShots();
            int[]  shotsOn = last.getShotsOn();
            int[]  corners = last.getCorners();
            int[]  fouls   = last.getFouls();
            matchDao.insertStats(matchId,
                shots[0], shots[1], shotsOn[0], shotsOn[1],
                corners[0], corners[1], fouls[0], fouls[1],
                last.getHomePoss());

            // ── 4. クラブ成績の更新 ───────────────────────────
            homeClub.recordResult(hg, ag);
            awayClub.recordResult(ag, hg);
            clubDao.update(homeClub);
            clubDao.update(awayClub);

            // ── 5. リーグ順位表に記録 ─────────────────────────
            ui.MainApp.leagueManager.recordMatch(homeClub, awayClub, hg, ag);

            // ── 6. 試合で一緒だった選手の仲良し度UP ─────────
            //    playerClub の試合なら出場選手に仲良し度を付与
            java.util.List<model.Player> homeSquad = homeClub.getSquad();
            java.util.List<model.Player> awaySquad = awayClub.getSquad();
            java.util.List<String> fMsgs = ui.MainApp.friendshipManager.recordMatch(homeSquad);
            fMsgs.addAll(ui.MainApp.friendshipManager.recordMatch(awaySquad));
            if (!fMsgs.isEmpty()) {
                System.out.println("[Friendship] 試合後: " + String.join(" / ", fMsgs));
            }

        } catch (SQLException e) {
            System.err.println("[DB] saveMatchResult エラー: " + e.getMessage());
        }
    }

    /**
     * 移籍を記録する
     * player=対象選手, fromClub=売り元(null=フリー), toClub=行き先(null=フリー解放), fee=移籍金
     */
    public void recordTransfer(Player player, Club fromClub, Club toClub, long fee) {
        try {
            Integer fromId = (fromClub != null && fromClub.getId() > 0) ? fromClub.getId() : null;
            Integer toId   = (toClub   != null && toClub.getId()   > 0) ? toClub.getId()   : null;
            if (fromId == null && fromClub != null) {
                int fid = clubDao.findIdByName(fromClub.getName());
                fromId = fid > 0 ? fid : null;
            }
            if (toId == null && toClub != null) {
                int tid = clubDao.findIdByName(toClub.getName());
                toId = tid > 0 ? tid : null;
            }
            transferDao.insert(player.getId(), fromId, toId, fee);
        } catch (SQLException e) {
            System.err.println("[DB] recordTransfer エラー: " + e.getMessage());
        }
    }

    // ── 照会 ─────────────────────────────────────────────────
    public List<String> getRecentMatches(int limit) {
        try {
            return matchDao.findRecentResults(limit);
        } catch (SQLException e) {
            return Collections.emptyList();
        }
    }

    public void saveWeeklyEvent(WeeklyEvent event) {
        try {
            weeklyEventDao.insert(event);
        } catch (java.sql.SQLException e) {
            System.err.println("[DB] saveWeeklyEvent エラー: " + e.getMessage());
        }
    }

    public String getDBStats() {
        try {
            int clubs   = clubDao.findAll().size();
            int matches = matchDao.countMatches();
            return String.format("DB: %s\nクラブ: %d件  試合: %d件",
                DatabaseConfig.getDbType().toUpperCase(), clubs, matches);
        } catch (SQLException e) {
            return "DB情報取得エラー: " + e.getMessage();
        }
    }
}

package service;

import model.*;
import model.SeasonManager.WeekRecord;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ClubAI を SeasonManager・WeeklyEventService と繋ぐ統合サービス
 *
 * WeeklyPlanView.executeAdvance() の中で以下の順に呼ぶ：
 *
 *   1. weeklyEventService.generateAndApply(club, week, season, playedIds, teamWon)
 *   2. leagueAIService.processAllClubs(allClubs, playerClub, week)   ← このクラス
 *   3. season.advanceWeek(...)
 *
 * 移籍ウィンドウ週には自動で processTransferWindow() も呼ばれる。
 */
public class LeagueAIService {

    private final ClubAI clubAI = new ClubAI();

    // プレイヤークラブ名（AI処理対象から除外するため保持）
    private String playerClubName = "";

    public void setPlayerClubName(String name) {
        this.playerClubName = name;
    }

    // ════════════════════════════════════════════════════════════
    // 毎週の一括処理（WeeklyPlanView から呼ぶ）
    // ════════════════════════════════════════════════════════════

    /**
     * 全AIクラブの週次処理を実行する
     *
     * @param allClubs    全18クラブ
     * @param week        現在の週番号
     * @return            AIが行動したクラブのログ一覧
     */
    public List<String> processAllClubs(List<Club> allClubs, int week) {
        List<String> logs = new ArrayList<>();

        for (Club club : allClubs) {
            // プレイヤーのクラブはAI処理しない
            if (club.getName().equals(playerClubName)) continue;

            // 週次AI判断
            ClubAI.AIWeeklyDecision decision = clubAI.processWeek(club, week);
            if (decision.hasActions()) {
                logs.add(decision.toString());
                // コンソールログ（デバッグ用）
                decision.logs.forEach(l ->
                    System.out.println("  [AI] " + l));
            }

            // 移籍ウィンドウ中なら移籍処理も実行
            if (SeasonManager.TRANSFER_WINDOWS.contains(week)) {
                ClubAI.AITransferDecision transfer =
                    clubAI.processTransferWindow(club, allClubs, null);
                if (transfer.hasActivity()) {
                    logs.add(transfer.toString());
                }
            }
        }

        return logs;
    }

    /**
     * シーズン開幕時の全AIクラブ処理
     * SeasonManager.startNewSeason() の後に呼ぶ
     */
    public void processSeasonStart(List<Club> allClubs) {
        for (Club club : allClubs) {
            if (club.getName().equals(playerClubName)) continue;
            clubAI.processSeasonStart(club, allClubs);
        }
    }

    /**
     * 特定クラブの思想ラベルを返す（UI表示用）
     * 順位表・スカウト画面でクラブ紹介に使う
     */
    public String getPhilosophyLabel(Club club) {
        return clubAI.getPhilosophyLabel(club);
    }

    public ClubAI.Philosophy getPhilosophy(Club club) {
        return clubAI.getPhilosophy(club);
    }
}

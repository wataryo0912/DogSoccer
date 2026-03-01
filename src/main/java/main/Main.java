package main;

import model.*;
import service.*;
import java.util.List;

/**
 * Soccer Manager - エントリーポイント
 * ※ GUI実装前の動作確認用コンソール起動
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("=== Soccer Manager 起動 ===\n");

        // --- クラブ作成 ---
        Club myClub = new Club("FC Tokyo Stars", 1_000_000_000L, 5_000_000L);

        // --- 選手作成 ---
        Player p1 = new Player("山田 蓮", 23, "日本",
                Player.Position.FW, 78, 85, 300_000, 500_000_000L, 3);
        Player p2 = new Player("佐藤 駿", 20, "日本",
                Player.Position.MF, 72, 90, 200_000, 300_000_000L, 4);
        Player p3 = new Player("鈴木 大", 27, "日本",
                Player.Position.DF, 75, 76, 250_000, 400_000_000L, 2);
        Player p4 = new Player("中村 颯", 30, "日本",
                Player.Position.GK, 80, 80, 350_000, 600_000_000L, 1);

        myClub.signPlayer(p1, 0);
        myClub.signPlayer(p2, 0);
        myClub.signPlayer(p3, 0);
        myClub.signPlayer(p4, 0);

        // --- 財務確認 ---
        FinanceService financeService = new FinanceService();
        System.out.println(financeService.getFinanceSummary(myClub));
        System.out.println();

        // --- スカウト ---
        System.out.println("--- スカウト開始 ---");
        ScoutService scoutService = new ScoutService();
        List<Player> scouted = scoutService.scout(myClub.getBudget());
        scouted.forEach(System.out::println);
        System.out.println();

        // --- トレーニング ---
        System.out.println("--- トレーニング ---");
        TrainingService trainingService = new TrainingService();
        for (Player p : myClub.getSquad()) {
            System.out.println(trainingService.trainPlayer(p));
        }
        System.out.println();

        // --- 試合シミュレーション ---
        Club opponent = new Club("Osaka United", 800_000_000L, 4_000_000L);
        Player op1 = new Player("Rodriguez A.", 25, "ブラジル",
                Player.Position.FW, 80, 82, 400_000, 700_000_000L, 2);
        opponent.signPlayer(op1, 0);

        System.out.println("--- 試合シミュレーション ---");
        MatchSimulator simulator = new MatchSimulator();
        Match match = simulator.simulate(myClub, opponent);
        System.out.println("試合結果: " + match);
        System.out.println();

        System.out.println("--- クラブ状況 ---");
        System.out.println(myClub);
    }
}

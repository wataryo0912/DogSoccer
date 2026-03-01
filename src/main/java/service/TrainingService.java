package service;

import model.Player;
import java.util.List;
import java.util.Random;

/**
 * 選手育成サービスクラス
 */
public class TrainingService {

    private Random random = new Random();

    /**
     * 個人トレーニング
     * 若い選手ほど成長しやすい
     */
    public String trainPlayer(Player player) {
        int growth = 0;
        if (player.getOverall() >= player.getPotential()) {
            return player.getName() + " はすでに潜在能力の限界に達しています。";
        }

        int age = player.getAge();
        if (age <= 21) {
            growth = random.nextInt(3) + 1; // 1~3
        } else if (age <= 26) {
            growth = random.nextInt(2) + 1; // 1~2
        } else if (age <= 30) {
            growth = random.nextInt(2);     // 0~1
        } else {
            return player.getName() + " は年齢的に成長が見込めません。";
        }

        if (growth > 0) {
            int before = player.getOverall();
            player.grow(growth);
            return String.format("%s が成長しました！ OVR: %d → %d",
                    player.getName(), before, player.getOverall());
        } else {
            return player.getName() + " は今回成長しませんでした。";
        }
    }

    /**
     * チーム全体のトレーニング（若手のみ成長）。
     * 練習後に全選手間の仲良し度ポイントを加算する。
     * @return レベルアップしたペアのメッセージリスト
     */
    public List<String> trainAll(List<Player> squad) {
        squad.forEach(this::trainPlayer);
        // 練習を一緒に行うことで仲良し度UP
        return ui.MainApp.friendshipManager.recordTraining(squad);
    }
}

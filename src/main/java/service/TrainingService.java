package service;

import model.Player;
import java.util.List;
import java.util.Random;

/**
 * 選手育成サービスクラス
 */
public class TrainingService {

    public enum Menu {
        SHOOT   ("シュートトレーニング"),
        PASS    ("パストレーニング"),
        DRIBBLE ("ドリブルトレーニング"),
        RUN     ("ラントレーニング"),
        PHYSICAL("フィジカルトレーニング"),
        IQ      ("IQトレーニング"),
        REST    ("休息");

        private final String label;
        Menu(String label) { this.label = label; }
        public String label() { return label; }
    }

    private final Random random = new Random();
    private Menu selectedMenu = Menu.PHYSICAL;

    public Menu getSelectedMenu() {
        return selectedMenu;
    }

    public void setSelectedMenu(Menu menu) {
        this.selectedMenu = (menu != null) ? menu : Menu.PHYSICAL;
    }

    /**
     * 個人トレーニング
     * 若い選手ほど成長しやすい
     */
    public String trainPlayer(Player player) {
        if (selectedMenu == Menu.REST) {
            int beforeSpirit = player.getSpirit();
            int beforeStm = player.getStamina();
            player.setSpirit(beforeSpirit + 8 + random.nextInt(5));
            player.setStamina(beforeStm + 1 + random.nextInt(2));
            return String.format("%s は休息で回復しました（SPIRIT:%d→%d / STAMINA:%d→%d）",
                    player.getName(), beforeSpirit, player.getSpirit(), beforeStm, player.getStamina());
        }

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
            String focus = applyFocusBonus(player);
            return String.format("%s が成長しました！ OVR: %d → %d（%s）",
                    player.getName(), before, player.getOverall(), focus);
        } else {
            return player.getName() + " は今回成長しませんでした。";
        }
    }

    private String applyFocusBonus(Player player) {
        return switch (selectedMenu) {
            case SHOOT -> {
                int before = player.getShooting();
                player.setShooting(before + 1 + random.nextInt(2));
                yield "シュート " + before + "→" + player.getShooting();
            }
            case PASS -> {
                int before = player.getPassing();
                player.setPassing(before + 1 + random.nextInt(2));
                yield "パス " + before + "→" + player.getPassing();
            }
            case DRIBBLE -> {
                int beforeSpd = player.getSpeed();
                int beforePas = player.getPassing();
                player.setSpeed(beforeSpd + 1 + random.nextInt(2));
                if (random.nextDouble() < 0.6) player.setPassing(beforePas + 1);
                yield "スピード " + beforeSpd + "→" + player.getSpeed()
                    + " / パス " + beforePas + "→" + player.getPassing();
            }
            case RUN -> {
                int beforeStm = player.getStamina();
                int beforeSpd = player.getSpeed();
                player.setStamina(beforeStm + 1 + random.nextInt(2));
                if (random.nextDouble() < 0.7) player.setSpeed(beforeSpd + 1);
                yield "スタミナ " + beforeStm + "→" + player.getStamina()
                    + " / スピード " + beforeSpd + "→" + player.getSpeed();
            }
            case PHYSICAL -> {
                int beforeDef = player.getDefending();
                int beforeStm = player.getStamina();
                player.setDefending(beforeDef + 1 + random.nextInt(2));
                if (random.nextDouble() < 0.7) player.setStamina(beforeStm + 1);
                yield "守備 " + beforeDef + "→" + player.getDefending()
                    + " / スタミナ " + beforeStm + "→" + player.getStamina();
            }
            case IQ -> {
                int beforePas = player.getPassing();
                int beforeDef = player.getDefending();
                int beforeSpirit = player.getSpirit();
                player.setPassing(beforePas + 1);
                player.setDefending(beforeDef + 1);
                player.setSpirit(beforeSpirit + 1);
                yield "パス " + beforePas + "→" + player.getPassing()
                    + " / 守備 " + beforeDef + "→" + player.getDefending()
                    + " / SPIRIT " + beforeSpirit + "→" + player.getSpirit();
            }
            case REST -> "休息";
        };
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

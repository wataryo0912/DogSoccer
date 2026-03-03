package ui;

import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import model.*;
import java.util.HashMap;
import java.util.Map;

/**
 * シーズンスケジュール一覧表示パネル
 * WeeklyPlanViewの右側に埋め込まれる。
 */
public class ScheduleView extends VBox {

    public ScheduleView(SeasonManager season, String playerClubName) {
        setStyle("-fx-background-color:#0d0d22;-fx-padding:16;");
        setSpacing(8);

        Label title = new Label("📅  シーズンスケジュール");
        title.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:rgba(255,255,255,0.6);");
        getChildren().add(title);

        ScrollPane scroll = new ScrollPane();
        scroll.setStyle("-fx-background:#0d0d22;-fx-background-color:#0d0d22;");
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox list = new VBox(6);
        list.setStyle("-fx-padding:4 0;");

        int currentWeek = season.getCurrentWeek();
        int currentSeason = season.getCurrentSeason();
        Map<Integer, ScheduledMatch> scheduleByWeek = new HashMap<>();
        for (ScheduledMatch sm : season.getSchedule()) {
            scheduleByWeek.put(sm.getWeek(), sm);
        }

        for (int week = 1; week <= SeasonManager.TOTAL_WEEKS; week++) {
            ScheduledMatch sm = scheduleByWeek.get(week);
            boolean hasMatch = sm != null;
            boolean isCurrent = week == currentWeek;
            boolean isPast    = week < currentWeek;
            boolean isPlayer  = hasMatch && sm.isPlayerMatch(playerClubName);

            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(6, 10, 6, 10));

            String rowBg = isCurrent  ? "#1a2a3a"
                         : isPlayer   ? "#151525"
                         : "#0f0f1e";
            String border = isCurrent ? "#4a9ade" : "transparent";
            row.setStyle("-fx-background-color:" + rowBg + ";"
                + "-fx-background-radius:6;"
                + "-fx-border-color:" + border + ";-fx-border-radius:6;-fx-border-width:1;");

            // 週表示（yy年目:mm月:w週）
            int month = ((week - 1) / 4) + 1;
            int weekInMonth = ((week - 1) % 4) + 1;
            Label weekBadge = new Label(
                String.format("%d年目:%d月:%d週", currentSeason, month, weekInMonth));
            weekBadge.setStyle("-fx-font-size:10px;-fx-text-fill:rgba(255,255,255,0.45);"
                + "-fx-min-width:110;-fx-alignment:CENTER-LEFT;");

            // 状態アイコン
            String stateIcon = hasMatch
                ? (sm.isPlayed() ? (
                        sm.isPlayerMatch(playerClubName)
                            ? (sm.isPlayerHome(playerClubName)
                                ? (sm.getHomeGoals() > sm.getAwayGoals() ? "✅" : sm.getHomeGoals() == sm.getAwayGoals() ? "🟡" : "❌")
                                : (sm.getAwayGoals() > sm.getHomeGoals() ? "✅" : sm.getAwayGoals() == sm.getHomeGoals() ? "🟡" : "❌"))
                            : "─"
                    ) : isCurrent ? "▶" : "○")
                : (isCurrent ? "▶" : (isPast ? "·" : " "));
            Label stateLabel = new Label(stateIcon);
            stateLabel.setStyle("-fx-font-size:12px;-fx-min-width:18;");

            String text;
            String textColor;
            if (!hasMatch) {
                text = "空白";
                textColor = "rgba(255,255,255,0.28)";
            } else if (sm.isPlayerMatch(playerClubName)) {
                String opponent = sm.getOpponentName(playerClubName);
                String homeAway = sm.isPlayerHome(playerClubName) ? "HOME" : "AWAY";
                if (sm.isPlayed()) {
                    text = String.format("VS %s [%s]  (%s)", opponent, homeAway, sm.getScoreText());
                } else {
                    text = String.format("VS %s [%s]", opponent, homeAway);
                }
                textColor = MainApp.playerClub.getColor();
            } else {
                text = sm.getHomeClubName() + " vs " + sm.getAwayClubName();
                textColor = "rgba(255,255,255,0.6)";
            }

            Label matchLabel = new Label(text);
            matchLabel.setStyle("-fx-font-size:11px;-fx-text-fill:" + textColor + ";"
                + "-fx-font-weight:" + (isPlayer ? "bold" : "normal") + ";");

            row.getChildren().addAll(weekBadge, stateLabel, matchLabel);
            list.getChildren().add(row);
        }

        scroll.setContent(list);
        getChildren().add(scroll);
    }
}

package ui;

import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import model.*;

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

        for (ScheduledMatch sm : season.getSchedule()) {
            boolean isCurrent = sm.getWeek() == currentWeek;
            boolean isPast    = sm.getWeek() < currentWeek;
            boolean isPlayer  = sm.isPlayerMatch(playerClubName);

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

            // 週番号バッジ
            Label weekBadge = new Label("W" + sm.getWeek());
            weekBadge.setStyle("-fx-font-size:9px;-fx-text-fill:rgba(255,255,255,0.3);"
                + "-fx-min-width:28;-fx-alignment:CENTER-RIGHT;");

            // 状態アイコン
            String stateIcon = sm.isPlayed() ? (
                    sm.isPlayerMatch(playerClubName)
                        ? (sm.isPlayerHome(playerClubName)
                            ? (sm.getHomeGoals() > sm.getAwayGoals() ? "✅" : sm.getHomeGoals() == sm.getAwayGoals() ? "🟡" : "❌")
                            : (sm.getAwayGoals() > sm.getHomeGoals() ? "✅" : sm.getAwayGoals() == sm.getHomeGoals() ? "🟡" : "❌"))
                        : "─"
                ) : isCurrent ? "▶" : "○";
            Label stateLabel = new Label(stateIcon);
            stateLabel.setStyle("-fx-font-size:12px;-fx-min-width:18;");

            // チーム名
            String homeText = sm.getHomeClubName();
            String awayText = sm.getAwayClubName();
            String homeColor = sm.getHomeClubName().equals(playerClubName)
                ? MainApp.playerClub.getColor() : "rgba(255,255,255,0.6)";
            String awayColor = sm.getAwayClubName().equals(playerClubName)
                ? MainApp.playerClub.getColor() : "rgba(255,255,255,0.6)";

            Label homeLbl = new Label(homeText);
            homeLbl.setStyle("-fx-font-size:11px;-fx-text-fill:" + homeColor + ";"
                + "-fx-font-weight:" + (isPlayer ? "bold" : "normal") + ";");
            Label vsLbl = new Label(sm.isPlayed() ? sm.getScoreText() : "vs");
            vsLbl.setStyle("-fx-font-size:11px;-fx-text-fill:rgba(255,255,255,0.3);"
                + "-fx-min-width:30;-fx-alignment:CENTER;");
            Label awayLbl = new Label(awayText);
            awayLbl.setStyle("-fx-font-size:11px;-fx-text-fill:" + awayColor + ";"
                + "-fx-font-weight:" + (isPlayer ? "bold" : "normal") + ";");

            row.getChildren().addAll(weekBadge, stateLabel, homeLbl, vsLbl, awayLbl);
            list.getChildren().add(row);
        }

        if (season.getSchedule().isEmpty()) {
            Label empty = new Label("スケジュールが生成されていません");
            empty.setStyle("-fx-font-size:12px;-fx-text-fill:rgba(255,255,255,0.3);");
            list.getChildren().add(empty);
        }

        scroll.setContent(list);
        getChildren().add(scroll);
    }
}

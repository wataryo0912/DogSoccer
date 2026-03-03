package ui;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import model.League;
import model.Player;
import model.Player.PlayerRole;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 仕様書の「順位表画面」。
 */
public class StandingsView extends VBox {

    private final TableView<RowData> table = new TableView<>();
    private final TableView<PlayerRankRow> scorerTable = new TableView<>();
    private final TableView<PlayerRankRow> assistTable = new TableView<>();
    private final ComboBox<League.Tier> tierBox = new ComboBox<>();

    public StandingsView(MainApp app) {
        setStyle("-fx-background-color:#08081a;");
        setSpacing(0);
        getChildren().add(buildHeader(app));
        getChildren().add(buildTableArea());
        VBox.setVgrow(table, Priority.ALWAYS);

        tierBox.getItems().setAll(League.Tier.values());
        League current = MainApp.leagueManager.getLeagueOf(MainApp.playerClub);
        tierBox.setValue(current != null ? current.getTier() : League.Tier.TIER2);
        tierBox.setOnAction(e -> refreshRows());
        refreshRows();
    }

    private HBox buildHeader(MainApp app) {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(12, 16, 12, 16));
        bar.setStyle("-fx-background-color:#0d0d22;"
            + "-fx-border-color:rgba(255,255,255,0.08);-fx-border-width:0 0 1 0;");

        Label title = new Label("📊 順位表");
        title.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#ffffff;");

        Label tierLabel = new Label("リーグ:");
        tierLabel.setStyle("-fx-font-size:12px;-fx-text-fill:rgba(255,255,255,0.7);");
        tierBox.setStyle("-fx-pref-width:180;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button scheduleBtn = new Button("次週のスケジュール");
        scheduleBtn.setStyle("-fx-background-color:#1f2a46;-fx-text-fill:#ffffff;"
            + "-fx-font-size:11px;-fx-font-weight:bold;-fx-background-radius:8;"
            + "-fx-padding:5 12;-fx-cursor:hand;");
        scheduleBtn.setOnAction(e -> app.showScheduleView());

        Button backBtn = new Button("メインメニュー");
        backBtn.setStyle("-fx-background-color:#2d5a2f;-fx-text-fill:#ffffff;"
            + "-fx-font-size:11px;-fx-font-weight:bold;-fx-background-radius:8;"
            + "-fx-padding:5 12;-fx-cursor:hand;");
        backBtn.setOnAction(e -> app.showMainMenu());

        bar.getChildren().addAll(title, tierLabel, tierBox, spacer, scheduleBtn, backBtn);
        return bar;
    }

    private VBox buildTableArea() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(14));

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setStyle("-fx-background-color:#101026;-fx-text-fill:#ffffff;");

        TableColumn<RowData, Number> rankCol = new TableColumn<>("順位");
        rankCol.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().rank));

        TableColumn<RowData, String> clubCol = new TableColumn<>("クラブ");
        clubCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().clubName));

        TableColumn<RowData, Number> pCol = new TableColumn<>("試");
        pCol.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().played));

        TableColumn<RowData, Number> wCol = new TableColumn<>("勝");
        wCol.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().wins));

        TableColumn<RowData, Number> dCol = new TableColumn<>("分");
        dCol.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().draws));

        TableColumn<RowData, Number> lCol = new TableColumn<>("敗");
        lCol.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().losses));

        TableColumn<RowData, Number> gdCol = new TableColumn<>("得失");
        gdCol.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().gd));

        TableColumn<RowData, Number> ptCol = new TableColumn<>("勝点");
        ptCol.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().points));

        table.getColumns().setAll(rankCol, clubCol, pCol, wCol, dCol, lCol, gdCol, ptCol);
        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(RowData item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                    return;
                }
                if (item.isPlayerClub) {
                    setStyle("-fx-background-color:rgba(74,122,53,0.35);");
                } else {
                    setStyle("");
                }
            }
        });

        HBox rankingRow = new HBox(12,
            buildRankingPanel("⚽ 得点ランキング", scorerTable, "得点"),
            buildRankingPanel("🎯 アシストランキング", assistTable, "アシスト")
        );
        HBox.setHgrow(rankingRow.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(rankingRow.getChildren().get(1), Priority.ALWAYS);

        box.getChildren().addAll(table, rankingRow);
        VBox.setVgrow(table, Priority.ALWAYS);
        return box;
    }

    private VBox buildRankingPanel(String titleText, TableView<PlayerRankRow> rankingTable, String statLabel) {
        VBox panel = new VBox(6);
        panel.setPadding(new Insets(8));
        panel.setStyle("-fx-background-color:#0d0d22;-fx-background-radius:8;"
            + "-fx-border-color:rgba(255,255,255,0.08);-fx-border-width:1;-fx-border-radius:8;");
        VBox.setVgrow(panel, Priority.ALWAYS);

        Label title = new Label(titleText);
        title.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#ffffff;");

        rankingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        rankingTable.setStyle("-fx-background-color:#101026;-fx-text-fill:#ffffff;");
        rankingTable.setPrefHeight(220);

        TableColumn<PlayerRankRow, Number> rankCol = new TableColumn<>("#");
        rankCol.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().rank));

        TableColumn<PlayerRankRow, String> playerCol = new TableColumn<>("選手");
        playerCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().playerName));

        TableColumn<PlayerRankRow, String> clubCol = new TableColumn<>("クラブ");
        clubCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().clubName));

        TableColumn<PlayerRankRow, Number> valueCol = new TableColumn<>(statLabel);
        valueCol.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().value));

        rankingTable.getColumns().setAll(rankCol, playerCol, clubCol, valueCol);

        panel.getChildren().addAll(title, rankingTable);
        VBox.setVgrow(rankingTable, Priority.ALWAYS);
        return panel;
    }

    private void refreshRows() {
        League.Tier tier = tierBox.getValue();
        League league = MainApp.leagueManager.getLeague(tier);
        if (league == null) {
            table.setItems(FXCollections.observableArrayList());
            scorerTable.setItems(FXCollections.observableArrayList());
            assistTable.setItems(FXCollections.observableArrayList());
            return;
        }
        List<RowData> rows = new ArrayList<>();
        List<League.Standing> sorted = league.getSortedStandings();
        for (int i = 0; i < sorted.size(); i++) {
            League.Standing s = sorted.get(i);
            boolean mine = s.club.getName().equals(MainApp.playerClub.getName());
            rows.add(new RowData(
                i + 1,
                s.club.getName(),
                s.played,
                s.wins,
                s.draws,
                s.losses,
                s.getGD(),
                s.getPoints(),
                mine
            ));
        }
        table.setItems(FXCollections.observableArrayList(rows));
        refreshPlayerRankings(league);
    }

    private void refreshPlayerRankings(League league) {
        List<PlayerRankRow> scorers = buildPlayerRankingRows(league, false);
        List<PlayerRankRow> assisters = buildPlayerRankingRows(league, true);

        scorerTable.setItems(FXCollections.observableArrayList(scorers));
        assistTable.setItems(FXCollections.observableArrayList(assisters));
    }

    private List<PlayerRankRow> buildPlayerRankingRows(League league, boolean assistMode) {
        List<PlayerRankRow> rows = new ArrayList<>();
        for (League.Standing s : league.getSortedStandings()) {
            List<Player> players = s.club.getSquad().stream()
                .filter(p -> p.getRole() != PlayerRole.RETIRED)
                .toList();
            for (Player p : players) {
                int value = assistMode ? p.getAssists() : p.getGoals();
                if (value <= 0) continue;
                rows.add(new PlayerRankRow(
                    0,
                    p.getFullName(),
                    s.club.getName(),
                    value
                ));
            }
        }

        List<PlayerRankRow> sorted = rows.stream()
            .sorted(Comparator
                .comparingInt((PlayerRankRow r) -> r.value).reversed()
                .thenComparing(r -> r.playerName)
                .thenComparing(r -> r.clubName))
            .limit(10)
            .toList();

        List<PlayerRankRow> ranked = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            PlayerRankRow r = sorted.get(i);
            ranked.add(new PlayerRankRow(i + 1, r.playerName, r.clubName, r.value));
        }
        return ranked;
    }

    private static final class RowData {
        final int rank;
        final String clubName;
        final int played;
        final int wins;
        final int draws;
        final int losses;
        final int gd;
        final int points;
        final boolean isPlayerClub;

        private RowData(int rank, String clubName, int played, int wins, int draws,
                        int losses, int gd, int points, boolean isPlayerClub) {
            this.rank = rank;
            this.clubName = clubName;
            this.played = played;
            this.wins = wins;
            this.draws = draws;
            this.losses = losses;
            this.gd = gd;
            this.points = points;
            this.isPlayerClub = isPlayerClub;
        }
    }

    private static final class PlayerRankRow {
        final int rank;
        final String playerName;
        final String clubName;
        final int value;

        private PlayerRankRow(int rank, String playerName, String clubName, int value) {
            this.rank = rank;
            this.playerName = playerName;
            this.clubName = clubName;
            this.value = value;
        }
    }

}

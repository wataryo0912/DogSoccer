package ui;

import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import model.Club;

/**
 * 財務管理画面
 */
public class FinanceView extends VBox {

    private MainApp app;

    public FinanceView(MainApp app) {
        this.app = app;
        setStyle("-fx-background-color: #1a1a2e; -fx-padding: 20;");
        setSpacing(20);

        Label title = new Label("💰 財務管理");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: white;");

        getChildren().addAll(title, buildFinanceCards(), buildSalarySection(), buildActions());
    }

    private HBox buildFinanceCards() {
        Club club = MainApp.playerClub;
        HBox box = new HBox(15);

        box.getChildren().addAll(
            card("💰 移籍予算", "¥" + String.format("%,d", club.getBudget()), "#00ff88"),
            card("📅 週給合計", "¥" + String.format("%,d", club.getTotalWeeklySalary()), "#ffd700"),
            card("📊 週給上限", "¥" + String.format("%,d", club.getWeeklySalaryBudget()), "#a8dadc"),
            card("📈 週給余裕", "¥" + String.format("%,d",
                    club.getWeeklySalaryBudget() - club.getTotalWeeklySalary()), "#e94560")
        );
        return box;
    }

    private VBox card(String title, String value, String color) {
        VBox box = new VBox(8);
        box.setStyle("-fx-background-color: #16213e; -fx-background-radius: 10; -fx-padding: 18;");
        box.setPrefWidth(200);
        box.setAlignment(Pos.CENTER);

        Label t = new Label(title);
        t.setStyle("-fx-text-fill: #888; -fx-font-size: 13px;");

        Label v = new Label(value);
        v.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 18px; -fx-font-weight: bold;");

        box.getChildren().addAll(t, v);
        return box;
    }

    private VBox buildSalarySection() {
        Club club = MainApp.playerClub;
        VBox box = new VBox(10);
        box.setStyle("-fx-background-color: #16213e; -fx-background-radius: 10; -fx-padding: 18;");

        Label lbl = new Label("選手別週給");
        lbl.setStyle("-fx-text-fill: #a8dadc; -fx-font-size: 15px; -fx-font-weight: bold;");

        // 週給の棒グラフ風表示
        VBox bars = new VBox(6);
        long maxSalary = club.getSquad().stream().mapToLong(p -> p.getSalary()).max().orElse(1);

        for (var p : club.getSquad()) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);

            Label name = new Label(String.format("%-12s", p.getName()));
            name.setStyle("-fx-text-fill: white; -fx-font-size: 12px;");
            name.setPrefWidth(130);

            double ratio = (double) p.getSalary() / maxSalary;
            ProgressBar bar = new ProgressBar(ratio);
            bar.setPrefWidth(250);
            bar.setStyle("-fx-accent: #0f3460;");

            Label sal = new Label("¥" + String.format("%,d", p.getSalary()));
            sal.setStyle("-fx-text-fill: #ffd700; -fx-font-size: 12px;");

            row.getChildren().addAll(name, bar, sal);
            bars.getChildren().add(row);
        }

        // 年俸合計
        Label yearlyTotal = new Label("年俸総額: ¥" + String.format("%,d", club.getTotalWeeklySalary() * 52));
        yearlyTotal.setStyle("-fx-text-fill: #e94560; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 10 0 0 0;");

        box.getChildren().addAll(lbl, bars, yearlyTotal);
        return box;
    }

    private HBox buildActions() {
        HBox box = new HBox(12);

        Button payBtn = new Button("💸 週給支払い処理");
        payBtn.setStyle(
            "-fx-background-color: #e94560; -fx-text-fill: white; -fx-font-weight: bold;" +
            "-fx-padding: 8 18; -fx-background-radius: 6; -fx-cursor: hand; -fx-font-size: 13px;"
        );
        payBtn.setOnAction(e -> {
            long before = MainApp.playerClub.getBudget();
            MainApp.financeService.processWeeklySalaries(MainApp.playerClub);
            long paid = before - MainApp.playerClub.getBudget();

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("週給支払い完了");
            alert.setHeaderText(null);
            alert.setContentText(String.format("¥%,d を支払いました。\n残り予算: ¥%,d", paid, MainApp.playerClub.getBudget()));
            alert.showAndWait();

            app.updateHeaderLabels();
            getChildren().clear();
            setSpacing(20);
            Label title = new Label("💰 財務管理");
            title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: white;");
            getChildren().addAll(title, buildFinanceCards(), buildSalarySection(), buildActions());
        });

        box.getChildren().add(payBtn);
        return box;
    }
}

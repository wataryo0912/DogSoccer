package ui;

import javafx.collections.FXCollections;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import model.Club;
import model.Player;
import model.Player.PlayerRole;
import service.ExcelLoaderService;

import java.util.ArrayList;
import java.util.List;

/**
 * スカウト・移籍・下部組織画面
 *
 * タブ構成:
 *   📋 スカウト    — 外部選手スカウト & 自クラブ売却
 *   🏟️ 下部組織   — アカデミー選手一覧 & 昇格申請（翌週反映）
 *   👥 役割管理   — スカッド区分変更 & キャプテン変更
 */
public class ScoutView extends VBox {

    private Runnable weeklyCallback  = null;
    private boolean  forceShowFinish = false;

    // スカウトタブ
    private ListView<String> scoutList;
    private List<Player>     scoutedPlayers = new ArrayList<>();
    private Label            scoutStatusLabel;

    // 下部組織タブ
    private VBox academyListBox;

    public ScoutView(MainApp app) {
        setStyle("-fx-background-color:#08081a;");
        setSpacing(0);
        getChildren().addAll(buildHeader(), buildTabPane(app));
        VBox.setVgrow(getChildren().get(1), Priority.ALWAYS);
    }

    // ── コールバック ──────────────────────────────────────
    public void setWeeklyCallback(Runnable cb) { this.weeklyCallback = cb; }
    public void setWeeklyCallback(Runnable cb, boolean forceShow) {
        this.weeklyCallback  = cb;
        this.forceShowFinish = forceShow;
        getChildren().set(0, buildHeader());
    }
    private void finishTransferWindow() {
        if (weeklyCallback != null) weeklyCallback.run();
    }

    // ── ヘッダー ─────────────────────────────────────────
    private HBox buildHeader() {
        HBox bar = new HBox(16);
        bar.setStyle("""
            -fx-background-color:#0d0d22;
            -fx-padding:16 24;
            -fx-border-color:rgba(255,255,255,0.06);
            -fx-border-width:0 0 1 0;
        """);
        bar.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("🔄  移籍・スカウト・下部組織");
        title.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:#fff;");

        boolean winOpen = MainApp.season.isTransferWindowOpen();
        Label winBadge = new Label(winOpen ? "✅ 移籍ウィンドウ 開放中" : "⚠️ ウィンドウ外（閲覧のみ）");
        winBadge.setStyle(String.format(
            "-fx-background-color:%s22;-fx-text-fill:%s;"
            + "-fx-border-color:%s;-fx-border-width:1;"
            + "-fx-padding:4 10;-fx-background-radius:6;-fx-border-radius:6;-fx-font-size:11px;",
            winOpen?"#4a7a35":"#8b4513",
            winOpen?"#4a7a35":"#ff8c00",
            winOpen?"#4a7a35":"#8b4513"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        boolean show = weeklyCallback != null || forceShowFinish;
        Button finBtn = new Button("✅  移籍ウィンドウを終えて次の週へ →");
        finBtn.setStyle("""
            -fx-background-color:#4a7a35;-fx-text-fill:white;
            -fx-font-size:12px;-fx-font-weight:bold;
            -fx-padding:9 18;-fx-background-radius:7;-fx-cursor:hand;
        """);
        finBtn.setOnAction(e -> finishTransferWindow());
        finBtn.setVisible(show); finBtn.setManaged(show);
        bar.getChildren().addAll(title, winBadge, spacer, finBtn);
        return bar;
    }

    // ── タブペイン ───────────────────────────────────────
    private TabPane buildTabPane(MainApp app) {
        TabPane tp = new TabPane();
        tp.setStyle("-fx-background-color:#08081a;");
        tp.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tp, Priority.ALWAYS);
        tp.getTabs().addAll(
            new Tab("📋  スカウト",   buildScoutTab()),
            new Tab("🏟️  下部組織",  buildAcademyTab()),
            new Tab("👥  役割管理",   buildRoleTab())
        );
        return tp;
    }

    // ═══════════════════════════════════════════════
    // タブ① スカウト
    // ═══════════════════════════════════════════════
    private VBox buildScoutTab() {
        VBox box = new VBox(0);
        box.setStyle("-fx-background-color:#08081a;");
        VBox list = buildScoutListBox();
        VBox.setVgrow(list, Priority.ALWAYS);
        box.getChildren().addAll(buildScoutControls(), list, buildSignButton());
        return box;
    }

    private HBox buildScoutControls() {
        HBox box = new HBox(12);
        box.setStyle("-fx-padding:16 24 8 24;");
        box.setAlignment(Pos.CENTER_LEFT);

        scoutStatusLabel = new Label("スカウトボタンで選手を探します");
        scoutStatusLabel.setStyle("-fx-font-size:12px;-fx-text-fill:rgba(255,255,255,0.4);");

        Button scoutBtn = new Button("🔍 スカウト実行");
        scoutBtn.setStyle("""
            -fx-background-color:#3a7bd5;-fx-text-fill:white;
            -fx-font-size:13px;-fx-font-weight:bold;
            -fx-padding:9 18;-fx-background-radius:7;-fx-cursor:hand;
        """);
        scoutBtn.setOnAction(e -> runScout());

        Label sellLbl = new Label("   自クラブ選手を売却:");
        sellLbl.setStyle("-fx-font-size:12px;-fx-text-fill:rgba(255,255,255,0.4);");
        ComboBox<String> sellCombo = new ComboBox<>();
        List<Player> sellable = MainApp.playerClub.getSquad().stream()
            .filter(p -> p.getRole()==PlayerRole.REGISTERED || p.getRole()==PlayerRole.BENCH)
            .toList();
        sellable.forEach(p -> sellCombo.getItems().add(
            p.getUniformLabel()+" "+p.getFullName()+"  (OVR:"+p.getOverall()+")"));
        sellCombo.setStyle("-fx-background-color:#1a1a2e;-fx-text-fill:#fff;"
            +"-fx-font-size:12px;-fx-pref-width:220;");
        Button sellBtn = new Button("売却 ¥");
        sellBtn.setStyle("""
            -fx-background-color:#8b4513;-fx-text-fill:white;
            -fx-font-size:12px;-fx-padding:8 14;-fx-background-radius:6;-fx-cursor:hand;
        """);
        sellBtn.setOnAction(e -> {
            int idx = sellCombo.getSelectionModel().getSelectedIndex();
            if (idx<0||idx>=sellable.size()) return;
            Player p = sellable.get(idx);
            long fee = p.getMarketValue();
            new Alert(Alert.AlertType.CONFIRMATION,
                String.format("%s を ¥%,d で売却しますか？",p.getFullName(),fee),
                ButtonType.YES,ButtonType.NO)
            .showAndWait().ifPresent(bt -> {
                if (bt==ButtonType.YES) {
                    MainApp.playerClub.sellPlayer(p,fee);
                    MainApp.gameDataService.recordTransfer(p,MainApp.playerClub,(Club)null,fee);
                    MainApp.gameDataService.saveClub(MainApp.playerClub);
                    MainApp.app.updateHeaderLabels();
                    sellCombo.getItems().remove(idx);
                    scoutStatusLabel.setText("💰 "+p.getFullName()
                        +" 売却 (+¥"+String.format("%,d",fee)+")");
                }
            });
        });
        box.getChildren().addAll(scoutBtn,scoutStatusLabel,
            new Separator(Orientation.VERTICAL),sellLbl,sellCombo,sellBtn);
        return box;
    }

    private VBox buildScoutListBox() {
        VBox box = new VBox(6);
        box.setStyle("-fx-padding:0 24 8 24;");
        VBox.setVgrow(box, Priority.ALWAYS);
        Label lbl = new Label("スカウト対象選手");
        lbl.setStyle("-fx-font-size:11px;-fx-text-fill:rgba(255,255,255,0.3);");
        scoutList = new ListView<>();
        scoutList.setStyle("-fx-background-color:#111128;-fx-text-fill:white;-fx-font-size:12px;");
        VBox.setVgrow(scoutList, Priority.ALWAYS);
        box.getChildren().addAll(lbl, scoutList);
        return box;
    }

    private HBox buildSignButton() {
        HBox box = new HBox(12);
        box.setStyle("-fx-padding:0 24 16 24;");
        box.setAlignment(Pos.CENTER_LEFT);
        Button btn = new Button("✅ 獲得契約");
        btn.setStyle("""
            -fx-background-color:#4a7a35;-fx-text-fill:white;
            -fx-font-size:13px;-fx-font-weight:bold;
            -fx-padding:10 24;-fx-background-radius:8;-fx-cursor:hand;
        """);
        btn.setOnAction(e -> signPlayer());
        Label hint = new Label("選手を選択して「獲得契約」をクリック");
        hint.setStyle("-fx-font-size:11px;-fx-text-fill:rgba(255,255,255,0.25);");
        box.getChildren().addAll(btn,hint);
        return box;
    }

    private void runScout() {
        scoutedPlayers = MainApp.scoutService.scout(MainApp.playerClub.getBudget());
        var items = FXCollections.<String>observableArrayList();
        for (Player p : scoutedPlayers) {
            items.add(String.format("🐾[%s] %s(%s)  OVR:%d POT:%d  %d歳  %s足  週給:¥%,d  市場価値:¥%,d",
                p.getPosition(),p.getName(),p.getBreed(),
                p.getOverall(),p.getPotential(),p.getAge(),
                p.getDominantFoot(),p.getSalary(),p.getMarketValue()));
        }
        scoutList.setItems(items);
        scoutStatusLabel.setText(scoutedPlayers.size()+"匹の選手を発見！");
    }

    private void signPlayer() {
        int idx = scoutList.getSelectionModel().getSelectedIndex();
        if (idx<0||idx>=scoutedPlayers.size()) return;
        Player p = scoutedPlayers.get(idx);
        long fee = p.getMarketValue();
        if (MainApp.playerClub.getBudget()<fee) {
            scoutStatusLabel.setText("⚠️ 予算不足（必要: ¥"+String.format("%,d",fee)+"）");
            return;
        }
        if (MainApp.playerClub.signPlayer(p,fee)) {
            MainApp.gameDataService.savePlayer(p,MainApp.playerClub);
            MainApp.gameDataService.recordTransfer(p,(Club)null,MainApp.playerClub,fee);
            MainApp.gameDataService.saveClub(MainApp.playerClub);
            MainApp.app.updateHeaderLabels();
            scoutedPlayers.remove(idx);
            scoutList.getItems().remove(idx);
            scoutStatusLabel.setText("🎉 "+p.getFullName()+"("+p.getBreed()+") 獲得！");
        } else {
            scoutStatusLabel.setText("⚠️ 週給予算オーバーです");
        }
    }

    // ═══════════════════════════════════════════════
    // タブ② 下部組織（昇格申請）
    // ═══════════════════════════════════════════════
    private ScrollPane buildAcademyTab() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(20,24,24,24));
        root.setStyle("-fx-background-color:#08081a;");

        Label desc = new Label(
            "下部組織の選手を「昇格申請」すると、来週からスカッド登録メンバーに加わります。\n"
            + "申請後もキャンセルできます。");
        desc.setStyle("-fx-font-size:12px;-fx-text-fill:#AAAACC;");
        desc.setWrapText(true);

        root.getChildren().addAll(desc, buildPendingBanner());

        Label listTitle = new Label("🏟️  下部組織選手一覧");
        listTitle.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#FFD700;"
            + "-fx-padding:4 0 0 0;");
        root.getChildren().add(listTitle);

        academyListBox = new VBox(8);
        refreshAcademyList();
        root.getChildren().add(academyListBox);

        ScrollPane sc = new ScrollPane(root);
        sc.setFitToWidth(true);
        sc.setStyle("-fx-background-color:#08081a;-fx-background:#08081a;");
        return sc;
    }

    private VBox buildPendingBanner() {
        List<Player> pending = MainApp.playerClub.getPendingPromotions();
        VBox box = new VBox(6);
        box.setStyle("-fx-background-color:#1A3A00;-fx-background-radius:8;-fx-padding:12 16;");

        Label t = new Label("⏳  昇格申請中 (" + pending.size() + "名) → 来週から登録");
        t.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#88FF44;");
        box.getChildren().add(t);

        if (pending.isEmpty()) {
            Label n = new Label("なし");
            n.setStyle("-fx-font-size:11px;-fx-text-fill:#AAAACC;");
            box.getChildren().add(n);
        } else {
            for (Player p : pending) {
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                Label nm = new Label("• "+p.getFullName()
                    +"  ["+p.getPosition()+"]  OVR:"+p.getOverall());
                nm.setStyle("-fx-font-size:11px;-fx-text-fill:#FFFFFF;");
                Button cb = new Button("取消");
                cb.setStyle("-fx-background-color:#4A0000;-fx-text-fill:#FF6666;"
                    +"-fx-font-size:10px;-fx-padding:2 8;-fx-background-radius:4;-fx-cursor:hand;");
                cb.setOnAction(e -> {
                    String msg = MainApp.academyService.cancelPromotion(MainApp.playerClub,p);
                    MainApp.gameDataService.saveSquad(MainApp.playerClub);
                    refreshAcademyList();
                    showInfo("昇格キャンセル",msg);
                });
                row.getChildren().addAll(nm,cb);
                box.getChildren().add(row);
            }
        }
        return box;
    }

    private void refreshAcademyList() {
        if (academyListBox==null) return;
        academyListBox.getChildren().clear();
        List<Player> list = MainApp.playerClub.getAcademyPlayers();
        if (list.isEmpty()) {
            Label n = new Label("下部組織に選手がいません");
            n.setStyle("-fx-font-size:12px;-fx-text-fill:#AAAACC;");
            academyListBox.getChildren().add(n);
            return;
        }
        list.forEach(p -> academyListBox.getChildren().add(buildAcademyRow(p)));
    }

    private HBox buildAcademyRow(Player p) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10,14,10,14));
        row.setStyle("-fx-background-color:"+(p.isPendingPromotion()?"#1A2A00":"#16213E")
            +";-fx-background-radius:8;");

        Label pos = new Label(p.getPosition().toString());
        pos.setMinWidth(36);
        pos.setStyle("-fx-background-color:#333;-fx-text-fill:#aaa;"
            +"-fx-font-size:10px;-fx-padding:2 6;-fx-background-radius:3;");

        Label ovr = new Label("OVR "+p.getOverall());
        ovr.setStyle("-fx-font-size:11px;-fx-font-weight:bold;-fx-text-fill:#44AAFF;");
        ovr.setMinWidth(52);

        Label pot = new Label("POT "+p.getPotential());
        pot.setStyle("-fx-font-size:11px;-fx-text-fill:#88FF44;");
        pot.setMinWidth(52);

        VBox nameBox = new VBox(2);
        Label name = new Label(p.getFullName());
        name.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#FFFFFF;");
        Label br = new Label(p.getBreed()+"  "+p.getAge()+"歳");
        br.setStyle("-fx-font-size:10px;-fx-text-fill:#AAAACC;");
        nameBox.getChildren().addAll(name,br);

        Label stats = new Label(String.format("SPD:%d SHT:%d PAS:%d DEF:%d STA:%d",
            p.getSpeed(),p.getShooting(),p.getPassing(),p.getDefending(),p.getStamina()));
        stats.setStyle("-fx-font-size:10px;-fx-text-fill:#666688;");

        Region sp = new Region(); HBox.setHgrow(sp,Priority.ALWAYS);

        if (p.isPendingPromotion()) {
            Label badge = new Label("⏳ 昇格申請中");
            badge.setStyle("-fx-background-color:#2A4A00;-fx-text-fill:#88FF44;"
                +"-fx-font-size:11px;-fx-font-weight:bold;"
                +"-fx-padding:6 14;-fx-background-radius:6;");
            row.getChildren().addAll(pos,ovr,pot,nameBox,stats,sp,badge);
        } else {
            Button btn = new Button("⬆️ 昇格申請");
            String base = "-fx-background-color:#1A3A5A;-fx-text-fill:#44AAFF;"
                +"-fx-font-size:11px;-fx-font-weight:bold;"
                +"-fx-padding:6 14;-fx-background-radius:6;-fx-cursor:hand;";
            String hover = "-fx-background-color:#2A4A6A;-fx-text-fill:#88CCFF;"
                +"-fx-font-size:11px;-fx-font-weight:bold;"
                +"-fx-padding:6 14;-fx-background-radius:6;-fx-cursor:hand;";
            btn.setStyle(base);
            btn.setOnMouseEntered(e->btn.setStyle(hover));
            btn.setOnMouseExited (e->btn.setStyle(base));
            btn.setOnAction(e -> {
                Alert c = new Alert(Alert.AlertType.CONFIRMATION);
                c.setTitle("昇格申請の確認");
                c.setHeaderText("⬆️  "+p.getFullName()+" を来週スカッドに登録しますか？");
                c.setContentText("OVR:"+p.getOverall()+"  POT:"+p.getPotential()
                    +"
週給: ¥"+String.format("%,d",p.getSalary())
                    +"

※ 翌週の週進行後にスカッドへ昇格します。");
                c.getButtonTypes().setAll(
                    new ButtonType("申請する",ButtonBar.ButtonData.OK_DONE),
                    new ButtonType("キャンセル",ButtonBar.ButtonData.CANCEL_CLOSE));
                c.showAndWait().ifPresent(bt -> {
                    if (bt.getButtonData()==ButtonBar.ButtonData.OK_DONE) {
                        String msg = MainApp.academyService.requestPromotion(MainApp.playerClub,p);
                        MainApp.gameDataService.saveSquad(MainApp.playerClub);
                        refreshAcademyList();
                        showInfo("昇格申請",msg);
                    }
                });
            });
            row.getChildren().addAll(pos,ovr,pot,nameBox,stats,sp,btn);
        }
        return row;
    }

    // ═══════════════════════════════════════════════
    // タブ③ 役割管理（キャプテン変更 + スカッド区分）
    // ═══════════════════════════════════════════════
    private ScrollPane buildRoleTab() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(20,24,24,24));
        root.setStyle("-fx-background-color:#08081a;");
        root.getChildren().addAll(buildCaptainSection(), buildRoleChangeSection());
        ScrollPane sc = new ScrollPane(root);
        sc.setFitToWidth(true);
        sc.setStyle("-fx-background-color:#08081a;-fx-background:#08081a;");
        return sc;
    }

    private VBox buildCaptainSection() {
        VBox card = card("©️  キャプテン変更（1クラブ1人制約）");

        Player cur = MainApp.playerClub.getCaptain();
        Label curLbl = new Label("現在のキャプテン: "+(cur!=null?cur.getFullName():"なし"));
        curLbl.setStyle("-fx-font-size:12px;-fx-text-fill:#FFD700;-fx-padding:0 0 6 0;");

        List<Player> reg = MainApp.playerClub.getRegistered().stream()
            .sorted((a,b)->a.getShirtNumber()-b.getShirtNumber()).toList();
        ComboBox<String> combo = new ComboBox<>();
        reg.forEach(p->combo.getItems().add(
            "#"+p.getShirtNumber()+"  "+p.getFullName()+"  ["+p.getPosition()+"]"));
        combo.setStyle("-fx-background-color:#16213E;-fx-text-fill:#FFFFFF;"
            +"-fx-font-size:12px;-fx-pref-width:300;");
        if (cur!=null) {
            for (int i=0;i<reg.size();i++) {
                if (reg.get(i).getId()==cur.getId()) { combo.getSelectionModel().select(i); break; }
            }
        }

        Button setBtn = new Button("©️ キャプテンに任命");
        setBtn.setStyle("""
            -fx-background-color:#3A2A00;-fx-text-fill:#FFD700;
            -fx-font-size:12px;-fx-font-weight:bold;
            -fx-padding:8 18;-fx-background-radius:6;-fx-cursor:hand;
        """);
        Label result = new Label(""); result.setStyle("-fx-font-size:11px;-fx-text-fill:#88FF44;");
        setBtn.setOnAction(e -> {
            int idx = combo.getSelectionModel().getSelectedIndex();
            if (idx<0||idx>=reg.size()) return;
            String msg = MainApp.academyService.changeCaptain(MainApp.playerClub,reg.get(idx));
            MainApp.gameDataService.saveSquad(MainApp.playerClub);
            result.setText(msg);
            curLbl.setText("現在のキャプテン: "+reg.get(idx).getFullName());
        });
        card.getChildren().addAll(curLbl,combo,setBtn,result);
        return card;
    }

    private VBox buildRoleChangeSection() {
        VBox card = card("👥  スカッド区分変更");
        Label desc = new Label(
            "登録 → 試合出場可  ／  ベンチ → 練習のみ  ／  スカッド外 → 一時離脱  ／  下部組織 → 育成");
        desc.setStyle("-fx-font-size:11px;-fx-text-fill:#AAAACC;-fx-padding:0 0 8 0;");
        desc.setWrapText(true);
        card.getChildren().add(desc);

        for (PlayerRole role : PlayerRole.values()) {
            List<Player> group = MainApp.playerClub.getSquad().stream()
                .filter(p->p.getRole()==role).toList();
            if (group.isEmpty()) continue;
            String rc = switch(role){
                case REGISTERED->"#44FF88"; case BENCH->"#FFD700";
                case INACTIVE->"#FF6666"; case ACADEMY->"#44AAFF";};
            Label rh = new Label(role.label+"  ("+group.size()+"名)");
            rh.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:"+rc
                +";-fx-padding:8 0 4 0;");
            card.getChildren().add(rh);
            group.forEach(p->card.getChildren().add(buildRoleRow(p,role)));
        }
        return card;
    }

    private HBox buildRoleRow(Player p, PlayerRole cur) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(5,10,5,10));
        row.setStyle("-fx-background-color:#111128;-fx-background-radius:5;");

        Label pos = new Label("["+p.getPosition()+"]");
        pos.setMinWidth(36);
        pos.setStyle("-fx-font-size:10px;-fx-text-fill:#888888;");
        Label nm = new Label(p.getFullName());
        nm.setMinWidth(120);
        nm.setStyle("-fx-font-size:12px;-fx-text-fill:#FFFFFF;"
            +(p.isCaptain()?"-fx-font-weight:bold;":""));
        Label capt = p.isCaptain()?new Label("©"):new Label("");
        capt.setStyle("-fx-font-size:11px;-fx-text-fill:#FFD700;");
        Label ovr = new Label("OVR:"+p.getOverall());
        ovr.setStyle("-fx-font-size:10px;-fx-text-fill:#AAAACC;");
        Region sp = new Region(); HBox.setHgrow(sp,Priority.ALWAYS);

        HBox btns = new HBox(5);
        for (PlayerRole t : PlayerRole.values()) {
            if (t==cur) continue;
            if (p.isCaptain()&&(t==PlayerRole.ACADEMY||t==PlayerRole.INACTIVE)) continue;
            String bg = switch(t){case REGISTERED->"#1A3A1A";case BENCH->"#3A3A00";
                case INACTIVE->"#3A1A1A";case ACADEMY->"#1A2A3A";};
            String fg = switch(t){case REGISTERED->"#44FF88";case BENCH->"#FFD700";
                case INACTIVE->"#FF6666";case ACADEMY->"#44AAFF";};
            Button b = new Button(t.label+"へ");
            b.setStyle("-fx-background-color:"+bg+";-fx-text-fill:"+fg
                +";-fx-font-size:10px;-fx-padding:3 7;-fx-background-radius:4;-fx-cursor:hand;");
            b.setOnAction(e->{
                String msg = MainApp.academyService.changeRole(p,t);
                MainApp.gameDataService.saveSquad(MainApp.playerClub);
                showInfo("役割変更",msg);
                javafx.application.Platform.runLater(()->
                    MainApp.app.setCenterView(new ScoutView(MainApp.app)));
            });
            btns.getChildren().add(b);
        }
        row.getChildren().addAll(pos,nm,capt,ovr,sp,btns);
        return row;
    }

    // ── ユーティリティ ─────────────────────────────────────
    private VBox card(String t) {
        VBox c = new VBox(8);
        c.setStyle("-fx-background-color:#16213E;-fx-background-radius:10;"
            +"-fx-padding:14 16 14 16;");
        Label tl = new Label(t);
        tl.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#FFD700;"
            +"-fx-padding:0 0 4 0;");
        Rectangle sep = new Rectangle();
        sep.setHeight(1); sep.setFill(Color.web("#2A2A4A"));
        sep.widthProperty().bind(c.widthProperty().subtract(32));
        c.getChildren().addAll(tl,sep);
        return c;
    }
    private void showInfo(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(msg);
        a.showAndWait();
    }
}

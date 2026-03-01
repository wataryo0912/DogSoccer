package ui;

import javafx.beans.property.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import model.Club;
import model.Player;
import service.ExcelLoaderService;

import java.util.List;
import java.util.stream.Collectors;
import model.Player.PlayerRole;

/**
 * スカッド画面
 * Excelから読み込んだ画像（players.image_file）をカードに表示する。
 */
public class SquadView extends VBox {

    private final MainApp app;
    private TableView<PlayerRow> table;

    public SquadView(MainApp app) {
        this.app = app;
        setStyle("-fx-background-color:#08081a;");
        setSpacing(0);

        getChildren().addAll(buildHeader(), buildContent());
    }

    // ── ヘッダー ─────────────────────────────────────────────
    private HBox buildHeader() {
        HBox bar = new HBox();
        bar.setStyle("-fx-background-color:#0d0d22;-fx-padding:20 24 16 24;"
            + "-fx-border-color:rgba(255,255,255,0.06);-fx-border-width:0 0 1 0;");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setSpacing(20);

        Label title = new Label("👥  スカッド管理");
        title.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:#fff;");

        Club club = MainApp.playerClub;
        String color = club.getColor();
        Label stats = new Label(String.format("選手数: %d  |  平均OVR: %.1f  |  週給総額: ¥%,d",
            club.getSquad().size(), club.getAverageOverall(), club.getTotalWeeklySalary()));
        stats.setStyle("-fx-font-size:12px;-fx-text-fill:rgba(255,255,255,0.4);");

        // ユニフォーム画像（ヘッダーに小さく表示）
        String uniformUrl = ExcelLoaderService.toImageURL(club.getUniformImageFile());
        HBox right = new HBox(8);
        right.setAlignment(Pos.CENTER_RIGHT);
        if (uniformUrl != null) {
            try {
                ImageView iv = new ImageView(new Image(uniformUrl));
                iv.setFitHeight(50); iv.setPreserveRatio(true);
                right.getChildren().add(iv);
            } catch (Exception ignored) {}
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bar.getChildren().addAll(title, stats, spacer, right);
        return bar;
    }

    // ── コンテンツ（役割別タブ） ────────────────────────────
    private TabPane buildContent() {
        TabPane tp = new TabPane();
        tp.setStyle("-fx-background-color:#08081a;");
        tp.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tp, Priority.ALWAYS);

        // 各役割タブを動的生成
        for (PlayerRole role : PlayerRole.values()) {
            List<Player> group = MainApp.playerClub.getSquad().stream()
                .filter(p -> p.getRole() == role).collect(Collectors.toList());
            String icon = switch (role) {
                case REGISTERED -> "⚽";
                case BENCH      -> "🪑";
                case INACTIVE   -> "🚑";
                case ACADEMY    -> "🏟️";
            };
            String tabLabel = icon + " " + role.label + " (" + group.size() + ")";
            Tab tab = new Tab(tabLabel, buildRolePane(group, role));
            tab.setClosable(false);
            tp.getTabs().add(tab);
        }
        return tp;
    }

    /** 役割ごとのカード＋テーブル分割ペイン */
    private SplitPane buildRolePane(List<Player> players, PlayerRole role) {
        SplitPane split = new SplitPane();
        split.setStyle("-fx-background-color:#08081a;");

        // 左: 選手カードグリッド
        FlowPane flow = new FlowPane();
        flow.setStyle("-fx-background-color:#08081a;-fx-padding:16;");
        flow.setHgap(12); flow.setVgap(12);
        for (Player p : players) flow.getChildren().add(buildPlayerCard(p));

        ScrollPane cardScroll = new ScrollPane(flow);
        cardScroll.setStyle("-fx-background:#08081a;-fx-background-color:#08081a;");
        cardScroll.setFitToWidth(true);

        // 右: テーブル
        VBox tableBox = new VBox(8);
        tableBox.setStyle("-fx-background-color:#08081a;-fx-padding:16;");

        // 役割説明ラベル
        String desc = switch (role) {
            case REGISTERED -> "試合登録メンバー（最大25名推奨）";
            case BENCH      -> "ベンチ要員 — 練習参加のみ、試合出場不可";
            case INACTIVE   -> "スカッド外 — 怪我・ペナルティ等で一時離脱";
            case ACADEMY    -> "下部組織 — 昇格申請でスカッドに加えられます（移籍>下部組織）";
        };
        Label descLbl = new Label(desc);
        descLbl.setStyle("-fx-font-size:11px;-fx-text-fill:#AAAACC;-fx-padding:0 0 6 0;");
        descLbl.setWrapText(true);

        table = buildTable(players);
        tableBox.getChildren().addAll(descLbl, table);
        VBox.setVgrow(table, Priority.ALWAYS);

        split.getItems().addAll(cardScroll, tableBox);
        split.setDividerPositions(0.40);
        return split;
    }

    private VBox buildPlayerCard(Player p) {
        String clubColor = MainApp.playerClub.getColor();

        VBox card = new VBox(4);
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(130);
        card.setStyle(String.format("""
            -fx-background-color:linear-gradient(to bottom,#13132a,#0d0d1e);
            -fx-border-color:%s;
            -fx-border-width:1;
            -fx-border-radius:12;
            -fx-background-radius:12;
            -fx-padding:10 8 8 8;
            -fx-effect:dropshadow(gaussian,rgba(0,0,0,0.6),8,0,0,2);
            -fx-cursor:hand;
        """, p.isCaptain() ? "#ffd700" : clubColor));

        // ── 選手画像 ──────────────────────────────────────────
        StackPane photoWrap = new StackPane();
        photoWrap.setPrefSize(80, 90);
        photoWrap.setStyle(String.format(
            "-fx-background-color:linear-gradient(to bottom,#1a1a30,#0a0a18);"
            + "-fx-border-color:%s44;-fx-border-width:1;"
            + "-fx-border-radius:8;-fx-background-radius:8;", clubColor));

        String imgUrl = ExcelLoaderService.toImageURL(p.getImageFile());
        if (imgUrl != null) {
            try {
                ImageView iv = new ImageView(new Image(imgUrl));
                iv.setFitWidth(80); iv.setFitHeight(90);
                iv.setPreserveRatio(true);
                photoWrap.getChildren().add(iv);
            } catch (Exception ex) {
                photoWrap.getChildren().add(defaultPhoto(p, clubColor));
            }
        } else {
            photoWrap.getChildren().add(defaultPhoto(p, clubColor));
        }

        // キャプテンマーク
        if (p.isCaptain()) {
            Label cap = new Label("©");
            cap.setStyle("-fx-font-size:14px;-fx-text-fill:#ffd700;"
                + "-fx-font-weight:bold;");
            StackPane.setAlignment(cap, Pos.TOP_RIGHT);
            photoWrap.getChildren().add(cap);
        }

        // ── 背番号バッジ ──────────────────────────────────────
        Label numBadge = new Label("#" + p.getShirtNumber());
        numBadge.setStyle(String.format(
            "-fx-background-color:%s;-fx-text-fill:#fff;"
            + "-fx-font-size:10px;-fx-font-weight:bold;"
            + "-fx-padding:2 6;-fx-background-radius:4;", clubColor));

        // ── 名前 ─────────────────────────────────────────────
        Label nameLabel = new Label(p.getFullName());
        nameLabel.setStyle("-fx-font-size:11px;-fx-text-fill:#fff;-fx-font-weight:bold;");
        nameLabel.setWrapText(true);
        nameLabel.setAlignment(Pos.CENTER);
        nameLabel.setMaxWidth(120);

        // ── ユニフォームネーム ────────────────────────────────
        Label uniLabel = new Label(p.getUniformName());
        uniLabel.setStyle(String.format(
            "-fx-font-size:9px;-fx-text-fill:%s;-fx-font-weight:bold;"
            + "-fx-letter-spacing:1;", clubColor));

        // ── ポジション ＋ OVR ─────────────────────────────────
        HBox posOvr = new HBox(4);
        posOvr.setAlignment(Pos.CENTER);
        Label posLabel = new Label(p.getPosition().toString());
        posLabel.setStyle("-fx-background-color:#333;-fx-text-fill:#aaa;"
            + "-fx-font-size:9px;-fx-padding:1 4;-fx-background-radius:3;");
        Label ovrLabel = new Label("OVR " + p.getOverall());
        ovrLabel.setStyle(String.format(
            "-fx-font-size:10px;-fx-text-fill:%s;-fx-font-weight:bold;", clubColor));
        posOvr.getChildren().addAll(posLabel, ovrLabel);

        // ── 犬種 ─────────────────────────────────────────────
        Label breedLabel = new Label(p.getBreed());
        breedLabel.setStyle("-fx-font-size:9px;-fx-text-fill:rgba(255,255,255,0.3);");

        card.getChildren().addAll(photoWrap, numBadge, nameLabel, uniLabel, posOvr, breedLabel);

        // ── 引退表明バッジ ────────────────────────────────────
        if (p.isRetirementAnnounced()) {
            Label retireBadge = new Label("🏁 今期引退");
            retireBadge.setStyle(
                "-fx-font-size:9px;-fx-text-fill:#FF6666;"
                + "-fx-background-color:#3A0000;-fx-background-radius:3;"
                + "-fx-padding:1 4;");
            card.getChildren().add(retireBadge);
        }

        // ── 仲良し度バッジ ────────────────────────────────────
        //    この選手の最高仲良し度ペアを表示
        java.util.List<model.Friendship> fList =
            MainApp.friendshipManager.getFriendshipsOf(p);
        if (!fList.isEmpty()) {
            model.Friendship best = fList.get(0);
            int pid = best.getPartnerId(p.getId());
            Player partner = MainApp.playerClub.getSquad().stream()
                .filter(x -> x.getId() == pid).findFirst().orElse(null);
            if (partner != null) {
                Label badge = new Label(best.getLevel().label + " " + partner.getUniformName());
                badge.setStyle("-fx-font-size:8px;-fx-text-fill:#FFD700;"
                    + "-fx-background-color:#1A1A00;-fx-background-radius:3;"
                    + "-fx-padding:1 4;");
                card.getChildren().add(badge);
            }
        }

        return card;
    }

    /** 画像がない場合のデフォルト表示 */
    private Label defaultPhoto(Player p, String clubColor) {
        String emoji = switch (p.getPosition()) {
            case GK -> "🧤"; case FW -> "⚽"; case DF -> "🛡️"; default -> "🐾";
        };
        Label lbl = new Label(emoji + "\n" + p.getBreed().charAt(0));
        lbl.setStyle(String.format(
            "-fx-text-fill:%s;-fx-font-size:22px;-fx-alignment:center;", clubColor));
        lbl.setAlignment(Pos.CENTER);
        return lbl;
    }

    // ── テーブル ─────────────────────────────────────────────
    private TableView<PlayerRow> buildTable(List<Player> players) {
        TableView<PlayerRow> tv = new TableView<>();
        tv.setStyle("-fx-background-color:#0d0d22;-fx-border-color:transparent;");
        tv.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        tv.getColumns().addAll(
            col("背番号",  "shirtNumber",  55, true),
            col("ユニ名",  "uniformName",  90, false),
            col("名前",    "fullName",    110, false),
            col("犬種",    "breed",       110, false),
            col("POS",     "position",     55, false),
            col("OVR",     "overall",      55, true),
            col("利き足",  "dominantFoot", 60, false),
            col("年齢",    "age",          50, true),
            col("SPD",     "speed",        50, true),
            col("SHT",     "shooting",     50, true),
            col("PAS",     "passing",      50, true),
            col("DEF",     "defending",    50, true),
            col("STM",     "stamina",      50, true),
            col("根性",    "spirit",       50, true),
            col("週給",    "salary",      110, false),
            col("📷",      "hasImage",     40, false)
        );

        tv.getItems().setAll(
            players.stream().map(PlayerRow::new).toList()
        );
        return tv;
    }

    @SuppressWarnings("unchecked")
    private <T> TableColumn<PlayerRow, T> col(String title, String prop, double w, boolean right) {
        TableColumn<PlayerRow, T> c = new TableColumn<>(title);
        c.setCellValueFactory(d -> {
            PlayerRow r = d.getValue();
            Object v = switch (prop) {
                case "shirtNumber"  -> r.shirtNumber;
                case "uniformName"  -> r.uniformName;
                case "fullName"     -> r.fullName;
                case "breed"        -> r.breed;
                case "position"     -> r.position;
                case "overall"      -> r.overall;
                case "dominantFoot" -> r.dominantFoot;
                case "age"          -> r.age;
                case "speed"        -> r.speed;
                case "shooting"     -> r.shooting;
                case "passing"      -> r.passing;
                case "defending"    -> r.defending;
                case "stamina"      -> r.stamina;
                case "spirit"       -> r.spirit;
                case "salary"       -> r.salary;
                case "hasImage"     -> r.hasImage;
                default             -> new SimpleStringProperty("");
            };
            return (javafx.beans.value.ObservableValue<T>) v;
        });
        c.setPrefWidth(w);
        if (right) c.setStyle("-fx-alignment: CENTER-RIGHT;");
        return c;
    }

    // ── PlayerRow（TableView用） ─────────────────────────────
    public static class PlayerRow {
        public final SimpleIntegerProperty shirtNumber;
        public final SimpleStringProperty  uniformName;
        public final SimpleStringProperty  fullName;
        public final SimpleStringProperty  breed;
        public final SimpleStringProperty  position;
        public final SimpleIntegerProperty overall;
        public final SimpleStringProperty  dominantFoot;
        public final SimpleIntegerProperty age;
        public final SimpleIntegerProperty speed;
        public final SimpleIntegerProperty shooting;
        public final SimpleIntegerProperty passing;
        public final SimpleIntegerProperty defending;
        public final SimpleIntegerProperty stamina;
        public final SimpleIntegerProperty spirit;
        public final SimpleStringProperty  salary;
        public final SimpleStringProperty  hasImage;

        public PlayerRow(Player p) {
            shirtNumber  = new SimpleIntegerProperty(p.getShirtNumber());
            uniformName  = new SimpleStringProperty(p.getUniformName());
            fullName     = new SimpleStringProperty(p.getFullName());
            breed        = new SimpleStringProperty(p.getBreed());
            position     = new SimpleStringProperty(p.getPosition().name());
            overall      = new SimpleIntegerProperty(p.getOverall());
            dominantFoot = new SimpleStringProperty(p.getDominantFoot());
            age          = new SimpleIntegerProperty(p.getAge());
            speed        = new SimpleIntegerProperty(p.getSpeed());
            shooting     = new SimpleIntegerProperty(p.getShooting());
            passing      = new SimpleIntegerProperty(p.getPassing());
            defending    = new SimpleIntegerProperty(p.getDefending());
            stamina      = new SimpleIntegerProperty(p.getStamina());
            spirit       = new SimpleIntegerProperty(p.getSpirit());
            salary       = new SimpleStringProperty("¥" + String.format("%,d", p.getSalary()));
            hasImage     = new SimpleStringProperty(p.getImageFile().isEmpty() ? "" : "📷");
        }
    }
}

# 画像管理システム README

## 概要

DALL-Eで生成した画像を**後から追加するだけで自動反映される**仕組みです。  
画像ファイルが存在しない間は、プレースホルダー（灰色の枠＋テキスト）が代わりに表示されます。

```
画像なし → プレースホルダー表示（ゲームは動く）
画像あり → 自動で画像に切り替わる
```

コードを一切変更する必要はありません。**ファイルを置くだけです。**

---

## ファイル構成

```
src/
└── main/
    ├── java/
    │   ├── ui/
    │   │   ├── util/
    │   │   │   └── ImageManager.java        ← 画像読み込み管理（中心クラス）
    │   │   └── component/
    │   │       ├── PlayerCardView.java       ← 選手カード表示部品
    │   │       ├── SpecialMoveCardView.java  ← 必殺技カード表示部品
    │   │       └── ClubVisualView.java       ← エンブレム・ユニフォーム部品
    └── resources/
        └── images/
            ├── players/     ← 選手画像をここに置く
            ├── moves/       ← 必殺技画像をここに置く
            ├── emblems/     ← クラブエンブレムをここに置く
            └── uniforms/    ← ユニフォーム画像をここに置く
```

---

## 画像の追加手順

### 1. DALL-Eで画像を生成する

`dalle_prompts.docx` の各セクションからプロンプトをコピーして生成してください。

| セクション | 対象 | 推奨サイズ |
|---|---|---|
| Section 1 | 選手カード | 1024×1024 |
| Section 2 | 必殺技エフェクト | 1792×1024（横長） |
| Section 3 | クラブエンブレム | 1024×1024 |
| Section 4 | ユニフォーム | 1024×1792（縦長） |

### 2. ファイル名を命名規則に合わせてリネーム

下の「ファイル命名規則」を参照してください。

### 3. 対応フォルダに配置する

```
src/main/resources/images/players/player_1.png   ← ここに置くだけ
```

### 4. ゲームを起動する（再ビルド不要）

Javaのリソースとして読み込まれるため、IDEで再起動するだけで反映されます。  
`ImageManager` のキャッシュは起動時にリセットされます。

---

## ファイル命名規則

### 選手画像 (`images/players/`)

| ファイル名 | 選手名 | 犬種 |
|---|---|---|
| `player_1.png` | ぶん助 | 柴犬 |
| `player_2.png` | ブル | ブルドッグ |
| `player_3.png` | キン太郎 | セントバーナード |
| `player_4.png` | 留二郎 | 秋田犬 |
| `player_5.png` | 菊次郎 | ボーダーコリー |
| `player_6.png` | 武 | ハスキー |
| `player_7.png` | セバスチャン | プードル |
| `player_8.png` | 田中 | ブルドッグ |
| `player_9.png` | 鼻男 | ビーグル |
| `player_10.png` | 餡兵衛 | ダックスフンド |
| `player_11.png` | ガストン | ゴールデンレトリバー |

> 新規選手を追加した場合は `player_12.png`, `player_13.png` ... と連番で続けてください。

### 必殺技画像 (`images/moves/`)

| ファイル名 | 技名 | タイプ |
|---|---|---|
| `move_bunsuke.png` | 流星ブルアタック | SHOOT |
| `move_buru.png` | ブル旋風脚 | DRIBBLE |
| `move_kintaro.png` | 大岩守護壁 | TACKLE |
| `move_tomejiro.png` | 老練の一閃 | SPIRIT |
| `move_kikujiro.png` | 疾風縦突破 | DRIBBLE |
| `move_takeshi.png` | 鋼鉄の前足 | SAVE |
| `move_sebastian.png` | エレガントビジョン | DRIBBLE |
| `move_tanaka.png` | ブルドッグタックル | TACKLE |
| `move_hanao.png` | 嗅覚スキャン | TACKLE |
| `move_anbe.png` | 餡兵衛バウンド | DRIBBLE |
| `move_gaston.png` | 欧州仕込みウィング | SHOOT |

> 新規必殺技は `seed.json` の `image_file` フィールドに記載のファイル名に合わせてください。

### クラブエンブレム (`images/emblems/`)

| ファイル名 | クラブ名 |
|---|---|
| `emblem_老犬カンフーズ.png` | 老犬カンフーズ |
| `emblem_白影アカデミア.png` | 白影アカデミア |
| `emblem_黒鎧フォートレス.png` | 黒鎧フォートレス |
| `emblem_蒼海ユースアカデミー.png` | 蒼海ユースアカデミー |
| `emblem_氷壁タクティクス.png` | 氷壁タクティクス |
| `emblem_ノーザン・ロングラン.png` | ノーザン・ロングラン |
| `emblem_赤牙プレス団.png` | 赤牙プレス団 |
| `emblem_サンフレア・フリーダム.png` | サンフレア・フリーダム |
| `emblem_闘魂バリオス.png` | 闘魂バリオス |
| `emblem_ゴールドスターズ.png` | ゴールドスターズ |
| `emblem_ロイヤル・ブレインズ.png` | ロイヤル・ブレインズ |
| `emblem_シルク・ウィングス.png` | シルク・ウィングス |
| `emblem_山岳ファミリア.png` | 山岳ファミリア |
| `emblem_グリーンハウンド.png` | グリーンハウンド |
| `emblem_ストーンウォールズ.png` | ストーンウォールズ |
| `emblem_都市型マネーボールFC.png` | 都市型マネーボールFC |
| `emblem_メトロポリス・エナジー.png` | メトロポリス・エナジー |
| `emblem_シリコン・アルゴリズムズ.png` | シリコン・アルゴリズムズ |

> ファイル名はクラブ名をそのまま使います。`ClubVisualView.emblemFileName()` が自動で生成します。

### ユニフォーム (`images/uniforms/`)

| ファイル名 | 対象クラブ思想 |
|---|---|
| `uniform_defensive.png` | DEFENSIVE系（老犬カンフーズ、黒鎧、ストーンウォールズ等） |
| `uniform_youth.png` | YOUTH_ACADEMY系（白影アカデミア、蒼海ユース等） |
| `uniform_galactico.png` | GALACTICO系（サンフレア、ゴールドスターズ等） |
| `uniform_physical.png` | PHYSICAL_PRESS系（赤牙プレス団、メトロポリス等） |
| `uniform_data.png` | MONEYBALL・PURE_DATA系（シリコン、都市型マネーボール等） |
| `uniform_default.png` | 上記以外のフォールバック |

---

## 各クラスの使い方

### ImageManager（直接使う場合）

```java
import ui.util.ImageManager;

// 選手画像（160×200）
StackPane box = ImageManager.playerCard("player_1.png", "ぶん助");

// サイズ指定版
StackPane box = ImageManager.playerCard("player_1.png", "ぶん助", 80, 100);

// 必殺技画像（320×180）
StackPane box = ImageManager.moveEffect("move_bunsuke.png", "流星ブルアタック");

// エンブレム（80×80）
StackPane box = ImageManager.emblem("emblem_老犬カンフーズ.png", "老犬カンフーズ");

// ユニフォーム（120×160）
StackPane box = ImageManager.uniform("uniform_defensive.png", "守備型");

// ファイルが存在するか確認
boolean exists = ImageManager.exists("player", "player_1.png");
```

### PlayerCardView（選手カード）

```java
import ui.component.PlayerCardView;

// チームメンバ一覧のアイコン（小）
PlayerCardView card = new PlayerCardView(player, PlayerCardView.Size.SMALL);

// 選手詳細タブ（中）
PlayerCardView card = new PlayerCardView(player, PlayerCardView.Size.MEDIUM);

// 選手詳細画面メイン（大）
PlayerCardView card = new PlayerCardView(player, PlayerCardView.Size.LARGE);

// slump状態は自動で赤い点が付く（player.getMood() == "slump" のとき）
```

### SpecialMoveCardView（必殺技カード）

```java
import ui.component.SpecialMoveCardView;

// 選手詳細内の小表示
SpecialMoveCardView card = new SpecialMoveCardView(move, SpecialMoveCardView.Size.SMALL);

// イベント画面・習得画面（中）
SpecialMoveCardView card = new SpecialMoveCardView(move, SpecialMoveCardView.Size.MEDIUM);

// 試合中の演出オーバーレイ（大）
SpecialMoveCardView card = new SpecialMoveCardView(move, SpecialMoveCardView.Size.LARGE);
```

### ClubVisualView（エンブレム・ユニフォーム）

```java
import ui.component.ClubVisualView;

// エンブレム（サイズ別）
StackPane emb = ClubVisualView.emblemSmall(club);    // 48×48  順位表
StackPane emb = ClubVisualView.emblemMedium(club);   // 96×96  クラブ詳細
StackPane emb = ClubVisualView.emblemLarge(club);    // 144×144 メイン表示

// ユニフォーム（思想から自動判定）
StackPane uni = ClubVisualView.uniform(club);

// エンブレム + クラブ名 の横並びセット（順位表の行など）
HBox row = ClubVisualView.emblemWithName(club);
```

---

## 既存画面への組み込み例

### WeeklyPlanView（既存ファイル）へのエンブレム追加

```java
// buildTopBar() の中でクラブエンブレムを表示する例
private HBox buildTopBar() {
    HBox bar = new HBox(16);
    // ...既存コード...

    // ← この1行を追加するだけ
    StackPane emblem = ClubVisualView.emblemSmall(MainApp.playerClub);
    bar.getChildren().add(0, emblem);  // 左端に挿入

    return bar;
}
```

### チームメンバ一覧への選手カード追加

```java
// 選手一覧をFlowPaneに並べる例
FlowPane membersPane = new FlowPane(12, 12);
for (Player player : club.getPlayers()) {
    PlayerCardView card = new PlayerCardView(player, PlayerCardView.Size.SMALL);
    card.setOnMouseClicked(e -> showPlayerDetail(player));
    membersPane.getChildren().add(card);
}
```

---

## プレースホルダーのデザイン

画像がない間はこのような表示になります：

| 種別 | 背景色 | 表示テキスト |
|---|---|---|
| 選手カード | 暗い紺 `#1a1a2e` | `🐾 選手名` |
| 必殺技 | 深い青黒 `#0d1b2a` | `⚡ 技名` |
| エンブレム | 暗い紺紫 `#12121f` | クラブ名先頭2文字 |
| ユニフォーム | 暗い紫紺 `#1e1e30` | `👕 クラブ名` |

既存ゲームの背景色（`#06060f`）に溶け込むよう設計しています。

---

## トラブルシューティング

**Q: 画像を置いたのに表示されない**  
A: `ImageManager.clearCache()` を呼び出してキャッシュをクリアしてください。または IDE を再起動してください。

**Q: 画像が引き伸ばされて表示される**  
A: `ImageManager` の `setPreserveRatio(true)` が有効なので、縦横比は保たれます。推奨サイズと異なる場合は余白が出ます。

**Q: 新しいクラブを追加した場合**  
A: `ClubVisualView.emblemFileName()` はクラブ名をそのままファイル名にします。`emblem_新クラブ名.png` を `images/emblems/` に置けば自動で認識されます。

**Q: 新しい選手を追加した場合**  
A: `seed.json` の `image_file` フィールドに `"player_12.png"` などを設定し、同名のファイルを `images/players/` に置いてください。

---

## 優先して画像を追加するおすすめ順

1. **ぶん助**（`player_1.png`）— 主人公。最もよく表示される
2. **クラブエンブレム**（`emblem_*.png`）— 順位表・移籍画面など全体に影響
3. **武**（`player_6.png`）— GKは試合中に大きく表示される
4. **流星ブルアタック**（`move_bunsuke.png`）— 必殺技演出で最初に目に入る
5. 残り選手・必殺技を順次追加

---

*最終更新: v0.1　画像は `dalle_prompts.docx` のプロンプトで DALL-E 3 を使って生成してください。*

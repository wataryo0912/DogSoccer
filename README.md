# 犬種サッカー管理ゲーム ⚽🐾

> **犬種ベース × 感情システム × クラブAI思想**を持つリーグシミュレーションゲーム

## コンセプト
- 数値だけでなく**ドラマを生む**
- クラブごとに**性格（思想）**を持つ
- シーズンが**自律的に回る**世界を作る

---

## 技術スタック

| 分類 | 技術 |
|---|---|
| 言語 | Java 21 |
| UI | JavaFX 21 |
| DB | MySQL 8 |
| ビルド | Maven |
| 設計書 | `docs/design/` 参照 |

---

## プロジェクト構成

```
SoccerManager/
├── src/main/java/
│   ├── main/           エントリーポイント
│   ├── model/          データモデル（Player, Club, SpecialMove ...）
│   ├── service/        ビジネスロジック（Match, Training, Event ...）
│   ├── db/             DAOクラス（MySQL接続）
│   └── ui/
│       ├── *.java      各画面（JavaFX）
│       ├── component/  再利用可能なUI部品
│       └── util/       ユーティリティ（ImageManager等）
├── src/main/resources/
│   ├── images/
│   │   ├── players/    選手画像（DALL-Eで生成 → ここに配置）
│   │   ├── moves/      必殺技エフェクト画像
│   │   ├── emblems/    クラブエンブレム
│   │   └── uniforms/   ユニフォーム
│   ├── data/           seed.json, CSVデータ
│   └── css/            スタイルシート
└── docs/design/        設計書・仕様書
```

---

## セットアップ

### 1. 必要なもの
- Java 21+
- Maven 3.9+
- MySQL 8.0+

### 2. データベース初期化
```bash
mysql -u root -p < src/main/resources/mysql_setup.sql
```

### 3. DB接続設定
`src/main/resources/db.properties` を作成（`.gitignore` 対象）：
```properties
db.url=jdbc:mysql://localhost:3306/soccer_manager
db.user=your_user
db.password=your_password
```

### 4. ビルド・起動
```bash
mvn clean package
mvn javafx:run
```

---

## 画像の追加方法

画像は**ファイルを置くだけで自動反映**されます（コード変更不要）。

詳細は [`docs/IMAGE_README.md`](docs/IMAGE_README.md) を参照してください。

生成プロンプト集：[`docs/design/dalle_prompts.docx`](docs/design/dalle_prompts.docx)

### 優先して追加するおすすめ順
1. `images/players/player_1.png` ← ぶん助（主人公）
2. `images/emblems/emblem_*.png` ← 全18クラブ（順位表に影響）
3. `images/moves/move_bunsuke.png` ← 必殺技演出

---

## 主要クラス

| クラス | 役割 |
|---|---|
| `ui.MainApp` | JavaFXアプリ起動・画面遷移管理 |
| `ui.WeeklyPlanView` | 週次メイン画面 |
| `ui.MatchView` | 試合シミュレーション画面 |
| `service.MatchSimulator` | 試合計算エンジン |
| `service.WeeklyEventService` | 週次イベント生成（30種類） |
| `model.Player` | 選手データ（感情パラメータ含む） |
| `ui.util.ImageManager` | 画像読み込み・プレースホルダー管理 |

---

## 設計ドキュメント

| ファイル | 内容 |
|---|---|
| `docs/design/game_design_AB.docx` | 感情フロー設計・クラブAI判断表 |
| `docs/design/event_design.xlsx` | 全30イベント仕様・変動パラメータ一覧 |
| `docs/design/dalle_prompts.docx` | DALL-E画像生成プロンプト集 |

---

## Claudeによるソース解析

このリポジトリをpublic公開後、以下の形式でURLを貼るとClaudeが解析できます：

```
# ファイル単体
https://github.com/[username]/SoccerManager/blob/main/src/main/java/service/MatchSimulator.java

# RAWファイル（直接読み込み）
https://raw.githubusercontent.com/[username]/SoccerManager/main/src/main/java/service/MatchSimulator.java
```

> ⚠️ Private リポジトリはClaudeから参照できません。解析時はPublicにするか、ファイルを直接貼り付けてください。

---

## ブランチ戦略

```
main        本番・安定版
develop     開発統合ブランチ
feature/*   機能開発（例: feature/emotion-system）
fix/*       バグ修正
```

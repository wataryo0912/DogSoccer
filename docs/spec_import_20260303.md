# DogSoccer 仕様書取り込みメモ (2026-03-03)

対象資料:
- `C:\Users\nabe1\Downloads\DogSoccer_画面仕様書_Codex用.docx`
- `C:\Users\nabe1\Downloads\DogSoccer_仕様書_Codex用.docx`

実装に反映した内容:
- 画面遷移APIを `MainApp` に追加:
  - `showScheduleView()`
  - `showTrainingView()`
  - `showPersonnelView()`
  - `showSquadView()`
  - `showClubView()`
  - `showStandingsView()`
- 仕様書の画面に対応する新規UIを追加:
  - `ui.ScheduleScreenView`（スケジュール確認画面）
  - `ui.StandingsView`（順位表画面）
- `MainMenuView` の下部ボタンを仕様ラベルに合わせて統一:
  - 次週のスケジュール / 練習 / 人事 / 編成 / クラブ管理 / 日程進行
- `WeeklyPlanView` の導線を仕様に合わせて調整:
  - 左パネルの各ナビボタンを `MainApp` の遷移APIへ接続
  - 順位ラベルをクリックで順位表へ遷移
  - 週次イベントパネルを常時表示

未反映（次工程候補）:
- `TrainingView` / `PersonnelView` / `ClubView` / `MemberListView` の専用クラス化
- 仕様書レベルのレイアウト統一（全画面共通ヘッダー・フッタ）
- 背景動画素材の `MediaView` 運用

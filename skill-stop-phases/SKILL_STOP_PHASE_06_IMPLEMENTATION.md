# SKILL STOP Phase06 — 最終総合受入
Status: IN_PROGRESS — FINAL_RUNTIME_ACCEPTANCE

Phase01〜05のproduction実装を変更せず、最終受入専用の実機シナリオと証拠生成を追加する。

## 実Paper/Fabricで追加確認する項目
- 上段 / 中段 / 下段777の実入力とBIG入賞
- 4コマ滑り、第一停止後・第二停止後のnextStopHints更新
- REG成立時にBIG入力ベクトルを使ってもBIGへ化けないこと
- 持越しBIG中REPLAYで下段ボーナス入賞
- 通常時BAR/BAR/BAR狙い拒否、Phase05のチャレンジBAR成功
- 1枚役 ONE_A / ONE_B / ONE_CD / ONE_E / ONE_F / ONE_H の回収・取りこぼし・BIG優先を各実操作
- Premium A/C/D/E のチェリー・ピエロ第一停止、Bの中段チェリー第一停止、Fの直入賞禁止
- Phase05の残G、チャレンジPNG、第三停止消灯、成功音1回、観覧同期、再接続/再起動を同じPhase06ランナーへ継承

各独立ケースの間はproductionの `/piri recover cashout` で正規にセッションを解放し、同じPaper/Fabricプロセス・同一production JARの試行内で次ケースへ進む。

## ベクトル
`SkillStopPhase06Vectors` がproduction `SkillStopControl` から入力ベクトルを生成する。ランナーはその入力を実Keyboard経路へ送信し、実サーバーの停止コマ・slip・払出・入賞状態を同じベクトルと照合する。ベクトル生成そのものを実機PASSとは数えない。

## 機械割
Phase04と同じproduction controller export + `verify-economy.cjs` をPhase06 sourceで再実行し、実整数重み・実行可能攻略手順が設定1〜6の確定値と一致することを再確認する。

## 実行
- GitHub Actions: `.github/workflows/skill-stop-phase06-runtime.yml`
- Windows: `run-skill-stop-phase06-runtime.bat`
- 実機ランナー: `runtime-test-support/run_skill_stop_phase06.py`

CI/runtime PASS後に本書・仕様・IMPLEMENTATION_STATUSをCOMPLETEへ更新し、mainへマージする。

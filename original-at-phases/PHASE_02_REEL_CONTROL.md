> **2026-11-08 現在**：ユーザー承認の特殊図柄/チェリー停止v2に更新中。v1の実機合格は保持するがv2への新たな実Paper/Fabric受入が必要。新仕様：[REEL_DESIGN_V2.md](REEL_DESIGN_V2.md)。

# HYPER_DOG — PHASE 02 Reels, symbols, and server-authoritative stops

Status: **IN_PROGRESS — V2_USER_APPROVED_REEL_REVISION_REVALIDATION**

**最終実機証跡:** [PHASE_02_RUNTIME_ACCEPTANCE.md](PHASE_02_RUNTIME_ACCEPTANCE.md)。[GitHub Actions #42442118865](https://github.com/Pasta0719/PiriJuggler/actions/runs/42442118865) にて実Paper/Fabric 57ラウンド・171停止、7種実画像表示・復帰、全回帰PASS。詳細実装履歴は [PHASE_02_IMPLEMENTATION.md](PHASE_02_IMPLEMENTATION.md)。機種登録・抽選経済・完成筐体は後続Phase。

## 先行設計・静的検証（実装前に作成済み）
- **[REEL_DESIGN_V2.md](REEL_DESIGN_V2.md)**：左・中・右それぞれ21コマの**更新後の配列**、通常4コマ停止、レア・確定引込み、AT赤7揃い、役別合法出目。
- **[REEL_STRIPS_V2.json](REEL_STRIPS_V2.json)**：機械可読のリール配列と形状制約。
- **[validate_reels.py](validate_reels.py)**：9,261最終停止3つ組の静的設計検証。実Minecraftの停止ソルバー・実機試験とは別。
- **21コマ配列と停止条件は設計確定済み。** ハズレ・ブドウ・リプレイは全停止順/全押下位置で4コマ以内、レア役・BAR・AT赤7・CZ虹7は4コマを超えても確定引込み（最大20コマ）。赤7は元役入賞・払出の後、無償の再回転で必ず揃える。**Javaコア・Paper側単独ラウンドは実装済み。実Paper/Fabric間のPhase02独立テスト経路と実機受入は完了（生産用機種登録はPhase03）**。

## 実装対象
- **ユーザーが用意するのは図柄画像のみ。左・中・右3本の図柄の並び順（配列）は本Phaseで開発側が設計・決定する。** 配列表の提出をユーザーに求めない。
- **2026-11-07：図柄画像7枚を受領し、256×256透過RGBA PNGとして専用パスに配置済み（commit `e950b32`）。** 実画面での表示確認は未実施。
- 図柄画像の納品前でも、図柄ID（RED7/RAINBOW7/BAR/CHERRY等）を使って**配列表の設計・停止制御テストを進められる**ようにする。画像が届いたら専用名前空間に登録し、既存JUGGLERのPNGは上書きしない。
- 3本それぞれの1周分の物理配列をこちらで設計し、3列×3段の表示、役ごとの有効ライン、停止テーブル、押し順と停止タイミング、外れ出目を専用実装。**確定配列は各21コマ。通常停止は最大4コマ、レア・確定役は最大20コマの追加回転で必ず成立させる。**
- 弱チェリーの上/下段左中2連、強チェリーの左上/下段を含む3連、チャンス目の中段赤7/BAR/赤7、強チャンス目の中段赤7/BAR/虹7を成立させる停止テーブルを構築。**AT当選時の中段赤7×3、BAR確定時の任意5ラインBAR×3、CZ成功時の中段虹7×3は確実に引き込む**。CZ失敗は左右虹7・中央外れ。
- 1回のレバーONでサーバーが内部役を確定し、3停止で再抽選しない。意図しないBAR・虹7・赤7の揃いを禁止。AT当選時は元役結果と払出を確定させたのち、追加遊技ではない専用の赤7自動再回転を行う。**[実装判断確定表](IMPLEMENTATION_DECISIONS_V1.md) を停止制御の優先仕様とする**。

## 完了条件
1. **通常役は全押し順×全押下位置で0〜4コマに収まること**、レア役・BAR・AT赤7・CZ虹7は最終停止で100%指定出目へ誘導できることを証明。役重複AT時の役払い出しと赤7告知を両立させ、役非成立時の偽BAR・虹7・赤7揃いを防ぐ網羅テスト。
2. Minecraft Paper+Fabricの実停止経路で入力、3列図柄、停止位置、復帰の記録を保存。
3. **図柄画像が未提供でも配列設計・ロジック検証は止めない。** Fabricの最終実図柄表示だけはユーザー提供の7枚を使用する。画像がまだない場合は画像受入だけBLOCKEDにする。配列・5ライン・特殊中段停止・滑り・赤7再告知は既に仕様決定済みで、選択肢として保留しない。
4. 既存FixedReels / SkillStopReels / JugglerGod停止ロジックの回帰テストPASS。

## 全Phase共通の絶対条件
- 最初に `CHANGE_SAFETY_POLICY.md` と `CODEX_START.md`、本フォルダの `SPEC_LOCK.md`、`PHASE_MAP.json` を読む。
- 既存 `JUGGLER` / `JUGGLER_GOD` / `JUGGLER_GOD_EXTREME` / `SKILL_STOP` / 既存モバイル・データ管理を壊さない。引退した単体 `GOD` は完成品扱いしない。
- 追加は新機種専用のクラス・タイプ・設定・アセット名前空間を優先し、既存の型・画面・プロトコルを変えるなら回帰テストを先に決める。
- 既存データと未完了遊技権利の保持は必須。Paperが抽選・差枚数・停止結果の正本、Fabricは表示と入力に限る。
- 実装Phaseはテストとビルドに加え、対象の機能を実MinecraftのPaper/Fabric経路で検証し、`runtime-evidence/` に再現手順・ログ・必要な画像・結果を残す。実環境が無ければBLOCKED。テスト通過のみでCOMPLETEにしない。
- 1 run = 1 Phase。前PhaseのCOMPLETEと次Phaseの明示開始承認が無ければ次へ進めない。確定仕様の変更・試作仕様からの逸脱には事前承認が必要。
- 本Phase計画作成自体はゲームコード変更の許可ではない。

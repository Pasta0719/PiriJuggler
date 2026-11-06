# PiriJuggler — 自律ホール経営・設定投入 実装仕様 v1.0

**Codex実装時の必読・定義優先順位:** 
1. [実装解釈固定契約 v1.1](HALL_AUTONOMY_DEFINITION_LOCK_V1.md) — 本文の曖昧な式・条件・期限・優先順位の最終定義
2. [v5 SQLite参照DDL](HALL_SCHEMA_V5_REFERENCE.sql) — 新しい表・制約・キー・索引の正規定義
3. 本文 — 機能要件・システム連携・実装順・受入条件
4. [既存SPEC.md](../SPEC.md) — 遊技抽選・プレイヤー資産・旧period・通信の互換仕様

※ 重複した記述が矛盾する場合は、**この定義優先順位**に従う。文中の「程度」「例」「必要なら」といった任意性をCodexの裁量で新仕様へ置き換えない。
更新: 2026-11-06 / 対象: Paper サーバー / 状態: 実装可能な詳細設計
関連: [SPEC.md](../SPEC.md), `paper/src/main/resources/config.yml`

## 0. 要求と優先順位
**最終成果物**: 1店舗で、人が何もしなくても営業日ごとに全自動で設定1〜6を決定し、過去の実績から経営方針を更新する。設定投入には角1/角2・末尾・連番・機種・日付等に由来する説明可能で継続的な傾向があり、遊技者は終了した日の履歴から店読み・答え合わせができる。人間の介入は任意で、その指定を最優先する。

本書は**新機能についての製品仕様の追加・上書き決定**であり、既存の `SPEC.md` のゲーム抽選・遊技状態・通信・景品・管理機能を勝手に変更する権限を与えない。ただし既存の §70–72 における「プロセス開始時に設定抽選」「固定profileが自動投入を決定」の部分に限り、本書の自律設定を優先する。旧仕様のプロセス単位 `business_period` は破棄しない。実装PRでは `SPEC.md` 冒頭に本書を明示的な追加仕様として参照させ、上書き範囲を明記する。

### 破ってはいけない不変条件
1. 各機種・設定の抽選確率、演出、ゲーム状態、持ちメダル、未完ボーナス/GODストック、獲得済み資産は変更しない。設定投入の決定と当選抽選は完全に別の乱数系列。
2. **シミュレーション結果は実際のプレイヤー稼働・現金売上・現金粗利に混入させない。** 過去のシミュレーション混入分が判別不能なら `LEGACY_UNVERIFIED` 扱い。実績を捏造しない。
3. プレイヤーが操作中・GRACE保持中・未完遊技がある台では設定を変更しない。強制清算、再抽選、資産破棄で変更可能状態を作らない。
4. 1営業日に1つの確定計画。再起動・リロードで再抽選しない。1日内の個別手動変更のみ例外。
5. 「高設定だった理由」は**投入前に確定したポリシーと評価データ**から記録する。出玉結果を見て理由を捏造しない。
6. 自動運営に運営者の初期入力は要求しない。必要設定はデフォルトと既存台構成から導出する。追加設定が無い場合も安全に稼働。
7. 重大なエラーでは前回の有効設定とゲーム状態を保ち、失敗をログで報告する。初期化失敗を理由にDBや台を削除しない。
8. Minecraftクライアント・Fabric側への秘匿設定/ポリシー漏洩は禁止。公開は終了営業日の情報のみ。OPは現在の内部情報を参照可能。

## 1. 現行コードへの接続点（2026-11-06 の main を確認）
- `PiriDatabase.open`: 同一JVMのperiod再利用、JVM再起動ごとの新 `business_periods` と `StartupProfile.allocate` による再配分。ここで自動投入と重複しないよう分岐させる。
- `StartupProfile`: 既存normal/light/strong・日付・曜日・NEXT profile・SUFFIX/RUN/ALLなどの**旧式手動イベント**を解釈する互換アダプタにする。自律運営が有効なら無指定の固定distributionで上書きしない。
- `MachineService`: サービス初期化・メインスレッド側予約/状態・1秒tick・管理コマンドの窓口。定期判定と安全適用の窓口にする。DBへの重い処理は既存専用executor。
- `AdminStore.setSetting/setAuto`: 現在は手動設定の保存とautoフラグを別管理。自律エンジン導入後は「当日指定」と「期限付き/無期限固定」を区別する。
- `GameStore.commit`: 実プレイのBET/払出を既存ゲーム結果の永続化トランザクション内で日別集計へ反映。トランザクションIDの冪等性を継承。
- `MachineDataSimulationService` / `MachineDataSimulator` / `JugglerGodMachineDataSimulator` / `SkillStopMachineDataSimulator`: 現行の /piri sim は**本物の machine_period_stats とグラフを進める**。ホール予測用には絶対に呼ばず、DB書込しない専用forecast実装を作る。
- `EconomyStore`, `PrizeService`, `ProfitService`: `economy_transactions` の `APPLIED` な `LOAN`, `PACHINKO_LOAN`, `PRIZE_TO_VAULT` を金銭実績の原資料とする。`PROFIT_WITHDRAW` は店内利益の引出しであり営業粗利から除外する。
- `Machine`: `machine_id` と `Location(world,x,y,z,facing)`, `autoSetting`, `enabled`, `deleted`, `setting` を利用。
- `schema-v4.sql`: 既存version=4。破壊的再作成は禁止。新機能のスキーマを前方移行する。
- `DataLampSnapshot`: 既存プロセスperiodの表示仕様を壊さない。新しい「04:00区切り営業日」の表示は独立コマンドで提供する。

## 2. 時間・営業日・実行順序
- タイムゾーン: `Asia/Tokyo`、区切り: 毎日 **04:00 JST**。
- `hallDay(instant) = (instant at Asia/Tokyo - 4 hours).toLocalDate()`。例: 11/8 03:59 は11/7営業日、11/8 04:00は11/8営業日。
- 旧 `business_period_id` はJVM再起動/既存レガシー処理に従って引き続き管理。**hallDayとの同一視は禁止**。同一hallDayに複数periodが存在してよい。
- 1分ごと、サービスREADY時、および台が空いた直後に `HallOrchestrator.ensureDay` を呼ぶ。状態変更はDB executorに直列化。Schedulerメインスレッドは起動要求だけを行う。
- 初回: 既存の営業データを読む→未作成の当日を一度作る→利用可能な台に設定を割当→結果・根拠を原子的保存→状態配信。持ち越し・ロック中の台は`PENDING`で計画保存。
- 同日再起動: 当日の `hall_days` と `hall_setting_decisions` があれば再利用し、**設定を引き直さない**。新規JVM `business_period` 作成は従来どおり実施。旧 `StartupProfile.allocate` の自動再抽選を抑止。
- 04:00: 前日`CLOSED`→前日の集計確定→月間実績更新→必要ならポリシー更新→当日計画作成と安全な台への適用。締めと新規計画はDB上で冪等。クラッシュ回復で二重反映禁止。
- 休止/数日飛び: 稼働0の空白日を `NO_ACTIVITY` と記録可能。ただし架空のBET/売上/払い出しを発生させない。再開当日の設定計画のみを適用。期間をまたいで生じた手動期限は正しく失効。
- busy台: `ACTIVE`, `SUSPENDED_GRACE`、未解決遊技・予約中は計画のみ保存。既存の自動保護ロジックが許した完全な空き状態に戻った際に**その時点の最新営業日**の設定を一度だけ適用。翌日もbusyなら旧設定のまま。安全化のためのforce settle禁止。
- 04:00をまたぐゲーム結果: **実際の各ゲーム完了時刻**で新hallDayへ集計する。旧 `source_business_period_id` による旧プロセスperiod統計は従来どおり。時刻・集計規則の違いを明記し、混用しない。
- 起動直後で台配置/キャッシュ不完全の場合、全てを設定1へ初期化しない。既存設定維持と再試行で安全化する。

## 3. 売上・粗利・交換ギャップの正解
### 3.1 実績（通貨単位は既存Vaultの通貨）
`CASH_IN(day) = Σ APPLIED[LOAN,PACHINKO_LOAN]`  
`CASH_OUT(day) = Σ APPLIED[PRIZE_TO_VAULT]`  
`REALIZED_GROSS(day) = CASH_IN(day) - CASH_OUT(day)`  
`REALIZED_MARGIN = REALIZED_GROSS / CASH_IN`（CASH_IN=0なら未定義）。
`PROFIT_WITHDRAW` は店主による引出しであり損益でない。ロールバック・未確定・REVIEW_REQUIREDは実現した入出金として確定集計しない（未確定の警告を保持）。
**賭けたメダルの総BETに交換ギャップを毎回掛けない。**

現行の`economy.loan_medals=46`, `loan_amount=1000`; 現行景品は `small 52枚/1000`, `medium 208/4000`, `large 468/9000`。計算は定数埋込みではなく実際の`economy` / `prizes` 設定から取得する。将来の値変更に対応。通貨はdecimalとして扱い、DB/計算でfloat誤差を累積させない。

**注意**: 実現粗利=当期に入金した額−当期に景品を換金した額であり、持ち越しメダル、未換金景品、持ち越し状態の違いで当日の機械割とは一致しない。現状の景品アイテムには完全な所有者別未交換価値台帳がないため、厳密な未精算負債を確定値と称しない。予測内では**推定未精算価値**（推定の分布と信頼幅を持つ）として別欄に置き、`ESTIMATED`の品質表示。将来、正確な発行/交換トークン台帳を追加するなら実残高に昇格可能。ユーザー画面は「実現粗利」と「推定調整後粗利」を明確に区別。

機種・台別の現金粗利はメダルを持って他台へ移せるため、元台に厳密配賦しない。台別は`REAL_BET / REAL_PAYOUT / REAL_GAMES`というメダル収支を用い、現金は全店合計で管理する。パチンコは玉単位をメダルに混ぜず独立計数し、金額換算はその機種の貸出・交換仕様に基づく。

### 3.2 将来予測
実績に影響しない**read-only forecast**を使用。台の機種・設定・現時点の継続状態、稼働/現金再投資/景品交換の行動をサンプリングし、各候補の`predictedGross`, `predictedCashIn`, `predictedCashOut`, `variance`, `lowerTailRisk`を算出。
- 既存のゲーム抽選ロジックまたは同一仕様の副作用なし純粋アダプタから、(type, setting, 構成hash)ごとの`expectedBet, expectedPayout, payoutVariance`を推定。GOD等の状態持越しを無視した一律RTP流用は禁止。
- 計算用RNGは`RandomStreams.production()`と完全分離し、保存済シードから再現可能。予測が実機の乱数を消費してはならない。
- 予測性能キャッシュは`type, setting, configHash, modelVersion`をキーとする。必要なら非同期で更新。初回は3,000〜10,000G/組の低コスト推定→信頼幅が広ければ上限100,000Gまで段階増加（計算負荷上限とタイムアウトを設定）。候補64通りごとに生スピンを再実行せず、キャッシュと台別状態補正から評価する。
- 直近7/28/90日の**実稼働のみ**から稼働・投資・交換行動を平滑化推定する。`/piri sim`は含めない。履歴不足時の予測用事前分布として、稼働`300G/台・日`、貸出`1000通貨単位/台・日`を使ってよいが、**見込み値のみ**であり実績には記録しない。実サンプル増加とともに事前分布の寄与をゼロへ近づける。
- 現金出入金の予測は`expectation from RTP × bet`の単純換算で代替しない。投入・持ちメダル再利用・追加貸出・やめ時・交換までの**セッション行動モデル**を通す。
- 観測できない機種/状態・予測が異常な台は`UNMODELLED`として、勝手に低設定化せず現設定維持、警告と残りの台の最適化を行う。
- 対価の実績がゼロでも設定決定は動く。粗利項の比較が無意味な場合は残りの評価軸を再正規化し、初期探索を進める。ゼロ割り禁止。

### 3.3 自動粗利目標（運営者入力不要）
初期`targetMargin=0.12`（12%）、下限6%、上限16%。実稼働の7日/28日平均`G7/G28`から、
`targetMargin = clamp(0.12 + 0.04*(G7/G28 - 1), 0.06, 0.16)`。過去が7日未満・G28=0・検証品質不足なら0.12。実績から極端に上振れ/下振れした日は即座に値を変えず7日平滑。
月間見込み貸出×`targetMargin`=月間目標。月末までの残必要粗利を残営業日に平滑配分して日次の目安にする。ただし過剰な前日赤字回収は禁止: 日次目標は直近28日の日次予測粗利の`0.5〜1.5倍`にクリップ。見込みがゼロなら日次目標もゼロ、利益を捏造しない。
マイナス粗利の営業日を許容し、月目標の「絶対達成」は要求しない。月末超過/未達は次月の判断材料にするが、台の出玉制御や後追い当選操作は禁止。

## 4. 台配置判定
各台の`Machine.Location`（同world・同y・同facing）の軸方向に並ぶ隣接台を列として構築。隣接座標差は軸方向1〜3ブロック、直交方向0。連結成分の長さ3以上を1列とし、端から`corner1`（両端）、次を`corner2`（2台目）とする。長さ3は中央が両側corner2に当たるが重複は1回だけ計数する。隣接方向が曖昧・斜め・異なる向き・隣接候補が複数・一列に判定できない場合は位置特徴を`UNKNOWN`とし、角/並び推定のスコアを適用しない。勝手に台番号順を物理並びとみなさない。
`/piri hall layout set <machineId> <islandId> <position>` と`layout clear`で手動補正可能（完全任意）。機種/台番の特徴は配置不明でも常に有効。台の設置・撤去・移動・機種変更時には翌日からレイアウト再評価。

## 5. 自律営業方針・店読み
方針は`HallPolicy`（生成ID、種別、対象、強度、開始日、最短終了日、最大終了日、作成理由、シード、性能メトリクス）として保存。自動方針の有効期間は**最低28日、最大84日**。見直しは28日経過後7日ごと/明らかな配置消滅時に行う。同時有効方針は通常2〜3個、台数が少ないときは1〜2個。店ごとの固定性格は持たせない。**1店舗の過去実績と意思決定が唯一の継続する個性**となる。

自動生成の候補:
1. 日付: 月内の特定末尾、曜日、ゾロ目日、一定の複数日（例7,17,27）、台番末尾と日付数字の一致。
2. 番号: 台番末尾0〜9、ゾロ目、末尾2桁、前後の連番。
3. 配置: corner1、corner2、特定島、島内連続2〜3台、島移動ローテーション。
4. 機種: 特定機種・GOD系等カテゴリーに対する高設定優遇、低稼働機種への改善投入。
5. 設定履歴: 据え置き、前日低設定からの上げ、前日高設定の移動、数日のローテーション。
6. 出玉・稼働履歴: 最近の高/低出玉率、期待出玉との差、低稼働改善、人気機種の強化。
7. 複合: 特定日×末尾、特定日×角2、機種×角/並びなど**実データに存在する**条件の組合せ。

方針選択はシード付で重み付きサンプリングし、テーマ適用の期待利益/稼働改善と「発見できる法則」の両方を評価。高設定`setting>=4`の事前優遇倍率は初期範囲`1.7〜2.5倍`で、候補抽選の傾向重みとして用いる（高設定台数の強制ではない）。**候補生成の約25%は主方針外の台も検討**し、法則が常に当たる状態を避ける。複合条件は単純加算で無限に確率が増えないよう倍率上限3.0倍。
方針が存在しても予算や台数により当日高設定投入がない場合がある。ログには「適用候補」「不採用理由」「実際の採用」を区別する。偶然一致した末尾等を後から「狙い」として記録しない。
旧方針の成否は`設定効果ではなく稼働/粗利・翌日以降の傾向`で測定し、同機種・営業日等の比較可能条件で平滑化。十分なデータが無い場合は`INSUFFICIENT_DATA`として維持/探索を行い、成果が出たと断言しない。中期的に改善しない方針は最短28日後の見直しで入れ替える。

## 6. 設定最適化
入力: 当日、eligible台、既存/手動固定設定、現在の保護状態、方針、台別過去実績、現金予測、乱数シード。対象は`!deleted && enabled && autoSetting`、その他は現在設定を維持して予測上は固定台として加算。

1. DBスナップショットを取得。台別実データの観測時点を固定し、今後の実績が最適化途中に混入しないようにする。
2. 全台のポリシー適合度と機種稼働改善効果を計算。
3. 手動固定台を先に確定。その条件と矛盾する自動方針は適用しない。
4. 64種類のユニークな初期配置候補を決定論的シードで生成。候補のうち75%はテーマ誘導、25%はテーマ外探索も許す。設定比率の固定テーブルからのみ抽選する実装は禁止。手動指定外は初期分布`normal`をあくまで無履歴時のベース事前分布に利用可。
5. 各候補の粗利・需要・継続性・法則性・下振れリスクをモデルで予測。
6. スコア上位を局所探索（各最大1000手。台同士の交換、単独設定±1、RUN変更等）。各手は対象台・稼働・手動優先・状態保護を再検証。改善時のみ採択。
7. 最高スコア採用。タイなら安定シードで決定。結果/適用状態/判断根拠/forecastスナップショットを先に原子的に記録。その後安全に適用する。**翌朝の実績を見て過去の理由を書き換えない**。

スコア= `0.40*profitFit + 0.25*traffic + 0.20*pattern + 0.10*continuity + 0.05*risk`。各項目を同一候補集合の情報から**0〜1**へ正規化する（全候補同値の軸は1を付与）。例: `profitFit=1-clamp(abs(predGross - targetGross)/max(1000,abs(targetGross)),0,1)`。traffic=予測稼働の相対順位、pattern=投入前方針に沿った高設定配置の達成度、continuity=前回設定から過度に変化しない割合（ただしローテーション方針がある場合は方針達成も考慮）、risk=収支の下方5%分位・分散からの安定度。手動強制台はスコアより優先。全台固定等で候補が1つならその配置で終了。
シードは日付・ホールID(固定1店)・`policyRevision`・独立した秘密乱数から生成してDB保存。次の日にシードは変わる。同じDB入力/シード/ソフトバージョンからは**同一計画**になること。ゲーム抽選RNGへの影響を禁止。

## 7. 学習の入力、品質
- 直近7日: 50%、直近28日:35%、直近90日:15%。実観測数が少ないセグメントは全店・同機種の値にベイズ/疑似標本による縮約を行う。履歴0でも起動し、架空の営業データは登録しない。
- 比較する台別結果: realGames, realBet, realPayout, actualReturnRate=`realPayout / realBet`（bet0なら未定義）、設定別期待差分、現金貸出セッション数/総店貸出額（台別への厳密配賦はしない）、プレイヤーの続行/離脱率。
- 前日差枚で設定を上げる方針は可能だが、「過去に負けたから次は当たる」と抽選に因果を持ち込まない。ゲーム抽選独立。
- `/piri sim`と管理者のローカルシミュレーションは `SIMULATED` として隔離。既存historical `machine_period_stats`から実稼働とシミュレーションを分離できなければ学習用`LEGACY_UNVERIFIED`にする。v1導入以後は実ゲームcommit時に専用カウンタへ記録する。
- セッションが別台に移動しても金銭出入金はhall全体で一度だけ計上し、台間移動に見かけの貸出を生まない。
- 機種が変わった台は新機種のデータを旧機種に流用しない。台単位レイアウト特徴は維持可能、機械性能キャッシュは機種別に再生成。
- 店内の台数が少なく法則性の統計検出が難しい場合、無理な「確定法則」を作らず複合条件を減らす。

## 8. 永続化 — SQLite v4 → v5
`schema-v4.sql` は保持し、新しい`hall-schema-v5.sql`を追加。既存DBが`schema_version=4`なら一度だけtransaction内で新規テーブル追加→metadataを5に更新、失敗時全ロールバック。v5の同日再起動では再適用しない。旧DBを削除/作り直ししない。バックアップ・integrity_check・ロールバックテスト必須。

以下のテーブルを**指定名・主キー・意味**で作る。自由なJSON列の値はschemaVersion付きで読み書きし、未知の値を安全扱い。SQLカラムは必要な検索インデックスも作成する。
- `hall_days` (day TEXT PK, status OPEN|CLOSED|NO_ACTIVITY, opened_at, closed_at, seed, policy_revision, target_margin, monthly_target, daily_target, predicted_cash_in, predicted_cash_out, predicted_gross, real_cash_in, real_cash_out, realized_gross, data_quality, model_version)
- `hall_machine_days` (day, machine_id PK複合, machine_type, planned_setting, applied_setting, planned_at, applied_at, apply_status PENDING|APPLIED|SKIPPED|FAILED, real_games, real_bet, real_payout, sim_games, sim_bet, sim_payout, position_json, manual_override_id)
- `hall_policies` (policy_id PK, kind, params_json, strength, start_day, min_end_day, max_end_day, ended_at, creation_reason_json, revision, statistics_json)
- `hall_setting_decisions` (day, machine_id PK複合, old_setting, proposed_setting, score_json, reasons_json, rejected_reasons_json, forecast_json, seed, created_at, superseded_by_manual_at)
- `hall_manual_overrides` (override_id PK, scope MACHINE|TYPE|ISLAND|DAY|MARGIN|PROFILE, scope_value, payload_json, start_day, end_day NULL=無期限, priority, actor_uuid NULL=console, created_at, revoked_at)
- `hall_economy_events` (transaction_id PK, day, occurred_at, operation, amount_decimal TEXT, player_uuid, machine_id nullable, origin TEXT CHECK(origin='REAL'), status TEXT CHECK(status='APPLIED'))。既存`economy_transactions`の`APPLIED`完了時に同一トランザクションまたは確実な再試行可能ジャーナルで1件登録。バックフィル時の不明な日帰属は`LEGACY_UNVERIFIED`へ。
- `hall_performance_cache` (machine_type, setting, config_hash, model_version PK複合, sample_games, expected_bet, expected_payout, variance, confidence_json, updated_at)
- `hall_audit_log` (id PK, day, machine_id nullable, kind, payload_json, created_at)。エラー・監査・設定保留・手動操作を記録。

実装は実際のDBスキーマで`CHECK`と`FOREIGN KEY`（必要なもの）を設定。金額は通貨最小単位/decimalテキストで扱い、SQLite REALの浮動小数を新しい確定金額の集計原本にしない。\(day,machine_id\) の一意制約・`transaction_id`一意制約は必須。
`hall_machine_days`へのゲーム別実績書込みは既存`GameStore.commit`のreceipt確定と同一トランザクションに参加させる。スピンの二重加算は禁止。ボーナス内部BET/払出も実際の精算アクション単位で集計。PACHINKO/GOD/SKILL_STOP等、集計経路が異なるエンジンには専用adapterを用意し、サポート前は対象を`UNMODELLED`扱いして現設定保持。
`hall_economy_events` の履歴未収集期間は`LEGACY_UNVERIFIED`で表示し、正確な日次現金売上として断定しない。旧ProfitServiceの引出可能残高計算は変えない。

## 9. 手動介入 — 全く設定しなくても稼働
基本優先順位:
1. 遊技・資産保護（絶対不変）
2. 管理者の台別固定設定（無期限/期限付き）と `auto_setting=false`
3. 当日手動設定、日付/機種/並び・旧eventsの明示的イベントprofile
4. 自律ポリシー、粗利/稼働最適化

既存`/piri setting <id> <1..6>`と管理GUIのsetting変更は**当日の手動設定**として記録（busyなら今までどおり拒否）。自動設定ONの台は翌営業日から自動へ戻る。既存`auto_setting=false`の台は手動でONに戻すまで同じ設定を維持する。既存 `/piri event next <profile>`は「次の04:00営業日」の手動イベント指示に移行し、同日再起動による早期消費は禁止。`clear` は次回分だけ取消し。既存profilesの最低台数・patternは検証し、無理な台数なら理由を報告して実行可能分に縮小（台を消したりbusy状態を解除したりしない）。

追加コマンド（全操作はOP、publicと明示したreadのみ非OP）:
- `/piri hall status` 当日・予測・実績・品質・未適用台数・現行テーマ（OP）
- `/piri hall plan [yyyy-MM-dd]` 配分・設定計画と評価結果（OP）
- `/piri hall explain <yyyy-MM-dd> <machineId>` 終了日の投入理由（public）。当日分はOPのみ。
- `/piri hall history [days]` 終了営業日の台別設定・出玉率とテーマ答え合わせ（public、最大90日）
- `/piri hall pin <machineId> <1..6> [yyyy-MM-dd|forever]` 台固定（OP）
- `/piri hall unpin <machineId>` 固定解除（OP）
- `/piri hall rule add <type> <JSON>` 任意の期日/末尾/角/機種/並び等の追加方針（OP。構文とschemaをvalidate）
- `/piri hall rule remove <ruleId>` 解除（OP）
- `/piri hall target auto|<marginPercent>` 利益率目標の自動/指定（OP）
- `/piri hall layout set <machineId> <islandId> <position>` / `layout clear <machineId>` 任意レイアウト（OP）
- `/piri hall preview [yyyy-MM-dd]` 結果を一切書換えない計画検証（OP）
- `/piri hall auto on|off` 自律設定のON/OFF（OP）。OFF時は旧profile投入か設定維持を管理者が選べる。デフォルトON。

手動同士が矛盾した場合は後勝ちで黙って破壊せず**新規指定を拒否し衝突内容を返す**。手動が粗利目標と矛盾する場合は手動優先、利益未達の見込みを表示。リセット後の設定保護は同一。

## 10. config追加
既存 `config.yml` の上位`hall:`を追加して `ConfigValidation`も更新。設定しなくても以下の初期値で動く。動的な自律決定量を毎日人間が入力させない。

```yaml
hall:
  enabled: true
  timezone: Asia/Tokyo
  cutoff_hour: 4
  target_margin_default: 0.12
  target_margin_min: 0.06
  target_margin_max: 0.16
  learn_days: [7, 28, 90]
  learn_weights: [0.50, 0.35, 0.15]
  policy_min_days: 28
  policy_max_days: 84
  policy_review_days: 7
  candidates: 64
  local_search_steps: 1000
  exploration_ratio: 0.25
  forecast_max_sample_games: 100000
  daily_check_seconds: 60
  legacy_profiles_as_manual_overrides: true
```

全項目の値域・非負・日数昇順・weights合計1・候補数上限を検証。無効値は既存の設定検証と同様に明確なエラー表示。後から最適化の重み変更をする場合はモデルバージョンを保存し、既に確定した当日計画は変更しない。

## 11. システム構成・公開API
`jp.pirijuggler.paper.hall` に追加:
- `HallDayClock`: 日付判定。テストでClock固定可。
- `HallStore`: v5 migration、day/policy/decisionsの原子的永続化、金銭ledgerの冪等更新。
- `HallLedgerRecorder`: REAL/SIMを厳密に区別した機種別累積、現金受払イベント。
- `HallForecastEngine`: 副作用なし、機種別設定性能キャッシュ・稼働行動モデル・分布予測。
- `HallLayoutResolver`: 物理列・角1/角2・並び検出。
- `HallPolicyEngine`: 28〜84日テーマの生成/改訂、運営実績からの改善。
- `HallSettingOptimizer`: 64配置生成→予測→最大1000局所探索→評価根拠出力。
- `HallOverrideService`: 手動pin/一日指定/profile対応と競合解決。
- `HallOrchestrator`: 営業日開始/終了、適用保留/再試行、状態遷移・通知・障害停止。
- `HallCommand`: 上記コマンド/権限制御。
- `HallAuditService`: 決定時根拠、予測モデル版、実績・誤差、警告の追跡。

主要レコード:
`HallDecision(day,machineId,oldSetting,proposedSetting,reasonCodes,scoreBreakdown,forecast,seed,policyIds,applyStatus)`
`HallForecast(cashIn,cashOut,gross,estimatedLiability,variance,downsideQuantile,quality)`
`HallDayPlan(day,decisions,policies,manualOverrides,targets,configHash,modelVersion)`
`HallEvidence(realGames,realBet,realPayout,actualReturnRate,forecastReturnRate,quality)`。

DBアクセスは既存DB executor、重いforecastは既存simulator executor又は専用bounded worker、Bukkit/Fabricへの通知はメインスレッド。DB更新に重い計算を保持したままの長時間ロックを掛けない。スナップショット取得→外側計算→データ/リビジョン整合検査→短いDB commit。競合時は同じseedでスナップショットを取り直す。全台適用が途中で失敗しても記録した計画と実際の適用状態がずれないよう、台ごと`PENDING/APPLIED/FAILED`の移行を冪等にする。

## 12. 詳細な完了条件（すべて自動テスト）
**A. マイグレーション**
- 空DBからv5で起動、旧v4に既存台・現金履歴・SAFE/GRACEセッションがある状態から移行、二度の再起動、失敗ロールバック、SQLite integrity_check、既存ゲーム・管理・モバイル互換。旧DBの既存行数/所持資産が変わらないこと。
- 既存プロセスperiod単位グラフ/データランプと新hallDayの差を明示的に検証。

**B. 日付・冪等**
- JST 03:59/04:00、月末/年末、サーバー停止3日、同日2回再起動、Plugin reload、途中クラッシュ後再開。1日計画は一つ、二重設定履歴・金銭二重計上なし。
- 同一snapshot+seed+modelVersionなら完全同一計画。翌日は別seed。予測RNG使用で実プレイ乱数が変わらないこと。

**C. ロック・資産**
- ACTIVE、SUSPENDED_GRACE、SKILL_STOP未完、GODストック/天国/継続、未完BONUS、BET済未LEVERで04:00越え。全て旧設定・資産・セッション保持。空き次第最新日の設定へ一度だけ切り替える。手動設定もbusy中は拒否。

**D. 会計**
- 46枚/1000の貸出、52枚/1000の景品交換、同じメダルの再BET10回、追加貸出、持越し、未換金、PROFIT_WITHDRAW、PACHINKO_LOAN、失敗/不明Vault取引。正しい`REALIZED_GROSS`かつ`estimatedLiability`は推定値として明示。realとsimの分離、旧未検証期間の隔離。
- 実ゲーム10万回/シミュレーション10万回でsimulationが現金実績を1通貨単位も変えないこと。

**E. 設定・店読み**
- 角1/角2(列長3,4,5)、斜め/異facing/複数島、末尾7、ゾロ目、日付連動、2/3台並び、機種優遇、据え置き、設定上げ、配置移動、台増減、0台/1台。根拠は決定時ログと一致。
- 同一条件の多数営業日で主方針候補が非対象候補より投入選好される統計テストを固定seed・複数seedで検証。必ず毎回当てられる固定設定パターンにもならない。高設定の割合は固定値に張り付かない。
- 設定6が不発の翌日も抽選確率が変更されていない。予測用モデルが元抽選に副作用を与えない。

**F. 経営・手動**
- 利益不足日・過剰日・来客0・月間赤字・大幅稼働変化で日次設定決定が停止しない。期待利益と実現利益を混同しない。
- pin/auto=false/手動当日設定/次日profile/特定日手動ルール/粗利overrideの優先順位と、競合時の拒否、期限切れ時の自動復帰。
- 非OPが現在の設定・方針・計画・シードを見られない。終了した日の履歴と根拠は閲覧できる。DB失敗時は前の有効設定を維持。

**G. ランタイム**
- Java21 / Paper1.21 / Fabric現行ビルドを全実行。 `./gradlew test`, `./gradlew build`, `./gradlew -PruntimeAcceptance=true build` 相当、および既存runtime acceptanceを回帰実行。単体だけでなく2日以上の時刻制御を含む実サーバー連携試験とログ証跡を残す。
- 設定決定はboundedで既存tickを止めない。最悪時のtimeout/失敗は現設定維持、次周期再試行。

## 13. Codex実装順序（この順にPR内で完了）
1. 既存の仕様・テスト・DBを監査。上書き範囲を `SPEC.md` に記録。schema v4→v5移行と検証を先に追加。
2. `HallDayClock`, `HallStore`, REAL/SIM台別集計、金銭ledgerの冪等処理と単体テスト。
3. `HallForecastEngine`のread-only抽選アダプタ、全機種状態依存キャッシュ、交換/再投資モデルとテスト。既存`/piri sim`から完全隔離。
4. `HallLayoutResolver`, `HallPolicyEngine`, `HallSettingOptimizer`を実装。固定seedによるgoldenテスト。
5. `HallOrchestrator`を `MachineService/PiriDatabase/AdminStore`へ安全統合。JVM再起動時旧profileとの二重配分を消し、04:00で一度だけ更新。legacy event profileと手動介入を統合。
6. コマンド、履歴公開、根拠監査、品質/障害表示、ConfigValidationを完成。
7. 全回帰・実ランタイム・長期の統計的検証を完了し、仕様差分と証跡を残す。

作業中の設計判断で未規定事項に遭遇した場合: (a) プレイヤー資産/抽選の保護、(b) 実績データの真正性、(c) 自動運営の継続、(d) 店読みの説明可能性、(e) 既存互換の順で選び、選択とテストをPRに記録。**この文書の機能を「一部だけ実装済み」で完成扱いしない。**

## 14. Codexへ渡す実装指示（コピー可）

**作業ブランチ**: GitHubの `feature/hall-autonomy-spec-v1` をチェックアウトして実装を始めること。mainには本書・定義ロック・DDLがまだないため、main単独から着手してはいけない。実装コミットと検証証跡は別の実装ブランチに作成してPRを出し、本仕様ブランチの3文書を必ず含めること。
> `docs/HALL_AUTONOMY_IMPLEMENTATION_V1.md`、`docs/HALL_AUTONOMY_DEFINITION_LOCK_V1.md`、`docs/HALL_SCHEMA_V5_REFERENCE.sql`を順に実装仕様として読み、リポジトリを監査してからPaper側へ1店舗の完全自律設定投入・粗利/稼働予測・店読み方針・手動介入を実装してください。現行`SPEC.md`の既存仕様を守りつつ、明記された§70–72の自動設定については本書を優先。v4→v5無破壊移行、ゲーム状態保護、REAL/SIM分離、04:00日次処理の冪等性、各機種実際の機械割/状態に基づいたread-only予測、確定時根拠ログを必須とします。実装・テスト・ビルド・Paper/Fabricランタイム検証まで一貫して行い、確認結果と変更ファイルを提示してください。

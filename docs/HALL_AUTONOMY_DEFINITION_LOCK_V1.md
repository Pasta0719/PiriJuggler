# HALL_AUTONOMY_DEFINITION_LOCK_V1.md — 実装解釈固定契約 v1.1

> **必ず先に読む**: `docs/HALL_AUTONOMY_IMPLEMENTATION_V1.md` の補完契約。曖昧語・任意選択を機械的な仕様へ変換する。本書の精密な式・条件は、親仕様にある「例」「程度」「など」「適切に」「必要なら」「選べる」より優先。原ゲームの抽選・状態遷移は `SPEC.md` と現行エンジンを優先。**仕様にない挙動を「なんとなく」追加しない。**

## 1. 入出力と初期化

- 対象ホールは常に `hall_id=1`。ホールごとの設定ファイル・複数店舗管理画面は作らない。
- 対象台: `MachineType` が実行可能なゲームエンジンとforecast adapterを持ち、`deleted=false, enabled=true`である台。自動設定の候補は更に `autoSetting=true`かつ当日の台別PINが無い台。disabled/deleted台は一切変更しない。
- 列挙型は `JUGGLER, JUGGLER_GOD, JUGGLER_GOD_EXTREME, OKIDOKI, GOD, DISC, SKILL_STOP, PACHINKO`。旧コードで未実装の機種や未登録のエンジン/forecast adapterは `UNMODELLED`、現設定を維持、監査ログに記録。既存の遊技可能全エンジンにはread-only adapterを実装すること。単一のJUGGLER機械割表で全機種を推定しない。
- `hall.enabled=true`をデフォルトとし、既存configに`hall`が無い時は全デフォルトが有効。手動でOFFにした場合、**現在設定の維持**のみを行い、旧固定profileを自動代入しない。再ONした次の営業日から計画を再開。同日の未確定初期化だけはその日に計画してよい。
- インストール・更新で既存の設定/DB/遊技メダルをリセットしない。初回計画の対象は「その日の未確定・安全に変更できる台」のみ。

## 2. 用語の固定

| 用語 | 定義 |
| --- | --- |
| `hallDay` | JSTの日時から4時間引いたLocalDate。04:00に切替 |
| `business_period` | 既存のJVMプロセス単位period。hallDayとは別物 |
| `realBet` | 実セッションで確定したBET枚数/玉数の合計 |
| `realPayout` | 実セッションで確定した払出枚数/玉数の合計 |
| `realGames` | 実セッションで確定したゲーム数。従来statsのnormalSpinsとの重複加算禁止 |
| `actualReturnRate` | `realBet>0 ? realPayout/realBet : null` |
| `REALIZED_GROSS` | 実入金（APPLIED貸出）-実出金（APPLIED景品→Vault） |
| `expectedGross` | 既存機械抽選に基づく遊技・貸出・交換の予測純入金額 |
| `estimatedLiability` | 未交換メダル等の推定将来交換額。**現金実績ではない** |
| `highSetting` | 設定4/5/6 |
| `policy` | 設定候補に重みを与える、投入前に確定した28〜84日継続テーマ |
| `dayPlan` | 当該hallDayに1回だけ生成する台別plannedSettingの集合 |
| `proposedSetting` | 計画上の設定。busy台では現在設定と異なっていてよい |
| `appliedSetting` | 実機に適用が完了した設定。適用していない日はNULL、差異をUI表示 |
| `confidence` | 予測サンプル数・95%信頼区間・品質区分の組 |
| `publicHistory` | CLOSED/NO_ACTIVITYになった日付の、設定結果・実績・投入時の説明だけ |
| `manualOverride` | 管理者が明示した指示。既定曜日normal等は指示ではない |

## 3. データ品質・明確な降格
- 品質区分は `REAL_VERIFIED`, `FORECAST_ESTIMATED`, `LEGACY_UNVERIFIED`, `UNMODELLED`, `UNKNOWN` の5種。実測ゲーム統計は`REAL_VERIFIED`のみを方針学習に用いる。過去の `/piri sim` 混入が判別できない期間は**必ず** `LEGACY_UNVERIFIED`。推測してREALに昇格させない。
- 未回収の実金銭取引は `UNKNOWN`。Vault外部コールの`CALL_STARTED/REVIEW_REQUIRED`を推定で `APPLIED`にしてはいけない。
- 予測できない台は`UNMODELLED`として現設定据置きし、残りの台だけ最適化する。既存で遊技可能なのにadapterが欠けた状態を**完成としない**。
- 台数0/1や稼働0日にも営業日の生成は成功する。1台では角・並び法則は不適用。1日すべてUNMODELLEDでもその日の計画は成立し、既存設定を維持。
- オーナーが旧`/piri sim`を実行しても、以後のhall REALカウンタは増えない。旧「公開データランプにシミュレーションが載る」現行挙動は別機能として維持可能。

## 4. 日次更新・同時実行の一意性
1. `ensureDay(now)`は1分ごと・起動完了直後・台がfreeになった直後に要求。DB executor上で単一フライトを保証し、重複要求は既存実行へ合流。別スレッドで同じ日を同時生成しない。
2. 現在日より前のOPEN日をCLOSED化。該当日にゲーム実績がなく現金イベントもなければNO_ACTIVITY。**終了時刻を過去に捏造しない**、境界のUTCミリ秒を`closed_at`に採用。
3. 当日`hall_days(day)`が存在すれば新しいseed・policies・decisionsを生成しない。ただしPENDING適用は再試行。実際の資産・ロック状態を再検証してから適用。
4. 生成前に`hall_days`、当日の台別計画・policyRevision・seedを単一SQLite transactionで確定。算出中に実ゲームが進む場合は`snapshotRevision`を再取得し、最大3回再計算。3回とも競合した場合は当日をFAILEDとして既存設定維持、次のチェックで再試行。FAILEDレコードとseedは残して同じseedで再計算。
5. 生成済みの同日計画は編集禁止。後から管理者がsetting/pinを変えた場合は元の計画を上書きせず、`superseded_by_manual_at`と別のoverride記録を追加。public historyは**最終的にその日に実際に使われた設定と設定変更時刻**を、必要なら複数エントリで表示。
6. 設定を変える前に`pendingMachines`、`state.busy`、セッションのACTIVE/GRACE、内部ボーナス/未完状態を既存機械エンジンで再確認。未確認ならPENDINGを維持。強制清算は**禁止**。適用できた瞬間に`setting_history`、`hall_machine_days.applied_setting`、`apply_status`を同じSQLite transactionに記録。
7. 途中クラッシュ時: DBにAPPLIEDがあるが実機キャッシュが古い場合はDBから再読込、PENDINGだが設定が既に同値なら履歴重複を作らずAPPLIEDへ修復する。DBの`machine.setting`を正とする。書込失敗時は不完全な新設定をクライアントへ配信しない。
8. オンラインのプレイヤーがいるかどうかと営業開始の有無は無関係。リアル稼働ゼロなら全候補が同じ見込みになりうるが、自動計画を生成・維持する。

### 04:00直後の最初の遊技/経済イベントとの競合

- 04:00を越えた最初のゲームやVault APPLIEDがスケジュールの1分間隔のensureDayより先に到着しても、その処理を欠落させない。DB書込側が対象hallDay未存在を検知した場合、同じDB executor上で当日行を`PLANNING`として最小作成（既存secretと日付から同一seed、modelVersion/configHash、当日初期目標を使用）し、当該実イベントを書き込んでから後続の通常ensureDayが残りの決定計画を補完する。PLANNING作成は`INSERT ... ON CONFLICT DO NOTHING`で冪等。
- 計画作成はその日の開始後に到着した実イベントを入力スナップショットから除外し、前日までの`CLOSED`実績だけで設定を決める。次の日付の実現粗利・実稼働は、その日設定決定の評価根拠に遡及反映しない。
- Vaultの`APPLIED`化が先にcommitされ、hall ledger書込前にクラッシュした場合、起動時と定期ensureDayで`economy_transactions`のAPPLIED行から存在しないtransaction_idを読み、`updated_at`で本来のhallDayに帰属させて埋める。**後から埋めた日**に計上しない。元statusがREVIEW_REQUIRED等なら自動でAPPLIEDにしない。
- 経済イベントの日付の外部キーを満たすため、過去日の穴を補填する場合も、日付行の最小作成を行う。過去日の予測や設定計画は捏造しない。実績だけ復元する行は`NO_ACTIVITY`でなく`CLOSED`にし、`data_quality=LEGACY_UNVERIFIED`に設定する。
- 同じ時刻帯の複数トランザクションはtransaction_idによりちょうど一回計上、台別realBet/realPayoutも`hall_game_events`を正として再集計できること。

## 5. 分布・乱数・再現性
- `hall_seed_secret`を初回移行で暗号学的乱数256bitとして一度発行し、metadataに保存（プレイヤー非公開）。`seed(day)=first64bits(SHA-256(secret || UTF8(day) || UTF8(policyRevision) || UTF8(modelVersion)))`、byte orderはbig-endian。シードはhall_daysにも保存。シードと秘密文字列を公開履歴/APIに返さない。
- 擬似乱数はJava `SplittableRandom(seed)`、乱数消費順序は`machine_id`昇順、候補index昇順、policy_id昇順。細部の再現性テストは同一Java21/モデル版を基準。
- 初回設定事前分布は既存 `events.profiles.normal.distribution` を `[55,25,12,5,2,1]`（settings1..6の重み）として利用。ただし管理者が設定を変えていればその最新の分布を読み、その日確定後はsnapshotとconfigHashを保存。通常日は固定比率を**強制しない**。
- 自律policyが台に一致した場合、`s>=4`に対する設定重みを `multiplier=1.7+0.8*strength`、`strength ∈[0,1]`とする。同時policyの倍率は積をとり上限3.0、非対象台は1.0。これを候補生成に用い、配分最適化後の設定6率を固定しない。
- **候補0〜47**はpolicyに沿う重み、**48〜63**はpolicy倍率を無効化した探索候補。固定台を先に埋める。同じ候補が出たら最大4096回再抽選。配置空間が64種類未満なら生成可能なユニーク集合で打ち切り、監査ログに候補数を記録する。
- 新しい日付が同じseedになる確率は極小でも0ではないため、翌日seedが前日と一致した場合だけ `hash(... || byte(1))`で再導出する。
- 候補と比較対象は固定済台も含めるが、最適化による変更はeligible台のみ。

## 6. 予測器（端末ゲームへの副作用ゼロ）
**入力**: `HallForecastInput(day, machineId, type, setting, runtimeSnapshot, configHash, modelVersion, observedHistory, seed)`。
**出力**: `HallForecast(cashIn,cashOut,gross,estimatedLiability,variance,p05Gross,predGames,predBet,predPayout,quality,samples)`。

### 6.1 既存ゲームエンジン対応
- 実機の`MachineType`ごとに**pure/read-only** forecast adapter を作る。シミュレートした`GameTransition`のbet,payout,normalSpinsと各機種固有のBONUS/ストック状態を使用する。元DB・実機のRNG・プレイヤー/機械stateを絶対に更新しない。
- `JUGGLER`は通常RoleWeightsとリプレイ/ボーナス、`JUGGLER_GOD`/ `EXTREME`は固有の小役・ストック・天国・GOD挙動、`GOD`はGOD runtime、`SKILL_STOP`は設定別役抽選と目押し成功率、`PACHINKO`は玉貸/賞球・RUSH継続を独立モデルとする。
- 目押し成功率は過去実プレイに実測があるならその推定値、無ければ**50%**とする。継続/やめ時の実測がなければ下記6.2のデフォルトを利用。ゲームエンジンが公開していない内部値を適当に創作してはいけない。
- `OKIDOKI`, `DISC`はエンジン未登録のままなら`UNMODELLED`。将来ゲームエンジン導入時、必ず同時にforecast adapterを実装。
- Stateに依存するモードは現在の機械runtimeをコピーして開始し、GOD/ボーナスストックを日付変更でリセットしない。DBの現物には書込禁止。観測不能な状態はUNMODELLEDに降格。

### 6.2 日別需要（全員プレイヤーは実在した分のみ実績）
- 実績7/28/90日平均`dailyRealGames`を算出（計測可能な営業日は0Gも含める）。窓の実測日数が0なら`baseDemand=300G/day`。1日以上あれば窓内実測平均の加重平均（有効な窓の重みを再正規化）を`empiricalDemand`、`n`を有効な観測日数とし`baseDemand=(n*empiricalDemand+14*300)/(n+14)`。台に実績が無い場合は同機種の結果を先に使い、それも無ければ300。重み50/35/15は二重カウントを許容する平滑化係数であり累計数値を足す意味ではない。
- 設定による見込み稼働の変化は `predGames=round(clamp(baseDemand*(1+0.03*(setting-3)), 0, 3000))`。高設定の客への訴求を模した近似であり抽選挙動ではない。日ごとの曜日/特定日効果は、実測された同条件の平均比から `[0.8,1.5]`にクリップした倍率を掛ける。サンプル5日未満は倍率1.0。
- 0台の店舗は全指標0。台の休止日は実測稼働0と数えない。過去シミュレーションだけの日は実測がなく未知として扱う。

### 6.3 投資・交換の行動（初期モデルを固定）
- 1人の仮想セッションの目安は300G。台ごとの`predGames`を300G以下のセッションに分割し、最後のみ端数Gとする。0Gならセッション0。
- 各セッションは`initialCarryMedals`が不明なら0枚開始。最初のBETに必要な枚数/玉数が無い時は実機`economy.loan_medals`枚を`economy.loan_amount`で借りたと予測。以後同じ規則で足りない分だけ追加貸出。内部リプレイ・ボーナスBET・PACHINKO球貸は実ゲームのルールに準拠。
- 終了時に保有枚数のうち **60%のセッション**が景品へ換金に進むものとし、各セッションで`uniform<0.60`により決定。交換先は既存景品の`medal_cost`と`vault_value`に従って、最大価値を実現する合法の整数個数組合せを選ぶ（同価値なら大→中→小の個数が多い組合せを選ぶ）。交換しないメダル/残りは`estimatedLiability`へ景品表の最大可換価値で推定。実際のプライズ外部コールは一切しない。
- 実セッションの交換率が28日で**30件以上**観測されれば`pCashout=(cashoutCount+12)/(sessionCount+20)`に切替（Beta(12,8)事前分布）。閾値未満は0.60。閾値到達で実測分へ徐々に寄せる。
- 各候補の現金予測は台ごと128試行し、全台合計の`cashIn`, `cashOut`, `gross`平均および分散・5%下方分位を採用。台のGOD等低確率イベントは100,000G上限の性能サンプル分布を保持して使用。各候補ごとに純ゲーム抽選をフル再計算せず、同じseedで保存された標本分布を比較する（共通乱数方式）。
- 遊技者の持ちメダルの正確な全店在庫が存在しないため、`initialCarryMedals=0`は**予測の仮定**。出玉実績や売上実績として記録しない。予測にはこの系統誤差があるとUI表示。
- 予測の信頼区間は128試行の標本分布から5%/95%分位を使用。単一状態しかサンプルできない・非数・overflowの場合は`UNMODELLED`で維持。

## 7. 固定スコア式・探索手順
対象配置`C`について:
1. `profitFit = 1 - min(1, abs(predGross - dailyTarget)/max(1000,abs(dailyTarget)))`
2. `traffic = clamp(predGamesTotal / max(1,1.25*baselineGamesTotal),0,1)`。baselineGamesTotalは全eligible台を現設定に保った場合の予測G。
3. `pattern = policyMatchHigh / max(1, eligibleHighCount)`。`policyMatchHigh`=高設定になったeligible台のうち投入**前**に有効な主方針に一致する台数。高設定0台ならpattern=0。ただし主方針が0件/適用候補0件ならpattern=1。
4. `continuity = 1 - Σ(abs(newSetting-oldSetting)/5)/max(1,eligibleCount)`（予測上の設定を比較。前日変更自体がローテーション方針なら該当台を計算から除外）。
5. `risk = 1 - clamp(max(0, -p05Gross)/max(1000,predCashIn),0,1)`。
**総合** `score = .40*profitFit + .25*traffic + .20*pattern + .10*continuity + .05*risk`。五項すべて[0,1]の絶対値であり、候補集合が変わっても再正規化しない。forecast未知で評価不能な台は固定扱いにして関連分母から除外。目標粗利ゼロでもprofitFitを計算できる。tieはスコア差`<=1e-12`、その際は設定配列（machine_id昇順）の辞書式順序が小さい方を採用、全同値なら候補indexが小さい方。

**局所探索**: 最良上位8配置を対象に、1配置最大1000試行。試行回数カウントは「改善しなかった手」も含む。1手で(1)ランダム台の設定±1（範囲1..6）、または(2)2台の設定交換を等確率で選択。手動/disabled/locked固定は変更対象に選ばない。`RUN変更`は(2)の交換で実現。改善した場合のみ置換。各候補の新スコアは同じ式で再計算。全候補空なら既存設定をそのまま計画とする。候補が少ない場合は全候補を使う。上位8を各1000試行してもメインスレッドを占有しない。

## 8. ポリシー生成・変更条件
- 初回に候補可能台が3台未満なら`NUMBER_SUFFIX`のみ（存在する末尾）を1件、3台以上なら `DATE_TAIL`, `NUMBER_SUFFIX`, `CORNER_2`, `MACHINE_TYPE`, `RUN_2`, `RAISE`, `KEEP` から**データ上成立する**種類を最大2件選ぶ。10台以上なら3件選ぶ。選択はseedで決定、同一条件種類の重複選択は禁止。
- 各policysの`strength`初期値0.5、適用倍率2.1。パラメータ（末尾数字、対象曜日、対象機種、対象島等）は配置実態からのみ選ぶ（存在しない機種や島は候補にしない）。有効期間28日、最大84日。
- 7日ごとに観測可能な稼働差分`lift=(observedGames - expectedGamesAtOldSettings)/max(1,expectedGamesAtOldSettings)`を求める。対象台で`REAL_VERIFIED`の観測ゲームが合計**5000G以上**かつ活動日**7日以上**ある場合だけ政策効果を評価。それ以外は`INSUFFICIENT_DATA`で継続。
- 観測が十分なら`strength=clamp(strength+0.1*clamp(lift,-1,1),0.1,1.0)`。期間28日未満は終了禁止。28日経過後、`lift< -0.10`が2回連続なら方針を終了し、新しい候補へ差替。84日で無条件終了→新方針生成。
- 店読みの確率的例外は**候補48..63のテーマ外探索**として実現。別の「店の性格」「根拠のない周期」生成は禁止。
- 実出玉率は`realPayout/realBet`として保存し、期待出玉率からの偏差を次回の上げ/据置/移動テーマの対象選定に使う。高設定の不発を補償するように当選率を操作しない。
- 28日ごとの再評価と7日ごとの調整の整合: **7日ごと=強度調整のみ**、**28日目以降=終了/差替可能**。

## 9. 手動ルールの厳密な解釈
- `/piri hall pin <id> <setting> [date|forever]`の省略は**当日限り**、日付指定はそのhallDayの04:00から次日04:00まで、foreverは無期限。過去日を指定したら拒否。
- `/piri hall unpin <id>`は対象台の現在のpinと当日以降の予約pinを解除。解除した当日はその台の現在設定を保ち、**次営業日から**自動に復帰。explicit `auto_setting=false` は自動ONにしない。
- `/piri setting <id> <setting>` と管理画面の変更は当日限定のmanual override。変更時点が04:00より前なら前hallDayに帰属。busy時は拒否。
- `/piri event next <profile>`: **呼び出し時の翌hallDay**に1回だけ適用。指定profileはその日の**明示的・完全な手動配分**とする。既存`StartupProfile.allocate`を独立hall乱数で**1回だけ**実行し、返ったすべての対象台のsettingを当日のハード制約として固定（既存のSUFFIX/RUN/ALL、分布、minSetting6/minSetting5Plusを同じ意味で適用）。経営予測・監査は続けるが設定最適化がこれを変更しない。`auto_setting=false`や当日PINは配分対象から除外し、その分profileの最低台数保証が成立しない場合は残り対象で最大限満たして警告。`events.weekday`のデフォルトnormalは明示指示でない。`special_dates`に日付がある場合と、weekdayがnormal以外の場合だけ同様の完全手動profileを適用。前日profileの自動継続は禁止。
- スコープの優先度: MACHINE_PIN > 当日MANUAL_SETTING > DATE_PROFILE > MACHINE_TYPE > ISLAND > DAY > MARGIN > 自律ポリシー。手動同士の「設定値の直接衝突」は新しい指定を拒否し、上位pinを解除しなければ変更できない。利益目標は設定値指示ではないので他と衝突しない。
- `/piri hall auto off`は当日以降自動配分を停止・設定維持。 `auto on`は次のhallDayから再開。途中の日付で既存計画を上書きしない。
- `/piri hall rule add <kind> <JSON>` の許容kind: `DATE_TAIL`, `WEEKDAY`, `NUMBER_SUFFIX`, `CORNER_1`, `CORNER_2`, `RUN_2`, `RUN_3`, `MACHINE_TYPE`, `KEEP`, `RAISE`。JSONは `{"target":"7","minSetting":4,"startDay":"2026-11-07","endDay":"2026-12-07"}` 型で、`target`はkindに応じて0..9の数字/曜日MONDAY..SUNDAY/機種enum/島ID/台番号、`minSetting`は1..6、日はISO8601 date、`startDay<=endDay`。対象存在・指定期間重複・台別pinとの矛盾を検証。未知キー・非型一致は拒否。 `KEEP/RAISE` はそれぞれ前日据え置き/前日より1以上上げるという条件、最終設定6を超えない。
- `/piri hall target <marginPercent>`は整数/小数%で`0..100`、入力例`12`→0.12。 `target auto`で次営業日から自動化。手動変更は**翌営業日以降**に反映。無期限。
- `/piri hall layout set`の`position`は島の通し番号1..9999、同じ島の同位置二重指定は拒否。`layout clear`は当日配置を壊さず翌日から自動位置判定。書込みは監査。
- `preview`はDB・secret・PRNG等を変更しない。既存seedがあればそれを利用、未確定日なら日付と現在保存済secretから導出する仮シードを用いる（生成済計画ではないことを表示）。前日から任意の日へのプレビューは、未知の未来実績を補間せず現在実績の静止スナップショットで比較。
- 非OPには現在のpolicy/secret/計画/当日設定および未終了日の正解を開示しない。public explain/historyはCLOSED日だけ。

## 9A. ポリシー型の完全定義（Codexが意味を推測しない）

本節の型名・一致条件・条件成立の単位が唯一の正解。自動テーマも手動ルールも同じmatch関数を使う。台は当日04:00時点のMachineと有効なlayout overrideを評価する。高設定誘導は一致台にのみ適用、非一致台を禁止する意味ではない。

| kind | 必須target | match(台, day) の意味 |
| --- | --- | --- |
| `DATE_TAIL` | "0"〜"9" | 当日の日付の末尾数字（dayOfMonth%10）がtargetと一致した日の**全対象台** |
| `WEEKDAY` | "MONDAY"〜"SUNDAY" | 当日のJST曜日がtargetと一致した日の**全対象台** |
| `NUMBER_SUFFIX` | "0"〜"9" | machine_id % 10 がtargetに一致する台 |
| `ZOROME` | "*" | 台番号の10進表記が2桁以上かつ**全桁同一**の台（11, 222, 3333等） |
| `DAY_NUMBER_MATCH` | "*" | machine_id % 10 == dayOfMonth % 10 |
| `CORNER_1` | "*" または島ID | 対象列の端から1番目。島ID指定ならその島に限定 |
| `CORNER_2` | "*" または島ID | 対象列の端から2番目。島ID指定ならその島に限定 |
| `RUN_2` | "*" または島ID | 同一島の隣接連続2台に設定4以上を優先配置する**組合せ条件** |
| `RUN_3` | "*" または島ID | 同一島の隣接連続3台に設定4以上を優先配置する**組合せ条件** |
| `MACHINE_TYPE` | MachineType enum名 | その機種の全対象台 |
| `KEEP` | "*" またはMachineType enum名 | 前hallDayに適用済みの設定がある台を、その設定と同値にする組合せ条件 |
| `RAISE` | "*" またはMachineType enum名 | 前hallDay適用設定が1〜5の台を、それより**1以上高い設定**にする条件 |

- `RUN_2/3`は単体台のmatch倍率ではなく、該当連続区間を候補として生成・局所探索する際に方針達成スコアへ寄与する。連続とは同島position連番で、欠番やdisabled/deletedを跨がない。
- `KEEP/RAISE`は台別旧設定が観測できる場合のみ有効。前日未適用/UNKNOWNならmatchなし。正しく比較可能な台が0なら方針の候補に入れない。
- 手動 rule JSON は `{"target":"7","minSetting":4,"startDay":"2026-11-07","endDay":"2026-12-07"}` を基本形とする。`target`は上表の形式に限定、`minSetting`省略時4、`startDay`省略時は次hallDay、`endDay`省略時は無期限、追加キーは拒否。kind`KEEP`の`minSetting`は意味を持たず指定を拒否、`RAISE`では最低設定との最大値を採用。`RUN_2/3`では区間の全台に`minSetting`を適用。`ZOROME/DAY_NUMBER_MATCH`は`target="*"`だけ許可。
- 末尾テーマは高設定投入を**確定**するルールではない。一方、管理者の手動ruleの`minSetting`は対象台に対する**最低設定制約**として最適化の前に固定する（複数手動制約がある場合は最大値。ただしより上位のPINと衝突する場合はコマンドを拒否）。手動固定で粗利が目標に届かなくても変更しない。
- 自動テーマの個数は eligible台`0→0,1..2→1,3..9→2,10以上→3`。候補型は上表から「台/日付条件が今後28日の中で少なくとも一度成立」するものだけ選ぶ。policy_id昇順で重複kindを除き、`SplittableRandom`により候補配列のインデックスを均等抽選（候補配列はkind名→target昇順の辞書順で安定ソート）。選択した候補は28日間維持。候補不足なら存在数で打ち切る。
- 28日以後の差替時は、同時に生きているkindと同じものは選ばない。候補が存在しない場合は方針を0件にしてもシステムを停止しない。
- 機種を対象にする場合、設定によって抽選分布に変化がない`MachineType`は高設定誘導候補にしない。**現行PACHINKOゲームはmachine.settingを抽選に参照しない**ため、自動設定変更対象ではない（現設定維持、営業収支予測/分析対象にはなる）。GODは設定値を参照している。OKIDOKI/DISCは遊技エンジン未登録なら対象外。将来PACHINKOに設定差を実装した場合のみ別バージョンで対象へ追加する。

## 9B. パチンコとメダルの異なる集計経路

- `GameTransition.bet/payout`だけで機種全体のBET/払出を集計できると仮定しない。現行`PachinkoGameEngine`には遷移のbet/payoutが0で、玉の消費/賞球が`PachinkoRuntime`側の変化として表現される経路がある。パチンコ用`HallPachinkoAccountingAdapter`で前後runtimeの実玉消費・賞球イベントを、**成立した各アクションのみに対して**抽出し`hall_game_events`へ記録する。台別現金貸出はPACHINKO_LOANからのみ。
- `GOD`は通常ゲームのLEVER時BETと結果完了時払出が別transitionになりうる。`hall_game_events`は状態遷移ごとの受払`bet,payout`を丸ごと記録し、`game_delta=0`のBET処理も含める。1アクション=1 transaction_idの冪等性を利用し、BETと払出を二重計上しない。
- 台別の「当日実出玉率」は種別ごとのBET/払出実単位で求める。メダル枚数とパチンコ玉を**合算して率を算出しない**。店舗の現金収支のみ全機種横断集計する。
- シミュレーションは`hall_game_events(origin='REAL')`を作ってはならない。検証用SIM行は専用テストDBかテスト専用明示フラグ下に限定する。

## 10. 論理スキーマ上の追加拘束
親仕様の表名・フィールド名を必須とし、少なくとも次のDDL条件を満たす:
- `hall_days.day TEXT PRIMARY KEY`、`status TEXT NOT NULL CHECK IN('PLANNING','OPEN','CLOSED','NO_ACTIVITY','FAILED')`、各 monetary は`TEXT`で非NULLのcanonical decimal（小数点以下2桁固定）、各計測counterは`INTEGER`で>=0。未知/未予測にはNULLを使い、0と混ぜない。
- `hall_machine_days PRIMARY KEY(day,machine_id)`、`CHECK(planned_setting BETWEEN 1 AND 6)`（不明な機種や非対象台も現設定をplannedとして入れる）、`applied_setting`はNULL許可、`CHECK(apply_status IN('PENDING','APPLIED','SKIPPED','FAILED'))`。
- `hall_setting_decisions PRIMARY KEY(day,machine_id)`、設定に1〜6のCHECK、FOREIGN KEY(day,machine_id)→hall_machine_days。未採用候補の理由/候補IDは`reasons_json`に記録。SQL insert/updateは適用/理由/履歴で二重計上不可。
- `hall_economy_events.transaction_id TEXT PRIMARY KEY`、operationは`LOAN,PACHINKO_LOAN,PRIZE_TO_VAULT`限定。`occurred_at`はAPPLIEDに遷移した確定時刻、`day`はその時刻のhallDay。amountDecimalはVault通貨で小数2位HALF_UPへ丸めた正の値。DBの元取引が二重になってもhall eventは一度きり。
- `hall_game_events`を追加し `transaction_id TEXT PRIMARY KEY, hall_day TEXT NOT NULL, machine_id INTEGER NOT NULL, machine_type TEXT NOT NULL, setting INTEGER NOT NULL, bet INTEGER NOT NULL CHECK(bet>=0), payout INTEGER NOT NULL CHECK(payout>=0), game_delta INTEGER NOT NULL CHECK(game_delta>=0), origin TEXT NOT NULL CHECK(origin IN('REAL','SIMULATED')), committed_at INTEGER NOT NULL`。**実ゲームのcommitと同じDB transaction**で`REAL`を記録。新hall実績は`origin='REAL'`のみ集約。ゲーム差分を手作業で推測しない。
- `hall_manual_overrides`は `override_id`一意、scope/date/actor/createdAt/revokedAtを保存。`hall_audit_log`は日付順のインデックスを張る。対応する逆参照FKは既存表を壊さない範囲で作成。
- `hall_layout_overrides`を追加: `machine_id INTEGER PRIMARY KEY, island_id TEXT NOT NULL, position INTEGER NOT NULL CHECK(position BETWEEN 1 AND 9999), starts_day TEXT NOT NULL, ended_day TEXT, UNIQUE(island_id,position,starts_day)`。配置の変更履歴を失わないため実テーブルは複合キー`(machine_id,starts_day)`も許容するが、同時有効な島位置衝突は必ず禁止。
- `hall_settings`またはmetadataに`hall_seed_secret`, `auto_enabled`, `manual_target_margin`を保持。金額は全てcanonical decimal文字列、浮動小数で計上しない。旧実金銭取引のfloatは `BigDecimal.valueOf(double)`から変換しHALF_UPで小数2位へ丸め、丸め前値と元IDを監査に保持。
- v4→v5の失敗時は明示エラーと元DB保全。起動中のmigrationはゲーム受付前。バックアップはSQLiteのBackup API/チェックポイント等で整合を保ち、WALのファイルコピー単体をバックアップと称しない。
- v5起動中のplugin reloadを含めバージョン二重昇格なし。旧`schema-v4.sql`と既存モデルとの互換性を維持。

## 11. 実装時の判断不要ルール
- 1つの仕様が曖昧なら**既存ゲーム・資産を保護する最も保守的な挙動**（現設定維持、監査ログ、再試行）を取る。勝手な高設定大量投入や設定1へのリセットをしない。
- 最適化予測が実装困難だからという理由で機械割を全機種一律にしたり、実ログと混ぜたり、疑似データを実績に入れたりすることは禁止。
- 現行ソースとの仕様矛盾はこの文書の補完範囲→親仕様の自律設定範囲→既存SPEC.mdのゲーム不変条件の順に解決。ゲーム本体の変更が必要な場合は必要なアダプタだけを追加し、抽選仕様を変更しない。
- 既存 `/piri sim` は実グラフ更新コマンドとして残しつつ、hallのreal ledgerから除外する。予測は別のpure simulatorでのみ行う。
- すべての`reasonCodes`は配置決定時に固定保存。決定後の実出玉・勝ち負けにより理由を変更しない。

## 12. 受入テストの数値基準（追加）
1. seed+過去実績+configHash+policyRevision同一→決定配列・理由・予測値がbyte単位一致（同Java21 build）。
2. `46枚貸し/1,000円`を10回借り、途中に同じメダルを何回再BETしても貸出記録は**10,000円**のみ。52枚を1,000円景品に交換したら実現粗利は**9,000円**。出玉の払出と現金粗利は別集計。別日の景品交換は**その交換日に出金**。
3. 経済イベント同じtransaction_idを100回再処理しても1件のみ。SIMゲームを10万回流してもhall realGames/realBet/realPayoutおよびrealizedGrossは不変。
4. 11/7 03:59→11/6営業日、11/7 04:00→11/7営業日。04:00を跨いだACTIVE, GRACE, unresolved GOD / BONUSは旧設定を維持し、遊技の完了前に新設定を適用しない。
5. v4→v5移行直前/直後のDB整合性検査を行い、`player_sessions`, `player_wallet`, `medal_tokens`, `machines`, `setting_history`, `business_periods`の既存レコードを破壊しない。
6. 初回から完全自動、全台busy、0台、1台、サーバー3日停止、当日複数起動、同一日にplan途中クラッシュ、手動pin競合、未知機種、手動でOFF、現金0円の全ケースで例外停止せず既存台と資産を保護。


## 13. 最終仕様閉鎖 — 追加の曖昧さを残さない契約

### 13.1 状態遷移と閉店後の遅延反映
- `hall_days.status` の有効遷移は `PLANNING→OPEN→CLOSED`、稼働/入出金が完全に0の既存OPEN日のみ `OPEN→NO_ACTIVITY`、予測生成失敗の `PLANNING→FAILED→PLANNING`（同日再試行）。過去日の遡及復元だけは `CLOSED` を直接生成できる。全てDBトランザクション内で条件付きUPDATE。
- `PLANNING/FAILED`状態で実遊技や金銭APPLIEDが来ても**ゲーム・経済の処理を止めない**。トランザクションと同一DB executorで`hall_day`を先に作り実績を記録。日次プラン未確定台は既存設定で遊技する。計画の生成開始時点で既に当日発生した実績を予測の学習には使わない。
- 04:00に前日を閉じた後で、過去のAPPLIED経済イベントの漏れを再収集した場合、過去`CLOSED`日の実現現金集計は再計算して訂正する。理由ログ/当初計画/シードは不変。監査ログに`LATE_LEDGER_RECONCILIATION`として旧金額、新金額、取引IDを追加する。
- 現金イベント確定日の判定は `economy_transactions` の`status`が`APPLIED`に変わった時刻を元とする。既存DBに専用確定時刻が無い場合、その更新処理が書いた`updated_at`を使用。管理者が後から監査情報を書き足して`updated_at`を書き換える実装は禁止。
- 毎日の`hall_days.real_cash_in/out/realized_gross`は`hall_economy_events`を集約して再計算できる値であり、原本はイベント行と既存APPLIEDトランザクション。日次カウンタを直接増分更新する場合も冪等キー制約を確認した後に実行。
- `hall_machine_days.applied_setting`は**営業日内の最後に実機へ適用された設定**。途中手動変更があった場合は`setting_history`の時刻付変更履歴と`hall_game_events.setting`を正として出玉率を設定別に集計。日次行だけから「終日同じ設定」を推定しない。
- 金銭イベントにmachine_idが取得できなければ`NULL`。機種・台別現金利益は非対応のため推定配賦もしない。

### 13.2 月間目標の数学的定義
- 月は`hallDay`の年月。たとえば11/1 03:59 JSTの実績は10月の残高に属する。月内の日数`D`は暦月の日数（28〜31）、現在日番号`d`。
- 予測日次貸出`L`は当日の全店128試行の`predictedCashIn`、有効な予測が一つも無ければ0。月間目標額`M = round2(D * L * targetMargin)`。当月累積確定粗利`A = sum(realizedGross for hallDay of this month strictly before today)`。当月の当日目安`T = (M-A)/(D-d+1)`。
- `L=0`なら`M=0,T=0`。データ不足で実現粗利にLEGACY_UNVERIFIEDを含む場合は`A`にその期間を足さず、残期間推定`T = round2(L*targetMargin)`を使い品質`FORECAST_ESTIMATED`で表示する。過去の未確定を確定扱いしない。
- 急な設定変化を防ぐため、`base=round2(L*targetMargin)`として、`base>0`なら`T=clamp(T,0.5*base,1.5*base)`、`base=0`なら`T=0`。日々の`T`が負の場合も0にクリップする。Tは「最適化の目安」であり達成保証ではない。
- 7日平滑化は直近7**暦営業日**の有効な生targetMarginの単純平均（データ0日は0.12を入れる）。毎日更新した平滑済の値をさらに平均しない。手動targetは翌日から固定し、平均を適用しない。
- 数値演算は`BigDecimal`、丸めは金額が`scale=2 HALF_UP`、比率は内部計算`MathContext.DECIMAL64`、保存時の表示は小数最大6桁とする。予測専用の乱数・分位計算にdoubleを使うことは許容し、最終金銭出力だけBigDecimalで確定する。

### 13.3 制約の扱いと「設定が入った」の定義
- `PIN`は指示した`setting`そのもの、`minSetting`は下限であって設定値の固定ではない。台別PINとminSettingが両立しないときはルール追加を拒否。台が現在busyでも未来PIN/ルールの登録自体は可能だが、適用はfreeになるまでPENDING。即時設定を試みる`/piri setting`のみbusyなら拒否。
- 既存`auto_setting=false`は手動固定同等の優先制約で、その日の`hall_machine_days`にplanned=currentSettingを記録。後日`auto_setting=true`にした場合も当日中は元設定を維持し翌日から再び自動候補に含める。
- ある台に設定を1回でも適用した事実は`setting_history`へ残す。ただし「計画と同じ設定だったためUPDATE不要」という場合でも当日の`apply_status=APPLIED`を記録し、同じ値の`setting_history`を増やさない。変更なしの理由は`NO_CHANGE`をauditに記録。
- 設定候補で`busy`台は当日plannedの対象には含めるが実機適用不可。日中の実際の`machine.setting`は旧設定である。月間期待粗利予測ではbusy台は**現設定**として評価し、将来いつ解放されるか不明な時間の利益変化を推定しない。free後の再適用は最新のhallDayのみ。

### 13.4 機種別予測の実装判定
- `MachineType`のうち設定が実抽選へ渡される機種だけ設定候補へ参加可能。`PACHINKO`は設定差が無いため投入設定は変更しない。ゲームエンジン未登録の`OKIDOKI/DISC`は未対応、既存設定を維持。これをエラーとしてサーバーを停止しない。
- 既存のゲームエンジンと同じ結果分布を生むread-only forecast adapterは**元のロジックから移植した決定論的純粋関数**または**乱数を外部注入した元コードの純粋な呼び出し**として実装。適切なデータが無い状態を勝手に設定別機械割の創作値で埋めない。
- adapterテストでは機種ごとに同一seed・同一初期状態で最低100,000試行を走らせ、実ゲームの独立RNGサンプルと役/ボーナス/払出の頻度の95%信頼区間が交差すること、状態遷移の不変条件を維持することを確認。照合に落ちたadapterは`UNMODELLED`に降格する。テストで実ゲームに手動のforce roleを注入しない。
- 各台128試行の出玉実現シミュレーションは、(機種,設定,状態hash)ごとに予測G数まで純粋に前進させる。低確率GOD等はサンプル分位から反映し、各候補のスコア比較で同一台の`sampleIndex`に同じシード派生乱数を使用（common random numbers）。試行順序はday→candidateIndex→machineId→sampleIndex昇順。
- 実際の0G、空セッション、換金額0は**ゼロとして記録**する。未実装のadapter・破損した持越し状態・欠測のpayoutは**UNKNOWN/UNMODELLED**に降格しゼロで補完しない。
- `HallForecast`は`predictedCashIn, predictedCashOut, predictedGross`が出玉のばらつきに従う推定値であり、最適化でゼロ未満になる可能性を残す。「設定6だから店が必ず赤字」「設定1だから必ず黒字」と決め打ちしない。

### 13.5 設定投入理由の固定フォーマット
- `reasonCodes`は`POLICY_MATCH, POLICY_RUN, PREVIOUS_KEEP, PREVIOUS_RAISE, TRAFFIC_RECOVERY, MARGIN_TARGET, MANUAL_PIN, MANUAL_RULE, LEGACY_PROFILE, LOCKED_PENDING, UNMODELLED, NO_CHANGE`から選ぶ。複数付与可能。
- `reasons_json`は`{"schemaVersion":1,"reasons":[{"code":"POLICY_MATCH","policyId":"...","evidence":{"kind":"NUMBER_SUFFIX","target":"7","machineId":107}}],"decisionScore":0.123456,"inputCutoffDay":"YYYY-MM-DD","modelVersion":"hall-v1"}`の構造。ポリシーが条件に一致したが設定最適化で不採用なら`rejected_reasons_json`に同じevidenceと`NOT_SELECTED_FOR_TARGET`を追加。変更しない場合は`NO_CHANGE`。
- 項目順は`policyId`→`code`昇順、台配列は`machineId`昇順、JSONオブジェクトキーは辞書順に整列してUTF-8で永続化。同じ入力とseedに対するbyte-level再現性を満たす。
- 公開回答に見せるのは保存済理由のみ。後日データを元にした後付け推測や稼働結果によるラベル変更は禁止。

### 13.6 コマンドの厳密な失敗と表示
- `/piri hall plan`, `status`, `preview`, `explain`, `history`が結果を生成できない場合は`HALL_NOT_READY`、日付が過去/不正なら`INVALID_DATE`、台番号不在なら`INVALID_MACHINE`、権限不足なら`NO_PERMISSION`、規則競合なら`HALL_CONFLICT`、DB失敗なら`HALL_DB_ERROR`を返す。警告・エラーは同じ理由コードでhall_audit_logとPaper logに保存。
- `/piri hall history [days]`の省略は7日、指定は整数1..90。結果はhallDay降順→machineId昇順。現行日を含めない。OPの`plan [day]`は既に確定した日のみ一覧を表示し、未計画の未来日は`preview`へ誘導する。
- `hall explain`で`apply_status=PENDING`の場合は「予定・未適用」と表示、実際に高設定が入ったと偽らない。入った理由として公開するのは`APPLIED`のみ。当日以外はCLOSED/NO_ACTIVITYになってから非OPへ公開。
- `hall preview`は読み取り専用で既存計画を変えず、未来日の実績を捏造しない。`hall rule add`のJSONはコマンド分割後の残り引数を半角スペースで再結合してパーサーへ渡す。予期しない引数/無効型/範囲超過は設定を一切変更せず失敗表示。


## 14. 決定論・評価母集団・ポリシー達成の固定

### 14.1 バージョン、ハッシュ、シード
- **本仕様のモデルID**は`hall-v1`。設定式/予測器/ランダム消費順序を変える将来リリースでは`hall-v2`へ昇格させ、旧日付の保存計画は再生成しない。
- `hall_seed_secret`はSecureRandomで生成する32byteを**小文字hex64文字**としてmetadataへ保存。seed導出の連結は`hexDecode(secret) || UTF8(day.toString()) || 0x00 || UTF8(Integer.toString(policyRevision)) || 0x00 || UTF8(modelVersion)`、SHA-256の先頭8byteをsigned long big-endianとして取り出す。日付違いでも同値なら再hash時に`0x01`を末尾追加する。
- `config_hash`は最適化・実機抽選に影響する現行設定 `economy`, `prizes`, `probabilities`, `juggler_god`, `juggler_god_extreme`, `premium`, `hall` の値を**辞書順キーのcanonical JSON**に直し、SHA-256 UTF-8を小文字hexで保存。JSONの小数はBigDecimalのstripTrailingZerosを使い、整数は整数表現に統一。音量/GUIなど出玉・経営モデルへ無関係な設定は含めない。
- `state_hash`はforecast対象台の`machine_type`, `machine_runtime_json`（nullなら空文字）をそのまま文字列結合してSHA-256を計算。可能な限りキャッシュの再利用を行うが、状態が変化したときは性能サンプルの再取得を優先し、**古い状態のperformance cacheを無検証で流用しない**。
- `policy_revision`は初期0。有効な自動営業方針集合が初めて作成/終了/入れ替わった**営業日開始時のみ**1加算。7日ごとのstrength更新でも1加算。ただし同じ営業日内の再試行・再起動では増加させない。手動ルールの追加・解除は独立override履歴で追跡し、当日確定済みのseed/計画に遡及しない。
- 保存する`hall_days.snapshot_revision`は台配置・機種/自動設定可否・レイアウト/手動指示・前日までの監査済実績が**変わった際**に増加する非負64bitの楽観ロック用番号。単純な当日プレイ中BETでは増やさない。適用直前の台runtime/セッションのbusy判定は別途都度実施。
- 秘密seedに由来しない公開APIでも同じ計画を再演算できないよう、seed/secretは非OPへ一切表示しない。

### 14.2 法則性の採点対象
- パターンスコアに含める分母`eligibleHighCount`は、「最終候補で**設定4以上かつ変更可能台**」となった台の数。これが0なら主方針が存在する場合pattern=0。対象方針0件ならpattern=1。
- policyMatchHighは各高設定台を**最大1回**数える。次のいずれかを満たせば一致: (a) 数字/日付/機種/角の単台policyがその台に一致、(b) RUN2/3方針で同島の連続して高設定となった区間の構成台、(c) KEEPなら前日最終適用設定と同じ、(d) RAISEなら前日最終適用設定より1以上高い。条件一致の判定は配置確定前に保存済みのpolicyだけを使い、候補の結果に応じて当日のpolicyを変えない。
- **手動PROFILE/PIN/RULEによる高設定は自動法則性の達成率に加算しない。** 自動テーマの採点対象はあくまで自動候補台。手動の最低設定で高設定にした台を「ホールが末尾を読み取らせるために入れた」と説明しない。
- 台番号末尾、角1/2、機種、日付、ゾロ目の個別条件に当たっても**候補選定前にテーマとして存在しない**場合は理由`POLICY_MATCH`を付与しない。単なる偶然一致は答え合わせで「偶然」として扱う。
- pattern以外のトラフィック・粗利項には全台の影響（自動対象外・手動固定・PACHINKO等）を含む。稼働中で変更不可の台は**現設定**の予測寄与だけを算入し、存在しない適用予定設定の利益を先取りしない。
- ローテーションの連続性は「旧設定と違うこと」自体を無条件に高評価しない。`RAISE`・`KEEP`など有効なpolicyと一致する場合のみpatternへ加点。continuityは元設定維持への独立評価。

### 14.3 遊技・金銭とプランの境界条件
- 予測評価には、同一台・同じ設定・同じ状態hashについて**候補を跨いで同一の128セッション標本**を再利用する。全64候補×各台×各設定×各試行で実ゲームを毎回ゼロから再実行しない。全6設定の副作用なし性能標本と、確定した台別予測需要を日別キャッシュして候補差だけ評価する。
- 途中終了した日次PLANNINGを再試行する場合、元seed、modelVersion、configHash、対象日付、手動override基準時点を固定して再計算。変更された新しい台/機種はその日の計画に追加しない（翌日から対象）。ただし計画未生成のPLANNINGで初回スナップショット未確定なら現時点の台を採用する。
- 当日中の新設台は既存createによる初期設定を保持し、`apply_status=SKIPPED`の補助日次行を追加して翌日から計画へ参加。撤去台は計画・既存履歴は削除せずSKIPPEDとして監査記録。機種/場所変更は既存busyガードに従い、その日確定済計画を再生成せず翌日から見直す。
- 期限付き手動PINがbusyで期間中一度も適用できなければ失効後に適用せず、`SKIPPED`と監査理由`EXPIRED_BEFORE_APPLY`を残す。期限が当日終了した時点で旧計画の設定をあとから追って適用しない。
- すべての営業日時刻はUnix epoch milliseconds。内部日付の比較/並びはISO8601文字列（YYYY-MM-DD）。全日付欄は日本時間で書込み、UTCシステム時計でもJST所属を間違えない。



## 15. 監査で検出した最後の計算上の未確定要素を固定

### 15.1 方針シードの循環参照回避
- 当日方針を決める前に`policyRevision`を含む「当日最終seed」を使ってはならない（方針更新でrevisionが変わるため循環する）。
- 営業日`d`の方針更新判定・候補選択は、前日までにDBで確定した`previousPolicyRevision`と`secret`から独立`policySeed(d) = first64bits(SHA256(secretBytes || UTF8("POLICY") || 0x00 || UTF8(d) || 0x00 || UTF8(previousRevision)))`を導出し、`SplittableRandom(policySeed)`で行う。
- 方針更新をDBで一度だけ反映して`newPolicyRevision`を確定した**後**、§14.1の`seed(day)`を生成して日次計画へ使う。再起動時は保存済policyRevision/seedを読み、方針更新を再実行しない。
- 方針開始日は`startDay`を含む。初回28日維持は`startDay`から28日間を意味し、`min_end_day=startDay.plusDays(27)`、`max_end_day=startDay.plusDays(83)`、評価による終了は次の営業日から有効。84日目の終了判定はその日の営業終了後、次の日から差替。現在日のplannedSettingを事後変更しない。
- 7日ごとの評価は`startDay.plusDays(7*k)`（k=1,2,...）の営業日開始時点までに**完了した7日間**を評価し、同日内の実稼働は使わない。単純経過日数で判定し、サーバー停止日も日数に含むが実観測日には含まない。

### 15.2 学習値・政策効果の比較対象
- `expectedGamesAtOldSettings` はポリシー判定対象の台について、評価窓の各営業日開始時点に保存した`HallForecast.predGames`を**「その前営業日の最終実設定で固定した反実仮想」**として再計算した合計。過去の予測構成hash・機種・配置・需要スナップショットは`hall_setting_decisions.forecast_json`内の`baselinePredGames`として投入前に保存する。後日の実測に合わせて再推定しない。
- `observedGames`は同じ台集合・同じ日集合の`REAL_VERIFIED`な`hall_game_events(origin='REAL')`の`game_delta`合計。`lift=(observedGames - baselinePredGames)/max(1,baselinePredGames)`。集計母集団はpolicy条件に合致した日の**変更可能かつ実際に設定適用済の台**のみ。PENDING台・手動強制台・未対応機種・品質不明日を除外。
- `baselinePredGames`の投入前記録がない営業日は評価対象から除外、観測日数にも数えない。`lift`推定に必要な実ゲーム合計>=5000、観測日>=7の双方が揃わない場合は`INSUFFICIENT_DATA`としてstrengthを変えず、28日経過後であっても84日までは維持。
- 方針は実験的に「稼働を増やした」と断定しない。liftは他の要因を含む**相関的な営業成績指標**であり、監査説明は`OBSERVED_LIFT`とする。出玉抽選への補正に使わない。

### 15.3 換金率を学習するためのセッション定義
- `sessionCount`はhall導入後の**実プレイヤーの着席〜離席サイクル**数（source=REAL、player UUID・machine ID・seatBegin timestamp・seatEnd timestampを保存）。サーバー再接続による同一セッション復元は新規着席としてカウントしない。セッションが異なる機種へ移動したときはその台の新サイクル。過去のDBにサイクル終端が無い場合はUNKNOWNとしてカウントしない。
- `cashoutCount`は同じサイクルの離席直後30分以内に、そのプレイヤーの`PRIZE_TO_VAULT(APPLIED)`が初めて確定した件数。複数回の景品換金は1回として数える。30分内に別台を遊技していた場合は最後に離席した台のサイクルへだけ帰属する。同時刻ならmachineId昇順の1件。
- `hall_seat_cycles`を追加する: `cycle_id TEXT PRIMARY KEY, player_uuid TEXT NOT NULL, machine_id INTEGER NOT NULL, opened_at INTEGER NOT NULL, closed_at INTEGER, cashout_at INTEGER, quality TEXT NOT NULL, FOREIGN KEY(machine_id) REFERENCES machines(machine_id)`。座席復元・離席の記録は実際の`MachineService`の状態遷移時に同期し、サーバー停止で正確な終端が無い場合は`quality='UNKNOWN'`。完全観測サイクルだけを28日窓で計測し、30件未満なら`pCashout=0.60`。
- `cashoutCount`と`sessionCount`の窓は`seatEnd`が属するhallDayから過去28**暦営業日**、未観測・継続中サイクルは数えない。観測した実換金は`hall_economy_events`から算出するため二重登録しない。

### 15.4 予測分位・比較時の境界
- 128試行のp05は各試行の**全台Gross合計**を昇順ソートした7番目（1-based、nearest-rank: `ceil(0.05*128)=7`）、p95は122番目（`ceil(0.95*128)=122`）。台別p05を合計して全店p05とみなすことは禁止。全台同じsample indexの合計を用いる。
- cashIn/cashOut/grossの平均は128試行の算術平均、VarianceはGrossの標本分散`sum((g-mean)^2)/(128-1)`。信頼幅は同じソート方法。評価用doubleから保存用decimalへの丸めは`HALF_UP(scale=2)`。
- 状態依存の性能キャッシュに必要なsample数は初回10,000G/機種・設定・状態hash、推定RTPの95%CI半幅が1.0 percentage point超なら100,000Gまで増やす（10,000Gずつ）。サンプル終了条件はCI半幅<=0.01または100,000G。95%CIの半幅は**1.96*(sample SD of netReturnPerGame)/sqrt(n) / max(epsilon, meanBetPerGame)**、epsilon=1e-9。variance=0なら半幅0。ただし単一状態の吸収や未抽選ならUNMODELLED。
- ベースライン需要の700?などの隠れ係数は設けない。任意の追加ヒューリスティックを足さず、§6.2の300G事前・実績平滑・設定倍率・曜日/特定日倍率を用いる。サーバー負荷が高い場合、候補探索の結果を勝手に短縮して**完了扱い**せず、FAILEDと再試行を記録する。

### 15.5 曖昧な機能名の排除
- `policyMatch`の種別ごとの挙動は§9Aの表を唯一の正とする。親仕様の説明例にだけ出る「島移動ローテーション」「複合条件」「末尾2桁」「機種カテゴリー」などは**初期v1では任意機能として実装しない**。拡張時は新しいpolicy kind、match関数、保存スキーマと合格テストを定義してから実装する。Codexが勝手にルールの意味を作らない。
- §9Aにある`ZOROME`、`DAY_NUMBER_MATCH`は自動テーマ候補に**含める**。手動ruleの許容kindにも追加する。対象の実機が無い場合は選択候補から除外する。
- `RUN_2/3`方針は、候補生成時に存在する連続区間から一様抽選した区間の全台に対して、`weightedDraw(setting, minSetting=4)`を実施して同時に高設定を試みる（その他台は通常抽選）。未存在なら当日無効。手動RUN指定は§9Aのハード下限制約。
- `weightedDraw`は1〜6の非負重みを合計で正規化し、サンプル`nextDouble()`の区間探索で抽選。自律方針の一致台では設定4,5,6の重みに方針倍率を掛ける。手動下限がある場合はその未満の重みを0にして再正規化。合計0なら候補を無効にし、現在設定を採用して監査ログを残す。

**以上を親仕様に加えて実装対象とし、曖昧な「実装しやすい方」を勝手に採らない。**

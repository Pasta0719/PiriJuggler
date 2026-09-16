# Post-Phase10 reel-control hotfix — 2026-09-16

Phase11開始前のユーザー指定によるリール制御修正。

## 要件

- BONUS当選ゲームでは、BONUS図柄による正式リーチ目が有効5LINE上に一直線で停止することを許可する。
- 非premiumでは `BAR-BAR-BAR` を有効LINE上の最終停止形として禁止する。
- 非premiumで第三停止が `BAR-BAR-BAR` を完成させる候補しか近傍にない場合も、その候補を除外して別の正式リーチ目（例 `BAR-BAR-SEVEN`）へ滑らせる。
- premium演出中は `BAR-BAR-BAR` を禁止しない。
- Premium Bは中段CHERRY特殊形を維持したまま、正式リーチ目1本までの共存を許可する。
- Premium Fは従来どおり第2停止の実SEVENテンパイを禁止する。現実装はSEVENテンパイだけを蹴り、BARテンパイは蹴っていないため、premium扱いとしてBAR系正式リーチ目および `BAR-BAR-BAR` を禁止しない。
- BIG/REG entry (`SEVEN-SEVEN-SEVEN` / `SEVEN-SEVEN-BAR`) の定義、payline、固定リール配列、payoutは変更しない。

## 実装

- `StopCatalogue.Evaluation.valid(PREMIUM_B)` を拡張し、中段CHERRY + 正式リーチ目0または1本を許可。固定配列上のPREMIUM_B候補数は758から782へ戻る。
- `StopSolver` に `allowBarConfirmation` を追加し、false時は `winningBarConfirmationLines != 0` の候補をSTOP候補集合から除外する。
- `NormalGame` は `premium_type != null` の通常ゲームだけ `allowBarConfirmation=true` として `ReelRound` へ渡す。通常当選はfalse、Premium A-Fはtrue。
- Premium Fの既存SEVEN非テンパイ制御は維持する。
- 既存constructor/solver overloadは互換を維持し、runtime helper / 既存test call siteを壊さない。

## 検証追加

- PREMIUM_B候補782件を独立oracleで照合。
- PREMIUM_Bに正式リーチ目候補とBAR確認形候補が存在することを確認。
- 非premium BONUSを全6停止順・全21^3押下indexで走査し、最終形に `BAR-BAR-BAR` が1本も出ないことを確認。
- premium許可時にはBAR確認形への到達経路が残ることを確認。
- Premium F既存全停止順/押下index self-testと第2停止SEVEN非テンパイ検査を維持。

## 状態

GitHub source/testへ反映済み。ユーザーWindows環境での `build-jars.bat` と実Minecraft確認はこのhotfix後に未実施。Phase11はまだ開始しない。

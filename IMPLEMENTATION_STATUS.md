# IMPLEMENTATION_STATUS.md

## MOBILE REMOTE Unified Phase — 2026-10-06
Status: RUNTIME_PASS — production build + mobile real E2E PASS / final SKILL_STOP real-browser presentation evidence pending

スマホ遠隔遊技、モバイル経済、景品交換/換金、Minecraft資産同期、モバイル表示を1つのPhaseへ統合。
正本: `mobile/MOBILE_REMOTE_PHASE.md`

現行実装には、離席後ロビーの景品交換所、着席中の景品交換/景品換金拒否、Minecraft↔mobileのメダル/景品同期、SKILL_STOP専用モバイルpresentation（cabinet background `#111015`）が存在する。
現行mainで production build と統合mobile E2EはPASS。資産往復・離席制約・実停止入力・inventory不足時の資産保持まで証跡化済み。COMPLETEはSKILL_STOPの実ブラウザ表示（#111015・challenge PNG・残G）の証跡化後に付与する。

このファイルは各Phase終了時の現在状態を示す。

## SKILL STOP Phase 06 — 2026-10-03
Status: COMPLETE — 最終総合実機受入 GitHub Actions run 37132184760 PASS（6f460301）。Phase05 productionを基準に、全体build、Phase04機械割再照合、Phase06専用実Paper/Fabric入力マトリクスを検証。

## SKILL STOP Phase 05 — 2026-10-03
Status: COMPLETE — チャレンジ画像/成功音/残Gの操作画面・観覧同期を実装・実機検証完了。
既存BAR/ベル/ピエロPNGをCHANCE下へ表示、第三停止で消灯、成功時notice音1回、残Gを操作画面/観覧画面へ同期。ESC復帰・再起動・第二Fabric観覧・重複通知再送も検証。
通常build CI PASS: Actions run 37127664141。Phase05実Paper/Fabric runtime PASS: Actions run 37127643241。詳細: skill-stop-phases/SKILL_STOP_PHASE_05_IMPLEMENTATION.md。次はPhase06最終総合受入。

## SKILL STOP Phase 04 — 2026-10-03
Status: COMPLETE — 実制御2,222,640履歴・機械割解析・独立60万完了ボーナス・全体CI・実機202/202 PASS。
実JAR整数重みで設定1〜6の98.5/100/102/105/109/113%を誤差0.00003ポイント未満で確認。全役確率・観測停止による攻略操作表・成功率別IN/OUTを保存。
証拠: runtime-evidence/SKILL_STOP_PHASE_04/REPORT.md。詳細: skill-stop-phases/SKILL_STOP_PHASE_04_IMPLEMENTATION.md。Phase05未着手。

## SKILL STOP Phase 03 — 2026-10-03
Status: COMPLETE — 全体CI・実Minecraft受入202/202 PASS。
BIG20/REG8から開始、1/15・1/9でチャレンジ抽選、BAR/ベル/ピエロ各1/3。第三停止14枚、残り-1と成功+3の後に終了判定。追加Gでも抽選。
未完了SKILL_STOPはタイムアウト/再起動で自動清算せず台の権利・固定抽選・入力/停止履歴・無料再遊技を保存。
実機でBIG23G、最終G成功、途中停止からPaper/Client再起動・復元、指定外失敗、REG8G終了、連続リプレイ無料を確認。
共通/Paper/Fabric実行373件PASS（Paperのopt-in1件skip）。root test/package/runtime helper build PASS。
検証対象: `fa81e18a5e4c52c7e2a0144b2324f10bf29fa3ae`。証拠: runtime-evidence/SKILL_STOP_PHASE_03/REPORT.md。
詳細: skill-stop-phases/SKILL_STOP_PHASE_03_IMPLEMENTATION.md。Phase04以降未着手。機械割・PNG/音の完成版検証はまだ行っていない。

## SKILL STOP Phase 02 — 2026-10-03

Status: COMPLETE — 停止制御・全体ビルド・実Paper/Fabric受入PASS。既存Paper/Fabric JARへSKILL_STOPを追加。
共通停止制御、専用配列、入力履歴、通常/持越し/プレミア/1枚役/チャレンジ制御、Paper入賞判定と保存、Fabric表示を実装。
停止制御79条件×6押し順×21³=4,389,714履歴PASS。root test/packagePiriJarsとruntime helpers PASS。
実Minecraft受入33/33 PASS。押した位置の送信漏れとSKILL_STOP再着席時の履歴消失を修正。
検証対象: 78911f2a6983896a87a3c6710473cabe541856fe。証拠: runtime-evidence/SKILL_STOP_PHASE_02/REPORT.md。
残りG・チャレンジ抽選/追加Gの状態管理は冒頭のPhase03完了記録を参照。機械割の実装値検証はPhase04、表示/音の追加はPhase05。
旧GOD/NEXTと既存Phase表の状態はこの作業で変更しない。
詳細: [SKILL_STOP_PHASE_02_IMPLEMENTATION.md](skill-stop-phases/SKILL_STOP_PHASE_02_IMPLEMENTATION.md)。

## Current status — 2026-09-20

PiriJuggler Phase01–12 はすべて COMPLETE。
Remote Machine Visual / Hall Audio は仕様確定済み。Phase12 runtime acceptanceまでPASS。Phase13–14 は未実装。

| Phase | 状態 | 概要 |
|---|---|---|
| 01 | COMPLETE | Gradle / Paper / Fabric / Protocol Handshake |
| 02 | COMPLETE | SQLite / Machine Registration / Session |
| 03 | COMPLETE | Fabric Slot Screen / Input / Base UI |
| 04 | COMPLETE | Fixed Reels / 9261 Precompute / Stop Solver |
| 05 | COMPLETE | RNG / Normal Game / Replay / Simulator |
| 06 | COMPLETE | Piri Chance / Premium / Audio / BIG / REG |
| 07 | COMPLETE | Vault / CREDIT / Held Medals / Medal Bundle |
| 08 | COMPLETE | Prize Exchange / Vault Exchange / Transactions |
| 09 | COMPLETE | Data Lamp / Graph / Piri Chain |
| 10 | COMPLETE | Admin / Settings / Startup Allocation / Events |
| 11 | COMPLETE | Suspend / Restart Recovery / Hardening / Final Tests |
| 12 | COMPLETE | Remote Public State / Interest Sync — build/test + real spectator runtime acceptance PASS |
| 13 | COMPLETE | Entity-Free World Cabinet Renderer — deterministic tests + real 42-cabinet runtime PASS |
| 14 | COMPLETE | Positional Hall Audio / Performance / Hardening — production acceptance complete; numeric CI verifier stalled without production failure |

## Existing final verification

- Phase11 Runtime Acceptance: PASS
- Security negative test: PASS
- Recover cashout: PASS
- Disconnect grace / expiry / idle / restart recovery: PASS
- `FINAL_VERIFICATION.md`: **40 / 40 PASS**
- Final Phase11 evidence: `runtime-evidence/PHASE_11/REPORT.md`
- Phase11 instruction status: `phases/PHASE_11_RECOVERY_FINAL.md` = COMPLETE

## Locked future specification

- Master spec: `docs/REMOTE_MACHINE_VISUAL_SPEC.md`
- Phase12: `phases/PHASE_12_REMOTE_STATE_SYNC.md`
- Phase13: `phases/PHASE_13_WORLD_CABINET_RENDERER.md`
- Phase14: `phases/PHASE_14_HALL_AUDIO_PERFORMANCE.md`

Important locked decisions:
- display entities 0
- custom BlockEntity 0
- registered machine button position/facing remains physical anchor
- Paper owns truth; Fabric only renders public state
- remote client cannot control machines
- no hidden setting/internal role/bonus type leaks
- 42-machine performance acceptance required before Phase14 COMPLETE

## Completion / next work

Current phase: none
Last completed phase: 14
Open blockers: none for Phase12
Next planned phase: Phase13 implementation/runtime acceptance
Phase13 start condition: satisfied and explicitly started

Phase12 production implementation and real Paper+Fabric runtime acceptance are PASS. Phase13 world rendering is COMPLETE. Phase14 hall audio/performance production acceptance is COMPLETE. Dedicated numeric CI runtime stalled in verifier infrastructure and is recorded as HARNESS_ERROR/CI_STALLED, not a production failure.


## GOD / successor machine branch — 2026-09-23

Piri GOD専用開発フローは **RETIRED_BY_USER**。

- GOD Phase 01: COMPLETE_RETAINED_REFERENCE
- GOD Phase 02: ABANDONED_INCOMPLETE
- GOD Phase 03–06: CANCELLED_NOT_TO_RUN
- GOD Phase 02 runtime acceptanceは後続開発のBLOCKERではない。
- GOD runtime runnerを修正してGODを完成させる作業は自動再開しない。
- GODのコード・資料は削除せず、後続方式で明示的に選ばれたものだけ再利用する。

後続方式のPhase正本は `NEXT_MACHINE_PHASE_MAP.json` と `next-machine-phases/README.md`。

Current successor status: COMPLETE through NEXT Phase 05 final runtime acceptance.

The obsolete dedicated NEXT Phase 02 runtime workflow/runner was retired by user decision on 2026-10-06. This does not remove JUGGLER_GOD production code or core tests.

Canonical successor runtime protection is now NEXT Phase 05 final runtime, which validates real Paper+Fabric GOD play, BIG transition, payout/result agreement, persisted recovery, and JUGGLER_GOD machine-type persistence.

## JUGGLER_GOD current verification

- Core gameplay tests remain in `JugglerGodCoreTest`.
- NEXT Phase 05 real runtime remains the canonical end-to-end acceptance.
- The old `run-next-phase02-runtime.bat`, `runtime-test-support/run_next_phase02.py`, and dedicated Phase02 workflow were removed.
- Direct-entry GOD-chain counter, authoritative heaven target, GOD freeze/result, payout, and persistence remain covered by current tests/runtime acceptance.

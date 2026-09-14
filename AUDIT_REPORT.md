# AUDIT_REPORT.md — v3全件横断監査結果

v3 SPECと11 Phaseを、章単位ではなく同一概念の全参照箇所を横断して監査した。
以下は検出してv4で修正した事項。

## Critical / 実装停止につながる矛盾

1. SUSPEND資産保存先: 第42章wallet移動 vs 第82/127章session保持。
2. `SUSPENDED`をGameStateに入れる設計では元のBETTED/SPINNING等resume stateが失われる。
3. true server restartを新営業日としながら`business_day=yyyy-MM-dd` PKだったため同日複数restartを表現不能。
4. unresolved suspended spinを残してmachine lockを解放すると、他playerの遊技とmachine currentGames/bonus timelineが分岐する。
5. premium BはCHERRY_BIG専用と書きながら同章に「単独BIGでも置換」と残存。
6. GRAPE/REPLAY最終候補がcorner CHERRYを同時表示可能で「1ゲーム1払い出し」と矛盾。
7. CHERRY/MISSはlineを持たないためstop solverのline tie-breakが未定義。
8. server-authoritative pressedIndexを要求しながら、server側reel phase/start phase/motion積分/latency補正が未定義。
9. Admin Screenはgame sessionを作らないのにadmin packet共通payloadがsessionIdを要求。
10. reconnect clientのclientSequence再開値が未定義で、client再起動後に全操作rejectされ得た。
11. Piri Chance imageが109章256x256、120章512x256で矛盾。
12. Java2D AssetGeneratorがMinecraft TextRenderer/system fontへ依存する記述でdeterministic hashと矛盾。

## Major / 資産・復旧・統計

13. player_walletにcredit/heldを置くことで「CREDIT=現在台」「物理メダル=携帯資産」という4資産モデルを破っていた。
14. heldMedalsをCREDITへ戻す規則が無く、heldMedalsが大量でもCREDIT不足で遊技不能になった。
15. CASH_OUTを受理するGameStateが未定義。
16. session schemaにlifecycle/premium/stoppedMask/display stops/phase等がなくexact resume不能。
17. restart後に旧session statsを同名business_day rowへ書く設計が新営業periodと競合。
18. machine create時の初期settingが未定義。
19. create/redefineで同一Buttonを複数machineへ登録する扱い未定義。
20. disabled machine通常interactionの扱い未定義。
21. suspended sessionを持つplayerが別machineへ着席する扱い未定義。
22. remove/redefine/reset/settingで「occupied」がACTIVEだけかgrace sessionも含むか未定義。
23. `/piri reset daily all`で一部busy時のpartial reset可否が未定義。
24. medal itemのvanilla merge/drag/crafting等が未定義でcount=1 invariant/複製防止が弱かった。
25. cashout item deliveryのduplicate token価値を防ぐledgerが無かった。
26. prize GUIのinventory capacityが既存stack空き容量を考慮していなかった。
27. Vault外部APIとSQLiteを完全ACIDのように扱っており、hard crash中exactly-onceが技術的に保証不能だった。

## Major / game/protocol/event

28. packet envelopeにpacketTypeがあるのにHELLO JSONにもtypeを重複させていた。
29. gameplay sequenceとadmin sequenceが分離されていなかった。
30. OPEN/admin入口のpacket設計とserver-side Button interaction設計が過去版から混在していた。
31. event pattern適用とminSetting保証補正の順序/保護範囲が未定義。
32. startup setting history reason SERVER_START/EVENTの使い分けが未定義。
33. manual next-profile overrideをallocation失敗時にも消費する可能性があった。
34. gameplay RNGとevent allocation RNGのstreamが同一概念で、event抽選がgameplay乱数列を変える余地があった。
35. premium configでBしかweightが無い場合、BIG/PIERO_BIGのeligible premiumが0件になるvalidation漏れ。

## Runtime / Workflow

36. Phaseごとの完了条件がunit test + build中心で、実際のPaper/Fabric runtime testを強制していなかった。
37. Phase1が「ゲーム上未テスト」でもCOMPLETE可能だった。
38. Phase2は旧schema migration v1と書かれ、SPEC更新後のschema versionと同期していなかった。
39. Phase11だけ実環境確認を書いても、前Phaseでruntime defectを早期検出できなかった。

## 全件再監査で追加検出した境界条件

40. プレミアFは「2本目で非テンパイ」とだけ書かれ、2本目STOP候補filterとtie-break手順が未固定だった。v4最終版ではbase役別候補、2本目filter、3本目処理、全停止順feasibility self-testまで固定。
41. Piri Medal 1枚tokenを右クリックsplitした場合の`floor(1/2)=0`が未定義だった。1枚時はno-opへ固定。
42. medal ledger更新とMinecraft inventory mutationは同一ACID transactionではないため、hard crash windowでtoken価値が利用不能/二重化し得た。`medal_inventory_transactions` journalとbefore/after reconciliation、判定不能時`REVIEW_REQUIRED`を追加。
43. Phase02 occupied runtime testが「second clientまたはsupport automation」と選択式だった。actual Fabric client 2台目を必須へ固定。

## 検証で問題なしと確認した事項

- 3リール配列は北電子公式ファンキージャグラー2の公開リール画像と21コマ×3本すべて一致。
- 6設定の12 role weightは各設定ちょうど1,000,000,000。
- BIG/REG合算は指定値（266.4等 / 439.8等）に一致。
- BET/REPLAY/bonus grossを含む理論機械割は97.800003/99.400002/101.000003/103.400006/105.999988/111.400001%前後。
- strict candidate化後も候補数: GRAPE750, BELL50, PIERO20, REPLAY525, CHERRY1514, MISS5250, BIG entry10, REG entry10。
- Fabric API 0.102.0+1.21 / Loader0.16.14 / Yarn1.21+build.9はFabric Maven上に実在。
- `com.tianscar.javasound:javasound-vorbis:2.1.0`はMaven Centralに存在。

## v4方針

- session assetはsessionに一本化。walletはpending deliveryだけ。
- exact resumeは60秒grace内だけ。lock解放前に必ずforce settlementしてtimelineを一本化。
- true process startupごとにUUID business period。
- runtime acceptanceを全Phase必須化。
- v3 Phase1既実装repoはmigration retrofit runtime testを通してからPhase2再開。

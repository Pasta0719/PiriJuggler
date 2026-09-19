# PHASE 13 — Entity-Free World Cabinet Renderer

Status: NOT_STARTED
Depends on: Phase12 COMPLETE

## 読むもの
- `../docs/REMOTE_MACHINE_VISUAL_SPEC.md`
- `PHASE_12_REMOTE_STATE_SYNC.md`
- existing Fabric `SlotScreen`, `SlotViewState`, reel textures, lamp textures

## 共通ルール
- Minecraft entity を1体も生成しない。
- BlockEntity を追加しない。
- world renderer は結果計算をしない。authoritative result は Paper。
- setting/internal role/hidden bonus type を表示しない。
- existing full-screen SlotScreen を置換しない。
- `./gradlew test` / `./gradlew build` exit0 必須。
- runtime未実施なら COMPLETE 禁止。
- COMPLETE後は Phase14 へ自動で進まず停止。

## 実装範囲

### Client state
Fabric に:
`Map<Integer, RemoteMachineViewState>`
を追加。

remote state は machineId 単位で:
- placement
- reel phases/stops
- spin id/profile
- stopped mask
- lamp
- public bonus mode/count
を保持する。

disconnect/world change で全 cache clear。

### Placement transform
anchor center:
`(x+0.5, y+0.5, z+0.5)`

display plane:
- front = stored facing; NORTH/SOUTH/EAST/WEST/UP/DOWN を全て扱う
- horizontal: localUp=world +Y, center=anchor center + front*0.505 + localUp*1.05
- UP/DOWN: localUp=world -Z, localRight=world +X, center=anchor center + front*0.505
- width = 1.60 blocks
- height = 1.00 blocks
- outward-facing
- depth bias = 0.002 blocks

redefine 後は新 anchor/facing に即座に移動。

### Render contents
compact cabinet face として:
- reel window x3
- existing symbol textures
- authoritative stop indexes
- Piri Chance lamp texture
- CREDIT
- PAY
- BIG/REG が public の時のみ mode
- bonus active 中 bonusCount

単なる floating text/icon ではなく、台前面に一体化した表示にする。

### Reel animation
REMOTE_MACHINE_SPIN 受信:
- local receive nano time を spin origin として保存
- existing ReelMotion profile で client-side animation

REMOTE_MACHINE_STOP:
- authoritative stopIndex へ existing stop interpolation と同じ規則で収束
- 3rd stop 後も最終 stop を保持

snapshot/resume:
- stoppedMask を尊重
- stopped reel は動かさない
- spinning reel だけ再開
- reconnect 後に古い spin id を持ち越さない

### Render culling
1つの world render callback だけを使用。

各 frame:
1. current world mismatch -> skip
2. distance squared > 32^2 -> skip
3. camera frustum outside -> skip
4. visible machine のみ phase 計算
5. draw

per-machine entity/tick callback は作らない。

## 完了条件
- ArmorStand/Display系/その他 entity 数が feature ON/OFF で増えない
- 1台: idle/stopped appearance が正しい
- 1台: spin -> 3 STOP が owner の authoritative stop index と一致
- lamp ON/OFF/blink が一致
- BIG/REG active 表示と count が一致
- internal bonus type は public 前に表示されない
- 6方向 NORTH/SOUTH/EAST/WEST/UP/DOWN で向きが正しく、UP/DOWNも回転が不定にならない
- redefine で表示位置/向きが追従
- 42台同時視界でも renderer crash / leak 無し
- local SlotScreen の入力・描画・音に regression 無し
- real Minecraft runtime PASS

## Evidence
COMPLETE 時:
- `runtime-evidence/PHASE_13/REPORT.md`
- six-facing screenshots
- owner screen vs external display stop-index comparison
- entity count before/after

## 終了時
IMPLEMENTATION_STATUS の Phase13 を COMPLETE にし、Phase14 は NOT_STARTED のまま停止。

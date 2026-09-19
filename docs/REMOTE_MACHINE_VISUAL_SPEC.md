# Remote Machine Visual / Hall Audio Specification

Status: SPEC_LOCKED / NOT_IMPLEMENTED
Target: Minecraft 1.21, Fabric API 0.102.0+, Paper plugin + existing Piri Fabric client

## 1. Goal

Registered Piri machines must be observable from the Minecraft world without opening the slot screen.
A nearby player can visually understand the same public machine appearance that a player at the machine can see: reel motion/stops, Piri Chance lamp state, whether the machine is in BIG/REG play, and visible counters needed for the cabinet display.

This feature must use **zero Minecraft entities** for the cabinet display.
Do not use ArmorStand, ItemDisplay, TextDisplay, BlockDisplay, marker entities, particles as the main display, or spawned fake entities.

## 2. Existing source of truth

The existing registered machine remains the source of physical placement.

A machine already stores:
- machine id
- world UUID / world name
- anchor block x/y/z
- facing
- last reel stops

The anchor is the registered button block. No new BlockEntity is introduced.

The Paper game/session state remains authoritative. The Fabric client is visual only and must never compute outcomes.

## 3. World placement

The external display is a client-side world render attached to the registered machine anchor.

Coordinate system:
- anchor center = (x + 0.5, y + 0.5, z + 0.5)
- front direction = stored machine facing
- display plane faces outward in the stored facing direction
- display plane center = anchor center + front * 0.505 + world-up * 1.05
- logical width = 1.60 blocks
- logical height = 1.00 blocks
- rendering depth offset = 0.002 blocks to prevent z-fighting

The display plane is not a physical block, has no collision and cannot be interacted with.
Machine redefine immediately moves the client-side display to the new anchor/facing.

## 4. External cabinet content

The world display is not a status icon substitute. It is a compact cabinet face.

It renders:
- three reel windows using the existing symbol textures
- exact stopped reel symbols from authoritative stop indexes
- spinning reel animation while the machine is spinning
- Piri Chance lamp on/off/blink using the existing lamp texture
- CREDIT
- PAY
- bonus count while BIG/REG is active
- BIG/REG mode indication only after the bonus type is public

It does not render:
- setting
- internal role before it is publicly visible
- premium selection internals
- RNG state
- stop hints
- hidden session data
- Vault balance
- held medals

A bonus that is only internally won must not leak its type. Until the type is publicly established, remote viewers may only see the same visible lamp/reel information as a normal observer.

## 5. Reel motion

No per-frame reel position packets are sent.

On spin start Paper broadcasts a remote visual state event containing:
- machine id
- spin id
- animation profile
- the three start phases
- current public game mode
- current lamp state

Fabric records local receive time and advances each reel with the existing ReelMotion profile.

On each authoritative REEL_STOP Paper broadcasts:
- machine id
- spin id
- reel
- stop index
- durationMs

Fabric interpolates to the authoritative stop index with the same visual stop motion used by SlotViewState.

On a fresh snapshot while a machine is already spinning, Paper sends the current authoritative display/stop mask and a spin-resume description sufficient for the client to continue visual motion. Exact outcome remains server-owned.

## 6. Remote state protocol

Add server-to-client packet types dedicated to public remote visualization. Existing owner/session packets remain unchanged.

Required packets:
- REMOTE_MACHINE_SNAPSHOT: full public visual state for one machine
- REMOTE_MACHINE_SPIN: spin start/resume
- REMOTE_MACHINE_STOP: one authoritative reel stop
- REMOTE_MACHINE_NOTICE: public lamp change/blink
- REMOTE_MACHINE_BONUS: BIG/REG start/end after type is public
- REMOTE_MACHINE_REMOVE: stop rendering a machine
- REMOTE_MACHINE_SOUND: one positional public sound event

Every remote packet contains machineId.
Placement-bearing snapshot packets also contain world UUID, x/y/z and facing.

The remote protocol contains no client-to-server control action. Remote viewers cannot stop, bet, loan, insert, cash out or mutate the machine through these packets.

## 7. Interest management

Paper sends remote state only to compatible Piri Fabric clients in the same world.

Visual interest radius: 32 blocks from the machine anchor.
Audio interest radius is defined separately below.

Interest set is refreshed:
- on join / successful protocol handshake
- on world change
- every 10 server ticks for player movement
- immediately when a machine is created, redefined, removed or enabled/disabled

When a viewer enters visual range, Paper sends one REMOTE_MACHINE_SNAPSHOT.
When a viewer leaves visual range, Paper sends REMOTE_MACHINE_REMOVE.

Normal gameplay is event-driven after the snapshot. There is no 20 TPS full-state broadcast and no per-tick database query.

## 8. Client renderer

Fabric keeps:
Map<Integer, RemoteMachineViewState>

There is one world-render callback for all remote machines.
There is not one renderer object registered as a Minecraft entity for each machine.

Per frame:
1. reject machine if world differs
2. reject if squared distance > 32^2
3. reject if outside camera frustum
4. compute local reel phases only for remaining visible machines
5. draw the cabinet plane and textures

The renderer reuses existing Piri reel/lamp assets. It must not allocate large temporary collections every frame.

## 9. Positional hall audio

Current owner audio remains unchanged.

Remote hall audio is separate and positional:
- player currently operating that machine is excluded from remote duplicate sound
- sound origin = machine anchor center + up * 1.0
- one-shot SE audible radius = 16 blocks
- normal external SE volume = 0.35
- notice / tenpai external volume = 0.45
- bonus BGM audible radius = 12 blocks
- external bonus BGM volume = 0.18
- distance attenuation enabled
- leaving range or receiving BONUS_END stops that machine's remote BGM

Remote BGM is tracked by machineId so multiple bonus machines can coexist.
Remote sounds never reveal a hidden internal result earlier than the owner/public machine appearance.

## 10. State lifecycle

Idle machine:
- stopped reels shown
- lamp reflects public state
- no animation or BGM

Seat/open:
- no special secret state is exposed

Spin:
- remote reels spin client-side

Each STOP:
- corresponding remote reel stops at the same authoritative index

Notice:
- lamp state/blink updates
- public notice sound may play positionally

Bonus pending:
- do not publish BIG/REG type until it is public
- reel/lamp appearance continues normally

BIG/REG start:
- mode becomes public
- bonus counter starts
- remote BGM starts for nearby non-owner viewers

Bonus end:
- remote BGM stops
- final visible state is synchronized

Session close/suspend:
- machine remains rendered at its authoritative stopped state
- no stale spinning state may remain

Server restart/reconnect:
- fresh snapshots reconstruct every in-range machine without relying on old client cache

## 11. Performance requirements

Target hall size: 42 registered machines.

Hard design rules:
- 0 display entities
- 0 per-machine server tick tasks
- 0 per-frame network packets
- 0 database queries from the client render loop
- no state broadcast to out-of-range viewers
- no continuous sound packets for BGM
- one render callback iterating the in-range client cache

Acceptance target:
- 42 registered machines may exist simultaneously
- 42 idle visible machines cause no continuous network traffic after snapshot
- spinning animation is client-side
- one STOP produces one stop event per interested viewer
- server TPS and client FPS are measured before/after in Phase14; regression thresholds are defined there

## 12. Compatibility and failure behavior

If the Fabric client does not support the new remote packet types, it must fail protocol compatibility rather than silently misdecode them. Protocol version must be bumped as part of Phase12.

If a remote visual packet is malformed:
- discard that remote machine state
- do not close the local player's active slot session
- do not crash the client

Disconnect/world change clears all remote machine cache and all remote BGM.

## 13. Non-goals

This feature does not:
- replace the existing full-screen SlotScreen
- make the machine a custom Minecraft block
- create a BlockEntity
- change game probabilities or stop control
- expose settings/internal roles
- allow spectators to control another player's machine
- use entity-based displays

## 14. Implementation phases

- Phase12: remote public state protocol + Paper interest/sync
- Phase13: Fabric entity-free world cabinet renderer
- Phase14: positional hall audio + performance/hardening/runtime acceptance

Implementation must stop at the end of each phase for runtime verification before proceeding.

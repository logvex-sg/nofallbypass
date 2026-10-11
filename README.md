# NoFall Bypass

A [Meteor Client](https://meteorclient.com) addon for **Minecraft 1.21.11** (Fabric, Java 21)
that adds one module, **NoFall Bypass**, with several independently implemented fall-damage
strategies, an adaptive selector, and server-profile-aware fallbacks.

> **No bypass is claimed.** Client-side fall mitigation cannot defeat server-side validation that
> simulates the player's movement. The packet strategies are attempts that some servers honour and
> others reject or correct; the conservative strategies (Safe Landing, MLG assist) never fabricate
> movement. Test on a local server, then on the specific server you care about, before relying on
> any mode.

## Requirements

- Minecraft 1.21.11
- Fabric Loader (see `gradle/libs.versions.toml`)
- Java 21
- [Meteor Client](https://meteorclient.com) 1.21.11

## Build

```bash
./gradlew build
```

Output: `build/libs/nofallbypass-<version>.jar`

Run only the unit tests:

```bash
./gradlew test
```

## Install

1. Install Fabric Loader for 1.21.11 and place `meteor-client-<version>.jar` in your `mods/` folder.
2. Run the build and copy `build/libs/nofallbypass-<version>.jar` into the same `mods/` folder.
3. Launch the Fabric profile. `NoFall Bypass` appears under **Movement** in the Meteor GUI.

## Module: NoFall Bypass

| Mode | What it does | Limitations | Verification |
| --- | --- | --- | --- |
| Vanilla | Makes no changes; parks the module without disabling it. | Does not mitigate fall damage. | Unit tested (no side effects). |
| Ground Spoof | Forces the on-ground flag of outgoing `PlayerMoveC2SPacket`s to `true` while descending past the minimum distance. | Only changes what the client reports. Servers that simulate movement ignore it and may correct or flag the spoofed flag. | Unit tested; **in-game verification required**. |
| Packet | Ground Spoof plus a single, edge-triggered `PlayerMoveC2SPacket.OnGroundOnly(true)` when a fall begins. | Sending an extra movement packet does not reset the server's own fall distance. Rejected by servers that validate position/velocity. | Unit tested; **in-game verification required**. |
| Grim Jump | Landing-time correction for prediction-based checks: releases jump while descending, then on the landing tick resends the last `on ground` position and re-presses jump, waiting for the server's velocity update. | Heuristic: works only against checks that defer to the client's landing transition. Servers that simulate the landing themselves ignore it and may flag the invalid movement. | Unit tested; **in-game verification required**. |
| MLG Assist | When a landing is imminent and a bucket is in the hotbar, places water/powder snow beneath the player. | Legitimate clutch, not a bypass. Needs a bucket, a surface in reach, and a server that accepts the placement. | Unit tested; **in-game verification required**. |
| Elytra | Tracks the transition out of a glide and requests an MLG clutch if the post-glide drop is lethal. | Cannot create fall protection from nothing. | Unit tested; **in-game verification required**. |
| Vehicle | Watches vehicle descent and warns before a hard landing. | The client cannot place blocks while seated; the only reliable defence is to dismount. | Unit tested; **in-game verification required**. |
| Safe Landing | Conservative fallback: monitors the fall and warns before a damaging landing. Never modifies packets. | By design cannot reduce fall damage. | Unit tested (no side effects). |
| Adaptive | Picks a concrete strategy per tick from the movement state and the server profile. | Leans on the same unverified packet strategies when they are allowed. | Unit tested for selection logic; **in-game verification required**. |
| Custom | Runs a chosen primary strategy with an automatic safe fallback when it is unsupported or cannot act. | Same limitations as the chosen primary. | Unit tested for fallback logic; **in-game verification required**. |

## Settings

- `mode` — strategy to use (default `Adaptive`).
- `custom-primary` — primary strategy for Custom mode.
- `adaptive` — allow Adaptive to re-select every tick; off falls back to Safe Landing.
- `min-fall-distance` — minimum fall distance before any strategy intervenes (0.5–100).
- `landing-threshold` — height above the ground at which a landing is "imminent" (0.5–20).
- `water-bucket-assist` — allow MLG / Adaptive to place a bucket.
- `elytra-handling`, `vehicle-handling` — enable the elytra and vehicle strategies.
- `notifications` — chat message when a strategy is selected, warns, or falls back.
- `debug` — log internal decisions to chat.
- `grim-fall-threshold` — fall distance at which Grim Jump arms its correction (3–15).
- `grim-modified-packet` — also resend the corrected position (`y + 0.01`) on landing.
- `grim-fast-fall` — optional one-shot downward velocity while falling (off by default; visible to the server).
- `grim-velocity-check` — only re-press jump after the server confirms with a velocity update.

## Architecture

```
src/main/java/com/logvex/nofallbypass/
  NoFallBypassAddon.java        Meteor addon entrypoint
  modules/NoFall.java           the module; the only place that touches Minecraft APIs
  modes/
    NoFallMode.java             abstract mode + Strategy enum + Context bridge
    NoFallSettings.java         validated, Minecraft-free view of the settings
    VanillaMode.java GroundSpoofMode.java PacketMode.java GrimJumpMode.java
    MlgAssistMode.java ElytraMode.java VehicleMode.java SafeLandingMode.java
    CompositeMode.java AdaptiveMode.java CustomMode.java
  compat/ServerCompatibility.java   server-specific knowledge, isolated here
  utils/FallTracker.java           Minecraft-free fall-state tracker
  utils/LandingHelper.java         Minecraft-free landing maths
```

Modes never call Minecraft APIs directly. They receive a `FallTracker` snapshot, a validated
`NoFallSettings`, and a `Context` whose only Minecraft-aware methods are `setPacketGround`,
`sendGroundPacket` (edge-triggered, self-tagged so it cannot recurse), `sendMovePacket`,
`requestMlg`, the deferred `requestJump`/`releaseJump`, and `applyVerticalVelocity`. This keeps
every mode unit-testable without a game client.

## Server compatibility

`ServerCompatibility.forAddress` normalises the server host and returns a conservative profile.
No address or anti-cheat implementation is hardcoded into the modes; the profile only records
which strategies the module is *willing to attempt*. The `6b6t` profile is treated like any other
anarchy server with an unknown movement check — no bypass is claimed and packet strategies are
explicitly unverified. World changes, respawns, disconnects and teleports reset all module state
(`FallTracker` detects them via world id and a Y-delta threshold), and the module restores the
jump key whenever it goes inactive so no input is ever left stuck.

## Tests

```bash
./gradlew test
```

Unit tests cover `FallTracker`, `LandingHelper`, `ServerCompatibility`, adaptive/custom selection
and per-mode behaviour (including the Grim Jump landing state machine). Everything that needs a
running client is listed as **in-game verification required** above. The `GrimJumpMode` packet
sequence follows the publicly published client logic (Krs `NoFall` "Grim 1.9+" and Epsilon
`GrimJump`); the module-level integration (deferred jump press, velocity acknowledgement, silent
self-tagged packets) is verified on a local dev server.

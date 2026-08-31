# Changelog — Immortal Snail (Minecraft 26.2, Fabric, Mojang mappings)

## 0.1.0 — Initial release

- The Immortal Snail thought experiment, as a Minecraft mod — ported to
  Minecraft 26.2 (unobfuscated, Fabric, released April 7, 2026).
- **The Bargain:** on your first login you receive a shulker box of
  max-enchanted starter gear and can fill two more shulker boxes with
  anything you want through a compact 352x216 in-game item picker
  (built-in picker plus optional JEI ghost-ingredient integration).
- Conservative item blacklist (command blocks, structure blocks, jigsaw,
  barrier, light, spawner, etc.).
- Operators are fully exempt; the snail freezes when nobody is online, and
  a caught player is added to the vanilla ban list.
- Server-side TOML config (config/immortalsnail-common.toml), live-reloadable
  via `/snail reload`; admin commands: `/snail locate`, `/snail spawn|despawn`,
  `/snail speed`.
- Requires: Fabric Loader (>=0.19.0), Fabric API, Cloth Config, Java 25.
  Optional: JEI.

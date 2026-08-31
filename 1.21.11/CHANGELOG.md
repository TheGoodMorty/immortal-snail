# Changelog — Immortal Snail (Minecraft 1.21.11, Fabric, Yarn mappings)

## 0.1.0 — Initial release

- The Immortal Snail thought experiment, as a Minecraft mod: accept the
  bargain and you are immortal — but a snail that knows where you are
  crawls toward you, forever. If it reaches you, you die.
- **The Bargain:** on your first login you get a shulker box of max-enchanted
  starter gear and can fill two more shulker boxes with anything you want
  through a compact in-game item picker (built-in picker plus optional JEI
  ghost-ingredient integration).
- Conservative item blacklist (command blocks, structure blocks, jigsaw,
  barrier, light, spawner, etc.).
- Operators are fully exempt; the server freezes the snail when nobody is
  online, and a caught player is added to the vanilla ban list.
- Server-side TOML config (config/immortalsnail-common.toml) — speed, ban
  settings, starter gear, and more, reloadable via `/snail reload`.
- Admin commands: `/snail locate`, `/snail spawn|despawn`,
  `/snail speed`, `/snail ban`.
- Requires: Fabric Loader, Fabric API, Cloth Config. Optional: JEI.

# Immortal Snail — the Minecraft mod

The Immortal Snail thought experiment, as a Minecraft mod. Accept the
bargain: become immortal with anything you want — and a snail that knows
where you are starts crawling toward you, forever. If it reaches you, you
die.

This repo hosts **two parallel ports of the same mod**:

| Folder | Minecraft | Mappings | Fabric Loom | Java |
|--------|-----------|----------|-------------|------|
| `1.21.11` | 1.21.11 | Yarn | 1.13.1 | 21 |
| `26.2` | 26.2 (unobfuscated) | Mojang official | 1.17.20 | 25 |

Each folder is a complete, independently buildable Fabric project (split
main/client source sets). Design details and per-version notes are in each
folder's README.md and DESIGN.md.

## Building

```bash
cd 1.21.11   # or 26.2
./gradlew build          # Windows: gradlew.bat build
# -> build/libs/immortalsnail-<version>.jar   (the installable mod)
```

On Minecraft 26.1+ the game is unobfuscated, so the plain jar output is the
final mod. On 1.21.11 the jar is remapped by Loom automatically.

Optional build-time dependencies:

- **Fabric API** and **Cloth Config** come from Maven repositories.
- **JEI** — the 1.21.11 build compiles against the vendored
  `libs/jei-api-intermediate.jar` (JEI's published API artifacts are built
  with a newer Loom than 1.21.11's Loom 1.13 and cannot be processed as
  mods by it); the 26.2 build compiles against the official JEI API
  artifacts from maven.blamejared.com. JEI is never bundled — end users
  install it from Modrinth or CurseForge.

## Publishing to Modrinth / CurseForge / GitHub Releases

Publishing is automated with the [mc-publish](https://github.com/Kir-Antipov/mc-publish)
GitHub Action (see `.github/workflows/publish.yml`). One release publishes
**both** mod versions.

### One-time GitHub setup

1. Create the repository and push this folder to it (commands below).
2. In your GitHub repository: **Settings - Secrets and variables - Actions**,
   add two secrets:
   - `MODRINTH_TOKEN` — Modrinth API token (modrinth.com - Settings -
     authorization token)
   - `CURSEFORGE_TOKEN` — CurseForge API token
   (`GITHUB_TOKEN` is provided by GitHub automatically.)
3. The platform project ids are already baked into the workflow:
   Modrinth `PoROc13w`, CurseForge `1675952`.

### Releasing a new version

1. Bump `mod_version` in BOTH gradle.properties files if needed.
2. Update the per-version CHANGELOG.md (its top entry is used as the
   changelog on every platform).
3. Commit, then:

```bash
git tag v0.1.0
git push origin v0.1.0
```

The workflow builds both versions and:

- creates versions `0.1.0+mc1.21.11` and `0.1.0+mc26.2` on Modrinth
- uploads matching files to CurseForge project 1675952
- attaches both jars to the GitHub release `v0.1.0`

You can also trigger a publish without pushing from the Actions tab
(workflow_dispatch - optionally set the release tag).

### Adding a new Minecraft version

The publish workflow is driven by `versions.json` at the repo root. Adding
a port needs **no workflow changes**:

1. Create a new project folder next to `1.21.11/` and `26.2/`
   (copy the closest existing one and adjust gradle.properties + build.gradle).
2. Add one object to `versions.json`:
   `{ "dir": "<folder>", "mc": "<game version>", "java": "<build JDK>" }`
3. Add a `<folder>/CHANGELOG.md` - its first section becomes the changelog
   on Modrinth, CurseForge and the GitHub release.

The next pushed tag builds and publishes the new version automatically.

## License

MIT — see 1.21.11/LICENSE / 26.2/LICENSE.
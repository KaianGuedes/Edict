# Edict

**Edict** is a lightweight rules plugin for 1.20+ Minecraft servers that lets you decide how a handful of vanilla mechanics behave, and where. Every rule has parameters, and every parameter can be set globally, per dimension (overworld, nether, the end) or per world, from a config file, commands or a chest GUI you can click through. Edict was formerly called PluginRules and upgrades old configs automatically.

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.20%2B%20%7C%2026.3-brightgreen?style=flat-square" alt="Minecraft 1.20+ | 26.3">
  <img src="https://img.shields.io/badge/Spigot%20%7C%20Paper-orange?style=flat-square" alt="Spigot | Paper"> <br>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square" alt="License GPL-3.0">
  </a>
</p>

---

## 🔧 Installation
1. Build `Edict-0.1.0.jar` from source (see [Developer Info](#%EF%B8%8F-developer-info)); it is written to `build/libs/`.
2. Place the `.jar` file into your server's `plugins/` directory.
3. Start or restart your Minecraft server. `plugins/Edict/config.yml` and `plugins/Edict/lang/` are generated.
4. Run `/edict` in game to open the GUI, or edit `config.yml` and use `/edict reload`.

One jar covers Minecraft 1.20.x up to 26.3 on Spigot and Paper. Folia is declared supported but untested. The code is built on the Spigot API and Paper-only features are loaded only when Paper is detected.

**Upgrading from PluginRules 0.1:** on the first start Edict copies `plugins/PluginRules/config.yml`, converts the flat `rules.<name>: true/false` format to the new structure, and keeps a backup next to it (`config.yml.v0.1.bak`). The old folder is left untouched. `/pluginrules`, `/prules`, `/pr` and the `pluginrules.admin` permission keep working.

## 📜 Rules

| Rule | Parameters | What it does |
| --- | --- | --- |
| `creeper_block_damage` | `enabled`, `also-other-explosions` | Off keeps terrain intact when a creeper explodes. Optionally covers TNT, ghasts, beds and more. |
| `enderman_block_theft` | `enabled`, `allow-place` | Off stops endermen from picking up blocks, and optionally from placing them. |
| `enter_end_portal` | `enabled`, `message` | Off cancels End portal travel and tells the player why. |
| `safe_void_fall` | `enabled`, `the-end-y`, `overworld-y`, `max-height-margin`, `fall-damage`, `keep-velocity` | **Only The End:** falling out of the End never kills, and sends the player to the matching overworld sky. |

`safe_void_fall` sends players to the matching overworld: `world_the_end` goes to `world`, and `<name>_the_end` goes to `<name>`. An explicit `corresponding-overworld` section in the config overrides that, and the main world is the fallback (with a log warning). The arrival Y is clamped to the destination world's height minus a margin, with a warning in the log when that happens.

## ⌨️ Commands

Main command `/edict`, aliases `/ed`, `/pluginrules`, `/prules`, `/pr`.

| Command | Description |
| --- | --- |
| `/edict` | Opens the GUI (players). |
| `/edict list [scope]` | Shows every rule and parameter as seen from a scope. |
| `/edict get <rule> [parameter] [scope]` | Shows one value and where it comes from. |
| `/edict set <rule> [parameter] <value> [scope]` | Changes a value. `/edict set creeper_block_damage false` still works. |
| `/edict reset <rule> [parameter] [scope]` | Removes an override (or restores the default for global). |
| `/edict reload` | Re-reads `config.yml` and the language files. |

Scopes: `global`, `overworld`, `nether`, `the_end`, `world:<name>`. Tab completion covers subcommands, rules, parameters, values and scopes.

## 🔐 Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `edict.admin` | op | Use and change Edict (commands and GUI). |
| `edict.*` | op | All Edict permissions. |
| `pluginrules.admin` | op | Legacy PluginRules 0.1 permission, grants the same as `edict.admin`. |

## ⚙️ Configuration

How a value is found, per parameter: **world override, then environment group, then global default.**

```yaml
config-version: 2
language: auto            # auto | en_US | pt_BR | es_ES | ... (see Languages)
rules:
  safe_void_fall:
    enabled: false
    the-end-y: -200
    overworld-y: 1624
    fall-damage: true
environments:
  nether:
    creeper_block_damage:
      enabled: false
worlds:
  creative:
    enter_end_portal:
      enabled: false
corresponding-overworld:
  my_end: my_overworld
```

- `rules.<rule>.<parameter>` is the global default.
- `environments.<overworld|nether|the_end>.<rule>.<parameter>` overrides it for every world of that dimension type.
- `worlds.<world name>.<rule>.<parameter>` overrides it for one world.
- `corresponding-overworld` maps an End world to the overworld that `safe_void_fall` sends players to.

## 🖥️ GUI

The GUI has a main menu with one icon per rule, a scope selector (global, each dimension, each world), toggles by click, numeric steps with left, right and shift clicks, exact values through a chat prompt (press Q), and back and close buttons. Every change is saved to `config.yml` immediately.

## 🌍 Languages

Messages and GUI text live in `plugins/Edict/lang/<code>.yml`. With `language: auto` a player sees their client language and the console follows the server locale. A client language without its own file falls back by prefix (for example `es_AR` to `es_ES`), then to the server default, then to `en_US`. Set `language` to a code to force one language for everyone.

| Code | Language |
| --- | --- |
| `en_US` | English |
| `pt_BR` | Português (Brasil) |
| `pt_PT` | Português (Portugal) |
| `es_ES` | Español (España) |
| `es_MX` | Español (México) |
| `fr_FR` | Français |
| `de_DE` | Deutsch |
| `it_IT` | Italiano |
| `ja_JP` | 日本語 |
| `ko_KR` | 한국어 |
| `zh_CN` | 简体中文 |
| `zh_TW` | 繁體中文 |
| `ru_RU` | Русский |
| `pl_PL` | Polski |
| `tr_TR` | Türkçe |
| `uk_UA` | Українська |

> Translations other than English and Brazilian Portuguese are AI-assisted. Corrections from native speakers are welcome, please open an issue or a pull request.

## 📢 Planned / Coming Soon
- Pagination in the scope selector for servers with many worlds.
- More rules.
- Tested Folia support.

## 🤝 Need Help or Want to Contribute?
Found a bug? Have a suggestion? Want to help improve it?
You can:
- Open [issues](https://github.com/KaianGuedes/Edict/issues) for bugs, ideas, translation fixes or feature requests. Please include your server version, platform and `plugins/Edict/config.yml`.
- Submit [pull requests](https://github.com/KaianGuedes/Edict/pulls) to improve the code, docs, translations or features.

> ⭐ Star the repo to support the project!

## 🛠️ Developer Info
Built with:
- Java 17 (bytecode target)
- Gradle (`./gradlew build`)
- Spigot API, with Paper features used when Paper is available

`scripts/selftest.sh` runs a harness plugin (`EdictSelfTest`, built by `./gradlew build`, never shipped in the main jar) on a test server. It checks config migration, scope resolution, every rule's effect, reload, every language file (same keys, same placeholders, valid YAML) and the GUI click handlers. Made by KaianGuedes.

## 📃 License
This project is open source under the [GPL-3.0](LICENSE).

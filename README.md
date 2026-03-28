# NexusRevive
### Modern downed, revive, carry, loot, GPS, and zone system

**Compatibility:** `1.19.4` to `26.1+` on **Paper**, **Folia**, and modern forks

[Download on Modrinth](https://modrinth.com/plugin/nexusrevive)  
[Support Discord](https://discord.gg/WHBWBWWYDq)

---

**NexusRevive** replaces instant death with a fully configurable **downed system** for modern Minecraft servers.
Players can be knocked down, revived by teammates, carried away, auto-revived inside zones, tracked with GPS, and even looted while downed.

It is built for modern Paper-based servers with a strong focus on:

- Clean configuration
- Modern UI and HUD elements
- Team-based rescue gameplay
- WorldGuard and plugin compatibility
- Paper and Folia-aware execution

---

## Main Features

### Downed Instead of Instant Death
- Players enter a **downed state** instead of dying immediately
- Custom death delay, invulnerability, sounds, effects, actionbar, scoreboard, and messages
- Optional suicide timer for players who do not want to wait

### Revive and Carry System
- Revive teammates by holding **shift** near them
- Carry downed players on your shoulders
- Drop them manually
- Block revive while carried if desired

### GPS System
- Open `/nr gps` to track downed players
- Supports **BOSSBAR**, **ACTIONBAR**, **HOLOGRAM**, or combined display modes
- Distance-based auto completion
- Fully customizable menu and display formatting

### Revive Zones
- Create revive zones with `/nr zone wand`
- Select two points and create the zone with `/nr zone create <name>`
- Auto-revive inside zones
- Zone speed multipliers
- Hologram-based selection preview

### Looting
- Sneak + right click to open a secure loot menu on a downed player
- Configurable access to inventory, armor, and offhand
- Optional single-robber lock

### Integrations
- PlaceholderAPI
- WorldGuard
- Vault
- DeluxeCombat
- CMI
- Essentials / EssentialsX
- SuperVanish
- QualityArmory
- WeaponMechanics

---

## Screenshots

Add your screenshots here on Modrinth/GitHub, for example:

- Downed player pose
- Revive progress HUD
- GPS hologram
- GPS menu
- Revive zone wand preview
- Loot menu

---

## Commands and Permissions

**Main Command:**
- `/nexusrevive`
- `/nr` (alias)

**Commands:**

| Command | Description | Permission |
|---|---|---|
| `/nr` | Opens the main help page | `nexusrevive.command.help` |
| `/nr reload` | Reloads config, messages, gps, and zones | `nexusrevive.command.reload` |
| `/nr down <player>` | Forces a player into the downed state | `nexusrevive.command.down` |
| `/nr revive <player>` | Revives a downed player | `nexusrevive.command.revive` |
| `/nr kill <player>` | Kills a downed player | `nexusrevive.command.kill` |
| `/nr gps` | Opens the GPS menu for downed players | `nexusrevive.command.gps` |
| `/nr zone help` | Shows zone help | `nexusrevive.command.zone` |
| `/nr zone wand` | Gives the zone selector wand | `nexusrevive.command.zone` |
| `/nr zone create <name>` | Creates a zone from your selection | `nexusrevive.command.zone` |
| `/nr zone remove <name>` | Removes a zone | `nexusrevive.command.zone` |
| `/nr zone list` | Lists all revive zones | `nexusrevive.command.zone` |
| `/nr zone speed <name> <multiplier>` | Changes a zone revive speed multiplier | `nexusrevive.command.zone` |

**Main Gameplay Permissions:**

| Permission | Description |
|---|---|
| `nexusrevive.revivable` | Allows the player to be revived |
| `nexusrevive.reviver` | Allows reviving downed players |
| `nexusrevive.pickable` | Allows the player to be carried |
| `nexusrevive.picker` | Allows carrying downed players |
| `nexusrevive.lootable` | Allows the player to be looted |
| `nexusrevive.robber` | Allows looting downed players |
| `nexusrevive.downable` | Allows the player to enter the downed state |

---

## WorldGuard Flags

NexusRevive includes dedicated WorldGuard flags:

- `nexus-revive`
- `nexus-revive-carry`
- `nexus-revive-loot`

Examples:

```text
/rg flag spawn nexus-revive allow
/rg flag hospital nexus-revive-carry deny
/rg flag safezone nexus-revive-loot deny
```

---

## PlaceholderAPI

Identifier:

```text
%nexusrevive_<placeholder>%
```

Useful placeholders:

- `status`
- `death_delay`
- `invulnerability`
- `reviver`
- `picker`
- `attacker`
- `victim`
- `progress`
- `auto_revive_zone`
- `in_auto_revive_zone`
- `auto_reviving`
- `death_delay_bar`
- `invulnerability_bar`
- `progress_bar`

---

## API

Main access class:

```java
com.aquiles.nexusrevive.api.NexusReviveAPI
```

Useful methods:

- `isAvailable()`
- `isDowned(Player)`
- `isReviving(Player)`
- `getDownedPlayer(Player)`
- `getReviveVictim(Player)`
- `getDownedPlayers()`
- `revivePlayer(Player)`
- `revivePlayer(Player, Player)`
- `killDowned(Player)`
- `downPlayer(Player)`
- `downPlayer(Player, EntityDamageEvent.DamageCause)`
- `downPlayer(Player, Player)`

Events:

- `PlayerDownedEvent`
- `PlayerStartReviveEvent`
- `PlayerStopReviveEvent`
- `PlayerReviveEvent`
- `PlayerPickupDownedEvent`
- `PlayerDropDownedEvent`
- `PlayerFinalDeathEvent`

---

## Configuration Preview

<details>
<summary><strong>config.yml preview</strong></summary>

```yml
player:
  downed-health: 1.0
  death-delay-seconds: 75
  invulnerability-seconds: 15
  disconnect-kill: true

revive:
  duration-seconds: 10
  max-distance: 5.0
  require-item:
    enabled: false

carry:
  enabled: true
  drop-on-sneak: true

loot:
  enabled: true
  require-sneak: true
  single-robber-lock: true

commands:
  mode: BLACKLIST
  list: []
```

</details>

<details>
<summary><strong>messages.yml preview</strong></summary>

```yml
prefix: "&8[&bNexusRevive&8] "

victim:
  downed: "&cYou have been seriously injured!"
  revived: "&aYou have been revived!"

reviver:
  start: "&bYou are reviving <victim>"
  success: "&aYou successfully revived <victim>"

status:
  carried: "&3CARRIED"
  auto-revive: "&bAUTO-REVIVE"
  protected: "&ePROTECTED"
  critical: "&cCRITICAL"
```

</details>

<details>
<summary><strong>gps.yml preview</strong></summary>

```yml
mode: BOSSBAR_AND_HOLOGRAM

bossbar:
  title: "&b<victim> &8| &f<distance>m &8| &a<arrow>"
  arrive-distance: 5.0

hologram:
  enabled: true
  distance: 2.5
  scale: 1.2
  pointer-text: "▲"

menu:
  title: "&8Downed Players"
```

</details>

---

## Installation

1. Download the latest `NexusRevive.jar` from [Modrinth](https://modrinth.com/plugin/nexusrevive)
2. Place it inside your server `plugins/` folder
3. Start the server once
4. Edit `config.yml`, `messages.yml`, `gps.yml`, and `zones.yml`
5. Use `/nr reload` for quick reloads or restart the server after major changes

---

## Compatibility

- Minecraft `1.19.4` to `26.1`
- Paper
- Folia
- Leaf
- Purpur
- Pufferfish
- Other modern Paper-compatible forks

Not supported:

- Spigot

---

## Open Source

NexusRevive is open source and released under the **MIT License**.

If you use it, contribute to it, or build something from it, a star on GitHub is always appreciated.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).

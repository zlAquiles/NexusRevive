# NexusRevive
### Modern downed, revive, carry, loot, GPS, and zone system.

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
# ==========================================================
# NexusRevive - Main configuration
# ==========================================================
# This file controls the general plugin behavior:
# downed state, revive, carrying players, sounds, and hooks.
#
# Recommendation:
# 1. Change one section at a time.
# 2. Save the file.
# 3. Use /nr reload to apply simple changes.
# ==========================================================

mechanics:
  # Health the player will have after entering the downed state.
  # 1.0 = half a heart, 2.0 = one heart, etc.
  downed-health: 1.0

  # Movement speed while the player is downed.
  # 0.2 is normal vanilla player speed.
  downed-walk-speed: 0.06

  # Total time in seconds before the downed player dies permanently.
  death-delay-seconds: 75

  # Seconds during which a newly downed player cannot be finished off.
  invulnerability-seconds: 15

  # Health the player will have after being revived.
  revived-health: 6.0

  # Delay in ticks before applying the downed pose.
  # 20 ticks = 1 second.
  # Useful to show the hit impact before dropping to the ground.
  downed-entry-delay-ticks: 4

  # Horizontal knockback when entering the downed state.
  # Increase this for a more dramatic fall.
  downed-entry-knockback-horizontal: 0.16

  # Vertical knockback when entering the downed state.
  # Lower values make the fall look more natural.
  downed-entry-knockback-vertical: 0.08

  # What should happen when the downed timer runs out:
  # KILL   = die permanently
  # REVIVE = stand back up automatically
  death-action: KILL

  # If the player disconnects while downed:
  # true  = dies / state is cleared
  # false = keeps the downed state on reconnect
  kill-on-disconnect: true

  # Hide the death message when the player dies permanently while downed.
  disable-death-message-while-downed: true

revive:
  # Time in seconds required to complete a revive.
  duration-seconds: 10

  # Whether the reviver must keep sneaking to continue reviving.
  require-sneak: true

  # Allow a downed player to revive themselves.
  allow-self-revive: false

  # Maximum distance to start a revive.
  start-distance: 2.0

  # If the reviver moves farther than this, the revive is cancelled.
  cancel-distance: 4.5

  # Where players can be revived:
  # ANYWHERE      = anywhere
  # ONLY_IN_ZONES = only inside zones created with /nr zone
  zone-mode: ANYWHERE

  # Whether revive zones affect revive speed.
  # true  = the zone speed multiplier affects revive duration
  # false = every zone revives at the same speed
  zones-affect-speed: true

  auto-revive-finish-effect:
    # Visual/audio effect played when an auto-revive finishes inside a zone.
    enabled: true

    primary-particle:
      enabled: true
      particle: TOTEM_OF_UNDYING
      count: 32
      offset-x: 0.45
      offset-y: 0.70
      offset-z: 0.45
      extra: 0.0

    secondary-particle:
      enabled: true
      particle: HAPPY_VILLAGER
      count: 18
      offset-x: 0.40
      offset-y: 0.60
      offset-z: 0.40
      extra: 0.0

    sound:
      enabled: true
      sound: ITEM_TOTEM_USE
      volume: 0.9
      pitch: 1.15

  required-item:
    # Require a valid item in hand or offhand to revive.
    enabled: false

    # Consume 1 item from the matching stack on successful revive.
    consume-on-success: true

    # NexusRevive checks both main hand and offhand.
    # If either hand matches a rule, the revive is allowed.
    items:
      revive_kit:
        material: PAPER
        has_custom_model_data: false
        custom_model_data: 0
        name_contains: "&eRevive Kit"

carry:
  # Whether downed players can be carried.
  enabled: true

  # If the carrier takes damage:
  # true  = drop the downed player
  # false = keep carrying
  drop-on-picker-damage: false

  # Pause the death timer while the player is being carried.
  stop-death-timer-while-carried: false

  # Allow the downed player to dismount by themselves.
  allow-downed-dismount: false

loot:
  # Allow looting downed players with shift + right click.
  enabled: true

  # Temporary debug for the loot system.
  # true  = prints detailed logs when opening/clicking/closing the menu
  # false = hides those logs
  debug: false

  # Require sneaking while right-clicking to open loot.
  require-sneak: true

  # Allow only one player to keep a loot session open per victim.
  # Recommended: true, for a safer and more predictable flow.
  single-robber-lock: true

  # Allow looting a downed player while someone is reviving them.
  allow-while-reviving: false

  # Allow looting a downed player while someone is carrying them.
  allow-while-carried: false

  # Allow looting the main inventory and hotbar.
  allow-main-inventory: true

  # Allow looting armor slots.
  allow-armor: true

  # Allow looting the offhand slot.
  allow-offhand: true

persistence:
  # Save states.yml to disk.
  # true  = keeps downed states and pending deaths across restarts
  # false = does not load or save states.yml
  enabled: false

updater:
  # Check for updates on startup.
  # The update URL is hardcoded in the plugin and cannot be edited here.
  enabled: true

sounds:
  # Sound played while revive progress is happening.
  # cooldown-ticks prevents it from repeating too often.
  # 20 ticks = 1 second.
  reliving:
    enabled: true
    sound: BLOCK_NOTE_BLOCK_HARP
    volume: 1.0
    pitch: 1.0
    cooldown-ticks: 20

  # Sound played when a revive starts.
  start-reliving:
    enabled: true
    sound: BLOCK_NOTE_BLOCK_HARP
    volume: 1.0
    pitch: 1.0
    cooldown-ticks: 0

  # Sound played when a revive is stopped or cancelled.
  stop-reliving:
    enabled: true
    sound: BLOCK_NOTE_BLOCK_HARP
    volume: 1.0
    pitch: 1.0
    cooldown-ticks: 0

  # Sound played when a revive completes.
  success-relive:
    enabled: true
    sound: BLOCK_NOTE_BLOCK_HARP
    volume: 1.0
    pitch: 1.0
    cooldown-ticks: 0

downed-effects:
  # Apply potion effects while the player is downed.
  # These effects are removed automatically when the player revives,
  # dies, or leaves the downed state.
  enabled: true

  # You can remove, edit, or add entries here.
  # The "type" field accepts vanilla effect names such as:
  # SLOWNESS, WEAKNESS, DARKNESS, BLINDNESS, SLOW_FALLING, etc.
  effects:
    slowness:
      type: SLOWNESS
      amplifier: 6
      ambient: false
      particles: false
      icon: false

    weakness:
      type: WEAKNESS
      amplifier: 1
      ambient: false
      particles: false
      icon: false

    # Optional example:
    # darkness:
    #   type: DARKNESS
    #   amplifier: 0
    #   ambient: false
    #   particles: false
    #   icon: false

suicide:
  # Allow the downed player to commit suicide by holding shift.
  enabled: true

  # How many seconds the player must hold shift.
  hold-seconds: 5

restrictions:
  worlds:
    # World control:
    # BLACKLIST = works in every world except the listed ones
    # WHITELIST = works only in the listed worlds
    mode: BLACKLIST

    # World list used by the mode above.
    list:
      - example_world_disabled

  # Damage causes ignored by the downed system.
  # Example: if you add VOID, falling into the void will kill normally.
  ignored-damage-causes:
    - VOID

downed-interactions:
  # Allow movement while downed.
  allow-move: true

  # Allow general block interaction while downed.
  allow-interact: false

  # Allow entity interaction while downed.
  allow-entity-interact: false

  # Allow breaking blocks while downed.
  allow-block-break: false

  # Allow placing blocks while downed.
  allow-block-place: false

  # Allow teleports or teleport-like commands while downed.
  allow-teleport: false

  # Allow shooting / throwing projectiles while downed.
  allow-projectiles: false

  # Allow eating or drinking consumable items while downed.
  allow-consume: false

  # Allow moving items in the main inventory and hotbar while downed.
  # This does not control offhand or armor slots.
  allow-inventory: false

  # Allow using or swapping the offhand slot while downed.
  allow-offhand: false

  # Allow changing the helmet slot while downed.
  allow-helmet: false

  # Allow changing the chestplate slot while downed.
  allow-chestplate: false

  # Allow changing the leggings slot while downed.
  allow-leggings: false

  # Allow changing the boots slot while downed.
  allow-boots: false

  # Allow dropping items while downed.
  allow-item-drop: false

  # Allow picking up items while downed.
  allow-item-pickup: false

  # Allow using elytra while downed.
  allow-gliding: false

  # Allow taking fall damage while downed.
  allow-fall-damage: false

  # Maximum fall distance allowed for downing.
  # -1 = unlimited
  # If the player falls farther than this, they will not enter downed state.
  max-fall-distance: -1

commands:
  # Command control while the player is downed.
  # BLACKLIST = blocks only the listed commands
  # WHITELIST = allows only the listed commands
  #
  # /nr and /nexusrevive are always allowed separately.
  mode: BLACKLIST
  list: []

hooks:
  vault:
    # Vault hook for revive cost.
    # Works only if Vault and an economy plugin are installed.
    enabled: true

    # Revive cost.
    # 0.0 = free
    revive-cost: 0.0

  worldguard:
    # WorldGuard hook.
    enabled: true

    # NexusRevive registers these region flags:
    # nexus-revive
    # nexus-revive-carry
    # nexus-revive-loot
    #
    # nexus-revive:
    # Controls only reviving.
    #
    # nexus-revive-carry:
    # Controls only carrying downed players.
    #
    # nexus-revive-loot:
    # Controls only looting downed players.
    #
    # If a flag is ALLOW, the action is allowed there.
    # If a flag is DENY, the action is blocked there.
    # If a flag is not defined, NexusRevive will not block that action by WorldGuard.
    #
    # Example:
    # /rg flag <region> nexus-revive allow
    # /rg flag <region> nexus-revive-carry deny
    # /rg flag <region> nexus-revive-loot deny

  deluxecombat:
    # DeluxeCombat hook.
    enabled: true

    # Allow reviving while one of the players is in combat.
    allow-revive-in-combat: true

    # Allow carrying while one of the players is in combat.
    allow-carry-in-combat: false

    # Respect DeluxeCombat PvP protection when trying to down players.
    respect-pvp-protection: true

  cmi:
    # CMI hook.
    enabled: true

    # Prevent players protected by CMI god mode from being downed.
    respect-god-mode: true

    # Hide vanished CMI players from GPS and downed interactions
    # unless the viewer can already see them.
    respect-vanish: true

  essentials:
    # EssentialsX hook.
    enabled: true

    # Prevent players protected by Essentials god mode from being downed.
    respect-god-mode: true

    # Hide vanished Essentials players from GPS and downed interactions
    # unless the viewer can already see them.
    respect-vanish: true

  supervanish:
    # SuperVanish hook.
    enabled: true

    # Hide vanished SuperVanish players from GPS and downed interactions
    # unless the viewer can already see them.
    respect-vanish: true

  qualityarmory:
    # QualityArmory hook.
    # Improves attacker detection for non-vanilla weapon damage.
    enabled: true

  weaponmechanics:
    # WeaponMechanics hook.
    # Improves attacker detection for custom weapon damage.
    enabled: true

scoreboard:
  # Show a personal NexusRevive scoreboard.
  enabled: true

  # Update interval in ticks.
  # 10 ticks = 0.5 seconds.
  update-interval-ticks: 10

  # Show a scoreboard for the downed player.
  show-for-downed: true

  # Show a scoreboard for the player who is reviving.
  show-for-reviver: true

  # Show a scoreboard for the player carrying a downed target.
  show-for-picker: true

events:
  # Command-based event system.
  # Each entry accepts a list of commands.
  #
  # Supported senders:
  # [console] = run as console
  # [victim]  = run as the downed player
  # [attacker]= run as the attacker if present
  # [reviver] = run as the reviver
  # [picker]  = run as the carrier
  #
  # Available placeholders:
  # <victim> <attacker> <reviver> <picker>
  # <world> <x> <y> <z>
  #
  # Example:
  # - "[console] broadcast &c<victim> has been downed in <world> (<x>, <y>, <z>)"

  player-downed:
    # Fired when a player enters the downed state.
    enabled: false
    commands:
      - "[console] say <victim> has been downed."

  player-start-revive:
    # Fired when someone starts reviving a downed player.
    enabled: false
    commands:
      - "[console] say <reviver> started reviving <victim>."

  player-stop-revive:
    # Fired when a revive is interrupted or cancelled.
    enabled: false
    commands:
      - "[console] say The revive of <victim> was cancelled."

  player-revived:
    # Fired when a revive completes successfully.
    enabled: false
    commands:
      - "[console] say <victim> has been revived."

  player-picked-up:
    # Fired when a player carries a downed target.
    enabled: false
    commands:
      - "[console] say <picker> is carrying <victim>."

  player-dropped:
    # Fired when a carried downed target is dropped.
    enabled: false
    commands:
      - "[console] say <picker> dropped <victim>."

  player-final-death:
    # Fired when a downed player dies permanently.
    enabled: false
    commands:
      - "[console] say <victim> died permanently."

# ==========================================================
# PlaceholderAPI
# ==========================================================
# If PlaceholderAPI is installed, NexusRevive registers
# internal placeholders with the identifier:
# %nexusrevive_<placeholder>%
#
# Available:
# %nexusrevive_status%
# %nexusrevive_death_delay%
# %nexusrevive_invulnerability%
# %nexusrevive_reviver%
# %nexusrevive_picker%
# %nexusrevive_attacker%
# %nexusrevive_victim%
# %nexusrevive_progress%
# %nexusrevive_auto_revive_zone%
# %nexusrevive_in_auto_revive_zone%
# %nexusrevive_auto_reviving%
# %nexusrevive_death_delay_bar%
# %nexusrevive_invulnerability_bar%
# %nexusrevive_progress_bar%
#
# Note:
# If persistence.enabled is true, NexusRevive saves
# temporary states in states.yml to support reconnects,
# restarts, and pending deaths.
```

</details>

<details>
<summary><strong>messages.yml preview</strong></summary>

```yml
general:
  prefix: "&8[&b&lNexusRevive&8] &7"
  no-permission: "&#ff5555You do not have permission to do that."
  only-player: "&#ff5555Only a player can use this command."
  player-not-found: "&#ff5555That player could not be found online."
  blocked-command: "&#ff5555You are downed. Use /nr or wait for help."
  reloaded: "&#55ff55Configurations reloaded successfully."

victim:
  downed: |-
    &#ff6b6bYou are downed.
    &7You need help to get back on your feet.
    &8- &fFinal death in: &c<death_delay>s
    &8- &fSafe window: &e<invulnerability>s
  reconnected-downed: "&#ffaa00You are still downed. You have &#ffffff<death_delay>s &#ffaa00left to receive help."
  start-revive: "&#55ffff<reviver> has started reviving you."
  stop-revive: "&#aaaaaa<reviver> interrupted the revive."
  revived: "&#55ff55You have been revived successfully."
  picked-up: "&#55ffff<picker> is carrying you."
  dropped: "&#ffaa00You were placed back on the ground."

attacker:
  downed: "&#ff5555You downed &#ffffff<victim>&#ff5555."

reviver:
  start: "&#55ffffYou started reviving &#ffffff<victim>&#55ffff."
  stop: "&#aaaaaaThe revive of &#ffffff<victim> &#aaaaaawas cancelled."
  success: "&#55ff55You revived &#ffffff<victim>&#55ff55."
  zone-required: "&#ff5555That player must be inside a revive zone."
  carry-blocked: "&#ff5555You cannot revive a player while carrying them."
  item-required: "&#ff5555You need a valid revive item in your hand or offhand."

picker:
  pickup: "&#55ffffYou are carrying &#ffffff<victim>&#55ffff."
  drop: "&#ffaa00You dropped &#ffffff<victim>&#ffaa00."

loot:
  title: "&8Looting &c<victim>"
  head-title: "&c<victim>"
  head-lore:
    - "&7World: &f<world>"
    - "&7X: &f<x> &7Y: &f<y> &7Z: &f<z>"
  opened: "&#55ffffYou are looting &#ffffff<victim>&#55ffff."
  busy: "&#ff5555That downed player is already being looted by someone else."
  no-space: "&#ffaa00You do not have enough space to steal that stack."
  blocked-reviving: "&#ff5555You cannot loot a downed player while they are being revived."
  blocked-carried: "&#ff5555You cannot loot a downed player while they are being carried."
  target-closed: "&#aaaaaaThe loot session was closed because the target state changed."
  robber-closed: "&#aaaaaaLoot session closed."
  nothing-to-steal: "&#ffaa00That slot no longer has anything to steal."
  cannot-loot-self: "&#ff5555You cannot loot yourself."

command:
  help: |-
    &b&lNexusRevive &7available commands
    &8
    &f/nr reload &8- &7Reload config, messages, gps, and zones.
    &f/nr down <player> &8- &7Force the downed state.
    &f/nr revive <player> &8- &7Revive a downed player.
    &f/nr kill <player> &8- &7Finish a downed player.
    &f/nr gps &8- &7Open the downed player tracker.
    &f/nr zone wand|create|remove|list|speed &8- &7Manage revive zones.
  downed: "&#55ff55Target downed: &#ffffff<victim>&#55ff55."
  already-downed: "&#ffaa00Could not down &#ffffff<victim>&#ffaa00."
  revived: "&#55ff55Target revived: &#ffffff<victim>&#55ff55."
  killed: "&#ff5555Target finished: &#ffffff<victim>&#ff5555."
  not-downed: "&#ffaa00That player is not downed."

actionbar:
  victim-waiting: "&cDOWNED &8| <status> &8| &fEnds in: &c<death_delay>s <death_delay_bar> &8| &fGrace: &e<invulnerability>s <invulnerability_bar>"
  victim-reviving: "&bREVIVING &8| &f<reviver> &8| &a<progress>% <progress_bar>"
  reviver: "&bREVIVING &f<victim> &8| &a<progress>% <progress_bar>"
  suicide: "&4GIVING UP &8| &c<suicide_seconds>s <suicide_bar>"

status:
  carried: "&3CARRIED"
  auto-revive: "&bAUTO-REVIVE"
  protected: "&ePROTECTED"
  critical: "&cCRITICAL"

bars:
  death-delay:
    format: "&8[<filled><empty>&8]"
    length: 12
    filled: "■"
    empty: "■"
    filled-color: "&c"
    empty-color: "&8"
  invulnerability:
    format: "&8[<filled><empty>&8]"
    length: 8
    filled: "■"
    empty: "■"
    filled-color: "&e"
    empty-color: "&8"
  progress:
    format: "&8[<filled><empty>&8]"
    length: 12
    filled: "■"
    empty: "■"
    filled-color: "&a"
    empty-color: "&8"
  suicide:
    format: "&8[<filled><empty>&8]"
    length: 12
    filled: "■"
    empty: "■"
    filled-color: "&c"
    empty-color: "&8"

scoreboard:
  victim-title: "&#ff7b7b&lDOWNED"
  victim-waiting:
    - ""
    - " &fStatus: <status>"
    - " &fFinal death: &c<death_delay>s"
    - " <death_delay_bar>"
    - ""
    - " &fProtection: &e<invulnerability>s"
    - " <invulnerability_bar>"
  victim-reviving:
    - ""
    - " &fStatus: <status>"
    - " &fReviver: &b<reviver>"
    - " &fProgress: &a<progress>%"
    - " <progress_bar>"
    - ""
    - " &fFinal death: &c<death_delay>s"
    - " <death_delay_bar>"
  reviver-title: "&#55ffff&lREVIVE"
  reviver-active:
    - " &7Assist in progress"
    - " &fTarget: &b<victim>"
    - " &fDistance: &e<distance>m"
    - " &fProgress: &a<progress>%"
    - " <progress_bar>"
    - " &fTarget status: <status>"
    - " &7Keep sneaking to finish."
  picker-title: "&#55aaff&lCARRY"
  picker-carrying:
    - ""
    - " &fTarget: &b<victim>"
    - " &fStatus: <status>"
    - " &fFinal death: &c<death_delay>s"
    - " <death_delay_bar>"
    - " &fProtection: &e<invulnerability>s"
    - " &7Press shift to drop them."

zone:
  help: |-
    &b&lRevive zones
    &8
    &f/nr zone wand &8- &7Gives you the selection axe.
    &f/nr zone create <name> &8- &7Creates a zone with the current selection.
    &f/nr zone remove <name> &8- &7Deletes a zone.
    &f/nr zone list &8- &7Lists registered zones.
    &f/nr zone speed <name> <value> &8- &7Changes the revive speed.
  wand-name: "&6Zone Wand"
  wand-lore:
    - "&7Left click block: &fPos 1"
    - "&7Right click block: &fPos 2"
    - "&7Use &b/nr zone create <name>"
  wand-given: "&#55ff55You received the &6Zone Wand&#55ff55. Use left and right click on blocks."
  pos1: "&#55ff55Position 1 saved at <x>, <y>, <z>."
  pos2: "&#55ff55Position 2 saved at <x>, <y>, <z>."
  need-selection: "&#ff5555You must mark pos1 and pos2 with the &6Zone Wand &#ff5555before creating the zone."
  selection-ready: "&#55ffffSelection ready. Use &#ffffff/nr zone create <name>&#55ffff."
  world-mismatch: "&#ff5555Both positions must be in the same world."
  created: "&#55ff55Zone created: &#ffffff<name>&#55ff55."
  create-failed: "&#ff5555Could not create zone &#ffffff<name>&#ff5555."
  removed: "&#ffaa00Zone removed: &#ffffff<name>&#ffaa00."
  not-found: "&#ff5555That zone does not exist."
  list: "&#55ffffRegistered zones: &#ffffff<zones>"
  speed-updated: "&#55ff55Zone &#ffffff<name> &#55ff55now uses multiplier &#ffffff<speed>&#55ff55."
  invalid-speed: "&#ff5555That multiplier is not valid."

gps:
  started: "&#55ff55You are now tracking &#ffffff<victim>&#55ff55."
  stopped: "&#ffaa00GPS disabled."
  arrived: "&#55ff55You have reached the target."
  no-downed: "&#ff5555There are no downed players right now."
  target-lost: "&#ff5555That target is no longer downed."

hooks:
  in-combat: "&#ff5555You cannot do that while in combat."
  region-blocked: "&#ff5555The current region blocks this action."
  not-enough-money: "&#ff5555You need &#ffffff<amount> &#ff5555to complete that revive."
  target-hidden: "&#ff5555That player is hidden and cannot be targeted right now."
```

</details>

<details>
<summary><strong>gps.yml preview</strong></summary>

```yml
display-mode: BOSSBAR_AND_HOLOGRAM
# Available modes:
# NONE                  = only update the compass, no extra HUD
# BOSSBAR               = show bossbar only
# HOLOGRAM              = show hologram only
# BOSSBAR_AND_HOLOGRAM  = bossbar + hologram
# ACTIONBAR             = show actionbar only

# GPS update interval.
# 4 ticks = 0.2 seconds
update-interval-ticks: 4

bossbar:
  # Placeholders:
  # <victim>   = downed player's name
  # <distance> = formatted distance, for example "34m" or "other world"
  # <arrow>    = arrow based on your direction
  format: "&b<victim> &8| &f<distance> &8| &a<arrow>"

  arrows:
    north: "↑"
    north-east: "↗"
    east: "→"
    south-east: "↘"
    south: "↓"
    south-west: "↙"
    west: "←"
    north-west: "↖"

  progress: 1.0
  arrive-distance: 5.0
  other-world-text: "other world"

actionbar:
  format: "&bGPS &8| &f<distance> &8| &a<arrow>"

hologram:
  # The hologram uses a TextDisplay that follows the tracking player.
  # Placeholders:
  # <victim>   = downed player's name
  # <distance> = distance to the target
  # <pointer>  = single hologram pointer
  format: "&a<pointer> &f<distance>"
  pointer-text: "▲"
  distance: 10.0
  offset-height: 0.15
  scale: 3.0
  interpolation-duration: 4
  shadowed: true
  see-through: false
  default-background: false

gui:
  title: "&0Nexus GPS"

  # Menu layout.
  # # = border
  # . = filler
  # + = accent
  # I = info item
  # T = downed player head slot
  # E = reserved slot for the "no targets" item
  # P = previous page
  # N = next page
  # C = close
  layout:
    - "###+I+###"
    - "#TTTTTTT#"
    - "#TTTTTTT#"
    - "#TTEEETT#"
    - "#TTTTTTT#"
    - "#P..C..N#"

  items:
    border:
      material: GRAY_STAINED_GLASS_PANE
      name: " "

    filler:
      material: BLACK_STAINED_GLASS_PANE
      name: " "

    accent:
      material: CYAN_STAINED_GLASS_PANE
      name: " "

    info:
      material: COMPASS
      name: "&b&lNexus GPS"
      lore:
        - "&7Visible downed players: &f<targets>"
        - "&7Page: &f<page>&7/&f<pages>"
        - "&7Current target: &f<active_target>"
        - "&8Click a head to start tracking."

    empty:
      material: RECOVERY_COMPASS
      name: "&cNo downed targets"
      lore:
        - "&7When a player goes down,"
        - "&7they will appear in this panel."

    previous:
      material: SPECTRAL_ARROW
      name: "&7Previous page"
      lore:
        - "&8Click to go back."

    previous-disabled:
      material: GRAY_DYE
      name: "&8Previous page"
      lore:
        - "&7You are already on the first page."

    next:
      material: SPECTRAL_ARROW
      name: "&7Next page"
      lore:
        - "&8Click to go forward."

    next-disabled:
      material: GRAY_DYE
      name: "&8Next page"
      lore:
        - "&7You are already on the last page."

    close:
      material: BARRIER
      name: "&cClose"
      lore:
        - "&8Closes this menu."

  target:
    name: "&c<victim>"
    lore:
      - "&7World: &f<world>"
      - "&7X: &f<x> &7Y: &f<y> &7Z: &f<z>"
      - "&eClick to track"
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

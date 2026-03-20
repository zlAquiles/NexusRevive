# NexusRevive

`NexusRevive` es un plugin de sistema abatido.

## Requisitos

- Paper o fork compatible con Paper
- Java 21
- Minecraft `1.21.x` afinado de forma principal
- Version minima del plugin: `1.19.4+` con fallback temporal fuera de la familia `1.21.x`

## Funciones principales

- Estado abatido en lugar de muerte inmediata
- Revive manual con `shift`
- Auto-revive dentro de zonas
- Cargar abatidos sobre la cabeza
- GPS con `bossbar`, `actionbar` o `holograma`
- Saqueo configurable del inventario del abatido
- Scoreboard del estado abatido
- Hooks con Vault, WorldGuard, DeluxeCombat y PlaceholderAPI

## Instalacion

1. Copia el jar en `plugins/`
2. Inicia el servidor una vez
3. Edita `config.yml`, `messages.yml`, `gps.yml` y `zones.yml`
4. Usa `/nr reload` para recargar cambios simples

## Comandos

- `/nr`
- `/nr reload`
- `/nr down <jugador>`
- `/nr revive <jugador>`
- `/nr kill <jugador>`
- `/nr gps`
- `/nr zone help`
- `/nr zone wand`
- `/nr zone create <nombre>`
- `/nr zone remove <nombre>`
- `/nr zone list`
- `/nr zone speed <nombre> <multiplicador>`

## Permisos

- `nexusrevive.command.reload`
- `nexusrevive.command.down`
- `nexusrevive.command.revive`
- `nexusrevive.command.kill`
- `nexusrevive.command.gps`
- `nexusrevive.command.zone`
- `nexusrevive.revivable`
- `nexusrevive.reviver`
- `nexusrevive.pickable`
- `nexusrevive.picker`
- `nexusrevive.lootable`
- `nexusrevive.robber`
- `nexusrevive.downable`

## WorldGuard Flags

- `nexus-revive`
  - controla solo el revive
- `nexus-revive-carry`
  - controla solo cargar abatidos
- `nexus-revive-loot`
  - controla solo saquear abatidos

Ejemplos:

```text
/rg flag spawn nexus-revive allow
/rg flag warzone nexus-revive-carry deny
/rg flag hospital nexus-revive-loot deny
```

## PlaceholderAPI

Identificador:

```text
%nexusrevive_<placeholder>%
```

Placeholders disponibles:

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

## API

Clase principal:

```java
com.aquiles.nexusrevive.api.NexusReviveAPI
```

Metodos utiles:

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

Eventos:

- `PlayerDownedEvent`
- `PlayerStartReviveEvent`
- `PlayerStopReviveEvent`
- `PlayerReviveEvent`
- `PlayerPickupDownedEvent`
- `PlayerDropDownedEvent`
- `PlayerFinalDeathEvent`

## Compatibilidad

- Paper: soportado
- Leaf: soportado
- Purpur / Pufferfish: normalmente compatibles si mantienen compatibilidad Paper
- Spigot: no soportado
- Folia: no soportado

## Notas

- `states.yml` solo se usa si `persistence.enabled: true`

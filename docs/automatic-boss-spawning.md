# Automatic boss spawning and respawn

This is an **opt-in** feature of the `bosses` module. Existing servers
continue to use `/christmas boss spawn grinch` unchanged.

In `plugins/UltimateChristmas/modules/bosses.yml`, under *each* boss
(`grinch`, `krampus`, `frost_monarch`, or a custom boss):

```yaml
auto-spawn:
  enabled: true
  interval-minutes: 60
  respawn-after-death-minutes: 30
  max-alive: 1
  announce: true
  locations:
    - { world: world, x: 100, y: 65, z: -50 }
    - "world,200,65,100"
```

- The initial encounter happens after the configured interval while the season
  is active and at least one player is online.
- The location is chosen randomly from the list; only loaded worlds are valid.
- A killed boss gets its own respawn countdown, starting at death.
- `max-alive` caps **automatic** spawns for the same boss type; manual commands
  are intentionally unchanged.
- `announce: false` suppresses the automatic spawn broadcast/taunts.
- A legacy `spawn: { world, x, y, z }` location is used if `locations`
  is empty.
- Disable or update a policy without restarting the entire plugin:
  `/christmas module reload bosses`.

The timer is module-owned; stopping the module cancels future spawning.
World creation runs on Paper's location-region scheduler.

This does not yet support WorldGuard entry triggers or a calendar scheduler;
those are separate changes.

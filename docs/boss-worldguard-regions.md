# Boss encounters triggered by WorldGuard entry

This optional feature builds on the automatic boss spawning PR.

Add the following under any boss in `modules/bosses.yml`:

```yaml
region-trigger:
  enabled: true
  regions: [christmas_arena, winter_forest]
  cooldown-seconds: 600
  max-alive: 1
```

- Requires **WorldGuard**. Without it, this feature is skipped and logs a warning.
- Spawn is triggered only by *entering* one of the configured regions, not
  moving inside it. Each boss/region pair has its own cooldown.
- The destination is selected from `auto-spawn.locations`, then the existing
  legacy `spawn` location, and lastly the player's region-entry coordinates.
- The automatic timer and the region trigger can be enabled independently;
  `auto-spawn.enabled: false` still allows locations to be defined for triggers.
- Requires an active Christmas season.
- Reload with `/christmas module reload bosses`. Disabling the module clears
  cached region cooldowns.
- `max-alive` limits the number of active instances of that boss created by
  region events (manual commands still work).

WorldGuard does not automatically find a good arena spawn point; choose
reachable locations in a loaded world.

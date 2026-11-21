# Christmas event scheduler

The `events` module starts and ends activities by dispatching configured
console commands. It is independent of boss, Santa, or snow internals.

`plugins/UltimateChristmas/modules/events.yml` supports two timing modes:

**Repeated daily times:** `start-time`, `end-time` (24h HH:mm),
`days: [FRIDAY, SATURDAY]`, and optional `timezone` override.
Overnight ranges are supported, with `days` representing the start day.

**One-shot windows:** `start-at`, `end-at` in ISO-8601 format.
Prefer explicit offsets, e.g. `2026-12-24T18:00:00+01:00`.

```yaml
enabled: true
timezone: Europe/Paris
events:
  winter_boss:
    enabled: true
    season-only: true
    days: [FRIDAY, SATURDAY]
    start-time: "18:00"
    end-time: "20:00"
    start-commands: ["christmas boss spawn grinch"]
    end-commands: ["christmas boss despawn grinch"]
```

Admin commands:
- `/christmas events list` — current schedules and status
- `/christmas events start <id>` — force start this session
- `/christmas events stop <id>` — force stop this session
- `/christmas events auto <id>` — return to automatic calendar control
- `/christmas events enable|disable <id>` — persisted into events.yml
- `/christmas module reload events` — re-read the file

Events run as the server console; configure only trusted commands.
When the module is stopped, active events run their end commands.
Unconfigured example events remain **disabled** by default.
These windows are checked every second while the module is enabled.

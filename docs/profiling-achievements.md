# Achievements Profiling Evidence

## Profiling Run

Date: May 22, 2026

Target: `service-achievements` running locally on port `18083` from the
`bootJar` artifact, with an in-memory H2 database and security bypass enabled.

The workload exercised the main user-facing read paths and a failing reward
claim path:

- `GET /api/achievements?userId=<uuid>`
- `GET /api/achievements/daily-missions/active?userId=<uuid>`
- `POST /api/achievements/daily-missions/{missionId}/claim?userId=<uuid>`
- `POST /api/achievements/admin`
- `POST /api/achievements/admin/daily-missions`

## Evidence Files

- Raw JFR recording: [`profiling/achievements-runtime.jfr`](profiling/achievements-runtime.jfr)
- JFR event summary: [`profiling/jfr-summary.txt`](profiling/jfr-summary.txt)
- Hot methods view: [`profiling/jfr-hot-methods.txt`](profiling/jfr-hot-methods.txt)
- Allocation by class: [`profiling/jfr-allocation-by-class.txt`](profiling/jfr-allocation-by-class.txt)
- Prometheus snapshot: [`profiling/prometheus-metrics-snapshot.txt`](profiling/prometheus-metrics-snapshot.txt)
- JFR dump command output: [`profiling/jfr-dump-command.txt`](profiling/jfr-dump-command.txt)

## Process Justification

Java Flight Recorder was used because it profiles JVM CPU samples, allocation
pressure, GC, and runtime events with low overhead. That makes it appropriate for
a Spring Boot service where the relevant risks are CPU hotspots, allocation
pressure, database access, request latency, and GC behavior.

Prometheus metrics were captured from `/actuator/prometheus` during the same run
because they show service-level latency, throughput, and error counters from the
same instrumentation used in staging. JFR explains where time and allocation go
inside the JVM; Prometheus explains how the service behaves from the runtime API
surface.

## Observed Results

Prometheus captured the following workload counts:

- `GET /api/achievements`: 200 successful requests.
- `GET /api/achievements/daily-missions/active`: 200 successful requests.
- `POST /api/achievements/daily-missions/{missionId}/claim`: 50 expected 404
  failures using random mission IDs.
- Admin creation endpoints were exercised for both success and duplicate
  conflict paths.

Average local action latency from the Prometheus snapshot:

| Action | Count | Sum seconds | Approx average |
| :-- | --: | --: | --: |
| `list_achievement_progress` | 200 | 0.5597245 | 2.80 ms |
| `list_active_daily_missions` | 200 | 0.5771202 | 2.89 ms |
| `claim_daily_mission_reward` failure path | 50 | 0.1523318 | 3.05 ms |
| `create_achievement` success path | 1 | 0.0081141 | 8.11 ms |
| `create_daily_mission` success path | 1 | 0.0066290 | 6.63 ms |

## Apdex Score

The Apdex score for this profiling run is **1.000**.

This score uses `T = 500 ms` because the Achievements monitoring SLA defines
normal action latency as `p95 < 500 ms`. Requests at or below `T` are counted as
satisfied, requests between `T` and `4T` (`2 s`) are counted as tolerating, and
requests above `4T` are counted as frustrated.

Formula:

```text
Apdex = (satisfied + (tolerating / 2)) / total
```

Based on `yomu_achievements_action_duration_seconds` in the Prometheus snapshot:

| Scope | Total samples | Satisfied `<= 500 ms` | Tolerating `> 500 ms and <= 2 s` | Frustrated `> 2 s` | Apdex |
| :-- | --: | --: | --: | --: | --: |
| API profiling workload | 454 | 454 | 0 | 0 | 1.000 |
| Full action timer snapshot, including one scheduler run | 455 | 455 | 0 | 0 | 1.000 |

The highest observed action duration was `rotate_daily_missions` at `19.82 ms`,
and the slowest API action duration was `claim_daily_mission_reward` failure at
`8.95 ms`. Because all observed action durations are below `500 ms`, every
sample in this local profiling run is classified as satisfied.

JFR hot methods were dominated by Spring Boot executable-jar startup and URL/JAR
loading work, including `java.net.URL.<init>`, `sun.net.www.ParseUtil`, and
Spring Boot loader methods. Allocation pressure was dominated by `byte[]`,
`String`, and `URL` related objects.

## Analysis And Improvements

The local read endpoints are fast with the small H2 profiling dataset, but this
does not prove production scalability. The important risks are in paths that
scale with achievement, mission, and progress row counts.

Recommended improvements:

1. Add database indexes for `achievements(metric, active)`,
   `daily_missions(metric, active_from, active_until)`, and progress lookup keys.
2. Reduce potential N+1 query behavior in `updateAchievementAndMissionProgress`
   by batch-loading progress states for all matching achievements and missions.
3. Make reward claim concurrency safer with a conditional update such as
   `WHERE claimed_at IS NULL`, then publish the reward event only after the
   update succeeds.
4. Consider after-commit publishing or an outbox pattern for achievement unlock
   and mission reward events so RabbitMQ latency does not stay inside the main
   transaction path.
5. Split future profiling into startup profiling and steady-state load profiling.
   This run includes service startup, so JFR is useful as proof and a first pass,
   but a longer steady-state run would expose business-method hotspots more
   clearly.

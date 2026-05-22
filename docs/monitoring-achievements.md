# Achievements Monitoring

## Scope

This monitoring change instruments `service-achievements` with custom Micrometer
metrics in addition to the default Spring Boot Actuator metrics already exposed
at `/actuator/prometheus`.

## Design Justification

The service uses Spring Actuator, Micrometer, Prometheus, and Grafana because the
Yomu stack already provisions Prometheus scraping and Grafana dashboards for
service observability. This keeps the monitoring path consistent with the rest
of the microservices and avoids adding another runtime dependency.

Custom metrics are intentionally low-cardinality. Tags use bounded values such
as action name, outcome, and `AchievementMetric` enum values. They do not include
`userId`, `achievementId`, `missionId`, or source event IDs, because those values
would create unbounded Prometheus series and make the monitoring system harder to
operate.

Counters are used for traffic and event volume. Timers are used for latency on
business actions. The event counters distinguish `new`, `duplicate`, and
`invalid_user_id` outcomes so idempotent consumer behavior can be observed.

## Metrics

| Metric | Type | Labels | Purpose |
| :-- | :-- | :-- | :-- |
| `yomu_achievements_actions_total` | Counter | `action`, `outcome` | Counts service-level actions and failures. |
| `yomu_achievements_action_duration_seconds` | Timer histogram | `action`, `outcome` | Tracks action latency and supports percentile queries. |
| `yomu_achievements_activity_events_total` | Counter | `metric`, `outcome` | Tracks consumed activity events and duplicate suppression. |
| `yomu_achievements_unlocks_total` | Counter | `metric` | Counts achievement unlocks by metric source. |
| `yomu_achievements_daily_mission_completions_total` | Counter | `metric` | Counts daily mission completions by metric source. |

## Example Usage

Check whether Prometheus can scrape the service:

```powershell
Invoke-WebRequest http://<STAGING_HOST>:8083/actuator/prometheus
```

PromQL examples:

```promql
rate(yomu_achievements_actions_total{outcome="failure"}[5m])
```

```promql
histogram_quantile(
  0.95,
  sum(rate(yomu_achievements_action_duration_seconds_bucket[5m])) by (le, action)
)
```

```promql
increase(yomu_achievements_activity_events_total{outcome="duplicate"}[1h])
```

```promql
increase(yomu_achievements_unlocks_total[1h])
```

```promql
increase(yomu_achievements_daily_mission_completions_total[1h])
```

## Expected Operational Signals

- A spike in `yomu_achievements_actions_total{outcome="failure"}` indicates
  broken API usage, invalid state transitions, or downstream persistence issues.
- A sustained rise in duplicate activity events means RabbitMQ redelivery or
  upstream duplicate publication is happening, but idempotency is still working.
- A drop in unlocks or daily mission completions while traffic remains normal can
  indicate event listener, repository, or mission rotation problems.
- High p95 action latency points to repository queries or transaction work that
  should be profiled further.

# Staging Deployment Links

Staging is deployed on the team infrastructure host:

```text
yomu-infra.duckdns.org
```

The service runs inside the Docker Compose network. Public traffic is intended to
enter through the API Gateway on port `8090`. Direct access to the
`service-achievements` container port `8083` is available from inside the server,
but it may be blocked externally by the staging firewall/security group.

## Service Links

| Service | Staging health link | Main runtime link |
| :-- | :-- | :-- |
| API Gateway | `http://yomu-infra.duckdns.org:8090/actuator/health` | `http://yomu-infra.duckdns.org:8090` |
| Auth Service | internal Docker target: `service-auth:8081/actuator/health` | `http://yomu-infra.duckdns.org:8090/api/auth` |
| Learning Service | internal Docker target: `service-learning:8082/actuator/health` | `http://yomu-infra.duckdns.org:8090/api/learning` |
| Achievements Service | internal Docker target: `service-achievements:8083/actuator/health` | `http://yomu-infra.duckdns.org:8090/api/achievements` |
| Forum Service | internal Docker target: `service-forum:8084/actuator/health` | `http://yomu-infra.duckdns.org:8090/api/forum` |
| Clan Service | internal Docker target: `service-clan:8085/actuator/health` | `http://yomu-infra.duckdns.org:8090/api/clan` |
| Notification Service | internal Docker target: `service-notification:8086/actuator/health` | `http://yomu-infra.duckdns.org:8090/api/notifications` |
| Prometheus | internal: `http://localhost:9090/-/healthy` on the server | `http://localhost:9090/targets` on the server |
| Grafana | `http://yomu-infra.duckdns.org:3000/api/health` | `http://yomu-infra.duckdns.org:3000/dashboards` |

## Achievements Links To Submit

- Gateway health: `http://yomu-infra.duckdns.org:8090/actuator/health`
- Gateway route: `http://yomu-infra.duckdns.org:8090/api/achievements`
- Grafana health: `http://yomu-infra.duckdns.org:3000/api/health`
- Internal Achievements health on server: `http://localhost:8083/actuator/health`
- Internal Achievements Prometheus metrics on server: `http://localhost:8083/actuator/prometheus`
- Internal Prometheus query on server: `http://localhost:9090/api/v1/query?query=yomu_achievements_actions_total`

## Deployment Source

The Achievements service is deployed through the production compose image:

```text
ghcr.io/advprog-2026-a15-project/service-achievements:latest
```

The infra compose exposes `service-achievements` on port `8083` and the
Prometheus scrape job targets `service-achievements:8083/actuator/prometheus`.

Verified staging result:

```text
service-achievements health: UP
yomu_achievements_actions_total{action="rotate_daily_missions",instance="service-achievements:8083",job="yomu-services",outcome="success"} = 1
```

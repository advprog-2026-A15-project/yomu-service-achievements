# Staging Deployment Links

The public staging host is not stored in this repository, in GitHub deployment
metadata, or in local environment variables. Replace `<STAGING_HOST>` with the
team's staging host or EC2 public DNS/IP when submitting these links.

## Service Links

| Service | Staging health link | Main runtime link |
| :-- | :-- | :-- |
| API Gateway | `http://<STAGING_HOST>:8090/actuator/health` | `http://<STAGING_HOST>:8090` |
| Auth Service | `http://<STAGING_HOST>:8081/actuator/health` | `http://<STAGING_HOST>:8081` |
| Learning Service | `http://<STAGING_HOST>:8082/actuator/health` | `http://<STAGING_HOST>:8082` |
| Achievements Service | `http://<STAGING_HOST>:8083/actuator/health` | `http://<STAGING_HOST>:8083/api/achievements` |
| Forum Service | `http://<STAGING_HOST>:8084/actuator/health` | `http://<STAGING_HOST>:8084` |
| Clan Service | `http://<STAGING_HOST>:8085/actuator/health` | `http://<STAGING_HOST>:8085` |
| Notification Service | `http://<STAGING_HOST>:8086/actuator/health` | `http://<STAGING_HOST>:8086` |
| Prometheus | `http://<STAGING_HOST>:9090/-/healthy` | `http://<STAGING_HOST>:9090/targets` |
| Grafana | `http://<STAGING_HOST>:3000` | `http://<STAGING_HOST>:3000/dashboards` |

## Achievements Links To Submit

- Health: `http://<STAGING_HOST>:8083/actuator/health`
- Prometheus metrics: `http://<STAGING_HOST>:8083/actuator/prometheus`
- Gateway route: `http://<STAGING_HOST>:8090/api/achievements`
- Prometheus target: `http://<STAGING_HOST>:9090/targets?search=service-achievements`

## Deployment Source

The Achievements service is deployed through the production compose image:

```text
ghcr.io/advprog-2026-a15-project/service-achievements:latest
```

The infra compose exposes `service-achievements` on port `8083` and the
Prometheus scrape job targets `service-achievements:8083/actuator/prometheus`.

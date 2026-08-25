# 🗺️ Hoja de Ruta (Roadmap) de PostgresPulse

Este documento describe la visión **posterior a la v1.0** de **PostgresPulse**. El plan de ejecución de la v1.0 (fases 0–8, con su Definición de Terminado) vive en [docs/SPECS.md §16](docs/SPECS.md). Todo lo que aparece aquí está **fuera de alcance de la v1.0** por decisión explícita — ver [docs/SPECS.md §3.2](docs/SPECS.md).

---

## Próximos pasos

Sin incrementos comprometidos pendientes por ahora — los tres que se planearon tras la v1.0 ya
están implementados. Lo que sigue son direcciones sin compromiso de ejecución, ver
[Ideas exploratorias](#ideas-exploratorias) abajo.

✅ **RBAC + JWT** (múltiples usuarios y roles ADMIN/LECTOR, reemplazando la Autenticación Básica de
un solo administrador de v1.0-1.2) ya está implementado — ver [CHANGELOG.md](CHANGELOG.md) y
[docs/API.md §1-3](docs/API.md).

✅ **Alertas (Email / Slack / PagerDuty)** ya está implementado — umbral configurable por fuente,
canales de envío configurables a nivel de instancia — ver [CHANGELOG.md](CHANGELOG.md) y
[docs/DEPLOYMENT.md §5.5](docs/DEPLOYMENT.md).

✅ **Tableros Grafana** ya está implementado — stack opcional (`docker compose --profile monitoring
up`) con un tablero de ejemplo sobre el exportador Prometheus disponible desde v1.2.0 — ver
[CHANGELOG.md](CHANGELOG.md) y [docs/DEPLOYMENT.md §5.6](docs/DEPLOYMENT.md).

---

## Ideas exploratorias

Direcciones posibles sin compromiso de ejecución — requieren su propia evaluación de alcance antes
de convertirse en plan. No asumas que alguna de estas está en curso.

- **Auditoría de cambios:** registro de quién modificó qué configuración y cuándo (RBAC, la
  dependencia que tenía, ya está lista).
- **Soporte MySQL / Oracle / SQL Server:** motor de chequeos extendido más allá de PostgreSQL 12–17.
- **Agente ligero (Go/Rust):** recolector de métricas de bajo consumo instalable en los servidores
  objetivo, como alternativa al modelo actual sin agente.
- **Fleet management:** monitoreo centralizado de decenas de instancias PostgreSQL desde un único
  panel.
- **Plataforma SaaS multiinquilino:** panel de suscripciones y aislamiento por organización.
- **Recomendaciones impulsadas por IA:** modelos que predicen cuellos de botella antes de que ocurran.
- **Auto-remediación:** ejecución automática de mantenimiento (`VACUUM`, `ANALYZE`) ante hallazgos
  específicos.
- **Internacionalización (i18n):** soporte multi-idioma del panel de control.

---

<div align="center">
  <em>¿Tienes alguna sugerencia para nuestra hoja de ruta? ¡Abre un issue y conversemos!</em>
</div>

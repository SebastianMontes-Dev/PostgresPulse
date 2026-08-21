# 🗺️ Hoja de Ruta (Roadmap) de PostgresPulse

Este documento describe la visión **posterior a la v1.0** de **PostgresPulse**. El plan de ejecución de la v1.0 (fases 0–8, con su Definición de Terminado) vive en [docs/SPECS.md §16](docs/SPECS.md). Todo lo que aparece aquí está **fuera de alcance de la v1.0** por decisión explícita — ver [docs/SPECS.md §3.2](docs/SPECS.md).

---

## Próximos pasos

Los incrementos que siguen, en el orden en que se abordarán. Cada uno encaja en la arquitectura existente sin rediseñarla.

- **Alertas (Email / Slack / PagerDuty):** notificaciones cuando la puntuación de salud cruce un
  umbral configurado.
- **Tableros Grafana:** dashboards de ejemplo listos para importar. El exportador ya está disponible
  desde v1.2.0 (`/actuator/prometheus`, ver [docs/DEPLOYMENT.md §5.4](docs/DEPLOYMENT.md)).

✅ **RBAC + JWT** (múltiples usuarios y roles ADMIN/LECTOR, reemplazando la Autenticación Básica de
un solo administrador de v1.0-1.2) ya está implementado — ver [CHANGELOG.md](CHANGELOG.md) y
[docs/API.md §1-3](docs/API.md).

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

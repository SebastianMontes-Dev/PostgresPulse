<div align="center">
  <h1>⚡ PostgresPulse</h1>
  <p><strong>Plataforma de Monitoreo y Salud de PostgreSQL a Nivel Empresarial</strong></p>
  
  [![CI](https://github.com/SebastianMontes-Dev/PostgresPulse/actions/workflows/ci.yml/badge.svg)](https://github.com/SebastianMontes-Dev/PostgresPulse/actions/workflows/ci.yml)
  [![Java Version](https://img.shields.io/badge/Java-21-blue.svg?style=for-the-badge&logo=openjdk)](#)
  [![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-brightgreen.svg?style=for-the-badge&logo=springboot)](#)
  [![PostgreSQL](https://img.shields.io/badge/PostgreSQL-316192?style=for-the-badge&logo=postgresql&logoColor=white)](#)
  [![License](https://img.shields.io/badge/License-MIT-purple.svg?style=for-the-badge)](#)
</div>

<br/>

**PostgresPulse** es una plataforma avanzada de nivel empresarial diseñada para monitorear, analizar y mantener la salud de las bases de datos PostgreSQL. Construida sobre una arquitectura moderna con **Java 21** y **Spring Boot 3.x**, ofrece métricas robustas en tiempo real, una arquitectura resiliente y APIs amigables para los desarrolladores.

---

## ✨ Características Principales

*   📊 **Monitoreo en Tiempo Real:** Información detallada sobre el rendimiento de la base de datos, ejecución de consultas y conexiones.
*   🛡️ **Arquitectura Resiliente:** Integrado con Resilience4j para tolerancia a fallos y degradación elegante.
*   🔄 **Migraciones Automatizadas:** Soporte de Flyway para una gestión impecable del esquema de la base de datos.
*   🔌 **APIs Listas para Desarrolladores:** Integración lista para usar con OpenAPI/Swagger.
*   🧪 **Pruebas en Contenedores:** Testcontainers integrado para entornos de prueba efímeros y confiables.

---

## 🏗️ Arquitectura y Tecnologías

| Categoría | Tecnología |
| :--- | :--- |
| **Framework Base** | Spring Boot 3.4.1, Java 21 |
| **Persistencia** | Spring Data JPA, Controlador PostgreSQL |
| **Resiliencia** | Resilience4j |
| **Migración de BD** | Flyway |
| **Documentación API**| Springdoc OpenAPI |
| **Pruebas** | JUnit Jupiter, Testcontainers |
| **Plantillas / UI** | Thymeleaf (para paneles y vistas) |

---

## 🚀 Guía de Inicio Rápido

### 1️⃣ Requisitos Previos

Antes de comenzar, asegúrate de tener instalado:

*   ☕ **JDK 21** o superior
*   📦 **Maven 3.8** o superior
*   🐳 **Docker** (para Testcontainers y PostgreSQL local)

### 2️⃣ Ejecutar la Aplicación

Puedes iniciar la aplicación rápidamente utilizando el *wrapper* de Maven incluido:

```bash
./mvnw spring-boot:run
```

O si prefieres un entorno completamente basado en contenedores usando Docker Compose:

```bash
docker-compose up -d
```

---

## 📚 Documentación

Las especificaciones técnicas detalladas, las guías de la API y las instrucciones de despliegue se pueden encontrar en el directorio `docs`:

*   📘 [Documentación de la API](docs/API.md)
*   🛠️ [Guía de Despliegue](docs/DEPLOYMENT.md)
*   📄 [Especificaciones Técnicas](docs/SPECS.md)

---

## 🤝 Contribuciones

¡Las contribuciones, informes de problemas (*issues*) y solicitudes de nuevas características son siempre bienvenidos! No dudes en visitar la página de *issues*.

---

<div align="center">
  <em>Desarrollado con ❤️ para la comunidad de código abierto de datos.</em>
</div>

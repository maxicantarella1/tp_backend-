# TPI Backend 2026 — Dominio 5: Gestión de actividades turísticas y excursiones

Proyecto basado en el código del compañero. Todo está comentado en español para que cualquier integrante se guíe.

| Carpeta               | Qué es                                                                   | Puerto |
|-----------------------|--------------------------------------------------------------------------|--------|
| `common-lib/`         | JAR reutilizable: errores uniformes (400/404/409/503)                    | –      |
| `actividadSpring/`    | Catálogo, prestadores y CUPO por día. API externa: geocodificación       | 8081   |
| `ReservaSpring/`      | Reservas y reglas. APIs externas: clima, calidad del aire, feriados      | 8082   |
| `keycloak/`           | Realm `turismo` (roles, clientes, usuarios) que Keycloak importa solo | –      |
| `docker-compose.yml`  | Levanta Keycloak en http://localhost:8180                                | –      |
| `original-companero/` | Código EXACTO del zip original, sin tocar, para comparar                 | –      |
| `CAMBIOS-PASO-1.md`   | Qué se corrigió del código original                                      | –      |
| `PASO-2.md`           | Reglas de negocio, APIs, guion de pruebas y cómo defenderlo              | –      |
| `PASO-3-KEYCLOAK.md`  | Seguridad: Keycloak, JWT, roles, permisos, pruebas y defensa             | –      |

## Cómo correrlo (JDK 17+, Maven, Docker)
```
docker compose up -d                           # 0) Keycloak (http://localhost:8180, admin/admin)
cd common-lib         && mvn clean install     # 1) instala el JAR en ~/.m2 (repetir si cambia common-lib)
cd ../actividadSpring && mvn clean test        # 2) tests de actividades
cd ../ReservaSpring   && mvn clean test        # 3) tests de reservas
# para levantar: una terminal por servicio
cd actividadSpring && mvn spring-boot:run      # http://localhost:8081
cd ReservaSpring   && mvn spring-boot:run      # http://localhost:8082
```

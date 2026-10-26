# TPI Backend 2026 — Dominio 5: Gestión de actividades turísticas y excursiones

Proyecto basado en el código del compañero. Todo está comentado en español para que cualquier integrante se guíe.

| Carpeta               | Qué es                                                             | Puerto |
|-----------------------|--------------------------------------------------------------------|--------|
| `common-lib/`         | JAR reutilizable: errores uniformes (404/409/503/400)              | –      |
| `actividadSpring/`    | Microservicio de actividades y su cupo                             | 8081   |
| `ReservaSpring/`      | Microservicio de reservas (llama a actividades por HTTP)           | 8082   |
| `original-companero/` | Código EXACTO del zip original, sin tocar, para comparar           | –      |
| `CAMBIOS-PASO-1.md`   | Qué se cambió respecto al original, cómo probar y cómo defenderlo  | –      |

## Cómo correrlo (JDK 17+, Maven)
```
cd common-lib       && mvn clean install     # 1) instala el JAR en ~/.m2
cd ../actividadSpring && mvn clean test      # 2) tests de actividades
cd ../ReservaSpring   && mvn clean test      # 3) tests de reservas
# para levantar: en una terminal cada uno
cd actividadSpring && mvn spring-boot:run    # http://localhost:8081
cd ReservaSpring   && mvn spring-boot:run    # http://localhost:8082
```

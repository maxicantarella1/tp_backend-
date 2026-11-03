# TPI Backend de Aplicaciones 2026 — Dominio 5: Gestión de Actividades Turísticas y Excursiones (Grupo 117)

Plataforma backend basada en **arquitectura de microservicios** (Java 17, Spring Boot 3.2.5, Spring Cloud Gateway MVC, Spring Security OAuth2 Resource Server, PostgreSQL 16 y Keycloak 24) para la gestión de catálogos de excursiones turísticas, control de cupo diario con bloqueo pesimista, reservas con validación meteorológica/ambiental en tiempo real, recargos por feriados nacionales y reevaluación automática programada.

---

## 🗂️ Mapa de Componentes del Repositorio

| Carpeta / Archivo | Rol dentro del Proyecto | Puerto |
| :--- | :--- | :---: |
| `common-lib/` | **Librería transversal Maven (`com.turismo:common-lib:1.0.0`):** DTO `ErrorResponse`, excepciones base, `ManejadorErroresComun`, `KeycloakJwtConverter` y `ErroresSeguridad` (401/403 uniformes). | — |
| `api-gateway/` | **API Gateway + Edge Security Filter:** Punto único de entrada, validación perimetral de JWT/roles, enrutamiento a microservicios, propagación de `X-Usuario-Autenticado` y respuesta `503` uniforme ante caídas. | `8080` |
| `actividadSpring/` | **MS Catálogo de Actividades y Prestadores:** ABM de prestadores y excursiones, geocodificación automática (Open-Meteo Geocoding API) y control transaccional de cupos diarios (`PESSIMISTIC_WRITE`). | `8081` |
| `ReservaSpring/` | **MS Gestión de Reservas (Core del MVP):** Proceso central de reserva, reglas de negocio puras, integración con 3 APIs externas (Clima, Calidad del Aire y Feriados), compensación Saga y reevaluador programado (`@Scheduled`). | `8082` |
| `keycloak/turismo-realm.json` | Configuración exportada del realm `turismo` (roles `turista`, `prestador`, `admin`, `servicio`, clientes OIDC y 5 usuarios de prueba). | `8180` |
| `postgres-init/` | Script SQL de inicialización automática para crear las dos bases de datos aisladas (`actividadesdb` y `reservasdb`) en PostgreSQL (`5432`). | `5432` |
| `docker-compose.yml` | Orquestación integral de los **5 contenedores** (`postgres`, `keycloak`, `actividades`, `reservas`, `api-gateway`) con healthchecks y variables de entorno. | — |
| `postman/` | **Colección exportable de Postman + Environment:** 28 peticiones organizadas en 6 carpetas con captura automática de tokens JWT y aserciones `pm.test`. | — |
| `CAMBIOS-PASO-1.md` · `PASO-2.md` · `PASO-3-KEYCLOAK.md` | Documentación evolutiva del proyecto a lo largo de las etapas previas. | — |

---

## 🚀 Opción 1: Arranque Integral con Docker Compose (Recomendado)

Levanta los **5 contenedores** (`postgres`, `keycloak`, `actividades`, `reservas` y `api-gateway`) compilando automáticamente `common-lib` y cada microservicio dentro de imágenes multi-etapa (no requiere tener Java ni Maven instalados en el host):

```bash
# 1. Levantar toda la plataforma en segundo plano (construye imágenes en la primera ejecución)
docker compose up -d --build

# 2. Verificar que los 5 contenedores estén en estado Up / healthy
docker compose ps

# 3. (Opcional) Seguir los logs del API Gateway y microservicios
docker compose logs -f api-gateway actividades reservas
```

### URLs disponibles una vez iniciado:
- **API Gateway (Punto único de entrada):** `http://localhost:8080`
- **Keycloak Admin Console (`admin` / `admin`):** `http://localhost:8180`
- **Swagger UI — MS Actividades:** `http://localhost:8081/swagger-ui.html`
- **Swagger UI — MS Reservas:** `http://localhost:8082/swagger-ui.html`
- **PostgreSQL (`postgres` / `postgres`):** `localhost:5432` (bases `actividadesdb` y `reservasdb`)

> **Nota sobre Datos Iniciales (`DataInitializer`):** Al iniciar por primera vez con la base vacía, `actividadSpring` precarga automáticamente **2 prestadores** (`Patagonia Extrema S.R.L.` de `prestador1` y `Andes Aventura S.A.` de `prestador2`) y **3 actividades turísticas** (IDs `1`, `2` y `3`) para poder demostrar todo el flujo sin carga manual previa.

---

## 💻 Opción 2: Ejecución en Modo Desarrollo Local (Maven + H2 en Memoria + Tests)

Los tests automatizados (**111 tests en total**) utilizan H2 en memoria, mocks de clientes externos y simulación de tokens JWT (`spring-security-test`), por lo que **corren en segundos sin necesitar Docker ni conexión a Internet**:

```bash
# 1. Instalar la librería común en el repositorio Maven local (~/.m2)
cd common-lib && mvn clean install

# 2. Ejecutar los 20 tests de Actividades
cd ../actividadSpring && mvn clean test

# 3. Ejecutar los 82 tests de Reservas (reglas puras, clientes HTTP, concurrencia y seguridad)
cd ../ReservaSpring && mvn clean test

# 4. Ejecutar los 9 tests del API Gateway (ruteo, JWT y resiliencia 503)
cd ../api-gateway && mvn clean test
```

Si querés correr los procesos Java localmente desde consola (usando H2 por defecto y sólo Keycloak en Docker):
```bash
docker compose up -d keycloak
cd actividadSpring && mvn spring-boot:run   # Puerto 8081
cd ReservaSpring   && mvn spring-boot:run   # Puerto 8082
cd api-gateway     && mvn spring-boot:run   # Puerto 8080
```

---

## 🔐 Usuarios y Credenciales del Realm `turismo` (Keycloak)

| Usuario / Cliente | Contraseña / Secreto | Rol en Keycloak (`realm_access.roles`) | Propósito en la Plataforma |
| :--- | :--- | :---: | :--- |
| `turista1` | `turista1` | `turista` | Crea reservas a su nombre y gestiona únicamente sus propias reservas. |
| `turista2` | `turista2` | `turista` | Segundo turista para demostrar aislamiento de propiedad (`403 Forbidden` al intentar ver/cancelar reservas de `turista1`). |
| `prestador1` | `prestador1` | `prestador` | Dueño del Prestador `1` (*Patagonia Extrema S.R.L.*); administra sus actividades (`1` y `2`). |
| `prestador2` | `prestador2` | `prestador` | Dueña del Prestador `2` (*Andes Aventura S.A.*); administra su actividad (`3`). |
| `admin1` | `admin1` | `admin` | Acceso administrativo total y ejecución manual de `POST /reservas/reevaluacion`. |
| `reservas-service` | `reservas-secret` | `servicio` | Cuenta técnica OAuth2 (`client_credentials`) usada exclusivamente por `ReservaSpring` para descontar y devolver cupo en `actividadSpring`. |

---

## 🧪 Cómo Ejecutar la Colección de Pruebas en Postman

1. Abrir **Postman** e importar los dos archivos ubicados en la carpeta `postman/`:
   - `postman/TPI-Turismo-2026.postman_collection.json`
   - `postman/TPI-Turismo-Local.postman_environment.json`
2. Seleccionar arriba a la derecha el entorno **`TPI Turismo 2026 - Local`**.
3. Ejecutar las 6 carpetas en orden (o con **Run Collection**):
   - **Carpeta 1 (Logins OIDC):** Obtiene los tokens JWT de `turista1`, `turista2`, `prestador1`, `prestador2` y `admin1` y los guarda automáticamente en variables.
   - **Carpeta 2 (Catálogo y Geocodificación):** Verifica las 3 excursiones precargadas y da de alta un nuevo prestador y excursión resolviendo coordenadas por nombre de ciudad.
   - **Carpeta 3 (Flujo Central de Reservas):** Demuestra reservas en estado `CONFIRMADA` (con pronóstico dentro de los 16 días), `PENDIENTE` (fecha > 16 días) y recargo automático del `+20%` en feriados nacionales.
   - **Carpeta 4 (Reglas de Negocio y Rechazos):** Verifica respuestas `400 Bad Request`, `404 Not Found` y `409 Conflict` (sin cupo diario disponible).
   - **Carpeta 5 (Seguridad y Propiedad):** Verifica `401 Unauthorized` sin token y `403 Forbidden` por rol insuficiente o intento de modificar recursos de otro usuario.
   - **Carpeta 6 (Ciclo de Vida Posterior):** Reprogramación de fecha con transferencia de cupo, reevaluación climática general (`ROLE_ADMIN`) y cancelación con devolución de cupo y cálculo de reintegro (`100%`, `50%` o `0%`).

### 📅 Importante sobre las Variables de Fecha en Postman

Como las reglas de negocio rechazan reservas en fechas pasadas y consultan pronósticos meteorológicos en tiempo real (ventana de 15 días), antes de ejecutar la colección verificá que las 4 variables de fecha (en la pestaña *Variables* de la Colección o en el *Environment* de Postman) sean futuras respecto del día en que estés corriendo las pruebas:

| Variable | Regla que debe cumplir al momento de probar | Ejemplo / Valor recomendado |
| :--- | :--- | :--- |
| `fechaCercana` | Entre **mañana y +15 días** desde la fecha actual (en temporada oct–abr) | `hoy + 3 días` |
| `fechaReprogramada` | Otra fecha distinta entre **+2 y +15 días** desde la fecha actual (en temporada oct–abr) | `hoy + 5 días` |
| `fechaLejana` | A **más de 15 días en el futuro** desde la fecha actual (en temporada oct–abr, para probar estado `PENDIENTE`) | Ej.: `2026-12-15` o `2027-01-15` |
| `fechaFeriado` | Un **feriado nacional argentino futuro** dentro de la temporada oct–abr (para probar recargo `+20%`) | Ej.: `2026-11-23`, `2026-12-08` o `2026-12-25` |

---

## 🏛️ Estructura Interna por Capas de cada Microservicio

Cada microservicio respeta separación estricta de responsabilidades (`controller → service → rules/client/repository`), manteniendo las reglas de dominio desacopladas del framework:

| Paquete | Responsabilidad | `api-gateway` | `actividadSpring` | `ReservaSpring` |
| :--- | :--- | :--- | :--- | :--- |
| `controller` | Exposición REST y validación de entrada (`@Valid`, `@PreAuthorize`) | — | `ActividadController`, `PrestadorController` | `ReservaController` |
| `service` | Orquestación transaccional, coordinación entre servicios y tareas programadas | — | `ActividadService` | `ReservaService`, `ReevaluadorProgramado`, `TokenServicio` |
| `rules` | Reglas de negocio puras (testeables en aislamiento sin levantar Spring) | — | — | `ReglaActividad`, `ReglaClima`, `ReglaPrecio`, `PoliticaCancelacion` |
| `client` | Clientes HTTP hacia otros servicios y APIs públicas con timeouts y manejo de fallos | — | `GeocodingClient` | `ClimaClient`, `AireClient`, `FeriadosClient` |
| `repository` | Persistencia JPA y bloqueo pesimista (`@Lock(PESSIMISTIC_WRITE)`) | — | `ActividadRepository`, `OcupacionDiaRepository`, `PrestadorRepository` | `ReservaRepository` |
| `config` | Seguridad OAuth2/JWT, rutas de Gateway, `RestClient`, Swagger y carga inicial | `SecurityConfig`, `GatewayRoutesConfig` | `SecurityConfig`, `DataInitializer` | `SecurityConfig`, `ClientesConfig` |

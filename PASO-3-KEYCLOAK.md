# Paso 3 — Seguridad con Keycloak (OIDC, JWT, roles) y Spring Security

> El Gateway todavía no existe (siguiente paso). Por ahora los DOS microservicios se protegen solos: cada uno valida el JWT.
> Cuando agreguemos el Gateway, el Security Filter del Gateway usará el mismo conversor de roles de `common-lib`.

## 0. Cómo probarlo
```
docker compose up -d                       # (carpeta raíz) levanta Keycloak en http://localhost:8180 (admin / admin)
docker compose logs -f keycloak            # esperar "Running the server in development mode" y que importe el realm "turismo"
cd common-lib         && mvn clean install # IMPORTANTE: cambió (seguridad); hay que reinstalarlo
cd ../actividadSpring && mvn clean test    # y luego: mvn spring-boot:run
cd ../ReservaSpring   && mvn clean test    # y luego: mvn spring-boot:run
```
Los tests NO necesitan Keycloak (simulan el token con `jwt()`). Para probar de verdad sí: Keycloak tiene que estar arriba
**antes** de hacer pedidos a los servicios.

**Obtener un token (Postman):** `POST http://localhost:8180/realms/turismo/protocol/openid-connect/token`, Body → `x-www-form-urlencoded`:
`grant_type=password` · `client_id=turismo-api` · `username=turista1` · `password=turista1`.
La respuesta trae `access_token`: copiarlo en cada pedido, pestaña *Authorization → Bearer Token*. Dura 15 min (después, pedir otro).
Pegando el token en https://jwt.io se ve el claim `realm_access.roles` y `preferred_username`.
En Swagger (`/swagger-ui.html` de cada servicio) está el botón **Authorize** para pegar el token.

**Usuarios del realm `turismo`** (contraseña = nombre de usuario; solo para desarrollo):
| Usuario | Rol | Para qué |
|---------|-----|----------|
| `admin1` | admin | todo |
| `prestador1`, `prestador2` | prestador | gestionar SUS actividades (probar que uno no toca lo del otro) |
| `turista1`, `turista2` | turista | reservar y gestionar SUS reservas |
| cliente `reservas-service` (secreto `reservas-secret`) | servicio | cuenta técnica de Reservas → Actividades |

## 1. Cambios que ROMPEN lo anterior
- **Todo exige token**, salvo: documentación Swagger y las consultas `GET` de `/actividades/**` y `/prestadores/**` (catálogo público).
- `POST /reservas`: el usuario de la reserva sale **del token**; el campo `usuario` del cuerpo se ignora (solo un admin puede indicar otro).
- `Prestador` tiene un campo nuevo `usuario` (dueño). Quien crea un prestador queda como su dueño.
- La consola H2 está **desactivada** (exponía la base sin autenticación).
- `common-lib` ahora depende (scope provided) de Spring Security: reinstalarlo.

## 2. Arquitectura de seguridad
```
Usuario ──(1) login ──────────────► KEYCLOAK (IdP)  ◄──(3) client_credentials── Reservas
   ▲                                     │ emite JWT firmado (RS256)                │
   └────────(2) access_token ◄───────────┘                                          │ (4) JWT con rol "servicio"
   │                                                                                 ▼
   └──(5) Authorization: Bearer <JWT> ──► Reservas (8082) ──(6) Bearer <JWT servicio>──► Actividades (8081)
                                          cada uno VALIDA firma, emisor y vencimiento con las claves públicas de Keycloak (JWKS)
```
- **Keycloak autentica** (usuario/contraseña) y **emite** el JWT. Los servicios **nunca ven contraseñas**.
- **Cada microservicio es un *resource server***: verifica el token sin llamar a Keycloak en cada pedido (firma con clave pública, claim `iss`, `exp`).
- **Roles** viven en Keycloak (`realm_access.roles`); `KeycloakJwtConverter` (en common-lib) los convierte en `ROLE_ADMIN`, etc.
- **Trusted zone:** los servicios no confían en nadie por estar "dentro de la red": validan el token siempre (defensa en profundidad).
  Cuando esté el Gateway, él también validará en el borde y rechazará lo no autenticado antes de enrutar.

## 3. Matriz de permisos
| Operación | Anónimo | turista | prestador | admin | servicio |
|-----------|:-------:|:-------:|:---------:|:-----:|:--------:|
| `GET` actividades / prestadores / disponibilidad | ✅ | ✅ | ✅ | ✅ | ✅ |
| `POST /prestadores` | 401 | 403 | ✅ (queda dueño) | ✅ | 403 |
| `POST /actividades`, `DELETE /actividades/{id}` | 401 | 403 | ✅ **solo si es SU prestador** | ✅ | 403 |
| `PATCH /actividades/{id}/cupo` y `/cupo/devolucion` | 401 | 403 | 403 | ✅ | ✅ |
| `POST /reservas` | 401 | ✅ (a su nombre) | 403 | ✅ (puede indicar usuario) | 403 |
| `GET /reservas` | 401 | solo las suyas | 403 | todas | 403 |
| `GET/cancelar/reevaluar/reprogramar /reservas/{id}` | 401 | **solo las suyas** (si no, 403) | 403 | ✅ | 403 |
| `POST /reservas/reevaluacion` | 401 | 403 | 403 | ✅ | 403 |

Tres niveles de control: **URL** (`SecurityConfig`: qué es público) → **ROL** (`@PreAuthorize`) → **PROPIEDAD** (el turista solo sus reservas, el
prestador solo lo suyo: código en controller/service).

## 4. Guion de pruebas de seguridad
1. Sin token: `GET :8082/reservas` → **401** con formato `{status,error,mensaje,timestamp}`.
2. Con token de `turista1`: `POST :8082/reservas` (cuerpo con `"usuario":"cualquiera"`) → la reserva queda a nombre de `turista1`.
3. Con token de `turista2`: `GET/POST cancelar :8082/reservas/{id de turista1}` → **403** "no te pertenece".
4. Token de `turista1`: `POST :8081/actividades` → **403** (rol insuficiente). · Token de `turista1`: `PATCH :8081/actividades/1/cupo` → **403** (turista no toca el cupo).
5. Token de `prestador1`: crear prestador y actividad → ✅. Token de `prestador2` intenta `DELETE` esa actividad → **403** (propiedad).
6. Token de `admin1`: `POST :8082/reservas/reevaluacion` → ✅; con `turista1` → 403.
7. Esperar 15 min (o cambiar la hora) y reusar el token → **401** (vencido).
8. Hacer una reserva completa: demuestra que Reservas obtuvo SOLO su token de servicio para descontar cupo (mirar el log de Actividades).

## 5. Cómo defenderlo
**¿Qué es OIDC y qué agrega a OAuth2?** OAuth2 es de *autorización* (delegar acceso); OpenID Connect agrega *autenticación*: define el ID token y cómo saber
QUIÉN es el usuario. Keycloak implementa ambos: acá lo usamos para autenticar usuarios y emitir el *access token* (JWT).

**¿Qué es un JWT y cómo se valida sin consultar a Keycloak?** Tres partes (cabecera.payload.firma). Keycloak firma con su clave privada; los servicios bajan
las claves **públicas** (JWKS) y verifican la firma, que el emisor (`iss`) sea nuestro realm y que no haya vencido (`exp`). Por eso es *stateless*: no hay sesión en el servidor.

**¿Por qué no sesiones/cookies?** API REST sin estado, escalable y consumible por cualquier cliente. Por eso se desactiva CSRF (CSRF aprovecha cookies que el navegador envía solo; con token en cabecera no aplica).

**¿Dónde están los roles y cómo llegan a Spring?** Se administran en Keycloak; viajan en `realm_access.roles`. Spring espera `scope`, así que `KeycloakJwtConverter` los traduce
a `ROLE_*`. Está en common-lib porque todos los componentes deben interpretar el token igual.

**¿Por qué la "propiedad" no está en Keycloak?** Keycloak sabe QUIÉN eres y tu ROL. "Esta reserva es de turista1" o "este prestador lo administra prestador1" es **dato de negocio**: vive en la base del dominio
(`Reserva.usuario`, `Prestador.usuario`). Meterlo en Keycloak acoplaría el IdP al modelo de negocio.

**¿Cómo se autentica Reservas contra Actividades?** *Client credentials*: Reservas tiene su propia identidad en Keycloak (`reservas-service` + secreto), obtiene un JWT con rol `servicio` y lo envía.
No reenvía el token del turista porque (a) la reevaluación programada corre sin usuario y (b) el turista no debe tener permiso para tocar el cupo. Principio de **mínimo privilegio**.

**¿Por qué dos propiedades (`issuer-uri` y `jwk-set-uri`)?** Con Docker, el servicio llega a Keycloak por `keycloak:8080` pero el `iss` del token es la URL pública. Se valida el emisor contra la
pública y las claves se bajan de la interna.

**401 vs 403:** 401 = no sé quién sos (sin token, inválido o vencido). 403 = sé quién sos pero no tenés permiso (rol o propiedad). Ambos devuelven el mismo formato JSON que el resto de los errores.

**¿Por qué el catálogo es público?** Decisión de negocio: ver actividades no requiere identidad; reservar sí. Es una decisión explícita y fácil de cambiar en `SecurityConfig`.

**Limitaciones (decirlas suma):** el flujo `password` (usado para probar en Postman) está desaconsejado: un frontend real debe usar *Authorization Code + PKCE*. Keycloak corre en `start-dev` (HTTP, base interna): en producción
iría con HTTPS y PostgreSQL. El secreto de `reservas-service` está en el realm de desarrollo: en producción se inyecta por variable de entorno. No se consulta revocación de tokens (dura 15 min). Falta el Gateway.

## 6. Si algo falla
- **401 con token válido:** el `iss` del token (se ve en jwt.io) debe ser EXACTAMENTE `http://localhost:8180/realms/turismo`. Hay que pedir el token a `localhost:8180`, no a `127.0.0.1` ni a otro host.
- **`invalid_grant: Account is not fully set up`:** el usuario no tiene nombre/email (los de este realm ya los traen).
- **`invalid_client` al pedir el token de servicio:** el secreto no coincide (`KEYCLOAK_CLIENT_SECRET` vs el del realm).
- **Reservas devuelve 503 "keycloak":** Keycloak no está arriba o `keycloak.token-url` es incorrecta.
- **Keycloak no importó el realm:** `docker compose logs keycloak`; si cambiaste el JSON, borrar el contenedor (`docker compose down -v`) y volver a subir.
- **Yo no pude ejecutar Keycloak:** el JSON del realm está escrito según el formato de importación de Keycloak 26; si algún campo da error, el log dice cuál.

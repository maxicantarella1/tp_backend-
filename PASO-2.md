# Paso 2 — Reglas de negocio completas y 4 APIs externas

## 0. Cómo probarlo
```
cd common-lib        && mvn clean install     # IMPORTANTE: se agregó SolicitudInvalidaException, hay que reinstalar
cd ../actividadSpring && mvn clean test       # luego: mvn spring-boot:run   (puerto 8081)
cd ../ReservaSpring   && mvn clean test       # luego: mvn spring-boot:run   (puerto 8082)
```
Los tests NO usan Internet (las APIs se simulan). Para el uso real, el equipo necesita conexión.

**Antes de la defensa, verificá las 4 APIs en el navegador** (yo no pude llamarlas desde mi entorno; el código asume estos formatos):
- Clima: https://api.open-meteo.com/v1/forecast?latitude=-31.4&longitude=-64.2&daily=precipitation_probability_max,wind_speed_10m_max,weather_code,sunset&timezone=auto
  → debe traer `daily` con `time`, `precipitation_probability_max`, `wind_speed_10m_max`, `weather_code`, `sunset`.
- Aire: https://air-quality-api.open-meteo.com/v1/air-quality?latitude=-31.4&longitude=-64.2&hourly=us_aqi&timezone=auto → `hourly.us_aqi`.
- Feriados: https://api.argentinadatos.com/v1/feriados/2026 → lista de `{fecha, nombre, tipo}`.
- Geocodificación: https://geocoding-api.open-meteo.com/v1/search?name=Villa%20General%20Belgrano&count=1&countryCode=AR → `results[0].latitude/longitude`.
Si alguna difiere, el ajuste es solo en el `record` interno del cliente correspondiente.

## 1. Cambios que ROMPEN lo anterior (avisar al equipo)
- `Actividad`: ya no existe `cupoDisponible`; ahora `cupoMaximo` (POR DÍA). Campos nuevos obligatorios: `prestadorId`, `horaInicio`,
  `duracionHoras`, `temporadaDesdeMes`, `temporadaHastaMes`, y ubicación (`latitud`+`longitud` o `localidad`).
- `PATCH /actividades/{id}/cupo` ahora recibe `{ "fecha": "2026-11-10", "cantidad": 2 }`.
- `POST /reservas` ahora exige `edadMenor` y `adultos`.
- `DELETE /actividades/{id}` ahora es baja lógica.

## 2. Las 4 APIs externas (y por qué cada una es necesaria)
| API | Usada por | Qué aporta al negocio | Si falla |
|-----|-----------|------------------------|----------|
| Open-Meteo **Pronóstico** | Reservas | lluvia, viento, tormenta y atardecer: deciden si la actividad se REALIZA ese día | `Optional.empty` → reserva **PENDIENTE** |
| Open-Meteo **Calidad del aire** | Reservas | humo/contaminación (incendios en las sierras): riesgo real aunque no llueva | no se aplica (no fuerza PENDIENTE) |
| ArgentinaDatos **Feriados** | Reservas | si la actividad opera ese feriado y el recargo de precio; cambian por decreto cada año | `SIN_DATOS` → **PENDIENTE** |
| Open-Meteo **Geocodificación** | Actividades | convierte "Villa General Belgrano" en coordenadas al CREAR la actividad (el clima se pide por coordenadas) | 503 (el prestador puede cargar lat/long a mano) |

**Por qué no son decorativas:** las 3 de Reservas cambian el RESULTADO de una reserva (confirmar, dejar pendiente, rechazar,
cancelar, cambiar precio). Ninguna es un dato que se pueda dejar como constante en la configuración. La geocodificación evita
mantener una tabla de localidades.

**Política común de fallas ("no inventamos datos"):** si falta un dato externo clave, la reserva queda PENDIENTE (no se rechaza ni se
confirma a ciegas) y la reevaluación la resuelve después. Todos los clientes tienen timeout (2 s conectar / 3 s leer) y capturan sus
errores: una API externa caída nunca tumba al servicio.

## 3. Reglas de negocio
### En Actividades (dueño del catálogo y del cupo)
| # | Regla | Resultado |
|---|-------|-----------|
| A1 | El prestador debe existir | 404 |
| A2 | Hay que informar ubicación (coordenadas o localidad); la localidad debe existir | 400 |
| A3 | `cupoMinimo` ≤ `cupoMaximo` | 400 |
| A4 | El cupo es **por día**: ocupados ≤ `cupoMaximo` en cada fecha; bloqueo pesimista contra sobreventa | 409 |
| A5 | Actividad dada de baja no admite reservas | 409 |
| A6 | Devolver cupo nunca deja el contador bajo cero | – |

### En Reservas (orquestador) — al CREAR
| # | Regla | Resultado |
|---|-------|-----------|
| R1 | Actividad existente y activa | 404 / 409 |
| R2 | Dentro de temporada (soporta temporadas que cruzan el año) | 409 |
| R3 | Grupo ≤ capacidad máxima | 409 |
| R4 | Edad del participante más joven ≥ edad mínima | 409 |
| R5 | Menores de 18 requieren ≥ 1 adulto; adultos ≤ total | 409 |
| R6 | Feriado + actividad que no opera feriados | 409 |
| R7 | Lluvia/viento/tormenta/atardecer/aire fuera de límites (solo actividades al aire libre) | 409 |
| R8 | Precio = precio × personas (+ recargo si es feriado) | – |
| R9 | Sin datos externos → **PENDIENTE**; con todo favorable → **CONFIRMADA** | – |
| R10 | Si falla el guardado después de reservar cupo → **compensación** (se devuelve el cupo) | – |
### Ciclo de vida posterior
| # | Regla | Resultado |
|---|-------|-----------|
| R11 | **Cancelar (usuario):** ≥48 h antes 100 % · 24–48 h 50 % · <24 h 0 % de reintegro; no se cancela dos veces ni lo ya realizado | 409 |
| R12 | **Reevaluar:** actividad dada de baja, feriado en que no opera o condiciones desfavorables → se cancela sola, reintegro 100 % | – |
| R13 | **Reevaluar:** PENDIENTE con datos completos y favorables → CONFIRMADA (y se fija el precio definitivo) | – |
| R14 | **Reprogramar:** se revalidan TODAS las reglas para la fecha nueva; se toma el cupo nuevo ANTES de liberar el viejo; no con <24 h | 409 |
| R15 | **Cupo mínimo:** a 24 h de la salida, si no se llegó al mínimo, se cancela la salida entera, reintegro 100 % | – |
| R16 | **Reevaluación programada** cada hora (`reservas.reevaluar.cron`) de todas las reservas de los próximos 15 días | – |
| R17 | Cancelar devuelve el cupo PRIMERO; si Actividades no responde → 503 y la reserva NO queda "cancelada con el lugar ocupado" | 503 |

Estados: `PENDIENTE → CONFIRMADA → CANCELADA` y `PENDIENTE → CANCELADA`. Reprogramar mantiene la reserva viva (puede volver a PENDIENTE
si la fecha nueva no tiene pronóstico todavía).

## 4. Endpoints
**Actividades (8081):** `POST/GET /prestadores`, `GET /prestadores/{id}` · `POST/GET /actividades`, `GET/DELETE /actividades/{id}` ·
`GET /actividades/{id}/disponibilidad?fecha=` · `PATCH /actividades/{id}/cupo` · `PATCH /actividades/{id}/cupo/devolucion`

**Reservas (8082):** `POST/GET /reservas`, `GET /reservas/{id}` · `POST /reservas/{id}/cancelar` · `POST /reservas/{id}/reevaluar` ·
`POST /reservas/{id}/reprogramar` · `POST /reservas/reevaluacion`

## 5. Guion de pruebas (Postman) — fechas: hoy es 27/10/2026
1. `POST :8081/prestadores` → `{"nombre":"Sierras Aventura","contacto":"info@sierras.com"}`
2. `POST :8081/actividades` (ubicación por localidad → prueba la 4ª API):
```json
{"nombre":"Trekking Los Gigantes","prestadorId":1,"localidad":"Villa General Belgrano",
 "cupoMaximo":10,"cupoMinimo":3,"precio":45000,"horaInicio":"09:00","duracionHoras":6,"edadMinima":12,
 "temporadaDesdeMes":10,"temporadaHastaMes":3,"operaFeriados":true,"recargoFeriadoPct":20,
 "aireLibre":true,"maxProbLluvia":40,"maxVientoKmh":45,"maxAqi":150}
```
3. `POST :8082/reservas` fecha próxima (≤ 15 días) → **CONFIRMADA**, `motivo` con el pronóstico real:
`{"actividadId":1,"usuario":"maxi","fecha":"2026-11-03","cantidadPersonas":2,"edadMenor":20,"adultos":2}`
4. Misma reserva con fecha a 30+ días (ej. `2026-12-05`) → **PENDIENTE** ("Sin pronóstico confiable").
5. Fecha en temporada baja (`2027-06-10`) → **409** fuera de temporada. · `edadMenor: 8` → 409. · `edadMenor: 15, adultos: 0` → 409.
6. Pedir 11 personas → 409 capacidad. · Llenar un día y pedir más → 409 sin cupo. · Otro día sigue teniendo lugar (cupo por fecha).
7. Fecha feriado (ver lista de la API de feriados) → recargo +20 % en `precioTotal`.
8. **Truco para mostrar el clima en vivo:** crear una actividad con `"maxProbLluvia":0,"maxVientoKmh":0` → casi cualquier día es
   inviable → 409 "Condiciones desfavorables: probabilidad de lluvia…".
9. `POST :8082/reservas/1/cancelar` → CANCELADA con `porcentajeReintegro` y `montoReintegro`; `GET :8081/actividades/1/disponibilidad?fecha=2026-11-03` → el cupo volvió.
10. `POST :8082/reservas/{id}/reprogramar` → `{"fecha":"2026-11-05"}`.
11. `POST :8082/reservas/reevaluacion` → resumen `{revisadas, cambiadas, errores}`.
12. Apagar Actividades y reservar → **503**. · Apagar internet y reservar → PENDIENTE (las APIs externas degradan, no rompen).

## 6. Cómo defenderlo
**¿Cómo se reparten las responsabilidades?** Actividades guarda las CONDICIONES de la actividad y es dueño del cupo por día. Reservas
decide si una reserva concreta es viable cruzando esas condiciones con los datos externos y con quién participa. Reservas nunca toca la
base de Actividades: le pide todo por HTTP.

**¿Por qué el cupo es por día y no un número global?** Con un único contador, reservar para el 10/11 gastaba lugares del 20/11. Es un
error de modelo del dominio: la capacidad es "por salida". `OcupacionDia` guarda ocupados por (actividad, fecha).

**¿Cómo evitan la sobreventa?** `SELECT … FOR UPDATE` (bloqueo pesimista) sobre la fila del día dentro de una transacción: dos reservas
simultáneas se serializan. Limitación honesta: la PRIMERA reserva de un día inserta la fila y dos inserciones simultáneas chocarían con
la restricción única (se resolvería con reintento o precreando el calendario).

**¿Qué pasa si falla algo a mitad de camino?** No hay transacción distribuida entre servicios. Se usa el patrón **compensación**
(saga simple): si falla el guardado tras descontar cupo, se devuelve; al reprogramar se toma el lugar nuevo antes de soltar el viejo; al cancelar se
devuelve primero el cupo. Si la compensación misma falla se registra `COMPENSACIÓN FALLIDA` en el log para revisión manual.

**¿Por qué PENDIENTE y no rechazar cuando no hay pronóstico?** El pronóstico solo existe ~15 días hacia adelante; rechazar todas las
reservas lejanas haría inutilizable la plataforma. Tampoco se confirma a ciegas. La reevaluación (manual o programada) decide cuando hay dato.

**¿Por qué calidad del aire no fuerza PENDIENTE y el clima sí?** El aire solo se pronostica pocos días; si lo forzáramos, casi todas
las reservas serían pendientes. Es una condición adicional que se aplica cuando hay dato.

**¿Por qué las reglas son clases "puras" (ReglaClima, ReglaActividad, ReglaPrecio, PoliticaCancelacion)?** Sin Spring ni HTTP: se prueban con tests
unitarios rápidos y el service solo orquesta. Cambiar un umbral no toca la infraestructura.

**¿Por qué un `Clock` inyectado?** Para probar fechas ("faltan 36 horas") sin depender del día real en que se corre el test.

**¿Por qué se cachea feriados por año y no el clima?** La lista de feriados de un año no cambia, el pronóstico sí. Solo se cachea éxito.

**¿Por qué POST para cancelar/reevaluar/reprogramar?** Son acciones con efectos de negocio (devolver cupo, reintegro), no una simple
modificación de datos: se modelan como comandos sobre el recurso.

**¿Por qué no usan transacción en `ReservaService`?** Haría HTTP con una transacción de base abierta; bloquea conexiones. Cada paso local es atómico y se compensa el resto.

**Limitaciones conocidas (decirlas suma):** sin Circuit Breaker ni reintentos (se pueden sumar con Resilience4j); el programador corre en cada
instancia (con varias réplicas haría falta ShedLock); caché en memoria (se pierde al reiniciar); la API gratuita de Open-Meteo es para uso no comercial; aún
falta seguridad (Keycloak/Gateway) y Docker; los feriados se asumen en horario local sin discriminar provincias.

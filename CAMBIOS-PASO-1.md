# Paso 1 — Base del compañero: corregida, comentada y con tests

## 1. Qué se cambió respecto al original (y por qué)
| # | Cambio | Motivo |
|---|--------|--------|
| 1 | `common-lib/pom.xml`: Spring Boot 2.5.6 → 3.2.5 | La librería y los servicios deben usar la misma generación (Boot 3 = `jakarta.*`; Boot 2 = `javax.*`). Mezclarlas rompe en ejecución. |
| 2 | `actividadSpring`: **se agregó `PATCH /actividades/{id}/cupo`** | `ReservaService` llamaba a ese endpoint pero NO existía: toda reserva fallaba con 404. El `ActividadService.descontarCupo` ya estaba, faltaba exponerlo. |
| 3 | Se borraron `ManejadorErrores` de ambos servicios | Duplicaban a `ManejadorErroresComun` (common-lib) y daban respuestas de error con **otro formato**. Ahora todo error tiene el mismo JSON (`ErrorResponse`). |
| 4 | Se borró `com.Reservas.ServicioNoDisponibleException` | Era una copia de la de common-lib; había dos clases con el mismo nombre (una sombreaba a la otra). |
| 5 | `Reserva`: se quitó `@Data` | `@Data` genera `equals/hashCode` sobre todos los campos: desaconsejado en entidades JPA. Con `@Getter/@Setter` alcanza. |
| 6 | Se limpiaron imports sobrantes en `ActividadNoEncontradaException` | Importaba clases de Spring que no usaba. |
| 7 | Comentarios en todos los archivos | Para que el equipo se guíe. |
| 8 | Tests nuevos: `ActividadControllerTest` y `ReservaServiceTest` | Verifican la API y la colaboración entre servicios. |

La lógica del compañero (orquestación en `ReservaService`, timeouts en `ClientesConfig`, `ActividadDto` propio, descuento
de cupo transaccional) se **conservó tal cual**: era correcta.

## 2. Qué prueban los tests
- `ActividadControllerTest`: crear/consultar (201/200), 404 y 400 con formato común, descontar cupo y luego 409.
- `ReservaServiceTest`: reserva OK → `CONFIRMADA`; sin cupo → `CupoAgotadoException` y NO guarda; actividad inexistente → 404;
  Actividades caído (5xx) → `ServicioNoDisponibleException` (503) y NO guarda. Usa `MockRestServiceServer`, o sea simula a
  Actividades sin levantarlo.

## 3. Cómo defenderlo
**¿Qué responsabilidad tiene cada microservicio?** Actividades es dueño del catálogo y del **cupo**. Reservas es dueño de las
reservas y **orquesta** el proceso. Ninguno toca la base del otro.

**¿Quién inicia la colaboración y qué necesita del otro?** Reservas inicia: pide los datos de la actividad (GET) y que descuente
cupo (PATCH). Actividades decide si alcanza el cupo (regla en su service) y responde 409 si no.

**¿Qué pasa si Actividades no responde?** `ClientesConfig` pone timeouts (2 s conectar, 3 s leer). Si vence o responde 5xx,
`ReservaService` lanza `ServicioNoDisponibleException` → 503 y **no se guarda** la reserva. Falla rápido y de forma controlada.

**¿Por qué Reservas tiene su propio `ActividadDto` y no comparte la entidad?** Para no acoplar los servicios: cada uno evoluciona su
modelo. `@JsonIgnoreProperties(ignoreUnknown = true)` evita que se rompa si Actividades agrega campos.

**¿Por qué Reserva guarda `actividadId` y no una relación JPA?** La actividad está en otro servicio/otra base: no hay JOIN ni FK entre
servicios. Se referencia por id (consistencia eventual, no transaccional).

**¿Por qué el descuento de cupo está en Actividades?** Porque es el dueño de ese dato; así la regla "no vender más de lo que hay" vive
en un solo lugar. Es `@Transactional`: lectura + validación + escritura atómicas.

**¿Qué contiene common-lib y qué NO?** Contiene lo realmente transversal: formato de error, excepciones base, manejador global.
NO contiene entidades ni reglas de negocio, que quedan en cada servicio. Se consume como dependencia Maven (`mvn install` en `.m2`).

**¿Cómo se activa el manejador común?** Con `@Import(ManejadorErroresComun.class)` en la clase principal, porque está en otro paquete
que Spring no escanea. (Alternativa más avanzada: auto-configuración, como los starters de Spring.)

**Limitación conocida (decirla es un punto a favor):** si falla el guardado de la reserva *después* de descontar el cupo, el cupo queda
descontado sin reserva. Solución prevista: compensación (endpoint para devolver cupo). Se implementa en el paso del clima/cancelaciones.

## 4. Pendiente del enunciado
API Gateway + Security Filter · Keycloak (OIDC/JWT/roles) · API externa (clima) con reglas de viabilidad · Docker Compose (+PostgreSQL)
· diagramas C1 y C2 · reglas de dominio (temporada, edad, cancelación/reprogramación) · entidad Prestador.

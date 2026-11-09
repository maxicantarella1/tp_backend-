package com.Reservas.service;

import com.Reservas.client.AireClient;
import com.Reservas.client.ClimaClient;
import com.Reservas.client.FeriadosClient;
import com.Reservas.dto.ActividadDto;
import com.Reservas.dto.CalidadAire;
import com.Reservas.dto.ConsultaFeriado;
import com.Reservas.dto.PronosticoDia;
import com.Reservas.dto.ReservaRequest;
import com.Reservas.dto.ResumenReevaluacion;
import com.Reservas.exception.ActividadInexistenteException;
import com.Reservas.exception.ActividadNoRealizableException;
import com.Reservas.exception.CupoAgotadoException;
import com.Reservas.exception.ReservaNoEncontradaException;
import com.Reservas.model.EstadoReserva;
import com.Reservas.model.Reserva;
import com.Reservas.repository.ReservaRepository;
import com.Reservas.rules.PoliticaCancelacion;
import com.Reservas.rules.ReglaActividad;
import com.Reservas.rules.ReglaClima;
import com.Reservas.rules.ReglaPrecio;
import com.turismo.common.ConflictoNegocioException;
import com.turismo.common.ServicioNoDisponibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.Reservas.dto.ConsultaFeriado.Estado;
import com.Reservas.rules.ReglaClima.Veredicto;

/**
 * Lógica de negocio de Reservas. Es el ORQUESTADOR: coordina
 *   - al microservicio de Actividades (HTTP): datos de la actividad y CUPO por día (él es el dueño);
 *   - a las APIs externas: clima, calidad del aire y feriados;
 *   - a las reglas puras: ReglaActividad, ReglaClima, ReglaPrecio, PoliticaCancelacion.
 *
 * PRINCIPIO CLAVE: "no inventamos datos". Si falta información externa la reserva queda PENDIENTE; la
 * reevaluación (manual o programada) la confirma o cancela cuando haya datos. Nunca se rechaza ni se
 * confirma a ciegas.
 *
 * Este service NO es @Transactional a propósito: hace llamadas HTTP y no conviene mantener una
 * transacción de base de datos abierta mientras se espera a otro servicio. En su lugar usa
 * COMPENSACIONES (devolver cupo) cuando un paso posterior falla.
 */
@Service
public class ReservaService {

    private static final Logger log = LoggerFactory.getLogger(ReservaService.class);
    // Estados que "ocupan lugar": solo estas reservas se reevalúan y cuentan para el cupo mínimo (R15).
    private static final List<EstadoReserva> ACTIVAS = List.of(EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA);

    private final ReservaRepository repo;
    private final RestClient actividades;   // cliente HTTP hacia el servicio de Actividades
    private final ClimaClient clima;
    private final AireClient aire;
    private final FeriadosClient feriados;
    private final Clock clock;

    public ReservaService(ReservaRepository repo, @Qualifier("actividadesClient") RestClient actividades,
                          ClimaClient clima, AireClient aire, FeriadosClient feriados, Clock clock) {
        this.repo = repo;
        this.actividades = actividades;
        this.clima = clima;
        this.aire = aire;
        this.feriados = feriados;
        this.clock = clock;
    }

    /** Resultado de consultar las fuentes externas para una actividad en una fecha. */
    private record Evaluacion(ReglaClima.Resultado clima, ConsultaFeriado feriado, BigDecimal precio) {

        /** Completa = tenemos pronóstico Y sabemos si es feriado. Solo así se puede CONFIRMAR. */
        boolean completa() {
            return clima.veredicto() != Veredicto.SIN_DATOS && feriado.estado() != Estado.SIN_DATOS;
        }

        EstadoReserva estadoPropuesto() {
            return completa() ? EstadoReserva.CONFIRMADA : EstadoReserva.PENDIENTE;
        }

        String motivo() {
            List<String> partes = new ArrayList<>();
            partes.add(clima.detalle());
            if (feriado.estado() == Estado.ES_FERIADO) partes.add("Feriado: " + feriado.nombre());
            if (feriado.estado() == Estado.SIN_DATOS) partes.add("No se pudo consultar el calendario de feriados");
            return String.join(". ", partes);
        }
    }

    // ============================================================
    //                         CREAR RESERVA
    // ============================================================

    /**
     * Crea una reserva. Orden de los pasos (importa):
     *  1. Traer la actividad (404 si no existe, 503 si Actividades no responde).
     *  2. Reglas propias de la actividad: activa, temporada, capacidad, edad, adulto acompañante.
     *  3. Consultar APIs externas (clima, aire, feriados) y evaluar: si es inviable se RECHAZA acá,
     *     ANTES de tocar el cupo (no hay nada que deshacer).
     *  4. Pedir a Actividades que descuente el cupo de ESA fecha (409 si no alcanza).
     *  5. Guardar la reserva. Si falla el guardado -> COMPENSACIÓN: devolver el cupo descontado.
     */
    public Reserva reservar(ReservaRequest req) {
        // PASO 1: datos de la actividad (llamada HTTP a Actividades). SI SE ROMPE: 404 = id inexistente; 503 = Actividades caído o token de servicio inválido.
        ActividadDto a = obtenerActividad(req.actividadId());
        // PASO 2: reglas R1-R5 (activa, temporada, capacidad, edad, adulto acompañante). Lanza 400/409 con título propio.
        ReglaActividad.validar(a, req.fecha(), req.cantidadPersonas(), req.edadMenor(), req.adultos());

        // PASO 3: APIs externas. Se rechaza ACÁ (antes de tocar el cupo) para no tener nada que deshacer.
        Evaluacion ev = evaluar(a, req.fecha(), req.cantidadPersonas());
        exigirRealizable(a, ev);

        // PASO 4: descuento atómico del cupo en Actividades (dueño del dato). 409 si no alcanza.
        descontarCupo(a.id(), req.fecha(), req.cantidadPersonas());

        // PASO 5: armamos y guardamos la reserva en NUESTRA base (reservasdb).
        Reserva r = new Reserva();
        r.setActividadId(a.id());
        r.setUsuario(req.usuario());
        r.setFecha(req.fecha());
        r.setCantidadPersonas(req.cantidadPersonas());
        r.setEdadMenor(req.edadMenor());
        r.setAdultos(req.adultos());
        r.setPrecioTotal(ev.precio());
        r.setEstado(ev.estadoPropuesto());   // CONFIRMADA si hay datos completos; PENDIENTE si falta clima o feriados
        r.setMotivo(ev.motivo());
        try {
            return repo.save(r);
        } catch (RuntimeException e) {
            devolverCupoSilencioso(a.id(), req.fecha(), req.cantidadPersonas());   // compensación
            throw e;
        }
    }

    public List<Reserva> listar() {
        return repo.findAll();
    }

    public List<Reserva> listarPorUsuario(String usuario) {
        return repo.findByUsuario(usuario);
    }

    public Reserva obtener(Long id) {
        return repo.findById(id).orElseThrow(() -> new ReservaNoEncontradaException(id));
    }

    // ============================================================
    //                    CANCELAR (por el usuario)
    // ============================================================

    /**
     * El usuario cancela. Se devuelve el cupo y se calcula el reintegro según la anticipación
     * (PoliticaCancelacion). No se puede cancelar dos veces ni una actividad que ya pasó.
     */
    public Reserva cancelar(Long id) {
        Reserva r = obtener(id);
        if (r.getEstado() == EstadoReserva.CANCELADA) {
            throw new ConflictoNegocioException("Reserva ya cancelada", "La reserva " + id + " ya está cancelada");
        }
        if (r.getFecha().isBefore(LocalDate.now(clock))) {
            throw new ConflictoNegocioException("Actividad ya realizada", "La actividad ya se realizó; no se puede cancelar");
        }
        // R11: reintegro según anticipación (>=48 h: 100 %, 24-48 h: 50 %, <24 h: 0 %)
        int pct = PoliticaCancelacion.porcentajeReintegroUsuario(r.getFecha(), LocalDateTime.now(clock));
        return cancelarInterno(r, "Cancelada por el usuario", pct);
    }

    // ============================================================
    //            REEVALUAR (una reserva ya existente)
    // ============================================================

    /**
     * Vuelve a mirar las condiciones: una reserva inicialmente válida puede volverse inviable (o poder
     * confirmarse) con el paso del tiempo.
     *  - Actividad dada de baja, feriado en que no opera, clima/aire desfavorable -> se CANCELA solo,
     *    devuelve el cupo y reintegra el 100%.
     *  - PENDIENTE con datos ahora completos y favorables -> pasa a CONFIRMADA.
     *  - Sigue sin haber datos -> no cambia nada.
     */
    public Reserva reevaluar(Long id) {
        Reserva r = obtener(id);
        if (r.getEstado() == EstadoReserva.CANCELADA) {
            throw new ConflictoNegocioException("Reserva cancelada", "La reserva " + id + " está cancelada; no hay nada que reevaluar");
        }
        if (r.getFecha().isBefore(LocalDate.now(clock))) {
            throw new ConflictoNegocioException("Fecha pasada", "La fecha de la reserva ya pasó");
        }
        // Se vuelve a pedir la actividad: pudo darse de baja o cambiar sus límites desde que se reservó.
        ActividadDto a = obtenerActividad(r.getActividadId());
        if (!a.activa()) {
            return cancelarInterno(r, "El prestador dio de baja la actividad", 100);
        }
        Evaluacion ev = evaluar(a, r.getFecha(), r.getCantidadPersonas());

        // Orden de decisión: 1) baja, 2) feriado sin operar, 3) clima desfavorable, 4) confirmar PENDIENTE, 5) sin cambios.
        if (ev.feriado().estado() == Estado.ES_FERIADO && !a.operaFeriados()) {
            return cancelarInterno(r, "La actividad no opera en feriados (" + ev.feriado().nombre() + ")", 100);
        }
        if (ev.clima().veredicto() == Veredicto.DESFAVORABLE) {
            return cancelarInterno(r, "Condiciones desfavorables: " + ev.clima().detalle(), 100);
        }
        if (r.getEstado() == EstadoReserva.PENDIENTE && ev.completa()) {
            r.setEstado(EstadoReserva.CONFIRMADA);
            r.setPrecioTotal(ev.precio());      // ahora sabemos si es feriado: precio definitivo
            r.setMotivo(ev.motivo());
            return repo.save(r);
        }
        return r;   // sin cambios
    }

    /**
     * Reevalúa TODAS las reservas activas de los próximos días. La dispara el programador (ver
     * ReevaluadorProgramado) y también se puede llamar a mano (POST /reservas/reevaluacion).
     * Primero aplica la regla de CUPO MÍNIMO (salidas de hoy y mañana) y luego revisa cada reserva.
     * Un error en una reserva no frena a las demás.
     */
    public ResumenReevaluacion reevaluarTodas() {
        LocalDate hoy = LocalDate.now(clock);
        int revisadas = 0, cambiadas = 0, errores = 0;

        // 1) Cupo mínimo: se mira por salida (actividad + fecha) dentro de las próximas 24 h
        List<Reserva> proximas = repo.findByEstadoInAndFechaBetween(ACTIVAS, hoy, hoy.plusDays(1));
        Map<String, List<Reserva>> salidas = proximas.stream()
                .collect(Collectors.groupingBy(r -> r.getActividadId() + "|" + r.getFecha()));
        for (List<Reserva> salida : salidas.values()) {
            try {
                cambiadas += aplicarCupoMinimo(salida);
            } catch (RuntimeException e) {
                errores++;
                log.warn("No se pudo evaluar el cupo mínimo: {}", e.toString());
            }
        }

        // 2) Reevaluación individual (clima, aire, feriados, baja) dentro del horizonte del pronóstico
        List<Reserva> activas = repo.findByEstadoInAndFechaBetween(ACTIVAS, hoy, hoy.plusDays(ClimaClient.HORIZONTE_DIAS));
        for (Reserva r : activas) {
            revisadas++;
            EstadoReserva antes = r.getEstado();
            try {
                if (reevaluar(r.getId()).getEstado() != antes) cambiadas++;
            } catch (RuntimeException e) {
                errores++;
                log.warn("No se pudo reevaluar la reserva {}: {}", r.getId(), e.toString());
            }
        }
        return new ResumenReevaluacion(revisadas, cambiadas, errores);
    }

    /** Si la salida no llegó al cupo mínimo de la actividad, se cancela entera con reintegro 100%. */
    private int aplicarCupoMinimo(List<Reserva> salida) {
        ActividadDto a = obtenerActividad(salida.get(0).getActividadId());
        int total = salida.stream().mapToInt(Reserva::getCantidadPersonas).sum();
        if (a.cupoMinimo() > 0 && total < a.cupoMinimo()) {
            String motivo = "No se alcanzó el cupo mínimo (" + total + " de " + a.cupoMinimo()
                    + " personas): la salida no se realiza";
            salida.forEach(r -> cancelarInterno(r, motivo, 100));
            return salida.size();
        }
        return 0;
    }

    // ============================================================
    //                         REPROGRAMAR
    // ============================================================

    /**
     * Cambia la fecha de una reserva. Se vuelven a validar TODAS las reglas para la fecha nueva.
     * ORDEN SEGURO: primero se toma el cupo de la fecha nueva y recién después se libera el de la vieja;
     * si algo falla, la reserva original queda intacta (nunca se queda sin lugar).
     * No se puede reprogramar con menos de 24 h de anticipación.
     */
    public Reserva reprogramar(Long id, LocalDate nueva) {
        Reserva r = obtener(id);
        if (r.getEstado() == EstadoReserva.CANCELADA) {
            throw new ConflictoNegocioException("Reserva cancelada", "La reserva " + id + " está cancelada; no se puede reprogramar");
        }
        if (nueva.equals(r.getFecha())) {
            throw new ConflictoNegocioException("Reprogramación sin cambios", "La nueva fecha es igual a la actual");
        }
        if (r.getFecha().isBefore(LocalDate.now(clock).plusDays(1))) {
            throw new ConflictoNegocioException("Reprogramación fuera de plazo", "No se puede reprogramar con menos de 24 horas de anticipación");
        }
        ActividadDto a = obtenerActividad(r.getActividadId());
        ReglaActividad.validar(a, nueva, r.getCantidadPersonas(), r.getEdadMenor(), r.getAdultos());
        Evaluacion ev = evaluar(a, nueva, r.getCantidadPersonas());
        exigirRealizable(a, ev);

        descontarCupo(a.id(), nueva, r.getCantidadPersonas());           // 1) fecha nueva (409 si no hay lugar)
        try {
            devolverCupo(a.id(), r.getFecha(), r.getCantidadPersonas()); // 2) liberar la vieja
        } catch (RuntimeException e) {
            devolverCupoSilencioso(a.id(), nueva, r.getCantidadPersonas()); // compensación
            throw e;
        }
        r.setFecha(nueva);
        r.setPrecioTotal(ev.precio());
        r.setEstado(ev.estadoPropuesto());
        r.setMotivo("Reprogramada. " + ev.motivo());
        return repo.save(r);
    }

    // ============================================================
    //                      MÉTODOS AUXILIARES
    // ============================================================

    /** Consulta las APIs externas y arma la evaluación (veredicto de clima/aire, feriado y precio). */
    private Evaluacion evaluar(ActividadDto a, LocalDate fecha, int personas) {
        // Clima y aire solo se consultan si la actividad es al aire libre y tiene coordenadas (bajo techo no depende del clima).
        boolean consultarAmbiente = a.aireLibre() && a.latitud() != null && a.longitud() != null;
        Optional<PronosticoDia> pronostico = consultarAmbiente
                ? clima.pronostico(a.latitud(), a.longitud(), fecha) : Optional.empty();
        Optional<CalidadAire> calidad = consultarAmbiente
                ? aire.calidad(a.latitud(), a.longitud(), fecha) : Optional.empty();

        ReglaClima.Resultado resultadoClima = ReglaClima.evaluar(a, fecha, pronostico, calidad);
        // El feriado se consulta SIEMPRE: afecta al precio (recargo R8) y a si la actividad opera (R9).
        ConsultaFeriado feriado = feriados.consultar(fecha);
        BigDecimal precio = ReglaPrecio.calcular(a, personas, feriado.estado() == Estado.ES_FERIADO);
        return new Evaluacion(resultadoClima, feriado, precio);
    }

    /** Rechaza (409) si la actividad no opera ese feriado o si las condiciones externas la hacen inviable. */
    private void exigirRealizable(ActividadDto a, Evaluacion ev) {
        if (ev.feriado().estado() == Estado.ES_FERIADO && !a.operaFeriados()) {
            throw new ActividadNoRealizableException("Actividad no opera en feriados", "La actividad no opera en feriados (" + ev.feriado().nombre() + ")");
        }
        if (ev.clima().veredicto() == Veredicto.DESFAVORABLE) {
            throw new ActividadNoRealizableException("Clima desfavorable", "Condiciones desfavorables: " + ev.clima().detalle());
        }
    }

    /**
     * Cancelación común (usuario o sistema). Primero se DEVUELVE el cupo (si Actividades no responde se
     * lanza 503 y la reserva NO se marca cancelada: así no queda "cancelada pero con el lugar ocupado").
     */
    private Reserva cancelarInterno(Reserva r, String motivo, int porcentajeReintegro) {
        devolverCupo(r.getActividadId(), r.getFecha(), r.getCantidadPersonas());
        r.setEstado(EstadoReserva.CANCELADA);
        r.setMotivo(motivo);
        r.setPorcentajeReintegro(porcentajeReintegro);
        // Monto a devolver = precio total x porcentaje / 100, redondeado a 2 decimales (HALF_UP)
        BigDecimal base = r.getPrecioTotal() == null ? BigDecimal.ZERO : r.getPrecioTotal();
        r.setMontoReintegro(base.multiply(BigDecimal.valueOf(porcentajeReintegro))
                .movePointLeft(2).setScale(2, RoundingMode.HALF_UP));
        return repo.save(r);
    }

    /**
     * GET a Actividades. Traduce los errores HTTP del otro servicio a excepciones de NUESTRO dominio:
     *  404 -> ActividadInexistenteException; caído / timeout / 5xx -> ServicioNoDisponibleException (503).
     */
    private ActividadDto obtenerActividad(Long id) {
        try {
            return actividades.get()
                    .uri("/actividades/{id}", id)
                    .retrieve()
                    .body(ActividadDto.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ActividadInexistenteException(id);
        } catch (ResourceAccessException | HttpServerErrorException
                 | HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            // 401/403: Actividades rechazó nuestro token de servicio (mala configuración): para el cliente es "no disponible"
            throw new ServicioNoDisponibleException("actividades-service");
        }
    }

    /** PATCH a Actividades para ocupar lugares ese día. 409 -> CupoAgotadoException. */
    private void descontarCupo(Long id, LocalDate fecha, int cantidad) {
        try {
            actividades.patch()
                    .uri("/actividades/{id}/cupo", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("fecha", fecha.toString(), "cantidad", cantidad))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Conflict e) {
            throw new CupoAgotadoException(id);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ActividadInexistenteException(id);
        } catch (ResourceAccessException | HttpServerErrorException
                 | HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            // 401/403: Actividades rechazó nuestro token de servicio (mala configuración): para el cliente es "no disponible"
            throw new ServicioNoDisponibleException("actividades-service");
        }
    }

    /** PATCH a Actividades para liberar lugares (cancelación / reprogramación / compensación). */
    private void devolverCupo(Long id, LocalDate fecha, int cantidad) {
        try {
            actividades.patch()
                    .uri("/actividades/{id}/cupo/devolucion", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("fecha", fecha.toString(), "cantidad", cantidad))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
            throw new ActividadInexistenteException(id);
        } catch (ResourceAccessException | HttpServerErrorException
                 | HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            // 401/403: Actividades rechazó nuestro token de servicio (mala configuración): para el cliente es "no disponible"
            throw new ServicioNoDisponibleException("actividades-service");
        }
    }

    /** Devolución "de mejor esfuerzo" dentro de una compensación: si también falla, solo se registra en el log. */
    private void devolverCupoSilencioso(Long id, LocalDate fecha, int cantidad) {
        try {
            devolverCupo(id, fecha, cantidad);
        } catch (RuntimeException e) {
            log.error("COMPENSACIÓN FALLIDA: no se pudo devolver {} lugares de la actividad {} para {}. Requiere revisión manual.",
                    cantidad, id, fecha, e);
        }
    }
}

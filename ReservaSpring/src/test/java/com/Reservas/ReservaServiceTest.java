package com.Reservas;

import com.Reservas.client.AireClient;
import com.Reservas.client.ClimaClient;
import com.Reservas.client.FeriadosClient;
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
import com.Reservas.service.ReservaService;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.turismo.common.ConflictoNegocioException;
import com.turismo.common.ServicioNoDisponibleException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Prueba el ORQUESTADOR completo sin Internet y sin levantar Actividades:
 *  - Actividades se simula con MockRestServiceServer (respuestas HTTP falsas);
 *  - clima, aire y feriados se simulan con Mockito;
 *  - el repositorio también (sin base de datos);
 *  - el reloj está FIJO en 26/10/2026 12:00 (Córdoba) para que las fechas no dependan del día en que se corra.
 * Truco útil: si el servicio hiciera una llamada HTTP que no esperamos (ej. descontar cupo cuando debía
 * rechazar antes), el mock falla el test. Así se prueba que "no se toca el cupo" en los rechazos.
 */
class ReservaServiceTest {

    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-10-26T15:00:00Z"), ZoneId.of("America/Argentina/Cordoba"));
    private static final LocalDate FECHA = LocalDate.of(2026, 11, 10);

    /** Actividad base: temporada oct-mar, 12+ años, cupo 10, $1000/persona, recargo feriado 20%, lluvia máx 30%. */
    private static final String ACTIVIDAD = """
            {"id":1,"nombre":"Trekking","activa":true,"latitud":-31.4,"longitud":-64.2,"precio":1000,
             "cupoMaximo":10,"cupoMinimo":0,"horaInicio":"09:00","duracionHoras":6,"edadMinima":12,
             "temporadaDesdeMes":10,"temporadaHastaMes":3,"operaFeriados":true,"recargoFeriadoPct":20,
             "aireLibre":true,"maxProbLluvia":30,"maxVientoKmh":40,"maxAqi":150}""";

    private MockRestServiceServer server;
    private ReservaRepository repo;
    private ClimaClient clima;
    private AireClient aire;
    private FeriadosClient feriados;
    private ReservaService service;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://actividades");
        server = MockRestServiceServer.bindTo(builder).build();
        repo = mock(ReservaRepository.class);
        clima = mock(ClimaClient.class);
        aire = mock(AireClient.class);
        feriados = mock(FeriadosClient.class);
        service = new ReservaService(repo, builder.build(), clima, aire, feriados, RELOJ);

        // Por defecto: clima favorable, aire sin datos, día común, guardar devuelve lo recibido
        when(repo.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));
        when(clima.pronostico(anyDouble(), anyDouble(), any(LocalDate.class)))
                .thenReturn(Optional.of(new PronosticoDia(10, 15, 1, LocalTime.of(19, 30))));
        when(aire.calidad(anyDouble(), anyDouble(), any(LocalDate.class))).thenReturn(Optional.empty());
        when(feriados.consultar(any(LocalDate.class))).thenReturn(ConsultaFeriado.no());
    }

    // ---------------- helpers ----------------

    private ReservaRequest pedido(int personas, int edadMenor, int adultos) {
        return new ReservaRequest(1L, "maxi", FECHA, personas, edadMenor, adultos);
    }

    private void esperarGetActividad(String json) {
        server.expect(requestTo("http://actividades/actividades/1")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private void esperarDescuentoDeCupo() {
        server.expect(requestTo("http://actividades/actividades/1/cupo")).andExpect(method(HttpMethod.PATCH))
                .andRespond(withSuccess());
    }

    private void esperarDevolucionDeCupo() {
        server.expect(requestTo("http://actividades/actividades/1/cupo/devolucion")).andExpect(method(HttpMethod.PATCH))
                .andRespond(withSuccess());
    }

    private Reserva reservaExistente(EstadoReserva estado, LocalDate fecha) {
        Reserva r = new Reserva();
        r.setId(7L);
        r.setActividadId(1L);
        r.setUsuario("maxi");
        r.setFecha(fecha);
        r.setCantidadPersonas(2);
        r.setEdadMenor(20);
        r.setAdultos(2);
        r.setPrecioTotal(new BigDecimal("2000.00"));
        r.setEstado(estado);
        when(repo.findById(7L)).thenReturn(Optional.of(r));
        return r;
    }

    // ================= CREAR =================

    @Test
    void reservaOk_confirmada_conPrecio_yEnviaLaFechaAlDescontarCupo() {
        esperarGetActividad(ACTIVIDAD);
        server.expect(requestTo("http://actividades/actividades/1/cupo")).andExpect(method(HttpMethod.PATCH))
                .andExpect(content().string(containsString("2026-11-10")))
                .andRespond(withSuccess());

        Reserva r = service.reservar(pedido(2, 20, 2));

        assertEquals(EstadoReserva.CONFIRMADA, r.getEstado());
        assertEquals(new BigDecimal("2000.00"), r.getPrecioTotal());
        server.verify();
    }

    @Test
    void feriadoQueLaActividadOpera_aplicaRecargo() {
        when(feriados.consultar(any(LocalDate.class))).thenReturn(ConsultaFeriado.es("Feriado puente"));
        esperarGetActividad(ACTIVIDAD);
        esperarDescuentoDeCupo();

        Reserva r = service.reservar(pedido(2, 20, 2));

        assertEquals(new BigDecimal("2400.00"), r.getPrecioTotal());
        assertEquals(EstadoReserva.CONFIRMADA, r.getEstado());
    }

    @Test
    void feriadoQueLaActividadNoOpera_rechaza_sinTocarElCupo() {
        when(feriados.consultar(any(LocalDate.class))).thenReturn(ConsultaFeriado.es("Feriado puente"));
        esperarGetActividad(ACTIVIDAD.replace("\"operaFeriados\":true", "\"operaFeriados\":false"));

        assertThrows(ActividadNoRealizableException.class, () -> service.reservar(pedido(2, 20, 2)));
        server.verify();   // solo hubo el GET: no se descontó cupo
        verify(repo, never()).save(any());
    }

    @Test
    void sinPronostico_quedaPendiente_peroReservaElCupo() {
        when(clima.pronostico(anyDouble(), anyDouble(), any(LocalDate.class))).thenReturn(Optional.empty());
        esperarGetActividad(ACTIVIDAD);
        esperarDescuentoDeCupo();

        Reserva r = service.reservar(pedido(2, 20, 2));

        assertEquals(EstadoReserva.PENDIENTE, r.getEstado());
        server.verify();
    }

    @Test
    void apiDeFeriadosCaida_quedaPendiente() {
        when(feriados.consultar(any(LocalDate.class))).thenReturn(ConsultaFeriado.sinDatos());
        esperarGetActividad(ACTIVIDAD);
        esperarDescuentoDeCupo();

        assertEquals(EstadoReserva.PENDIENTE, service.reservar(pedido(2, 20, 2)).getEstado());
    }

    @Test
    void climaDesfavorable_rechaza_sinTocarElCupo() {
        when(clima.pronostico(anyDouble(), anyDouble(), any(LocalDate.class)))
                .thenReturn(Optional.of(new PronosticoDia(80, 15, 1, LocalTime.of(19, 30))));
        esperarGetActividad(ACTIVIDAD);

        ActividadNoRealizableException e =
                assertThrows(ActividadNoRealizableException.class, () -> service.reservar(pedido(2, 20, 2)));

        assertTrue(e.getMessage().contains("lluvia"));
        server.verify();
        verify(repo, never()).save(any());
    }

    @Test
    void fueraDeTemporada_rechaza() {
        esperarGetActividad(ACTIVIDAD.replace("\"temporadaDesdeMes\":10,\"temporadaHastaMes\":3",
                "\"temporadaDesdeMes\":6,\"temporadaHastaMes\":8"));
        assertThrows(ActividadNoRealizableException.class, () -> service.reservar(pedido(2, 20, 2)));
        server.verify();
    }

    @Test
    void edadInsuficiente_rechaza() {
        esperarGetActividad(ACTIVIDAD);
        assertThrows(ActividadNoRealizableException.class, () -> service.reservar(pedido(2, 8, 1)));
    }

    @Test
    void menorSinAdulto_rechaza() {
        esperarGetActividad(ACTIVIDAD);
        assertThrows(ActividadNoRealizableException.class, () -> service.reservar(pedido(2, 15, 0)));
    }

    @Test
    void sinCupo_lanzaCupoAgotado_yNoGuarda() {
        esperarGetActividad(ACTIVIDAD);
        server.expect(requestTo("http://actividades/actividades/1/cupo")).andRespond(withStatus(HttpStatus.CONFLICT));

        assertThrows(CupoAgotadoException.class, () -> service.reservar(pedido(2, 20, 2)));
        verify(repo, never()).save(any());
    }

    @Test
    void actividadInexistente_404() {
        server.expect(requestTo("http://actividades/actividades/1")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThrows(ActividadInexistenteException.class, () -> service.reservar(pedido(2, 20, 2)));
    }

    @Test
    void actividadesCaido_503_yNoGuarda() {
        server.expect(requestTo("http://actividades/actividades/1")).andRespond(withServerError());
        assertThrows(ServicioNoDisponibleException.class, () -> service.reservar(pedido(2, 20, 2)));
        verify(repo, never()).save(any());
    }

    @Test
    void siFallaElGuardado_devuelveElCupo_compensacion() {
        when(repo.save(any(Reserva.class))).thenThrow(new RuntimeException("base caída"));
        esperarGetActividad(ACTIVIDAD);
        esperarDescuentoDeCupo();
        esperarDevolucionDeCupo();   // <- la compensación

        assertThrows(RuntimeException.class, () -> service.reservar(pedido(2, 20, 2)));
        server.verify();
    }

    // ================= CANCELAR =================

    @Test
    void cancelarConAnticipacion_devuelveCupo_yReintegroTotal() {
        Reserva r = reservaExistente(EstadoReserva.CONFIRMADA, LocalDate.of(2026, 11, 5));
        esperarDevolucionDeCupo();

        Reserva res = service.cancelar(7L);

        assertEquals(EstadoReserva.CANCELADA, res.getEstado());
        assertEquals(100, res.getPorcentajeReintegro());
        assertEquals(0, new BigDecimal("2000.00").compareTo(res.getMontoReintegro()));
        server.verify();
        assertSame(r, res);
    }

    @Test
    void cancelarEntre24y48Horas_reintegroDel50() {
        reservaExistente(EstadoReserva.CONFIRMADA, LocalDate.of(2026, 10, 28));
        esperarDevolucionDeCupo();

        Reserva res = service.cancelar(7L);

        assertEquals(50, res.getPorcentajeReintegro());
        assertEquals(0, new BigDecimal("1000.00").compareTo(res.getMontoReintegro()));
    }

    @Test
    void cancelarElMismoDia_sinReintegro() {
        reservaExistente(EstadoReserva.CONFIRMADA, LocalDate.of(2026, 10, 26));
        esperarDevolucionDeCupo();

        Reserva res = service.cancelar(7L);

        assertEquals(0, res.getPorcentajeReintegro());
        assertEquals(0, BigDecimal.ZERO.compareTo(res.getMontoReintegro()));
    }

    @Test
    void cancelarDosVeces_conflicto() {
        reservaExistente(EstadoReserva.CANCELADA, FECHA);
        assertThrows(ConflictoNegocioException.class, () -> service.cancelar(7L));
    }

    @Test
    void cancelarUnaActividadQueYaPaso_conflicto() {
        reservaExistente(EstadoReserva.CONFIRMADA, LocalDate.of(2026, 10, 20));
        assertThrows(ConflictoNegocioException.class, () -> service.cancelar(7L));
    }

    @Test
    void siActividadesNoResponde_nuncaQuedaCanceladaConElLugarOcupado() {
        Reserva r = reservaExistente(EstadoReserva.CONFIRMADA, FECHA);
        server.expect(requestTo("http://actividades/actividades/1/cupo/devolucion")).andRespond(withServerError());

        assertThrows(ServicioNoDisponibleException.class, () -> service.cancelar(7L));
        assertEquals(EstadoReserva.CONFIRMADA, r.getEstado());   // sigue confirmada: no se marcó cancelada
    }

    @Test
    void reservaInexistente_404() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ReservaNoEncontradaException.class, () -> service.cancelar(99L));
    }

    // ================= REEVALUAR =================

    @Test
    void reevaluar_cancelaSola_siElClimaEmpeoro_conReintegroTotal() {
        reservaExistente(EstadoReserva.CONFIRMADA, FECHA);
        when(clima.pronostico(anyDouble(), anyDouble(), any(LocalDate.class)))
                .thenReturn(Optional.of(new PronosticoDia(90, 15, 1, LocalTime.of(19, 30))));
        esperarGetActividad(ACTIVIDAD);
        esperarDevolucionDeCupo();

        Reserva r = service.reevaluar(7L);

        assertEquals(EstadoReserva.CANCELADA, r.getEstado());
        assertEquals(100, r.getPorcentajeReintegro());
        assertTrue(r.getMotivo().contains("lluvia"));
        server.verify();
    }

    @Test
    void reevaluar_confirmaUnaPendienteSiYaHayDatosFavorables() {
        reservaExistente(EstadoReserva.PENDIENTE, FECHA);
        esperarGetActividad(ACTIVIDAD);

        assertEquals(EstadoReserva.CONFIRMADA, service.reevaluar(7L).getEstado());
    }

    @Test
    void reevaluar_sinDatos_noCambiaNada() {
        reservaExistente(EstadoReserva.PENDIENTE, FECHA);
        when(clima.pronostico(anyDouble(), anyDouble(), any(LocalDate.class))).thenReturn(Optional.empty());
        esperarGetActividad(ACTIVIDAD);

        assertEquals(EstadoReserva.PENDIENTE, service.reevaluar(7L).getEstado());
        server.verify();   // ni cupo ni nada más
    }

    @Test
    void reevaluar_cancelaSiElPrestadorDioDeBajaLaActividad() {
        reservaExistente(EstadoReserva.CONFIRMADA, FECHA);
        esperarGetActividad(ACTIVIDAD.replace("\"activa\":true", "\"activa\":false"));
        esperarDevolucionDeCupo();

        Reserva r = service.reevaluar(7L);

        assertEquals(EstadoReserva.CANCELADA, r.getEstado());
        assertTrue(r.getMotivo().contains("baja"));
    }

    @Test
    void reevaluar_cancelaSiLaFechaPasoAFeriadoEnQueNoOpera() {
        reservaExistente(EstadoReserva.CONFIRMADA, FECHA);
        when(feriados.consultar(any(LocalDate.class))).thenReturn(ConsultaFeriado.es("Feriado puente"));
        esperarGetActividad(ACTIVIDAD.replace("\"operaFeriados\":true", "\"operaFeriados\":false"));
        esperarDevolucionDeCupo();

        assertEquals(EstadoReserva.CANCELADA, service.reevaluar(7L).getEstado());
    }

    // ================= REPROGRAMAR =================

    @Test
    void reprogramar_tomaElCupoDeLaNuevaFecha_yLiberaLaVieja() {
        reservaExistente(EstadoReserva.CONFIRMADA, FECHA);
        esperarGetActividad(ACTIVIDAD);
        esperarDescuentoDeCupo();      // 1º: fecha nueva
        esperarDevolucionDeCupo();     // 2º: fecha vieja

        Reserva r = service.reprogramar(7L, LocalDate.of(2026, 11, 20));

        assertEquals(LocalDate.of(2026, 11, 20), r.getFecha());
        server.verify();
    }

    @Test
    void reprogramar_sinCupoEnLaNueva_laOriginalQuedaIntacta() {
        Reserva r = reservaExistente(EstadoReserva.CONFIRMADA, FECHA);
        esperarGetActividad(ACTIVIDAD);
        server.expect(requestTo("http://actividades/actividades/1/cupo")).andRespond(withStatus(HttpStatus.CONFLICT));

        assertThrows(CupoAgotadoException.class, () -> service.reprogramar(7L, LocalDate.of(2026, 11, 20)));

        assertEquals(FECHA, r.getFecha());   // no se liberó nada ni se cambió la fecha
        server.verify();
    }

    @Test
    void reprogramar_conMenosDe24Horas_conflicto() {
        reservaExistente(EstadoReserva.CONFIRMADA, LocalDate.of(2026, 10, 26));
        assertThrows(ConflictoNegocioException.class, () -> service.reprogramar(7L, LocalDate.of(2026, 11, 20)));
    }

    @Test
    void reprogramar_aLaMismaFecha_conflicto() {
        reservaExistente(EstadoReserva.CONFIRMADA, FECHA);
        assertThrows(ConflictoNegocioException.class, () -> service.reprogramar(7L, FECHA));
    }

    @Test
    void reprogramar_aUnaFechaConClimaMalo_rechaza_yNoMueveCupo() {
        reservaExistente(EstadoReserva.CONFIRMADA, FECHA);
        when(clima.pronostico(anyDouble(), anyDouble(), any(LocalDate.class)))
                .thenReturn(Optional.of(new PronosticoDia(95, 15, 1, LocalTime.of(19, 30))));
        esperarGetActividad(ACTIVIDAD);

        assertThrows(ActividadNoRealizableException.class, () -> service.reprogramar(7L, LocalDate.of(2026, 11, 20)));
        server.verify();
    }

    // ================= CUPO MÍNIMO / REEVALUACIÓN MASIVA =================

    @Test
    void reevaluarTodas_cancelaLaSalidaQueNoLlegoAlCupoMinimo() {
        Reserva r = reservaExistente(EstadoReserva.CONFIRMADA, LocalDate.of(2026, 10, 27)); // mañana
        // 1ª consulta (ventana de 24 h) devuelve la reserva; 2ª (reevaluación individual) ya no hay activas
        when(repo.findByEstadoInAndFechaBetween(any(), any(), any())).thenReturn(List.of(r)).thenReturn(List.of());
        esperarGetActividad(ACTIVIDAD.replace("\"cupoMinimo\":0", "\"cupoMinimo\":5"));   // piden 2 de un mínimo de 5
        esperarDevolucionDeCupo();

        ResumenReevaluacion resumen = service.reevaluarTodas();

        assertEquals(EstadoReserva.CANCELADA, r.getEstado());
        assertTrue(r.getMotivo().contains("cupo mínimo"));
        assertEquals(1, resumen.cambiadas());
        assertEquals(0, resumen.errores());
    }

    @Test
    void reevaluarTodas_unaReservaConErrorNoFrenaALasDemas() {
        Reserva r = reservaExistente(EstadoReserva.PENDIENTE, FECHA);
        when(repo.findByEstadoInAndFechaBetween(any(), any(), any())).thenReturn(List.of()).thenReturn(List.of(r));
        server.expect(requestTo("http://actividades/actividades/1")).andRespond(withServerError());  // Actividades caído

        ResumenReevaluacion resumen = service.reevaluarTodas();

        assertEquals(1, resumen.revisadas());
        assertEquals(1, resumen.errores());
    }
}

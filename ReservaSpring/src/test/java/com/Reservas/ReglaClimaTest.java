package com.Reservas;

import com.Reservas.dto.ActividadDto;
import com.Reservas.dto.CalidadAire;
import com.Reservas.dto.PronosticoDia;
import com.Reservas.rules.ReglaClima;
import static org.junit.jupiter.api.Assertions.*;

import com.Reservas.rules.ReglaClima.Veredicto;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Tests unitarios de la regla que decide si el clima/aire permiten realizar la actividad. */
class ReglaClimaTest {

    private static final LocalDate DIA = LocalDate.of(2026, 11, 10);
    // base(): inicia 09:00, dura 6 h (termina 15:00), lluvia máx 30%, viento máx 40, AQI máx 150

    private PronosticoDia pron(int lluvia, double viento, int codigo, LocalTime atardecer) {
        return new PronosticoDia(lluvia, viento, codigo, atardecer);
    }

    private ReglaClima.Resultado evaluar(Optional<PronosticoDia> p, Optional<CalidadAire> c) {
        return ReglaClima.evaluar(TestDatos.base(), DIA, p, c);
    }

    @Test
    void todoDentroDeLosLimites_favorable() {
        var r = evaluar(Optional.of(pron(10, 15, 1, LocalTime.of(19, 30))), Optional.empty());
        assertEquals(Veredicto.FAVORABLE, r.veredicto());
    }

    @Test
    void valorIgualAlLimite_noEsProblema() {
        var r = evaluar(Optional.of(pron(30, 40, 1, LocalTime.of(19, 30))), Optional.empty());
        assertEquals(Veredicto.FAVORABLE, r.veredicto());
    }

    @Test
    void lluviaSuperaElLimite_desfavorable() {
        var r = evaluar(Optional.of(pron(80, 15, 1, LocalTime.of(19, 30))), Optional.empty());
        assertEquals(Veredicto.DESFAVORABLE, r.veredicto());
        assertTrue(r.detalle().contains("lluvia"));
    }

    @Test
    void vientoSuperaElLimite_desfavorable() {
        var r = evaluar(Optional.of(pron(10, 55, 1, LocalTime.of(19, 30))), Optional.empty());
        assertEquals(Veredicto.DESFAVORABLE, r.veredicto());
        assertTrue(r.detalle().contains("viento"));
    }

    @Test
    void tormenta_desfavorable() {
        var r = evaluar(Optional.of(pron(10, 15, 95, LocalTime.of(19, 30))), Optional.empty());
        assertEquals(Veredicto.DESFAVORABLE, r.veredicto());
        assertTrue(r.detalle().contains("tormenta"));
    }

    @Test
    void terminaDespuesDelAtardecer_desfavorable() {
        // empieza 09:00 + 6 h = 15:00; atardecer 14:30 -> no llega a terminar con luz
        var r = evaluar(Optional.of(pron(10, 15, 1, LocalTime.of(14, 30))), Optional.empty());
        assertEquals(Veredicto.DESFAVORABLE, r.veredicto());
        assertTrue(r.detalle().contains("atardecer"));
    }

    @Test
    void aireMalo_desfavorable_aunqueElClimaSeaBueno() {
        var r = evaluar(Optional.of(pron(10, 15, 1, LocalTime.of(19, 30))), Optional.of(new CalidadAire(180)));
        assertEquals(Veredicto.DESFAVORABLE, r.veredicto());
        assertTrue(r.detalle().contains("aire"));
    }

    @Test
    void aireMaloSinPronostico_igualDesfavorable() {
        var r = evaluar(Optional.empty(), Optional.of(new CalidadAire(180)));
        assertEquals(Veredicto.DESFAVORABLE, r.veredicto());
    }

    @Test
    void sinPronostico_sinDatos() {
        var r = evaluar(Optional.empty(), Optional.empty());
        assertEquals(Veredicto.SIN_DATOS, r.veredicto());
    }

    @Test
    void actividadBajoTecho_ignoraElClima() {
        ActividadDto interior = TestDatos.base(true, 10, 3, 12, 10, false);
        var r = ReglaClima.evaluar(interior, DIA,
                Optional.of(pron(100, 99, 99, LocalTime.of(10, 0))), Optional.of(new CalidadAire(400)));
        assertEquals(Veredicto.FAVORABLE, r.veredicto());
    }
}

package com.Reservas;

import com.Reservas.rules.PoliticaCancelacion;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PoliticaCancelacionTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 26, 12, 0);

    @Test
    void conMasDe48Horas_reintegroTotal() {
        assertEquals(100, PoliticaCancelacion.porcentajeReintegroUsuario(LocalDate.of(2026, 11, 5), AHORA));
    }

    @Test
    void entre24y48Horas_reintegroParcial() {
        // 26/10 12:00 -> 28/10 00:00 = 36 horas
        assertEquals(50, PoliticaCancelacion.porcentajeReintegroUsuario(LocalDate.of(2026, 10, 28), AHORA));
    }

    @Test
    void conMenosDe24Horas_sinReintegro() {
        // 26/10 12:00 -> 27/10 00:00 = 12 horas
        assertEquals(0, PoliticaCancelacion.porcentajeReintegroUsuario(LocalDate.of(2026, 10, 27), AHORA));
    }
}

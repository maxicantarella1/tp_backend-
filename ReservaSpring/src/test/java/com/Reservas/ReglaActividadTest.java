package com.Reservas;

import com.Reservas.exception.ActividadNoRealizableException;
import com.Reservas.rules.ReglaActividad;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Tests unitarios (sin Spring) de las reglas propias de la actividad. */
class ReglaActividadTest {

    private static final LocalDate NOVIEMBRE = LocalDate.of(2026, 11, 10);

    @Test
    void temporadaNormal_y_temporadaQueCruzaElAnio() {
        assertTrue(ReglaActividad.enTemporada(5, 3, 9));
        assertFalse(ReglaActividad.enTemporada(11, 3, 9));
        // 10 a 3: oct, nov, dic, ene, feb, mar
        assertTrue(ReglaActividad.enTemporada(11, 10, 3));
        assertTrue(ReglaActividad.enTemporada(2, 10, 3));
        assertFalse(ReglaActividad.enTemporada(6, 10, 3));
    }

    @Test
    void grupoValido_noLanza() {
        assertDoesNotThrow(() -> ReglaActividad.validar(TestDatos.base(), NOVIEMBRE, 2, 20, 2));
    }

    @Test
    void actividadInactiva_rechaza() {
        assertThrows(ActividadNoRealizableException.class,
                () -> ReglaActividad.validar(TestDatos.base(false, 10, 3, 12, 10, true), NOVIEMBRE, 2, 20, 2));
    }

    @Test
    void fueraDeTemporada_rechaza() {
        assertThrows(ActividadNoRealizableException.class,
                () -> ReglaActividad.validar(TestDatos.base(true, 6, 8, 12, 10, true), NOVIEMBRE, 2, 20, 2));
    }

    @Test
    void grupoMayorAlCupoMaximo_rechaza() {
        assertThrows(ActividadNoRealizableException.class,
                () -> ReglaActividad.validar(TestDatos.base(), NOVIEMBRE, 11, 20, 11));
    }

    @Test
    void edadInsuficiente_rechaza() {
        assertThrows(ActividadNoRealizableException.class,
                () -> ReglaActividad.validar(TestDatos.base(), NOVIEMBRE, 2, 8, 1));
    }

    @Test
    void menorSinAdulto_rechaza() {
        // 15 años cumple la edad mínima (12) pero es menor de 18 y no hay adulto
        assertThrows(ActividadNoRealizableException.class,
                () -> ReglaActividad.validar(TestDatos.base(), NOVIEMBRE, 2, 15, 0));
    }

    @Test
    void menorConAdulto_ok() {
        assertDoesNotThrow(() -> ReglaActividad.validar(TestDatos.base(), NOVIEMBRE, 2, 15, 1));
    }

    @Test
    void masAdultosQueTotal_rechaza() {
        assertThrows(ActividadNoRealizableException.class,
                () -> ReglaActividad.validar(TestDatos.base(), NOVIEMBRE, 2, 20, 3));
    }
}

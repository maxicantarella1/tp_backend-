package com.Reservas;

import com.Reservas.dto.ActividadDto;
import com.Reservas.rules.ReglaPrecio;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ReglaPrecioTest {

    @Test
    void diaComun_precioPorPersonas() {
        assertEquals(new BigDecimal("3000.00"), ReglaPrecio.calcular(TestDatos.base(), 3, false));
    }

    @Test
    void feriado_aplicaRecargo() {
        // base: $1000 por persona, recargo 20% -> 2 personas = 2000 * 1.20 = 2400
        assertEquals(new BigDecimal("2400.00"), ReglaPrecio.calcular(TestDatos.base(), 2, true));
    }

    @Test
    void feriadoSinRecargoConfigurado_noCambia() {
        ActividadDto sinRecargo = new ActividadDto(1L, "X", true, -31.4, -64.2, 1000.0, 10, 0,
                java.time.LocalTime.of(9, 0), 6, 12, 10, 3, true, 0, true, 30, 40.0, 150);
        assertEquals(new BigDecimal("2000.00"), ReglaPrecio.calcular(sinRecargo, 2, true));
    }
}

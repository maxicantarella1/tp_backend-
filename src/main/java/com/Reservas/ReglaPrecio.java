package com.Reservas;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calcula el precio total de una reserva: precio por persona x personas, con recargo si la fecha es feriado.
 * Usa BigDecimal (no double) para dinero: evita errores de redondeo binario.
 */
public final class ReglaPrecio {

    private ReglaPrecio() { }

    public static BigDecimal calcular(ActividadDto a, int personas, boolean esFeriado) {
        BigDecimal total = BigDecimal.valueOf(a.precio()).multiply(BigDecimal.valueOf(personas));
        if (esFeriado && a.recargoFeriadoPct() > 0) {
            BigDecimal factor = BigDecimal.ONE.add(BigDecimal.valueOf(a.recargoFeriadoPct()).movePointLeft(2));
            total = total.multiply(factor);
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }
}

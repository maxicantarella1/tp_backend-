package com.Reservas.rules;

import com.Reservas.dto.ActividadDto;
import com.Reservas.exception.ActividadNoRealizableException;
import java.time.LocalDate;

/**
 * REGLAS que dependen solo de la actividad y de quién participa (no de datos externos).
 * Es una clase de funciones puras (sin Spring ni HTTP): se prueba con tests unitarios simples.
 * Si alguna regla falla lanza ActividadNoRealizableException con el motivo.
 */
public final class ReglaActividad {

    private ReglaActividad() { }

    /** Mayoría de edad: un menor no puede participar sin un adulto. */
    public static final int MAYORIA_EDAD = 18;

    public static void validar(ActividadDto a, LocalDate fecha, int personas, int edadMenor, int adultos) {
        // R1. La actividad debe estar activa (el prestador no la dio de baja)
        if (!a.activa()) {
            throw new ActividadNoRealizableException("Actividad dada de baja", "La actividad '" + a.nombre() + "' ya no está disponible");
        }
        // R2. Debe estar dentro de su época del año
        if (!enTemporada(fecha.getMonthValue(), a.temporadaDesdeMes(), a.temporadaHastaMes())) {
            throw new ActividadNoRealizableException("Fuera de temporada", "La actividad solo se realiza entre los meses "
                    + a.temporadaDesdeMes() + " y " + a.temporadaHastaMes() + " (fecha pedida: mes "
                    + fecha.getMonthValue() + ")");
        }
        // R3. Un grupo más grande que el cupo máximo diario nunca podría entrar
        if (personas > a.cupoMaximo()) {
            throw new ActividadNoRealizableException("Capacidad máxima excedida", "El grupo (" + personas + ") supera la capacidad máxima de la actividad ("
                    + a.cupoMaximo() + ")");
        }
        // R4. Edad mínima: se mide contra el participante MÁS JOVEN del grupo
        if (edadMenor < a.edadMinima()) {
            throw new ActividadNoRealizableException("Edad mínima no cumplida", "Edad mínima " + a.edadMinima()
                    + " años; el participante más joven tiene " + edadMenor);
        }
        // R5. Los adultos no pueden ser más que el total, y un menor necesita al menos un adulto
        if (adultos > personas) {
            throw new ActividadNoRealizableException("Cantidad de adultos inválida", "La cantidad de adultos (" + adultos + ") supera al total de personas (" + personas + ")");
        }
        if (edadMenor < MAYORIA_EDAD && adultos < 1) {
            throw new ActividadNoRealizableException("Menores sin adulto acompañante", "Hay participantes menores de " + MAYORIA_EDAD
                    + " años: se requiere al menos un adulto acompañante");
        }
    }

    /**
     * ¿El mes está dentro de la temporada? Si desde <= hasta es un rango normal (3 a 9).
     * Si desde > hasta la temporada cruza el fin de año (11 a 3 = nov, dic, ene, feb, mar).
     */
    public static boolean enTemporada(int mes, int desde, int hasta) {
        return desde <= hasta ? (mes >= desde && mes <= hasta) : (mes >= desde || mes <= hasta);
    }
}

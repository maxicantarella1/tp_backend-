package com.Reservas.dto;

/**
 * Resultado de preguntar "¿esa fecha es feriado?". Tiene TRES estados (no un boolean) porque
 * "no sé" es distinto de "no es feriado": si la API cayó no podemos asumir nada.
 */
public record ConsultaFeriado(Estado estado, String nombre) {

    public enum Estado { ES_FERIADO, NO_ES_FERIADO, SIN_DATOS }

    public static ConsultaFeriado es(String nombre) { return new ConsultaFeriado(Estado.ES_FERIADO, nombre); }
    public static ConsultaFeriado no()              { return new ConsultaFeriado(Estado.NO_ES_FERIADO, null); }
    public static ConsultaFeriado sinDatos()        { return new ConsultaFeriado(Estado.SIN_DATOS, null); }
}

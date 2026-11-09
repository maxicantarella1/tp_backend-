package com.turismo.common;

/**
 * Otro microservicio o una API externa (ej: la del clima) no responde. Se traduce a HTTP 503.
 * Permite fallar de forma controlada en lugar de quedarse colgado o devolver un error 500 confuso.
 * Uso: throw new ServicioNoDisponibleException("actividades-service");
 */
public class ServicioNoDisponibleException extends RuntimeException {
    /** Título corto del error (va en el campo "error" de la respuesta). */
    public String getTitulo() { return "Servicio no disponible"; }

    public ServicioNoDisponibleException(String servicio) {
        super("El servicio " + servicio + " no está disponible, intente más tarde");
    }
}

package com.Reservas;

public class ServicioNoDisponibleException extends RuntimeException {
    public ServicioNoDisponibleException(String servicio) {
        super("El servicio " + servicio + " no está disponible, intente más tarde");
    }
}

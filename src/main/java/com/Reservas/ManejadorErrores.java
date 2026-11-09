package com.Reservas;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler({ReservaNoEncontradaException.class, ActividadInexistenteException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> noEncontrado(RuntimeException e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler(CupoAgotadoException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> conflicto(CupoAgotadoException e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler(ServicioNoDisponibleException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, String> noDisponible(ServicioNoDisponibleException e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> invalido(MethodArgumentNotValidException e) {
        return Map.of("error", "Datos inválidos",
                "detalle", e.getBindingResult().getFieldErrors().stream()
                        .map(f -> f.getField() + " " + f.getDefaultMessage())
                        .toList());
    }
}

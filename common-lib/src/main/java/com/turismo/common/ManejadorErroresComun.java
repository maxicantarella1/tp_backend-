package com.turismo.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Manejador GLOBAL de errores, compartido por todos los microservicios.
 * @RestControllerAdvice hace que Spring intercepte las excepciones lanzadas por cualquier
 * controller y las convierta en una respuesta JSON (ErrorResponse) con el código HTTP correcto.
 * Beneficio: controllers y services solo LANZAN excepciones; nadie arma respuestas de error a mano.
 *
 * Cada microservicio lo activa con  @Import(ManejadorErroresComun.class)  en su clase principal.
 */
@RestControllerAdvice
public class ManejadorErroresComun {

    /** Cualquier subclase de RecursoNoEncontradoException -> 404. */
    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> noEncontrado(RecursoNoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND, e.getMessage()));
    }

    /** Cualquier subclase de ConflictoNegocioException (sin cupo, clima...) -> 409. */
    @ExceptionHandler(ConflictoNegocioException.class)
    public ResponseEntity<ErrorResponse> conflicto(ConflictoNegocioException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT, e.getMessage()));
    }

    /** Un servicio del que dependemos no responde -> 503. */
    @ExceptionHandler(ServicioNoDisponibleException.class)
    public ResponseEntity<ErrorResponse> noDisponible(ServicioNoDisponibleException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage()));
    }

    /**
     * Falló una validación de @Valid (nombre vacío, cupo negativo...) -> 400.
     * Junta todos los campos incorrectos en un solo texto: "campo1 mensaje; campo2 mensaje".
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> invalido(MethodArgumentNotValidException e) {
        String detalle = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, "Datos inválidos: " + detalle));
    }
}

package com.Reservas;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API REST de reservas. Solo traduce HTTP <-> service; la lógica está en ReservaService.
 *   POST /reservas      -> crea una reserva (201)
 *   GET  /reservas      -> lista
 *   GET  /reservas/{id} -> detalle (404 si no existe)
 */
@RestController
@RequestMapping("/reservas")
public class ReservaController {

    private final ReservaService service;

    public ReservaController(ReservaService service) {
        this.service = service;
    }

    /** @Valid valida el ReservaRequest; si falla, el manejador común responde 400. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Reserva crear(@Valid @RequestBody ReservaRequest req) {
        return service.reservar(req);
    }

    @GetMapping
    public List<Reserva> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public Reserva obtener(@PathVariable Long id) {
        return service.obtener(id);
    }
}

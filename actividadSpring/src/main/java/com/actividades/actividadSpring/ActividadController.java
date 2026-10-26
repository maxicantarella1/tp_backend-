package com.actividades.actividadSpring;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API REST de actividades. Verbos y códigos HTTP:
 *   GET    -> leer (200)        POST -> crear (201)
 *   DELETE -> borrar (204)      PATCH -> modificar solo una parte (el cupo)
 * @Valid dispara las validaciones; si fallan, el manejador común responde 400.
 * Errores de negocio (404, 409) los traduce el manejador común de common-lib.
 */
@RestController
@RequestMapping("/actividades")
public class ActividadController {

    private final ActividadRepository repo;
    private final ActividadService service;

    public ActividadController(ActividadRepository repo, ActividadService service) {
        this.repo = repo;
        this.service = service;
    }

    /** GET /actividades -> todas. */
    @GetMapping
    public List<Actividad> listar() {
        return repo.findAll();
    }

    /** GET /actividades/{id} -> una, o 404 si no existe. */
    @GetMapping("/{id}")
    public Actividad obtener(@PathVariable Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ActividadNoEncontradaException(id));
    }

    /** POST /actividades -> crea una nueva. Responde 201. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)          // 201
    public Actividad crear(@Valid @RequestBody Actividad a) {
        return repo.save(a);
    }

    /** DELETE /actividades/{id} -> borra. Responde 204 (sin cuerpo). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)       // 204
    public void borrar(@PathVariable Long id) {
        repo.deleteById(id);
    }

    /**
     * PATCH /actividades/{id}/cupo  con { "cantidad": N }
     * CAMBIO RESPECTO AL ORIGINAL: este endpoint FALTABA y Reservas lo invoca para reservar.
     * Lo llama el servicio de Reservas; responde 409 si no alcanza el cupo.
     */
    @PatchMapping("/{id}/cupo")
    public Actividad descontarCupo(@PathVariable Long id, @Valid @RequestBody CupoRequest req) {
        return service.descontarCupo(id, req.cantidad());
    }
}

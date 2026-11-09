package com.actividades.actividadSpring;

import com.turismo.common.Seguridad;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * API REST de actividades. Solo traduce HTTP <-> service; las reglas están en ActividadService.
 *   GET    /actividades                          -> listar                                  [público]
 *   GET    /actividades/{id}                     -> detalle (404 si no existe)              [público]
 *   GET    /actividades/{id}/disponibilidad?fecha=  -> cupo de un día                       [público]
 *   POST   /actividades                          -> crear (201)         [admin, o prestador DUEÑO]
 *   DELETE /actividades/{id}                     -> baja lógica (204)   [admin, o prestador DUEÑO]
 *   PATCH  /actividades/{id}/cupo                -> descontar cupo -> 409 si no alcanza     [servicio, admin]
 *   PATCH  /actividades/{id}/cupo/devolucion     -> devolver cupo (compensación)            [servicio, admin]
 * El cupo solo lo modifica la CUENTA DE SERVICIO de Reservas: un turista no puede "regalarse" lugares.
 * Los errores (404, 409, 400) los traduce el manejador común de common-lib.
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

    @GetMapping
    public List<Actividad> listar() {
        return repo.findAll();
    }

    @GetMapping("/{id}")
    public Actividad obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    /** @Valid aplica las validaciones de la entidad (rangos de mes, % de lluvia, etc.). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','PRESTADOR')")
    public Actividad crear(@Valid @RequestBody Actividad a, Authentication auth) {
        return service.crear(a, Seguridad.usuario(auth), Seguridad.tieneRol(auth, "admin"));
    }

    /** CAMBIO: antes borraba la fila (deleteById). Ahora es baja lógica para no dejar reservas huérfanas. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','PRESTADOR')")
    public void darDeBaja(@PathVariable Long id, Authentication auth) {
        service.darDeBaja(id, Seguridad.usuario(auth), Seguridad.tieneRol(auth, "admin"));
    }

    @GetMapping("/{id}/disponibilidad")
    public CupoResponse disponibilidad(@PathVariable Long id,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return service.disponibilidad(id, fecha);
    }

    @PatchMapping("/{id}/cupo")
    @PreAuthorize("hasAnyRole('SERVICIO','ADMIN')")
    public CupoResponse descontarCupo(@PathVariable Long id, @Valid @RequestBody CupoRequest req) {
        return service.descontarCupo(id, req.fecha(), req.cantidad());
    }

    @PatchMapping("/{id}/cupo/devolucion")
    @PreAuthorize("hasAnyRole('SERVICIO','ADMIN')")
    public CupoResponse devolverCupo(@PathVariable Long id, @Valid @RequestBody CupoRequest req) {
        return service.devolverCupo(id, req.fecha(), req.cantidad());
    }
}

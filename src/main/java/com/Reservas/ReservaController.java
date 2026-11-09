package com.Reservas;

import com.turismo.common.AccesoDenegadoException;
import com.turismo.common.Seguridad;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API REST de reservas. Solo traduce HTTP <-> service; la lógica está en ReservaService.
 * AUTORIZACIÓN en dos capas:
 *   1) ROL  (@PreAuthorize): qué tipo de usuario puede llamar a cada operación.
 *   2) PROPIEDAD (método propia()): un turista solo opera sobre SUS reservas; el admin sobre todas.
 *
 *   POST /reservas                    crear (201)                       [turista, admin]
 *   GET  /reservas                    listar: las propias / admin todas [turista, admin]
 *   GET  /reservas/{id}               detalle                           [dueño, admin]
 *   POST /reservas/{id}/cancelar      cancelar con reintegro            [dueño, admin]
 *   POST /reservas/{id}/reevaluar     reevaluar clima/aire/feriados     [dueño, admin]
 *   POST /reservas/{id}/reprogramar   cambiar la fecha                  [dueño, admin]
 *   POST /reservas/reevaluacion       reevaluar TODAS                   [solo admin]
 * Errores: 401 sin token · 403 sin rol o reserva ajena · 400/404/409/503 los traduce el manejador común.
 */
@RestController
@RequestMapping("/reservas")
public class ReservaController {

    private final ReservaService service;

    public ReservaController(ReservaService service) {
        this.service = service;
    }

    /**
     * El usuario de la reserva sale del TOKEN, no del cuerpo: así nadie puede reservar a nombre de otro.
     * (Un admin sí puede indicar otro usuario en el campo "usuario", por ejemplo al cargar una reserva telefónica.)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('TURISTA','ADMIN')")
    public Reserva crear(@Valid @RequestBody ReservaRequest req, Authentication auth) {
        boolean adminIndicaUsuario = Seguridad.tieneRol(auth, "admin") && req.usuario() != null && !req.usuario().isBlank();
        String usuario = adminIndicaUsuario ? req.usuario() : Seguridad.usuario(auth);
        return service.reservar(new ReservaRequest(req.actividadId(), usuario, req.fecha(),
                req.cantidadPersonas(), req.edadMenor(), req.adultos()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TURISTA','ADMIN')")
    public List<Reserva> listar(Authentication auth) {
        return Seguridad.tieneRol(auth, "admin") ? service.listar() : service.listarPorUsuario(Seguridad.usuario(auth));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TURISTA','ADMIN')")
    public Reserva obtener(@PathVariable Long id, Authentication auth) {
        return propia(id, auth);
    }

    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('TURISTA','ADMIN')")
    public Reserva cancelar(@PathVariable Long id, Authentication auth) {
        propia(id, auth);
        return service.cancelar(id);
    }

    @PostMapping("/{id}/reevaluar")
    @PreAuthorize("hasAnyRole('TURISTA','ADMIN')")
    public Reserva reevaluar(@PathVariable Long id, Authentication auth) {
        propia(id, auth);
        return service.reevaluar(id);
    }

    @PostMapping("/{id}/reprogramar")
    @PreAuthorize("hasAnyRole('TURISTA','ADMIN')")
    public Reserva reprogramar(@PathVariable Long id, @Valid @RequestBody ReprogramarRequest req, Authentication auth) {
        propia(id, auth);
        return service.reprogramar(id, req.fecha());
    }

    /** Operación de mantenimiento: la dispara un administrador (el programador la ejecuta solo, sin pasar por acá). */
    @PostMapping("/reevaluacion")
    @PreAuthorize("hasRole('ADMIN')")
    public ResumenReevaluacion reevaluarTodas() {
        return service.reevaluarTodas();
    }

    /**
     * Regla de PROPIEDAD: devuelve la reserva si es del usuario autenticado (o si es admin); si no, 403.
     * (Se responde 403 y no 404 para ser simple; devolver 404 evitaría revelar que el id existe.)
     * Si la reserva no existe, service.obtener lanza 404.
     */
    private Reserva propia(Long id, Authentication auth) {
        Reserva r = service.obtener(id);
        if (!Seguridad.tieneRol(auth, "admin") && !Seguridad.usuario(auth).equals(r.getUsuario())) {
            throw new AccesoDenegadoException("La reserva " + id + " no te pertenece");
        }
        return r;
    }
}

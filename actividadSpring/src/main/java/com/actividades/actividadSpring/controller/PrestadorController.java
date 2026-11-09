package com.actividades.actividadSpring.controller;

import com.actividades.actividadSpring.exception.PrestadorNoEncontradoException;
import com.actividades.actividadSpring.model.Prestador;
import com.actividades.actividadSpring.repository.PrestadorRepository;
import com.turismo.common.Seguridad;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API REST de prestadores:
 *   POST /prestadores      -> crear (201)            [rol: admin o prestador]
 *   GET  /prestadores      -> listar                 [público]
 *   GET  /prestadores/{id} -> detalle (404)          [público]
 * El dueño del prestador es el usuario autenticado que lo crea (salvo que un admin indique otro en "usuario").
 */
@RestController
@RequestMapping("/prestadores")
public class PrestadorController {

    private final PrestadorRepository repo;

    public PrestadorController(PrestadorRepository repo) {
        this.repo = repo;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','PRESTADOR')")
    public Prestador crear(@Valid @RequestBody Prestador p, Authentication auth) {
        p.setId(null); // por si el cliente manda un id: siempre se crea uno NUEVO
        boolean adminIndicaDueno = Seguridad.tieneRol(auth, "admin") && p.getUsuario() != null && !p.getUsuario().isBlank();
        if (!adminIndicaDueno) {
            p.setUsuario(Seguridad.usuario(auth));   // un prestador NO puede crear prestadores a nombre de otro
        }
        return repo.save(p);
    }

    @GetMapping
    public List<Prestador> listar() {
        return repo.findAll();
    }

    @GetMapping("/{id}")
    public Prestador obtener(@PathVariable Long id) {
        return repo.findById(id).orElseThrow(() -> new PrestadorNoEncontradoException(id));
    }
}

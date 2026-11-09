package com.actividades.actividadSpring;

import com.turismo.common.AccesoDenegadoException;
import com.turismo.common.SolicitudInvalidaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Capa de NEGOCIO de actividades. Es DUEÑA de dos cosas: el catálogo y el cupo por día.
 * Reservas nunca toca estas tablas: le pide las operaciones por HTTP.
 * @Transactional: cada método corre en una transacción; si lanza excepción hay rollback.
 */
@Service
public class ActividadService {

    private final ActividadRepository repo;
    private final PrestadorRepository prestadores;
    private final OcupacionDiaRepository ocupaciones;
    private final GeocodingClient geocoding;

    public ActividadService(ActividadRepository repo, PrestadorRepository prestadores,
                            OcupacionDiaRepository ocupaciones, GeocodingClient geocoding) {
        this.repo = repo;
        this.prestadores = prestadores;
        this.ocupaciones = ocupaciones;
        this.geocoding = geocoding;
    }

    // ============================ CATÁLOGO ============================

    /**
     * Crea una actividad. REGLAS:
     *  - el prestador debe existir (si no, 404);
     *  - PROPIEDAD: solo el dueño del prestador (o un admin) puede crearle actividades (si no, 403);
     *  - el cupo mínimo no puede superar al máximo (400);
     *  - debe haber ubicación: coordenadas, o una localidad que se resuelve con la API de geocodificación (400).
     */
    @Transactional
    public Actividad crear(Actividad a, String usuario, boolean esAdmin) {
        Prestador prestador = prestadores.findById(a.getPrestadorId())
                .orElseThrow(() -> new PrestadorNoEncontradoException(a.getPrestadorId()));
        exigirPropiedad(prestador, usuario, esAdmin);
        if (a.getCupoMinimo() > a.getCupoMaximo()) {
            throw new SolicitudInvalidaException("El cupo mínimo (" + a.getCupoMinimo()
                    + ") no puede superar al cupo máximo (" + a.getCupoMaximo() + ")");
        }
        resolverUbicacion(a);
        a.setId(null);        // siempre se crea una NUEVA
        a.setActiva(true);    // una actividad nueva siempre nace activa
        return repo.save(a);
    }

    /** Si faltan coordenadas, las obtiene de la API externa a partir de la localidad. */
    private void resolverUbicacion(Actividad a) {
        if (a.getLatitud() != null && a.getLongitud() != null) {
            return;
        }
        if (a.getLocalidad() == null || a.getLocalidad().isBlank()) {
            throw new SolicitudInvalidaException("Informá la ubicación: latitud y longitud, o una localidad");
        }
        Coordenadas c = geocoding.buscar(a.getLocalidad());
        a.setLatitud(c.latitud());
        a.setLongitud(c.longitud());
    }

    @Transactional(readOnly = true)
    public Actividad obtener(Long id) {
        return repo.findById(id).orElseThrow(() -> new ActividadNoEncontradaException(id));
    }

    /** Baja lógica: no se borra la fila, se marca inactiva. Solo el dueño del prestador o un admin. */
    @Transactional
    public void darDeBaja(Long id, String usuario, boolean esAdmin) {
        Actividad a = obtener(id);
        exigirPropiedad(prestadores.findById(a.getPrestadorId())
                .orElseThrow(() -> new PrestadorNoEncontradoException(a.getPrestadorId())), usuario, esAdmin);
        a.setActiva(false);   // JPA guarda el cambio al terminar la transacción
    }

    /** Regla de propiedad: el rol "prestador" no alcanza, además tiene que ser SU prestador. */
    private void exigirPropiedad(Prestador prestador, String usuario, boolean esAdmin) {
        if (!esAdmin && (usuario == null || !usuario.equals(prestador.getUsuario()))) {
            throw new AccesoDenegadoException("El prestador " + prestador.getId() + " no te pertenece");
        }
    }

    // ============================== CUPO ==============================

    /**
     * Reserva lugares para una fecha. REGLAS:
     *  - la actividad debe existir y estar activa;
     *  - no se pueden ocupar más lugares de los libres ese día (si no -> 409).
     * El bloqueo pesimista evita la sobreventa cuando llegan reservas simultáneas.
     */
    @Transactional
    public CupoResponse descontarCupo(Long id, LocalDate fecha, int cantidad) {
        Actividad a = obtener(id);
        if (!a.isActiva()) {
            throw new ActividadInactivaException(id);
        }
        OcupacionDia o = ocupaciones.buscarBloqueando(id, fecha).orElseGet(() -> nueva(id, fecha));

        int libres = a.getCupoMaximo() - o.getOcupados();
        if (cantidad > libres) {
            throw new CupoInsuficienteException(id, cantidad, libres);
        }
        o.setOcupados(o.getOcupados() + cantidad);
        ocupaciones.save(o);
        return respuesta(a, fecha, o.getOcupados());
    }

    /**
     * Operación inversa (COMPENSACIÓN): devuelve lugares cuando una reserva se cancela, se reprograma
     * o falló después de haber descontado. Nunca deja el contador por debajo de cero.
     */
    @Transactional
    public CupoResponse devolverCupo(Long id, LocalDate fecha, int cantidad) {
        Actividad a = obtener(id);
        OcupacionDia o = ocupaciones.buscarBloqueando(id, fecha).orElseGet(() -> nueva(id, fecha));
        o.setOcupados(Math.max(0, o.getOcupados() - cantidad));
        ocupaciones.save(o);
        return respuesta(a, fecha, o.getOcupados());
    }

    /** Consulta cuántos lugares quedan en una fecha (no modifica nada). */
    @Transactional(readOnly = true)
    public CupoResponse disponibilidad(Long id, LocalDate fecha) {
        Actividad a = obtener(id);
        int ocupados = ocupaciones.findByActividadIdAndFecha(id, fecha)
                .map(OcupacionDia::getOcupados).orElse(0);
        return respuesta(a, fecha, ocupados);
    }

    private OcupacionDia nueva(Long id, LocalDate fecha) {
        OcupacionDia o = new OcupacionDia();
        o.setActividadId(id);
        o.setFecha(fecha);
        o.setOcupados(0);
        return o;
    }

    private CupoResponse respuesta(Actividad a, LocalDate fecha, int ocupados) {
        return new CupoResponse(a.getId(), fecha, a.getCupoMaximo(), ocupados, a.getCupoMaximo() - ocupados);
    }
}

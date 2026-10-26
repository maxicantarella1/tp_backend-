package com.Reservas;

import com.turismo.common.ServicioNoDisponibleException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Lógica de negocio de Reservas. ESTE es el proceso que atraviesa dos microservicios:
 *   Reservas (inicia) --HTTP--> Actividades (dueño del cupo).
 * Reservas NO toca la base de Actividades: le pide las cosas por HTTP, así siguen independientes.
 */
@Service
public class ReservaService {

    private final ReservaRepository repo;
    private final RestClient actividades;   // cliente HTTP hacia el servicio de Actividades

    public ReservaService(ReservaRepository repo, RestClient actividadesClient) {
        this.repo = repo;
        this.actividades = actividadesClient;
    }

    /**
     * Crea una reserva. Orden de los pasos (importa):
     *  1. Verificar que la actividad exista.
     *  2. Pedir a Actividades que descuente el cupo (ella decide si alcanza -> 409).
     *  3. Recién ahora guardar la reserva localmente.
     * LIMITE CONOCIDO: si falla el paso 3 después del 2, el cupo queda descontado sin reserva.
     * Se resolverá con una compensación (devolver el cupo) en un próximo paso.
     */
    public Reserva reservar(ReservaRequest req) {
        // 1. Verifico que la actividad exista (más adelante: acá se consulta el clima)
        ActividadDto actividad = obtenerActividad(req.actividadId());

        // 2. Pido a Actividades que descuente el cupo (ella decide si alcanza)
        descontarCupo(actividad.id(), req.cantidadPersonas());

        // 3. Recién ahora guardo mi reserva
        Reserva r = new Reserva();
        r.setActividadId(actividad.id());
        r.setUsuario(req.usuario());
        r.setFecha(req.fecha());
        r.setCantidadPersonas(req.cantidadPersonas());
        r.setEstado(EstadoReserva.CONFIRMADA);
        return repo.save(r);
    }

    public List<Reserva> listar() {
        return repo.findAll();
    }

    public Reserva obtener(Long id) {
        return repo.findById(id).orElseThrow(() -> new ReservaNoEncontradaException(id));
    }

    /**
     * GET a Actividades. Traduce los errores HTTP del otro servicio a excepciones de NUESTRO dominio:
     *  404 -> ActividadInexistenteException; servicio caído / error 5xx -> ServicioNoDisponibleException (503).
     */
    private ActividadDto obtenerActividad(Long id) {
        try {
            return actividades.get()
                    .uri("/actividades/{id}", id)
                    .retrieve()
                    .body(ActividadDto.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ActividadInexistenteException(id);
        } catch (ResourceAccessException | HttpServerErrorException e) {
            // ResourceAccessException = no conecta / timeout. HttpServerErrorException = respondió 5xx.
            throw new ServicioNoDisponibleException("actividades-service");
        }
    }

    /** PATCH a Actividades para descontar cupo. 409 -> CupoAgotadoException. */
    private void descontarCupo(Long id, int cantidad) {
        try {
            actividades.patch()
                    .uri("/actividades/{id}/cupo", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("cantidad", cantidad))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Conflict e) {
            throw new CupoAgotadoException(id);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ActividadInexistenteException(id);
        } catch (ResourceAccessException | HttpServerErrorException e) {
            throw new ServicioNoDisponibleException("actividades-service");
        }
    }
}

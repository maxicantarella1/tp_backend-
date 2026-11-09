package com.Reservas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Integración con la API pública de FERIADOS de Argentina (ArgentinaDatos, sin clave):
 *   GET https://api.argentinadatos.com/v1/feriados/2026   ->  [{"fecha":"2026-11-23","nombre":"...","tipo":"..."}, ...]
 *
 * POR QUÉ SE NECESITA: los feriados (sobre todo los trasladables y los "puente") cambian por decreto cada
 * año; no se pueden dejar como constante en el código. Condicionan si la actividad opera y su precio.
 *
 * CACHE: la lista de un año no cambia durante el año, así que se consulta UNA vez por año y se guarda en
 * memoria. Evita llamar a la API en cada reserva. Solo se cachean respuestas exitosas.
 *
 * SI FALLA: devuelve SIN_DATOS (la reserva queda PENDIENTE; no inventamos si es feriado o no).
 */
@Component
public class FeriadosClient {

    private static final Logger log = LoggerFactory.getLogger(FeriadosClient.class);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Feriado(String fecha, String nombre) { }

    private final RestClient http;
    private final Map<Integer, List<Feriado>> cache = new ConcurrentHashMap<>();

    public FeriadosClient(@Qualifier("feriadosHttp") RestClient http) {
        this.http = http;
    }

    public ConsultaFeriado consultar(LocalDate fecha) {
        List<Feriado> del;
        try {
            del = cache.get(fecha.getYear());
            if (del == null) {
                Feriado[] lista = http.get().uri("/v1/feriados/{anio}", fecha.getYear())
                        .retrieve().body(Feriado[].class);
                if (lista == null) return ConsultaFeriado.sinDatos();
                del = List.of(lista);
                cache.put(fecha.getYear(), del);
            }
        } catch (RuntimeException e) {
            log.warn("API de feriados no disponible ({}): {}", fecha, e.toString());
            return ConsultaFeriado.sinDatos();
        }
        String iso = fecha.toString();
        return del.stream().filter(f -> iso.equals(f.fecha())).findFirst()
                .map(f -> ConsultaFeriado.es(f.nombre()))
                .orElseGet(ConsultaFeriado::no);
    }
}

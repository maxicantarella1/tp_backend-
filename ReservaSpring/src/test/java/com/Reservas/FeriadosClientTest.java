package com.Reservas;

import com.Reservas.client.FeriadosClient;
import com.Reservas.dto.ConsultaFeriado;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.Reservas.dto.ConsultaFeriado.Estado;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FeriadosClientTest {

    private static final String LISTA = """
            [{"fecha":"2026-11-23","tipo":"trasladable","nombre":"Día de la Soberanía Nacional"},
             {"fecha":"2026-12-08","tipo":"inamovible","nombre":"Inmaculada Concepción de María"}]""";

    private MockRestServiceServer server;
    private FeriadosClient cliente;

    @BeforeEach
    void preparar() {
        RestClient.Builder b = RestClient.builder().baseUrl("https://feriados.test");
        server = MockRestServiceServer.bindTo(b).build();
        cliente = new FeriadosClient(b.build());
    }

    @Test
    void detectaFeriado_y_noFeriado() {
        server.expect(requestTo(containsString("/v1/feriados/2026"))).andRespond(withSuccess(LISTA, MediaType.APPLICATION_JSON));

        ConsultaFeriado si = cliente.consultar(LocalDate.of(2026, 11, 23));
        ConsultaFeriado no = cliente.consultar(LocalDate.of(2026, 11, 24));

        assertEquals(Estado.ES_FERIADO, si.estado());
        assertEquals("Día de la Soberanía Nacional", si.nombre());
        assertEquals(Estado.NO_ES_FERIADO, no.estado());
    }

    @Test
    void cacheaElAnio_unaSolaLlamadaALaApi() {
        // expect(...) por defecto admite UNA sola llamada: una segunda al servidor haría fallar el test
        server.expect(requestTo(containsString("/v1/feriados/2026"))).andRespond(withSuccess(LISTA, MediaType.APPLICATION_JSON));
        cliente.consultar(LocalDate.of(2026, 11, 23));
        cliente.consultar(LocalDate.of(2026, 12, 8));
        cliente.consultar(LocalDate.of(2026, 12, 9));
        server.verify();
    }

    @Test
    void apiCaida_sinDatos() {
        server.expect(requestTo(containsString("/v1/feriados/2026"))).andRespond(withServerError());
        assertEquals(Estado.SIN_DATOS, cliente.consultar(LocalDate.of(2026, 11, 23)).estado());
    }

    @Test
    void elErrorNoSeCachea_laSiguienteConsultaReintenta() {
        server.expect(requestTo(containsString("/v1/feriados/2026"))).andRespond(withServerError());
        server.expect(requestTo(containsString("/v1/feriados/2026"))).andRespond(withSuccess(LISTA, MediaType.APPLICATION_JSON));
        assertEquals(Estado.SIN_DATOS, cliente.consultar(LocalDate.of(2026, 11, 23)).estado());
        assertEquals(Estado.ES_FERIADO, cliente.consultar(LocalDate.of(2026, 11, 23)).estado());
    }
}

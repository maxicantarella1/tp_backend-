package com.Reservas;

import com.Reservas.client.AireClient;
import com.Reservas.dto.CalidadAire;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AireClientTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-26T15:00:00Z"), ZoneId.of("America/Argentina/Cordoba"));
    private MockRestServiceServer server;
    private AireClient cliente;

    @BeforeEach
    void preparar() {
        RestClient.Builder b = RestClient.builder().baseUrl("https://aire.test");
        server = MockRestServiceServer.bindTo(b).build();
        cliente = new AireClient(b.build(), RELOJ);
    }

    @Test
    void tomaElPeorValorDelDia() {
        server.expect(requestTo(containsString("/v1/air-quality")))
                .andRespond(withSuccess("""
                        {"hourly":{"time":["a","b","c","d"],"us_aqi":[30.0,45.0,180.0,null]}}""", MediaType.APPLICATION_JSON));
        Optional<CalidadAire> c = cliente.calidad(-31.4, -64.2, LocalDate.of(2026, 10, 28));
        assertEquals(180, c.orElseThrow().aqiMaximo());
    }

    @Test
    void fechaLejana_noConsulta() {
        assertTrue(cliente.calidad(-31.4, -64.2, LocalDate.of(2026, 11, 20)).isEmpty());
        server.verify();
    }

    @Test
    void apiCaida_vacio() {
        server.expect(requestTo(containsString("/v1/air-quality"))).andRespond(withServerError());
        assertTrue(cliente.calidad(-31.4, -64.2, LocalDate.of(2026, 10, 28)).isEmpty());
    }
}

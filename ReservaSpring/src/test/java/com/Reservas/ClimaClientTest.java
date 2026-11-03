package com.Reservas;

import com.Reservas.client.ClimaClient;
import com.Reservas.dto.PronosticoDia;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Prueba la integración con la API de clima SIN Internet: MockRestServiceServer simula sus respuestas. */
class ClimaClientTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-26T15:00:00Z"), ZoneId.of("America/Argentina/Cordoba"));
    private MockRestServiceServer server;
    private ClimaClient cliente;

    @BeforeEach
    void preparar() {
        RestClient.Builder b = RestClient.builder().baseUrl("https://clima.test");
        server = MockRestServiceServer.bindTo(b).build();
        cliente = new ClimaClient(b.build(), RELOJ);
    }

    @Test
    void interpretaLaRespuestaDeLaApi() {
        server.expect(requestTo(containsString("/v1/forecast")))
                .andExpect(queryParam("latitude", "-31.4"))
                .andExpect(queryParam("start_date", "2026-10-28"))
                .andRespond(withSuccess("""
                        {"daily":{"time":["2026-10-28"],"precipitation_probability_max":[65.0],
                         "wind_speed_10m_max":[22.4],"weather_code":[61],"sunset":["2026-10-28T19:41"]}}""",
                        MediaType.APPLICATION_JSON));

        Optional<PronosticoDia> p = cliente.pronostico(-31.4, -64.2, LocalDate.of(2026, 10, 28));

        assertTrue(p.isPresent());
        assertEquals(65, p.get().probLluvia());
        assertEquals(22.4, p.get().vientoKmh(), 0.001);
        assertEquals(61, p.get().codigoClima());
        assertEquals(LocalTime.of(19, 41), p.get().atardecer());
        server.verify();
    }

    @Test
    void apiCaida_devuelveVacio_sinLanzar() {
        server.expect(requestTo(containsString("/v1/forecast"))).andRespond(withServerError());
        assertTrue(cliente.pronostico(-31.4, -64.2, LocalDate.of(2026, 10, 28)).isEmpty());
    }

    @Test
    void fechaFueraDelHorizonte_noConsultaLaApi() {
        // sin expectativas registradas: cualquier request haría fallar el test
        assertTrue(cliente.pronostico(-31.4, -64.2, LocalDate.of(2026, 12, 25)).isEmpty());
        server.verify();
    }

    @Test
    void respuestaConValoresNulos_devuelveVacio() {
        server.expect(requestTo(containsString("/v1/forecast")))
                .andRespond(withSuccess("""
                        {"daily":{"time":["2026-10-28"],"precipitation_probability_max":[null],"wind_speed_10m_max":[10.0]}}""",
                        MediaType.APPLICATION_JSON));
        assertTrue(cliente.pronostico(-31.4, -64.2, LocalDate.of(2026, 10, 28)).isEmpty());
    }

    @Test
    void jsonInesperado_devuelveVacio() {
        server.expect(requestTo(containsString("/v1/forecast")))
                .andRespond(withSuccess("esto no es json", MediaType.APPLICATION_JSON));
        assertTrue(cliente.pronostico(-31.4, -64.2, LocalDate.of(2026, 10, 28)).isEmpty());
    }
}

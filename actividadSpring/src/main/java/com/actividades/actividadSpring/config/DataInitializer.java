package com.actividades.actividadSpring.config;

import com.actividades.actividadSpring.model.Actividad;
import com.actividades.actividadSpring.model.Prestador;
import com.actividades.actividadSpring.repository.ActividadRepository;
import com.actividades.actividadSpring.repository.PrestadorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalTime;

/**
 * Carga inicial de datos de prueba (Data Seeder) cuando el microservicio inicia con la base vacía.
 *
 * POR QUÉ EXISTE (Criterios INF-01 y NEG-01 de la rúbrica):
 *  - Permite demostrar el proceso de negocio central de extremo a extremo desde un entorno limpio (Docker o local)
 *    sin necesidad de cargar prestadores y actividades manualmente antes de probar una reserva.
 *  - Solo se ejecuta si las tablas están vacías (count == 0), por lo que en PostgreSQL no duplica datos al reiniciar.
 */
@Configuration
@ConditionalOnProperty(name = "actividades.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    // CommandLineRunner = Spring lo ejecuta UNA vez al terminar de arrancar la aplicación.
    // SI SE ROMPE (ids que no coinciden con Postman): borrar el volumen de Postgres (docker compose down -v) para recargar desde cero.
    // Los usuarios "prestador1" y "prestador2" existen en keycloak/turismo-realm.json: el vínculo es el NOMBRE de usuario.
    @Bean
    CommandLineRunner inicializarDatosDemo(PrestadorRepository prestadores, ActividadRepository actividades) {
        return args -> {
            if (prestadores.count() > 0 || actividades.count() > 0) {
                log.info("Catálogo existente detectado ({} prestadores, {} actividades). No se requiere carga inicial.",
                        prestadores.count(), actividades.count());
                return;
            }

            // 1. Prestadores asociados a los usuarios del realm "turismo" de Keycloak
            Prestador p1 = new Prestador();
            p1.setNombre("Sierras Aventura");
            p1.setContacto("info@sierrasaventura.com.ar");
            p1.setUsuario("prestador1");
            p1 = prestadores.save(p1);

            Prestador p2 = new Prestador();
            p2.setNombre("Punilla Excursiones");
            p2.setContacto("reservas@punillaexcursiones.com.ar");
            p2.setUsuario("prestador2");
            p2 = prestadores.save(p2);

            // 2. Actividad 1 (id=1): Trekking al aire libre en temporada alta (octubre a abril), opera feriados (+20%)
            Actividad a1 = new Actividad();
            a1.setNombre("Trekking Cerro Champaquí");
            a1.setPrestadorId(p1.getId());
            a1.setLocalidad("Villa General Belgrano");
            a1.setLatitud(-31.98);
            a1.setLongitud(-64.93);
            a1.setCupoMaximo(10);
            a1.setCupoMinimo(3);
            a1.setPrecio(45000);
            a1.setHoraInicio(LocalTime.of(9, 0));
            a1.setDuracionHoras(6);
            a1.setEdadMinima(12);
            a1.setTemporadaDesdeMes(10);
            a1.setTemporadaHastaMes(4);
            a1.setOperaFeriados(true);
            a1.setRecargoFeriadoPct(20);
            a1.setAireLibre(true);
            a1.setMaxProbLluvia(40);
            a1.setMaxVientoKmh(45);
            a1.setMaxAqi(150);
            a1.setActiva(true);
            actividades.save(a1);

            // 3. Actividad 2 (id=2): Actividad bajo techo (no depende del clima), mayores de 18, NO opera feriados
            Actividad a2 = new Actividad();
            a2.setNombre("Visita y Cata en Bodega Calamuchita");
            a2.setPrestadorId(p1.getId());
            a2.setLocalidad("Santa Rosa de Calamuchita");
            a2.setLatitud(-32.06);
            a2.setLongitud(-64.53);
            a2.setCupoMaximo(15);
            a2.setCupoMinimo(2);
            a2.setPrecio(30000);
            a2.setHoraInicio(LocalTime.of(11, 0));
            a2.setDuracionHoras(3);
            a2.setEdadMinima(18);
            a2.setTemporadaDesdeMes(1);
            a2.setTemporadaHastaMes(12);
            a2.setOperaFeriados(false);
            a2.setRecargoFeriadoPct(0);
            a2.setAireLibre(false);
            a2.setMaxProbLluvia(100);
            a2.setMaxVientoKmh(100);
            a2.setMaxAqi(300);
            a2.setActiva(true);
            actividades.save(a2);

            // 4. Actividad 3 (id=3, de prestador2): Umbral climático estricto (lluvia 0%, viento 0 km/h)
            //    Ideal para demostrar en vivo el rechazo 409 por pronóstico desfavorable de Open-Meteo y permisos entre prestadores.
            Actividad a3 = new Actividad();
            a3.setNombre("Kayak en Lago San Roque (Clima Estricto)");
            a3.setPrestadorId(p2.getId());
            a3.setLocalidad("Villa Carlos Paz");
            a3.setLatitud(-31.42);
            a3.setLongitud(-64.49);
            a3.setCupoMaximo(6);
            a3.setCupoMinimo(2);
            a3.setPrecio(38000);
            a3.setHoraInicio(LocalTime.of(9, 0));
            a3.setDuracionHoras(4);
            a3.setEdadMinima(10);
            a3.setTemporadaDesdeMes(10);
            a3.setTemporadaHastaMes(3);
            a3.setOperaFeriados(true);
            a3.setRecargoFeriadoPct(15);
            a3.setAireLibre(true);
            a3.setMaxProbLluvia(0);
            a3.setMaxVientoKmh(0);
            a3.setMaxAqi(100);
            a3.setActiva(true);
            actividades.save(a3);

            log.info("Datos iniciales de demostración cargados: 2 prestadores y 3 actividades listas para probar.");
        };
    }
}

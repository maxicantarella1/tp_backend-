package com.Reservas;

import com.turismo.common.ManejadorErroresComun;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de entrada del microservicio de RESERVAS (puerto 8082).
 * RESPONSABILIDAD: registrar las reservas de los usuarios y orquestar el proceso de reservar:
 * consulta a Actividades (HTTP) y a las APIs externas (clima, calidad del aire, feriados) y aplica las reglas.
 *
 * @Import(ManejadorErroresComun.class): activa los errores HTTP unificados (400/404/409/503) de la librería común.
 * @EnableScheduling: habilita @Scheduled, que usa la reevaluación automática de reservas.
 */
@SpringBootApplication
@Import(ManejadorErroresComun.class)
@EnableScheduling
public class BaseEjemploApplication {

	public static void main(String[] args) {
		SpringApplication.run(BaseEjemploApplication.class, args);
	}
}

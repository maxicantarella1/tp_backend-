package com.Reservas;

import com.turismo.common.ManejadorErroresComun;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * Punto de entrada del microservicio de RESERVAS (puerto 8082).
 * RESPONSABILIDAD: registrar las reservas de los usuarios y orquestar el proceso de reservar,
 * consultando a Actividades (HTTP) y, más adelante, a la API del clima.
 *
 * @Import(ManejadorErroresComun.class): activa los errores HTTP unificados (404/409/503/400)
 * de la librería común.
 */
@SpringBootApplication
@Import(ManejadorErroresComun.class)
public class BaseEjemploApplication {

	public static void main(String[] args) {
		SpringApplication.run(BaseEjemploApplication.class, args);
	}
}

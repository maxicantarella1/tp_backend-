package com.actividades.actividadSpring;

import com.turismo.common.ManejadorErroresComun;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * Punto de entrada del microservicio de ACTIVIDADES (puerto 8081).
 * RESPONSABILIDAD: administrar las actividades turísticas y su CUPO.
 * Es el dueño de esa información: otros servicios (Reservas) le piden cupo, no lo tocan directo.
 *
 * @Import(ManejadorErroresComun.class) activa el manejador de errores de la librería común.
 * Hace falta porque esa clase está en otro paquete (com.turismo.common) que Spring no escanea solo.
 */
@SpringBootApplication
@Import(ManejadorErroresComun.class)
public class ActividadSpringApplication {

	public static void main(String[] args) {
		SpringApplication.run(ActividadSpringApplication.class, args);
	}
}

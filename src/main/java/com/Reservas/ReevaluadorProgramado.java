package com.Reservas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarea programada: cada cierto tiempo reevalúa las reservas activas. Es lo que hace que una reserva
 * "inicialmente válida" pueda cancelarse sola cuando cambia el pronóstico, o confirmarse cuando
 * por fin hay datos. El horario se configura con reservas.reevaluar.cron (por defecto, cada hora).
 */
@Component
public class ReevaluadorProgramado {

    private static final Logger log = LoggerFactory.getLogger(ReevaluadorProgramado.class);
    private final ReservaService service;

    public ReevaluadorProgramado(ReservaService service) {
        this.service = service;
    }

    @Scheduled(cron = "${reservas.reevaluar.cron}")
    public void ejecutar() {
        ResumenReevaluacion r = service.reevaluarTodas();
        log.info("Reevaluación programada: {} revisadas, {} cambiadas, {} con error", r.revisadas(), r.cambiadas(), r.errores());
    }
}

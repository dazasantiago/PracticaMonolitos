package com.rica.investigadoresservice.investigadores.infraestructura.salida.mensajeria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxEventRepository repositorio;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPoller(OutboxEventRepository repositorio, KafkaTemplate<String, String> kafkaTemplate) {
        this.repositorio = repositorio;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void publicarPendientes() {
        List<OutboxEvent> pendientes = repositorio.findTop50ByPublicadoFalseOrderByIdAsc();
        for (OutboxEvent evento : pendientes) {
            try {
                kafkaTemplate.send(evento.getTopico(), evento.getClave(), evento.getPayload())
                        .get(5, TimeUnit.SECONDS);
                evento.marcarPublicado();
                log.info("Evento {} publicado en {}", evento.getId(), evento.getTopico());
            } catch (Exception e) {
                log.warn("No se pudo publicar el evento {} (se reintentará): {}", evento.getId(), e.getMessage());
                return; // conserva el orden: no seguimos con los siguientes
            }
        }
    }
}

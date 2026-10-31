package com.rica.publicacionesservice.publicaciones;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Component
public class InvestigadorEnEliminacionListener {

    private static final Logger log = LoggerFactory.getLogger(InvestigadorEnEliminacionListener.class);

    private final PublicacionRepository publicacionRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public InvestigadorEnEliminacionListener(PublicacionRepository publicacionRepository,
                                             KafkaTemplate<String, String> kafkaTemplate,
                                             ObjectMapper objectMapper) {
        this.publicacionRepository = publicacionRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "investigadores.en-eliminacion")
    public void alIniciarseEliminacion(String mensaje) {
        String correo = objectMapper.readTree(mensaje).get("correoInstitucional").asString();

        List<Publicacion> publicaciones = publicacionRepository.findByInvestigadorCorreo(correo);
        publicaciones.forEach(Publicacion::marcarComoHuerfana);
        publicacionRepository.saveAll(publicaciones);
        log.info("Saga: {} publicaciones de {} marcadas como huérfanas", publicaciones.size(), correo);

        String confirmacion = objectMapper.writeValueAsString(
                Map.of("correoInstitucional", correo, "cantidad", publicaciones.size()));
        kafkaTemplate.send("publicaciones.marcadas-huerfanas", correo, confirmacion);
    }
}

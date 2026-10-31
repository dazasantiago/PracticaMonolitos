package com.rica.investigadoresservice.investigadores.infraestructura.salida.mensajeria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String topico;

    @Column(nullable = false)
    private String clave;

    @Column(nullable = false, length = 4000)
    private String payload;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(nullable = false)
    private boolean publicado;

    protected OutboxEvent() {
    }

    public OutboxEvent(String topico, String clave, String payload) {
        this.topico = topico;
        this.clave = clave;
        this.payload = payload;
        this.creadoEn = Instant.now();
        this.publicado = false;
    }

    public Long getId() { return id; }
    public String getTopico() { return topico; }
    public String getClave() { return clave; }
    public String getPayload() { return payload; }
    public Instant getCreadoEn() { return creadoEn; }
    public boolean isPublicado() { return publicado; }

    public void marcarPublicado() {
        this.publicado = true;
    }
}

CREATE TABLE outbox_event (
    id         BIGSERIAL PRIMARY KEY,
    topico     VARCHAR(100)  NOT NULL,
    clave      VARCHAR(200)  NOT NULL,
    payload    VARCHAR(4000) NOT NULL,
    creado_en  TIMESTAMPTZ   NOT NULL,
    publicado  BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_outbox_pendientes ON outbox_event (publicado, id);

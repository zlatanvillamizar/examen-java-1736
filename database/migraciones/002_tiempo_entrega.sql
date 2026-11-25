CREATE TABLE IF NOT EXISTS tiempo_entrega (
    servicio_id INT PRIMARY KEY,
    duracion_estimada_segundos BIGINT NOT NULL,
    fecha_entrega_estimada DATETIME NOT NULL,
    CONSTRAINT fk_tiempo_entrega_servicio
        FOREIGN KEY (servicio_id) REFERENCES servicios(id),
    CONSTRAINT chk_tiempo_entrega_duracion CHECK (duracion_estimada_segundos >= 0)
) ENGINE=InnoDB;

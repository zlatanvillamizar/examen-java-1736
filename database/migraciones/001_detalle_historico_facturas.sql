-- Aplicar una vez sobre un esquema anterior, con la aplicación detenida.
-- Las facturas existentes conservan sus importes y quedan sin desglose histórico.
ALTER TABLE facturas
    ADD COLUMN cliente_nombre VARCHAR(255),
    ADD COLUMN cliente_documento VARCHAR(20),
    ADD COLUMN vehiculo_descripcion VARCHAR(125),
    ADD COLUMN detalle_historico BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE factura_detalles (
    factura_id INT NOT NULL,
    repuesto_id INT NOT NULL,
    nombre_repuesto VARCHAR(255),
    cantidad INT NOT NULL,
    precio_unitario DECIMAL(10,2) NOT NULL,
    PRIMARY KEY (factura_id, repuesto_id),
    CONSTRAINT fk_factura_detalles_factura FOREIGN KEY (factura_id) REFERENCES facturas(id),
    CONSTRAINT chk_factura_detalles_cantidad CHECK (cantidad > 0),
    CONSTRAINT chk_factura_detalles_precio CHECK (precio_unitario >= 0)
) ENGINE=InnoDB;

-- CarMotors: bloque de servicios.
-- Ejecutar en el esquema donde ya existen vehiculos y repuestos.
-- MySQL 8.0.16 o posterior para aplicar las restricciones CHECK.
-- Decisiones de implementación: docs/servicios/contratos-pendientes.md.

CREATE TABLE servicio_estados (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE
) ENGINE=InnoDB;

CREATE TABLE mantenimiento_tipos (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE
) ENGINE=InnoDB;

CREATE TABLE tecnicos (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    especialidad VARCHAR(100),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_tecnicos_activo CHECK (activo IN (0, 1))
) ENGINE=InnoDB;

CREATE TABLE servicios (
    id INT AUTO_INCREMENT PRIMARY KEY,
    vehiculo_id INT NOT NULL,
    mantenimiento_tipo_id INT NOT NULL,
    fecha_recepcion DATETIME NOT NULL,
    fecha_entrega DATETIME NULL,
    descripcion_problema TEXT NOT NULL,
    costo_mano_obra DECIMAL(10,2) NULL,
    estado_id INT NOT NULL,
    CONSTRAINT fk_servicios_vehiculo
        FOREIGN KEY (vehiculo_id) REFERENCES vehiculos(id),
    CONSTRAINT fk_servicios_mantenimiento_tipo
        FOREIGN KEY (mantenimiento_tipo_id) REFERENCES mantenimiento_tipos(id),
    CONSTRAINT fk_servicios_estado
        FOREIGN KEY (estado_id) REFERENCES servicio_estados(id),
    CONSTRAINT chk_servicios_fecha_entrega
        CHECK (fecha_entrega IS NULL OR fecha_entrega >= fecha_recepcion),
    CONSTRAINT chk_servicios_costo_mano_obra CHECK (costo_mano_obra >= 0)
) ENGINE=InnoDB;

CREATE TABLE servicios_repuestos (
    servicio_id INT,
    repuesto_id INT,
    cantidad_reservada INT NOT NULL DEFAULT 0,
    cantidad_usada INT NOT NULL DEFAULT 0,
    precio_unitario_venta DECIMAL(10,2) NOT NULL,
    PRIMARY KEY (servicio_id, repuesto_id),
    CONSTRAINT fk_servicios_repuestos_servicio
        FOREIGN KEY (servicio_id) REFERENCES servicios(id),
    CONSTRAINT fk_servicios_repuestos_repuesto
        FOREIGN KEY (repuesto_id) REFERENCES repuestos(id),
    CONSTRAINT chk_servicios_repuestos_reservada CHECK (cantidad_reservada >= 0),
    CONSTRAINT chk_servicios_repuestos_usada CHECK (cantidad_usada >= 0),
    CONSTRAINT chk_servicios_repuestos_precio CHECK (precio_unitario_venta >= 0)
) ENGINE=InnoDB;

CREATE TABLE servicios_tecnicos (
    servicio_id INT,
    tecnico_id INT,
    fecha_asignacion DATETIME NOT NULL,
    PRIMARY KEY (servicio_id, tecnico_id),
    CONSTRAINT fk_servicios_tecnicos_servicio
        FOREIGN KEY (servicio_id) REFERENCES servicios(id),
    CONSTRAINT fk_servicios_tecnicos_tecnico
        FOREIGN KEY (tecnico_id) REFERENCES tecnicos(id)
) ENGINE=InnoDB;

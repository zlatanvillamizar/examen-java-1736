-- MySQL 8.0.16+. Seleccionar una base vacía antes de ejecutar.

-- Bloque: Clientes (Marilud) | Depende de: vehiculos (Crisbely)

CREATE TABLE clientes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    documento_identidad VARCHAR(20) NOT NULL UNIQUE,
    telefono VARCHAR(20),
    email VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE vehiculos (
    id INT AUTO_INCREMENT PRIMARY KEY,
    cliente_id INT NOT NULL,
    placa VARCHAR(10) NOT NULL UNIQUE,
    marca VARCHAR(50) NOT NULL,
    modelo VARCHAR(50) NOT NULL,
    anio SMALLINT NOT NULL,

    CONSTRAINT fk_vehiculos_clientes
    FOREIGN KEY (cliente_id)
    REFERENCES clientes(id)
);

-- Bloque: Inventario y proveedores (Valeria) | Depende de: ninguno
-- Tablas: repuesto_tipos, proveedores, repuestos, lotes,
--         proveedores_repuestos, evaluaciones_proveedor
-- Nota: repuestos es referenciada por servicios_repuestos (bloque Servicios),
--       por eso este bloque debe ejecutarse antes que servicios.sql.

-- ---------------------------------------------------------------------
-- 1. Catálogo de tipos de repuesto
-- ---------------------------------------------------------------------
CREATE TABLE repuesto_tipos (
    id      INT          NOT NULL AUTO_INCREMENT,
    nombre  VARCHAR(100) NOT NULL,
    CONSTRAINT pk_repuesto_tipos PRIMARY KEY (id),
    CONSTRAINT uq_repuesto_tipos_nombre UNIQUE (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 2. Proveedores
-- ---------------------------------------------------------------------
CREATE TABLE proveedores (
    id                INT          NOT NULL AUTO_INCREMENT,
    nombre            VARCHAR(255) NOT NULL,
    persona_contacto  VARCHAR(120) NULL,
    telefono          VARCHAR(20)  NULL,
    email             VARCHAR(100) NULL,
    CONSTRAINT pk_proveedores PRIMARY KEY (id),
    CONSTRAINT uq_proveedores_nombre UNIQUE (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 3. Repuestos (tabla principal)
-- ---------------------------------------------------------------------
CREATE TABLE repuestos (
    id                INT           NOT NULL AUTO_INCREMENT,
    nombre            VARCHAR(255)  NOT NULL,
    marca             VARCHAR(100)  NOT NULL,
    repuesto_tipo_id  INT           NOT NULL,
    precio_venta      DECIMAL(10,2) NOT NULL,
    cantidad_stock    INT           NOT NULL DEFAULT 0,
    stock_minimo      INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_repuestos PRIMARY KEY (id),
    CONSTRAINT fk_repuestos_tipo
        FOREIGN KEY (repuesto_tipo_id) REFERENCES repuesto_tipos (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_repuestos_precio  CHECK (precio_venta >= 0),
    CONSTRAINT chk_repuestos_stock   CHECK (cantidad_stock >= 0),
    CONSTRAINT chk_repuestos_minimo  CHECK (stock_minimo >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 4. Lotes (trazabilidad: ingreso y caducidad)
-- ---------------------------------------------------------------------
CREATE TABLE lotes (
    id                   INT         NOT NULL AUTO_INCREMENT,
    repuesto_id          INT         NOT NULL,
    proveedor_id         INT         NOT NULL,
    codigo_lote          VARCHAR(50) NOT NULL,
    fecha_ingreso        DATE        NOT NULL,
    fecha_caducidad      DATE        NULL,
    cantidad_inicial     INT         NOT NULL,
    cantidad_disponible  INT         NOT NULL,
    CONSTRAINT pk_lotes PRIMARY KEY (id),
    CONSTRAINT uq_lotes_codigo UNIQUE (codigo_lote),
    CONSTRAINT fk_lotes_repuesto
        FOREIGN KEY (repuesto_id) REFERENCES repuestos (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_lotes_proveedor
        FOREIGN KEY (proveedor_id) REFERENCES proveedores (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_lotes_cant_inicial CHECK (cantidad_inicial > 0),
    CONSTRAINT chk_lotes_cant_disp
        CHECK (cantidad_disponible >= 0 AND cantidad_disponible <= cantidad_inicial),
    CONSTRAINT chk_lotes_fechas
        CHECK (fecha_caducidad IS NULL OR fecha_caducidad >= fecha_ingreso)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 5. Catálogo proveedor ↔ repuesto (tabla intermedia N:M)
-- ---------------------------------------------------------------------
CREATE TABLE proveedores_repuestos (
    proveedor_id         INT           NOT NULL,
    repuesto_id          INT           NOT NULL,
    costo                DECIMAL(10,2) NOT NULL,
    tiempo_entrega_dias  INT           NOT NULL,
    CONSTRAINT pk_proveedores_repuestos PRIMARY KEY (proveedor_id, repuesto_id),
    CONSTRAINT fk_pr_proveedor
        FOREIGN KEY (proveedor_id) REFERENCES proveedores (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_pr_repuesto
        FOREIGN KEY (repuesto_id) REFERENCES repuestos (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT chk_pr_costo   CHECK (costo >= 0),
    CONSTRAINT chk_pr_entrega CHECK (tiempo_entrega_dias >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 6. Evaluaciones de proveedor (calidad y puntualidad, escala 1–5)
-- ---------------------------------------------------------------------
CREATE TABLE evaluaciones_proveedor (
    id                INT      NOT NULL AUTO_INCREMENT,
    proveedor_id      INT      NOT NULL,
    fecha_evaluacion  DATE     NOT NULL,
    calidad           TINYINT  NOT NULL,
    puntualidad       TINYINT  NOT NULL,
    comentario        TEXT     NULL,
    CONSTRAINT pk_evaluaciones_proveedor PRIMARY KEY (id),
    CONSTRAINT fk_eval_proveedor
        FOREIGN KEY (proveedor_id) REFERENCES proveedores (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT chk_eval_calidad     CHECK (calidad BETWEEN 1 AND 5),
    CONSTRAINT chk_eval_puntualidad CHECK (puntualidad BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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

CREATE TABLE recordatorios_mantenimiento (
    id INT AUTO_INCREMENT PRIMARY KEY,
    vehiculo_id INT NOT NULL,
    servicio_origen_id INT NULL,
    fecha_proximo_mantenimiento DATE NOT NULL,
    descripcion VARCHAR(255),
    notificado BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_recordatorio_vehiculo
        FOREIGN KEY (vehiculo_id) REFERENCES vehiculos(id)
);


ALTER TABLE recordatorios_mantenimiento
ADD CONSTRAINT fk_recordatorio_servicio
    FOREIGN KEY (servicio_origen_id) REFERENCES servicios(id);

-- Facturación según el DER. La relación servicio-factura es 1:1.
CREATE TABLE facturas (
    id INT AUTO_INCREMENT PRIMARY KEY,
    servicio_id INT NOT NULL,
    fecha_emision DATETIME NOT NULL,
    total_repuestos DECIMAL(10,2) NOT NULL,
    total_mano_obra DECIMAL(10,2) NOT NULL,
    impuestos DECIMAL(10,2) NOT NULL,
    total_final DECIMAL(10,2) NOT NULL,
    ruta_archivo VARCHAR(255),
    cliente_nombre VARCHAR(255),
    cliente_documento VARCHAR(20),
    vehiculo_descripcion VARCHAR(125),
    detalle_historico BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_facturas_servicio UNIQUE (servicio_id),
    CONSTRAINT fk_facturas_servicio FOREIGN KEY (servicio_id) REFERENCES servicios(id)
) ENGINE=InnoDB;

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

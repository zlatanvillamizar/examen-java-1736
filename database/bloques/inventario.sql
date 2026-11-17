use carmotors;
-- Bloque: Inventario y proveedores (Valeria) | Depende de: ninguno
-- Tablas: repuesto_tipos, proveedores, repuestos, lotes,
--         proveedores_repuestos, evaluaciones_proveedor
-- Nota: repuestos es referenciada por servicios_repuestos (bloque Servicios),
--       por eso este bloque debe ejecutarse antes que servicios.sql.

-- ---------------------------------------------------------------------
-- 1. Catálogo de tipos de repuesto
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS repuesto_tipos (
    id      INT          NOT NULL AUTO_INCREMENT,
    nombre  VARCHAR(100) NOT NULL,
    CONSTRAINT pk_repuesto_tipos PRIMARY KEY (id),
    CONSTRAINT uq_repuesto_tipos_nombre UNIQUE (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 2. Proveedores
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS proveedores (
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
CREATE TABLE IF NOT EXISTS repuestos (
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
CREATE TABLE IF NOT EXISTS lotes (
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
CREATE TABLE IF NOT EXISTS proveedores_repuestos (
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
CREATE TABLE IF NOT EXISTS evaluaciones_proveedor (
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

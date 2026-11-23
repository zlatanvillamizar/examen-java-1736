-- Datos iniciales de los catálogos de Servicios: servicio_estados y mantenimiento_tipos.
-- Es un script de DATOS: no crea, altera ni borra tablas, y no modifica el esquema
-- aprobado (database/esquema/esquema.sql).
--
-- Ejecutar después del esquema, sobre la misma base:
--   mysql -u <usuario> -p <base> < database/datos/catalogos_servicios.sql
--
-- Se puede ejecutar varias veces: un nombre que ya existe no se duplica ni falla
-- (columnas nombre UNIQUE + ON DUPLICATE KEY UPDATE sin cambios).
--
-- Nombres que el código exige tal cual (sin distinguir mayúsculas):
--   "Completado" -> FacturaService solo factura órdenes en este estado.
--   "Cancelado"  -> ServicioService libera las reservas y bloquea la orden.
-- "Recibido", "En proceso", "Preventivo" y "Correctivo" son una propuesta; el equipo
-- puede agregar otros con un INSERT similar.
SET NAMES utf8mb4;

INSERT INTO servicio_estados (nombre) VALUES
    ('Recibido'),
    ('En proceso'),
    ('Completado'),
    ('Cancelado')
ON DUPLICATE KEY UPDATE nombre = nombre;

INSERT INTO mantenimiento_tipos (nombre) VALUES
    ('Preventivo'),
    ('Correctivo')
ON DUPLICATE KEY UPDATE nombre = nombre;

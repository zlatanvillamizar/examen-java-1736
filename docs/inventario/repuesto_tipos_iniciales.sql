-- Datos iniciales del catálogo repuesto_tipos (Inventario).
-- Es un script de DATOS: no crea, altera ni borra tablas, y no forma parte del
-- esquema aprobado (database/esquema/esquema.sql), que no se modifica.
--
-- Ejecutar después del esquema, sobre la misma base:
--   mysql -u <usuario> -p <base> < docs/inventario/repuesto_tipos_iniciales.sql
--
-- Se puede ejecutar varias veces: un nombre que ya existe no se duplica ni falla
-- (uq_repuesto_tipos_nombre + ON DUPLICATE KEY UPDATE sin cambios).
-- La lista es una propuesta inicial; el equipo puede agregar o cambiar nombres.
INSERT INTO repuesto_tipos (nombre) VALUES
    ('Filtros'),
    ('Frenos'),
    ('Lubricantes'),
    ('Eléctricos'),
    ('Suspensión'),
    ('Motor')
ON DUPLICATE KEY UPDATE nombre = nombre;

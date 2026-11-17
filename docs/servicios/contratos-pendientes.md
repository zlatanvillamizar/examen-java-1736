# Contratos pendientes — módulo Servicios (recuperación de tarea 14/11/2026)

Este documento se elaboró el 17/11/2026 para recuperar el trabajo planificado el sábado 14. **No es el DER aprobado**. Issues: #1, #16, #19.

## Tablas a cargo y dependencias

| Tabla | Dependencia confirmada por el reparto | Columnas y restricciones |
| --- | --- | --- |
| `servicio_estados` | Catálogo de estados | Pendiente de DER |
| `mantenimiento_tipos` | Catálogo de tipos | Pendiente de DER |
| `tecnicos` | Entidad independiente | Pendiente de DER |
| `servicios` | `vehiculos`; catálogos según DER | Pendiente de DER |
| `servicios_repuestos` | `servicios` y `repuestos` | Pendiente de DER |
| `servicios_tecnicos` | `servicios` y `tecnicos` | Pendiente de DER |

## Contratos externos conocidos

- `feature/clientes-database` y `main` declaran `clientes.id INT AUTO_INCREMENT PRIMARY KEY` en `database/bloques/clientes.sql`. El bloque también crea `recordatorios_mantenimiento` con una FK a `vehiculos(id)` y luego agrega otra a `servicios(id)`.
- `feature/vehiculos-database` (PR #12) declara `vehiculos.id INT AUTO_INCREMENT PRIMARY KEY` y `vehiculos.cliente_id INT NOT NULL` con FK a `clientes(id)`. El bloque de vehículos está vacío en `main` y `develop`.
- `feature/repuestos-database` y `main` declaran `repuestos.id INT NOT NULL AUTO_INCREMENT` como PK en `database/bloques/inventario.sql` (PR #18). Los identificadores de las tres tablas usan `INT` con signo. Los tres bloques de dependencias están vacíos en `develop` y en la rama de servicios revisada.

## Borrador SQL local

El borrador de las seis tablas quedó en el commit local `5e42265` de `backup-trabajo-local-17nov`. Sus FK a `vehiculos(id)` y `repuestos(id)` coinciden en tipo con los scripts revisados. El bloque de servicios de la rama de trabajo sigue vacío hasta cotejar el borrador con el DER.

| Tabla | Qué cotejar en el borrador |
| --- | --- |
| `servicio_estados` | ID, nombre único y valores permitidos. |
| `mantenimiento_tipos` | ID, nombre único, descripción y nulabilidad. |
| `tecnicos` | ID, nombre, especialidad y valor inicial de activo. |
| `servicios` | PK, FK a vehículo y catálogos, obligatoriedad, fechas, descripción y costo de mano de obra. |
| `servicios_repuestos` | PK compuesta, cardinalidad, tipos y límites de cantidades, precio unitario y comportamiento en reserva, consumo, cancelación y devolución. |
| `servicios_tecnicos` | PK compuesta, cardinalidad, fecha de asignación y precisión y límites de horas trabajadas. |

## Decisiones que debe aprobar el equipo

1. Columnas, tipos, índices, PK, FK, UNIQUE y nulabilidad de cada tabla.
2. Si `servicios.id` es `INT`, compatible con la FK propuesta por recordatorios.
3. Confirmación del identificador `repuestos.id INT` propuesto por Valeria y política de stock.
4. Significado exacto de `cantidad_reservada` y `cantidad_usada`: reserva, consumo, cancelación, devolución y concurrencia.
5. Estados de servicio permitidos y transiciones válidas.

## Orden de integración propuesto (pendiente de confirmación)

Crear primero solo la tabla `clientes` del bloque de clientes. Crear `vehiculos`, los catálogos de servicios y `tecnicos` antes de `servicios`. Ejecutar inventario antes de crear `servicios_repuestos` y crear `servicios_tecnicos` después de sus dos tablas destino. Crear `recordatorios_mantenimiento` y aplicar su `ALTER TABLE` cuando ya existan `vehiculos` y `servicios`.

## Alcance de la recuperación

Se puede desarrollar un contrato Java sin esquema físico, pero **no** crear DDL definitivo ni implementar consultas JDBC hasta que se apruebe el DER. No se han ejecutado pruebas MySQL de este documento.

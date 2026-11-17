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

- El PR #12 propone `vehiculos.id INT AUTO_INCREMENT PRIMARY KEY`, con referencia a `clientes(id)`. **El PR no constituye todavía un contrato integrado en develop**.
- **Actualización 17/11:** la rama `feature/repuestos-database` ya publica `database/bloques/inventario.sql`, con `repuestos.id INT NOT NULL AUTO_INCREMENT` como PK. Este contrato es **propuesto y todavía no integrado a `develop`**; verificar contra el DER aprobado antes de crear la FK desde servicios.
- La tabla `recordatorios_mantenimiento` de clientes contiene una FK a `servicios(id)`; el orden de integración debe considerarla.

## Decisiones que debe aprobar el equipo

1. Columnas, tipos, índices, PK, FK, UNIQUE y nulabilidad de cada tabla.
2. Si `servicios.id` es `INT`, compatible con la FK propuesta por recordatorios.
3. Confirmación del identificador `repuestos.id INT` propuesto por Valeria y política de stock.
4. Significado exacto de `cantidad_reservada` y `cantidad_usada`: reserva, consumo, cancelación, devolución y concurrencia.
5. Estados de servicio permitidos y transiciones válidas.

## Orden de integración propuesto (pendiente de confirmación)

`clientes` → `vehiculos` → catálogos/técnicos → `servicios` → `servicios_tecnicos`; integrar `repuestos` antes de `servicios_repuestos` y agregar las FK de `recordatorios_mantenimiento` cuando existan sus destinos.

## Alcance de la recuperación

Se puede desarrollar un contrato Java sin esquema físico, pero **no** crear DDL definitivo ni implementar consultas JDBC hasta que se apruebe el DER. No se han ejecutado pruebas MySQL de este documento.

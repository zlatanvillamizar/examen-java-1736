# Contratos pendientes del módulo de servicios

El issue #16 y las instrucciones de `main` establecen el alcance. Las interfaces Java expresan los casos de uso y el puerto de lectura. La consulta por ID devuelve un `Optional` y el listado devuelve una copia no modificable. Son decisiones del contrato Java. Los tipos concretos, el adaptador JDBC y la implementación de escritura quedan pendientes.

## Matriz del esquema

| Tabla | Campos confirmados | Tipo | PK | FK | NOT NULL | UNIQUE | Origen |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `servicio_estados` | PENDIENTE DE DER | PENDIENTE DE DER | PENDIENTE DE DER | PENDIENTE DE DER | PENDIENTE DE DER | PENDIENTE DE DER | Issue #16 |
| `mantenimiento_tipos` | PENDIENTE DE DER | PENDIENTE DE DER | PENDIENTE DE DER | PENDIENTE DE DER | PENDIENTE DE DER | PENDIENTE DE DER | Issue #16 |
| `tecnicos` | PENDIENTE DE DER | PENDIENTE DE DER | PENDIENTE DE DER | PENDIENTE DE DER | PENDIENTE DE DER | PENDIENTE DE DER | Issue #16 |
| `servicios` | PENDIENTE DE DER | PENDIENTE DE DER | PENDIENTE DE DER | Destino `vehiculos`. Columnas y demás relaciones PENDIENTE DE DER | PENDIENTE DE DER | PENDIENTE DE DER | AGENTS.md, issues #1 y #16 |
| `servicios_repuestos` | `cantidad_reservada`, `cantidad_usada`. Demás campos PENDIENTE DE DER | PENDIENTE DE DER | PENDIENTE DE DER | Destinos `servicios` y `repuestos`. Columnas PENDIENTE DE DER | PENDIENTE DE DER | PENDIENTE DE DER | AGENTS.md, issues #1 y #16 |
| `servicios_tecnicos` | PENDIENTE DE DER | PENDIENTE DE DER | PENDIENTE DE DER | Destinos `servicios` y `tecnicos`. Columnas PENDIENTE DE DER | PENDIENTE DE DER | PENDIENTE DE DER | AGENTS.md, issue #16 |

Confirmar los valores de los catálogos, la pertenencia de cada catálogo a una orden y su obligatoriedad, el estado inicial, las transiciones permitidas, la cardinalidad de las asignaciones y el tratamiento de duplicados.

## Orden de dependencias

1. Integrar `clientes` antes de `vehiculos`. El PR #12 propone `vehiculos.id INT` como PK y una FK a `clientes(id)`. Verificar el contrato tras su integración en `develop`.
2. Crear los catálogos y `tecnicos` antes de las tablas que los referencien. Crear `servicios` después de `vehiculos`.
3. Crear `servicios_repuestos` después de `servicios` y `repuestos`. Confirmar nombre y tipo exacto de la PK de `repuestos` con inventario, incluido el signo si el tipo es numérico.
4. Crear `servicios_tecnicos` después de `servicios` y `tecnicos`. Coordinar `recordatorios_mantenimiento` después de las tablas referenciadas, conforme al issue #15.

## Escenarios propuestos, pendientes de aprobación

| Escenario | Decisión pendiente |
| --- | --- |
| Reservar con stock disponible o insuficiente | Qué representa `cantidad_reservada`, cuándo cambia y si se descuenta disponibilidad, stock físico o una reserva por lote. |
| Consumir en una o varias operaciones | Si `cantidad_usada` es acumulada, si el consumo puede superar la reserva y cómo cambia la reserva restante. |
| Cancelar antes o después de consumir | Qué cantidades se liberan o se conservan como historial y cómo se refleja en stock. |
| Devolver repuestos | Cuándo se permite, qué cantidades se revierten y dónde reingresa el stock. |
| Reservas concurrentes o reintentos | Qué actualizaciones de cantidades y stock son atómicas y cómo identificar una operación repetida. |

## Verificación

Ejecutar `mvn clean compile` y `mvn test`. Las pruebas unitarias usan un puerto de lectura en memoria. Como siguiente paso de CI, se propone una validación de Maven en GitHub Actions con JDK 17.

Cuando se integre el DDL, aplicar los bloques en el orden anterior en un esquema MySQL desechable con las FK habilitadas. Comprobar cada relación insertando una referencia válida y una inexistente. Probar nulabilidad, unicidad y cantidades con los límites que establezca el DER y con escenarios aprobados de consumo, cancelación y devolución.

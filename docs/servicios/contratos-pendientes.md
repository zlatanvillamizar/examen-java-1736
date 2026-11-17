# Contratos del módulo de servicios

El DER compartido para esta revisión confirma las columnas, los tipos, las claves y las relaciones de la siguiente tabla. Las decisiones de implementación se describen más abajo.

| Tabla | Columnas y tipos confirmados | PK y FK confirmadas |
| --- | --- | --- |
| `servicio_estados` | `id INT`, `nombre VARCHAR(100)` | PK `id` |
| `mantenimiento_tipos` | `id INT`, `nombre VARCHAR(100)` | PK `id` |
| `tecnicos` | `id INT`, `nombre VARCHAR(255)`, `especialidad VARCHAR(100)`, `activo BOOLEAN` | PK `id` |
| `servicios` | `id INT`, `vehiculo_id INT`, `mantenimiento_tipo_id INT`, `fecha_recepcion DATETIME`, `fecha_entrega DATETIME`, `descripcion_problema TEXT`, `costo_mano_obra DECIMAL(10,2)`, `estado_id INT` | PK `id`. FK `vehiculo_id → vehiculos.id`, `mantenimiento_tipo_id → mantenimiento_tipos.id`, `estado_id → servicio_estados.id`. |
| `servicios_repuestos` | `servicio_id INT`, `repuesto_id INT`, `cantidad_reservada INT`, `cantidad_usada INT`, `precio_unitario_venta DECIMAL(10,2)` | PK `(servicio_id, repuesto_id)`. FK `servicio_id → servicios.id`, `repuesto_id → repuestos.id`. |
| `servicios_tecnicos` | `servicio_id INT`, `tecnico_id INT`, `fecha_asignacion DATETIME` | PK `(servicio_id, tecnico_id)`. FK `servicio_id → servicios.id`, `tecnico_id → tecnicos.id`. |

Cada FK representa una relación 1:N desde la tabla referenciada hacia la tabla que contiene la FK.

## Decisiones de implementación aprobadas

El DER no especifica explícitamente `AUTO_INCREMENT`, `NOT NULL`, `NULL`, `UNIQUE`, `DEFAULT` ni `CHECK`.

| Decisión | Justificación |
| --- | --- |
| `AUTO_INCREMENT` en los cuatro identificadores simples. | Sigue la convención de clientes, vehículos e inventario y permite que MySQL genere sus identificadores. Las tablas puente usan las claves de las filas relacionadas. |
| Nombres de catálogo con `NOT NULL UNIQUE`. Nombre del técnico y las tres FK de `servicios` con `NOT NULL`. | Los catálogos requieren nombres únicos. Cada técnico tiene un nombre y cada orden tiene un vehículo, un tipo de mantenimiento y un estado. |
| `NOT NULL` implícito en las PK. | MySQL lo aplica a las PK simples y a ambas columnas de cada PK compuesta. La unicidad de las PK proviene de las claves confirmadas. |
| `tecnicos.activo BOOLEAN NOT NULL DEFAULT TRUE` y `CHECK (activo IN (0, 1))`. | Todo técnico tiene un estado booleano y las altas quedan activas por defecto. |
| `fecha_recepcion` y `fecha_asignacion` con `NOT NULL` y sin `DEFAULT`. | Java registra las fechas efectivas de ambos eventos. |
| `fecha_entrega` admite `NULL`. Si se informa, debe ser igual o posterior a `fecha_recepcion`. | Una orden abierta puede seguir pendiente de entrega. |
| `descripcion_problema NOT NULL`. `costo_mano_obra` admite `NULL` y se valida con `CHECK (costo_mano_obra >= 0)`. | Se registra el motivo de ingreso. Un costo pendiente se representa con `NULL` y un costo confirmado de cero se registra como `0.00`. |
| Ambas cantidades con `NOT NULL DEFAULT 0` y un `CHECK >= 0` por columna. Precio unitario con `NOT NULL`, sin `DEFAULT` y con `CHECK >= 0`. | Una línea puede empezar sin reserva ni consumo. Cada línea registra explícitamente su precio y los valores negativos se rechazan. |
| `ENGINE=InnoDB` en las seis tablas. | Hace explícito el motor que aplicará las FK y sigue la convención de inventario. Las FK usan las acciones referenciales predeterminadas de MySQL, que impiden borrar o cambiar una clave referenciada mientras haya filas dependientes. |

Los `CHECK`, incluido el de `activo`, requieren MySQL 8.0.16 o posterior. Inventario ya usa `CHECK` y el repositorio no fija la versión del servidor. Confirmar la versión antes de ejecutar el bloque.

## Responsabilidades de Java

- Quitar espacios innecesarios de los nombres e impedir descripciones vacías.
- Validar las transiciones de estado y exigir `fecha_entrega` cuando la orden se entregue.
- Validar la disponibilidad y coordinar la reserva, el consumo y la devolución de inventario mediante transacciones.
- Determinar cuándo debe informarse `costo_mano_obra`.
- Proporcionar explícitamente las fechas efectivas de `fecha_recepcion` y `fecha_asignacion`.

Queda pendiente definir si `cantidad_reservada` es un saldo o un total acumulado. Hasta confirmarlo, cada cantidad se valida por separado. También quedan por definir el catálogo de estados, sus transiciones y las reglas de inventario para cancelación y concurrencia.

## Alcance de la primera versión Java

`ServicioService` valida los registros, las fechas y la asignación de técnicos activos. El cambio de estado comprueba que el identificador sea positivo y delega la existencia del estado a la FK. Queda pendiente definir las transiciones permitidas y cuáles requieren registrar la entrega. La PK compuesta rechaza asignaciones duplicadas. El contrato de lectura se reutiliza para buscar y listar órdenes. El contrato de escritura existente también incluye repuestos, cuya implementación queda para la fase de inventario.

Para registrar órdenes, MySQL requiere un vehículo existente y filas en los catálogos de estados y tipos de mantenimiento. El Java de esta fase no usa Inventario. Crear el bloque SQL completo sí requiere la tabla `repuestos`.

## Diferencias con el borrador `5e42265`

- `servicio_estados.nombre` pasa de `VARCHAR(30)` a `VARCHAR(100)`.
- `mantenimiento_tipos.descripcion` se omite y `tecnicos.nombre` pasa de `VARCHAR(100)` a `VARCHAR(255)`.
- `servicios.descripcion_problema` pasa de `VARCHAR(500)` a `TEXT` y `servicios_tecnicos.horas_trabajadas` se omite.
- Se conservan `UNIQUE` en ambos catálogos, el valor inicial de activo y los ceros iniciales de las cantidades. Se eliminan los valores por defecto de las fechas de recepción y asignación y del costo de mano de obra. El costo admite `NULL`.
- Se agregan `CHECK` para activo, cronología de entrega, costo, cantidades y precio.
- Las tablas puente conservan la PK compuesta y su nulabilidad implícita. Se indica InnoDB en las seis tablas.

## Orden de creación y dependencias externas

1. Crear `clientes` antes de `vehiculos`. El script de vehículos del PR #12 declara `vehiculos.id INT` como PK. Crear `repuestos` mediante el bloque de inventario, que declara `repuestos.id INT` como PK. Ambas PK externas usan `INT` con signo. Verificar que ambas tablas existan en el esquema seleccionado antes de ejecutar el bloque completo de servicios.
2. Crear `servicio_estados`, `mantenimiento_tipos` y `tecnicos` en cualquier orden.
3. Crear `servicios` cuando existan `vehiculos` y sus dos catálogos.
4. Crear `servicios_repuestos` cuando existan `servicios` y `repuestos`. Crear `servicios_tecnicos` cuando existan `servicios` y `tecnicos`.
5. Crear `recordatorios_mantenimiento` y aplicar su `ALTER TABLE` después de crear `vehiculos` y `servicios`. El bloque de clientes contiene tanto la creación de `clientes` como la de recordatorios, por lo que hay que coordinar esos pasos.

El DDL incorpora las decisiones aprobadas y está pendiente de validación en MySQL.

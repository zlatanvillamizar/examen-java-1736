# Contrato Inventario ↔ Servicios — decisiones e implementación

**Responsable de Inventario:** Valeria · **Responsable de Servicios:** Zlatan
**Estado (19/11/2026, issue #53):** reglas de reserva, consumo, liberación y transacción **aprobadas** por el responsable de Servicios e **implementadas** en `InventarioServiciosJdbc` (sección 0). Lotes (D4) y devoluciones (D8) siguen pendientes.
**Relacionado:** issues #16 y #32, PR #44, [`docs/servicios/integracion-inventario.md`](../servicios/integracion-inventario.md) y [`docs/servicios/contratos-pendientes.md`](../servicios/contratos-pendientes.md).

Este documento describe qué ofrece el módulo de Inventario al módulo de Servicios, las reglas acordadas y lo que sigue pendiente.

## 0. Decisiones aprobadas e implementación (19/11/2026)

### Reglas aprobadas por Servicios (Zlatan, issue #53)

1. La reserva reduce el stock disponible para que otros servicios no usen unidades comprometidas, sin descontar dos veces el stock físico. → **D2 = opción A.**
2. Inventario gestiona los movimientos y valida las cantidades; Servicios inicia y coordina la transacción de negocio. → **D6.**
3. Las operaciones relacionadas comparten la misma conexión JDBC y son atómicas: si algo falla, `rollback` completo. → **D6, D7.**
4. Consumir no vuelve a descontar lo reservado; liberar devuelve solo lo reservado que aún no se consumió.

### Decisiones aprobadas por el revisor técnico (cierre del 19/11/2026)

| Decisión | Regla aprobada | Cómo se cumple |
|---|---|---|
| **D1** — cantidad reservada | `servicios_repuestos.cantidad_reservada` es **exclusivamente la cantidad pendiente de consumir**. Ejemplo: reservar 5 → reservada 5; consumir 2 → reservada 3, usada 2; liberar el resto → reservada 0, usada 2. El consumo no vuelve a descontar el stock. | `consumir` mueve unidades de `cantidad_reservada` a `cantidad_usada` sin tocar `repuestos`; `liberar` resta del saldo y lo devuelve al stock. Prueba: `unConsumoParcialPasaUnidadesAUsadasSinVolverADescontarStock`, `liberarDevuelveSoloLoNoConsumidoYNoAlteraLoUsado`. |
| **D3** — reservas parciales | No hay reservas parciales automáticas: sin stock suficiente, la operación completa falla y se revierte. | `UPDATE … WHERE cantidad_stock >= n` y `StockInsuficienteException`. Prueba: `conStockInsuficienteSeRechazaLaReservaCompletaSinCambios`. |
| **D5** — precio | El precio unitario se fija al crear la línea; cambios posteriores del inventario no lo alteran. | `INSERT … SELECT precio_venta` solo al crear; `ON DUPLICATE KEY` suma cantidad sin tocar el precio. Prueba: `reservasSucesivasSumanAlSaldoYConservanElPrecioDeLaLinea`. |
| **D8** — cancelación | Cancelar libera solo lo pendiente; nunca devuelve unidades ya usadas. | `liberarTodo(servicioId)`. Prueba: `cancelarUnServicioLiberaTodoSuSaldoSinTocarLoUsadoNiOtrosServicios`. |
| **D4** — lotes y devoluciones | Fuera del alcance de esta entrega; sin tablas ni funciones nuevas. | No implementado a propósito. |
| **Transacciones** | Servicios coordina; Inventario recibe la misma conexión JDBC y no hace `commit` ni `rollback` cuando participa en una transacción compartida. | El adaptador nunca confirma, deshace ni cierra la conexión. Prueba: `unErrorJdbcLlegaComoIllegalStateExceptionConLaCausaYSinConfirmarNada`. |

Compatibilidad verificada con el esquema de `develop` (`database/bloques/inventario.sql` y `servicios.sql`): no requiere columnas nuevas ni cambios de SQL. El esquema consolidado (`database/esquema/esquema.sql`, PR #70) conserva `repuestos`, `proveedores`, `repuesto_tipos` y `servicios_repuestos` con las mismas columnas y claves; lo verifica `AceptacionInventarioProveedoresTest`.

### Implementación

| Pieza | Responsabilidad |
|---|---|
| `servicios.inventario.InventarioServiciosJdbc` | Implementa `InventarioServiciosPort` (**sin cambiar la interfaz**). Recibe en el constructor la `Connection` de la transacción de Servicios; no confirma, no deshace y no la cierra. Exige `autoCommit` desactivado. Añade `liberarTodo(servicioId)` para cancelaciones y `ejecutarEnTransaccion(...)`, que confirma o hace `rollback` completo. |
| `dao.ReservaRepuestoDAO` | SQL de `servicios_repuestos` con `UPDATE` condicionados (`cantidad_reservada >= ?`). |
| `dao.RepuestoDAO` | `restarStock`, `sumarStock` y `bloquearParaActualizar` sobre una transacción abierta; las entradas y salidas existentes los reutilizan. |

| Operación | `repuestos.cantidad_stock` | `servicios_repuestos` |
|---|---|---|
| `reservar(s, r, n)` | `− n` si hay stock (`UPDATE … WHERE cantidad_stock >= n`); si no, `StockInsuficienteException` | crea la línea `(s, r, n, 0, precio_venta)` o `cantidad_reservada + n` (conserva el precio) |
| `consumir(s, r, n)` | sin cambios | `cantidad_reservada − n`, `cantidad_usada + n`, solo si `cantidad_reservada >= n` |
| `liberar(s, r, n)` | `+ n` | `cantidad_reservada − n`, solo si `cantidad_reservada >= n`; `cantidad_usada` no cambia |
| `liberarTodo(s)` (cancelación) | `+` saldo pendiente de cada repuesto | `cantidad_reservada = 0`; `cantidad_usada` no cambia |

Bloqueos en orden fijo (primero `repuestos`, luego `servicios_repuestos`) en **las cuatro operaciones**, también en `consumir` (corrección del cierre: sin ese bloqueo, una transacción que consume y luego reserva se interbloqueaba con reservas simultáneas; lo detecta `transaccionesQueConsumenYReservanNoSeInterbloqueanConReservasSimultaneas`). Si una transacción de Servicios toca varios repuestos, conviene operarlos en orden ascendente de `repuesto_id`; un interbloqueo eventual lo resuelve MySQL abortando una transacción, que se puede reintentar completa. Antes de cancelar, Servicios debería bloquear la fila del servicio (`SELECT id FROM servicios WHERE id = ? FOR UPDATE`) para que no entren reservas nuevas mientras se libera.

`ejecutarEnTransaccion` y las entradas/salidas de `RepuestoDAO` usan `dao.Transaccion`: confirman o hacen `rollback` completo ante cualquier excepción (también `Error`), restauran `autoCommit`, cierran la conexión y **conservan la excepción original** aunque también fallen el `rollback` o la restauración (quedan como suprimidas). Errores: `StockInsuficienteException`, `RepuestoNoEncontradoException`, `NoSuchElementException` (sin reserva), `IllegalStateException` (supera el saldo pendiente, conexión en `autoCommit` o error JDBC con la `SQLException` como causa, porque el puerto no declara excepciones comprobadas). Ante **cualquier** excepción, quien coordina debe hacer `rollback`.

### Uso desde Servicios

```java
InventarioServiciosJdbc.ejecutarEnTransaccion(ConexionFactory::obtenerConexion, (conexion, inventario) -> {
    // ... cambios propios de Servicios con la MISMA conexion ...
    inventario.reservar(new ReservaRepuesto(servicioId, repuestoId, cantidad));
    return null;
}); // commit si todo sale bien; rollback completo ante cualquier excepción
```

Si Servicios ya tiene su propia transacción abierta: `new InventarioServiciosJdbc(conexion).reservar(...)` y Servicios confirma o deshace.

### Pendiente

- Conectar el adaptador a `ServicioService`/`ServicioControlador` (módulo de Servicios; no se modificó desde Inventario).
- D4: movimientos por lote y devolución de unidades ya consumidas (fuera de alcance).
- Esquema consolidado: llega con el PR #70 y ya se verificó con `AceptacionInventarioProveedoresTest` (ver `docs/inventario/README.md`, sección 7).

## 1. Datos involucrados

| Tabla | Columnas relevantes | Módulo dueño |
|---|---|---|
| `repuestos` | `id`, `precio_venta`, `cantidad_stock`, `stock_minimo` | Inventario |
| `lotes` | `repuesto_id`, `proveedor_id`, `fecha_ingreso`, `fecha_caducidad`, `cantidad_inicial`, `cantidad_disponible` | Inventario |
| `servicios_repuestos` | `servicio_id`, `repuesto_id` (PK compuesta), `cantidad_reservada`, `cantidad_usada`, `precio_unitario_venta` | Servicios |

Definiciones: `database/bloques/inventario.sql` y `database/bloques/servicios.sql`.

## 2. Reglas ya documentadas por Servicios

Tomadas de `docs/servicios/integracion-inventario.md`; se citan aquí sin cambios:

1. Los IDs de servicio y repuesto deben ser positivos.
2. Reservar, consumir o liberar cero unidades no es válido.
3. Inventario debe rechazar una reserva cuando no exista disponibilidad suficiente.
4. El consumo no puede superar la cantidad reservada pendiente de esa orden y repuesto.
5. La liberación no puede superar la cantidad reservada pendiente.
6. `cantidad_usada` representa consumo confirmado y no se reduce al liberar una reserva.
7. Servicios no calcula ni actualiza `cantidad_stock` ni `lotes.cantidad_disponible`.
8. El precio aplicado se conserva en `servicios_repuestos.precio_unitario_venta`.

Servicios definió el puerto `InventarioServiciosPort` (`reservar`, `consumir`, `liberar`) y los registros `ReservaRepuesto` y `ConsumoRepuesto`, que validan IDs y cantidades positivas. Su implementación corresponde a Inventario y **todavía no existe**.

## 3. Operaciones de Inventario disponibles hoy

Todas están en `InventarioService` y se exponen en `InventarioControlador` (PR #44):

| Necesidad de Servicios | Operación disponible | Notas |
|---|---|---|
| Comprobar que un repuesto existe | `buscarRepuesto(int id)` → `Optional<Repuesto>` | Rechaza IDs no positivos con `IllegalArgumentException` |
| Leer el precio vigente para guardarlo en `precio_unitario_venta` | `Repuesto.precioVenta()` | `DECIMAL(10,2)`, nunca negativo |
| Consultar si hay unidades suficientes | `hayStockSuficiente(int repuestoId, int cantidad)` | Hoy compara contra `cantidad_stock`. Su significado depende de la decisión D2 |
| Registrar entrada de unidades | `registrarEntrada(int repuestoId, int cantidad)` → stock resultante | Cantidad positiva; repuesto existente; sin desbordar `INT` |
| Registrar salida de unidades | `registrarSalida(int repuestoId, int cantidad)` → stock resultante | Nunca deja el stock negativo; si no alcanza lanza `StockInsuficienteException` sin cambiar nada. El `UPDATE` es atómico (`WHERE cantidad_stock >= ?`) |
| Repuestos por reponer | `listarRepuestosEnStockMinimo()` | `cantidad_stock <= stock_minimo` |

Errores que puede recibir quien llame: `IllegalArgumentException` (datos inválidos), `StockInsuficienteException` (salida mayor al stock), `RepuestoNoEncontradoException` (repuesto inexistente) y `SQLException` (errores de base de datos).

Limitaciones actuales:

- `registrarEntrada` y `registrarSalida` modifican solo `repuestos.cantidad_stock`; **no** modifican `lotes.cantidad_disponible` (ver D4).
- `registrarEntrada`/`registrarSalida` abren su propia transacción. Para operar dentro de la transacción de Servicios se usan `restarStock`/`sumarStock` o, mejor, `InventarioServiciosJdbc` (sección 0).

Atomicidad verificada (issue #53): `registrarEntrada` y `registrarSalida` hacen `rollback` ante cualquier error JDBC (incluido un fallo al confirmar), restauran el *autocommit* y no dejan cambios en la base; 20 salidas simultáneas sobre 10 unidades aceptan exactamente 10 y el stock nunca queda negativo. Pruebas: `RepuestoDAOTransaccionTest` (sin MySQL) y `RepuestoDAOTransaccionIntegracionTest` (MySQL en base desechable).

## 4. Estado de las operaciones

| Operación | Estado | Bloqueada por |
|---|---|---|
| Implementación de `InventarioServiciosPort.reservar` | **Implementada** (`InventarioServiciosJdbc`) | — |
| Implementación de `InventarioServiciosPort.consumir` | **Implementada** | — |
| Implementación de `InventarioServiciosPort.liberar` | **Implementada** (+ `liberarTodo` para cancelaciones) | — |
| Consulta de disponibilidad que considere reservas | **Resuelta por D2 = A:** `cantidad_stock` ya excluye lo reservado, así que `hayStockSuficiente` refleja lo disponible | — |
| Movimiento de unidades por lote | No implementado | D4 |
| Devolución de unidades ya consumidas | No implementada (fuera de alcance) | D4 |

## 5. Decisiones planteadas (contexto; lo aprobado está en la sección 0)

Cada punto incluye opciones para discutir; **ninguna está elegida**.

**D1. Significado de `cantidad_reservada`.**
- *Opción saldo:* guarda solo lo reservado que aún no se usó ni se liberó. Ejemplo: reservar 5 y usar 3 deja `cantidad_reservada = 2`, `cantidad_usada = 3`.
- *Opción acumulado:* guarda el total reservado; el saldo pendiente se calcula (`reservada − usada − liberada`). Requiere registrar lo liberado en algún lugar, y hoy no existe una columna para eso.

**D2. Momento en que una reserva afecta el stock.**
- *Opción A, descontar al reservar:* `reservar` baja `cantidad_stock`, `liberar` la sube y `consumir` no la cambia. `hayStockSuficiente` seguiría funcionando igual, pero `cantidad_stock` dejaría de representar las unidades físicas en bodega.
- *Opción B, descontar al consumir:* `cantidad_stock` sigue siendo el stock físico, y lo disponible se calcula como `cantidad_stock − Σ reservas pendientes`. Habría que cambiar o complementar `hayStockSuficiente` para que consulte `servicios_repuestos`.

**D3. Reserva con stock insuficiente.** La regla 3 indica rechazarla. Falta confirmar que no se admiten reservas parciales.

**D4. Relación con lotes.**
- ¿Se descuenta de un lote concreto al consumir o reservar? Si es así, ¿cuál: el que vence primero (`fecha_caducidad`) o el más antiguo (`fecha_ingreso`)?
- ¿Se excluyen los lotes vencidos?
- ¿Debe cumplirse siempre `cantidad_stock = Σ lotes.cantidad_disponible` de ese repuesto?

**D5. Precio aplicado.** ¿`precio_unitario_venta` se fija al reservar o al consumir? ¿Qué ocurre si `precio_venta` cambia entre ambos momentos?

**D6. Transacción.** Una reserva, un consumo o una liberación modifica `servicios_repuestos` (Servicios) y `repuestos`/`lotes` (Inventario), y ambos cambios deben confirmarse o deshacerse juntos. Hay que definir quién abre y confirma la transacción. Una posibilidad para discutir es que la implementación de Inventario reciba la `Connection` de la transacción que abre Servicios.

**D7. Concurrencia.** Cómo evitar que dos órdenes reserven las mismas unidades. Una posibilidad es repetir el patrón de `registrarSalida`, un `UPDATE … WHERE` con condición, sobre el dato que se decida en D2.

**D8. Cancelación y devolución.** Si una orden se cancela, ¿se libera todo el saldo reservado? ¿Una unidad ya consumida puede devolverse al stock y, en ese caso, a qué lote?

**D9. Ubicación del código.** En qué paquete vive la implementación del puerto. Depende de la convención de nombres pendiente (`servicios` o `servicio`; ver PR #47 e issue #46).

## 6. Propuesta de Inventario para D1–D9 (histórico; lo aprobado está en la sección 0)

> Propuesta del responsable de Inventario para discutir con Servicios. **No es una decisión aprobada** y no se implementará hasta que ambos responsables la acepten, la modifiquen o la reemplacen. Cada punto indica la evidencia en la que se apoya.

| # | Propuesta | Evidencia / motivo |
|---|---|---|
| D1 | `cantidad_reservada` guarda el **saldo pendiente** (reservado aún no consumido ni liberado). | Es lo que describe el ejemplo de `docs/servicios/integracion-inventario.md` (reservar 5, usar 3 y liberar 2 deja el saldo en 0 y `cantidad_usada` en 3). Con *acumulado* haría falta una columna para lo liberado, que el esquema no tiene; las reglas 4 y 5 hablan de "reserva pendiente". |
| D2 | **Opción A: descontar al reservar.** `reservar` resta de `cantidad_stock`; `liberar` suma; `consumir` no cambia `cantidad_stock`. | El descuento ocurre una sola vez (evita descontar dos veces). Reutiliza el `UPDATE … WHERE cantidad_stock >= ?` atómico ya probado, sin que Inventario tenga que sumar `servicios_repuestos` en cada consulta. `hayStockSuficiente` y las alertas de stock mínimo pasan a reflejar lo disponible para reservar. **Contrapartida:** el stock físico en bodega sería `cantidad_stock + Σ cantidad_reservada`; si se prefiere conservar `cantidad_stock` como stock físico, la alternativa es la opción B. |
| D3 | Rechazar la reserva completa si no hay disponibilidad; **sin reservas parciales**. | Regla 3 ya documentada por Servicios. |
| D4 | **Fuera del alcance de esta fase.** Las reservas trabajan sobre `cantidad_stock`; los movimientos por lote (`lotes.cantidad_disponible`, selección por `fecha_caducidad`, exclusión de vencidos) se acuerdan en una fase posterior. | Hoy ninguna operación mueve lotes y no hay regla para elegir lote. Mezclarlo ahora agrega decisiones sin datos de prueba. Riesgo conocido: `cantidad_stock` y la suma de lotes pueden no coincidir. |
| D5 | `precio_unitario_venta` se fija **al crear la línea**, en la primera reserva, con `repuestos.precio_venta`; reservas posteriores de la misma línea conservan ese precio. | La columna es `NOT NULL` sin valor por defecto: debe tener precio desde que se inserta la fila, y la fila nace con la reserva. Mantener un único precio por línea es coherente con la PK `(servicio_id, repuesto_id)`. |
| D6 | **Servicios abre y confirma la transacción**; el adaptador de Inventario trabaja sobre esa misma `Connection`, sin abrir otra, sin `commit` y sin cerrarla. | `servicios_repuestos` pertenece a Servicios y el cambio de stock a Inventario; ambos deben confirmarse juntos (sección "Consistencia transaccional" del documento de Servicios). Requiere acordar cómo recibe el adaptador la conexión (por constructor o por parámetro); `InventarioServiciosPort` hoy no la recibe. |
| D7 | Concurrencia con `UPDATE` condicionados en ambos lados: en `repuestos` (`cantidad_stock >= ?`) y en `servicios_repuestos` (`cantidad_reservada >= ?` al consumir o liberar). Si una condición no se cumple, se lanza la excepción y se hace `rollback` de todo. | El mismo patrón ya está probado con 20 salidas simultáneas (sección 3). |
| D8 | Cancelar una orden **libera todo el saldo** de cada línea (no toca `cantidad_usada`). La devolución de unidades ya consumidas **no se admite en esta fase**. | Regla 6 (liberar no reduce lo consumido). Una devolución necesitaría registrar un movimiento adicional que el esquema no contempla. |
| D9 | La implementación del puerto vive en el módulo de Inventario (`com.carmotors.servicios.inventario`) hasta que se cierre la convención de nombres. | Hoy conviven `servicio.taller` (donde está el puerto) y `servicios.inventario`/`clientes`/`vehiculos`/`facturacion` (issue #46). |

### Secuencias resultantes si se aprueba la propuesta (opción A)

Todas dentro de **una** transacción abierta por Servicios:

| Operación | Inventario (`repuestos`) | Servicios (`servicios_repuestos`) |
|---|---|---|
| `reservar(s, r, n)` | `UPDATE repuestos SET cantidad_stock = cantidad_stock - n WHERE id = r AND cantidad_stock >= n`; 0 filas → stock insuficiente o repuesto inexistente | Inserta la línea `(s, r, n, 0, precio_venta)` o suma `n` a `cantidad_reservada` si ya existe |
| `consumir(s, r, n)` | Sin cambios en `cantidad_stock` (ya se descontó al reservar) | `cantidad_reservada -= n`, `cantidad_usada += n`, con la condición `cantidad_reservada >= n` |
| `liberar(s, r, n)` | `cantidad_stock += n` | `cantidad_reservada -= n`, con la condición `cantidad_reservada >= n`; `cantidad_usada` no cambia |

Cualquier fallo en cualquiera de los dos lados → `rollback` de la transacción completa.

### Preguntas que se plantearon a Servicios (respondidas en la sección 0)

1. ¿Se aprueba D1 (saldo) tal como describe su propio ejemplo?
2. ¿Opción A o B para D2? Si es B, `cantidad_stock` sigue siendo stock físico y la disponibilidad se calcula restando los saldos de `servicios_repuestos`.
3. Para D6: ¿se agrega la `Connection` como parámetro de `InventarioServiciosPort` o el adaptador la recibe al construirse?
4. ¿Se acepta dejar lotes (D4) y devoluciones (D8) para una fase posterior?

## 7. Próximos pasos

1. Servicios conecta `InventarioServiciosJdbc` a sus casos de uso (registrar repuestos de una orden, consumir al usarlos, liberar o cancelar), dentro de su transacción.
2. Acordar D4 (lotes y devoluciones) en una fase posterior.
3. Repetir `AceptacionInventarioProveedoresTest` al fusionar el PR #70 (hoy pasa 5/5 en la combinación con esta rama).

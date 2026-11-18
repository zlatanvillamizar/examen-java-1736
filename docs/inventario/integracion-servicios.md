# Contrato Inventario ↔ Servicios — estado y decisiones pendientes

**Responsable de Inventario:** Valeria · **Responsable de Servicios:** Zlatan
**Estado:** borrador para acordar. Nada de lo marcado como *propuesta* o *por acordar* es una decisión definitiva.
**Relacionado:** issues #16 y #32, PR #44, [`docs/servicios/integracion-inventario.md`](../servicios/integracion-inventario.md) y [`docs/servicios/contratos-pendientes.md`](../servicios/contratos-pendientes.md).

Este documento describe qué ofrece hoy el módulo de Inventario al módulo de Servicios, qué falta y qué deben acordar ambos responsables antes de implementar reservas, consumo y liberación de repuestos.

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
- Cada operación abre su propia conexión. No pueden participar todavía en una transacción junto con cambios en `servicios_repuestos` (ver D6).

## 4. Operaciones pendientes

| Operación | Estado | Bloqueada por |
|---|---|---|
| Implementación de `InventarioServiciosPort.reservar` | No implementada | D1, D2, D3, D5, D6, D7 |
| Implementación de `InventarioServiciosPort.consumir` | No implementada | D1, D2, D4, D6, D7 |
| Implementación de `InventarioServiciosPort.liberar` | No implementada | D1, D2, D6, D8 |
| Consulta de disponibilidad que considere reservas | No implementada | D2 |
| Movimiento de unidades por lote | No implementado | D4 |

## 5. Decisiones por acordar

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

## 6. Próximos pasos propuestos

1. Ambos responsables acuerdan D1 a D9 y registran la decisión en este documento y en el issue #16.
2. Inventario implementa el puerto según lo acordado, con pruebas unitarias y pruebas de integración en bases desechables.
3. Servicios conecta el puerto a sus casos de uso y verifica el flujo completo.

Hasta entonces, Inventario **no** implementa reservas, consumo, liberación, transacciones compartidas ni movimientos por lote.

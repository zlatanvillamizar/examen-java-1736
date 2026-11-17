# Contrato Servicios ↔ Inventario

## Responsabilidades

**Servicios** conserva la relación de una orden con sus repuestos en `servicios_repuestos`: qué repuesto requiere la orden, cuánto se reservó, cuánto se usó y el precio de venta aplicado.

**Inventario** es el único responsable de la existencia física: `repuestos.cantidad_stock`, lotes y selección/descuento de unidades disponibles. Servicios no debe ejecutar SQL que modifique esas estructuras.

## Contrato Java

Servicios depende de `InventarioServiciosPort`, que expone tres intenciones:

- `reservar(ReservaRepuesto)`: solicita apartar una cantidad positiva para una orden.
- `consumir(ConsumoRepuesto)`: confirma que una cantidad positiva reservada fue utilizada.
- `liberar(ReservaRepuesto)`: devuelve al inventario una cantidad reservada que ya no será utilizada.

La implementación concreta del puerto pertenece a Inventario.

## Reglas acordadas

1. IDs de servicio y repuesto deben ser positivos.
2. Reservar, consumir o liberar cero unidades no es una operación válida.
3. Inventario debe rechazar una reserva cuando no exista disponibilidad suficiente.
4. El consumo no puede superar la cantidad reservada pendiente de esa orden/repuesto.
5. La liberación no puede superar la cantidad reservada pendiente.
6. `cantidad_usada` representa consumo confirmado y no debe reducirse al liberar una reserva.
7. Servicios no calcula ni actualiza `cantidad_stock` ni `lotes.cantidad_disponible`.
8. El precio de venta aplicado se conserva en `servicios_repuestos.precio_unitario_venta` para mantener el valor histórico del servicio.

## Consistencia transaccional pendiente de implementación

El contrato separa responsabilidades, pero una operación real afecta tanto el registro de `servicios_repuestos` como Inventario. La implementación del adaptador debe coordinar ambos cambios en una misma transacción JDBC (misma conexión) o definir un mecanismo equivalente antes de habilitar estas operaciones desde la vista. No se implementa una doble actualización desde Servicios para evitar estados parciales y duplicación de lógica.

## Secuencia esperada

- Reserva: Servicios valida la orden → solicita reserva a Inventario → registra/actualiza `cantidad_reservada`.
- Consumo: Servicios valida la orden → solicita consumo a Inventario → incrementa `cantidad_usada` y ajusta la reserva pendiente.
- Liberación/cancelación: Servicios solicita liberación a Inventario → reduce la reserva pendiente; no altera lo ya consumido.

Hasta que el adaptador transaccional esté implementado, `ServicioControlador` no expone operaciones de repuestos. Esto evita presentar a la futura vista una operación que todavía no puede garantizar atomicidad.

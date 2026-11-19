# Integración de CarMotors

La rama parte de `origin/develop` en `a2e62d9`. Las referencias de #67, #62, #61 y #68 se auditaron por separado contra esa base. Git pudo construir los cuatro árboles de comparación sin conflictos. `develop` incluye los archivos iniciales de Facturación, con clases de marcador. La implementación efectiva está en #68.

La rama de verificación temporal combinó #70 (`6af0dca`), #67 (`0fa2744`) y #62 (`ce2416b`). Conservó la numeración de #70, adaptó las pruebas de navegación de #67 y mantuvo los tres esquemas de prueba de Inventario, Inventario con Servicios y DDL consolidado. `ServicioService` y los archivos del adaptador verificados en la fase anterior conservaron su implementación.

## Orden propuesto de revisión e integración

| Orden | PR y revisión | Qué repetir tras aplicar sus cambios |
| --- | --- | --- |
| 1 | [#67](https://github.com/zlatanvillamizar/proyecto-java-grupo03/pull/67), `0fa2744`. Aprobación técnica registrada. El candidato de #70 ya incluye la resolución de `MenuPrincipal`. | Flujo de Vehículos desde Main, regreso a Servicios, validaciones y DAO de Vehículos en MySQL. |
| 2 | [#62](https://github.com/zlatanvillamizar/proyecto-java-grupo03/pull/62), `576e0b7`. El candidato incluye las vistas, la corrección UTF-8 del catálogo y su regresión. `cantidad_reservada` representa el saldo pendiente. | Probar la carga del catálogo con distintas codificaciones de cliente, los movimientos de stock y el flujo transaccional con Servicios. |
| 3 | [#70](https://github.com/zlatanvillamizar/proyecto-java-grupo03/pull/70). El candidato incluye #67 y #62 para revisión conjunta. Confirmar el orden de llegada a `develop` antes de fusionar. | Instalación en MySQL 8 vacío, flujo transaccional y navegación desde Main. |
| 4 | [#61](https://github.com/zlatanvillamizar/proyecto-java-grupo03/pull/61), `3cd4e76`. Integrado en #70 con la vista conectada y la actualización condicional de notificación. | FK de vehículo y servicio opcional, fechas límite, notificación concurrente y fin de entrada en la vista. |
| 5 | [#68](https://github.com/zlatanvillamizar/proyecto-java-grupo03/pull/68), `2acc467`. Integrado con emisión JDBC y vista. `3155e48` preserva el detalle de emisión. La tasa de la demostración académica es 0 %. | Consumo cero, estado facturable, rollback, emisiones simultáneas y consulta histórica. |
| 6 | [#69](https://github.com/zlatanvillamizar/proyecto-java-grupo03/pull/69), `45f78f2`. Aceptación y reauditoría de la factura histórica integradas con el esquema consolidado. | Ejecutar los flujos reales de consola, JDBC, EOF y recuperación de errores. |

Después de cada integración, ejecutar `mvn clean verify` con MySQL aislado. Las siete opciones usan las vistas reales. Los movimientos por lote y las devoluciones de cantidades ya consumidas siguen pendientes del acuerdo de Inventario.

## Resultados locales reproducibles

- `mvn clean verify` en `42fe6b85671a10736aca81199cdb3f6546099066`, con JDK 17.0.20.1 y MySQL local 26.7.0. Maven informó **207 aprobadas, 0 fallidas, 0 errores y 0 omitidas**. De esas pruebas, 52 usaron MySQL, incluidas 4 del DAO de Vehículos, 19 del adaptador de Inventario y 3 de Servicios con el esquema consolidado.
- `ServicioInventarioIntegracionTest` verifica rollback del alta y del stock, claves foráneas, precio de la primera reserva, consumo sin doble descuento, cancelación y dos órdenes que compiten por el mismo stock. `FlujosJdbcAceptacionTest` verifica la emisión real, el consumo cero y la unicidad de facturas, incluso bajo concurrencia.
- `MenuPrincipalTest` y `VistasEofAceptacionTest` verifican entradas inválidas, fin de entrada y regreso desde las vistas.
- Los XML de Surefire se generan en `target/surefire-reports`. El workflow del PR ejecuta la misma suite sobre MySQL 8.0 y publica esos informes.

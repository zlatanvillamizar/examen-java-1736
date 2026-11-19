# Integración de CarMotors

La rama parte de `origin/develop` en `a2e62d9`. Las referencias de #67, #62, #61 y #68 se auditaron por separado contra esa base. Git pudo construir los cuatro árboles de comparación sin conflictos. `develop` incluye los archivos iniciales de Facturación, con clases de marcador. La implementación efectiva está en #68.

La rama de verificación temporal combinó #70 (`6af0dca`), #67 (`0fa2744`) y #62 (`ce2416b`). Conservó la numeración de #70, adaptó las pruebas de navegación de #67 y mantuvo los tres esquemas de prueba de Inventario, Inventario con Servicios y DDL consolidado. `ServicioService` y los archivos del adaptador verificados en la fase anterior conservaron su implementación.

## Orden propuesto de revisión e integración

| Orden | PR y revisión | Qué repetir tras aplicar sus cambios |
| --- | --- | --- |
| 1 | [#67](https://github.com/zlatanvillamizar/proyecto-java-grupo03/pull/67), `0fa2744`. Aprobación técnica registrada. El candidato de #70 ya incluye la resolución de `MenuPrincipal`. | Flujo de Vehículos desde Main, regreso a Servicios, validaciones y DAO de Vehículos en MySQL. |
| 2 | [#62](https://github.com/zlatanvillamizar/proyecto-java-grupo03/pull/62), `ce2416b`. Su suite específica pasó junto con Servicios. Falta revisión formal del PR. `cantidad_reservada` como saldo pendiente quedó confirmado para la integración. | Probar los DAO de movimientos y el adaptador de Valeria, alta con reservas, concurrencia, stock insuficiente, cancelación y rollback. |
| 3 | [#70](https://github.com/zlatanvillamizar/proyecto-java-grupo03/pull/70). El candidato incluye #67 y #62 para revisión conjunta. Confirmar el orden de llegada a `develop` antes de fusionar. | Instalación en MySQL 8 vacío, flujo transaccional y navegación desde Main. |
| 4 | [#61](https://github.com/zlatanvillamizar/proyecto-java-grupo03/pull/61), `a820836`. La revisión requiere pruebas JDBC y conexión de `RecordatorioVista` con el escáner compartido. Dos notificaciones concurrentes pueden superar la lectura previa de `notificado` porque el `UPDATE` solo filtra por ID. | FK de vehículo y servicio opcional, fechas límite, notificación concurrente y fin de entrada en la vista. |
| 5 | [#68](https://github.com/zlatanvillamizar/proyecto-java-grupo03/pull/68), `f1aaf4a`. Los commits nuevos filtran las líneas de consumo cero y manejan EOF en la vista. Quedan por verificar esas correcciones en conjunto, las reglas del estado `Completado`, la tasa de impuestos y la facturación por JDBC. | Emitir usando el DAO y servicio reales, probar líneas sin consumo, estado facturable, rollback y dos emisiones simultáneas. Conectar la vista con el escáner compartido y probar regreso y fin de entrada. |

Después de cada integración, ejecutar `mvn clean verify` con MySQL aislado. Los 12 commits de #62 entre `ce2416b` y `d248951` ya forman parte del candidato de #70. Las opciones 1, 4 y 5 usan las vistas reales de Clientes, Inventario y Proveedores. Recordatorios y Facturación conservan sus rutas pendientes. Los movimientos por lote y las devoluciones de cantidades ya consumidas siguen pendientes del acuerdo de Inventario.

## Resultados locales reproducibles

- `mvn clean verify` en `42fe6b85671a10736aca81199cdb3f6546099066`, con JDK 17.0.20.1 y MySQL local 26.7.0. Maven informó **207 aprobadas, 0 fallidas, 0 errores y 0 omitidas**. De esas pruebas, 52 usaron MySQL, incluidas 4 del DAO de Vehículos, 19 del adaptador de Inventario y 3 de Servicios con el esquema consolidado.
- `ServicioInventarioIntegracionTest` verifica rollback del alta y del stock, claves foráneas, precio de la primera reserva, consumo sin doble descuento, cancelación y dos órdenes que compiten por el mismo stock. La comprobación de factura usa SQL y cubre la restricción de unicidad de la base. La emisión con `FacturaService` y `FacturaDAO` sigue pendiente de #68.
- `MenuPrincipalTest` ejecuta el punto de entrada, verifica entradas inválidas, recorre Vehículos, Servicios y Técnicos, y comprueba fin de entrada y regreso desde una opción con vista pendiente.
- Los XML de Surefire se generan en `target/surefire-reports`. El workflow del PR ejecuta la misma suite sobre MySQL 8.0 y publica esos informes.

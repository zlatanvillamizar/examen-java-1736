# Integración de CarMotors

La rama parte de `origin/develop` en `a2e62d9`. Las referencias de #67, #62, #61 y #68 se auditaron por separado contra esa base. Git pudo construir los cuatro árboles de comparación sin conflictos. `develop` incluye los archivos iniciales de Facturación, con clases de marcador. La implementación efectiva está en #68.

Servicios incorpora los tres archivos de producción necesarios de #62 (`InventarioServiciosJdbc`, `ReservaRepuestoDAO`, `RepuestoDAO`) y el menú reutiliza `VistaVehiculos` de #67. Los PR originales siguen siendo el lugar de revisión de sus cambios y conservan sus pruebas y documentación.

## Orden propuesto de revisión e integración

| Orden | PR y revisión | Qué repetir tras aplicar sus cambios |
| --- | --- | --- |
| 1 | [#67](https://github.com/zlatanvillamizar/proyecto-java-grupo03/pull/67), `0fa2744`. Aprobación técnica registrada. Preservar la numeración del menú integrado al reconciliar `MenuPrincipal` y adaptar las expectativas de su prueba. | Flujo de Vehículos desde Main, regreso a Servicios, validaciones y DAO de Vehículos en MySQL. |
| 2 | [#62](https://github.com/zlatanvillamizar/proyecto-java-grupo03/pull/62), `ce2416b`. El adaptador funciona con Servicios en las pruebas de esta rama. Falta revisión formal del PR y repetir su suite específica. `cantidad_reservada` como saldo pendiente quedó confirmado para la integración. | Probar los DAO de movimientos y el adaptador de Valeria, alta con reservas, concurrencia, stock insuficiente, cancelación y rollback. Conservar las extensiones del esquema de pruebas de ambos cambios en `EsquemaInventarioPrueba`. |
| 3 | Este PR. Reconciliar la rama con los cambios aprobados de #67 y #62 antes de fusionarla. | Instalación en MySQL 8 vacío, flujo transaccional y navegación desde Main. |
| 4 | [#61](https://github.com/zlatanvillamizar/proyecto-java-grupo03/pull/61), `a820836`. La revisión requiere pruebas JDBC y conexión de `RecordatorioVista` con el escáner compartido. Dos notificaciones concurrentes pueden superar la lectura previa de `notificado` porque el `UPDATE` solo filtra por ID. | FK de vehículo y servicio opcional, fechas límite, notificación concurrente y fin de entrada en la vista. |
| 5 | [#68](https://github.com/zlatanvillamizar/proyecto-java-grupo03/pull/68), `394492d`. Tiene cambios solicitados. El DAO consulta todas las líneas de repuestos, incluidas las de `cantidad_usada = 0`, que `DetalleFactura` rechaza. Quedan por validar las reglas del estado `Completado`, la tasa de impuestos y la facturación por JDBC. | Emitir usando el DAO y servicio reales, probar líneas sin consumo, estado facturable, rollback y dos emisiones simultáneas. Conectar la vista con el escáner compartido y probar regreso y fin de entrada. |

Después de cada integración, ejecutar `mvn clean verify` con MySQL aislado. Las vistas de Clientes, Inventario y Proveedores quedan pendientes. Los movimientos por lote y las devoluciones de cantidades ya consumidas siguen pendientes del acuerdo de Inventario.

## Resultados locales reproducibles

- `mvn clean verify` con JDK 17 y MySQL local 26.7.0. Maven informó **150 aprobadas, 0 fallidas, 0 errores y 0 omitidas**. De esas pruebas, 24 usaron MySQL y 3 instalaron el esquema consolidado en bases desechables.
- `ServicioInventarioIntegracionTest` verifica rollback del alta y del stock, claves foráneas, precio de la primera reserva, consumo sin doble descuento, cancelación y dos órdenes que compiten por el mismo stock. La comprobación de factura usa SQL y cubre la restricción de unicidad de la base. La emisión con `FacturaService` y `FacturaDAO` sigue pendiente de #68.
- `NavegacionMainTest` ejecuta el punto de entrada, verifica una opción inválida y recorre los submenús disponibles. Las demás opciones anuncian que su vista está pendiente.
- Los XML de Surefire se generan en `target/surefire-reports`. El workflow del PR ejecuta la misma suite sobre MySQL 8.0 y publica esos informes.

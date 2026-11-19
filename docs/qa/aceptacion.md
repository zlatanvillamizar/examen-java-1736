# Aceptación de CarMotors · 19 de noviembre de 2026

La aceptación de los recorridos disponibles pasó. La aceptación integral está pendiente de las rutas de consola que faltan y de los defectos de fin de entrada detallados abajo.

La rama de QA parte de `develop` en `a2e62d9`. También se construyó una copia temporal aplicando los cambios de los PR [#61](https://github.com/zlatanvillamizar/proyecto-java-grupo03/pull/61) (`a820836`), [#62](https://github.com/zlatanvillamizar/proyecto-java-grupo03/pull/62) (`ce2416b`), [#67](https://github.com/zlatanvillamizar/proyecto-java-grupo03/pull/67) (`0fa2744`) y [#68](https://github.com/zlatanvillamizar/proyecto-java-grupo03/pull/68) (`394492d`). Los cambios se aplicaron sin solapamientos en el menú y no se incorporaron a ninguna rama de integración. En esa copia, solo el #67 añade una opción principal.

## Matriz funcional

| Módulo | Escenario y resultado esperado | Estado y evidencia |
| --- | --- | --- |
| Menú principal | Una opción inválida repite el menú. Servicios abre Técnicos, se vuelve por ambos niveles, se vuelve a entrar a Servicios y se sale con 0. El mismo flujo de entrada sigue disponible hasta el cierre de la aplicación. | **Aprobado en ambas configuraciones.** `NavegacionAceptacionTest` invoca `Main.main`, comprueba el orden de los menús y verifica un único cierre de la entrada al salir. |
| Servicios | Registrar una orden para un vehículo existente, consultarla por ID, informar que falta una orden inexistente y presentar un error JDBC si se intenta registrar una orden con un vehículo inexistente. Después del error se puede seguir usando la consola. | **Aprobado.** Recorrido manual con MySQL en la copia temporal. Se registró y consultó la orden 1, la búsqueda 999 devolvió «No se encontró la orden 999» y el alta con vehículo 999 mostró «Error de base de datos». |
| Técnicos | Registrar y buscar un técnico, informar si el ID no existe y regresar a Servicios. Una opción inválida o un ID no numérico permite corregir. El EOF en el menú o al pedir el ID termina la aplicación. | **Aprobado.** `NavegacionAceptacionTest`, `VistaServiciosTecnicosTest` y recorrido real en MySQL con técnico 1 e ID inexistente 999. |
| Vehículos | Entrar desde `Main`, registrar para un cliente existente, buscar por ID y placa, listar, regresar y abrir Servicios. Una placa duplicada o un cliente inexistente se rechaza. Persistir entre conexiones. | **Aprobado en la copia con #67.** `MenuPrincipalTest`, `VistaVehiculosTest`, `VehiculoDAOIntegracionTest` y recorrido MySQL Vehículo → Orden. La opción no está en el `develop` auditado. |
| Clientes | Registrar, consultar y actualizar contactos desde su vista y volver con el lector compartido. Usar un cliente existente al registrar un vehículo. | **Consola bloqueada.** La opción y la vista faltan en las dos configuraciones. `ClienteControladorTest` y `ClienteServiceTest` cubren la capa disponible. El recorrido MySQL confirmó que el vehículo y la orden quedaron relacionados con el cliente 1 previamente insertado. |
| Inventario | Registrar y consultar repuestos, registrar movimientos, rechazar una salida sin stock e informar un ID inexistente. Errores de integridad deben cerrar sentencias y conexiones y permitir una consulta posterior. | **MySQL aprobado, consola bloqueada.** `InventarioFlujoIntegracionTest` y `RecursosJdbcAceptacionTest`. La copia con #62 también pasó las pruebas transaccionales y de concurrencia. Falta una ruta de consola. |
| Proveedores | Registrar, consultar y actualizar proveedores desde la consola. En una consulta sin coincidencias devolver vacío y cerrar los recursos JDBC. | **MySQL aprobado, consola bloqueada.** `InventarioFlujoIntegracionTest`, `ProveedorDAOIntegracionTest` y `RecursosJdbcAceptacionTest`. Falta una ruta de consola. |
| Recordatorios | Entrar, listar pendientes, notificar una vez, volver con el mismo `Scanner` y salir limpiamente si la entrada se agota. | **Consola bloqueada.** #61 aporta la vista y pruebas de controlador, alertas y servicio. En una sonda directa, la vista validó entradas y devolvió el lector abierto al elegir 0, pero el EOF lanzó `NoSuchElementException`. La navegación desde `Main` y el DAO real quedan pendientes. |
| Facturación | Generar y consultar facturas de órdenes, comprobar totales y transacción JDBC, volver con el mismo `Scanner` y salir limpiamente con EOF. | **Consola y JDBC bloqueados.** #68 aporta la vista y pruebas de cálculo y de controlador. En la sonda directa, la vista validó entradas y devolvió el lector abierto al elegir 0, pero el EOF lanzó `NoSuchElementException`. Falta la ruta desde `Main` y no hay DDL versionado de `facturas` en los bloques auditados. |

## Ejecuciones

Java 17.0.7, Maven y MySQL 8.0.46 desechable. `mvn clean verify` terminó con `BUILD SUCCESS` en los tres escenarios.

| Configuración | Detectadas | Aprobadas | Fallidas | Omitidas | JDBC real ejecutadas |
| --- | ---: | ---: | ---: | ---: | ---: |
| QA sobre `develop`, sin URL de MySQL | 153 | 130 | 0 | 23 | 0 |
| QA sobre `develop`, con MySQL | 153 | 153 | 0 | 0 | 23 |
| QA y los cuatro PR en la copia temporal, con MySQL | 289 | 289 | 0 | 0 | 51 |

Las dos pruebas JDBC nuevas usan las tablas de Inventario en esquemas aleatorios desechables. Observan el número de sentencias activas justo antes de cerrar cada conexión y comprueban que la conexión quedó cerrada. La prueba de error verifica el SQLState `23000` de MySQL ante una clave foránea rechazada, seguido de un listado vacío exitoso.

Para repetir la suite sin MySQL:

```bash
env -u CARMOTORS_TEST_DB_URL mvn clean verify
```

Para ejecutar las pruebas JDBC, prepara una instancia aislada y una cuenta con permisos sobre `carmotors_prueba_*`, configura `CARMOTORS_TEST_DB_PASSWORD` en el entorno y ejecuta:

```bash
export CARMOTORS_TEST_DB_URL=jdbc:mysql://127.0.0.1:33367/
export CARMOTORS_TEST_DB_USER=usuario_de_pruebas
mvn clean verify
```

## Evidencia de consola y criterios para liberar

- **Recorrido real.** Con los bloques de Clientes, Vehículos, Inventario y Servicios cargados en una base temporal, se arrancó `Main` con una factoría JDBC temporal en el classpath. Se registraron `ABC123`, la orden 1 y el técnico 1. Una consulta MySQL confirmó la relación `orden 1 → vehículo ABC123 → cliente 1` y que el técnico 1 estaba activo. El intento de crear una orden con vehículo 999 se rechazó. El flujo regresó de Técnicos a Servicios, después al menú principal y terminó con 0.
- **Reproducción del EOF.** En las vistas de #61 y #68, llama a `mostrarMenu()` con el constructor que recibe un `Scanner`. Con entradas `abc\n99\n0\nCONTINUAR`, ambas muestran los avisos de validación y el llamador aún puede leer `CONTINUAR`. Con entrada vacía, ambas lanzan `NoSuchElementException`. También ocurre al agotar la entrada tras elegir consultar factura (`2\n`) o listar recordatorios por vehículo (`4\n`).
- **Coordinación de menú.** Antes de aceptar las rutas bloqueadas, Zlatan debe acordar con Mary y Valeria la numeración y el punto de entrada de cada vista. El contrato de aceptación es recibir el lector del menú, devolverlo abierto al elegir 0, terminar limpiamente con EOF y conservar las rutas existentes. Para Facturación queda además por confirmar el esquema JDBC y las reglas de negocio pendientes en #68. La suite verde de la copia temporal cubre el código incluido y no acredita estas rutas pendientes.

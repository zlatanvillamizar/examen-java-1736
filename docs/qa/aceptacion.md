# Aceptación de CarMotors · 19 de noviembre de 2026

La segunda fase automatizó la reproducción de cuatro fallos de fin de entrada y verificó los recorridos JDBC A, B, D y E. La aceptación integral sigue pendiente de los fallos reproducidos, las rutas de consola faltantes y el contrato SQL de Facturación.

La rama de QA parte de `develop` en `a2e62d9`. También se construyó una copia temporal aplicando los cambios de los PR [#61](https://github.com/zlatanvillamizar/proyecto-java-grupo03/pull/61) (`a820836`), [#62](https://github.com/zlatanvillamizar/proyecto-java-grupo03/pull/62) (`ce2416b`), [#67](https://github.com/zlatanvillamizar/proyecto-java-grupo03/pull/67) (`0fa2744`) y [#68](https://github.com/zlatanvillamizar/proyecto-java-grupo03/pull/68) (`394492d`). Los cambios se aplicaron sin solapamientos en el menú y no se incorporaron a ninguna rama de integración. En esa copia, solo el #67 añade una opción principal.

## Primera fase · Matriz funcional

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

## Segunda fase · Referencias evaluadas

Tras `git fetch --all --prune`, `develop` seguía en `a2e62d9`. Los PR #61, #62, #67 y #68 seguían abiertos en los SHA de la primera fase. El PR #69 partió de su propio HEAD publicado (`68ef527)). En `develop`, `Main` ofrece Servicios y su submenú Técnicos. Clientes, Vehículos e Inventario/Proveedores tienen capas Java base. El #67 añade la ruta de Vehículos. Recordatorios y la implementación de consola de Facturación dependen de #61 y #68. El #62 aporta el adaptador transaccional de Inventario.

La prueba de cada rama individual se realizó aplicando solo el cambio de ese PR sobre la rama de QA basada en `develop`. La integración temporal aplicó los cuatro cambios sobre QA. Ninguna copia se publicó como rama de integración.

### Navegación y respuesta a fallos

| Módulo | Escenario y resultado esperado | Resultado obtenido | Estado | Commit y evidencia | Responsable del bloqueo |
| --- | --- | --- | --- | --- | --- |
| Servicios | Desde `Main`, abrir, rechazar entradas inválidas, registrar, consultar, mostrar un fallo JDBC recuperable, volver y gestionar EOF sin cerrar el lector del menú. | La navegación y el cierre único pasaron. MySQL rechazó el alta para el vehículo 999, y la aplicación continuó con la consulta y el submenú. | **Aprobado** | `a2e62d9` y copia combinada. `NavegacionAceptacionTest`, `FlujosJdbcAceptacionTest.mainContinuaTrasErrorJdbcYPermiteVolverDesdeTecnicos` y recorrido de la primera fase. | |
| Técnicos | Desde Servicios, abrir, validar ID, informar ausencia y fallo JDBC, volver a Servicios y gestionar EOF. | Pasaron la prueba de `Main` con un técnico inexistente en MySQL y las pruebas de vista con un fallo JDBC simulado. `Main` conserva el lector hasta salir. | **Aprobado** | `a2e62d9` y copia combinada. `VistaServiciosTecnicosTest`, `NavegacionAceptacionTest` y `FlujosJdbcAceptacionTest`. | |
| Vehículos | Abrir, registrar, consultar, validar entradas, gestionar error JDBC y EOF, conservar el lector y volver a Servicios. | Pasaron las 2 pruebas de menú, 14 de vista y 4 JDBC del #67. La opción aún falta en `develop`. | **Aprobado en la copia combinada** | `0fa2744`. `MenuPrincipalTest`, `VistaVehiculosTest` y `VehiculoDAOIntegracionTest`. | Zlatan, integración en `develop`. |
| Clientes | Abrir la vista, rechazar entradas inválidas, volver y gestionar EOF o fallos JDBC con el mismo lector. | Sin ruta ni vista en los commits auditados. El flujo JDBC A sí pasó. | **Bloqueado** | `a2e62d9` y copia combinada. Inventario de rutas de `MenuPrincipal` y prueba A. | Mary y Zlatan. |
| Inventario | Abrir, validar, consultar, recuperar la sesión tras errores JDBC y volver con el lector abierto. | Sin ruta de consola. Los flujos JDBC B y E y el cierre de recursos pasaron. | **Bloqueado en consola** | `ce2416b` en la copia combinada. Pruebas B/E y `RecursosJdbcAceptacionTest`. | Valeria y Zlatan. |
| Proveedores | Abrir, validar, manejar EOF y fallos JDBC, volver con el lector abierto. | Sin ruta de consola. Consulta ausente y cierre de recursos verificados en JDBC. | **Bloqueado en consola** | `a2e62d9` y copia combinada. `RecursosJdbcAceptacionTest` y `ProveedorDAOIntegracionTest`. | Valeria y Zlatan. |
| Recordatorios | Abrir desde `Main`, validar y volver con el mismo lector. Entrada vacía o agotada durante el listado debe devolver el control. | Sin ruta en `Main`. La vista directa valida y conserva el lector al elegir 0. `mostrarMenu` y `leerEntero` fallan ante entrada vacía y `4\n`. `leerTexto` y `leerFecha` también leen con `nextLine()` directo. | **Fallido en EOF. Bloqueado desde Main** | `a820836`. Dos fallos de `VistasEofAceptacionTest` y una prueba de retorno aprobada, tanto por separado como en la copia combinada. | Mary, EOF. Zlatan y Mary, ruta de menú. |
| Facturación | Abrir desde `Main`, validar y volver con el mismo lector. Entrada vacía o agotada durante la consulta debe devolver el control. | Sin ruta en `Main`. La vista directa valida y conserva el lector al elegir 0. `mostrarMenu` y `leerEntero` fallan ante entrada vacía y `2\n`. | **Fallido en EOF. Bloqueado desde Main y JDBC real** | `394492d`. Dos fallos de `VistasEofAceptacionTest` y una prueba de retorno aprobada, tanto por separado como en la copia combinada. Falta DDL de `facturas`. | Mary, EOF. Zlatan y Mary, contrato SQL y ruta de menú. |

### Flujos JDBC

| Flujo | Resultado esperado | Resultado obtenido | Estado | Commit y evidencia | Responsable del bloqueo |
| --- | --- | --- | --- | --- | --- |
| A · Cliente y Vehículo | Registrar ambos por sus controladores y verificar la relación tras otra conexión. | Búsqueda por placa devolvió el ID de alta y el ID del cliente registrado. Se releyó el documento del cliente. | **Aprobado** | `a2e62d9` y copia combinada con `0fa2744`. `registraClienteYVehiculoYCompruebaLaRelacionPersistida`. | |
| B · Servicios e Inventario | Crear orden, reservar 5 de 10, consumir 2 y liberar 3. | Stock final 8, reserva pendiente 0 y consumo 2. Se creó la orden por el controlador de Servicios y se ejecutó la transacción mediante el adaptador de Inventario. Falta invocar el adaptador desde la operación normal de Servicios. | **Aprobado mediante el adaptador. Bloqueado desde la operación normal de Servicios** | Copia combinada, `ce2416b`. `creaOrdenReservaConsumeYLiberaYSeRecuperaTrasRollbackJdbc`. | Zlatan, conexión del caso de uso con el adaptador. |
| C · Facturación | Emitir para servicio completado, consultar y rechazar duplicados. | Las pruebas de servicio/controlador del #68 pasaron. La persistencia y la unicidad real por servicio quedan pendientes del contrato de `facturas`. | **Bloqueado en JDBC** | `394492d`. `FacturaServiceTest`, `FacturaControladorTest` e inspección de `database/bloques/`. | Mary y Zlatan. |
| D · Recordatorios | Crear, consultar próximos y vencidos, notificar y comprobar persistencia. | Se creó un próximo por el controlador y se sembró un vencido histórico, ya que la API rechaza altas en el pasado. Ambas búsquedas clasificaron correctamente. Tras notificar, otra conexión leyó el cambio y el registro desapareció de próximos. | **Aprobado en JDBC** | `a820836` individual y combinado. `clasificaRecordatoriosYConservaLaNotificacionEntreConexiones`. | |
| E · Recuperación | Una reserva inicial seguida de otra con FK inválida debe revertir ambas y permitir una operación válida posterior. | La segunda reserva falló con una violación de integridad JDBC. El stock volvió a 10 y desapareció la línea de la primera reserva. La siguiente transacción completó el flujo B. `Main` también continuó tras un error JDBC independiente en Servicios. | **Aprobado** | `ce2416b` en copia combinada, y prueba de consola sobre `a2e62d9`. `FlujosJdbcAceptacionTest`. | |

### Evidencia de ejecución de la segunda fase

JDK 17.0.7, Maven y MySQL 8.0.46. La suite ejecutó `mvn clean verify` en cada configuración.

| Configuración | Detectadas | Aprobadas | Fallidas | Omitidas | JDBC real ejecutadas |
| --- | ---: | ---: | ---: | ---: | ---: |
| QA sobre `develop`, sin MySQL | 163 | 130 | 0 | 33 | 0 |
| QA sobre `develop`, con MySQL | 163 | 155 | 0 | 8 | 25 |
| QA más #61 individual, con MySQL | 212 | 206 | 2 | 4 | 26 |
| QA más #68 individual, con MySQL | 193 | 186 | 2 | 5 | 25 |
| QA más #61, #62, #67 y #68 en la copia temporal, con MySQL | 299 | 295 | 4 | 0 | 55 |

Las cuatro fallidas combinadas son dos casos EOF por vista, con `NoSuchElementException: No line found`. Las pruebas de esas vistas se omiten solo cuando su API aún no existe en la compilación. En el entorno combinado estuvieron activas las seis pruebas de vista y los cuatro casos JDBC nuevos. Los flujos JDBC cargan los bloques versionados en un esquema de nombre aleatorio y redirigen la fábrica fija solo durante la clase de pruebas aislada. Para repetirlos, usa las variables de la primera fase con una URL de servidor sin catálogo ni parámetros y ejecuta:

```bash
mvn -Dtest=FlujosJdbcAceptacionTest,NavegacionAceptacionTest,VistasEofAceptacionTest test
```

### Orden recomendado para Zlatan

1. Incorporar #67 y reevaluar el #69 sobre el nuevo `develop`. Conservar las rutas de Servicios/Técnicos y Vehículos y la salida con 0.
2. Incorporar el adaptador del #62 tras validar B y E y conectar la operación de Servicios con la transacción compartida. Mantener el caso de rollback seguido de recuperación.
3. Integrar las vistas de #61 y #68 al menú definitivo después de que sus regresiones de EOF pasen. En Facturación, validar antes el DDL y el rechazo de duplicados sobre MySQL.
4. Al conectar Clientes, Inventario y Proveedores, añadir sus rutas al guion automatizado desde `Main` y repetir la suite completa con MySQL. Acordar la numeración y que cada vista devuelva el control sin cerrar el lector compartido.

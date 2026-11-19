# Navegación de consola

## Opciones de Main

| Opción | Módulo | Estado en #70 |
| --- | --- | --- |
| 1 | Clientes | Activa. Registra, busca por ID o documento, lista y actualiza el contacto. |
| 2 | Vehículos | Activa. Registra, busca por ID o placa y lista. |
| 3 | Servicios y Técnicos | Activa. Gestiona órdenes, técnicos y repuestos. La opción 8 abre el submenú de Técnicos. |
| 4 | Inventario y Repuestos | Activa. Registra y consulta repuestos, stock, entradas, salidas y alertas de stock mínimo. |
| 5 | Proveedores | Activa. Registra, busca por ID o nombre, lista y actualiza el contacto. |
| 6 | Recordatorios y Alertas | Activa. Crea recordatorios, consulta alertas y marca notificaciones. |
| 7 | Facturación | Activa. Emite, consulta y lista facturas de servicios completados. |
| 0 | Salir | Termina Main. |

Las siete rutas abren las vistas reales desde `Main`. `MenuPrincipalTest` y `VistasEofAceptacionTest` verifican entrada inválida, regreso con `0` y EOF. `FlujosJdbcAceptacionTest` incluye alta y notificación de recordatorios y emisión de factura desde las opciones 6 y 7 sobre el esquema consolidado.

Para registrar repuestos, carga primero [los datos iniciales de tipos](inventario/repuesto_tipos_iniciales.sql) en la base de la aplicación y consulta los IDs de `repuesto_tipos`. `AceptacionInventarioProveedoresTest` instala el esquema consolidado en MySQL, prueba ese catálogo y verifica persistencia del proveedor y el repuesto tras entradas, salidas y un rechazo por stock insuficiente.

## Contrato de las vistas

- El menú principal crea el único `Scanner` de `System.in`. Cada vista y sus submenús reciben esa instancia. La salida se comparte al construir la vista cuando su API lo admite.
- Lee líneas completas. Antes de leer, comprueba `hasNextLine()`. Si se acaba la entrada durante una operación, devuelve el control sin procesar datos incompletos ni quedar esperando. Con EOF, el menú principal termina.
- La opción `0` devuelve el control al menú que abrió la vista. Ninguna vista cierra el `Scanner` compartido ni vuelve a llamar a `Main` o a `MenuPrincipal`.
- Una opción desconocida muestra un mensaje y deja que la persona elija de nuevo. Un dato inválido dentro de una operación se informa en la vista y no causa una salida abrupta.
- El menú registra una acción de apertura asociada al número. Conecta la acción a la API que publique el módulo y pasa el `Scanner` compartido en la composición. El menú delega el trabajo del módulo a su vista.

## Clientes

La comparación de #61 (`a820836`) con el candidato `9ceb167` confirma que `Cliente`, `ClienteDAO`, `ClienteService`, `ClienteControlador`, sus pruebas existentes y el bloque SQL de Clientes ya están en #70. El delta de #61 agrega Recordatorios y Alertas. La única vista de ese delta es `RecordatorioVista`. Clientes depende de su propia tabla y de la conexión JDBC. Vehículos usa `ClienteDAO.buscarPorId` y su clave foránea apunta a `clientes.id`, por lo que Clientes puede conectarse por separado.

Estas son las firmas públicas de `ClienteControlador`. Todas declaran `throws SQLException`.

| Operación | Firma |
| --- | --- |
| Registrar | `Cliente registrar(String nombre, String documento, String telefono, String email)` |
| Buscar por ID | `Optional<Cliente> buscarPorId(int id)` |
| Buscar por documento | `Optional<Cliente> buscarPorDocumento(String documento)` |
| Listar | `List<Cliente> listar()` |
| Actualizar contacto | `boolean actualizarContacto(int id, String telefono, String email)` |

`VistaClientes` usa el `Scanner` compartido y `EntradaConsola` para volver con `0`, manejar EOF y mostrar errores de entrada y JDBC. El alta muestra el ID que se debe indicar al registrar un vehículo. Al actualizar el contacto, un teléfono vacío lo elimina y el correo es obligatorio. `ClientesMainIntegracionTest` abre dos sesiones reales de `Main` sobre un esquema MySQL desechable. Registra el cliente en la primera y utiliza su ID para consultar, actualizar y registrar un vehículo en la segunda.

## Lista de verificación para incorporar cambios

- [ ] Registrar los SHA del candidato de #70 y de los nuevos commits. Compararlos con `fe652cd9`, cuya ejecución con Java 17 y MySQL 8.0 aprobó 207 pruebas.
- [ ] Probar primero en una rama temporal. Revisar los deltas del menú, del contrato JDBC y de los esquemas de prueba para conservar sus resoluciones.
- [x] **Valeria — opciones 4 y 5.** Las vistas de `d248951` comparten la entrada y el controlador. Pasaron la aceptación sobre el esquema consolidado y las regresiones de stock, consumo, cancelación, rollback y concurrencia.
- [x] **Clientes — opción 1.** Vista real conectada y flujo Cliente → Vehículo comprobado desde `Main`.
- [x] **Mary — opción 6.** `3cd4e76` integrado. EOF seguro, consultas JDBC y una sola transición ante dos notificaciones concurrentes.
- [x] **Mary — opción 7.** `2acc467` integrado. EOF, consumo cero, rollback y una sola factura por servicio ante emisiones concurrentes.
- [x] Verificar desde `Main` la apertura de las vistas reales, entrada inválida, regreso y EOF.
- [ ] Ejecutar `mvn clean verify` con Java 17 y MySQL 8 aislado. Conservar la cobertura de las 207 pruebas de referencia, ejecutar las nuevas y revisar que ninguna prueba JDBC se omita. Adjuntar el SHA y los informes a #70 antes de decidir la integración definitiva.

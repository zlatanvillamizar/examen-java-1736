# Navegación de consola

## Opciones de Main

| Opción | Módulo | Estado en #70 |
| --- | --- | --- |
| 1 | Clientes | Ruta pendiente. El controlador y el DAO están disponibles. |
| 2 | Vehículos | Activa. Registra, busca por ID o placa y lista. |
| 3 | Servicios y Técnicos | Activa. Gestiona órdenes, técnicos y repuestos. La opción 8 abre el submenú de Técnicos. |
| 4 | Inventario y Repuestos | Ruta pendiente de vista. |
| 5 | Proveedores | Ruta pendiente de vista. |
| 6 | Recordatorios y Alertas | Ruta pendiente. La vista candidata está en #61. |
| 7 | Facturación | Ruta pendiente. La vista candidata está en #68. |
| 0 | Salir | Termina Main. |

Una ruta pendiente muestra un aviso y conserva el menú principal. `MenuPrincipalTest` comprueba ese comportamiento para las cinco opciones desde `Main`. `RutasPendientesMenuTest` instala acciones simuladas en el mismo despachador de producción y verifica selección, entrada compartida, opción inválida, retorno con `0` y fin de entrada. Las simulaciones cubren el despacho del menú. Cada vista real debe demostrar su propio comportamiento al incorporarse.

## Contrato de las vistas

- El menú principal crea el único `Scanner` de `System.in`. Cada vista y sus submenús reciben esa instancia. La salida se comparte al construir la vista cuando su API lo admite.
- Lee líneas completas. Antes de leer, comprueba `hasNextLine()`. Si se acaba la entrada durante una operación, devuelve el control sin procesar datos incompletos ni quedar esperando. Con EOF, el menú principal termina.
- La opción `0` devuelve el control al menú que abrió la vista. Ninguna vista cierra el `Scanner` compartido ni vuelve a llamar a `Main` o a `MenuPrincipal`.
- Una opción desconocida muestra un mensaje y deja que la persona elija de nuevo. Un dato inválido dentro de una operación se informa en la vista y no causa una salida abrupta.
- El menú registra una acción de apertura asociada al número. Conecta la acción a la API que publique el módulo y pasa el `Scanner` compartido en la composición. El menú delega el trabajo del módulo a su vista.

## Lista de verificación para incorporar cambios

- [ ] Registrar los SHA del candidato de #70 y de los nuevos commits. Compararlos con `fe652cd9`, cuya ejecución con Java 17 y MySQL 8.0 aprobó 207 pruebas.
- [ ] Probar primero en una rama temporal. Revisar los deltas del menú, del contrato JDBC y de los esquemas de prueba para conservar sus resoluciones.
- [ ] **Valeria — opciones 4 y 5.** Verificar que las vistas publicadas reciben la entrada compartida, llaman al controlador existente y vuelven con `0` o EOF. Repetir los casos de stock insuficiente, precio fijado, consumo, cancelación, rollback y concurrencia con los DAO de Inventario y Servicios.
- [ ] **Clientes — opción 1.** Conectar la vista que publique el módulo y comprobar que un cliente registrado desde la consola se puede usar para registrar un vehículo.
- [ ] **Mary — opción 6.** Verificar consultas reales en MySQL, límites de fechas, FK de vehículo y servicio opcional, y que dos intentos concurrentes de notificación solo produzcan una transición. Probar EOF tanto en el menú como durante la captura de datos.
- [ ] **Mary — opción 7.** Verificar emisión en MySQL con el estado facturable acordado y el impuesto configurado, líneas con cero unidades consumidas, rollback y una sola factura ante dos emisiones concurrentes. Probar EOF, entrada inválida, retorno y consulta de una factura emitida.
- [ ] Añadir pruebas de la vista real desde `Main` con entrada inválida, regreso y EOF. Cambiar la expectativa de aviso pendiente para la ruta que se habilita y conservar esa expectativa para las demás.
- [ ] Ejecutar `mvn clean verify` con Java 17 y MySQL 8 aislado. Conservar la cobertura de las 207 pruebas de referencia, ejecutar las nuevas y revisar que ninguna prueba JDBC se omita. Adjuntar el SHA y los informes a #70 antes de decidir la integración definitiva.

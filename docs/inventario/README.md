# Inventario y Proveedores en CarMotors

Este módulo controla los repuestos del taller, su stock y los proveedores. Además, entrega a Servicios las operaciones transaccionales para reservar, consumir y liberar repuestos de una orden.

- **Uso desde la consola:** `VistaInventario` y `VistaProveedores` (secciones 2 y 3).
- **Integración con Servicios:** `InventarioServiciosJdbc` (sección 5 y [integracion-servicios.md](integracion-servicios.md), donde están las decisiones aprobadas D1–D8).
- **Diagrama de clases:** [clases.svg](clases.svg) para verlo y [clases.drawio](clases.drawio) para editarlo en draw.io. El DER aprobado no cambia: sigue en [database/CarMotors_DER_Ordenado_V2.drawio](../../database/CarMotors_DER_Ordenado_V2.drawio).

![Diagrama de clases de Inventario y Proveedores](clases.svg)

## 1. Arquitectura

| Capa | Clase | Responsabilidad |
| --- | --- | --- |
| Vista | `VistaInventario` | Menú de repuestos y stock. Lee, valida el formato y muestra resultados o errores. |
| Vista | `VistaProveedores` | Menú de proveedores. Solo permite actualizar los datos de contacto. |
| Vista | `EntradaConsola` | Lectura validada compartida por las dos vistas: enteros, decimales, textos obligatorios u opcionales, y mensajes de error sin detalles internos de JDBC. **No cierra el `Scanner`.** |
| Controlador | `InventarioControlador` | Punto de entrada de las vistas. No contiene SQL. |
| Servicio | `InventarioService` | Reglas: ids y cantidades positivas, stock nunca negativo, nombre de proveedor único. |
| Servicio | `InventarioServiciosJdbc` | Implementa `InventarioServiciosPort` para Servicios dentro de la transacción que Servicios abre. |
| DAO | `RepuestoDAO`, `ProveedorDAO`, `ReservaRepuestoDAO` | SQL parametrizado sobre `repuestos`, `proveedores` y `servicios_repuestos`. |
| DAO | `Transaccion` | Commit o `rollback` completo y cierre de la conexión; conserva la excepción original. |
| Modelo | `Repuesto`, `Proveedor` | Records validados que reflejan las tablas de `database/bloques/inventario.sql`. |

## 2. Inventario de repuestos (`VistaInventario`)

| Opción | Qué hace |
| --- | --- |
| 1. Registrar repuesto | Pide nombre y marca, muestra los tipos disponibles (`1 = Filtros, 2 = Frenos…`) y pide el ID del tipo, el precio de venta (punto decimal), el stock inicial y el stock mínimo. Un tipo que no está en la lista se rechaza antes de guardar. |
| 2. Buscar repuesto por ID | Muestra el repuesto o avisa que no existe. |
| 3. Listar repuestos | Lista todos los repuestos por ID y marca los que están en su stock mínimo. |
| 4. Consultar stock disponible | Muestra el stock disponible y el mínimo, y alerta si conviene reponer. |
| 5. Registrar entrada | Suma unidades al stock. |
| 6. Registrar salida | Resta unidades solo si alcanzan. Si no alcanzan, muestra el error y no cambia nada. |
| 7. Listar repuestos en stock mínimo | Lista los repuestos para reposición. |
| 8. Ver repuestos de una orden de servicio | Muestra, por repuesto, las unidades reservadas sin usar, las usadas y el precio fijado. Avisa si quedan unidades apartadas: vuelven al stock al liberar, completar o cancelar la orden. |
| 0. Volver | Regresa al menú principal. |

Sesión real (salida de la consola, sin repetir el menú):

```text
Opción: 1
Nombre: Filtro de aceite
Marca: Bosch
ID del tipo de repuesto: 1
Precio de venta (punto decimal, por ejemplo 25000.50): 25000.00
Stock inicial: 10
Stock mínimo: 3
Repuesto registrado con ID 1.
Opción: 5   → ID 1, cantidad 5   → Entrada registrada. Stock actual: 15.
Opción: 6   → ID 1, cantidad 12  → Salida registrada. Stock actual: 3.
Opción: 6   → ID 1, cantidad 4   → Stock insuficiente: se pidieron 4 y hay 3 disponibles. No se registró el movimiento.
Opción: 4   → ID 1
Repuesto 1 | Filtro de aceite | Stock disponible: 3 | Stock mínimo: 3
(El stock disponible ya excluye las unidades reservadas para servicios.)
Alerta: el repuesto está en su stock mínimo o por debajo; conviene reponerlo.
```

El tipo de repuesto debe existir en `repuesto_tipos`. Si el catálogo está vacío, la vista explica cómo cargarlo con `repuesto_tipos_iniciales.sql`; si el ID no está en la lista, responde *"El tipo de repuesto 99 no existe. Elija uno de la lista."* y no guarda nada.

## 3. Proveedores (`VistaProveedores`)

| Opción | Qué hace |
| --- | --- |
| 1. Registrar proveedor | Nombre obligatorio y único (sin distinguir mayúsculas, igual que la columna `UNIQUE`). Contacto, teléfono y email son opcionales. |
| 2. Buscar por ID / 3. Buscar por nombre | La búsqueda por nombre usa el nombre exacto, sin espacios al inicio ni al final. |
| 4. Listar proveedores | Lista todos por ID. |
| 5. Actualizar contacto | Cambia persona de contacto, teléfono y email. **El nombre no se modifica**, porque es el dato único del proveedor. |

Al actualizar, cada dato muestra el valor actual: **Enter** lo conserva, **-** lo borra y cualquier otro texto lo reemplaza.

```text
Opción: 5
ID del proveedor: 1
Proveedor: AutoPartes SAS (el nombre no se modifica).
Persona de contacto [Laura Gómez] (Enter conserva, - borra):
Teléfono [3001234567] (Enter conserva, - borra): -
Email [ventas@autopartes.co] (Enter conserva, - borra): compras@autopartes.co
Contacto del proveedor actualizado.
Proveedor 1 | AutoPartes SAS | Contacto: Laura Gómez | Teléfono: sin registrar | Email: compras@autopartes.co
```

### Errores que muestran las dos vistas

| Situación | Mensaje |
| --- | --- |
| Opción o número inválido | `Opción inválida…`, `… debe ser un número entero.`, `… debe ser positivo.` |
| Regla del modelo o del servicio | `Validación: …` (por ejemplo, email inválido o precio que no cabe en `DECIMAL(10,2)`) |
| Proveedor repetido | `Error: Ya existe un proveedor con el nombre …` |
| Falla de conexión o SQL | `Error de base de datos. Revise la conexión y vuelva a intentarlo.` (sin detalles internos) |
| Fin de la entrada | La vista vuelve al menú que la llamó sin guardar la operación incompleta. |

Después de cualquier error, el menú sigue funcionando.

## 4. Reglas de stock

1. Toda entrada o salida necesita un repuesto existente y una cantidad positiva.
2. **El stock nunca queda negativo.** El servicio revisa el stock antes de la salida, y el DAO lo vuelve a comprobar dentro del `UPDATE … WHERE cantidad_stock >= ?`. Así, dos salidas simultáneas no pueden sacar más unidades de las que hay.
3. Cada entrada o salida usa su propia transacción. Si algo falla, se hace `rollback` y la base queda igual.
4. `cantidad_stock` es el **stock disponible**: ya excluye lo reservado para órdenes de servicio (decisión D2, opción A).
5. Un repuesto está en stock mínimo cuando `cantidad_stock <= stock_minimo`.

## 5. Reservas, consumos y liberaciones (integración con Servicios)

Servicios coordina la transacción e Inventario mueve el stock. Las reglas aprobadas son:

| Operación | Stock (`repuestos.cantidad_stock`) | Línea (`servicios_repuestos`) |
| --- | --- | --- |
| **Reservar** n | Baja n **una sola vez**. Si no alcanza, falla todo (sin reservas parciales, D3). | `cantidad_reservada + n`. Al crear la línea se fija el precio (D5). |
| **Consumir** n | **No cambia**: ya se descontó al reservar. | `cantidad_reservada − n`, `cantidad_usada + n`. No puede superar lo pendiente. |
| **Liberar** n | Sube n. | `cantidad_reservada − n`. `cantidad_usada` no cambia. |
| **Cancelar o completar** (`liberarTodo`) | Sube todo lo pendiente. | `cantidad_reservada = 0`. Nunca devuelve lo usado (D8). Completar libera desde la revisión del 20/11/2026. |

Ejemplo aprobado de D1 (`cantidad_reservada` es lo pendiente de consumir), con stock inicial de 10:

| Paso | Stock | Reservada | Usada |
| --- | --- | --- | --- |
| Reservar 5 | 5 | 5 | 0 |
| Consumir 2 | 5 | 3 | 2 |
| Liberar 3 | 8 | 0 | 2 |

**Transacciones:** el adaptador recibe la `Connection` de Servicios con `autoCommit` desactivado y nunca hace `commit`, `rollback` ni `close`. Si una operación falla, Servicios deshace toda la transacción: sus propios cambios y los de Inventario. `ejecutarEnTransaccion` lo hace automáticamente.

**Bloqueos:** todas las operaciones bloquean primero la fila de `repuestos` y después la línea de `servicios_repuestos`, para evitar interbloqueos.

```java
// Servicios: una orden reserva dos repuestos y actualiza su estado en una sola transacción.
InventarioServiciosJdbc.ejecutarEnTransaccion(ConexionFactory::obtenerConexion, (conexion, inventario) -> {
    // ... cambios propios de Servicios con la MISMA conexion ...
    inventario.reservar(new ReservaRepuesto(servicioId, 1, 2));   // en orden ascendente de repuesto_id
    inventario.reservar(new ReservaRepuesto(servicioId, 2, 4));
    return null;
}); // commit si todo sale bien; rollback completo ante cualquier excepción

// Al usar las piezas (otra transacción de Servicios):
InventarioServiciosJdbc.ejecutarEnTransaccion(ConexionFactory::obtenerConexion, (conexion, inventario) -> {
    inventario.consumir(new ConsumoRepuesto(servicioId, 1, 2));
    return null;
});

// Al cancelar la orden: Servicios bloquea antes la fila del servicio y libera lo pendiente.
int devueltas = InventarioServiciosJdbc.ejecutarEnTransaccion(ConexionFactory::obtenerConexion,
        (conexion, inventario) -> inventario.liberarTodo(servicioId));
```

| Error | Significado |
| --- | --- |
| `StockInsuficienteException` | No hay stock para reservar. |
| `RepuestoNoEncontradoException` | El repuesto no existe. |
| `NoSuchElementException` | El servicio no tiene reserva de ese repuesto. |
| `IllegalStateException` | Se pide más que lo pendiente, la conexión está en `autoCommit` o falló JDBC (la `SQLException` queda como causa). |

### Dependencias entre Inventario y Servicios

- **Servicios → Inventario:** `ServicioService` usará `InventarioServiciosPort`, implementado por `InventarioServiciosJdbc`, y le pasa su conexión. Ese cableado lo hace Zlatan; Inventario no modifica Servicios.
- **Inventario → Servicios:** usa los records `ReservaRepuesto` y `ConsumoRepuesto` y la interfaz del paquete `servicio.taller.integracion`, sin cambiarlos. Solo escribe en `servicios_repuestos`; nunca en `servicios`.
- **Base de datos:** `inventario.sql` debe cargarse antes que `servicios.sql`, porque `servicios_repuestos.repuesto_id` referencia `repuestos.id`. No se agregaron columnas ni tablas.

## 6. Conexión al menú principal (para Zlatan)

Las instrucciones técnicas completas, con firmas reales, diff de `MenuPrincipal` y de `MenuPrincipalTest` contra `fae432b` y verificación esperada, están en **[INTEGRACION_MENU.md](INTEGRACION_MENU.md)**. Resumen:

Las vistas son independientes y **esta rama no modifica `MenuPrincipal` ni `Main`**. El menú definitivo llega con el PR #70 (`integracion/carmotors-final`), que ya muestra "4. Inventario y Repuestos" y "5. Proveedores" como rutas pendientes en un `Map`. Para conectarlas basta con este cambio:

```diff
+import com.carmotors.controlador.InventarioControlador;
 import com.carmotors.controlador.ServicioControlador;
 import com.carmotors.controlador.VehiculoControlador;
@@
         VistaVehiculos vehiculos = new VistaVehiculos(entrada, salida, new VehiculoControlador());
-        mostrar(entrada, salida, Map.of("2", vehiculos::mostrar, "3", servicios::mostrar));
+        InventarioControlador inventarioControlador = new InventarioControlador();
+        VistaInventario inventario = new VistaInventario(entrada, salida, inventarioControlador);
+        VistaProveedores proveedores = new VistaProveedores(entrada, salida, inventarioControlador);
+        mostrar(entrada, salida, Map.of("2", vehiculos::mostrar, "3", servicios::mostrar,
+                "4", inventario::mostrar, "5", proveedores::mostrar));
```

En `MenuPrincipalTest` (PR #70), las opciones 4 y 5 salen de la prueba de rutas pendientes, `@ValueSource(strings = {"1", "6", "7"})`, y conviene agregar:

```java
@Test
void navegaPorInventarioYProveedoresDesdeMainYRegresaAlMenu() {
    String salida = ejecutarMain("4\n2\nx\n0\n5\n2\n0\n0\n0\n");
    assertTrue(salida.contains("=== Inventario ==="), salida);
    assertTrue(salida.contains("ID del repuesto debe ser un número entero."));
    assertTrue(salida.contains("=== Proveedores ==="));
    assertTrue(salida.contains("ID del proveedor debe ser positivo."));
    assertFalse(salida.contains("pendiente de integración"));
    assertEquals(3L, salida.lines().filter(linea -> linea.contains("=== CarMotors SGT ===")).count());
}
```

Las vistas comparten el `Scanner` del menú y no cierran `System.in`. Cuando la entrada se acaba, vuelven al menú, que termina normalmente.

### Integración temporal verificada (19/11/2026)

Se hicieron en copias aparte que luego se descartaron, con Java 17.0.21 y MySQL 8.0.46 desechable. Ninguna rama de otros integrantes se modificó.

| Combinación | Resultado de `mvn clean verify` |
| --- | --- |
| `develop` + PR #67 + esta rama + menú conectado (sobre el menú del PR #67) | 234 aprobadas, 0 fallidas, 0 omitidas |
| PR #70 (`fae432b`) + esta rama + prueba de aceptación, sin tocar el menú | 256 aprobadas, 0 fallidas, 0 omitidas |
| PR #70 + esta rama + prueba de aceptación + menú conectado (cambio de arriba) | 255 aprobadas, 0 fallidas, 0 omitidas |
| PR #70 (`fae432b`) + PR #62 (`d248951`) + corrección de codificación + menú conectado + pruebas de menú de [INTEGRACION_MENU.md](INTEGRACION_MENU.md) | **261 aprobadas**, 0 fallidas, 0 omitidas |

Las ramas se fusionan sin conflictos. `EsquemaInventarioPrueba` tiene en esta rama exactamente el mismo cambio que el PR #70 (`crearConsolidado()`), así que la fusión de ese archivo también es limpia.

## 7. Evidencia de pruebas

Comando: `mvn clean verify` con Java 17.0.21 y Maven 3.9.11. Las pruebas JDBC solo corren si existe `CARMOTORS_TEST_DB_URL` y usan bases desechables `carmotors_prueba_<aleatorio>` que se crean y eliminan en cada prueba. Nunca tocan la base `carmotors`.

| Funcionalidad | Clases de prueba | Ejecutadas | Aprobadas | Fallidas | Omitidas sin MySQL |
| --- | --- | --- | --- | --- | --- |
| Vistas de consola de Inventario y Proveedores | `VistaInventarioTest`, `VistaProveedoresTest`, `VistasInventarioIntegracionTest` | 21 | 21 | 0 | 2 |
| Reservas, consumos, liberaciones, cancelación, rollback, concurrencia y conexiones | `InventarioServiciosJdbcTest`, `InventarioServiciosJdbcIntegracionTest` | 29 | 29 | 0 | 22 |
| Transacciones y movimientos de stock | `TransaccionTest`, `RepuestoDAOTransaccionTest`, `RepuestoDAOTransaccionIntegracionTest`, `RepuestoDAOTest`, `RepuestoDAOIntegracionTest` | 28 | 28 | 0 | 13 |
| Proveedores (DAO) | `ProveedorDAOTest`, `ProveedorDAOIntegracionTest` | 10 | 10 | 0 | 7 |
| Reglas de negocio y controlador | `InventarioServiceTest`, `InventarioControladorTest`, `InventarioFlujoIntegracionTest` | 31 | 31 | 0 | 3 |
| Modelos | `RepuestoTest`, `ProveedorTest` | 17 | 17 | 0 | 0 |
| Base de pruebas desechable | `EsquemaInventarioPruebaTest` | 3 | 3 | 0 | 3 |
| Aceptación sobre el esquema consolidado (PR #70) | `AceptacionInventarioProveedoresTest` | 5 | 5 en la combinación con el PR #70 | 0 | 5 (también con MySQL mientras `esquema.sql` no esté en la rama) |
| **Total Inventario y Proveedores** | | **144** | **139 en esta rama + 5 en la combinación** | **0** | **55** |
| Resto del proyecto | otros módulos | 75 | 75 | 0 | 0 |
| **Total del proyecto en esta rama** | | **219** | **214** (5 omitidas: aceptación) | **0** | **55** |

Las pruebas detectan errores reales. Se comprobó con mutaciones temporales, revertidas después:

| Error introducido a propósito | Resultado |
| --- | --- |
| `consumir` sin bloquear primero el repuesto | Interbloqueo de MySQL en 3 de 3 ejecuciones. Así se encontró y corrigió el error del cierre. |
| La transacción no cierra la conexión | La prueba de conexiones detecta 40 conexiones abiertas. |
| Consumir vuelve a descontar stock (fase anterior) | 6 fallos. |
| Liberar sin condición de saldo (fase anterior) | 1 fallo y 1 error. |

La clase de integración del adaptador se repitió 10 veces: 10 de 10 sin fallos.

### Prueba de aceptación sobre el esquema consolidado

`AceptacionInventarioProveedoresTest` instala `database/esquema/esquema.sql` completo en una base desechable con `EsquemaInventarioPrueba.crearConsolidado()`. Después maneja las vistas por consola como lo haría un usuario:

1. Comprueba que el esquema tiene exactamente las columnas que usan los DAO (`repuesto_tipos`, `proveedores`, `repuestos`, `servicios_repuestos`) y que no trae tipos de repuesto.
2. Ejecuta dos veces `repuesto_tipos_iniciales.sql`: quedan 6 tipos sin duplicados y la estructura de tablas y columnas no cambia.
3. **Sesión 1:** registra el proveedor *AutoPartes SAS* y el repuesto *Filtro de aceite* (stock 10, mínimo 3); entrada de 5 → 15; salida de 12 → 3; una salida de 4 se rechaza por stock insuficiente.
4. **Sesión 2:** con un controlador nuevo, DAO nuevos y conexiones nuevas, buscar, consultar stock y buscar el proveedor por nombre muestran los mismos datos.
5. Una conexión SQL independiente confirma lo guardado: stock 3, tipo, precio y contacto del proveedor.
6. Sin inicializar los tipos, el registro de un repuesto se rechaza con un mensaje claro y no guarda nada.
7. El script guarda bien los acentos aunque el cliente use `latin1`, y repetirlo con clientes distintos no duplica tipos (`elScriptDeTiposGuardaBienLosAcentosAunqueElClienteUseOtraCodificacion`).

Si las transacciones de stock hicieran `rollback` en lugar de `commit`, la prueba falla (mutación comprobada).

En esta rama la prueba queda **omitida**, porque `esquema.sql` todavía no está publicado aquí. Se ejecuta sola cuando el archivo llegue con el PR #70. Hoy pasa 5 de 5 en la combinación PR #70 + esta rama.

| Verificación final | Prueba que lo demuestra |
| --- | --- |
| No se permite stock negativo | `unaSalidaSinStockSuficienteMuestraElErrorYNoCambiaNada`, `RepuestoDAOTransaccionIntegracionTest` (20 salidas simultáneas sobre 10 unidades: se aceptan exactamente 10) |
| Las reservas simultáneas no asignan de más | `reservasConcurrentesNuncaComprometenMasUnidadesDeLasDisponibles` |
| Consumir no descuenta dos veces | `unConsumoParcialPasaUnidadesAUsadasSinVolverADescontarStock`, `ejemploAprobadoD1ReservarCincoConsumirDosYLiberarElResto` |
| Liberar devuelve solo lo pendiente | `liberarDevuelveSoloLoNoConsumidoYNoAlteraLoUsado`, `noSePuedeLiberarMasQueElSaldoPendiente` |
| Un rollback revierte todo | `siFallaUnaOperacionSeDeshaceTodaLaTransaccionDeNegocio`, `TransaccionTest` |
| No hay fugas de conexiones | `noQuedanConexionesAbiertasTrasOperacionesCorrectasYFallidas` |
| Las vistas funcionan con entradas válidas e inválidas | `VistaInventarioTest`, `VistaProveedoresTest`, `VistasInventarioIntegracionTest` |
| Persistencia entre conexiones sobre el esquema consolidado | `flujoCompletoDesdeLaConsolaPersisteEntreConexiones` |
| Compatible con Java 17 | Compilación y pruebas con JDK 17.0.21; GitHub Actions (`Java CI`) |

## 8. Ejecución

Se necesitan Java 17, Maven y MySQL 8.0.16 o superior. Para instalar la base, use el esquema consolidado `database/esquema/esquema.sql` (PR #70) sobre una base vacía o, en desarrollo, los bloques `inventario.sql` (antes) y `servicios.sql`. La conexión de la aplicación sale de `com.carmotors.config.ConexionFactory`.

### Inicializar `repuesto_tipos` sin modificar el esquema aprobado

El esquema solo crea la tabla `repuesto_tipos` y no inserta datos. Sin al menos un tipo, la opción "Registrar repuesto" se rechaza: *"verifique que el tipo de repuesto exista"*. Los tipos son **datos**, así que se cargan aparte con [`repuesto_tipos_iniciales.sql`](repuesto_tipos_iniciales.sql), sin tocar `esquema.sql`:

```bash
mysql --default-character-set=utf8mb4 -u <usuario> -p <base> < database/esquema/esquema.sql   # una vez, base vacía
mysql -u <usuario> -p <base> < docs/inventario/repuesto_tipos_iniciales.sql   # repetible
```

```sql
SET NAMES utf8mb4;
INSERT INTO repuesto_tipos (nombre) VALUES
    ('Filtros'), ('Frenos'), ('Lubricantes'), ('Eléctricos'), ('Suspensión'), ('Motor')
ON DUPLICATE KEY UPDATE nombre = nombre;
```

- Empieza con `SET NAMES utf8mb4` porque el archivo está en UTF-8. Sin esa línea, un cliente `mysql` con `latin1` por defecto (frecuente) guardaba "Eléctricos" doblemente codificado, y al repetir el script con otro cliente se duplicaban los tipos con tilde. Este defecto se encontró y corrigió en la auditoría del 19/11/2026. Al instalar el esquema conviene usar también `--default-character-set=utf8mb4`.
- Solo inserta filas: no tiene `CREATE`, `ALTER`, `DROP`, `TRUNCATE` ni `DELETE`.
- Se puede repetir sin duplicar ni fallar, gracias a `uq_repuesto_tipos_nombre` y a que `ON DUPLICATE KEY UPDATE` no cambia nada. Funciona desde MySQL 8.0.16, la versión mínima del esquema.
- La lista es una propuesta: para agregar un tipo basta con otro `INSERT INTO repuesto_tipos (nombre) VALUES ('…');`.
- El ID que pide la consola al registrar un repuesto se consulta con `SELECT id, nombre FROM repuesto_tipos ORDER BY id;`.
- La prueba de aceptación ejecuta exactamente este archivo y verifica que no cambia la estructura de tablas y columnas.

### Pruebas JDBC

Se usan una URL y un usuario de una instancia de pruebas. Cada prueba crea y borra su propia base `carmotors_prueba_<aleatorio>`:

```bash
CARMOTORS_TEST_DB_URL="jdbc:mysql://localhost:3306/" CARMOTORS_TEST_DB_USER=<usuario_pruebas> \
CARMOTORS_TEST_DB_PASSWORD=<clave_pruebas> mvn clean verify
```

## 9. Pendientes reales

| Pendiente | Responsable |
| --- | --- |
| Conectar `VistaInventario` y `VistaProveedores` a las rutas 4 y 5 de `MenuPrincipal` del PR #70 (instrucciones en [INTEGRACION_MENU.md](INTEGRACION_MENU.md)). | Zlatan (Servicios / menú) |
| Usar `InventarioServiciosJdbc` desde `ServicioService` y `ServicioControlador`. | Zlatan |
| Lotes y devoluciones (D4): fuera de esta entrega. | Fase posterior |
| `database/esquema/esquema.sql` llega con el PR #70. La compatibilidad ya se verificó con la prueba de aceptación (5/5). Al fusionarse, la prueba deja de omitirse en esta rama. | Hecho; se repite sola tras fusionar #70 |
| No hay pantalla para los tipos de repuesto: se cargan con `repuesto_tipos_iniciales.sql` (sección 8) y el registro pide el ID del tipo. Las tablas `lotes`, `proveedores_repuestos` y `evaluaciones_proveedor` no tienen código Java. | Fuera de esta entrega |

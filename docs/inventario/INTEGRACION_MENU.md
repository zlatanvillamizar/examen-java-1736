# Conexión de Inventario y Proveedores al menú principal (para Zlatan)

Las opciones 4 y 5 ya están conectadas en el PR #70 desde el candidato `9ceb167`. Los diffs siguientes documentan la propuesta original sobre `fae432b`.

Instrucciones para conectar las vistas reales de Inventario y Proveedores en `MenuPrincipal` de la rama `integracion/carmotors-final` (PR #70). Las firmas se tomaron del código publicado: PR #62 en `d248951` y PR #70 en `fae432b`.

**La rama de Valeria no modifica `Main` ni `MenuPrincipal`.** Este cambio lo aplica quien coordina el menú.

## 1. Clases y firmas reales

Todas están en `src/main/java/com/carmotors/`.

| Clase | Firma | Notas |
| --- | --- | --- |
| `vista.VistaInventario` | `final class VistaInventario` · `VistaInventario(Scanner entrada, PrintStream salida, InventarioControlador controlador)` · `void mostrar()` | Visibilidad de paquete, igual que `VistaServicios` y `VistaVehiculos`. Se usa desde `MenuPrincipal`, que está en el mismo paquete `com.carmotors.vista`. |
| `vista.VistaProveedores` | `final class VistaProveedores` · `VistaProveedores(Scanner entrada, PrintStream salida, InventarioControlador controlador)` · `void mostrar()` | Igual. |
| `vista.EntradaConsola` | (interna) | Las dos vistas la crean solas; `MenuPrincipal` no la usa. |
| `controlador.InventarioControlador` | `public InventarioControlador()` · `public InventarioControlador(InventarioService inventario)` | El constructor sin argumentos crea `new InventarioService()`. |
| `servicios.inventario.InventarioService` | `public InventarioService()` · `public InventarioService(RepuestoDAO, ProveedorDAO)` | Crea `new RepuestoDAO()` y `new ProveedorDAO()`. |
| `dao.RepuestoDAO`, `dao.ProveedorDAO` | `public RepuestoDAO()` / `public ProveedorDAO()` → `ConexionFactory::obtenerConexion` | **No abren conexiones al construirse**; cada operación abre y cierra la suya. |

Comportamiento con el que ya cuenta `MenuPrincipal.mostrar(Scanner, PrintStream, Map<String, Runnable>)`:

- Las vistas usan el `Scanner` recibido y **nunca lo cierran** (no cierran `System.in`).
- Opción `0`: `mostrar()` termina y el control vuelve al menú.
- **EOF:** `mostrar()` termina sin excepción y sin guardar la operación a medias; después el menú termina, porque `hasNextLine()` devuelve `false`.
- Errores de datos, de stock o de JDBC se muestran dentro de la vista y el submenú sigue funcionando. Si faltan `CARMOTORS_DB_URL`, `CARMOTORS_DB_USER` o `CARMOTORS_DB_PASSWORD`, la operación muestra *"Error de base de datos. Revise la conexión y vuelva a intentarlo."* y el programa no se cae.
- Un solo `InventarioControlador` sirve para las dos vistas.

## 2. Cambio en `MenuPrincipal.java`

Opciones acordadas: **4 = Inventario y Repuestos** y **5 = Proveedores**. Diff contra `fae432b`:

```diff
@@ -1,5 +1,6 @@
 package com.carmotors.vista;

+import com.carmotors.controlador.InventarioControlador;
 import com.carmotors.controlador.ServicioControlador;
 import com.carmotors.controlador.VehiculoControlador;

@@ -13,7 +14,11 @@ public class MenuPrincipal {
         PrintStream salida = System.out;
         VistaServicios servicios = new VistaServicios(entrada, salida, new ServicioControlador());
         VistaVehiculos vehiculos = new VistaVehiculos(entrada, salida, new VehiculoControlador());
-        mostrar(entrada, salida, Map.of("2", vehiculos::mostrar, "3", servicios::mostrar));
+        InventarioControlador inventarioControlador = new InventarioControlador();
+        VistaInventario inventario = new VistaInventario(entrada, salida, inventarioControlador);
+        VistaProveedores proveedores = new VistaProveedores(entrada, salida, inventarioControlador);
+        mostrar(entrada, salida, Map.of("2", vehiculos::mostrar, "3", servicios::mostrar,
+                "4", inventario::mostrar, "5", proveedores::mostrar));
     }

     // Cada acción usa la entrada compartida y devuelve el control con 0 o al llegar a EOF.
```

No hay que tocar nada más: la lista del menú ya muestra "4. Inventario y Repuestos" y "5. Proveedores", y `abrirVista` ejecuta la ruta en lugar del aviso "pendiente de integración".

## 3. Cambio en `MenuPrincipalTest.java`

Las opciones 4 y 5 dejan de estar pendientes. Se agregan pruebas de navegación, de entrada inválida y de EOF dentro de cada vista. Ninguna necesita MySQL. Diff contra `fae432b`:

```diff
@@ -34,8 +34,36 @@ class MenuPrincipalTest {
         assertTrue(ejecutarMain("2\n1\n").contains("ID del cliente: "));
     }

+    @Test
+    void navegaPorInventarioYProveedoresDesdeMainYRegresaAlMenu() {
+        String salida = ejecutarMain("4\n2\nx\n0\n5\n2\n0\n0\n0\n");
+        assertTrue(salida.contains("=== Inventario ==="), salida);
+        assertTrue(salida.contains("ID del repuesto debe ser un número entero."));
+        assertTrue(salida.contains("=== Proveedores ==="));
+        assertTrue(salida.contains("ID del proveedor debe ser positivo."));
+        assertFalse(salida.contains("pendiente de integración"));
+        assertEquals(3L, salida.lines().filter(linea -> linea.contains("=== CarMotors SGT ===")).count());
+    }
+
+    @Test
+    void opcionesInvalidasDentroDeInventarioYProveedoresNoSalenDeLaVista() {
+        String salida = ejecutarMain("4\n9\nabc\n0\n5\n8\n0\n0\n");
+        assertTrue(salida.contains("Opción inválida. Elija un número entre 0 y 7."), salida);
+        assertTrue(salida.contains("Opción inválida. Elija un número entre 0 y 5."));
+        assertEquals(3L, salida.lines().filter(linea -> linea.contains("=== CarMotors SGT ===")).count());
+    }
+
+    @ParameterizedTest
+    @ValueSource(strings = {"4\n1\nFiltro\n", "5\n1\nAutoPartes\n", "4\n", "5\n"})
+    void terminaSinErroresSiLaEntradaAcabaDentroDeInventarioOProveedores(String datos) {
+        String salida = ejecutarMain(datos);
+        assertTrue(salida.contains("=== Inventario ===") || salida.contains("=== Proveedores ==="), salida);
+        assertFalse(salida.contains("Exception"), salida);
+        assertFalse(salida.contains("registrado con ID"), "No guarda una operación incompleta");
+    }
+
     @ParameterizedTest
-    @ValueSource(strings = {"1", "4", "5", "6", "7"})
+    @ValueSource(strings = {"1", "6", "7"})
     void informaQueLaVistaEstaPendienteYRegresaAlMenu(String opcion) {
         String salida = ejecutarMain(opcion + "\n0\n");
         assertTrue(salida.contains("La vista de este módulo está pendiente de integración."));
```

`RutasPendientesMenuTest` no cambia: usa rutas simuladas.

## 4. Verificación esperada

Con Java 17 y MySQL 8 aislado:

```bash
CARMOTORS_TEST_DB_URL="jdbc:mysql://127.0.0.1:3306/" CARMOTORS_TEST_DB_USER=<usuario_pruebas> \
CARMOTORS_TEST_DB_PASSWORD=<clave_pruebas> mvn clean verify
```

Resultado local del 19/11/2026 en una rama temporal (PR #70 `fae432b` + PR #62 + estos dos cambios + la corrección de codificación de `repuesto_tipos_iniciales.sql`): **261 aprobadas, 0 fallidas, 0 errores, 0 omitidas**. Incluye `MenuPrincipalTest` (11), `AceptacionInventarioProveedoresTest` (5) y `ServicioInventarioIntegracionTest` (3).

Prueba manual con el menú real, después de instalar el esquema y los tipos (sección 8 del [README](README.md)):

```bash
export CARMOTORS_DB_URL="jdbc:mysql://localhost:3306/carmotors" CARMOTORS_DB_USER=<usuario> CARMOTORS_DB_PASSWORD=<clave>
mvn -q compile && java -cp "target/classes:<mysql-connector-j.jar>" com.carmotors.Main
# 4 → 1 (registrar repuesto, tipo 1) → 5 (entrada) → 6 (salida) → 0 → 5 → 1 (registrar proveedor) → 0 → 0
```

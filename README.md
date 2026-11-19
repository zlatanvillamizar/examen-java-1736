# CarMotors — Sistema de Gestión de Taller

Proyecto académico de Zlatan Ricardo Villamizar Fajardo, Valeria, Crisbely y Marilud. Gestiona clientes, vehículos, órdenes, técnicos, inventario, proveedores, recordatorios y facturación.

## Requisitos

- JDK 17 y Maven 3.
- MySQL 8.0.16 o posterior para aplicar las restricciones `CHECK`.
- Una cuenta JDBC con acceso al esquema de la aplicación. Para las pruebas JDBC, usa una cuenta con permisos de creación, uso y eliminación sobre esquemas de prueba `carmotors_prueba_*`.

## Instalación y ejecución

1. Clona el repositorio y abre la rama que quieras revisar. La rama principal de integración es `develop`.
2. Crea una base **vacía** para CarMotors y verifica el servidor y el nombre del esquema antes de ejecutar SQL.
3. Desde la raíz del repositorio, abre el cliente `mysql` con el esquema vacío como destino. Comprueba `SELECT DATABASE();` y ejecuta `SOURCE database/esquema/esquema.sql`. También puedes ejecutar el archivo desde tu IDE con ese esquema seleccionado.
4. Carga los catálogos de estados, tipos de mantenimiento y tipos de repuesto antes de registrar órdenes y repuestos. El esquema deja la asignación de IDs al catálogo. Servicios identifica la cancelación por los nombres `Cancelado` o `Cancelada`, sin distinguir mayúsculas. Facturación en el PR #68 espera `Completado`.
5. Configura estas variables en tu terminal o en la configuración local de IntelliJ, sin versionar los valores.

| Variable | Contenido |
| --- | --- |
| `CARMOTORS_DB_URL` | URL JDBC del esquema, por ejemplo `jdbc:mysql://localhost:3306/carmotors` |
| `CARMOTORS_DB_USER` | Usuario de la aplicación |
| `CARMOTORS_DB_PASSWORD` | Contraseña de la cuenta JDBC |

Ejecuta `com.carmotors.Main` desde IntelliJ tras importar el `pom.xml`, o desde la terminal.

```bash
mvn compile exec:java -Dexec.mainClass=com.carmotors.Main
```

El script consolidado crea clientes → vehículos → inventario → servicios → recordatorios → facturas. Los archivos de `database/bloques/` conservan el DDL de cada equipo. El bloque original de Clientes también contiene Recordatorios y requiere separarlos para una instalación desde cero. El script consolidado usa el esquema seleccionado, crea tablas y restricciones, y está destinado a una instalación nueva. Una actualización de una base existente requiere su propia migración.

## Módulos y arquitectura

| Módulo | Estado de la consola en esta rama |
| --- | --- |
| Clientes | Controlador y persistencia disponibles. Vista pendiente. |
| Vehículos | Registro y consultas con la vista de #67. |
| Servicios y Técnicos | Órdenes, asignaciones y movimientos de repuestos. |
| Inventario y Proveedores | Controlador y persistencia disponibles. Vistas pendientes. |
| Recordatorios y Alertas | Vista candidata en #61, pendiente de validación JDBC e integración. |
| Facturación | Vista y DAO en #68, pendientes de validar con JDBC e integrar al menú. |

`Main` abre `vista.MenuPrincipal`, que comparte un único `Scanner` con las vistas. Los controladores delegan en las clases de servicio y los DAO ejecutan SQL mediante JDBC. Servicios coordina la transacción y construye el adaptador de Inventario con esa misma conexión. La reserva descuenta unidades disponibles, el consumo las mueve al saldo usado y la liberación devuelve el pendiente. Al cancelar, Servicios bloquea la orden y confirma a la vez el estado y la devolución. La primera reserva fija el precio de la línea.

## Pruebas

```bash
mvn clean verify
```

Para ejecutar también las pruebas JDBC, configura `CARMOTORS_TEST_DB_URL` con la URL del servidor (por ejemplo, `jdbc:mysql://localhost:3306/`), `CARMOTORS_TEST_DB_USER` y `CARMOTORS_TEST_DB_PASSWORD`. Las pruebas crean esquemas aleatorios desechables y eliminan los que crearon. Sin URL de prueba, las pruebas JDBC se marcan como omitidas. Los informes de JUnit quedan en `target/surefire-reports` y GitHub Actions los adjunta a su ejecución con MySQL 8.0.

Consulta los resultados, el orden de integración y los bloqueos pendientes en el [informe de integración](docs/integracion-final.md).

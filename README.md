# CarMotors — Sistema de Gestión de Taller

Proyecto académico de Zlatan Ricardo Villamizar Fajardo, Valeria, Crisbely y Marilud. Gestiona clientes, vehículos, órdenes, técnicos, inventario, proveedores, recordatorios y facturación.

## Requisitos

- JDK 17 y Maven 3.
- MySQL 8.0.16 o posterior para aplicar las restricciones `CHECK`.
- Una cuenta JDBC con acceso al esquema de la aplicación. Para las pruebas JDBC, usa una cuenta con permisos de creación, uso y eliminación sobre esquemas de prueba `carmotors_prueba_*`.

## Instalación y ejecución

Clona la rama de revisión del PR #70. La rama principal de integración es `develop`.

```bash
git clone --branch integracion/carmotors-final https://github.com/zlatanvillamizar/proyecto-java-grupo03.git
cd proyecto-java-grupo03
java -version
mvn -version
```

Comprueba que Maven también muestra Java 17. Desde la raíz del repositorio, abre el cliente MySQL con una cuenta administrativa. Sustituye `admin_local` por tu usuario de MySQL.

```bash
mysql --host=127.0.0.1 --port=3306 --user=admin_local --password
```

En el cliente, comprueba el destino y crea una base nueva. Si el nombre ya existe, detente y elige otro antes de importar el esquema. Usa el mismo nombre al configurar la URL JDBC.

```sql
SELECT @@hostname, @@port, VERSION();
CREATE DATABASE carmotors CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE carmotors;
SELECT DATABASE();
```

En esa sesión interactiva, ejecuta `SOURCE database/esquema/esquema.sql` desde la raíz del repositorio. También puedes salir del cliente y cargar el mismo archivo por entrada estándar. MySQL solicitará la contraseña administrativa.

```bash
mysql --host=127.0.0.1 --port=3306 --user=admin_local --password --default-character-set=utf8mb4 --database=carmotors < database/esquema/esquema.sql
```

Usa una cuenta de aplicación con permisos de lectura y escritura sobre ese esquema. Si necesitas crearla, ejecuta estas dos sentencias como administrador, ajustando el nombre de la base y el host de la cuenta si corresponde. La generación de contraseña del ejemplo requiere MySQL 8.0.18 o posterior. Guarda la contraseña de forma privada. [Manual de MySQL](https://dev.mysql.com/doc/refman/8.0/en/create-user.html)

```sql
CREATE USER 'carmotors_app'@'localhost' IDENTIFIED BY RANDOM PASSWORD;
GRANT SELECT, INSERT, UPDATE, DELETE ON carmotors.* TO 'carmotors_app'@'localhost';
```

Carga los catálogos de estados y tipos de mantenimiento antes de registrar órdenes. Servicios identifica la cancelación por los nombres `Cancelado` o `Cancelada`, sin distinguir mayúsculas. Facturación en el PR #68 espera `Completado`. Registrar un vehículo requiere un cliente existente. Una instalación vacía permite probar la conexión desde **2 → 4** (Vehículos → Listar vehículos).

Antes de registrar repuestos, puedes cargar el catálogo inicial de Valeria en la misma base seleccionada. El script añade seis nombres, se puede volver a ejecutar y conserva el DDL aprobado.

```sql
SELECT DATABASE();
SOURCE docs/inventario/repuesto_tipos_iniciales.sql
SELECT id, nombre FROM repuesto_tipos ORDER BY nombre;
```

Usa los IDs devueltos al registrar un repuesto. Si cargas el archivo por entrada estándar, emplea el mismo comando de importación del esquema con la ruta `docs/inventario/repuesto_tipos_iniciales.sql`.

Configura las variables en la misma terminal que ejecutará Maven, o en la configuración local de IntelliJ. Mantén los valores fuera del repositorio.

| Variable | Contenido |
| --- | --- |
| `CARMOTORS_DB_URL` | URL JDBC del esquema, por ejemplo `jdbc:mysql://127.0.0.1:3306/carmotors` |
| `CARMOTORS_DB_USER` | Usuario de la aplicación |
| `CARMOTORS_DB_PASSWORD` | Contraseña de la cuenta JDBC |

En Bash puedes leer la contraseña sin mostrarla.

```bash
export CARMOTORS_DB_URL='jdbc:mysql://127.0.0.1:3306/carmotors'
export CARMOTORS_DB_USER='carmotors_app'
read -rs -p 'Contraseña JDBC: ' CARMOTORS_DB_PASSWORD
echo
export CARMOTORS_DB_PASSWORD
```

En PowerShell 7.2 o posterior.

```powershell
$env:CARMOTORS_DB_URL = 'jdbc:mysql://127.0.0.1:3306/carmotors'
$env:CARMOTORS_DB_USER = 'carmotors_app'
$env:CARMOTORS_DB_PASSWORD = Read-Host 'Contraseña JDBC' -MaskInput
```

Ejecuta `com.carmotors.Main` desde IntelliJ tras importar el `pom.xml`, o usa el comando de terminal para iniciar una JVM Java 17 con el classpath de Maven. La primera ejecución descarga el complemento y las dependencias. Usa `0` para volver desde un submenú y `0` en el menú principal para salir.

```bash
mvn compile org.codehaus.mojo:exec-maven-plugin:3.5.0:exec "-Dexec.executable=java" "-Dexec.args=-classpath %classpath com.carmotors.Main"
```

El script consolidado crea clientes → vehículos → inventario → servicios → recordatorios → facturas. Los archivos de `database/bloques/` conservan el DDL de cada equipo. El bloque original de Clientes también contiene Recordatorios y requiere separarlos para una instalación desde cero. El script consolidado usa el esquema seleccionado, crea tablas y restricciones, y está destinado a una instalación nueva. Una actualización de una base existente requiere su propia migración.

## Módulos y arquitectura

| Módulo | Estado de la consola en esta rama |
| --- | --- |
| Clientes | Activa en la opción 1. Registra clientes, muestra su ID, permite buscarlos por ID o documento, listarlos y actualizar su contacto. |
| Vehículos | Registro y consultas con la vista de #67. |
| Servicios y Técnicos | Órdenes, asignaciones y movimientos de repuestos. |
| Inventario y Proveedores | Vistas activas en las opciones 4 y 5. Incluyen registro, consultas, movimientos de stock y actualización del contacto del proveedor. |
| Recordatorios y Alertas | Vista candidata en #61, pendiente de validación JDBC e integración. |
| Facturación | Vista y DAO en #68, pendientes de validar con JDBC e integrar al menú. |

`Main` abre `vista.MenuPrincipal`, que comparte un único `Scanner` con las vistas. Los controladores delegan en las clases de servicio y los DAO ejecutan SQL mediante JDBC. Servicios coordina la transacción y construye el adaptador de Inventario con esa misma conexión. La reserva descuenta unidades disponibles, el consumo las mueve al saldo usado y la liberación devuelve el pendiente. Al cancelar, Servicios bloquea la orden y confirma a la vez el estado y la devolución. La primera reserva fija el precio de la línea.

## Pruebas

```bash
mvn clean verify
```

Para ejecutar también las pruebas JDBC, configura `CARMOTORS_TEST_DB_URL` con la URL del servidor (por ejemplo, `jdbc:mysql://localhost:3306/`), `CARMOTORS_TEST_DB_USER` y `CARMOTORS_TEST_DB_PASSWORD`. Las pruebas crean esquemas aleatorios desechables y eliminan los que crearon. Sin URL de prueba, las pruebas JDBC se marcan como omitidas. Los informes de JUnit quedan en `target/surefire-reports` y GitHub Actions los adjunta a su ejecución con MySQL 8.0.

Consulta el [contrato de consola y la lista de incorporación de vistas](docs/navegacion-consola.md) y el [informe de integración](docs/integracion-final.md).

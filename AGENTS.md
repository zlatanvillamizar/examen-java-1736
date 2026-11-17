# Instrucciones para Codex — módulo de Zlatan (CarMotors)

## Objetivo
Implementar la parte asignada a Zlatan en el proyecto académico CarMotors (Java 17, Maven, JDBC, MySQL): gestión de servicios y técnicos. Referencia: issue #16 y diagrama entidad-relación aprobado. Trabajar en `feature/servicios-database` (crear desde `develop` si no existe). **No fusionar a main ni a develop automáticamente**.

## Antes de modificar
1. Inspeccionar README, estructura Maven, archivos SQL en `database/`, clases Java y configuración JDBC, y comprobar estado de las ramas.
2. Localizar el DER aprobado en el repositorio. Si no está, usar solo contratos explícitos ya presentes y **documentar dudas** en lugar de inventar campos o relaciones. No modificar tablas de Valeria, Crisbely ni Marilud.
3. Identificar las claves y nombres efectivos de `clientes`, `vehiculos`, `repuestos` y otras tablas compartidas. Verificar qué existe en main/develop y qué permanece en PR.

## SQL a implementar
En un script versionable propio de servicios (preferiblemente `database/bloques/servicios.sql`, siguiendo convenciones existentes):
- `servicio_estados`
- `mantenimiento_tipos`
- `tecnicos`
- `servicios`
- `servicios_repuestos`
- `servicios_tecnicos`

Definir PK, FK, UNIQUE, NOT NULL y tipos según DER, sin inventar columnas. Garantizar que `servicios` referencie correctamente a `vehiculos`; `servicios_repuestos` referencie a `servicios` y `repuestos`; y la tabla puente de técnicos referencie a sus entidades. Incorporar `cantidad_reservada` y `cantidad_usada` conforme a la regla de negocio acordada, con validaciones coherentes. Evitar que un script falle solo por crear una FK antes de que exista su tabla destino: documentar orden de ejecución o separar creación y constraints. No desactivar integridad referencial como solución permanente.

## Java
Siguiendo la arquitectura existente (model, repository/DAO, service, controller, view), implementar las entidades y operaciones básicas de servicios: crear una orden, consultar por ID/listar, cambiar estado y asignar técnicos/repuestos **solo cuando los contratos SQL estén confirmados**. Usar JDBC con PreparedStatement, manejo correcto de recursos y transacciones para operaciones multi-tabla. Reutilizar conexión/configuración existente; no introducir credenciales ni secretos en commits. No añadir frameworks ajenos al proyecto.

## Verificación y evidencia
- Ejecutar `mvn clean compile` y `mvn test` cuando el entorno lo permita; informar comandos, resultados y limitaciones reales.
- Probar el SQL en MySQL con un esquema de prueba, incluyendo claves foráneas, restricciones y cantidades. Si MySQL no está disponible, dejar explícito que SQL no fue ejecutado y proporcionar instrucciones de prueba; no declarar éxito ficticio.
- Commits pequeños en inglés (Conventional Commits), por ejemplo `feat(database): add service and technician tables` y `feat(services): implement service order management`.
- Push solo a `feature/servicios-database` y preparar PR hacia `develop`, enlazando issue #16; no fusionar ni sobrescribir trabajo de compañeras.
- Actualizar issue #16 con resumen, archivos modificados, commits, pruebas, bloqueos y pendientes. Mantener un registro diario veraz.

## Criterios de aceptación
Las seis tablas están definidas conforme al DER, las dependencias y reglas de reserva están validadas, el código Java compila, las operaciones implementadas tienen evidencia de prueba y los cambios están aislados en la rama de Zlatan. Ante ambigüedad del DER, detener únicamente la parte bloqueada, explicar la decisión pendiente y continuar con lo verificable.

## Protocolo para continuar si el DER no está publicado (17/11/2026)
El DER completo **no está en el repositorio** según la revisión del issue #16; no repetir una inspección interminable ni detener todo el desarrollo por ello. La prioridad es producir entregables independientes sin especular sobre columnas ni reglas.

1. En `feature/servicios-database`, primero confirmar el estado y evitar sobrescribir cambios ajenos. Si esta rama aún no contiene `AGENTS.md`, leer esta versión de `main` y seguirla. Consultar también issues #1 y #16.
2. Avanzar con piezas **sin dependencia de SQL definitivo**: clases o interfaces de dominio para operaciones de orden de servicio (solicitud, consulta, transición de estado, asignación de técnicos y repuestos), separando contratos e implementación. No introducir propiedades persistentes de campos cuya definición está pendiente. Preferir abstraer persistencia tras un DAO/puerto para evitar codificar sentencias JDBC con columnas inventadas.
3. Añadir pruebas unitarias de reglas independientes del DER, usando solamente reglas confirmadas por la especificación. Si se requiere JUnit, incorporarlo de forma mínima y compatible con Java 17/Maven. Para reglas aún no acordadas, redactar escenarios **propuestos, pendientes de aprobación**, no afirmar que ya son requisitos.
4. Elaborar `docs/contratos-servicios-pendientes.md` (o ubicación coherente con el repo) con matriz de las seis tablas: campos, tipo, PK, FK, NOT NULL, UNIQUE y origen de cada decisión; dejar `PENDIENTE DE DER` en valores desconocidos. Identificar específicamente PK y tipo de `repuestos`, y semántica/transiciones de `cantidad_reservada` y `cantidad_usada` (reservar, consumir, cancelar, devolución y manejo del stock). Documentar orden de dependencias.
5. No inventar ni publicar DDL definitivo mientras falte el contrato. Si el código preparado es solo interfaz/esqueleto, indicarlo expresamente y nunca afirmar que hay módulo funcional completo.
6. Para pruebas: verificar `./mvnw` o `mvnw.cmd` si existen; si no hay Maven instalado, no reportar compilación exitosa. Proponer GitHub Actions con Java 17 + Maven para validar el proyecto, pero no desplegar ni instalar herramientas en sistemas ajenos sin autorización. Si no hay MySQL, distinguir validación estática y ejecución real; no fingir ejecución de SQL.
7. Mantener commits descriptivos y pequeños SOLO en la rama de Zlatan; crear PR **draft** a `develop` solo si hay cambios reales que revisar, indicando los bloqueos. Actualizar issue #16 con evidencia real. No cambiar `main`, `develop` ni código de las compañeras.
8. Seguridad: `ConexionFactory.java` contiene una contraseña literal en el código compartido; no repetirla en documentación, logs ni pruebas. Si se aborda, proponer externalizar por variables de entorno en cambio independiente y coordinado, evitando tocar el archivo compartido como parte del módulo sin revisión.

**Resultado mínimo exigible antes de volver a contestar «no puedo avanzar»:** contrato de decisiones pendientes versionado, estructura Java no acoplada a campos inventados, pruebas unitarias viables y al menos un commit trazable. Informar bloqueos exactos, sin culpar a la falta del DER por trabajo independiente que sí puede completarse.

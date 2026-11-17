# Recuperación del lunes 16/11/2026 — pruebas e integración

Trabajo realizado el **17/11/2026**, correspondiente a tareas planificadas para el lunes 16. Issues #16 y #21.

## Entregables

- Contratos de dominio y operaciones en `src/main/java/com/carmotors/servicios/`.
- Pruebas JUnit 5 de identificadores positivos, inválidos e igualdad.
- Configuración de JUnit Jupiter y Maven Surefire en `pom.xml` de la rama feature.

## Validación reproducible

Desde la raíz del proyecto, con Java 17 y Maven instalados:

```bash
mvn clean compile
mvn test
```

**Estado:** comandos pendientes de ejecutar en un entorno con Maven. Las pruebas están escritas pero no se declaran aprobadas sin una ejecución. La implementación JDBC y el SQL de las seis tablas siguen pendientes del DER aprobado; tampoco hay ejecución de MySQL.

## Integración posterior

1. Revisar y aprobar el DER y reglas de stock.
2. Implementar `database/bloques/servicios.sql` según contrato real.
3. Crear implementación JDBC de `OperacionesServicio` sin modificar módulos ajenos.
4. Ejecutar pruebas de integración en base de datos de prueba.
5. Revisar PR draft hacia `develop` y fusionar solo tras aprobación.

No se han alterado las fechas de commits para simular trabajo realizado anteriormente.

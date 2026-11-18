package com.carmotors.dao;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Comprueba que las pruebas de integración solo usan y borran bases desechables propias. */
@EnabledIfEnvironmentVariable(named = "CARMOTORS_TEST_DB_URL", matches = ".+")
class EsquemaInventarioPruebaTest {

    private static boolean existe(Connection conexion, String base) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = ?")) {
            sentencia.setString(1, base);
            try (ResultSet fila = sentencia.executeQuery()) {
                fila.next();
                return fila.getInt(1) == 1;
            }
        }
    }

    private static List<String> tablas(Connection conexion, String base) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? ORDER BY TABLE_NAME")) {
            sentencia.setString(1, base);
            try (ResultSet filas = sentencia.executeQuery()) {
                List<String> nombres = new java.util.ArrayList<>();
                while (filas.next()) {
                    nombres.add(filas.getString(1));
                }
                return nombres;
            }
        }
    }

    @Test
    void creaUnaBaseNuevaConLasSeisTablasYLaEliminaAlTerminar() throws Exception {
        EsquemaInventarioPrueba esquema = EsquemaInventarioPrueba.crear();
        String nombre = esquema.nombre();
        try (Connection conexion = esquema.conectar()) {
            assertTrue(nombre.matches("carmotors_prueba_[0-9a-f]{16}"));
            assertEquals(nombre, conexion.getCatalog());
            assertEquals(List.of("evaluaciones_proveedor", "lotes", "proveedores",
                    "proveedores_repuestos", "repuesto_tipos", "repuestos"), tablas(conexion, nombre));

            esquema.close();

            assertFalse(existe(conexion, nombre), "La base desechable debía eliminarse");
        } finally {
            esquema.close();
        }
    }

    @Test
    void cadaEjecucionUsaUnaBaseDistinta() throws Exception {
        try (EsquemaInventarioPrueba primera = EsquemaInventarioPrueba.crear();
             EsquemaInventarioPrueba segunda = EsquemaInventarioPrueba.crear()) {
            assertNotEquals(primera.nombre(), segunda.nombre());
        }
    }

    @Test
    void noPermiteConectarseDespuesDeEliminarla() throws Exception {
        EsquemaInventarioPrueba esquema = EsquemaInventarioPrueba.crear();
        esquema.close();

        assertThrows(IllegalStateException.class, esquema::conectar);
    }
}

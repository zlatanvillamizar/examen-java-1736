package com.carmotors.dao;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

final class EsquemaMaryPrueba implements AutoCloseable {
    private final EsquemaInventarioPrueba esquema;

    private EsquemaMaryPrueba(EsquemaInventarioPrueba esquema) {
        this.esquema = esquema;
    }

    static EsquemaMaryPrueba crear() throws IOException, SQLException {
        return new EsquemaMaryPrueba(EsquemaInventarioPrueba.crearConsolidado());
    }

    Connection conectar() throws SQLException {
        return esquema.conectar();
    }

    int insertar(String sql) throws SQLException {
        try (var con = conectar(); var st = con.createStatement()) {
            st.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
            try (var claves = st.getGeneratedKeys()) {
                if (!claves.next()) {
                    throw new SQLException("La inserción no devolvió un ID");
                }
                return claves.getInt(1);
            }
        }
    }

    @Override
    public void close() throws SQLException {
        esquema.close();
    }
}

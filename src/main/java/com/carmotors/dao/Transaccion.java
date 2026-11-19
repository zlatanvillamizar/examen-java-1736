package com.carmotors.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Ejecuta un trabajo JDBC en una transacción propia: obtiene la conexión, desactiva
 * {@code autoCommit}, confirma si todo sale bien y hace {@code rollback} completo ante
 * cualquier excepción ({@link SQLException}, {@link RuntimeException} o {@link Error}).
 * Siempre cierra la conexión.
 *
 * <p>La excepción original nunca se pierde: si también fallan el {@code rollback} o la
 * restauración de {@code autoCommit}, esos errores se agregan como suprimidos.</p>
 */
public final class Transaccion {

    /** Trabajo que se ejecuta con la conexión de la transacción. */
    @FunctionalInterface
    public interface Trabajo<T> {
        T ejecutar(Connection conexion) throws SQLException;
    }

    private Transaccion() {
    }

    public static <T> T ejecutar(FuenteConexion fuente, Trabajo<T> trabajo) throws SQLException {
        Objects.requireNonNull(fuente, "La fuente de conexiones es obligatoria");
        Objects.requireNonNull(trabajo, "El trabajo de la transacción es obligatorio");
        try (Connection conexion = fuente.obtener()) {
            boolean autoCommitOriginal = conexion.getAutoCommit();
            conexion.setAutoCommit(false);
            T resultado;
            try {
                resultado = trabajo.ejecutar(conexion);
                conexion.commit();
            } catch (Throwable error) {
                deshacer(conexion, error);
                restaurarAutoCommit(conexion, autoCommitOriginal, error);
                throw error;
            }
            restaurarAutoCommit(conexion, autoCommitOriginal, null);
            return resultado;
        }
    }

    private static void deshacer(Connection conexion, Throwable original) {
        try {
            conexion.rollback();
        } catch (SQLException | RuntimeException errorRollback) {
            original.addSuppressed(errorRollback);
        }
    }

    /**
     * Restaura {@code autoCommit}. Si falla tras un commit correcto no se propaga: los
     * datos ya están confirmados y la conexión se cierra a continuación.
     */
    private static void restaurarAutoCommit(Connection conexion, boolean valor, Throwable original) {
        try {
            conexion.setAutoCommit(valor);
        } catch (SQLException | RuntimeException errorRestaurar) {
            if (original != null) {
                original.addSuppressed(errorRestaurar);
            }
        }
    }
}

package com.carmotors.servicios.inventario;

import com.carmotors.dao.FuenteConexion;
import com.carmotors.dao.ReservaRepuestoDAO;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/**
 * Consulta de solo lectura de los repuestos reservados y usados por una orden de servicio
 * ({@code servicios_repuestos}). Separada como interfaz para probar la vista sin MySQL.
 */
@FunctionalInterface
public interface ConsultaReservas {

    /** Líneas de la orden ordenadas por repuesto; vacía si la orden no tiene repuestos. */
    List<ReservaRepuestoDAO.Linea> listarPorServicio(int servicioId) throws SQLException;

    /** Implementación JDBC: abre una conexión, lee las líneas y la cierra. */
    static ConsultaReservas jdbc(FuenteConexion fuente) {
        Objects.requireNonNull(fuente, "La fuente de conexiones es obligatoria");
        ReservaRepuestoDAO reservas = new ReservaRepuestoDAO();
        return servicioId -> {
            try (Connection conexion = fuente.obtener()) {
                return reservas.listarPorServicio(conexion, servicioId);
            }
        };
    }
}

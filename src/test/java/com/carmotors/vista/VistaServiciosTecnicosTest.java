package com.carmotors.vista;

import com.carmotors.controlador.ServicioControlador;
import com.carmotors.dao.ServicioDAO;
import com.carmotors.dao.TecnicoDAO;
import com.carmotors.modelo.Tecnico;
import com.carmotors.servicio.taller.ServicioService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VistaServiciosTecnicosTest {
    @Test
    void consultaTecnicosYVuelveAServicios() {
        String salida = ejecutar(new TecnicosEnMemoria(), "8", "1", "1", "1", "99", "2", "0", "0");
        assertTrue(salida.contains("Técnico 1 | Aurora"));
        assertTrue(salida.contains("Técnico 2 | Brisa"));
        assertTrue(salida.contains("No se encontró el técnico 99."));
        assertTrue(salida.lastIndexOf("=== Servicios ===") > salida.lastIndexOf("=== Técnicos ==="));
    }

    @Test
    void listaSoloActivosYActualizaElEstado() {
        String salida = ejecutar(new TecnicosEnMemoria(), "8", "3", "4", "1", "d", "3",
                "4", "1", "a", "3", "4", "99", "a", "0", "0");
        assertTrue(salida.contains("Técnico desactivado."));
        assertTrue(salida.contains("No hay técnicos activos."));
        assertTrue(salida.contains("Técnico activado."));
        assertTrue(salida.contains("Técnico 1 | Aurora"));
        assertTrue(salida.contains("No se encontró el técnico 99."));
        assertFalse(salida.contains("Técnico 2 | Brisa"));
    }

    @Test
    void mantieneElSubmenuTrasEntradasInvalidasYUnErrorDeDatos() {
        TecnicoDAO tecnicos = new TecnicosEnMemoria() {
            @Override
            public List<Tecnico> listar() throws SQLException {
                throw new SQLException("Detalle interno simulado");
            }
        };
        String salida = ejecutar(tecnicos, "8", "9", "1", "abc", "1", "0", "4", "1", "x",
                "2", "3", "0", "0");
        assertTrue(salida.contains("Opción inválida."));
        assertTrue(salida.contains("debe ser un número entero."));
        assertTrue(salida.contains("debe ser positivo"));
        assertTrue(salida.contains("La respuesta debe ser a o d."));
        assertTrue(salida.contains("Error de base de datos."));
        assertFalse(salida.contains("Detalle interno simulado"));
        assertTrue(salida.indexOf("Técnico 1 | Aurora") > salida.indexOf("Error de base de datos."));
    }

    @Test
    void terminaSinGuardarCuandoSeAgotaLaEntradaEnUnaOperacion() {
        TecnicosEnMemoria tecnicos = new TecnicosEnMemoria();
        String salida = ejecutar(tecnicos, "8", "4", "1");
        assertTrue(salida.contains("¿Activar o desactivar?"));
        assertFalse(salida.contains("Técnico desactivado."));
        assertTrue(tecnicos.buscarPorId(1).orElseThrow().activo());
    }

    private String ejecutar(TecnicoDAO tecnicos, String... entrada) {
        var bytes = new ByteArrayOutputStream();
        try (var scanner = new Scanner(String.join("\n", entrada));
             var salida = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            var controlador = new ServicioControlador(new ServicioService(new ServicioDAO(), tecnicos));
            new VistaServicios(scanner, salida, controlador).mostrar();
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static class TecnicosEnMemoria extends TecnicoDAO {
        private Tecnico primero = new Tecnico(1, "Aurora", null, true);
        private final Tecnico segundo = new Tecnico(2, "Brisa", "Eléctrica", false);

        @Override
        public Optional<Tecnico> buscarPorId(int id) {
            return List.of(primero, segundo).stream().filter(tecnico -> tecnico.id() == id).findFirst();
        }

        @Override
        public List<Tecnico> listar() throws SQLException {
            return List.of(primero, segundo);
        }

        @Override
        public List<Tecnico> listarActivos() {
            return primero.activo() ? List.of(primero) : List.of();
        }

        @Override
        public boolean actualizarActivo(int id, boolean activo) {
            if (id != primero.id()) {
                return false;
            }
            primero = new Tecnico(id, primero.nombre(), primero.especialidad(), activo);
            return true;
        }
    }
}

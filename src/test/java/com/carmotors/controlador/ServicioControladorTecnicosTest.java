package com.carmotors.controlador;

import com.carmotors.dao.ServicioDAO;
import com.carmotors.dao.TecnicoDAO;
import com.carmotors.modelo.Tecnico;
import com.carmotors.servicio.taller.ServicioService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServicioControladorTecnicosTest {
    private final ServicioControlador controlador =
            new ServicioControlador(new ServicioService(new ServicioDAO(), new TecnicosEnMemoria()));

    @Test
    void consultaYFiltraTecnicosPorEstado() throws Exception {
        assertEquals(Optional.of(new Tecnico(1, "Tecnico Demo", null, true)), controlador.buscarTecnico(1));
        assertTrue(controlador.buscarTecnico(99).isEmpty());
        assertEquals(1, controlador.listarTecnicosActivos().size());

        assertTrue(controlador.actualizarTecnicoActivo(1, false));
        assertFalse(controlador.buscarTecnico(1).orElseThrow().activo());
        assertTrue(controlador.listarTecnicosActivos().isEmpty());
        assertEquals(1, controlador.listarTecnicos().size());
        assertFalse(controlador.actualizarTecnicoActivo(99, true));
        assertTrue(controlador.actualizarTecnicoActivo(1, true));
        assertTrue(controlador.buscarTecnico(1).orElseThrow().activo());
        assertEquals(1, controlador.listarTecnicosActivos().size());
    }

    @Test
    void rechazaIdentificadoresNoPositivos() {
        assertThrows(IllegalArgumentException.class, () -> controlador.buscarTecnico(0));
        assertThrows(IllegalArgumentException.class, () -> controlador.actualizarTecnicoActivo(-1, true));
    }

    private static final class TecnicosEnMemoria extends TecnicoDAO {
        private Tecnico tecnico = new Tecnico(1, "Tecnico Demo", null, true);

        @Override
        public Optional<Tecnico> buscarPorId(int id) {
            return id == tecnico.id() ? Optional.of(tecnico) : Optional.empty();
        }

        @Override
        public List<Tecnico> listar() {
            return List.of(tecnico);
        }

        @Override
        public List<Tecnico> listarActivos() {
            return tecnico.activo() ? List.of(tecnico) : List.of();
        }

        @Override
        public boolean actualizarActivo(int id, boolean activo) {
            if (id != tecnico.id()) {
                return false;
            }
            tecnico = new Tecnico(id, tecnico.nombre(), tecnico.especialidad(), activo);
            return true;
        }
    }
}

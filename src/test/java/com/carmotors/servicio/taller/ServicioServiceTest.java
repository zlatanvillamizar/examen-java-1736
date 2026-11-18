package com.carmotors.servicio.taller;

import com.carmotors.dao.ServicioDAO;
import com.carmotors.dao.TecnicoDAO;
import com.carmotors.modelo.Servicio;
import com.carmotors.modelo.ServicioTecnico;
import com.carmotors.modelo.Tecnico;
import com.carmotors.servicio.taller.dominio.IdentificadorServicio;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServicioServiceTest {
    private static final LocalDateTime RECEPCION = LocalDateTime.of(2026, 11, 17, 10, 0);
    private final ServiciosEnMemoria servicios = new ServiciosEnMemoria();
    private Tecnico tecnico = new Tecnico(2, "Ana", null, true);
    private final ServicioService servicio = new ServicioService(servicios, new TecnicoDAO() {
        @Override
        public Optional<Tecnico> buscarPorId(int id) {
            return Optional.ofNullable(tecnico).filter(resultado -> resultado.id() == id);
        }
    });

    @Test
    void rechazaIdentificadorInvalido() {
        assertThrows(IllegalArgumentException.class, () -> servicio.buscarPorId(0));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.registrarServicio(new Servicio(null, -1, 1, RECEPCION,
                        null, "Revisión", null, 1)));
        assertNull(servicios.registrado);
    }

    @Test
    void rechazaDescripcionVacia() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio.registrarServicio(solicitud("  ", null, null)));
        assertNull(servicios.registrado);
    }

    @Test
    void rechazaCostoNegativo() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio.registrarServicio(solicitud("Revisión", BigDecimal.ONE.negate(), null)));
        assertNull(servicios.registrado);
    }

    @Test
    void rechazaEntregaAnteriorALaRecepcion() {
        LocalDateTime anterior = RECEPCION.minusMinutes(1);
        assertThrows(IllegalArgumentException.class,
                () -> servicio.registrarServicio(solicitud("Revisión", null, anterior)));
        assertThrows(IllegalArgumentException.class, () -> servicio.actualizarFechaEntrega(1, anterior));
        assertNull(servicios.registrado);
        assertNull(servicios.entrega);
    }

    @Test
    void registraUnServicioValidoConDatosOpcionalesPendientes() throws Exception {
        Servicio solicitud = solicitud("Revisión", null, null);
        assertEquals(new IdentificadorServicio(1), servicio.registrarServicio(solicitud));
        assertEquals(solicitud, servicios.registrado);
    }

    @Test
    void actualizaUnaFechaDeEntregaValida() throws Exception {
        LocalDateTime entrega = RECEPCION.plusHours(1);
        servicio.actualizarFechaEntrega(1, entrega);
        assertEquals(entrega, servicios.entrega);
    }

    @Test
    void asignaUnTecnicoActivo() throws Exception {
        ServicioTecnico asignacion = new ServicioTecnico(1, 2, RECEPCION);
        servicio.asignarTecnico(asignacion);
        assertEquals(asignacion, servicios.asignacion);
    }

    @Test
    void rechazaTecnicoInactivoOInexistente() {
        tecnico = new Tecnico(2, "Ana", null, false);
        assertThrows(IllegalStateException.class,
                () -> servicio.asignarTecnico(new ServicioTecnico(1, 2, RECEPCION)));
        assertThrows(NoSuchElementException.class,
                () -> servicio.asignarTecnico(new ServicioTecnico(1, 3, RECEPCION)));
        assertNull(servicios.asignacion);
    }

    private static Servicio solicitud(String descripcion, BigDecimal costo, LocalDateTime entrega) {
        return new Servicio(null, 1, 1, RECEPCION, entrega, descripcion, costo, 1);
    }

    private static final class ServiciosEnMemoria extends ServicioDAO {
        private Servicio registrado;
        private LocalDateTime entrega;
        private ServicioTecnico asignacion;

        @Override
        public IdentificadorServicio registrar(Servicio solicitud) {
            registrado = solicitud;
            return new IdentificadorServicio(1);
        }

        @Override
        public Optional<Servicio> buscarPorId(IdentificadorServicio id) {
            return id.valor() == 1
                    ? Optional.of(new Servicio(1, 1, 1, RECEPCION, null, "Revisión", null, 1))
                    : Optional.empty();
        }

        @Override
        public boolean actualizarFechaEntrega(IdentificadorServicio id, LocalDateTime fechaEntrega) {
            entrega = fechaEntrega;
            return true;
        }

        @Override
        public boolean asignarTecnico(ServicioTecnico solicitud) {
            asignacion = solicitud;
            return true;
        }
    }
}

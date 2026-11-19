package com.carmotors.dao;

import com.carmotors.modelo.Repuesto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba {@link RepuestoDAO} contra un MySQL real de <b>prueba</b>.
 * Usa una base desechable propia (ver {@link EsquemaInventarioPrueba}) y solo se ejecuta si existe {@code CARMOTORS_TEST_DB_URL}.
 */
@EnabledIfEnvironmentVariable(named = "CARMOTORS_TEST_DB_URL", matches = ".+")
class RepuestoDAOIntegracionTest {

    private EsquemaInventarioPrueba esquema;
    private RepuestoDAO dao;

    /** Cada prueba usa una base desechable nueva; nunca toca una base existente. */
    @BeforeEach
    void crearBaseDesechable() throws SQLException, IOException {
        esquema = EsquemaInventarioPrueba.crear();
        esquema.ejecutar("INSERT INTO repuesto_tipos (nombre) VALUES ('Filtros')");
        dao = new RepuestoDAO(esquema::conectar);
    }

    @AfterEach
    void eliminarBaseDesechable() throws SQLException {
        if (esquema != null) {
            esquema.close();
        }
    }

    private Repuesto guardar(String nombre, int stock) throws SQLException {
        return dao.registrar(Repuesto.nuevo(nombre, "Bosch", 1, new BigDecimal("25000.00"), stock, 2));
    }

    @Test
    void registraYBuscaPorId() throws SQLException {
        Repuesto guardado = guardar("Filtro de aceite", 10);

        Optional<Repuesto> encontrado = dao.buscarPorId(guardado.id());

        assertEquals(Optional.of(guardado), encontrado);
    }

    @Test
    void buscarUnIdInexistenteDevuelveVacio() throws SQLException {
        assertTrue(dao.buscarPorId(999).isEmpty());
    }

    @Test
    void listaEnOrdenDeId() throws SQLException {
        Repuesto primero = guardar("Filtro de aceite", 10);
        Repuesto segundo = guardar("Bujía", 4);

        assertEquals(List.of(primero, segundo), dao.listar());
    }

    @Test
    void laEntradaSumaAlStock() throws SQLException {
        Repuesto repuesto = guardar("Filtro de aceite", 10);

        assertEquals(15, dao.registrarEntrada(repuesto.id(), 5));
        assertEquals(15, dao.buscarPorId(repuesto.id()).orElseThrow().cantidadStock());
    }

    @Test
    void laSalidaRestaDelStockHastaCero() throws SQLException {
        Repuesto repuesto = guardar("Filtro de aceite", 10);

        assertEquals(4, dao.registrarSalida(repuesto.id(), 6));
        assertEquals(0, dao.registrarSalida(repuesto.id(), 4));
    }

    @Test
    void laSalidaNoDejaElStockNegativoYNoCambiaNada() throws SQLException {
        Repuesto repuesto = guardar("Filtro de aceite", 3);

        StockInsuficienteException error =
                assertThrows(StockInsuficienteException.class, () -> dao.registrarSalida(repuesto.id(), 5));

        assertEquals(3, error.getDisponible());
        assertEquals(5, error.getSolicitado());
        assertEquals(3, dao.buscarPorId(repuesto.id()).orElseThrow().cantidadStock());
    }

    @Test
    void losMovimientosSobreUnRepuestoInexistenteFallan() {
        assertThrows(RepuestoNoEncontradoException.class, () -> dao.registrarEntrada(999, 1));
        assertThrows(RepuestoNoEncontradoException.class, () -> dao.registrarSalida(999, 1));
    }

    @Test
    void noRegistraUnRepuestoConTipoInexistente() {
        Repuesto conTipoInexistente =
                Repuesto.nuevo("Filtro", "Bosch", 99, new BigDecimal("1.00"), 1, 0);

        assertThrows(SQLException.class, () -> dao.registrar(conTipoInexistente));
    }
}

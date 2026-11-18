package com.carmotors.servicio.taller;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsultaOrdenServicioTest {

    private final LinkedHashMap<String, String> datos = new LinkedHashMap<>();
    private final ConsultaOrdenServicio<String, String> consulta =
            new ConsultaOrdenServicio<>(new RepositorioConsultaOrdenServicio<>() {
                @Override
                public Optional<String> buscarPorId(String id) {
                    return Optional.ofNullable(datos.get(id));
                }

                @Override
                public List<String> listar() {
                    return new ArrayList<>(datos.values());
                }
            });

    @Test
    void recuperaLaOrdenCorrespondienteAlIdentificador() {
        datos.put("primera", "Orden A");
        datos.put("segunda", "Orden B");

        assertEquals(Optional.of("Orden B"), consulta.buscarPorId("segunda"));
    }

    @Test
    void devuelveVacioCuandoLaOrdenNoExiste() {
        assertTrue(consulta.buscarPorId("ausente").isEmpty());
    }

    @Test
    void devuelveUnListadoVacioSinOrdenes() {
        assertTrue(consulta.listar().isEmpty());
    }

    @Test
    void devuelveUnaCopiaNoModificableDelListado() {
        datos.put("primera", "Orden A");
        List<String> resultado = consulta.listar();
        datos.put("segunda", "Orden B");

        assertEquals(List.of("Orden A"), resultado);
        assertThrows(UnsupportedOperationException.class, () -> resultado.add("Otra orden"));
        assertEquals(List.of("Orden A", "Orden B"), consulta.listar());
    }
}

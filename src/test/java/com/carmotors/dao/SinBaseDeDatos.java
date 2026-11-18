package com.carmotors.dao;

/** Fuente de conexión que falla si una prueba en memoria intenta usar MySQL. */
final class SinBaseDeDatos {

    static final FuenteConexion FUENTE = () -> {
        throw new AssertionError("La prueba no debe usar una base de datos real");
    };

    private SinBaseDeDatos() {
    }
}

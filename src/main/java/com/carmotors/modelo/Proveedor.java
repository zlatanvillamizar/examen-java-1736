package com.carmotors.modelo;

import java.util.regex.Pattern;

/**
 * Proveedor de repuestos. Refleja la tabla {@code proveedores} de
 * {@code database/bloques/inventario.sql}.
 *
 * <p>El {@code id} es {@code null} mientras el proveedor no se ha guardado.
 * {@code personaContacto}, {@code telefono} y {@code email} son opcionales:
 * un texto vacío se guarda como {@code null}.</p>
 */
public record Proveedor(
        Integer id,
        String nombre,
        String personaContacto,
        String telefono,
        String email) {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Proveedor {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("El id del proveedor debe ser positivo");
        }
        nombre = textoObligatorio(nombre, "nombre", 255);
        personaContacto = textoOpcional(personaContacto, "persona de contacto", 120);
        telefono = textoOpcional(telefono, "teléfono", 20);
        email = textoOpcional(email, "email", 100);
        if (email != null && !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("El email del proveedor no es válido");
        }
    }

    /** Crea un proveedor nuevo, todavía sin id. */
    public static Proveedor nuevo(String nombre, String personaContacto, String telefono, String email) {
        return new Proveedor(null, nombre, personaContacto, telefono, email);
    }

    /** Devuelve una copia con el id asignado por la base de datos. */
    public Proveedor conId(int nuevoId) {
        return new Proveedor(nuevoId, nombre, personaContacto, telefono, email);
    }

    private static String textoObligatorio(String valor, String campo, int longitudMaxima) {
        String limpio = textoOpcional(valor, campo, longitudMaxima);
        if (limpio == null) {
            throw new IllegalArgumentException("El campo " + campo + " es obligatorio");
        }
        return limpio;
    }

    private static String textoOpcional(String valor, String campo, int longitudMaxima) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpio = valor.strip();
        if (limpio.length() > longitudMaxima) {
            throw new IllegalArgumentException(
                    "El campo " + campo + " no puede superar " + longitudMaxima + " caracteres");
        }
        return limpio;
    }
}

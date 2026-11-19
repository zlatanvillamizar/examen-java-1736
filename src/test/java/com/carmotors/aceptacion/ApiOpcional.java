package com.carmotors.aceptacion;

import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Acceso a API de ramas en revisión que todavía no existen en develop. */
final class ApiOpcional {
    private ApiOpcional() {
    }

    static Class<?> clase(String nombre) throws ClassNotFoundException {
        try {
            return Class.forName(nombre);
        } catch (ClassNotFoundException e) {
            assumeTrue(false, "Módulo ausente de la compilación: " + nombre);
            throw e;
        }
    }

    static Object invocar(Object instancia, String metodo, Class<?>[] tipos, Object... argumentos) throws Exception {
        try {
            return instancia.getClass().getMethod(metodo, tipos).invoke(instancia, argumentos);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception causa) {
                throw causa;
            }
            throw e;
        }
    }
}

package com.example.smartinsulinguardian;

import androidx.annotation.ColorRes;
import androidx.annotation.StringRes;

/**
 * Estados posibles de la conexion MQTT con el broker.
 * Provee metodos para obtener los recursos de texto y color
 * asociados a cada estado, evitando duplicar logica en las pantallas.
 */
public enum EstadoConexion {
    CONECTANDO(R.string.conexion_conectando, R.color.conexion_conectando),
    CONECTADO(R.string.conexion_conectado, R.color.conexion_conectado),
    RECONECTANDO(R.string.conexion_reconectando, R.color.conexion_conectando),
    ERROR(R.string.conexion_error, R.color.conexion_error);

    @StringRes
    private final int recursoTexto;

    @ColorRes
    private final int recursoColor;

    EstadoConexion(@StringRes int recursoTexto, @ColorRes int recursoColor) {
        this.recursoTexto = recursoTexto;
        this.recursoColor = recursoColor;
    }

    @StringRes
    public int getRecursoTexto() {
        return recursoTexto;
    }

    @ColorRes
    public int getRecursoColor() {
        return recursoColor;
    }
}

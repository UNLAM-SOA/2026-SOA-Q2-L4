package com.example.smartinsulinguardian;

/**
 * Envuelve el resultado de un comando publicado al broker.
 * Se usa como evento de un solo consumo a traves de LiveData
 * para evitar que un Toast se muestre dos veces tras una
 * recreacion de la Activity (por ejemplo, al rotar la pantalla).
 */
public class ResultadoComando {

    private final String comando;
    private final boolean exitoso;
    private boolean consumido;

    public ResultadoComando(String comando, boolean exitoso) {
        this.comando = comando;
        this.exitoso = exitoso;
        this.consumido = false;
    }

    public String getComando() {
        return comando;
    }

    public boolean isExitoso() {
        return exitoso;
    }

    /**
     * Marca el resultado como consumido y devuelve true.
     * Si ya fue consumido, devuelve false sin efecto.
     * Debe llamarse desde el hilo principal (observer de LiveData).
     */
    public boolean consumir() {
        if (consumido) {
            return false;
        }
        consumido = true;
        return true;
    }
}

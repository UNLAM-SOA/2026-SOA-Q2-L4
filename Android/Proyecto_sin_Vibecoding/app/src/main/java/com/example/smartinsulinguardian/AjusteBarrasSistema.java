package com.example.smartinsulinguardian;

import android.app.Activity;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Aplica a las pantallas el espacio seguro de las barras del sistema y del recorte de pantalla. */
final class AjusteBarrasSistema {

    private AjusteBarrasSistema() {
    }

    /** Evita que el contenido quede debajo de las barras superior e inferior del dispositivo. */
    static void aplicar(Activity actividad) {
        View contenido = actividad.findViewById(android.R.id.content);
        int paddingIzquierdo = contenido.getPaddingLeft();
        int paddingSuperior = contenido.getPaddingTop();
        int paddingDerecho = contenido.getPaddingRight();
        int paddingInferior = contenido.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(contenido, (vista, insets) -> {
            Insets barras = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            vista.setPadding(
                    paddingIzquierdo + barras.left,
                    paddingSuperior + barras.top,
                    paddingDerecho + barras.right,
                    paddingInferior + barras.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(contenido);
    }
}

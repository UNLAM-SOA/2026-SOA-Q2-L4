package com.example.smartinsulinguardian;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * Pantalla del grafico de temperatura. En la Version 2 se alimenta con lecturas
 * simuladas; en la Version 3 las lecturas llegaran por MQTT y se mostraran
 * llamando a agregarLectura() desde el hilo principal.
 */
public class GraficoActivity extends AppCompatActivity {

    private static final long INTERVALO_SIMULACION_MS = 1000L;
    private static final float TEMP_SIMULADA_INICIAL_C = 5f;
    private static final float TEMP_SIMULADA_MIN_C = 1f;
    private static final float TEMP_SIMULADA_MAX_C = 11f;
    private static final float VARIACION_MAX_SIMULADA_C = 1.5f;

    private GraficoTemperaturaView grafico;
    private TextView tvUltimaLectura;

    private final Handler manejador = new Handler(Looper.getMainLooper());
    private final Random aleatorio = new Random();
    private final SimpleDateFormat formatoHora =
            new SimpleDateFormat(GraficoTemperaturaView.FORMATO_HORA, Locale.getDefault());
    private float temperaturaSimulada = TEMP_SIMULADA_INICIAL_C;

    private final Runnable simulador = new Runnable() {
        @Override
        public void run() {
            generarLecturaSimulada();
            manejador.postDelayed(this, INTERVALO_SIMULACION_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_grafico);

        grafico = findViewById(R.id.graficoTemperatura);
        tvUltimaLectura = findViewById(R.id.tvUltimaLectura);
    }

    @Override
    protected void onResume() {
        super.onResume();
        manejador.post(simulador);
    }

    @Override
    protected void onPause() {
        super.onPause();
        manejador.removeCallbacks(simulador);
    }

    /** Genera una lectura de prueba (variacion aleatoria acotada) y la muestra. */
    private void generarLecturaSimulada() {
        float variacion = (aleatorio.nextFloat() * 2f - 1f) * VARIACION_MAX_SIMULADA_C;
        temperaturaSimulada = Math.max(TEMP_SIMULADA_MIN_C,
                Math.min(TEMP_SIMULADA_MAX_C, temperaturaSimulada + variacion));

        agregarLectura(temperaturaSimulada);
    }

    /**
     * Agrega una lectura al grafico con la hora actual y actualiza el texto de la
     * ultima lectura. Con MQTT (V3) se usaria la hora de recepcion del mensaje.
     */
    private void agregarLectura(float temperatura) {
        long ahora = System.currentTimeMillis();

        grafico.agregarValor(temperatura, ahora);
        tvUltimaLectura.setText(getString(R.string.ultima_lectura,
                temperatura, formatoHora.format(new Date(ahora))));
    }
}

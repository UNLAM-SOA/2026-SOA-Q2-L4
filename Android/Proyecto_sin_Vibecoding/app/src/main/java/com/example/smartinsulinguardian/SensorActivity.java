package com.example.smartinsulinguardian;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Pantalla del acelerometro: detecta una sacudida del celular y alterna el estado
 * de la alarma entre silenciada y activa. En la Version 2 el efecto es solo en
 * pantalla; en la Version 3 se publicaran los comandos SILENCIAR y REACTIVAR por
 * MQTT hacia el sistema embebido.
 */
public class SensorActivity extends AppCompatActivity implements SensorEventListener {

    private static final int INDICE_EJE_X = 0;
    private static final int INDICE_EJE_Y = 1;
    private static final int INDICE_EJE_Z = 2;

    // Una sacudida son varios picos de aceleracion seguidos y separados entre si
    private static final float UMBRAL_SACUDIDA_G = 2.5f;
    private static final int PICOS_PARA_SACUDIDA = 3;
    private static final long SEPARACION_MIN_PICOS_MS = 100L;
    private static final long VENTANA_PICOS_MS = 800L;
    private static final long ESPERA_ENTRE_ACCIONES_MS = 1500L;
    private static final long INTERVALO_ACTUALIZAR_VALORES_MS = 200L;

    private SensorManager sensorManager;
    private Sensor acelerometro;

    private TextView tvValores;
    private TextView tvAlarma;

    private boolean alarmaSilenciada = false;
    private int picos = 0;
    private long instanteUltimoPico = 0L;
    private long instanteUltimaAccion = 0L;
    private long instanteUltimaActualizacion = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor);

        tvValores = findViewById(R.id.tvValores);
        tvAlarma = findViewById(R.id.tvAlarma);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        acelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        if (acelerometro == null) {
            tvValores.setText(R.string.sin_acelerometro);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (acelerometro != null) {
            sensorManager.registerListener(this, acelerometro, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    /** Se invoca con cada lectura del acelerometro: muestra los ejes y evalua si hubo sacudida. */
    @Override
    public void onSensorChanged(SensorEvent evento) {
        float x = evento.values[INDICE_EJE_X];
        float y = evento.values[INDICE_EJE_Y];
        float z = evento.values[INDICE_EJE_Z];
        long ahora = SystemClock.elapsedRealtime();

        actualizarValores(x, y, z, ahora);
        evaluarSacudida(x, y, z, ahora);
    }

    /** Requerido por SensorEventListener; no se usa. */
    @Override
    public void onAccuracyChanged(Sensor sensor, int precision) {
    }

    /** Muestra los valores de los ejes, limitando la frecuencia de refresco del texto. */
    private void actualizarValores(float x, float y, float z, long ahora) {
        if (ahora - instanteUltimaActualizacion >= INTERVALO_ACTUALIZAR_VALORES_MS) {
            tvValores.setText(getString(R.string.valores_acelerometro, x, y, z));
            instanteUltimaActualizacion = ahora;
        }
    }

    /**
     * Cuenta picos de aceleracion (fuerza total en G) y, cuando hay PICOS_PARA_SACUDIDA
     * dentro de la ventana de tiempo, alterna la alarma. Tras cada accion se ignoran las
     * sacudidas durante ESPERA_ENTRE_ACCIONES_MS para no alternar dos veces por un mismo gesto.
     */
    private void evaluarSacudida(float x, float y, float z, long ahora) {
        float fuerzaG = (float) Math.sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH;

        if (fuerzaG < UMBRAL_SACUDIDA_G) {
            return;
        }
        if (ahora - instanteUltimoPico < SEPARACION_MIN_PICOS_MS) {
            return;
        }
        if (ahora - instanteUltimoPico > VENTANA_PICOS_MS) {
            picos = 0;
        }

        instanteUltimoPico = ahora;
        picos++;

        if (picos >= PICOS_PARA_SACUDIDA && ahora - instanteUltimaAccion > ESPERA_ENTRE_ACCIONES_MS) {
            picos = 0;
            instanteUltimaAccion = ahora;
            alternarAlarma();
        }
    }

    /** Silencia la alarma si esta activa, o la reactiva si estaba silenciada. */
    private void alternarAlarma() {
        if (alarmaSilenciada) {
            reactivarAlarma();
        } else {
            silenciarAlarma();
        }
    }

    /** Silencia la alarma. Version 3: aqui se publicara SILENCIAR por MQTT. */
    private void silenciarAlarma() {
        alarmaSilenciada = true;
        tvAlarma.setText(R.string.alarma_silenciada);
    }

    /** Reactiva la alarma. Version 3: aqui se publicara REACTIVAR por MQTT. */
    private void reactivarAlarma() {
        alarmaSilenciada = false;
        tvAlarma.setText(R.string.alarma_activa);
    }
}

package com.example.smartinsulinguardian;

import android.content.Intent;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pantalla principal: muestra el estado y la temperatura del sistema embebido,
 * permite silenciar la alarma y navegar al grafico de temperatura.
 * Tanto el boton de alarma como sacudir el celular publican SILENCIAR / REACTIVAR
 * en el broker MQTT.
 */
public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final String COMANDO_SILENCIAR = "SILENCIAR";
    private static final String COMANDO_REACTIVAR = "REACTIVAR";
    private static final int MQTT_QOS = 1;

    private static final int INDICE_EJE_X = 0;
    private static final int INDICE_EJE_Y = 1;
    private static final int INDICE_EJE_Z = 2;

    // Una sacudida son varios picos de aceleracion seguidos y separados entre si
    private static final float UMBRAL_SACUDIDA_G = 2.5f;
    private static final int PICOS_PARA_SACUDIDA = 3;
    private static final long SEPARACION_MIN_PICOS_MS = 100L;
    private static final long VENTANA_PICOS_MS = 800L;
    private static final long ESPERA_ENTRE_ACCIONES_MS = 1500L;

    /** Hilo de fondo para no bloquear la UI con operaciones de red. */
    private final ExecutorService ejecutorMqtt = Executors.newSingleThreadExecutor();
    /** Ultimo comando enviado con exito; la alarma arranca activa. */
    private String ultimoComando = COMANDO_REACTIVAR;

    private SensorManager sensorManager;
    private Sensor acelerometro;
    private int picos = 0;
    private long instanteUltimoPico = 0L;
    private long instanteUltimaAccion = 0L;

    private TextView tvEstado;
    private TextView tvTemperatura;
    private Button btnSilenciar;
    private Button btnGrafico;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        enlazarVistas();
        configurarBotones();

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        acelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
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

    /** Obtiene las referencias a las vistas definidas en el layout. */
    private void enlazarVistas() {
        tvEstado = findViewById(R.id.tvEstado);
        tvTemperatura = findViewById(R.id.tvTemperatura);
        btnSilenciar = findViewById(R.id.btnSilenciar);
        btnGrafico = findViewById(R.id.btnGrafico);
    }

    /** Asigna la accion de cada boton. */
    private void configurarBotones() {
        btnSilenciar.setOnClickListener(v -> alTocarBotonAlarma());
        btnGrafico.setOnClickListener(v -> abrirGrafico());
    }

    /** Envia el comando de alarma y avisa que tambien se puede hacer sacudiendo el celular. */
    private void alTocarBotonAlarma() {
        alSilenciarAlarma();
        mostrarDialogoSacudida();
    }

    /** Muestra un dialogo con un celular animado que explica el gesto de sacudir. */
    private void mostrarDialogoSacudida() {
        View contenido = getLayoutInflater().inflate(R.layout.dialogo_sacudida, null);
        ImageView ivCelular = contenido.findViewById(R.id.ivCelularSacudida);

        new AlertDialog.Builder(this)
                .setTitle(R.string.titulo_dialogo_sacudida)
                .setView(contenido)
                .setPositiveButton(R.string.boton_entendido, null)
                .show();

        Drawable animacion = ivCelular.getDrawable();
        if (animacion instanceof Animatable) {
            ((Animatable) animacion).start();
        }
    }

    /** Se invoca con cada lectura del acelerometro y evalua si hubo sacudida. */
    @Override
    public void onSensorChanged(SensorEvent evento) {
        float x = evento.values[INDICE_EJE_X];
        float y = evento.values[INDICE_EJE_Y];
        float z = evento.values[INDICE_EJE_Z];

        evaluarSacudida(x, y, z, SystemClock.elapsedRealtime());
    }

    /** Requerido por SensorEventListener; no se usa. */
    @Override
    public void onAccuracyChanged(Sensor sensor, int precision) {
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
            alSilenciarAlarma();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ejecutorMqtt.shutdownNow();
    }

    /**
     * Publica en el broker el comando opuesto al ultimo enviado:
     * SILENCIAR si la alarma estaba reactivada, REACTIVAR si estaba silenciada.
     * Se ignora si todavia hay un envio en curso.
     */
    private void alSilenciarAlarma() {
        if (!btnSilenciar.isEnabled()) {
            return;
        }
        String comando = COMANDO_REACTIVAR.equals(ultimoComando) ? COMANDO_SILENCIAR : COMANDO_REACTIVAR;
        btnSilenciar.setEnabled(false);
        ejecutorMqtt.execute(() -> {
            boolean enviado = publicarComando(comando);
            runOnUiThread(() -> alFinalizarEnvio(comando, enviado));
        });
    }

    /** Se conecta al broker, publica el comando y se desconecta. Devuelve true si se envio. */
    private boolean publicarComando(String comando) {
        MqttClient cliente = null;
        try {
            cliente = new MqttClient(ConfiguracionMqtt.BROKER_URI, MqttClient.generateClientId(), new MemoryPersistence());
            MqttConnectOptions opciones = new MqttConnectOptions();
            opciones.setCleanSession(true);
            opciones.setConnectionTimeout(ConfiguracionMqtt.TIMEOUT_CONEXION_S);
            cliente.connect(opciones);

            MqttMessage mensaje = new MqttMessage(comando.getBytes());
            mensaje.setQos(MQTT_QOS);
            cliente.publish(ConfiguracionMqtt.TOPIC_COMANDO, mensaje);
            return true;
        } catch (MqttException e) {
            return false;
        } finally {
            cerrarCliente(cliente);
        }
    }

    /** Desconecta y libera el cliente MQTT, ignorando errores al cerrar. */
    private void cerrarCliente(MqttClient cliente) {
        if (cliente == null) {
            return;
        }
        try {
            if (cliente.isConnected()) {
                cliente.disconnect();
            }
            cliente.close();
        } catch (MqttException ignorada) {
            // No hay nada mas que hacer si falla el cierre.
        }
    }

    /** Actualiza el estado y la UI segun el resultado del envio. */
    private void alFinalizarEnvio(String comando, boolean enviado) {
        btnSilenciar.setEnabled(true);
        if (!enviado) {
            Toast.makeText(this, R.string.msg_error_broker, Toast.LENGTH_SHORT).show();
            return;
        }
        ultimoComando = comando;
        btnSilenciar.setText(COMANDO_SILENCIAR.equals(comando)
                ? R.string.boton_reactivar : R.string.boton_silenciar);
        Toast.makeText(this, getString(R.string.msg_comando_enviado, comando), Toast.LENGTH_SHORT).show();
    }

    /** Abre la pantalla del grafico de temperatura mediante un Intent explicito. */
    private void abrirGrafico() {
        Intent intent = new Intent(this, GraficoActivity.class);
        startActivity(intent);
    }
}
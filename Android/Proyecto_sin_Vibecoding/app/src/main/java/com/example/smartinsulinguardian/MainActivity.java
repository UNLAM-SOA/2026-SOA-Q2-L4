package com.example.smartinsulinguardian;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

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
 * El boton de alarma publica SILENCIAR / REACTIVAR en el broker MQTT.
 */
public class MainActivity extends AppCompatActivity {

    private static final String COMANDO_SILENCIAR = "SILENCIAR";
    private static final String COMANDO_REACTIVAR = "REACTIVAR";
    private static final int MQTT_QOS = 1;

    /** Hilo de fondo para no bloquear la UI con operaciones de red. */
    private final ExecutorService ejecutorMqtt = Executors.newSingleThreadExecutor();
    /** Ultimo comando enviado con exito; la alarma arranca activa. */
    private String ultimoComando = COMANDO_REACTIVAR;

    private TextView tvEstado;
    private TextView tvTemperatura;
    private Button btnSilenciar;
    private Button btnGrafico;
    private Button btnSensor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        enlazarVistas();
        configurarBotones();
    }

    /** Obtiene las referencias a las vistas definidas en el layout. */
    private void enlazarVistas() {
        tvEstado = findViewById(R.id.tvEstado);
        tvTemperatura = findViewById(R.id.tvTemperatura);
        btnSilenciar = findViewById(R.id.btnSilenciar);
        btnGrafico = findViewById(R.id.btnGrafico);
        btnSensor = findViewById(R.id.btnSensor);
    }

    /** Asigna la accion de cada boton. */
    private void configurarBotones() {
        btnSilenciar.setOnClickListener(v -> alSilenciarAlarma());
        btnGrafico.setOnClickListener(v -> abrirGrafico());
        btnSensor.setOnClickListener(v -> abrirSensor());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ejecutorMqtt.shutdownNow();
    }

    /**
     * Publica en el broker el comando opuesto al ultimo enviado:
     * SILENCIAR si la alarma estaba reactivada, REACTIVAR si estaba silenciada.
     */
    private void alSilenciarAlarma() {
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

    /** Abre la pantalla del acelerometro mediante un Intent explicito. */
    private void abrirSensor() {
        Intent intent = new Intent(this, SensorActivity.class);
        startActivity(intent);
    }
}
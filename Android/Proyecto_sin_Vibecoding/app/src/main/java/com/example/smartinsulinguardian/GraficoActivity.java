package com.example.smartinsulinguardian;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pantalla del grafico de temperatura. Mientras esta visible se suscribe al
 * topico de temperatura del broker MQTT y agrega cada lectura recibida al grafico.
 */
public class GraficoActivity extends AppCompatActivity {

    private static final int MQTT_QOS = 0;

    private GraficoTemperaturaView grafico;
    private TextView tvUltimaLectura;

    private final SimpleDateFormat formatoHora =
            new SimpleDateFormat(GraficoTemperaturaView.FORMATO_HORA, Locale.getDefault());

    /** Hilo de fondo para las operaciones de red; el cliente solo se usa desde aqui. */
    private final ExecutorService ejecutorMqtt = Executors.newSingleThreadExecutor();
    private MqttClient cliente;

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
        ejecutorMqtt.execute(this::conectar);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ejecutorMqtt.execute(this::desconectar);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ejecutorMqtt.shutdown();
    }

    /**
     * Se conecta al broker con reconexion automatica. La suscripcion se hace en
     * connectComplete() para que se repita tras cada reconexion.
     */
    private void conectar() {
        try {
            cliente = new MqttClient(ConfiguracionMqtt.BROKER_URI,
                    MqttClient.generateClientId(), new MemoryPersistence());
            cliente.setCallback(new ReceptorTemperatura());

            MqttConnectOptions opciones = new MqttConnectOptions();
            opciones.setCleanSession(true);
            opciones.setAutomaticReconnect(true);
            opciones.setConnectionTimeout(ConfiguracionMqtt.TIMEOUT_CONEXION_S);
            cliente.connect(opciones);
        } catch (MqttException e) {
            desconectar();
            runOnUiThread(() -> Toast.makeText(this,
                    R.string.msg_error_conexion_broker, Toast.LENGTH_SHORT).show());
        }
    }

    /** Se suscribe al topico de temperatura. */
    private void suscribir() {
        try {
            if (cliente != null && cliente.isConnected()) {
                cliente.subscribe(ConfiguracionMqtt.TOPIC_TEMPERATURA, MQTT_QOS);
            }
        } catch (MqttException ignorada) {
            // Se reintenta en la proxima reconexion.
        }
    }

    /** Desconecta y libera el cliente MQTT, ignorando errores al cerrar. */
    private void desconectar() {
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
        cliente = null;
    }

    /**
     * Recibe los eventos del cliente MQTT. Corre en un hilo de Paho, por lo que
     * no llama metodos bloqueantes del cliente ni toca la UI directamente.
     */
    private class ReceptorTemperatura implements MqttCallbackExtended {

        @Override
        public void connectComplete(boolean reconexion, String uriServidor) {
            ejecutorMqtt.execute(GraficoActivity.this::suscribir);
        }

        @Override
        public void messageArrived(String topico, MqttMessage mensaje) {
            float temperatura;
            try {
                temperatura = Float.parseFloat(new String(mensaje.getPayload()).trim());
            } catch (NumberFormatException e) {
                return;
            }
            runOnUiThread(() -> agregarLectura(temperatura));
        }

        @Override
        public void connectionLost(Throwable causa) {
            // La reconexion automatica de Paho se encarga de volver a conectar.
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            // Esta pantalla no publica mensajes.
        }
    }

    /**
     * Agrega una lectura al grafico con la hora de recepcion y actualiza el texto
     * de la ultima lectura.
     */
    private void agregarLectura(float temperatura) {
        long ahora = System.currentTimeMillis();

        grafico.agregarValor(temperatura, ahora);
        tvUltimaLectura.setText(getString(R.string.ultima_lectura,
                temperatura, formatoHora.format(new Date(ahora))));
    }
}

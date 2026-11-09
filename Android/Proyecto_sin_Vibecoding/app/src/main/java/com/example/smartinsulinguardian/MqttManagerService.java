package com.example.smartinsulinguardian;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class MqttManagerService extends Service {

    private static final String TAG = "MQTT";
    private static final int MQTT_QOS = 0;

    private final ExecutorService ejecutorMqtt =
            Executors.newSingleThreadExecutor();

    private volatile boolean cerrando = false;

    private MqttClient cliente;

    // Binder para permitir conexiones locales al servicio.
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {

        public MqttManagerService getService() {
            return MqttManagerService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "Servicio MQTT creado");

        ejecutorMqtt.execute(this::conectar);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Aplicación vinculada al servicio");
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "Aplicación desvinculada del servicio");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        cerrando = true;

        Log.d(TAG, "Destruyendo servicio MQTT");

        ejecutorMqtt.execute(this::desconectar);
        ejecutorMqtt.shutdown();

        super.onDestroy();
    }

    /**
     * Establece la conexión con el broker MQTT.
     */
    private void conectar() {

        if (cerrando || cliente != null) {
            return;
        }

        try {
            cliente = new MqttClient(
                    ConfiguracionMqtt.BROKER_URI,
                    MqttClient.generateClientId(),
                    new MemoryPersistence()
            );

            cliente.setCallback(new ReceptorTemperatura());

            MqttConnectOptions opciones = new MqttConnectOptions();

            opciones.setCleanSession(true);
            opciones.setAutomaticReconnect(true);
            opciones.setConnectionTimeout(
                    ConfiguracionMqtt.TIMEOUT_CONEXION_S
            );

            cliente.connect(opciones);

            Log.d(TAG, "Conexión con el broker establecida");

        } catch (MqttException e) {
            Log.e(TAG, "Error al conectar con el broker", e);
            desconectar();
        }
    }

    /**
     * Desconecta y libera el cliente MQTT.
     */
    private void desconectar() {

        if (cliente == null) {
            return;
        }

        try {
            if (cliente.isConnected()) {
                cliente.disconnect();
            }

        } catch (MqttException e) {
            Log.e(TAG, "Error al desconectar", e);

        } finally {
            try {
                cliente.close();
            } catch (MqttException e) {
                Log.e(TAG, "Error al cerrar el cliente", e);
            }

            cliente = null;
        }

        Log.d(TAG, "Cliente MQTT desconectado");
    }

    /**
     * Se suscribe al tópico de temperatura.
     */
    private void suscribir() {

        try {
            if (!cerrando && cliente != null && cliente.isConnected()) {

                cliente.subscribe(
                        ConfiguracionMqtt.TOPIC_TEMPERATURA,
                        MQTT_QOS
                );

                Log.d(TAG, "Suscrito al tópico temperatura");
            }

        } catch (MqttException e) {
            Log.e(TAG, "Error al suscribirse", e);
        }
    }

    /**
     * Recibe los eventos del cliente MQTT.
     */
    private class ReceptorTemperatura implements MqttCallbackExtended {

        @Override
        public void connectComplete(
                boolean reconexion,
                String uriServidor) {

            if (!cerrando && !ejecutorMqtt.isShutdown()) {

                try {
                    ejecutorMqtt.execute(
                            MqttManagerService.this::suscribir
                    );

                } catch (RejectedExecutionException ignorada) {
                    // El ejecutor se está cerrando.
                }
            }
        }

        @Override
        public void messageArrived(String topico, MqttMessage mensaje) {

            if (cerrando) {
                return;
            }

            try {
                float temperatura = Float.parseFloat(
                        new String(mensaje.getPayload()).trim()
                );

                if (!Float.isFinite(temperatura)) {
                    return;
                }

                long marcaTiempoMs = System.currentTimeMillis();

                GuardianApplication app =
                        (GuardianApplication) getApplication();

                // Guardar la temperatura en el historial.
                app.getHistorialTemperaturas().agregar(
                        temperatura,
                        marcaTiempoMs
                );

                // Notificar al gráfico que hay nuevas lecturas.
                app.notificarCambioHistorial();

                Log.d(TAG, "Temperatura almacenada: " + temperatura);

            } catch (NumberFormatException e) {
                Log.w(TAG, "Temperatura inválida recibida");
            }
        }

        @Override
        public void connectionLost(Throwable causa) {
            Log.e(TAG, "Se perdió la conexión MQTT", causa);
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            // Este servicio no publica mensajes.
        }
    }
}
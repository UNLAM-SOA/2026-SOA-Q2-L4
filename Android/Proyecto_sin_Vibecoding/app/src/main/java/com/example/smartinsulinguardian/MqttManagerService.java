package com.example.smartinsulinguardian;

import android.app.Service;
import android.content.Intent;
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

    private static final int MQTT_QOS = 0;

    private final ExecutorService ejecutorMqtt = Executors.newSingleThreadExecutor();

    private volatile boolean cerrando = false;

    private MqttClient cliente;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId) {

        ejecutorMqtt.execute(this::conectar);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        cerrando = true;

        ejecutorMqtt.execute(this::desconectar);
        ejecutorMqtt.shutdown();

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Se conecta al broker con reconexion automatica. La suscripcion se hace en
     * connectComplete() para que se repita tras cada reconexion.
     */
    private void conectar() {
        if(cerrando || cliente != null){
            return;
        }

        try {
            cliente = new MqttClient(ConfiguracionMqtt.BROKER_URI,
                    MqttClient.generateClientId(), new MemoryPersistence());
            cliente.setCallback(new ReceptorTemperatura());

            MqttConnectOptions opciones = new MqttConnectOptions();
            opciones.setCleanSession(true);
            opciones.setAutomaticReconnect(true);
            opciones.setConnectionTimeout(ConfiguracionMqtt.TIMEOUT_CONEXION_S);
            cliente.connect(opciones);
            Log.d("MQTT","Conexión con el broker establecida");
        } catch (MqttException e) {
            desconectar();
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
        } catch (MqttException e) {
           Log.e("MQTT","Error al desconectar", e);
        } finally {
           try {
               cliente.close();
           } catch (MqttException e) {
               Log.e("MQTT", "Error al cerrar el cliente", e);
           }
        }
        cliente = null;
    }

    /** Se suscribe al topico de temperatura. */
    private void suscribir() {
        try {
            if (!cerrando && cliente != null && cliente.isConnected()) {
                cliente.subscribe(ConfiguracionMqtt.TOPIC_TEMPERATURA, MQTT_QOS);
                Log.d("MQTT","Suscrito al tópico temperatura");
            }
        } catch (MqttException ignorada) {
            return;
        }
    }

    /**
     * Recibe los eventos del cliente MQTT. Corre en un hilo de Paho, por lo que
     * no llama metodos bloqueantes del cliente ni toca la UI directamente.
     */
    private class ReceptorTemperatura implements MqttCallbackExtended {

        @Override
        public void connectComplete(boolean reconexion, String uriServidor) {
            if (!cerrando && !ejecutorMqtt.isShutdown()) {
                try{
                    ejecutorMqtt.execute(MqttManagerService.this::suscribir);
                }
                catch (RejectedExecutionException ignorada){
                    //El ejecutor se esta cerrando
                }
            }
        }

        @Override
        public void messageArrived(String topico, MqttMessage mensaje) {
            float temperatura;
            try {
                temperatura = Float.parseFloat(new String(mensaje.getPayload()).trim());
                Log.d("MQTT","Temperatura recibida " + temperatura);
            } catch (NumberFormatException e) {
                return;
            }
        }

        @Override
        public void connectionLost(Throwable causa) {
            Log.e("MQTT","Se perdió la conexión MQTT", causa);
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            // Esta servicio no publica mensajes.
        }
    }
}
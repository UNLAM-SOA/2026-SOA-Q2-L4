package com.example.smartinsulinguardian;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.pm.ServiceInfo;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

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

    private static final String CANAL_MONITOREO =
            "canal_monitoreo_temperatura";

    private static final int ID_NOTIFICACION_MONITOREO = 100;
    private static final String TAG = "MQTT";
    private static final int MQTT_QOS = 0;

    private final ExecutorService ejecutorMqtt =
            Executors.newSingleThreadExecutor();

    private volatile boolean cerrando = false;

    private MqttClient cliente;

    @Override
    public void onCreate() {
        super.onCreate();

        crearCanalNotificacion();
        iniciarEnPrimerPlano();

        Log.d(TAG, "Servicio MQTT foreground creado");

        ejecutorMqtt.execute(this::conectar);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Servicio MQTT iniciado");
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        cerrando = true;

        Log.d(TAG, "Destruyendo servicio MQTT");

        ejecutorMqtt.execute(this::desconectar);
        ejecutorMqtt.shutdown();

        stopForeground(STOP_FOREGROUND_REMOVE);

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


    private void crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel canal = new NotificationChannel(
                CANAL_MONITOREO,
                getString(R.string.canal_monitoreo_nombre),
                NotificationManager.IMPORTANCE_LOW
        );

        canal.setDescription(
                getString(R.string.notificacion_monitoreo_texto)
        );

        NotificationManager notificationManager =
                getSystemService(NotificationManager.class);

        notificationManager.createNotificationChannel(canal);
    }

    private void iniciarEnPrimerPlano() {
        Intent intentAbrirApp =
                new Intent(this, MainActivity.class);

        intentAbrirApp.setFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intentAbrirApp,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notificacion =
                new NotificationCompat.Builder(
                        this,
                        CANAL_MONITOREO
                )
                        .setSmallIcon(R.drawable.ic_estado)
                        .setContentTitle(
                                getString(
                                        R.string.notificacion_monitoreo_titulo
                                )
                        )
                        .setContentText(
                                getString(
                                        R.string.notificacion_monitoreo_texto
                                )
                        )
                        .setContentIntent(pendingIntent)
                        .setOngoing(true)
                        .setCategory(
                                NotificationCompat.CATEGORY_SERVICE
                        )
                        .setPriority(
                                NotificationCompat.PRIORITY_LOW
                        )
                        .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    ID_NOTIFICACION_MONITOREO,
                    notificacion,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            );
        } else {
            startForeground(
                    ID_NOTIFICACION_MONITOREO,
                    notificacion
            );
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
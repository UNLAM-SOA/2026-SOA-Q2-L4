package com.example.smartinsulinguardian;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Servicio en primer plano que mantiene la unica conexion MQTT
 * de la aplicacion. Se suscribe a temperatura y estado, y expone
 * publicarComando() a las Activities mediante un Binder local.
 * Toda operacion de red y manipulacion del cliente se serializa
 * en un solo hilo (ejecutorMqtt).
 */
public class MqttManagerService extends Service {

    private static final String CANAL_MONITOREO =
            "canal_monitoreo_temperatura";

    private static final int ID_NOTIFICACION_MONITOREO = 100;
    private static final String TAG = "MQTT";

    private final ScheduledExecutorService ejecutorMqtt =
            Executors.newSingleThreadScheduledExecutor();

    private final IBinder binder = new BinderLocal();

    private volatile boolean cerrando = false;
    private MqttClient cliente;
    private ScheduledFuture<?> reintentoActivo;

    /**
     * Binder local que permite a las Activities obtener una
     * referencia al servicio para invocar publicarComando().
     */
    public class BinderLocal extends Binder {

        /** Devuelve la instancia del servicio. */
        public MqttManagerService getServicio() {
            return MqttManagerService.this;
        }
    }

    // ── Ciclo de vida ─────────────────────────────────────────

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

    /** Devuelve el Binder para que las Activities envien comandos. */
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        cerrando = true;

        Log.d(TAG, "Destruyendo servicio MQTT");

        try {
            ejecutorMqtt.execute(() -> {
                cancelarReintento();
                desconectar();
            });
        } catch (RejectedExecutionException ignorada) {
            // El ejecutor se estaba cerrando
        }
        ejecutorMqtt.shutdown();

        stopForeground(STOP_FOREGROUND_REMOVE);

        super.onDestroy();
    }

    // ── Conexion ──────────────────────────────────────────────

    /**
     * Establece la conexion con el broker MQTT. Si falla,
     * programa un reintento. Reutiliza el mismo cliente si
     * ya fue creado previamente.
     */
    private void conectar() {
        if (cerrando) {
            return;
        }

        cancelarReintento();

        if (cliente != null && cliente.isConnected()) {
            return;
        }

        GuardianApplication app =
                (GuardianApplication) getApplication();
        app.postEstadoConexion(EstadoConexion.CONECTANDO);

        try {
            if (cliente == null) {
                cliente = new MqttClient(
                        ConfiguracionMqtt.BROKER_URI,
                        MqttClient.generateClientId(),
                        new MemoryPersistence()
                );

                cliente.setCallback(new ReceptorMensajes());
            }

            MqttConnectOptions opciones = new MqttConnectOptions();

            opciones.setCleanSession(true);
            opciones.setAutomaticReconnect(true);
            opciones.setConnectionTimeout(
                    ConfiguracionMqtt.TIMEOUT_CONEXION_S
            );

            cliente.connect(opciones);

            Log.d(TAG, "Conexion con el broker establecida");

        } catch (MqttException e) {
            Log.e(TAG, "Error al conectar con el broker", e);
            programarReintento();
        }
    }

    /**
     * Programa un unico reintento de conexion. Si ya hay uno
     * activo, no acumula otro.
     */
    private void programarReintento() {
        if (cerrando || reintentoActivo != null) {
            return;
        }

        GuardianApplication app =
                (GuardianApplication) getApplication();
        app.postEstadoConexion(EstadoConexion.ERROR);

        Log.d(TAG, "Reintentando conexion en "
                + ConfiguracionMqtt.REINTENTO_CONEXION_S + " s");

        reintentoActivo = ejecutorMqtt.schedule(
                this::conectar,
                ConfiguracionMqtt.REINTENTO_CONEXION_S,
                TimeUnit.SECONDS
        );
    }

    /** Cancela el reintento pendiente, si existe. */
    private void cancelarReintento() {
        if (reintentoActivo != null) {
            reintentoActivo.cancel(false);
            reintentoActivo = null;
        }
    }

    /** Desconecta y libera el cliente MQTT. */
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

    // ── Suscripcion ───────────────────────────────────────────

    /**
     * Se suscribe a los topicos de temperatura y estado.
     * Si las suscripciones tienen exito, postea CONECTADO.
     * Si fallan, postea ERROR.
     */
    private void suscribir() {
        GuardianApplication app =
                (GuardianApplication) getApplication();

        try {
            if (cerrando || cliente == null
                    || !cliente.isConnected()) {
                return;
            }

            cliente.subscribe(
                    ConfiguracionMqtt.TOPIC_TEMPERATURA,
                    ConfiguracionMqtt.QOS_SUSCRIPCION
            );

            cliente.subscribe(
                    ConfiguracionMqtt.TOPIC_ESTADO,
                    ConfiguracionMqtt.QOS_SUSCRIPCION
            );

            app.postEstadoConexion(EstadoConexion.CONECTADO);
            Log.d(TAG, "Suscrito a temperatura y estado");

        } catch (MqttException e) {
            Log.e(TAG, "Error al suscribirse", e);
            app.postEstadoConexion(EstadoConexion.ERROR);
        }
    }

    // ── Comandos ──────────────────────────────────────────────

    /**
     * Publica un comando (SILENCIAR o REACTIVAR) en el broker.
     * Si el servicio se esta cerrando, reporta error de inmediato.
     * El resultado se notifica a las Activities por LiveData.
     * Este metodo lo invoca la Activity a traves del Binder.
     */
    public void publicarComando(String comando) {
        GuardianApplication app =
                (GuardianApplication) getApplication();

        if (cerrando) {
            app.postResultadoComando(
                    new ResultadoComando(comando, false)
            );
            return;
        }

        try {
            ejecutorMqtt.execute(
                    () -> ejecutarPublicacion(comando, app)
            );

        } catch (RejectedExecutionException ignorada) {
            app.postResultadoComando(
                    new ResultadoComando(comando, false)
            );
        }
    }

    /**
     * Ejecuta la publicacion del comando en el hilo del ejecutor.
     * Actualiza ultimoComando en la Application si tiene exito.
     */
    private void ejecutarPublicacion(
            String comando, GuardianApplication app) {

        if (cerrando || cliente == null
                || !cliente.isConnected()) {
            app.postResultadoComando(
                    new ResultadoComando(comando, false)
            );
            return;
        }

        try {
            MqttMessage mensaje = new MqttMessage(
                    comando.getBytes(StandardCharsets.UTF_8)
            );
            mensaje.setQos(ConfiguracionMqtt.QOS_COMANDO);

            cliente.publish(
                    ConfiguracionMqtt.TOPIC_COMANDO, mensaje
            );

            app.setUltimoComando(comando);
            app.postResultadoComando(
                    new ResultadoComando(comando, true)
            );

            Log.d(TAG, "Comando publicado: " + comando);

        } catch (MqttException e) {
            Log.e(TAG, "Error al publicar comando", e);
            app.postResultadoComando(
                    new ResultadoComando(comando, false)
            );
        }
    }

    // ── Procesamiento de mensajes ─────────────────────────────

    /**
     * Procesa un mensaje del topico de temperatura: lo almacena
     * en el historial y actualiza el LiveData de temperatura actual.
     */
    private void procesarTemperatura(
            String contenido, GuardianApplication app) {

        try {
            float temperatura = Float.parseFloat(contenido);

            if (!Float.isFinite(temperatura)) {
                return;
            }

            long marcaTiempoMs = System.currentTimeMillis();

            app.getHistorialTemperaturas().agregar(
                    temperatura, marcaTiempoMs
            );
            app.notificarCambioHistorial();
            app.postTemperaturaActual(temperatura);

            Log.d(TAG, "Temperatura almacenada: " + temperatura);

        } catch (NumberFormatException e) {
            Log.w(TAG, "Temperatura invalida recibida");
        }
    }

    // ── Callback MQTT ─────────────────────────────────────────

    /**
     * Recibe los eventos del cliente MQTT y despacha
     * por topico (temperatura o estado de la FSM).
     */
    private class ReceptorMensajes implements MqttCallbackExtended {

        @Override
        public void connectComplete(
                boolean reconexion, String uriServidor) {

            if (!cerrando && !ejecutorMqtt.isShutdown()) {
                try {
                    ejecutorMqtt.execute(
                            MqttManagerService.this::suscribir
                    );

                } catch (RejectedExecutionException ignorada) {
                    // El ejecutor se esta cerrando.
                }
            }
        }

        @Override
        public void messageArrived(
                String topico, MqttMessage mensaje) {

            if (cerrando) {
                return;
            }

            String contenido = new String(
                    mensaje.getPayload(), StandardCharsets.UTF_8
            ).trim();

            GuardianApplication app =
                    (GuardianApplication) getApplication();

            if (ConfiguracionMqtt.TOPIC_TEMPERATURA
                    .equals(topico)) {
                procesarTemperatura(contenido, app);

            } else if (ConfiguracionMqtt.TOPIC_ESTADO
                    .equals(topico)) {
                app.postEstadoFsm(contenido);
                Log.d(TAG, "Estado recibido: " + contenido);
            }
        }

        @Override
        public void connectionLost(Throwable causa) {
            Log.e(TAG,
                    "Se perdio la conexion MQTT", causa);

            GuardianApplication app =
                    (GuardianApplication) getApplication();
            app.postEstadoConexion(EstadoConexion.RECONECTANDO);
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            // Nada que hacer tras la entrega.
        }
    }

    // ── Notificacion ──────────────────────────────────────────

    /** Crea el canal de notificacion requerido en Android 8+. */
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

    /** Inicia el servicio en primer plano con su notificacion persistente. */
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
}

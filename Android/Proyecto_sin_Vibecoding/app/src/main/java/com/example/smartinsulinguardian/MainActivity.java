package com.example.smartinsulinguardian;

import android.content.Intent;
import android.content.res.ColorStateList;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
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
    private volatile boolean pantallaVisible;
    private boolean brokerConectado;
    private boolean envioComandoEnCurso;
    private int picos = 0;
    private long instanteUltimoPico = 0L;
    private long instanteUltimaAccion = 0L;

    private TextView tvEstado;
    private TextView tvTemperatura;
    private TextView tvConexion;
    private ImageView ivEstado;
    private View viewConexion;
    private MaterialButton btnSilenciar;
    private MaterialButton btnReactivar;
    private MaterialButton btnGrafico;
    private MqttClient clienteDatos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AjusteBarrasSistema.aplicar(this);

        enlazarVistas();
        actualizarDisponibilidadComandos();
        configurarBotones();

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        acelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    @Override
    protected void onResume() {
        super.onResume();
        pantallaVisible = true;
        tvEstado.setText(R.string.estado_inicial);
        ivEstado.setImageTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.color_primario)));
        tvTemperatura.setText(R.string.temperatura_inicial);
        brokerConectado = false;
        envioComandoEnCurso = false;
        actualizarEstadoConexion(R.string.conexion_conectando);
        if (acelerometro != null) {
            sensorManager.registerListener(this, acelerometro, SensorManager.SENSOR_DELAY_GAME);
        }
        ejecutorMqtt.execute(this::conectarDatos);
    }

    @Override
    protected void onPause() {
        super.onPause();
        pantallaVisible = false;
        sensorManager.unregisterListener(this);
        ejecutorMqtt.execute(this::desconectarDatos);
    }

    /** Obtiene las referencias a las vistas definidas en el layout. */
    private void enlazarVistas() {
        tvEstado = findViewById(R.id.tvEstado);
        ivEstado = findViewById(R.id.ivEstado);
        tvTemperatura = findViewById(R.id.tvTemperatura);
        tvConexion = findViewById(R.id.tvConexion);
        viewConexion = findViewById(R.id.viewConexion);
        btnSilenciar = findViewById(R.id.btnSilenciar);
        btnReactivar = findViewById(R.id.btnReactivar);
        btnGrafico = findViewById(R.id.btnGrafico);
    }

    /** Asigna la accion de cada boton. */
    private void configurarBotones() {
        btnSilenciar.setOnClickListener(v -> alTocarBotonSilenciar());
        btnReactivar.setOnClickListener(v -> enviarComandoAlarma(COMANDO_REACTIVAR));
        btnGrafico.setOnClickListener(v -> abrirGrafico());
    }

    /** Envia la orden de silenciar y muestra la ayuda del sensor del celular. */
    private void alTocarBotonSilenciar() {
        enviarComandoAlarma(COMANDO_SILENCIAR);
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
        ejecutorMqtt.shutdown();
    }

    /** Conecta al broker y prepara la recepcion de estado y temperatura en segundo plano. */
    private void conectarDatos() {
        if (!pantallaVisible) {
            return;
        }
        try {
            MqttClient cliente = new MqttClient(ConfiguracionMqtt.BROKER_URI,
                    MqttClient.generateClientId(), new MemoryPersistence());
            clienteDatos = cliente;
            cliente.setCallback(new ReceptorDatos(cliente));

            MqttConnectOptions opciones = new MqttConnectOptions();
            opciones.setCleanSession(true);
            opciones.setAutomaticReconnect(true);
            opciones.setConnectionTimeout(ConfiguracionMqtt.TIMEOUT_CONEXION_S);
            cliente.connect(opciones);
        } catch (MqttException e) {
            desconectarDatos();
            actualizarEstadoConexion(R.string.conexion_error);
        }
    }

    /** Se suscribe a los topics de estado y temperatura al conectar o reconectar. */
    private void suscribirDatos(MqttClient cliente) {
        if (!pantallaVisible || clienteDatos != cliente || !cliente.isConnected()) {
            return;
        }
        try {
            cliente.subscribe(ConfiguracionMqtt.TOPIC_ESTADO, 0);
            cliente.subscribe(ConfiguracionMqtt.TOPIC_TEMPERATURA, 0);
            actualizarEstadoConexion(R.string.conexion_conectado);
        } catch (MqttException e) {
            actualizarEstadoConexion(R.string.conexion_error_suscripcion);
        }
    }

    /** Desconecta el cliente de lectura cuando la pantalla deja de estar visible. */
    private void desconectarDatos() {
        MqttClient cliente = clienteDatos;
        clienteDatos = null;
        cerrarCliente(cliente);
    }

    /** Actualiza el indicador de conexion desde el hilo principal. */
    private void actualizarEstadoConexion(int recursoTexto) {
        runOnUiThread(() -> {
            if (pantallaVisible) {
                if (recursoTexto == R.string.conexion_conectado) {
                    brokerConectado = true;
                } else if (recursoTexto != R.string.conexion_dato_invalido) {
                    brokerConectado = false;
                }
                tvConexion.setText(recursoTexto);
                int colorConexion = brokerConectado
                        ? R.color.conexion_conectado
                        : (recursoTexto == R.string.conexion_conectando
                        || recursoTexto == R.string.conexion_reconectando
                        ? R.color.conexion_conectando : R.color.conexion_error);
                viewConexion.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(this, colorConexion)));
                actualizarDisponibilidadComandos();
            }
        });
    }

    /**
     * Recibe los topics del dispositivo, analiza sus payloads y actualiza las vistas.
     * Los eventos de Paho se procesan fuera del hilo principal.
     */
    private class ReceptorDatos implements MqttCallbackExtended {

        private final MqttClient cliente;

        ReceptorDatos(MqttClient cliente) {
            this.cliente = cliente;
        }

        @Override
        public void connectComplete(boolean reconexion, String uriServidor) {
            if (!ejecutorMqtt.isShutdown()) {
                ejecutorMqtt.execute(() -> suscribirDatos(cliente));
            }
        }

        @Override
        public void messageArrived(String topico, MqttMessage mensaje) {
            String contenido = new String(mensaje.getPayload(), StandardCharsets.UTF_8).trim();
            if (ConfiguracionMqtt.TOPIC_ESTADO.equals(topico)) {
                runOnUiThread(() -> actualizarVistaEstado(contenido));
                return;
            }
            if (ConfiguracionMqtt.TOPIC_TEMPERATURA.equals(topico)) {
                try {
                    float temperatura = Float.parseFloat(contenido);
                    runOnUiThread(() -> {
                        if (pantallaVisible) {
                            tvTemperatura.setText(getString(R.string.temperatura_actual, temperatura));
                        }
                    });
                } catch (NumberFormatException e) {
                    actualizarEstadoConexion(R.string.conexion_dato_invalido);
                }
            }
        }

        @Override
        public void connectionLost(Throwable causa) {
            actualizarEstadoConexion(R.string.conexion_reconectando);
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
        }
    }

    /**
     * Publica en el broker el comando opuesto al ultimo enviado:
     * SILENCIAR si la alarma estaba reactivada, REACTIVAR si estaba silenciada.
     * Se ignora si todavia hay un envio en curso.
     */
    private void alSilenciarAlarma() {
        if (!brokerConectado || envioComandoEnCurso) {
            return;
        }
        String comando = COMANDO_REACTIVAR.equals(ultimoComando) ? COMANDO_SILENCIAR : COMANDO_REACTIVAR;
        enviarComandoAlarma(comando);
    }

    /** Envia el comando solicitado y deshabilita ambos botones durante el envio. */
    private void enviarComandoAlarma(String comando) {
        if (!brokerConectado || envioComandoEnCurso) {
            return;
        }
        envioComandoEnCurso = true;
        actualizarDisponibilidadComandos();
        ejecutorMqtt.execute(() -> {
            boolean enviado = publicarComando(comando);
            runOnUiThread(() -> alFinalizarEnvio(comando, enviado));
        });
    }

    /** Habilita los comandos solo mientras el broker esta conectado y no hay un envio activo. */
    private void actualizarDisponibilidadComandos() {
        boolean habilitados = brokerConectado && !envioComandoEnCurso;
        btnSilenciar.setEnabled(habilitados);
        btnReactivar.setEnabled(habilitados);
    }

    /** Actualiza el texto y el color del icono segun el estado publicado por el embebido. */
    private void actualizarVistaEstado(String estado) {
        if (!pantallaVisible) {
            return;
        }
        tvEstado.setText(getString(R.string.estado_actual, estado));
        ivEstado.setImageTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, obtenerColorEstado(estado))));
    }

    /** Asocia el estado MQTT con el color que representa al estado del dispositivo. */
    private int obtenerColorEstado(String estado) {
        String estadoNormalizado = estado.toUpperCase(Locale.ROOT);
        if (estadoNormalizado.contains("ERROR")) {
            return R.color.estado_error;
        }

        boolean temperaturaElevada = estadoNormalizado.contains("TEMP_ELEVADA");
        boolean luzDetectada = estadoNormalizado.contains("LUZ_DETECTADA");
        boolean puertaAbierta = estadoNormalizado.contains("PUERTA_ABIERTA");
        int condicionesActivas = (temperaturaElevada ? 1 : 0)
                + (luzDetectada ? 1 : 0)
                + (puertaAbierta ? 1 : 0);

        if (condicionesActivas > 1) {
            return R.color.estado_combinado;
        }
        if (temperaturaElevada) {
            return R.color.estado_temp;
        }
        if (luzDetectada) {
            return R.color.estado_luz;
        }
        if (puertaAbierta) {
            return R.color.estado_puerta;
        }
        if (estadoNormalizado.contains("SEGURO")) {
            return R.color.estado_seguro;
        }
        return R.color.color_primario;
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
        envioComandoEnCurso = false;
        actualizarDisponibilidadComandos();
        if (!enviado) {
            Toast.makeText(this, R.string.msg_error_broker, Toast.LENGTH_SHORT).show();
            return;
        }
        ultimoComando = comando;
        Toast.makeText(this, getString(R.string.msg_comando_enviado, comando), Toast.LENGTH_SHORT).show();
    }

    /** Abre la pantalla del grafico de temperatura mediante un Intent explicito. */
    private void abrirGrafico() {
        Intent intent = new Intent(this, GraficoActivity.class);
        startActivity(intent);
    }
}
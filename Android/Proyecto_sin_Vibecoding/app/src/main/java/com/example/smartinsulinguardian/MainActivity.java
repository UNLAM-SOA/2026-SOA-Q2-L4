package com.example.smartinsulinguardian;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.util.Locale;

/**
 * Pantalla principal: muestra el estado y la temperatura del sistema embebido,
 * permite silenciar la alarma y navegar al grafico de temperatura.
 * Tanto el boton de alarma como sacudir el celular publican SILENCIAR / REACTIVAR
 * a traves del MqttManagerService centralizado.
 */
public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final int SOLICITUD_NOTIFICACIONES = 1001;
    private static final String COMANDO_SILENCIAR = "SILENCIAR";
    private static final String COMANDO_REACTIVAR = "REACTIVAR";

    private static final int INDICE_EJE_X = 0;
    private static final int INDICE_EJE_Y = 1;
    private static final int INDICE_EJE_Z = 2;

    // Parametros para deteccion de sacudida con acelerometro
    private static final float UMBRAL_SACUDIDA_G = 2.5f;
    private static final int PICOS_PARA_SACUDIDA = 3;
    private static final long SEPARACION_MIN_PICOS_MS = 100L;
    private static final long VENTANA_PICOS_MS = 800L;
    private static final long ESPERA_ENTRE_ACCIONES_MS = 1500L;

    private SensorManager sensorManager;
    private Sensor acelerometro;
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

    private MqttManagerService servicioMqtt;
    private boolean servicioEnlazado;
    private EstadoConexion estadoConexionActual = EstadoConexion.CONECTANDO;
    private boolean envioComandoEnCurso;

    private final ServiceConnection conexionServicio = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MqttManagerService.BinderLocal binder = (MqttManagerService.BinderLocal) service;
            servicioMqtt = binder.getServicio();
            servicioEnlazado = true;
            actualizarDisponibilidadComandos();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            servicioMqtt = null;
            servicioEnlazado = false;
            actualizarDisponibilidadComandos();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AjusteBarrasSistema.aplicar(this);

        solicitarPermisoNotificaciones();
        iniciarServicioMqtt();

        enlazarVistas();
        configurarBotones();
        suscribirObservadores();

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            acelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intentServicio = new Intent(this, MqttManagerService.class);
        bindService(intentServicio, conexionServicio, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (servicioEnlazado) {
            unbindService(conexionServicio);
            servicioEnlazado = false;
            servicioMqtt = null;
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
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
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

    /** Observa los LiveData provistos por GuardianApplication. */
    private void suscribirObservadores() {
        GuardianApplication app = (GuardianApplication) getApplication();

        app.getEstadoConexionLiveData().observe(this, this::actualizarEstadoConexion);
        app.getEstadoFsmLiveData().observe(this, this::actualizarVistaEstado);
        app.getTemperaturaActualLiveData().observe(this, this::actualizarVistaTemperatura);
        app.getResultadoComandoLiveData().observe(this, this::alRecibirResultadoComando);
    }

    /** Procesa el resultado de un comando enviado. */
    private void alRecibirResultadoComando(ResultadoComando resultado) {
        if (resultado == null || !resultado.consumir()) {
            return;
        }

        envioComandoEnCurso = false;
        actualizarDisponibilidadComandos();

        if (resultado.isExitoso()) {
            Toast.makeText(
                    this,
                    getString(R.string.msg_comando_enviado, resultado.getComando()),
                    Toast.LENGTH_SHORT
            ).show();
        } else {
            Toast.makeText(this, R.string.msg_error_broker, Toast.LENGTH_SHORT).show();
        }
    }

    /** Actualiza la vista de temperatura al recibir un nuevo valor. */
    private void actualizarVistaTemperatura(Float temperatura) {
        if (temperatura == null) {
            tvTemperatura.setText(R.string.temperatura_inicial);
            return;
        }
        tvTemperatura.setText(getString(R.string.temperatura_actual, temperatura));
    }

    /** Actualiza el texto y el color del icono segun el estado publicado por el embebido. */
    private void actualizarVistaEstado(String estado) {
        if (estado == null || estado.isEmpty()) {
            tvEstado.setText(R.string.estado_inicial);
            ivEstado.setImageTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.color_primario)));
            return;
        }

        tvEstado.setText(getString(R.string.estado_actual, estado));
        ivEstado.setImageTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, obtenerColorEstado(estado))));
    }

    /** Actualiza el indicador de conexion con el broker. */
    private void actualizarEstadoConexion(EstadoConexion estado) {
        if (estado == null) {
            return;
        }

        estadoConexionActual = estado;

        int recursoTexto;
        int recursoColor;

        switch (estado) {
            case CONECTADO:
                recursoTexto = R.string.conexion_conectado;
                recursoColor = R.color.conexion_conectado;
                break;
            case RECONECTANDO:
                recursoTexto = R.string.conexion_reconectando;
                recursoColor = R.color.conexion_conectando;
                break;
            case ERROR:
                recursoTexto = R.string.conexion_error;
                recursoColor = R.color.conexion_error;
                break;
            case CONECTANDO:
            default:
                recursoTexto = R.string.conexion_conectando;
                recursoColor = R.color.conexion_conectando;
                break;
        }

        tvConexion.setText(recursoTexto);
        viewConexion.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, recursoColor)));

        actualizarDisponibilidadComandos();
    }

    /** Habilita los comandos solo si el servicio esta enlazado, conectado y no hay envio activo. */
    private void actualizarDisponibilidadComandos() {
        boolean brokerConectado = (estadoConexionActual == EstadoConexion.CONECTADO);
        boolean habilitados = servicioEnlazado && brokerConectado && !envioComandoEnCurso;
        btnSilenciar.setEnabled(habilitados);
        btnReactivar.setEnabled(habilitados);
    }

    /** Envia el comando solicitado al servicio MQTT. */
    private void enviarComandoAlarma(String comando) {
        if (!servicioEnlazado || servicioMqtt == null
                || estadoConexionActual != EstadoConexion.CONECTADO || envioComandoEnCurso) {
            return;
        }

        envioComandoEnCurso = true;
        actualizarDisponibilidadComandos();
        servicioMqtt.publicarComando(comando);
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

    @Override
    public void onAccuracyChanged(Sensor sensor, int precision) {
    }

    /**
     * Cuenta picos de aceleracion y alterna la alarma al detectar una sacudida.
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

    /**
     * Alterna la alarma segun el ultimo comando registrado en GuardianApplication.
     */
    private void alSilenciarAlarma() {
        if (!servicioEnlazado || estadoConexionActual != EstadoConexion.CONECTADO || envioComandoEnCurso) {
            return;
        }
        GuardianApplication app = (GuardianApplication) getApplication();
        String ultimo = app.getUltimoComando();
        String nuevoComando = COMANDO_REACTIVAR.equals(ultimo) ? COMANDO_SILENCIAR : COMANDO_REACTIVAR;
        enviarComandoAlarma(nuevoComando);
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

    /** Abre la pantalla del grafico de temperatura mediante un Intent explicito. */
    private void abrirGrafico() {
        Intent intent = new Intent(this, GraficoActivity.class);
        startActivity(intent);
    }

    /** Inicia el servicio MQTT en primer plano. */
    private void iniciarServicioMqtt() {
        Intent intent = new Intent(this, MqttManagerService.class);
        ContextCompat.startForegroundService(this, intent);
    }

    /** Solicita permisos de notificacion requeridos para Android 13+. */
    private void solicitarPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.POST_NOTIFICATIONS
                    },
                    SOLICITUD_NOTIFICACIONES
            );
        }
    }
}

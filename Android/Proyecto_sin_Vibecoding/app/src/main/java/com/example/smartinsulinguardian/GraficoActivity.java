package com.example.smartinsulinguardian;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

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
    private GraficoTemperaturaView grafico;
    private TextView tvUltimaLectura;
    private TextView tvConexion;
    private volatile boolean pantallaVisible;

    private final SimpleDateFormat formatoHora =
            new SimpleDateFormat(GraficoTemperaturaView.FORMATO_HORA, Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_grafico);
        AjusteBarrasSistema.aplicar(this);

        grafico = findViewById(R.id.graficoTemperatura);
        tvUltimaLectura = findViewById(R.id.tvUltimaLectura);
        tvConexion = findViewById(R.id.tvConexionGrafico);
        MaterialToolbar toolbar = findViewById(R.id.toolbarGrafico);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        pantallaVisible = true;
        tvConexion.setText(R.string.conexion_conectando);
    }

    @Override
    protected void onPause() {
        super.onPause();
        pantallaVisible = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


}

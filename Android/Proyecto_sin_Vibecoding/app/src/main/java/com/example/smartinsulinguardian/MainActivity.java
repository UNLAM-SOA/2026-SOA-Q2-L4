package com.example.smartinsulinguardian;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Pantalla principal: muestra el estado y la temperatura del sistema embebido,
 * permite silenciar la alarma y navegar al grafico de temperatura.
 * En la Version 2 es un prototipo; la comunicacion MQTT se completa en la Version 3.
 */
public class MainActivity extends AppCompatActivity {

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

    /** Prototipo: informa que el envio del comando SILENCIAR se implementa en la Version 3. */
    private void alSilenciarAlarma() {
        Toast.makeText(this, R.string.msg_silenciar_pendiente, Toast.LENGTH_SHORT).show();
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
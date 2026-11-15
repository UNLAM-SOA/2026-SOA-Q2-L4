package com.example.smartinsulinguardian;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GraficoActivity extends AppCompatActivity {

    private GraficoTemperaturaView grafico;
    private TextView tvUltimaLectura;
    private TextView tvConexion;

    private final SimpleDateFormat formatoHora =
            new SimpleDateFormat(
                    GraficoTemperaturaView.FORMATO_HORA,
                    Locale.getDefault()
            );

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

        GuardianApplication app =
                (GuardianApplication) getApplication();

        app.getLecturasLiveData().observe(this, this::actualizarGrafico);
        app.getEstadoConexionLiveData().observe(this, this::actualizarEstadoConexion);
    }

    private void actualizarEstadoConexion(EstadoConexion estado) {
        if (estado == null || tvConexion == null) {
            return;
        }

        switch (estado) {
            case CONECTADO:
                tvConexion.setText(R.string.conexion_conectado);
                break;
            case RECONECTANDO:
                tvConexion.setText(R.string.conexion_reconectando);
                break;
            case ERROR:
                tvConexion.setText(R.string.conexion_error);
                break;
            case CONECTANDO:
            default:
                tvConexion.setText(R.string.conexion_conectando);
                break;
        }
    }

    private void actualizarGrafico(List<LecturaTemperatura> historial) {

        grafico.mostrarHistorial(historial);

        if (historial.isEmpty()) {
            return;
        }

        LecturaTemperatura ultimaLectura =
                historial.get(historial.size() - 1);

        tvUltimaLectura.setText(getString(
                R.string.ultima_lectura,
                ultimaLectura.getTemperatura(),
                formatoHora.format(
                        new Date(ultimaLectura.getMarcaTiempoMs())
                )
        ));
    }
}

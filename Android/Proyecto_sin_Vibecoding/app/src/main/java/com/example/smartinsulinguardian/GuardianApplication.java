package com.example.smartinsulinguardian;

import android.app.Application;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;

public class GuardianApplication extends Application {
    private static final String TAG = "MQTT";

    // Historial compartido de temperaturas
    private final HistorialTemperaturas historialTemperaturas =
            new HistorialTemperaturas();

    // LiveData para notificar cambios en el historial
    private final MutableLiveData<List<LecturaTemperatura>> lecturasLiveData =
            new MutableLiveData<>(new ArrayList<>());

    // Acceso al historial compartido
    public HistorialTemperaturas getHistorialTemperaturas() {
        return historialTemperaturas;
    }

    // Acceso al LiveData
    public LiveData<List<LecturaTemperatura>> getLecturasLiveData() {
        return lecturasLiveData;
    }

    // Notifica una nueva versión del historial
    public void notificarCambioHistorial() {
        lecturasLiveData.postValue(
                historialTemperaturas.obtenerHistorial()
        );
    }

}
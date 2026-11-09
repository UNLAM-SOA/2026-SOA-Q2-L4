package com.example.smartinsulinguardian;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ProcessLifecycleOwner;

import java.util.ArrayList;
import java.util.List;

public class GuardianApplication extends Application
        implements DefaultLifecycleObserver {

    private static final String TAG = "MQTT";

    // Historial compartido de temperaturas
    private final HistorialTemperaturas historialTemperaturas =
            new HistorialTemperaturas();

    // LiveData para notificar cambios en el historial
    private final MutableLiveData<List<LecturaTemperatura>> lecturasLiveData =
            new MutableLiveData<>(new ArrayList<>());

    // Indica si tenemos un vínculo solicitado con el servicio
    private boolean servicioVinculado = false;

    // Conexión con MqttManagerService
    private final ServiceConnection conexionServicio = new ServiceConnection() {

        @Override
        public void onServiceConnected(
                ComponentName nombre,
                IBinder binder) {

            MqttManagerService.LocalBinder localBinder =
                    (MqttManagerService.LocalBinder) binder;

            MqttManagerService servicio = localBinder.getService();

            Log.d(TAG, "Vínculo con servicio MQTT establecido");
        }

        @Override
        public void onServiceDisconnected(ComponentName nombre) {
            Log.w(TAG, "Se perdió la conexión con el servicio MQTT");
        }
    };

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

    @Override
    public void onCreate() {
        super.onCreate();

        ProcessLifecycleOwner.get()
                .getLifecycle()
                .addObserver(this);
    }

    // Aplicación entra en primer plano
    @Override
    public void onStart(@NonNull LifecycleOwner owner) {

        Log.d(TAG, "Aplicación en primer plano");

        vincularServicio();
    }

    // Aplicación pasa a segundo plano
    @Override
    public void onStop(@NonNull LifecycleOwner owner) {

        Log.d(TAG, "Aplicación en segundo plano");

        desvincularServicio();
    }

    private void vincularServicio() {

        if (servicioVinculado) {
            return;
        }

        Intent intent = new Intent(this, MqttManagerService.class);

        try {
            servicioVinculado = bindService(
                    intent,
                    conexionServicio,
                    Context.BIND_AUTO_CREATE
            );

            if (servicioVinculado) {
                Log.d(TAG, "Vinculación al servicio MQTT solicitada");
            } else {
                Log.e(TAG, "No se pudo vincular al servicio MQTT");
            }

        } catch (SecurityException e) {
            Log.e(TAG, "Error de permisos al vincular servicio MQTT", e);
        }
    }

    private void desvincularServicio() {

        if (!servicioVinculado) {
            return;
        }

        unbindService(conexionServicio);
        servicioVinculado = false;

        Log.d(TAG, "Servicio MQTT desvinculado");
    }
}
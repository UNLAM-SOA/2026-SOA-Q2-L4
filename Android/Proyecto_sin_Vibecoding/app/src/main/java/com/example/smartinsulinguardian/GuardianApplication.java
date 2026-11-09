package com.example.smartinsulinguardian;

import android.app.Application;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;


//clase dedicada a ejecutar y detener el servicio MQTT segun si la aplicacion esta o no visible
public class GuardianApplication extends Application
        implements DefaultLifecycleObserver {

    @Override
    public void onCreate() {
        super.onCreate();

        ProcessLifecycleOwner.get()
                .getLifecycle()
                .addObserver(this);
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        Log.d("MQTT", "Aplicación en primer plano");

        Intent intent = new Intent(this, MqttManagerService.class);
        startService(intent);
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        Log.d("MQTT", "Aplicación en segundo plano");

        Intent intent = new Intent(this, MqttManagerService.class);
        stopService(intent);
    }
}
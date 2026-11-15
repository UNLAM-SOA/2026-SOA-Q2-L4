package com.example.smartinsulinguardian;

import android.app.Application;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;

/**
 * Estado global de la aplicacion. Contiene los LiveData que el
 * servicio MQTT actualiza y las Activities observan.
 */
public class GuardianApplication extends Application {

    private final HistorialTemperaturas historialTemperaturas =
            new HistorialTemperaturas();

    /** Historial completo de lecturas para el grafico. */
    private final MutableLiveData<List<LecturaTemperatura>>
            lecturasLiveData = new MutableLiveData<>(
                    new ArrayList<>()
            );

    /** Ultima temperatura recibida (null si aun no llego ninguna). */
    private final MutableLiveData<Float>
            temperaturaActualLiveData = new MutableLiveData<>();

    /** Ultimo estado de la FSM publicado por el embebido. */
    private final MutableLiveData<String>
            estadoFsmLiveData = new MutableLiveData<>();

    /** Estado de la conexion MQTT con el broker. */
    private final MutableLiveData<EstadoConexion>
            estadoConexionLiveData = new MutableLiveData<>(
                    EstadoConexion.CONECTANDO
            );

    /** Resultado del ultimo comando publicado (evento de un solo consumo). */
    private final MutableLiveData<ResultadoComando>
            resultadoComandoLiveData = new MutableLiveData<>();

    /**
     * Ultimo comando enviado con exito al broker.
     * La alarma arranca activa, asi que el valor inicial es REACTIVAR.
     * Se accede desde el hilo principal (Activity) y desde el ejecutor
     * del servicio, por eso es volatile.
     */
    private volatile String ultimoComando = "REACTIVAR";

    /** Controla si el dialogo explicativo de la sacudida ya se mostro en esta sesion. */
    private boolean dialogoSacudidaMostrado = false;

    /** Acceso al historial compartido. */
    public HistorialTemperaturas getHistorialTemperaturas() {
        return historialTemperaturas;
    }

    /** LiveData del historial completo (para el grafico). */
    public LiveData<List<LecturaTemperatura>> getLecturasLiveData() {
        return lecturasLiveData;
    }

    /** LiveData de la temperatura actual (para la pantalla principal). */
    public LiveData<Float> getTemperaturaActualLiveData() {
        return temperaturaActualLiveData;
    }

    /** LiveData del estado de la FSM (para la pantalla principal). */
    public LiveData<String> getEstadoFsmLiveData() {
        return estadoFsmLiveData;
    }

    /** LiveData del estado de conexion con el broker. */
    public LiveData<EstadoConexion> getEstadoConexionLiveData() {
        return estadoConexionLiveData;
    }

    /** LiveData del resultado del ultimo comando publicado. */
    public LiveData<ResultadoComando> getResultadoComandoLiveData() {
        return resultadoComandoLiveData;
    }

    /** Devuelve el ultimo comando enviado con exito. */
    public String getUltimoComando() {
        return ultimoComando;
    }

    /** Actualiza el ultimo comando enviado con exito. */
    public void setUltimoComando(String comando) {
        ultimoComando = comando;
    }

    /** Indica si el dialogo explicativo de sacudida ya fue mostrado en la sesion actual. */
    public boolean fueDialogoSacudidaMostrado() {
        return dialogoSacudidaMostrado;
    }

    /** Marca el dialogo explicativo de sacudida como mostrado en la sesion actual. */
    public void marcarDialogoSacudidaMostrado() {
        this.dialogoSacudidaMostrado = true;
    }

    /** Notifica una nueva version del historial a los observadores. */
    public void notificarCambioHistorial() {
        lecturasLiveData.postValue(
                historialTemperaturas.obtenerHistorial()
        );
    }

    /** Publica la temperatura actual desde el hilo del servicio. */
    public void postTemperaturaActual(float temperatura) {
        temperaturaActualLiveData.postValue(temperatura);
    }

    /** Publica el estado de la FSM desde el hilo del servicio. */
    public void postEstadoFsm(String estado) {
        estadoFsmLiveData.postValue(estado);
    }

    /** Publica el estado de la conexion desde el hilo del servicio. */
    public void postEstadoConexion(EstadoConexion estado) {
        estadoConexionLiveData.postValue(estado);
    }

    /** Publica el resultado de un comando desde el hilo del servicio. */
    public void postResultadoComando(ResultadoComando resultado) {
        resultadoComandoLiveData.postValue(resultado);
    }
}

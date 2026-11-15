package com.example.smartinsulinguardian;

/**
 * Estados posibles de la conexion MQTT con el broker.
 * El servicio publica el estado actual y las Activities
 * lo traducen a texto y color para la interfaz.
 */
public enum EstadoConexion {
    CONECTANDO,
    CONECTADO,
    RECONECTANDO,
    ERROR
}

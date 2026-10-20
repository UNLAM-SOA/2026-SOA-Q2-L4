package com.example.smartinsulinguardian;

/**
 * Datos de conexion al broker MQTT compartidos por las pantallas.
 * Deben coincidir con los definidos en el sistema embebido.
 */
public final class ConfiguracionMqtt {

    public static final String BROKER_URI = "tcp://broker.emqx.io:1883";
    public static final String TOPIC_TEMPERATURA = "/unlam/insulin_guardian/L4/temperatura";
    public static final String TOPIC_COMANDO = "/unlam/insulin_guardian/L4/comando";
    public static final int TIMEOUT_CONEXION_S = 10;

    private ConfiguracionMqtt() {
    }
}

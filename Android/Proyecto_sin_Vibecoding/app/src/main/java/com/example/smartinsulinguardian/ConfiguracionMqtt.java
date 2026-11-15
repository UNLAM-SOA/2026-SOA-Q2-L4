package com.example.smartinsulinguardian;

/**
 * Datos de conexion al broker MQTT compartidos por el servicio
 * y las pantallas. Deben coincidir con los definidos en el
 * sistema embebido.
 */
public final class ConfiguracionMqtt {

    public static final String BROKER_URI =
            "tcp://broker.emqx.io:1883";

    public static final String TOPIC_TEMPERATURA =
            "/unlam/insulin_guardian/L4/temperatura";

    public static final String TOPIC_ESTADO =
            "/unlam/insulin_guardian/L4/estado";

    public static final String TOPIC_COMANDO =
            "/unlam/insulin_guardian/L4/comando";

    public static final int TIMEOUT_CONEXION_S = 10;
    public static final int REINTENTO_CONEXION_S = 5;
    public static final int QOS_SUSCRIPCION = 0;
    public static final int QOS_COMANDO = 1;

    private ConfiguracionMqtt() {
    }
}

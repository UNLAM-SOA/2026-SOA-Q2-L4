/*------------------------ INCLUDES Y DEFINES ------------------------*/
// IMPORTANTE: INSTALAR LAS LIBRERIAS (desde Library Manager)
// ONEWIRE Y DALLASTEMPERATURE EN WOKWI PARA EL MANEJO DEL SENSOR DE TEMPERATURA
#include <OneWire.h>
#include <DallasTemperature.h>

//librerias para la comunicacion MQTT (instalar PubSubClient desde library manager)
#include <WiFi.h>
#include <PubSubClient.h>

#define PIN_SENSOR_MAGNETICO     27
#define PIN_SENSOR_LUZ           34
#define PIN_SENSOR_TEMPERATURA   4
#define PIN_LED_ROJO             25
#define PIN_LED_VERDE            26
#define PIN_LED_AZUL             33
#define PIN_BUZZER               18
#define PIN_RELE                 19

// CONFIGURACIONES
#define VELOCIDAD_SERIAL         115200 //inicia y configura comunicacion entre ESP32 y ARDUINO_IDE
#define INDICE_SENSOR_TEMPERATURA 0  //tenemos un solo sensor el cual esta en la posicion 0 del indice del DS18B20
#define TIEMPO_CONVERSION_TEMP_MS 750 //tiempo que tarda el DS18B20, el valor se obtuvo haciendo pruebas en wokwi y viendo cuanto tarda
#define DELAY_TAREAS 200
#define MAX_EVENTS_QUEUE 6
#define TIME_OUT 2000
#define TIEMPO_SIN_ESPERA 0
#define DELAY_1000_MS 1000
#define FRECUENCIA_BUZZER_HZ 1000
#define CANTIDAD_SENSORES_TEMPERATURA 1
#define INTERVALO_PUBLICACION_MQTT_MS 10000 //intervalo para publicar via mqtt
#define INTERVALO_REINTENTO_MQTT_MS   10000
#define DELAY_TAREA_MQTT_MS           10
#define MQTT_RETENER_MENSAJE          true //el broker guarda el ultimo estado para quien se suscriba despues
#define RANGO_ID_CLIENTE_MQTT         0xffff
#define TAMANIO_BUFFER_MENSAJE        16
#define ANCHO_MINIMO_TEMPERATURA      1
#define DECIMALES_TEMPERATURA         2

// TAREAS FREERTOS
#define TAMANIO_STACK_SENSORES        (1024 * 4)
#define TAMANIO_STACK_MQTT            4096
#define PRIORIDAD_TAREAS              1


//manejar el brillo con PWM
#define FRECUENCIA_PWM           5000
#define RESOLUCION_PWM           8

//calcular el brillo maximo con la resolucion de PWM
#define BRILLO_MAXIMO ((1 << RESOLUCION_PWM) - 1)  // 255 con RESOLUCION_PWM=8
#define BRILLO_APAGADO 0

//manejar canales de cada color del led RGB
#define CANAL_PWM_ROJO  0
#define CANAL_PWM_VERDE 1
#define CANAL_PWM_AZUL  2

// CONFIGURACION DE SENSORES 
#define UMBRAL_TEMP_MIN          24
#define UMBRAL_TEMP_MAX          25    //la insulina no debe superar los 8 grados
#define UMBRAL_LUZ_DETECTADA     2000 // segun LDR en wokwi 4063= cerrado (poca luz); 32=abierto (mucha luz), definimos un valor intermedio

/*------------------------ FIN INCLUDES Y DEFINE ------------------------*/

/*------------------------ INICIO DECLARACION VARIABLES GLOBALES ------------------------*/

//objetos para leer la temperatura
OneWire oneWire(PIN_SENSOR_TEMPERATURA);
DallasTemperature sensor_temperatura(&oneWire);

// VARIABLES PARA MEDICION NO BLOQUEANTE DE TEMPERATURA
unsigned long tiempo_solicitud_temperatura = 0;
bool medicion_temperatura_en_curso = false;

QueueHandle_t eventQueue;

//variables compartidas entre la tarea del sensor y la tarea MQTT
volatile float ultima_temperatura = 0.0;
volatile bool temperatura_disponible = false;

//indica a la tarea MQTT que hay un estado nuevo para publicar (se publica el inicial apenas conecta)
volatile bool estado_pendiente_publicar = true;

/*------------------------ FIN DECLARACION VARIABLES GLOBALES ------------------------*/

/*------------------------ INICIO DECLARACION VARIABLES PARA LA COMUNICACION MQTT ------------------------*/
// WIFI WOKWI
const char* WIFI_SSID = "SO Avanzados";
const char* WIFI_PASSWORD = "SOA.2019";

// MQTT
const char* MQTT_SERVER = "broker.emqx.io";
const int MQTT_PORT = 1883;

// Topics: el ESP32 publica en temperatura y estado, y recibe en comando
const char* TOPIC_TEMPERATURA = "/unlam/insulin_guardian/L4/temperatura";
const char* TOPIC_ESTADO      = "/unlam/insulin_guardian/L4/estado";
const char* TOPIC_COMANDO     = "/unlam/insulin_guardian/L4/comando";

// Comandos aceptados en TOPIC_COMANDO
const char* COMANDO_SILENCIAR = "SILENCIAR";
const char* COMANDO_REACTIVAR = "REACTIVAR";

WiFiClient wifiClient;
PubSubClient mqttClient(wifiClient);

/*------------------------ FIN DECLARACION VARIABLES PARA COMUNICACION MQTT------------------------*/


/*------------------------ INICIO FSM ------------------------*/
#define MAX_STATES 10
#define MAX_EVENTS 8

enum states {
              ST_INIT,
              ST_ERROR,
              ST_SEGURO,
              ST_PUERTA_ABIERTA,
              ST_LUZ_DETECTADA,
              ST_TEMP_ELEVADA,
              ST_TEMP_ELEVADA_Y_LUZ_DETECTADA,
              ST_TEMP_ELEVADA_Y_PUERTA_ABIERTA,
              ST_TEMP_ELEVADA_Y_LUZ_DETECTADA_Y_PUERTA_ABIERTA,
              ST_LUZ_DETECTADA_Y_PUERTA_ABIERTA
            } current_state;


String s_states [] = {
              "ST_INIT",
              "ST_ERROR",
              "ST_SEGURO",
              "ST_PUERTA_ABIERTA",
              "ST_LUZ_DETECTADA",
              "ST_TEMP_ELEVADA",
              "ST_TEMP_ELEVADA_Y_LUZ_DETECTADA",
              "ST_TEMP_ELEVADA_Y_PUERTA_ABIERTA",
              "ST_TEMP_ELEVADA_Y_LUZ_DETECTADA_Y_PUERTA_ABIERTA",
              "ST_LUZ_DETECTADA_Y_PUERTA_ABIERTA"
            };

enum events {
              EV_SIN_LUZ_DETECTADA,
              EV_LUZ_DETECTADA,
              EV_TEMP_ELEVADA,
              EV_TEMP_NORMAL,
              EV_PUERTA_ABIERTA,
              EV_PUERTA_CERRADA,
              EV_CMD_SILENCIAR,
              EV_CMD_REACTIVAR
            } new_event;

String s_events [] = {
              "EV_SIN_LUZ_DETECTADA",
              "EV_LUZ_DETECTADA",
              "EV_TEMP_ELEVADA",
              "EV_TEMP_NORMAL",
              "EV_PUERTA_ABIERTA",
              "EV_PUERTA_CERRADA",
              "EV_CMD_SILENCIAR",
              "EV_CMD_REACTIVAR"
            };


/*------------------------ DECLARACION FUNCIONES FSM ------------------------*/
void ir_init(); //lo usamos como inicializador de la fsm
void a_error();
void none();
void a_seguro();
void a_luz();
void a_temp();
void a_puerta();
void a_luztemp();
void a_luzpuerta();
void a_temppuerta();
void a_completo();
void a_silenciar();
void a_reactivar();
/*------------------------ FIN DECLARACION FUNCIONES FSM ------------------------*/


/*------------------------ MAQUINA DE ESTADOS ------------------------*/
typedef void (*transition)();

transition state_table[MAX_STATES][MAX_EVENTS] =
{
  //                                                      EV_SIN_LUZ_DETECTADA  EV_LUZ_DETECTADA  EV_TEMP_ELEVADA  EV_TEMP_NORMAL  EV_PUERTA_ABIERTA  EV_PUERTA_CERRADA  EV_CMD_SILENCIAR  EV_CMD_REACTIVAR
  /* ST_INIT */                                          { none,                 none,             none,            none,           none,              none,              none,             none },
  /* ST_ERROR */                                         { none,                 none,             none,            none,           none,              none,              none,             none },
  /* ST_SEGURO */                                        { none,                 a_luz,            a_temp,          none,           a_puerta,          none,              none,             none },
  /* ST_PUERTA_ABIERTA */                                { none,                 a_luzpuerta,      a_temppuerta,    none,           none,              a_seguro,          a_silenciar,      a_reactivar },
  /* ST_LUZ_DETECTADA */                                 { a_seguro,             none,             a_luztemp,       none,           a_luzpuerta,       none,              a_silenciar,      a_reactivar },
  /* ST_TEMP_ELEVADA */                                  { none,                 a_luztemp,        none,            a_seguro,       a_temppuerta,      none,              a_silenciar,      a_reactivar },
  /* ST_TEMP_ELEVADA_Y_LUZ_DETECTADA */                  { a_temp,               none,             none,            a_luz,          a_completo,        none,              a_silenciar,      a_reactivar },
  /* ST_TEMP_ELEVADA_Y_PUERTA_ABIERTA */                 { none,                 a_completo,       none,            a_puerta,       none,              a_temp,            a_silenciar,      a_reactivar },
  /* ST_TEMP_ELEVADA_Y_LUZ_DETECTADA_Y_PUERTA_ABIERTA */ { a_temppuerta,         none,             none,            a_luzpuerta,    none,              a_luztemp,         a_silenciar,      a_reactivar },
  /* ST_LUZ_DETECTADA_Y_PUERTA_ABIERTA */                { a_puerta,             none,             a_completo,      none,           none,              a_luz,             a_silenciar,      a_reactivar }
};

/* Espera un evento de la cola (bloqueante) y lo guarda en new_event. */
void get_event()
{
    events incoming_event;
    if(xQueueReceive(eventQueue,&incoming_event,portMAX_DELAY) == pdPASS)
    {
      new_event = incoming_event;
      Serial.print("Nuevo evento recibido: ");
      Serial.println(s_events[new_event]);
    }
}

/* Procesa un evento: ejecuta la transicion de la tabla, informa el cambio
  de estado por terminal y avisa a la tarea MQTT que hay un estado nuevo. */
void state_machine()
{
    get_event();

    // Guardamos el estado anterior para poder imprimir un mensaje si hubo un cambio de estado DEBUG
    int estado_anterior = current_state;

    state_table[current_state][new_event]();

    // Imprimimos un mensaje si hubo un cambio de estado DEBUG
    if (current_state != estado_anterior) {
        Serial.print("Cambio de estado: ");
        Serial.println(s_states[current_state]);

        estado_pendiente_publicar = true;
    }
}


/*------------------------ INICIO FUNCIONES DE LECTURA DE SENSORES ------------------------*/
/* Tarea FreeRTOS: lee el sensor magnetico cada segundo y encola un
  evento de puerta abierta o cerrada cuando cambia su estado. */
void leer_sensor_magnetico(void* p)
{
    int value;
    events event;
    events evento_anterior;
    bool primer_evento = true;

    while(1)
    {
        value = digitalRead(PIN_SENSOR_MAGNETICO);

        if(value == LOW)
        {
            event = EV_PUERTA_ABIERTA;
        }
        else
        {
            event = EV_PUERTA_CERRADA;
        }

        // envia solo la primera vez o cuando cambia el estado
        if(primer_evento || event != evento_anterior)
        {
            xQueueSend(eventQueue, &event, pdMS_TO_TICKS(TIME_OUT));

            evento_anterior = event;
            primer_evento = false;
        }

        vTaskDelay(pdMS_TO_TICKS(DELAY_1000_MS));
    }
}


/* Tarea FreeRTOS: lee el sensor de luz cada segundo y encola un evento
  cuando el valor cruza el umbral de luz detectada. */
void leer_sensor_luz(void* p)
{
    int value;
    events event;
    events evento_anterior;
    bool primer_evento = true;

    while(1)
    {
        value = analogRead(PIN_SENSOR_LUZ);

        if(value >= UMBRAL_LUZ_DETECTADA)
        {
            event = EV_LUZ_DETECTADA;
        }
        else
        {
            event = EV_SIN_LUZ_DETECTADA;
        }

        // envia solo la primera vez o cuando cambia el estado
        if(primer_evento || event != evento_anterior)
        {
            xQueueSend(eventQueue, &event, pdMS_TO_TICKS(TIME_OUT));

            evento_anterior = event;
            primer_evento = false;
        }

        vTaskDelay(pdMS_TO_TICKS(DELAY_1000_MS));
    }
}


/* Tarea FreeRTOS: mide la temperatura con el DS18B20 sin bloquear (pide la
  conversion y lee cuando paso el tiempo de conversion), guarda el valor
  para MQTT y encola un evento al cruzar los umbrales. */
void leer_sensor_temperatura(void* p)
{
    float value;
    events event;
    events evento_anterior;

    bool primer_evento = true;
    bool hay_evento = false;

    while(1)
    {
        if(!medicion_temperatura_en_curso)
        {
            sensor_temperatura.requestTemperatures();

            tiempo_solicitud_temperatura = millis();
            medicion_temperatura_en_curso = true;
        }
        else if(millis() - tiempo_solicitud_temperatura >= TIEMPO_CONVERSION_TEMP_MS)
        {
            value = sensor_temperatura.getTempCByIndex(INDICE_SENSOR_TEMPERATURA);
            ultima_temperatura = value; //para comunicarlo mediante MQTT
            temperatura_disponible = true;

            medicion_temperatura_en_curso = false;
            hay_evento = false;

            if(value >= UMBRAL_TEMP_MAX)
            {
                event = EV_TEMP_ELEVADA;
                hay_evento = true;
            }
            else if(value <= UMBRAL_TEMP_MIN)
            {
                event = EV_TEMP_NORMAL;
                hay_evento = true;
            }

            // envia solamente si existe un evento y hubo un cambio
            if(hay_evento && (primer_evento || event != evento_anterior))
            {
                xQueueSend(eventQueue, &event, pdMS_TO_TICKS(TIME_OUT));

                evento_anterior = event;
                primer_evento = false;
            }
        }

        vTaskDelay(pdMS_TO_TICKS(DELAY_TAREAS));
    }
}

/*------------------------ FIN FUNCIONES DE LECTURA DE SENSORES ------------------------*/

/*------------------------------- FUNCIONES DE ACTUADORES ------------------------------*/
/* Configura un pin como salida PWM para un canal del LED RGB.
  Usa la API de LEDC segun la version del core de ESP32. */
void inicializarPWMLed(uint8_t pin, uint8_t canal)
{
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  ledcAttach(pin, FRECUENCIA_PWM, RESOLUCION_PWM);
#else
  ledcSetup(canal, FRECUENCIA_PWM, RESOLUCION_PWM);
  ledcAttachPin(pin, canal);
#endif
}

/* Escribe un valor de brillo en el canal PWM de un color del LED RGB. */
void escribirPWMLed(uint8_t pin, uint8_t canal, uint32_t valor)
{
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  ledcWrite(pin, valor);
#else
  ledcWrite(canal, valor);
#endif
}
/* Establece el color del LED RGB con el brillo de cada canal (0 a BRILLO_MAXIMO). */
void establecerColorRGB(uint8_t r, uint8_t g, uint8_t b)
{
  escribirPWMLed(PIN_LED_ROJO,  CANAL_PWM_ROJO,  r);
  escribirPWMLed(PIN_LED_VERDE, CANAL_PWM_VERDE, g);
  escribirPWMLed(PIN_LED_AZUL,  CANAL_PWM_AZUL,  b);
}

/*----------------------FIN FUNCIONES DE ACTUADORES----------------------*/


/* Comprueba durante la inicializacion que el sensor
  DS18B20 se encuentre conectado y sea detectado
  Retorna true si el sensor fue encontrado
 */
bool comprobar_sensor_temperatura()
{
    return sensor_temperatura.getDeviceCount() >= CANTIDAD_SENSORES_TEMPERATURA;
}

/* Inicia la conexion a la red WiFi. No bloquea: la conexion
  se completa en segundo plano y la verifica tarea_mqtt. */
void iniciar_wifi()
{
    Serial.println("Iniciando conexion WiFi...");
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
}

/* Intenta conectar al broker MQTT con un clientId aleatorio y,
  si lo logra, se suscribe al topic de comandos. */
void conectar_mqtt()
{
    Serial.print("Conectando a MQTT...");

    String clientId = "InsulinGuardian-";
    clientId += String(random(RANGO_ID_CLIENTE_MQTT), HEX);

    if(mqttClient.connect(clientId.c_str()))
    {
        Serial.println(" conectado");

        mqttClient.subscribe(TOPIC_COMANDO);

        Serial.print("Suscripto a: ");
        Serial.println(TOPIC_COMANDO);
    }
    else
    {
        Serial.print(" error=");
        Serial.println(mqttClient.state());
    }
}

/* Tarea FreeRTOS de comunicacion. Es la unica que usa mqttClient (la
  libreria no es thread-safe). Mantiene la conexion WiFi/MQTT, recibe
  comandos, publica la temperatura periodicamente y publica el estado
  de la FSM cada vez que cambia. */
void tarea_mqtt(void* p)
{
    unsigned long ultima_publicacion = 0;
    unsigned long ultimo_intento_mqtt = 0;
    bool wifi_conectado_anunciado = false;

    while(1)
    {
        // Si todavia no hay WiFi, esperamos sin bloquear las otras tareas
        if(WiFi.status() != WL_CONNECTED)
        {
            wifi_conectado_anunciado = false;
        }
        else
        {
            // Mostrar una sola vez cuando se conecta
            if(!wifi_conectado_anunciado)
            {
                Serial.println("WiFi conectado");
                Serial.print("IP: ");
                Serial.println(WiFi.localIP());

                wifi_conectado_anunciado = true;
            }

            // Intentar conexion MQTT cada cierto tiempo
            if(!mqttClient.connected())
            {
                if(millis() - ultimo_intento_mqtt >= INTERVALO_REINTENTO_MQTT_MS)
                {
                    conectar_mqtt();

                    ultimo_intento_mqtt = millis();
                }
            }
            else
            {
                mqttClient.loop();

                // Publicar el estado de la FSM cuando cambia
                if(estado_pendiente_publicar)
                {
                    estado_pendiente_publicar = false;

                    mqttClient.publish(TOPIC_ESTADO, s_states[current_state].c_str(), MQTT_RETENER_MENSAJE);

                    Serial.print("Estado enviado por MQTT: ");
                    Serial.println(s_states[current_state]);
                }

                // Publicar temperatura cada cierto tiempo
                if( temperatura_disponible && millis() - ultima_publicacion >= INTERVALO_PUBLICACION_MQTT_MS)
                {
                    char mensaje[TAMANIO_BUFFER_MENSAJE];

                    dtostrf(ultima_temperatura, ANCHO_MINIMO_TEMPERATURA, DECIMALES_TEMPERATURA, mensaje);

                    mqttClient.publish(TOPIC_TEMPERATURA, mensaje);

                    Serial.print("Temperatura enviada por MQTT: ");
                    Serial.println(mensaje);

                    ultima_publicacion = millis();
                }
            }
        }

        vTaskDelay(pdMS_TO_TICKS(DELAY_TAREA_MQTT_MS));
    }
}

/* Callback de PubSubClient al recibir un mensaje. No actua sobre los
  actuadores: traduce el comando a un evento y lo encola para que lo
  procese la FSM, que ejecuta la accion en la funcion de transicion. */
void recibir_mensaje_mqtt(char* topic, byte* payload, unsigned int length)
{
    String mensaje = "";
    events evento;

    for(unsigned int i = 0; i < length; i++)
    {
        mensaje += (char)payload[i];
    }
    mensaje.trim();

    Serial.print("Mensaje MQTT recibido en ");
    Serial.print(topic);
    Serial.print(": ");
    Serial.println(mensaje);

    if(String(topic) != TOPIC_COMANDO)
    {
        return;
    }

    if(mensaje == COMANDO_SILENCIAR)
    {
        evento = EV_CMD_SILENCIAR;
    }
    else if(mensaje == COMANDO_REACTIVAR)
    {
        evento = EV_CMD_REACTIVAR;
    }
    else
    {
        Serial.println("Comando MQTT desconocido");
        return;
    }

    // Sin espera: este callback corre dentro de tarea_mqtt y no debe bloquearla
    xQueueSend(eventQueue, &evento, TIEMPO_SIN_ESPERA);
}


/*------------------------ BEGIN ESP32 ------------------------*/
void setup() 
{
    Serial.begin(VELOCIDAD_SERIAL);
    Serial.println("Smart Insulin Guardian System");

    iniciar_wifi();
    mqttClient.setServer(MQTT_SERVER,MQTT_PORT);

    mqttClient.setCallback(recibir_mensaje_mqtt);

    // INPUTS
    pinMode(PIN_SENSOR_MAGNETICO, INPUT_PULLUP);
    pinMode(PIN_SENSOR_LUZ, INPUT);

    // OUTPUTS
    pinMode(PIN_BUZZER, OUTPUT);
    pinMode(PIN_RELE, OUTPUT);

    // LED RGB
    inicializarPWMLed(PIN_LED_ROJO, CANAL_PWM_ROJO);
    inicializarPWMLed(PIN_LED_VERDE, CANAL_PWM_VERDE);
    inicializarPWMLed(PIN_LED_AZUL, CANAL_PWM_AZUL);

    // SENSOR DE TEMPERATURA
    sensor_temperatura.begin();
    sensor_temperatura.setWaitForConversion(false);

    // ESTADO INICIAL
    ir_init();

    // COMPROBACION DEL SENSOR AL INICIAR
    if(comprobar_sensor_temperatura())
    {
        Serial.println("Sensor de temperatura detectado correctamente");

        eventQueue = xQueueCreate(MAX_EVENTS_QUEUE, sizeof(events));

        xTaskCreate(leer_sensor_magnetico, "sensor magnetico", TAMANIO_STACK_SENSORES, NULL, PRIORIDAD_TAREAS, NULL);
        xTaskCreate(leer_sensor_luz, "sensor luminico", TAMANIO_STACK_SENSORES, NULL, PRIORIDAD_TAREAS, NULL);
        xTaskCreate(leer_sensor_temperatura, "sensor temperatura", TAMANIO_STACK_SENSORES, NULL, PRIORIDAD_TAREAS, NULL);
        xTaskCreate(tarea_mqtt, "MQTT", TAMANIO_STACK_MQTT, NULL, PRIORIDAD_TAREAS, NULL);

        a_seguro();
    }
    else
    {
        Serial.println("ERROR: sensor de temperatura no detectado");

        a_error();
    }
}

void loop()
{
    if(current_state != ST_ERROR)
    {
        state_machine();
    }
    else
    {
        vTaskDelay(pdMS_TO_TICKS(DELAY_1000_MS));
    }
}

/*------------------------ END ESP32 ------------------------*/

// ---------- INICIO FUNCIONES DE TRANSICION ---------- //

/* Estado inicial: LED apagado, sin buzzer ni ventilacion. */
void ir_init()
{
    current_state = ST_INIT;
    establecerColorRGB(BRILLO_APAGADO, BRILLO_APAGADO, BRILLO_APAGADO);
    noTone(PIN_BUZZER);
    digitalWrite(PIN_RELE, LOW);
}

/* Error de inicializacion: LED rojo y buzzer, sin ventilacion. */
void a_error()
{
    current_state = ST_ERROR;

    establecerColorRGB(
        BRILLO_MAXIMO,
        BRILLO_APAGADO,
        BRILLO_APAGADO
    );

    tone(PIN_BUZZER, FRECUENCIA_BUZZER_HZ);
    digitalWrite(PIN_RELE, LOW);
}

/* Evento sin efecto en el estado actual: no se realiza ninguna accion. */
void none()
{
    // No se realiza cambio de estado
}

/* Estado seguro: LED verde, sin buzzer ni ventilacion. */
void a_seguro()
{
    current_state = ST_SEGURO;
    establecerColorRGB(BRILLO_APAGADO, BRILLO_MAXIMO, BRILLO_APAGADO);
    noTone(PIN_BUZZER);
    digitalWrite(PIN_RELE, LOW);
}

/* Luz detectada: LED amarillo y buzzer, sin ventilacion. */
void a_luz()
{
    current_state = ST_LUZ_DETECTADA;
    establecerColorRGB(BRILLO_MAXIMO, BRILLO_MAXIMO, BRILLO_APAGADO);
    tone(PIN_BUZZER, FRECUENCIA_BUZZER_HZ);
    digitalWrite(PIN_RELE, LOW);
}

/* Temperatura elevada: LED rojo, buzzer y ventilacion. */
void a_temp()
{
    current_state = ST_TEMP_ELEVADA;
    establecerColorRGB(BRILLO_MAXIMO, BRILLO_APAGADO, BRILLO_APAGADO);
    tone(PIN_BUZZER, FRECUENCIA_BUZZER_HZ);
    digitalWrite(PIN_RELE, HIGH);
}

/* Puerta abierta: LED azul y buzzer, sin ventilacion. */
void a_puerta()
{
    current_state = ST_PUERTA_ABIERTA;
    establecerColorRGB(BRILLO_APAGADO, BRILLO_APAGADO, BRILLO_MAXIMO);
    tone(PIN_BUZZER, FRECUENCIA_BUZZER_HZ);
    digitalWrite(PIN_RELE, LOW);
}

/* Temperatura elevada y luz detectada: LED magenta, buzzer y ventilacion. */
void a_luztemp()
{
    current_state = ST_TEMP_ELEVADA_Y_LUZ_DETECTADA;
    establecerColorRGB(BRILLO_MAXIMO, BRILLO_APAGADO, BRILLO_MAXIMO);
    tone(PIN_BUZZER, FRECUENCIA_BUZZER_HZ);
    digitalWrite(PIN_RELE, HIGH);
}

/* Luz detectada y puerta abierta: LED magenta y buzzer, sin ventilacion. */
void a_luzpuerta()
{
    current_state = ST_LUZ_DETECTADA_Y_PUERTA_ABIERTA;
    establecerColorRGB(BRILLO_MAXIMO, BRILLO_APAGADO, BRILLO_MAXIMO);
    tone(PIN_BUZZER, FRECUENCIA_BUZZER_HZ);
    digitalWrite(PIN_RELE, LOW);
}

/* Temperatura elevada y puerta abierta: LED magenta, buzzer y ventilacion. */
void a_temppuerta()
{
    current_state = ST_TEMP_ELEVADA_Y_PUERTA_ABIERTA;
    establecerColorRGB(BRILLO_MAXIMO, BRILLO_APAGADO, BRILLO_MAXIMO);
    tone(PIN_BUZZER, FRECUENCIA_BUZZER_HZ);
    digitalWrite(PIN_RELE, HIGH);
}

/* Las tres condiciones activas: LED magenta, buzzer y ventilacion. */
void a_completo()
{
    current_state = ST_TEMP_ELEVADA_Y_LUZ_DETECTADA_Y_PUERTA_ABIERTA;
    establecerColorRGB(BRILLO_MAXIMO, BRILLO_APAGADO, BRILLO_MAXIMO);
    tone(PIN_BUZZER, FRECUENCIA_BUZZER_HZ);
    digitalWrite(PIN_RELE, HIGH);
}

/* Comando silenciar (solo estados de alarma): apaga el buzzer y
  mantiene el estado, el LED y la ventilacion. */
void a_silenciar()
{
    noTone(PIN_BUZZER);
}

/* Comando reactivar (solo estados de alarma): vuelve a encender
  el buzzer y mantiene el estado, el LED y la ventilacion. */
void a_reactivar()
{
    tone(PIN_BUZZER, FRECUENCIA_BUZZER_HZ);
}
// ---------- FIN FUNCIONES DE TRANSICION ---------- //

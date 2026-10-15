/*------------------------ INCLUDES Y DEFINES ------------------------*/
// IMPORTANTE: INSTALAR LAS LIBRERIAS (desde Library Manager)
// ONEWIRE Y DALLASTEMPERATURE EN WOKWI PARA EL MANEJO DEL SENSOR DE TEMPERATURA
#include <OneWire.h>
#include <DallasTemperature.h>

#define PIN_SENSOR_MAGNETICO     27
#define PIN_SENSOR_LUZ           34
#define PIN_SENSOR_TEMPERATURA   4
#define PIN_LED_ROJO             25
#define PIN_LED_VERDE            26
#define PIN_LED_AZUL             33
#define PIN_BUZZER               18
#define PIN_RELE                 19

// CONFIGURACIONES
#define VELOCIDAD_SERIAL         115200 //inicia y configura comunicacion entre EPS32 y ARDUINO_IDE
#define INDICE_SENSOR_TEMPERATURA 0  //tenemos un solo sensor el cual esta en la posicion 0 del indice del DS18B20
#define TIEMPO_CONVERSION_TEMP_MS 750 //tiempo que tarda el DS18B20, el valor se obtuvo haciendo pruebas en wokwi y viendo cuanto tarda
#define DELAY_TAREAS 200
#define MAX_EVENTS_QUEUE 6
#define TIME_OUT 2000
#define DELAY_1000_MS 1000
#define FRECUENCIA_BUZZER_HZ 1000
#define CANTIDAD_SENSORES_TEMPERATURA 1


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
#define UMBRAL_TEMP_MIN          4
#define UMBRAL_TEMP_MAX          8    //la insulina no debe superar los 8 grados
#define UMBRAL_LUZ_DETECTADA     2000 // segun LDR en wokwi 4063= cerrado (poca luz); 32=abierto (mucha luz), definimos un valor intermedio

// TEMPORIZACION
#define INTERVALO_LECTURA_MS     1000 //1 segundo
/*------------------------ FIN INCLUDES Y DEFINE ------------------------*/

/*------------------------ INICIO DECLARACION VARIABLES GLOBALES ------------------------*/

//objetos para leer la temperatura
OneWire oneWire(PIN_SENSOR_TEMPERATURA);
DallasTemperature sensor_temperatura(&oneWire);

// VARIABLES PARA MEDICION NO BLOQUEANTE DE TEMPERATURA
unsigned long tiempo_solicitud_temperatura = 0;
bool medicion_temperatura_en_curso = false;

QueueHandle_t eventQueue;
/*------------------------ FIN DECLARACION VARIABLES GLOBALES ------------------------*/


/*------------------------ INICIO FSM ------------------------*/
#define MAX_STATES 10
#define MAX_EVENTS 6

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
              EV_PUERTA_CERRADA
            } new_event;

String s_events [] = {
              "EV_SIN_LUZ_DETECTADA",
              "EV_LUZ_DETECTADA",
              "EV_TEMP_ELEVADA",
              "EV_TEMP_NORMAL",
              "EV_PUERTA_ABIERTA",
              "EV_PUERTA_CERRADA"
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
/*------------------------ FIN DECLARACION FUNCIONES FSM ------------------------*/


/*------------------------ MAQUINA DE ESTADOS ------------------------*/
typedef void (*transition)();

transition state_table[MAX_STATES][MAX_EVENTS] =
{
  //                                                      EV_SIN_LUZ_DETECTADA  EV_LUZ_DETECTADA  EV_TEMP_ELEVADA  EV_TEMP_NORMAL  EV_PUERTA_ABIERTA  EV_PUERTA_CERRADA
  /* ST_INIT */                                          { none,                 none,             none,            none,           none,              none },
  /* ST_ERROR */                                         { none,                 none,             none,            none,           none,              none },
  /* ST_SEGURO */                                        { none,                 a_luz,            a_temp,          none,           a_puerta,          none },
  /* ST_PUERTA_ABIERTA */                                { none,                 a_luzpuerta,      a_temppuerta,    none,           none,              a_seguro },
  /* ST_LUZ_DETECTADA */                                 { a_seguro,             none,             a_luztemp,       none,           a_luzpuerta,       none },
  /* ST_TEMP_ELEVADA */                                  { none,                 a_luztemp,        none,            a_seguro,       a_temppuerta,      none },
  /* ST_TEMP_ELEVADA_Y_LUZ_DETECTADA */                  { a_temp,               none,             none,            a_luz,          a_completo,        none },
  /* ST_TEMP_ELEVADA_Y_PUERTA_ABIERTA */                 { none,                 a_completo,       none,            a_puerta,       none,              a_temp },
  /* ST_TEMP_ELEVADA_Y_LUZ_DETECTADA_Y_PUERTA_ABIERTA */ { a_temppuerta,         none,             none,            a_luzpuerta,    none,              a_luztemp },
  /* ST_LUZ_DETECTADA_Y_PUERTA_ABIERTA */                { a_puerta,             none,             a_completo,      none,           none,              a_luz }
};

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
    }
}


/*------------------------ INICIO FUNCIONES DE LECTURA DE SENSORES ------------------------*/
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


void leer_sensor_luz(void* p)
{
    int value;
    events event;
    events evento_anterior;
    bool primer_evento = true;

    while(1)
    {
        value = analogRead(PIN_SENSOR_LUZ);

        if(value <= UMBRAL_LUZ_DETECTADA)
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
void inicializarPWMLed(uint8_t pin, uint8_t canal)
{
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  ledcAttach(pin, FRECUENCIA_PWM, RESOLUCION_PWM);
#else
  ledcSetup(canal, FRECUENCIA_PWM, RESOLUCION_PWM);
  ledcAttachPin(pin, canal);
#endif
}

void escribirPWMLed(uint8_t pin, uint8_t canal, uint32_t valor)
{
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  ledcWrite(pin, valor);
#else
  ledcWrite(canal, valor);
#endif
}
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


/*------------------------ BEGIN ESP32 ------------------------*/
void setup() 
{
    Serial.begin(VELOCIDAD_SERIAL);
    Serial.println("Smart Insulin Guardian System");

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

        xTaskCreate(leer_sensor_magnetico, "sensor magnetico", 1024*4, NULL, 1, NULL);
        xTaskCreate(leer_sensor_luz, "sensor luminico", 1024*4, NULL, 1, NULL);
        xTaskCreate(leer_sensor_temperatura, "sensor temperatura", 1024*4, NULL, 1, NULL);

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
// ---------- FIN FUNCIONES DE TRANSICION ---------- //

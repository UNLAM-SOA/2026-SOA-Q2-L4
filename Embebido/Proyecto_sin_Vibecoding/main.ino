/*------------------------ INCLUDES Y DEFINES ------------------------*/
// IMPORTANTE: INSTALAR LAS LIBRERIAS (desde Library Manager)
// ONEWIRE Y DALLASTEMPERATURE EN WOKWI PARA EL MANEJO DEL SENSOR DE TEMPERATURA
#include <OneWire.h>
#include <DallasTemperature.h>

#define PIN_SENSOR_MAGNETICO     27
#define PIN_SENSOR_LUZ           34
#define PIN_SENSOR_TEMPERATURA   2
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


//manejar el brillo con PWM
#define FRECUENCIA_PWM           5000
#define RESOLUCION_PWM           8

//calcular el brillo maximo con la resolucion de PWM
#define BRILLO_MAXIMO ((1 << RESOLUCION_PWM) - 1)  // 255 con RESOLUCION_PWM=8
#define BRILLO_APAGADO 0
#define PERIODO_BEEP_MS 300

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
      xQueueSend(eventQueue,&event,TIME_OUT);
      vTaskDelay(DELAY_1000_MS);
   }
}

void leer_sensor_luz(void* p)
{
    int value;
    events event;
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
        xQueueSend(eventQueue,&event,TIME_OUT);
        vTaskDelay(DELAY_1000_MS);
    }
}

void leer_sensor_temperatura(void* p)
{
    float value;
    events event;
    while(1)
    {
        value = sensor_temperatura.getTempCByIndex(INDICE_SENSOR_TEMPERATURA);
        if(value >= UMBRAL_TEMP_MAX)
        {
            event = EV_TEMP_ELEVADA;
        }
        else if(value <= UMBRAL_TEMP_MIN)
        {
            event = EV_TEMP_NORMAL;
        }
        xQueueSend(eventQueue,&event,TIME_OUT);
        vTaskDelay(DELAY_1000_MS);
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
  escribirPWMLed(PIN_LED_ROJO,  CANAL_PWM_ROJO,  BRILLO_MAXIMO - r);
  escribirPWMLed(PIN_LED_VERDE, CANAL_PWM_VERDE, BRILLO_MAXIMO - g);
  escribirPWMLed(PIN_LED_AZUL,  CANAL_PWM_AZUL,  BRILLO_MAXIMO - b);
}

void actualizarBuzzer(bool activo)
{
  static uint32_t ultimoCambioMs = 0;
  static bool encendido = false;

  if (!activo)
  {
    digitalWrite(PIN_BUZZER, LOW);
    encendido = false;
    return;
  }

  if (millis() - ultimoCambioMs >= PERIODO_BEEP_MS)
  {
    ultimoCambioMs = millis();
    encendido = !encendido;
    digitalWrite(PIN_BUZZER, encendido ? HIGH : LOW);
  }
}

void actualizarActuadores(states estado)
{
  bool alertaActiva = (estado != ST_SEGURO && estado != ST_INIT && estado != ST_ERROR);
  bool ventilacionActiva = (estado == ST_TEMP_ELEVADA || estado == ST_TEMP_ELEVADA_Y_LUZ_DETECTADA ||
                            estado == ST_TEMP_ELEVADA_Y_PUERTA_ABIERTA || estado == ST_TEMP_ELEVADA_Y_LUZ_DETECTADA_Y_PUERTA_ABIERTA);

  switch (estado)
  {
    case ST_INIT:                establecerColorRGB(BRILLO_APAGADO, BRILLO_APAGADO, BRILLO_APAGADO); break;
    case ST_SEGURO:               establecerColorRGB(BRILLO_APAGADO, BRILLO_MAXIMO,  BRILLO_APAGADO); break;
    case ST_LUZ_DETECTADA:        establecerColorRGB(BRILLO_MAXIMO,  BRILLO_MAXIMO,  BRILLO_APAGADO); break;
    case ST_TEMP_ELEVADA:         establecerColorRGB(BRILLO_MAXIMO,  BRILLO_APAGADO, BRILLO_APAGADO); break;
    case ST_PUERTA_ABIERTA:       establecerColorRGB(BRILLO_APAGADO, BRILLO_APAGADO, BRILLO_MAXIMO); break;
    case ST_TEMP_ELEVADA_Y_LUZ_DETECTADA:
    case ST_LUZ_DETECTADA_Y_PUERTA_ABIERTA:
    case ST_TEMP_ELEVADA_Y_PUERTA_ABIERTA:
    case ST_TEMP_ELEVADA_Y_LUZ_DETECTADA_Y_PUERTA_ABIERTA:
                                  establecerColorRGB(BRILLO_MAXIMO,  BRILLO_APAGADO, BRILLO_MAXIMO); break;
    case ST_ERROR:                establecerColorRGB(BRILLO_MAXIMO,  BRILLO_APAGADO, BRILLO_APAGADO); break;
  }

  actualizarBuzzer(alertaActiva);
  digitalWrite(PIN_RELE, ventilacionActiva ? HIGH : LOW);
}
/*----------------------FIN FUNCIONES DE ACTUADORES----------------------*/

/*------------------------ BEGIN ESP32 ------------------------*/
void setup() 
{
    ir_init();

    Serial.begin(VELOCIDAD_SERIAL);
    Serial.println("Smart Insulin Guardian System");

    // INPUTS
    pinMode(PIN_SENSOR_MAGNETICO, INPUT_PULLUP);
    pinMode(PIN_SENSOR_LUZ, INPUT);

    pinMode(PIN_BUZZER, OUTPUT);
    pinMode(PIN_RELE, OUTPUT);

    inicializarPWMLed(PIN_LED_ROJO, CANAL_PWM_ROJO);
    inicializarPWMLed(PIN_LED_VERDE, CANAL_PWM_VERDE);
    inicializarPWMLed(PIN_LED_AZUL, CANAL_PWM_AZUL);

    // SENSOR DE TEMPERATURA
    sensor_temperatura.begin();
    sensor_temperatura.setWaitForConversion(false); //evita que la ESP32 se bloquee mientras el DS18B20 realiza la medicion

    eventQueue = xQueueCreate(MAX_EVENTS_QUEUE,sizeof(events));
    xTaskCreate(leer_sensor_magnetico,"sensor temperatura",1024*4,NULL,1,NULL);
    xTaskCreate(leer_sensor_luz,"sensor luminico",1024*4,NULL,1,NULL);
    xTaskCreate(leer_sensor_temperatura,"sensor temperatura",1024*4,NULL,1,NULL);

    a_seguro();

}

void loop() 
{
    state_machine();
    actualizarActuadores(current_state);
}




/*------------------------ END ESP32 ------------------------*/



// ---------- INICIO FUNCIONES DE TRANSICION ---------- //
void ir_init()
{
    current_state = ST_INIT;
}

void none()
{
    // No se realiza cambio de estado
}

void a_seguro()
{
    current_state = ST_SEGURO;
}

void a_luz()
{
    current_state = ST_LUZ_DETECTADA;
}

void a_temp()
{
    current_state = ST_TEMP_ELEVADA;
}

void a_puerta()
{
    current_state = ST_PUERTA_ABIERTA;
}

void a_luztemp()
{
    current_state = ST_TEMP_ELEVADA_Y_LUZ_DETECTADA;
}

void a_luzpuerta()
{
    current_state = ST_LUZ_DETECTADA_Y_PUERTA_ABIERTA;
}

void a_temppuerta()
{
    current_state = ST_TEMP_ELEVADA_Y_PUERTA_ABIERTA;
}

void a_completo()
{
    current_state = ST_TEMP_ELEVADA_Y_LUZ_DETECTADA_Y_PUERTA_ABIERTA;
}
// ---------- FIN FUNCIONES DE TRANSICION ---------- //

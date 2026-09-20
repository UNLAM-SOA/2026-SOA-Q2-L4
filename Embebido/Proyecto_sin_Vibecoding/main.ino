//enlace a wokwi para pruebas: https://wokwi.com/projects/474901754345519105

#include <OneWire.h>
#include <DallasTemperature.h>


// ---------- BEGIN CONSTANTS ---------- //
#define BUZZER_PIN 18
#define PHOTORESISTOR_PIN 34
#define LED_RED_PIN 25
#define LED_GREEN_PIN 26
#define LED_BLUE_PIN 33
#define TEMPERATURE_SENSOR_PIN 2
#define RELAY_PIN 19
#define BUTTON_PIN 27
// ---------- END CONSTANTS ---------- //


// ---------- BEGIN VARIABLES ---------- //
OneWire oneWire(TEMPERATURE_SENSOR_PIN);
DallasTemperature sensors(&oneWire);
// ---------- END VARIABLES ---------- //


// ---------- BEGIN TRANSITION FUNCTIONS DECLARATION ---------- //
void error();
void none();


// ---------- BEGIN STATE MACHINE ---------- //
/*TODO: definir globales segun diagrama*/
#define MAX_STATES 10
#define MAX_EVENTS 6
#define MAX_TYPE_EVENTS 1

/*TODO: sumar estados segun diagrama*/
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

/*definicion de funciones de la maquina de estados*/

typedef void (*transition)();

// ---------- Tabla de estados ---------- //
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

// ---------- Funciones de transición de estado ---------- //
void a_seguro()     { current_state = ST_SEGURO; }
void a_luz()        { current_state = ST_LUZ_DETECTADA; }
void a_temp()       { current_state = ST_TEMP_ELEVADA; }
void a_puerta()     { current_state = ST_PUERTA_ABIERTA; }
void a_luztemp()    { current_state = ST_TEMP_ELEVADA_Y_LUZ_DETECTADA; }
void a_luzpuerta()  { current_state = ST_LUZ_DETECTADA_Y_PUERTA_ABIERTA; }
void a_temppuerta() { current_state = ST_TEMP_ELEVADA_Y_PUERTA_ABIERTA; }
void a_completo()   { current_state = ST_TEMP_ELEVADA_Y_LUZ_DETECTADA_Y_PUERTA_ABIERTA; }

// ---------- END STATE MACHINE ---------- //

void setup() {
    Serial.begin(115200);
    Serial.println("Hola Mundo!");
    
    //INPUTS
    pinMode(TEMPERATURE_SENSOR_PIN,INPUT);
    pinMode(PHOTORESISTOR_PIN, INPUT);
    pinMode(BUTTON_PIN, INPUT_PULLUP);

    //OUTPUTS
    pinMode(BUZZER_PIN, OUTPUT);
    pinMode(LED_RED_PIN, OUTPUT);
    pinMode(LED_GREEN_PIN, OUTPUT);
    pinMode(LED_BLUE_PIN, OUTPUT);
    pinMode(RELAY_PIN, OUTPUT);

    sensors.begin();
}

void loop() {
  // put your main code here, to run repeatedly:

}

// ---------- TRANSITION FUNCTIONS ---------- //
void error()
{  
}

void none()
{
}
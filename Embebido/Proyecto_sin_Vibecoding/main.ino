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

// ---------- BEGIN STATE MACHINE ---------- //
/*TODO: definir globales segun diagrama*/
#define MAX_STATES 1
#define MAX_EVENTS 1
#define MAX_TYPE_EVENTS 1

/*TODO: sumar estados segun diagrama*/
enum states {} current_state;
String s_states [] = {};

/*TODO: sumar eventos segun diagrama*/
enum events {} new_event;
String s_events [] = {};

/*definicion de funciones de la maquina de estados*/

typedef void (*transition)();

/*TODO: completar tabla con funciones*/
transition state_table[MAX_STATES][MAX_EVENTS] = {

};

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

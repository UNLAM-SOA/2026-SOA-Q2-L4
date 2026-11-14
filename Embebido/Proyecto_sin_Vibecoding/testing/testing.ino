//archivo .ino temporal
//solamente con fines de testing (sensores y actuadores)

//enlace a wokwi para pruebas: https://wokwi.com/projects/474901754345519105

//IMPORTANTE: SE REQUIERE INSTALAR LAS LIBRERIAS ONEWIRE Y DALLASTEMPERATURE PARA EL MANEJO DEL SENSOR DE TEMPERATURA
#include <OneWire.h>
#include <DallasTemperature.h>


#define BUZZER_PIN 18
#define PHOTORESISTOR_PIN 34
#define LED_RED_PIN 25
#define LED_GREEN_PIN 26
#define LED_BLUE_PIN 33
#define TEMPERATURE_SENSOR_PIN 2
#define RELAY_PIN 19
#define BUTTON_PIN 27

//setup oneWire
OneWire oneWire(TEMPERATURE_SENSOR_PIN);

//pass oneWire
DallasTemperature sensors(&oneWire);

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

    /*TESTEAR POR SEPARADO*/
    
    //TEST 1: BUZZER
    //test_buzzer();
    
    //TEST 2: LDR PHOTORESISTOR
    //test_photoresistor();

    //TEST 3: LED RGB
    //test_led_rgb();

    //TEST 4: TEMPERATURE SENSOR (DS18B20)
    //test_temperature_sensor();

    //TEST 5: RELAY WITH COOLER
    //test_relay_with_cooler();

    //TEST 6: MAGNETIC SENSOR (BUTTON)
    test_magnetic_sensor();

}

void test_buzzer()
{
    delay(1000);
    tone(BUZZER_PIN, 1000);
    delay(1000);
    noTone(BUZZER_PIN);
}

void test_photoresistor()
{
    int analogValue = analogRead(PHOTORESISTOR_PIN);
    Serial.println(analogValue);
}

void test_led_rgb()
{
    turn_on_led_color(0,255,255);
    delay(1000);
    turn_on_led_color(255,0,255);
    delay(1000);
    turn_on_led_color(255,255,0);
    delay(1000);
}

void test_temperature_sensor()
{
    sensors.requestTemperatures();
    float temperatureC = sensors.getTempCByIndex(0);
    Serial.println("----Temperature----");
    Serial.print(temperatureC);
    Serial.println("°C");
    delay(1000);
}

void test_relay_with_cooler()
{
    digitalWrite(RELAY_PIN,HIGH);
    delay(1000);
    digitalWrite(RELAY_PIN,LOW);
    delay(1000);
}

void test_magnetic_sensor()
{
    int buttonState = digitalRead(BUTTON_PIN);
    if(buttonState == LOW)
    {
        Serial.println("PUERTA CERRADA");
    }
    else
    {
        Serial.println("PUERTA ABIERTA");
    }
    delay(1000);
}

void turn_on_led_color(int red_value, int green_value, int blue_value)
{
    analogWrite(LED_RED_PIN,red_value);
    analogWrite(LED_GREEN_PIN,green_value);
    analogWrite(LED_BLUE_PIN,blue_value);
}

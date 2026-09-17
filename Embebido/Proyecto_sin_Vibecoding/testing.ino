//archivo .ino temporal
//solamente con fines de testing (sensores y actuadores)

//enlace a wokwi para pruebas: https://wokwi.com/projects/474901754345519105

#define PIN_BUZZER 18

void setup() {
    Serial.begin(115200);
    Serial.println("Hola Mundo!");
    pinMode(PIN_BUZZER, OUTPUT);
}

void loop() {
    //TEST 1: BUZZER
    delay(1000);
    tone(PIN_BUZZER, 1000);
    delay(1000);
    noTone(PIN_BUZZER);

    //TEST 2: 
}

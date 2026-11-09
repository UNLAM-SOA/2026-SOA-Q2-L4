package com.example.smartinsulinguardian;

public class LecturaTemperatura {

    private final float temperatura;
    private final long marcaTiempoMs;

    public LecturaTemperatura(float temperatura, long marcaTiempoMs) {
        this.temperatura = temperatura;
        this.marcaTiempoMs = marcaTiempoMs;
    }

    public float getTemperatura() {
        return temperatura;
    }

    public long getMarcaTiempoMs() {
        return marcaTiempoMs;
    }
}
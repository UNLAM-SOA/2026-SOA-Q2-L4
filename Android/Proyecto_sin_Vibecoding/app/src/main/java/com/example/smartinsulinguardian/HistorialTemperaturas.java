package com.example.smartinsulinguardian;

import java.util.ArrayList;
import java.util.List;

public class HistorialTemperaturas {

    private static final int MAX_LECTURAS = 100;

    private final List<LecturaTemperatura> lecturas =
            new ArrayList<>();

    public synchronized void agregar(float temperatura, long marcaTiempoMs) {

        lecturas.add(new LecturaTemperatura(temperatura, marcaTiempoMs));

        if (lecturas.size() > MAX_LECTURAS) {
            lecturas.remove(0);
        }
    }

    public synchronized List<LecturaTemperatura> obtenerHistorial() {
        return new ArrayList<>(lecturas);
    }
}
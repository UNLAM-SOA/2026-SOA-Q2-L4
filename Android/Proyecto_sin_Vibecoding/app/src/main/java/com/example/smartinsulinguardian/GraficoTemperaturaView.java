package com.example.smartinsulinguardian;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Vista propia que dibuja las ultimas lecturas de temperatura como una linea,
 * con el limite maximo marcado y la hora de las lecturas en el eje horizontal.
 * No usa librerias externas. Las lecturas se agregan con agregarValor()
 * desde el hilo principal.
 */
public class GraficoTemperaturaView extends View {

    /** Formato de hora usado en el grafico y en el texto de la ultima lectura. */
    public static final String FORMATO_HORA = "HH:mm:ss";

    private static final int MAX_PUNTOS = 20;
    private static final int CANTIDAD_DIVISIONES = 4;
    private static final int PUNTOS_ENTRE_ETIQUETAS = 5;

    private static final float RANGO_MIN_C = 0f;
    private static final float RANGO_MAX_C = 12f;
    private static final float LIMITE_TEMP_MAX_C = 8f;

    private static final float MARGEN_IZQUIERDO_DP = 40f;
    private static final float MARGEN_SUPERIOR_DP = 16f;
    private static final float MARGEN_DERECHO_DP = 16f;
    private static final float MARGEN_INFERIOR_DP = 32f;
    private static final float SEPARACION_TEXTO_DP = 6f;

    private static final float GROSOR_EJE_DP = 1f;
    private static final float GROSOR_LINEA_DP = 3f;
    private static final float GROSOR_LIMITE_DP = 2f;
    private static final float RADIO_PUNTO_DP = 4f;
    private static final float LARGO_GUION_DP = 8f;
    private static final float TAMANIO_TEXTO_SP = 12f;

    private static final int ALFA_GUIAS = 60;
    private static final int COLOR_LINEA = 0xFF1E88E5;
    private static final int COLOR_ALERTA = 0xFFE53935;

    /** Una lectura de temperatura con el momento en que se tomo. */
    private static class Lectura {
        final float temperatura;
        final long marcaTiempoMs;

        Lectura(float temperatura, long marcaTiempoMs) {
            this.temperatura = temperatura;
            this.marcaTiempoMs = marcaTiempoMs;
        }
    }

    private final List<Lectura> lecturas = new ArrayList<>();
    private final Path trazo = new Path();
    private final Date fecha = new Date();
    private final SimpleDateFormat formatoHora = new SimpleDateFormat(FORMATO_HORA, Locale.getDefault());

    private final Paint pincelEje = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pincelGuias = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pincelLinea = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pincelPunto = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pincelLimite = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pincelTexto = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float densidad;
    private int totalLecturas = 0;

    public GraficoTemperaturaView(Context context) {
        super(context);
        inicializar();
    }

    public GraficoTemperaturaView(Context context, AttributeSet atributos) {
        super(context, atributos);
        inicializar();
    }

    /** Configura los pinceles. Los colores de texto y ejes siguen el tema (claro u oscuro). */
    private void inicializar() {
        densidad = getResources().getDisplayMetrics().density;
        int colorTexto = obtenerColorTextoDelTema();

        pincelEje.setColor(colorTexto);
        pincelEje.setStrokeWidth(GROSOR_EJE_DP * densidad);

        pincelGuias.setColor(ColorUtils.setAlphaComponent(colorTexto, ALFA_GUIAS));
        pincelGuias.setStrokeWidth(GROSOR_EJE_DP * densidad);

        pincelLinea.setColor(COLOR_LINEA);
        pincelLinea.setStyle(Paint.Style.STROKE);
        pincelLinea.setStrokeWidth(GROSOR_LINEA_DP * densidad);
        pincelLinea.setStrokeJoin(Paint.Join.ROUND);

        pincelPunto.setStyle(Paint.Style.FILL);

        float guion = LARGO_GUION_DP * densidad;
        pincelLimite.setColor(COLOR_ALERTA);
        pincelLimite.setStyle(Paint.Style.STROKE);
        pincelLimite.setStrokeWidth(GROSOR_LIMITE_DP * densidad);
        pincelLimite.setPathEffect(new DashPathEffect(new float[]{guion, guion}, 0f));

        pincelTexto.setColor(colorTexto);
        pincelTexto.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, TAMANIO_TEXTO_SP, getResources().getDisplayMetrics()));
    }

    /** Devuelve el color de texto principal del tema actual. */
    private int obtenerColorTextoDelTema() {
        TypedValue valor = new TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.textColorPrimary, valor, true);
        if (valor.resourceId != 0) {
            return ContextCompat.getColor(getContext(), valor.resourceId);
        }
        return valor.data;
    }

    /**
     * Agrega una lectura de temperatura (grados Celsius) con su marca de tiempo
     * (milisegundos desde epoch) y redibuja. Conserva las ultimas MAX_PUNTOS.
     */
    public void agregarValor(float temperatura, long marcaTiempoMs) {
        lecturas.add(new Lectura(temperatura, marcaTiempoMs));
        totalLecturas++;

        if (lecturas.size() > MAX_PUNTOS) {
            lecturas.remove(0);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float izquierda = MARGEN_IZQUIERDO_DP * densidad;
        float arriba = MARGEN_SUPERIOR_DP * densidad;
        float derecha = getWidth() - MARGEN_DERECHO_DP * densidad;
        float abajo = getHeight() - MARGEN_INFERIOR_DP * densidad;

        float minimo = RANGO_MIN_C;
        float maximo = RANGO_MAX_C;
        for (Lectura lectura : lecturas) {
            minimo = Math.min(minimo, lectura.temperatura);
            maximo = Math.max(maximo, lectura.temperatura);
        }

        dibujarGuiasYEjes(canvas, izquierda, arriba, derecha, abajo, minimo, maximo);
        dibujarLimite(canvas, izquierda, arriba, derecha, abajo, minimo, maximo);

        if (lecturas.isEmpty()) {
            dibujarSinDatos(canvas, izquierda, arriba, derecha, abajo);
        } else {
            dibujarLinea(canvas, izquierda, arriba, derecha, abajo, minimo, maximo);
            dibujarEtiquetasHora(canvas, izquierda, derecha, abajo);
        }
    }

    /** Convierte una temperatura en la coordenada Y de la pantalla. */
    private float convertirY(float valor, float minimo, float maximo, float arriba, float abajo) {
        return abajo - (valor - minimo) / (maximo - minimo) * (abajo - arriba);
    }

    /** Calcula la separacion horizontal entre dos lecturas consecutivas. */
    private float calcularPaso(float izquierda, float derecha) {
        return (derecha - izquierda) / (MAX_PUNTOS - 1);
    }

    /** Dibuja los ejes, las lineas de guia horizontales y sus etiquetas de temperatura. */
    private void dibujarGuiasYEjes(Canvas canvas, float izquierda, float arriba, float derecha,
                                   float abajo, float minimo, float maximo) {
        pincelTexto.setTextAlign(Paint.Align.RIGHT);
        float ajusteVertical = -(pincelTexto.ascent() + pincelTexto.descent()) / 2f;

        for (int i = 0; i <= CANTIDAD_DIVISIONES; i++) {
            float valor = minimo + (maximo - minimo) * i / CANTIDAD_DIVISIONES;
            float y = convertirY(valor, minimo, maximo, arriba, abajo);

            canvas.drawLine(izquierda, y, derecha, y, pincelGuias);
            canvas.drawText(String.format(Locale.getDefault(), "%.0f", valor),
                    izquierda - SEPARACION_TEXTO_DP * densidad, y + ajusteVertical, pincelTexto);
        }

        canvas.drawLine(izquierda, arriba, izquierda, abajo, pincelEje);
        canvas.drawLine(izquierda, abajo, derecha, abajo, pincelEje);
    }

    /** Dibuja la linea punteada del limite maximo de temperatura con su etiqueta. */
    private void dibujarLimite(Canvas canvas, float izquierda, float arriba, float derecha,
                               float abajo, float minimo, float maximo) {
        float y = convertirY(LIMITE_TEMP_MAX_C, minimo, maximo, arriba, abajo);

        canvas.drawLine(izquierda, y, derecha, y, pincelLimite);

        pincelTexto.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(getContext().getString(R.string.etiqueta_limite_max, LIMITE_TEMP_MAX_C),
                derecha, y - SEPARACION_TEXTO_DP * densidad, pincelTexto);
    }

    /** Dibuja la linea de lecturas y un punto por lectura (rojo si alcanza el limite). */
    private void dibujarLinea(Canvas canvas, float izquierda, float arriba, float derecha,
                              float abajo, float minimo, float maximo) {
        float paso = calcularPaso(izquierda, derecha);

        trazo.reset();
        for (int i = 0; i < lecturas.size(); i++) {
            float x = izquierda + i * paso;
            float y = convertirY(lecturas.get(i).temperatura, minimo, maximo, arriba, abajo);

            if (i == 0) {
                trazo.moveTo(x, y);
            } else {
                trazo.lineTo(x, y);
            }
        }
        canvas.drawPath(trazo, pincelLinea);

        for (int i = 0; i < lecturas.size(); i++) {
            float temperatura = lecturas.get(i).temperatura;
            float x = izquierda + i * paso;
            float y = convertirY(temperatura, minimo, maximo, arriba, abajo);

            pincelPunto.setColor(temperatura >= LIMITE_TEMP_MAX_C ? COLOR_ALERTA : COLOR_LINEA);
            canvas.drawCircle(x, y, RADIO_PUNTO_DP * densidad, pincelPunto);
        }
    }

    /**
     * Dibuja la hora debajo del eje X cada PUNTOS_ENTRE_ETIQUETAS lecturas. El criterio
     * usa el numero acumulado de lecturas, asi cada etiqueta acompaña a su punto cuando
     * el grafico se desplaza. Se omiten las que quedarian cortadas en los bordes.
     */
    private void dibujarEtiquetasHora(Canvas canvas, float izquierda, float derecha, float abajo) {
        float paso = calcularPaso(izquierda, derecha);
        int primerIndiceAcumulado = totalLecturas - lecturas.size();
        float baseTexto = abajo - pincelTexto.ascent() + SEPARACION_TEXTO_DP * densidad;

        pincelTexto.setTextAlign(Paint.Align.CENTER);

        for (int i = 0; i < lecturas.size(); i++) {
            if ((primerIndiceAcumulado + i) % PUNTOS_ENTRE_ETIQUETAS != 0) {
                continue;
            }

            fecha.setTime(lecturas.get(i).marcaTiempoMs);
            String hora = formatoHora.format(fecha);

            float x = izquierda + i * paso;
            float mitadAncho = pincelTexto.measureText(hora) / 2f;

            if (x - mitadAncho >= 0f && x + mitadAncho <= getWidth()) {
                canvas.drawText(hora, x, baseTexto, pincelTexto);
            }
        }
    }

    /** Dibuja un texto centrado cuando todavia no hay lecturas. */
    private void dibujarSinDatos(Canvas canvas, float izquierda, float arriba, float derecha,
                                 float abajo) {
        pincelTexto.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(getContext().getString(R.string.sin_datos_grafico),
                (izquierda + derecha) / 2f, (arriba + abajo) / 2f, pincelTexto);
    }
}
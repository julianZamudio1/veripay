package com.veripay.biometria;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.TextDetectionModel_DB;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.veripay.cliente.Curp;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import nu.pattern.OpenCV;

/**
 * Lee la CURP de una foto de la INE en tres pasos:
 * <ol>
 *   <li><b>PP-OCRv3 (OpenCV)</b> localiza los renglones de texto, aunque la credencial esté inclinada.</li>
 *   <li><b>Tesseract</b> lee cada renglón largo, enderezado y limitado a A-Z y 0-9.</li>
 *   <li>Cada lectura se corrige por posición (en la CURP hay posiciones que solo admiten letras y
 *       otras solo dígitos) y se acepta únicamente si pasa el dígito verificador de RENAPO.</li>
 * </ol>
 * El dígito verificador es lo que hace confiable el resultado: una lectura con un solo carácter
 * equivocado casi nunca produce otra CURP válida.
 */
@Component
public class LectorCurpOcr implements LectorDocumento {

    private static final Logger log = LoggerFactory.getLogger(LectorCurpOcr.class);

    private static final int LADO_MAXIMO = 1280;
    /** Un renglón con la CURP es mucho más ancho que alto; los rótulos cortos se descartan. */
    private static final double PROPORCION_MINIMA = 5.0;
    private static final double PROPORCION_CURP = 9.0;
    private static final int EDAD_MAXIMA = 110;
    private static final int ALTO_RENGLON = 64;

    // Confusiones típicas del OCR, en pares alineados por posición:
    // en posiciones de dígito, la letra se cambia por el dígito parecido (O→0, I→1, S→5, B→8…)
    private static final String LETRAS_CONFUNDIBLES = "OQDILZSBGTAY";
    private static final String SU_DIGITO           = "000112586749";
    // en posiciones de letra, el dígito se cambia por la letra parecida (0→O, 1→I, 5→S, 8→B…)
    private static final String DIGITOS             = "0123456789";
    private static final String SU_LETRA            = "OIZEASGTBG";

    private final TextDetectionModel_DB detector;
    private final Tesseract tesseract;

    public LectorCurpOcr() {
        OpenCV.loadLocally();
        Path modelo = Modelos.extraer("modelos/text_detection_en_ppocrv3_2023may.onnx");
        this.detector = new TextDetectionModel_DB(modelo.toString());
        detector.setBinaryThreshold(0.3f).setPolygonThreshold(0.5f).setUnclipRatio(2.0).setMaxCandidates(200);
        // Normalización de ImageNet, la misma con que se entrenó PP-OCRv3
        detector.setInputMean(new Scalar(123.675, 116.28, 103.53));
        detector.setInputScale(new Scalar(1 / 255.0 / 0.229, 1 / 255.0 / 0.224, 1 / 255.0 / 0.225));

        this.tesseract = new Tesseract();
        tesseract.setDatapath(Modelos.extraerCarpeta("modelos/tessdata/eng.traineddata").toString());
        tesseract.setLanguage("eng");
        tesseract.setPageSegMode(7);                       // un solo renglón
        tesseract.setVariable("tessedit_char_whitelist", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
        tesseract.setVariable("user_defined_dpi", "300");
        log.info("Lector de CURP listo (PP-OCRv3 + Tesseract)");
    }

    /** Sincronizado: ni la red de OpenCV ni la instancia de Tesseract admiten uso concurrente. */
    @Override
    public synchronized Optional<String> leerCurp(byte[] imagenIdentificacion) {
        ImagenSegura.validar(imagenIdentificacion, "identificación");
        Mat imagen = Imgcodecs.imdecode(new MatOfByte(imagenIdentificacion), Imgcodecs.IMREAD_COLOR);
        if (imagen.empty()) {
            throw new IllegalArgumentException("No se pudo leer la imagen de identificación");
        }
        double escala = Math.min(1.0, (double) LADO_MAXIMO / Math.max(imagen.cols(), imagen.rows()));
        // La red exige dimensiones múltiplos de 32
        detector.setInputSize(new Size(multiplo32(imagen.cols() * escala), multiplo32(imagen.rows() * escala)));

        List<MatOfPoint> renglones = new ArrayList<>();
        detector.detect(imagen, renglones);
        // Primero los renglones con la proporción típica de una CURP impresa (18 caracteres ≈ 9:1)
        renglones.sort(Comparator.comparingDouble((MatOfPoint r) -> Math.abs(proporcion(r.toArray()) - PROPORCION_CURP)));

        for (MatOfPoint renglon : renglones) {
            Point[] p = renglon.toArray();
            if (proporcion(p) < PROPORCION_MINIMA) {
                continue;
            }
            Optional<String> curp = buscarCurp(leer(enderezar(imagen, p)));
            if (curp.isPresent()) {
                return curp;
            }
        }
        return Optional.empty();
    }

    /**
     * Busca una CURP válida dentro del texto leído (puede venir pegada a un rótulo, como "CURPZAGE...").
     * Para cada ventana de 18 caracteres corrige las confusiones según la posición y valida.
     */
    static Optional<String> buscarCurp(String texto) {
        String limpio = texto.toUpperCase().replaceAll("[^A-Z0-9]", "");
        for (int i = 0; i + 18 <= limpio.length(); i++) {
            String corregida = corregir(limpio.substring(i, i + 18));
            for (String candidata : variantesHomoclave(corregida)) {
                if (Curp.esValida(candidata) && edadPlausible(candidata)) {
                    return Optional.of(candidata);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * O/0, G/6 y L/1 en la homoclave producen el mismo dígito verificador y solo cambian el siglo
     * (p. ej. 1985 o 2085). Se descarta la variante que daría una edad imposible.
     */
    private static boolean edadPlausible(String curp) {
        return Curp.fechaNacimiento(curp)
                .map(f -> Period.between(f, LocalDate.now()).getYears() <= EDAD_MAXIMA)
                .orElse(false);
    }

    /**
     * La posición 17 (homoclave) admite letra o dígito, así que no se puede corregir por posición:
     * se prueban la lectura original y su par confundible (O/0, I/1, S/5…). El dígito verificador
     * descarta la variante equivocada.
     */
    private static List<String> variantesHomoclave(String curp) {
        char h = curp.charAt(16);
        int comoLetra = LETRAS_CONFUNDIBLES.indexOf(h);
        int comoDigito = DIGITOS.indexOf(h);
        char alterno = comoLetra >= 0 ? SU_DIGITO.charAt(comoLetra) : comoDigito >= 0 ? SU_LETRA.charAt(comoDigito) : h;
        return alterno == h ? List.of(curp) : List.of(curp, curp.substring(0, 16) + alterno + curp.charAt(17));
    }

    /** Posiciones 5-10 (fecha) y 18 (verificador) son dígitos; 1-4, 11-16 son letras; 17 es libre. */
    static String corregir(String c) {
        StringBuilder sb = new StringBuilder(18);
        for (int i = 0; i < 18; i++) {
            char ch = c.charAt(i);
            boolean esDigito = (i >= 4 && i <= 9) || i == 17;
            boolean esLetra = i <= 3 || (i >= 10 && i <= 15);
            if (esDigito && LETRAS_CONFUNDIBLES.indexOf(ch) >= 0) {
                ch = SU_DIGITO.charAt(LETRAS_CONFUNDIBLES.indexOf(ch));
            } else if (esLetra && Character.isDigit(ch)) {
                ch = SU_LETRA.charAt(DIGITOS.indexOf(ch));
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    private String leer(Mat renglonGris) {
        try {
            return tesseract.doOCR(aImagen(renglonGris));
        } catch (TesseractException e) {
            log.warn("Tesseract no pudo leer un renglón: {}", e.getMessage());
            return "";
        }
    }

    /** Endereza el renglón a su proporción real y lo pasa a gris con un margen (Tesseract lo necesita). */
    private static Mat enderezar(Mat imagen, Point[] p) {
        double ancho = distancia(p[1], p[2]);
        double alto = distancia(p[0], p[1]);
        int w = (int) Math.round(ALTO_RENGLON * ancho / Math.max(alto, 1));
        // El detector DB entrega los vértices como: inferior izquierdo, superior izquierdo, superior derecho, inferior derecho
        MatOfPoint2f destino = new MatOfPoint2f(new Point(0, ALTO_RENGLON - 1), new Point(0, 0),
                new Point(w - 1, 0), new Point(w - 1, ALTO_RENGLON - 1));
        Mat transformacion = Imgproc.getPerspectiveTransform(new MatOfPoint2f(p), destino);
        Mat recorte = new Mat();
        Imgproc.warpPerspective(imagen, recorte, transformacion, new Size(w, ALTO_RENGLON), Imgproc.INTER_CUBIC);
        Imgproc.cvtColor(recorte, recorte, Imgproc.COLOR_BGR2GRAY);
        Core.copyMakeBorder(recorte, recorte, 12, 12, 12, 12, Core.BORDER_REPLICATE);
        return recorte;
    }

    private static BufferedImage aImagen(Mat gris) {
        BufferedImage img = new BufferedImage(gris.cols(), gris.rows(), BufferedImage.TYPE_BYTE_GRAY);
        gris.get(0, 0, ((DataBufferByte) img.getRaster().getDataBuffer()).getData());
        return img;
    }

    private static double proporcion(Point[] p) {
        return distancia(p[1], p[2]) / Math.max(distancia(p[0], p[1]), 1);
    }

    private static double distancia(Point a, Point b) {
        return Math.hypot(a.x - b.x, a.y - b.y);
    }

    private static int multiplo32(double valor) {
        return Math.max(32, (int) Math.round(valor / 32) * 32);
    }
}

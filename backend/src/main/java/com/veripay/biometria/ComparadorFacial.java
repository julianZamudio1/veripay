package com.veripay.biometria;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.FaceDetectorYN;
import org.opencv.objdetect.FaceRecognizerSF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.veripay.common.NegocioException;

import nu.pattern.OpenCV;

/**
 * Reconocimiento facial local con OpenCV:
 * <ol>
 *   <li><b>YuNet</b> localiza el rostro y 5 puntos (ojos, nariz, comisuras) en cada imagen.
 *       En una INE se toma el rostro más grande (la credencial también trae una foto fantasma pequeña).</li>
 *   <li><b>SFace</b> alinea el rostro con esos puntos y lo convierte en un vector de 128 dimensiones.</li>
 *   <li>El puntaje es la similitud coseno entre ambos vectores (de -1 a 1).</li>
 * </ol>
 * Umbral 0.363: el recomendado por OpenCV para SFace con distancia coseno (LFW, precisión ~99.6%).
 * No incluye prueba de vida: una foto impresa de la persona también coincidiría.
 */
@Component
@ConditionalOnProperty(name = "veripay.biometria.motor", havingValue = "facial", matchIfMissing = true)
public class ComparadorFacial implements ComparadorBiometrico {

    private static final Logger log = LoggerFactory.getLogger(ComparadorFacial.class);

    static final BigDecimal UMBRAL = new BigDecimal("0.363");
    /** Las fotos de teléfono se reducen a este lado máximo antes de buscar el rostro. */
    private static final int LADO_MAXIMO = 1280;
    /** Columnas 2 y 3 de cada fila que devuelve YuNet: ancho y alto del rostro. */
    private static final int COL_ANCHO = 2;
    private static final int COL_ALTO = 3;

    private final FaceDetectorYN detector;
    private final FaceRecognizerSF reconocedor;

    public ComparadorFacial() {
        OpenCV.loadLocally();
        // OpenCV solo carga modelos desde archivo: se copian del WAR/JAR a una carpeta temporal
        Path yunet = Modelos.extraer("modelos/face_detection_yunet_2023mar.onnx");
        Path sface = Modelos.extraer("modelos/face_recognition_sface_2021dec.onnx");
        this.detector = FaceDetectorYN.create(yunet.toString(), "", new Size(320, 320), 0.8f, 0.3f, 5000);
        this.reconocedor = FaceRecognizerSF.create(sface.toString(), "");
        log.info("Reconocimiento facial listo (YuNet + SFace, umbral {})", UMBRAL);
    }

    @Override
    public BigDecimal umbralRecomendado() {
        return UMBRAL;
    }

    /** Sincronizado: las redes de OpenCV no son seguras para uso concurrente. */
    @Override
    public synchronized BigDecimal comparar(byte[] imagenIdentificacion, byte[] imagenSelfie) {
        Mat rasgosId = rasgos(imagenIdentificacion, "identificación");
        Mat rasgosSelfie = rasgos(imagenSelfie, "selfie");
        double coseno = reconocedor.match(rasgosId, rasgosSelfie, FaceRecognizerSF.FR_COSINE);
        return BigDecimal.valueOf(coseno).setScale(4, RoundingMode.HALF_UP);
    }

    private Mat rasgos(byte[] datos, String etiqueta) {
        ImagenSegura.validar(datos, etiqueta);
        // IMREAD_COLOR respeta la orientación EXIF: las selfies de teléfono llegan derechas
        Mat imagen = Imgcodecs.imdecode(new MatOfByte(datos), Imgcodecs.IMREAD_COLOR);
        if (imagen.empty()) {
            throw new IllegalArgumentException("No se pudo leer la imagen de " + etiqueta);
        }
        imagen = reducir(imagen);

        detector.setInputSize(new Size(imagen.cols(), imagen.rows()));
        Mat rostros = new Mat();
        detector.detect(imagen, rostros);
        if (rostros.rows() == 0) {
            throw new NegocioException("ROSTRO_NO_DETECTADO", "No se encontró un rostro en la imagen de " + etiqueta
                    + ". Usa una foto nítida, de frente y con buena luz.");
        }

        Mat alineado = new Mat();
        reconocedor.alignCrop(imagen, rostros.row(rostroMasGrande(rostros)), alineado);
        Mat rasgos = new Mat();
        reconocedor.feature(alineado, rasgos);
        return rasgos.clone();
    }

    private static int rostroMasGrande(Mat rostros) {
        int mejor = 0;
        double mayorArea = -1;
        for (int i = 0; i < rostros.rows(); i++) {
            double area = rostros.get(i, COL_ANCHO)[0] * rostros.get(i, COL_ALTO)[0];
            if (area > mayorArea) {
                mayorArea = area;
                mejor = i;
            }
        }
        return mejor;
    }

    private static Mat reducir(Mat imagen) {
        int lado = Math.max(imagen.cols(), imagen.rows());
        if (lado <= LADO_MAXIMO) {
            return imagen;
        }
        double escala = (double) LADO_MAXIMO / lado;
        Mat reducida = new Mat();
        Imgproc.resize(imagen, reducida, new Size(), escala, escala, Imgproc.INTER_AREA);
        return reducida;
    }
}

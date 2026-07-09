package com.elias.mismonedas.adapter.ocr;

import com.elias.mismonedas.exception.OcrBusyException;
import com.elias.mismonedas.exception.OcrProcessingException;
import com.elias.mismonedas.port.OcrPort;
import com.elias.mismonedas.port.OcrResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
public class TesseractOcrAdapter implements OcrPort {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcrAdapter.class);

    private final Semaphore concurrencyLimiter;
    private final String tessdataPath;
    private final long timeoutSeconds;
    private final int maxLongEdgePx;

    public TesseractOcrAdapter(
            @Value("${ocr.tessdata-path:/usr/share/tessdata-fast}") String tessdataPath,
            @Value("${ocr.max-concurrent:1}") int maxConcurrent,
            @Value("${ocr.timeout-seconds:20}") long timeoutSeconds,
            @Value("${ocr.max-long-edge-px:1600}") int maxLongEdgePx) {
        this.tessdataPath = tessdataPath;
        this.concurrencyLimiter = new Semaphore(maxConcurrent);
        this.timeoutSeconds = timeoutSeconds;
        this.maxLongEdgePx = maxLongEdgePx;
    }

    @Override
    public OcrResult extractText(byte[] imageBytes) {
        long start = System.currentTimeMillis();
        if (!concurrencyLimiter.tryAcquire()) {
            throw new OcrBusyException("El servicio de escaneo está ocupado, intenta en unos segundos");
        }
        Path tempImage = null;
        Path tempOutputBase = null;
        try {
            byte[] preprocessed = downscaleAndGrayscale(imageBytes, maxLongEdgePx);

            tempImage = Files.createTempFile("ocr-in-", ".png");
            Files.write(tempImage, preprocessed);
            tempOutputBase = Files.createTempFile("ocr-out-", "");
            Files.deleteIfExists(tempOutputBase);

            ProcessBuilder pb = new ProcessBuilder(
                    "tesseract", tempImage.toString(), tempOutputBase.toString(),
                    "-l", "spa_fast", "--psm", "6", "--tessdata-dir", tessdataPath
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new OcrProcessingException("OCR excedió el tiempo límite de " + timeoutSeconds + "s");
            }
            if (process.exitValue() != 0) {
                String errOutput = new String(process.getInputStream().readAllBytes());
                throw new OcrProcessingException("Tesseract falló (exit " + process.exitValue() + "): " + errOutput);
            }

            String text = Files.readString(Path.of(tempOutputBase + ".txt"));
            long durationMs = System.currentTimeMillis() - start;

            log.info("ocr_invocation duration_ms={} image_bytes={} outcome=success", durationMs, imageBytes.length);

            return new OcrResult(text, estimateConfidence(text), durationMs);

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("ocr_invocation duration_ms={} image_bytes={} outcome=error error={}",
                    System.currentTimeMillis() - start, imageBytes.length, e.getMessage());
            throw new OcrProcessingException("No se pudo procesar la imagen del recibo", e);
        } finally {
            concurrencyLimiter.release();
            cleanupQuietly(tempImage, tempOutputBase);
        }
    }

    private byte[] downscaleAndGrayscale(byte[] original, int maxLongEdge) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(original));
        if (img == null) throw new OcrProcessingException("El archivo no es una imagen válida");

        int w = img.getWidth(), h = img.getHeight();
        double scale = Math.min(1.0, (double) maxLongEdge / Math.max(w, h));
        int newW = Math.max(1, (int) (w * scale));
        int newH = Math.max(1, (int) (h * scale));

        BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, newW, newH, null);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(resized, "png", out);
        return out.toByteArray();
    }

    private double estimateConfidence(String rawText) {
        return rawText.isBlank() ? 0.0 : 0.6;
    }

    private void cleanupQuietly(Path... paths) {
        for (Path p : paths) {
            if (p == null) continue;
            try {
                Files.deleteIfExists(p);
                Files.deleteIfExists(Path.of(p + ".txt"));
            } catch (IOException ignored) {}
        }
    }
}

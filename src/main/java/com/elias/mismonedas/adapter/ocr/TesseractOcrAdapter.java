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
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
public class TesseractOcrAdapter implements OcrPort {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcrAdapter.class);

    private final Semaphore concurrencyLimiter;
    private final String tesseractPath;
    private final String tessdataPath;
    private final long timeoutSeconds;
    private final int maxLongEdgePx;
    private final double deskewThreshold;
    private final String language;
    private final List<Integer> psmModes;

    public TesseractOcrAdapter(
            @Value("${app.ocr.tesseract-path:tesseract}") String tesseractPath,
            @Value("${app.ocr.tessdata-path:/usr/share/tessdata-fast}") String tessdataPath,
            @Value("${app.ocr.max-concurrent:1}") int maxConcurrent,
            @Value("${app.ocr.timeout-seconds:20}") long timeoutSeconds,
            @Value("${app.ocr.max-long-edge-px:1600}") int maxLongEdgePx,
            @Value("${app.ocr.deskew-threshold:0.5}") double deskewThreshold,
            @Value("${app.ocr.language:spa}") String language,
            @Value("${app.ocr.tesseract-psm-modes:3,4,6}") String psmModesStr) {
        this.tesseractPath = tesseractPath;
        this.tessdataPath = tessdataPath;
        this.concurrencyLimiter = new Semaphore(maxConcurrent);
        this.timeoutSeconds = timeoutSeconds;
        this.maxLongEdgePx = maxLongEdgePx;
        this.deskewThreshold = deskewThreshold;
        this.language = language;
        this.psmModes = parsePsmModes(psmModesStr);
    }

    private static List<Integer> parsePsmModes(String csv) {
        return List.of(csv.split(",")).stream()
                .map(String::trim)
                .map(Integer::parseInt)
                .toList();
    }

    @Override
    public OcrResult extractText(byte[] imageBytes) {
        long start = System.currentTimeMillis();
        if (!concurrencyLimiter.tryAcquire()) {
            throw new OcrBusyException("El servicio de escaneo está ocupado, intenta en unos segundos");
        }
        Path tempImage = null;
        List<Path> tempOutputs = new java.util.ArrayList<>();
        try {
            byte[] preprocessed = preprocess(imageBytes, maxLongEdgePx, deskewThreshold);

            tempImage = Files.createTempFile("ocr-in-", ".png");
            Files.write(tempImage, preprocessed);

            String bestText = "";
            double bestConfidence = 0.0;
            int bestPsm = psmModes.getFirst();
            for (int psm : psmModes) {
                Path outBase = Files.createTempFile("ocr-out-" + psm + "-", "");
                Files.deleteIfExists(outBase);
                tempOutputs.add(outBase);

                ProcessBuilder pb = new ProcessBuilder(
                        tesseractPath, tempImage.toString(), outBase.toString(),
                        "-l", language, "--psm", String.valueOf(psm), "--tessdata-dir", tessdataPath
                );
                pb.redirectErrorStream(true);
                Process process = pb.start();

                boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    log.warn("ocr_psm_{}_timeout", psm);
                    continue;
                }
                if (process.exitValue() != 0) {
                    String errOutput = new String(process.getInputStream().readAllBytes());
                    log.warn("ocr_psm_{}_failed exit={} error={}", psm, process.exitValue(), errOutput);
                    continue;
                }

                String text = readOutputFile(outBase, "txt");
                if (text != null) {
                    double confidence = 0.0;
                    Path tsvFile = Path.of(outBase + ".tsv");
                    if (Files.exists(tsvFile)) {
                        try {
                            String tsvContent = Files.readString(tsvFile);
                            confidence = parseTsvConfidence(tsvContent);
                        } catch (Exception e) {
                            log.trace("ocr_psm_{}_tsv_parse_failed, using default confidence", psm);
                        }
                    }
                    log.debug("ocr_psm_{}_text_length={} confidence={}", psm, text.length(), confidence);
                    if (text.length() > bestText.length()) {
                        bestText = text;
                        bestConfidence = confidence;
                        bestPsm = psm;
                    }
                }
            }

            if (bestText.isBlank()) {
                throw new OcrProcessingException("Tesseract no produjo texto con ningún modo PSM: " + psmModes);
            }

            long durationMs = System.currentTimeMillis() - start;
            log.info("ocr_invocation duration_ms={} image_bytes={} psm={} outcome=success", durationMs, imageBytes.length, bestPsm);
            log.debug("ocr_output_text_length={} text_preview={}", bestText.length(), truncate(bestText, 500));

            return new OcrResult(bestText, bestConfidence, durationMs);

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("ocr_invocation duration_ms={} image_bytes={} outcome=error error={}",
                    System.currentTimeMillis() - start, imageBytes.length, e.getMessage());
            throw new OcrProcessingException("No se pudo procesar la imagen del recibo", e);
        } finally {
            concurrencyLimiter.release();
            cleanupQuietly(tempImage);
            for (Path p : tempOutputs) {
                cleanupQuietly(p);
            }
        }
    }

    private byte[] preprocess(byte[] original, int maxLongEdge, double deskewThreshold) throws IOException {
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

        BufferedImage binarized = binarizeOtsu(resized);

        BufferedImage deskewed = deskew(binarized, deskewThreshold);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(deskewed, "png", out);
        return out.toByteArray();
    }

    private BufferedImage binarizeOtsu(BufferedImage gray) {
        int w = gray.getWidth(), h = gray.getHeight();
        int[] pixels = gray.getRaster().getPixels(0, 0, w, h, (int[]) null);

        int threshold = otsuThreshold(pixels);

        BufferedImage binary = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = pixels[y * w + x] < threshold ? 0 : 255;
                binary.getRaster().setSample(x, y, 0, v);
            }
        }
        return binary;
    }

    private int otsuThreshold(int[] pixels) {
        int[] histogram = new int[256];
        for (int p : pixels) histogram[p & 0xff]++;

        int total = pixels.length;
        float sum = 0;
        for (int i = 0; i < 256; i++) sum += i * histogram[i];

        float sumB = 0;
        int wB = 0;
        float maxVariance = 0;
        int threshold = 128;

        for (int i = 0; i < 256; i++) {
            wB += histogram[i];
            if (wB == 0) continue;
            int wF = total - wB;
            if (wF == 0) break;

            sumB += i * histogram[i];
            float mB = sumB / wB;
            float mF = (sum - sumB) / wF;
            float variance = (float) wB * wF * (mB - mF) * (mB - mF);

            if (variance > maxVariance) {
                maxVariance = variance;
                threshold = i;
            }
        }
        return threshold;
    }

    private BufferedImage deskew(BufferedImage img, double threshold) {
        int maxAngle = 5;
        double bestAngle = 0;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (int angle = -maxAngle; angle <= maxAngle; angle++) {
            BufferedImage rotated = rotate(img, angle);
            double score = projectionVariance(rotated);
            if (score > bestScore) {
                bestScore = score;
                bestAngle = angle;
            }
        }

        if (Math.abs(bestAngle) < threshold) {
            return img;
        }

        log.info("ocr_deskew angle={}", bestAngle);
        return rotate(img, bestAngle);
    }

    private BufferedImage rotate(BufferedImage img, double angleDeg) {
        double rad = Math.toRadians(angleDeg);
        int w = img.getWidth(), h = img.getHeight();
        int newW = (int) Math.ceil(Math.abs(w * Math.cos(rad)) + Math.abs(h * Math.sin(rad)));
        int newH = (int) Math.ceil(Math.abs(w * Math.sin(rad)) + Math.abs(h * Math.cos(rad)));

        BufferedImage rotated = new BufferedImage(newW, newH, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = rotated.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, newW, newH);
        g.translate((newW - w) / 2.0, (newH - h) / 2.0);
        g.rotate(rad, w / 2.0, h / 2.0);
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return rotated;
    }

    private double projectionVariance(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int[] rows = new int[h];
        for (int y = 0; y < h; y++) {
            int sum = 0;
            for (int x = 0; x < w; x++) {
                sum += img.getRaster().getSample(x, y, 0);
            }
            rows[y] = sum;
        }
        double mean = 0;
        for (int v : rows) mean += v;
        mean /= h;
        double variance = 0;
        for (int v : rows) variance += (v - mean) * (v - mean);
        return variance / h;
    }

    private double parseTsvConfidence(String tsv) {
        double confSum = 0;
        int confCount = 0;

        String[] lines = tsv.split("\\r?\\n");
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isBlank()) continue;

            String[] cols = line.split("\\t");
            if (cols.length < 12) continue;

            try {
                int level = Integer.parseInt(cols[0]);
                if (level != 5) continue;

                String confStr = cols[10].trim();
                if (confStr.isEmpty()) continue;
                int conf = Integer.parseInt(confStr);
                if (conf >= 0) {
                    confSum += conf;
                    confCount++;
                }
            } catch (NumberFormatException e) {
                log.trace("ocr_tsv_skip_line: {}", line);
            }
        }

        return confCount > 0 ? confSum / confCount / 100.0 : 0.0;
    }

    private String readOutputFile(Path outBase, String extension) {
        Path file = Path.of(outBase + "." + extension);
        try {
            if (Files.exists(file)) {
                return Files.readString(file).trim();
            }
        } catch (IOException e) {
            log.warn("ocr_read_{}_failed: {}", extension, e.getMessage());
        }
        return null;
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "null";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private void cleanupQuietly(Path... paths) {
        for (Path p : paths) {
            if (p == null) continue;
            try {
                Files.deleteIfExists(p);
                Files.deleteIfExists(Path.of(p + ".txt"));
                Files.deleteIfExists(Path.of(p + ".tsv"));
            } catch (IOException ignored) {}
        }
    }
}

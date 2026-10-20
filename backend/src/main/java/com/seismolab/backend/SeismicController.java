
package com.seismolab.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/seismic")
@CrossOrigin(origins = "http://localhost:5173")
public class SeismicController {

    private final ObjectMapper mapper = new ObjectMapper();

    /*
     * Endpoint 1:
     * Upload CSV and perform analysis in Java.
     */
    @PostMapping(
        value = "/upload",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public Map<String, Object> uploadCsv(
            @RequestParam("file") MultipartFile file) throws IOException {

        validateFile(file, ".csv");

        List<Double> samples = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                    file.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            boolean headerFound = false;
            int sampleColumn = -1;

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                String[] columns = line.split(",", -1);

                if (!headerFound) {
                    for (int i = 0; i < columns.length; i++) {
                        String header = columns[i]
                                .replace("\uFEFF", "")
                                .replace("\"", "")
                                .trim()
                                .toLowerCase(Locale.ROOT);

                        if (header.equals("sample")) {
                            sampleColumn = i;
                        }
                    }

                    if (sampleColumn >= 0) {
                        headerFound = true;
                    }

                    continue;
                }

                if (sampleColumn >= columns.length) {
                    continue;
                }

                try {
                    double value = Double.parseDouble(
                        columns[sampleColumn]
                            .replace("\"", "")
                            .trim()
                    );

                    if (Double.isFinite(value)) {
                        samples.add(value);
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore blank or invalid sample rows.
                }
            }
        }

        if (samples.isEmpty()) {
            throw new IllegalArgumentException(
                "No numeric samples found. CSV must contain a Sample column."
            );
        }

        // The current project uses a 20 Hz sampling rate.
        double sampleRate = 20.0;
        int n = samples.size();

        // Remove DC offset.
        double mean = samples.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        List<Double> centered = samples.stream()
                .map(value -> value - mean)
                .collect(Collectors.toList());

        // Five-point moving-average FIR filter.
        List<Double> filtered = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            double sum = 0.0;
            int count = 0;

            for (int j = Math.max(0, i - 2);
                    j <= Math.min(n - 1, i + 2); j++) {
                sum += centered.get(j);
                count++;
            }

            filtered.add(sum / count);
        }

        double min = centered.stream()
                .mapToDouble(Double::doubleValue)
                .min().orElse(0.0);

        double max = centered.stream()
                .mapToDouble(Double::doubleValue)
                .max().orElse(0.0);

        double sumSquares = centered.stream()
                .mapToDouble(v -> v * v)
                .sum();

        double rms = Math.sqrt(sumSquares / n);
        double duration = (n - 1) / sampleRate;

        // Single-sided spectrum using a Hann window and direct DFT.
        int spectrumSize = n / 2 + 1;
        List<Map<String, Object>> spectrum = new ArrayList<>();

        double dominantFrequency = 0.0;
        double largestAmplitude = -1.0;

        for (int k = 0; k < spectrumSize; k++) {
            double real = 0.0;
            double imaginary = 0.0;

            for (int i = 0; i < n; i++) {
                double window = n == 1 ? 1.0
                    : 0.5 - 0.5 * Math.cos(
                        2.0 * Math.PI * i / (n - 1));

                double angle = 2.0 * Math.PI * k * i / n;
                double value = centered.get(i) * window;

                real += value * Math.cos(angle);
                imaginary -= value * Math.sin(angle);
            }

            double amplitude = Math.hypot(real, imaginary) / n;

            if (k > 0 && k < n / 2) {
                amplitude *= 2.0;
            }

            double frequency = k * sampleRate / n;

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("frequency", frequency);
            point.put("amplitude", amplitude);
            spectrum.add(point);

            if (k > 0 && amplitude > largestAmplitude) {
                largestAmplitude = amplitude;
                dominantFrequency = frequency;
            }
        }

        // Short-time energy using a 0.5 second window.
        int windowSize = Math.max(
            1, (int) Math.round(sampleRate * 0.5));

        List<Double> energies = new ArrayList<>();
        double energySum = 0.0;

        for (int i = 0; i < n; i++) {
            double squared = centered.get(i) * centered.get(i);
            energySum += squared;

            if (i >= windowSize) {
                double old = centered.get(i - windowSize);
                energySum -= old * old;
            }

            int count = Math.min(i + 1, windowSize);
            energies.add(energySum / count);
        }

        double energyMean = energies.stream()
                .mapToDouble(Double::doubleValue)
                .average().orElse(0.0);

        double variance = energies.stream()
                .mapToDouble(v -> (v - energyMean) * (v - energyMean))
                .average().orElse(0.0);

        double threshold = energyMean + 2.0 * Math.sqrt(variance);

        int detectedCount = 0;
        double firstDetectionTime = -1.0;
        double maximumEnergy = 0.0;
        double timeOfMaximumEnergy = 0.0;

        List<Map<String, Object>> energySeries = new ArrayList<>();
        List<Map<String, Object>> waveform = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            double time = i / sampleRate;
            double energy = energies.get(i);

            if (energy > threshold) {
                detectedCount++;

                if (firstDetectionTime < 0) {
                    firstDetectionTime = time;
                }
            }

            if (energy > maximumEnergy) {
                maximumEnergy = energy;
                timeOfMaximumEnergy = time;
            }

            Map<String, Object> energyPoint = new LinkedHashMap<>();
            energyPoint.put("time", time);
            energyPoint.put("energy", energy);
            energyPoint.put("threshold", threshold);
            energySeries.add(energyPoint);

            Map<String, Object> wavePoint = new LinkedHashMap<>();
            wavePoint.put("time", time);
            wavePoint.put("original", centered.get(i));
            wavePoint.put("filtered", filtered.get(i));
            waveform.add(wavePoint);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "Java");
        result.put("filename", file.getOriginalFilename());
        result.put("message", "CSV analyzed successfully.");
        result.put("sampleRate", sampleRate);
        result.put("sampleCount", n);
        result.put("duration", duration);
        result.put("minimumAmplitude", min);
        result.put("maximumAmplitude", max);
        result.put("maximumAbsoluteAmplitude",
                Math.max(Math.abs(min), Math.abs(max)));
        result.put("peakToPeakAmplitude", max - min);
        result.put("rmsAmplitude", rms);
        result.put("dominantFrequency", dominantFrequency);
        result.put("spectralPeakAmplitude",
                Math.max(0.0, largestAmplitude));
        result.put("frequencyResolution", sampleRate / n);
        result.put("nyquistFrequency", sampleRate / 2.0);
        result.put("maximumEnergy", maximumEnergy);
        result.put("timeOfMaximumEnergy", timeOfMaximumEnergy);
        result.put("detectionThreshold", threshold);
        result.put("detectedSampleCount", detectedCount);
        result.put("detectionStatus", detectedCount > 0);
        result.put("firstDetectionTime", firstDetectionTime);
        result.put("filterOrder", 4);
        result.put("filterType", "5-point moving average FIR");
        result.put("waveform", waveform);
        result.put("energySeries", energySeries);
        result.put("spectrum", spectrum);

        return result;
    }

    /*
     * Endpoint 2:
     * Accept summary results exported from MATLAB Online.
     * MATLAB performs the analysis; this endpoint validates the results.
     */
    @PostMapping(
        value = "/matlab-results",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public Map<String, Object> uploadMatlabResults(
            @RequestParam("file") MultipartFile file) throws IOException {

        validateFile(file, ".json");

        JsonNode json = mapper.readTree(file.getInputStream());

        String[] requiredFields = {
            "sampleRate",
            "duration",
            "minimumAmplitude",
            "maximumAmplitude",
            "rmsAmplitude",
            "dominantFrequency",
            "detectionThreshold",
            "detectedSampleCount",
            "detectionStatus"
        };

        for (String field : requiredFields) {
            if (!json.hasNonNull(field)) {
                throw new IllegalArgumentException(
                    "Missing MATLAB field: " + field);
            }
        }

        String[] numericFields = {
            "sampleRate",
            "duration",
            "minimumAmplitude",
            "maximumAmplitude",
            "rmsAmplitude",
            "dominantFrequency",
            "detectionThreshold",
            "detectedSampleCount"
        };

        for (String field : numericFields) {
            if (!json.get(field).isNumber()) {
                throw new IllegalArgumentException(
                    "MATLAB field must be numeric: " + field);
            }
        }

        double sampleRate = json.get("sampleRate").asDouble();
        double duration = json.get("duration").asDouble();
        double min = json.get("minimumAmplitude").asDouble();
        double max = json.get("maximumAmplitude").asDouble();
        double rms = json.get("rmsAmplitude").asDouble();
        double frequency = json.get("dominantFrequency").asDouble();
        double threshold = json.get("detectionThreshold").asDouble();
        double detectedValue = json.get("detectedSampleCount").asDouble();

        if (!Double.isFinite(sampleRate) || sampleRate <= 0
                || !Double.isFinite(duration) || duration < 0
                || !Double.isFinite(min) || !Double.isFinite(max)
                || min > max
                || !Double.isFinite(rms) || rms < 0
                || !Double.isFinite(frequency) || frequency < 0
                || frequency > sampleRate / 2.0
                || !Double.isFinite(threshold)
                || detectedValue < 0
                || detectedValue != Math.floor(detectedValue)
                || detectedValue > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                "MATLAB JSON contains invalid values.");
        }

        JsonNode statusNode = json.get("detectionStatus");
        String statusText = statusNode.asText();

        if (!(statusText.equalsIgnoreCase("true")
                || statusText.equalsIgnoreCase("false")
                || statusText.equals("1")
                || statusText.equals("0"))) {
            throw new IllegalArgumentException(
                "detectionStatus must be true, false, 1, or 0.");
        }

        boolean detected = statusText.equalsIgnoreCase("true")
                || statusText.equals("1");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "MATLAB");
        result.put("filename", file.getOriginalFilename());
        result.put("message", "MATLAB results validated successfully.");
        result.put("sampleRate", sampleRate);
        result.put("duration", duration);
        result.put("minimumAmplitude", min);
        result.put("maximumAmplitude", max);
        result.put("peakToPeakAmplitude", max - min);
        result.put("rmsAmplitude", rms);
        result.put("dominantFrequency", frequency);
        result.put("detectionThreshold", threshold);
        result.put("detectedSampleCount", (int) detectedValue);
        result.put("detectionStatus", detected);

        // MATLAB's current JSON contains summary values only.
        // Return optional fields only when the JSON actually provides them.
        copyOptionalNumber(json, result, "sampleCount");
        copyOptionalNumber(json, result, "maximumAbsoluteAmplitude");
        copyOptionalNumber(json, result, "spectralPeakAmplitude");
        copyOptionalNumber(json, result, "frequencyResolution");
        copyOptionalNumber(json, result, "nyquistFrequency");
        copyOptionalNumber(json, result, "maximumEnergy");
        copyOptionalNumber(json, result, "timeOfMaximumEnergy");
        copyOptionalNumber(json, result, "firstDetectionTime");
        copyOptionalNumber(json, result, "filterOrder");

        if (json.hasNonNull("waveform")
                && json.get("waveform").isArray()) {
            result.put("waveform", json.get("waveform"));
        }

        if (json.hasNonNull("energySeries")
                && json.get("energySeries").isArray()) {
            result.put("energySeries", json.get("energySeries"));
        }

        if (json.hasNonNull("spectrum")
                && json.get("spectrum").isArray()) {
            result.put("spectrum", json.get("spectrum"));
        }

        return result;
    }

    private void validateFile(
            MultipartFile file, String expectedExtension) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                "Please select a file to upload.");
        }

        String filename = file.getOriginalFilename();

        if (filename == null
                || !filename.toLowerCase(Locale.ROOT)
                    .endsWith(expectedExtension)) {
            throw new IllegalArgumentException(
                "Please upload a " + expectedExtension + " file.");
        }
    }

    private void copyOptionalNumber(
            JsonNode source,
            Map<String, Object> destination,
            String field) {

        if (source.hasNonNull(field) && source.get(field).isNumber()) {
            destination.put(field, source.get(field).numberValue());
        }
    }
}

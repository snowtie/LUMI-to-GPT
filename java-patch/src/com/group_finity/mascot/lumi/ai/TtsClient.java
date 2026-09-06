/*
 * Decompiled with CFR 0.152.
 */
package com.group_finity.mascot.lumi.ai;

import com.group_finity.mascot.lumi.SpeechDirector;
import com.group_finity.mascot.lumi.ai.AiSettings;
import com.group_finity.mascot.lumi.ai.MiniJson;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;

public final class TtsClient {
    private static final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
    private static volatile String lastGptSovitsDeviceStatus = "미리듣기로 확인";

    private TtsClient() {
    }

    public static boolean configured() {
        AiSettings aiSettings = AiSettings.get();
        return switch (aiSettings.ttsProvider()) {
            case "gpt_sovits" -> true;
            case "eleven" -> !aiSettings.elevenKey().isEmpty();
            default -> !aiSettings.fishKey().isEmpty();
        };
    }

    public static Audio synthesize(String string) throws Exception {
        return TtsClient.synthesize(string, null);
    }

    public static Audio synthesize(String string, String string2) throws Exception {
        AiSettings aiSettings = AiSettings.get();
        return switch (aiSettings.ttsProvider()) {
            case "gpt_sovits" -> TtsClient.gptSovits(string, string2);
            case "eleven" -> TtsClient.eleven(aiSettings, string, string2);
            default -> TtsClient.fish(aiSettings, string, string2);
        };
    }

    private static Audio gptSovits(String string, String string2) throws Exception {
        AiSettings aiSettings = AiSettings.get();
        LinkedHashMap<String, String> linkedHashMap = new LinkedHashMap<String, String>();
        linkedHashMap.put("text", string);
        linkedHashMap.put("character", string2 == null ? "" : string2);
        linkedHashMap.put("base_url", aiSettings.gptSovitsBase());
        linkedHashMap.put("runtime_dir", aiSettings.gptSovitsRuntime());
        linkedHashMap.put("gpt_weights_path", aiSettings.gptSovitsGptWeights());
        linkedHashMap.put("sovits_weights_path", aiSettings.gptSovitsSovitsWeights());
        linkedHashMap.put("reference_audio_path", aiSettings.gptSovitsReferenceAudio());
        linkedHashMap.put("prompt_text", aiSettings.gptSovitsReferenceText());
        linkedHashMap.put("text_language", aiSettings.gptSovitsTextLanguage());
        linkedHashMap.put("prompt_language", aiSettings.gptSovitsPromptLanguage());
        linkedHashMap.put("power_mode", aiSettings.gptSovitsPowerMode());
        linkedHashMap.put("device_mode", aiSettings.gptSovitsDeviceMode());
        linkedHashMap.put("speed_factor", aiSettings.gptSovitsSpeed());
        String string3 = aiSettings.llmBaseFor("gpt_web");
        AiSettings.ensureGptWebBridgeReady(string3);
        string3 = string3.replaceAll("/v1/?$", "").replaceAll("/+$", "");
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(string3 + "/voice/synthesize"))
                .timeout(Duration.ofSeconds(150L))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MiniJson.write(linkedHashMap), StandardCharsets.UTF_8))
                .build();
        HttpResponse<byte[]> httpResponse = TtsClient.sendResponse(httpRequest);
        return TtsClient.wavAudio(httpResponse.body());
    }

    private static Audio fish(AiSettings aiSettings, String string, String string2) throws Exception {
        String string3;
        if (aiSettings.fishKey().isEmpty()) {
            throw new IllegalStateException("no-tts-key");
        }
        int n = 44100;
        String string4 = string3 = string2 == null ? aiSettings.fishVoice() : aiSettings.fishVoiceFor(string2);
        if (string3.isEmpty()) {
            throw TtsClient.noVoiceId();
        }
        LinkedHashMap<String, Object> linkedHashMap = new LinkedHashMap<String, Object>();
        linkedHashMap.put("text", string);
        linkedHashMap.put("reference_id", string3);
        linkedHashMap.put("format", "pcm");
        linkedHashMap.put("sample_rate", n);
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create("https://api.fish.audio/v1/tts")).timeout(Duration.ofSeconds(60L)).header("Content-Type", "application/json").header("Authorization", "Bearer " + aiSettings.fishKey()).header("model", aiSettings.fishModel()).POST(HttpRequest.BodyPublishers.ofString(MiniJson.write(linkedHashMap), StandardCharsets.UTF_8)).build();
        return TtsClient.pcmAudio(TtsClient.send(httpRequest), n);
    }

    private static Audio eleven(AiSettings aiSettings, String string, String string2) throws Exception {
        String string3;
        if (aiSettings.elevenKey().isEmpty()) {
            throw new IllegalStateException("no-tts-key");
        }
        int n = 16000;
        String string4 = string3 = string2 == null ? aiSettings.elevenVoice() : aiSettings.elevenVoiceFor(string2);
        if (string3.isEmpty()) {
            throw TtsClient.noVoiceId();
        }
        LinkedHashMap<String, String> linkedHashMap = new LinkedHashMap<String, String>();
        linkedHashMap.put("text", string);
        linkedHashMap.put("model_id", aiSettings.elevenModel());
        String string5 = "https://api.elevenlabs.io/v1/text-to-speech/" + URLEncoder.encode(string3, StandardCharsets.UTF_8) + "?output_format=pcm_" + n;
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(string5)).timeout(Duration.ofSeconds(60L)).header("Content-Type", "application/json").header("xi-api-key", aiSettings.elevenKey()).POST(HttpRequest.BodyPublishers.ofString(MiniJson.write(linkedHashMap), StandardCharsets.UTF_8)).build();
        return TtsClient.pcmAudio(TtsClient.send(httpRequest), n);
    }

    private static IllegalStateException noVoiceId() {
        return new IllegalStateException(SpeechDirector.localized("AiTtsNoVoiceId", "\ubcf4\uc774\uc2a4 ID\ub97c \ub123\uc5b4 \uc8fc\uc138\uc694 \u2014 \ubaa9\uc18c\ub9ac \ud0ed\uc758 \ubcf4\uc774\uc2a4 ID \uce78\uc774 \ube44\uc5b4 \uc788\uc5b4\uc694."));
    }

    private static byte[] send(HttpRequest httpRequest) throws Exception {
        return TtsClient.sendResponse(httpRequest).body();
    }

    private static HttpResponse<byte[]> sendResponse(HttpRequest httpRequest) throws Exception {
        HttpResponse<byte[]> httpResponse = http.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
        TtsClient.updateGptSovitsDeviceStatus(httpResponse);
        if (httpResponse.statusCode() / 100 != 2) {
            String string = new String(httpResponse.body(), StandardCharsets.UTF_8);
            if (string.length() > 300) {
                string = string.substring(0, 300);
            }
            throw new IllegalStateException("TTS HTTP " + httpResponse.statusCode() + ": " + string);
        }
        return httpResponse;
    }

    private static void updateGptSovitsDeviceStatus(HttpResponse<?> httpResponse) {
        String string = httpResponse.headers().firstValue("X-Lumi-TTS-Device").orElse(null);
        if (string == null) {
            return;
        }
        String string2 = httpResponse.headers().firstValue("X-Lumi-TTS-Reason").orElse("unknown");
        lastGptSovitsDeviceStatus = switch (string2) {
            case "cuda_available" -> "GPU (CUDA + FP16 자동 감지)";
            case "cuda_forced" -> "GPU (CUDA + FP16 강제)";
            case "cuda_unavailable" -> "CPU (CUDA를 사용할 수 없어 자동 전환)";
            case "cuda_probe_failed" -> "CPU (CUDA 호환 검사 실패로 자동 전환)";
            case "cpu_forced" -> "CPU (호환성 모드)";
            default -> "unavailable".equals(string) ? "GPU 사용 불가" : "장치 확인 불가";
        };
    }

    public static String gptSovitsDeviceStatus() {
        return lastGptSovitsDeviceStatus;
    }

    private static Audio pcmAudio(byte[] byArray, int n) {
        AudioFormat audioFormat = new AudioFormat(n, 16, 1, true, false);
        long l = (long)byArray.length * 1000L / ((long)n * 2L);
        return new Audio(audioFormat, byArray, l);
    }

    private static Audio wavAudio(byte[] byArray) throws Exception {
        try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(byArray))) {
            AudioFormat audioFormat = audioInputStream.getFormat();
            byte[] byArray2 = audioInputStream.readAllBytes();
            long l = (long)((double)byArray2.length * 1000.0 / audioFormat.getFrameRate() / audioFormat.getFrameSize());
            return new Audio(audioFormat, byArray2, l);
        }
    }

    public record Audio(AudioFormat format, byte[] pcm, long millis) {
    }
}

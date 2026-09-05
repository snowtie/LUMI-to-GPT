/*
 * Decompiled with CFR 0.152.
 */
package com.group_finity.mascot.lumi.ai;

import com.group_finity.mascot.lumi.AtomicFiles;
import java.io.File;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AiSettings {
    private static final Logger log = Logger.getLogger(AiSettings.class.getName());
    private static final File FILE = new File("conf/ai.properties");
    private static final Object GPT_WEB_BRIDGE_LOCK = new Object();
    public static final Map<String, String[]> LLM_PROVIDERS = new LinkedHashMap<String, String[]>();
    private static AiSettings instance;
    private final Properties props = new Properties();

    public static synchronized AiSettings get() {
        if (instance == null) {
            instance = new AiSettings();
        }
        return instance;
    }

    private AiSettings() {
        if (FILE.isFile()) {
            try (InputStreamReader inputStreamReader = new InputStreamReader(Files.newInputStream(FILE.toPath(), new OpenOption[0]), StandardCharsets.UTF_8);){
                this.props.load(inputStreamReader);
            }
            catch (Exception exception) {
                log.log(Level.WARNING, "Failed to load " + String.valueOf(FILE), exception);
            }
        }
    }

    public synchronized void save() {
        try {
            File file = FILE.getParentFile();
            if (file != null) {
                file.mkdirs();
            }
            AtomicFiles.storeRestricted(FILE, this.props, "Lumi AI settings \u2014 API \ud0a4 \ud3ec\ud568, \uacf5\uc720/\ucee4\ubc0b \uae08\uc9c0");
        }
        catch (Exception exception) {
            log.log(Level.WARNING, "Failed to save " + String.valueOf(FILE), exception);
        }
    }

    private String get(String string, String string2) {
        String string3 = this.props.getProperty(string);
        return string3 == null || string3.isBlank() ? string2 : string3.trim();
    }

    public synchronized void set(String string, String string2) {
        this.props.setProperty(string, string2 == null ? "" : string2.trim());
    }

    public String llmProvider() {
        String string = this.get("llm.provider", "ollama");
        return LLM_PROVIDERS.containsKey(string) ? string : "ollama";
    }

    public String llmBase() {
        String string = this.llmProvider();
        String string2 = this.get("llm.base." + string, LLM_PROVIDERS.get(string)[1]);
        if ("gpt_web".equals(string)) {
            AiSettings.ensureGptWebBridgeReady(string2);
        }
        return string2;
    }

    public String llmModel() {
        return this.get("llm.model." + this.llmProvider(), LLM_PROVIDERS.get(this.llmProvider())[2]);
    }

    public String llmKey() {
        return this.get("llm.key." + this.llmProvider(), "");
    }

    public String llmProtocol() {
        return LLM_PROVIDERS.get(this.llmProvider())[3];
    }

    public String llmBaseFor(String string) {
        return this.get("llm.base." + string, LLM_PROVIDERS.get(string)[1]);
    }

    public static void ensureGptWebBridgeReady(String string) {
        if (AiSettings.bridgeReachable(string)) {
            return;
        }
        synchronized (GPT_WEB_BRIDGE_LOCK) {
            if (AiSettings.bridgeReachable(string)) {
                return;
            }
            File file = AiSettings.installedBridge();
            if (!file.isFile()) {
                throw new IllegalStateException("LUMI to GPT가 설치되어 있지 않습니다: " + file.getAbsolutePath());
            }
            try {
                new ProcessBuilder(file.getAbsolutePath()).directory(file.getParentFile()).start();
            }
            catch (Exception exception) {
                String string2 = exception.getMessage();
                throw new IllegalStateException("LUMI to GPT 실행 실패: " + (string2 == null ? exception.getClass().getSimpleName() : string2), exception);
            }
            long l = System.currentTimeMillis() + 30000L;
            while (System.currentTimeMillis() < l) {
                if (AiSettings.bridgeReachable(string)) {
                    return;
                }
                try {
                    Thread.sleep(250L);
                }
                catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("LUMI to GPT 실행 대기가 중단되었습니다.", interruptedException);
                }
            }
            throw new IllegalStateException("LUMI to GPT가 30초 안에 시작되지 않았습니다.");
        }
    }

    private static File installedBridge() {
        String string = System.getenv("LOCALAPPDATA");
        File file = string == null || string.isBlank()
                ? new File(System.getProperty("user.home"), "AppData/Local")
                : new File(string);
        return new File(file, "LumiToGPT/app/lumi-to-gpt.exe");
    }

    private static boolean bridgeReachable(String string) {
        try {
            URI uRI = URI.create(string);
            String string2 = uRI.getHost();
            int n = uRI.getPort();
            if (string2 == null || n < 1 || !("127.0.0.1".equals(string2) || "localhost".equalsIgnoreCase(string2) || "::1".equals(string2))) {
                return false;
            }
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(string2, n), 500);
                return true;
            }
        }
        catch (Exception exception) {
            return false;
        }
    }

    public String llmModelFor(String string) {
        return this.get("llm.model." + string, LLM_PROVIDERS.get(string)[2]);
    }

    public String llmKeyFor(String string) {
        return this.get("llm.key." + string, "");
    }

    public boolean ttsEnabled() {
        return Boolean.parseBoolean(this.get("tts.enabled", "false"));
    }

    public String ttsProvider() {
        String string = this.get("tts.provider", "fish");
        return "eleven".equals(string) || "gpt_sovits".equals(string) ? string : "fish";
    }

    public String fishKey() {
        return this.get("tts.fish.key", "");
    }

    public String fishVoice() {
        return this.get("tts.fish.voice", "");
    }

    public String fishVoiceFor(String string) {
        return this.get("tts.fish.voice." + string, this.fishVoice());
    }

    public String elevenVoiceFor(String string) {
        return this.get("tts.eleven.voice." + string, this.elevenVoice());
    }

    public String voiceOverride(String string) {
        return this.get(("eleven".equals(this.ttsProvider()) ? "tts.eleven.voice." : "tts.fish.voice.") + string, "");
    }

    public String voiceOverrideKey(String string) {
        return ("eleven".equals(this.ttsProvider()) ? "tts.eleven.voice." : "tts.fish.voice.") + string;
    }

    public String fishModel() {
        return this.get("tts.fish.model", "s2.1-pro-free");
    }

    public String elevenKey() {
        return this.get("tts.eleven.key", "");
    }

    public String elevenVoice() {
        return this.get("tts.eleven.voice", "");
    }

    public String elevenModel() {
        return this.get("tts.eleven.model", "eleven_multilingual_v2");
    }

    public boolean ttsExternalLines() {
        return Boolean.parseBoolean(this.get("tts.externalLines", "true"));
    }

    public boolean ttsSave() {
        return Boolean.parseBoolean(this.get("tts.save", "false"));
    }

    public int ttsVolume() {
        try {
            return Math.max(0, Math.min(100, Integer.parseInt(this.get("tts.volume", "100"))));
        }
        catch (NumberFormatException numberFormatException) {
            return 100;
        }
    }

    public String gptSovitsBase() {
        return this.get("tts.gpt_sovits.base", "http://127.0.0.1:9880");
    }

    public String gptSovitsRuntime() {
        return this.get("tts.gpt_sovits.runtime", "");
    }

    public String gptSovitsGptWeights() {
        return this.get("tts.gpt_sovits.gpt_weights", "");
    }

    public String gptSovitsSovitsWeights() {
        return this.get("tts.gpt_sovits.sovits_weights", "");
    }

    public String gptSovitsReferenceAudio() {
        return this.get("tts.gpt_sovits.reference_audio", "");
    }

    public String gptSovitsReferenceText() {
        return this.get("tts.gpt_sovits.reference_text", "");
    }

    public String gptSovitsTextLanguage() {
        return this.get("tts.gpt_sovits.text_language", "ko");
    }

    public String gptSovitsPromptLanguage() {
        return this.get("tts.gpt_sovits.prompt_language", "ko");
    }

    public String gptSovitsPowerMode() {
        return this.get("tts.gpt_sovits.power_mode", "balanced");
    }

    public String gptSovitsSpeed() {
        return this.get("tts.gpt_sovits.speed", "1.0");
    }

    public boolean chatterEnabled() {
        return Boolean.parseBoolean(this.get("chatter.enabled", "false"));
    }

    public int chatterMinutes() {
        try {
            return Math.max(3, Integer.parseInt(this.get("chatter.minutes", "20")));
        }
        catch (NumberFormatException numberFormatException) {
            return 20;
        }
    }

    public boolean screenwatchEnabled() {
        return Boolean.parseBoolean(this.get("screenwatch.enabled", "false"));
    }

    public int screenwatchSeconds() {
        try {
            return Math.max(15, Integer.parseInt(this.get("screenwatch.seconds", "60")));
        }
        catch (NumberFormatException numberFormatException) {
            return 60;
        }
    }

    public int historyTurns() {
        try {
            return Math.max(2, Integer.parseInt(this.get("chat.historyTurns", "12")));
        }
        catch (NumberFormatException numberFormatException) {
            return 12;
        }
    }

    static {
        LLM_PROVIDERS.put("gpt_web", new String[]{"ChatGPT (OAuth)", "http://127.0.0.1:32123/v1", "gpt-5.6-luna", "openai"});
        LLM_PROVIDERS.put("ollama", new String[]{"Ollama Cloud", "https://ollama.com/v1", "deepseek-v4-flash:0731-cloud", "openai"});
        LLM_PROVIDERS.put("anthropic", new String[]{"Anthropic", "https://api.anthropic.com/v1", "claude-sonnet-5", "anthropic"});
        LLM_PROVIDERS.put("openrouter", new String[]{"OpenRouter", "https://openrouter.ai/api/v1", "openrouter/auto", "openai"});
        LLM_PROVIDERS.put("openai", new String[]{"OpenAI", "https://api.openai.com/v1", "gpt-5-mini", "openai"});
        LLM_PROVIDERS.put("google", new String[]{"Google (Gemini)", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-3.5-flash-lite", "openai"});
        LLM_PROVIDERS.put("cerebras", new String[]{"Cerebras", "https://api.cerebras.ai/v1", "gpt-oss-120b", "openai"});
    }
}

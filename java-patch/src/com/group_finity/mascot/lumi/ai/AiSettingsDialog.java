/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  com.group_finity.mascot.Main
 *  com.group_finity.mascot.lumi.Features
 *  com.group_finity.mascot.lumi.LumiTheme
 *  com.group_finity.mascot.lumi.LumiWindows
 *  com.group_finity.mascot.lumi.ai.AiSettings
 *  com.group_finity.mascot.lumi.ai.Characters
 *  com.group_finity.mascot.lumi.ai.ChatService
 *  com.group_finity.mascot.lumi.ai.LlmClient
 *  com.group_finity.mascot.lumi.ai.LlmClient$Msg
 *  com.group_finity.mascot.lumi.ai.TtsClient
 *  com.group_finity.mascot.lumi.ai.TtsClient$Audio
 *  com.group_finity.mascot.lumi.ai.TtsPlayer
 *  com.group_finity.mascot.lumi.ai.VoiceRecorder
 *  com.group_finity.mascot.lumi.settings.ProtoUi
 *  com.group_finity.mascot.lumi.settings.ProtoUi$ButtonStyle
 *  com.group_finity.mascot.lumi.settings.SettingsUi
 */
package com.group_finity.mascot.lumi.ai;

import com.group_finity.mascot.Main;
import com.group_finity.mascot.lumi.Features;
import com.group_finity.mascot.lumi.LumiTheme;
import com.group_finity.mascot.lumi.LumiWindows;
import com.group_finity.mascot.lumi.SpeechDirector;
import com.group_finity.mascot.lumi.ai.AiSettings;
import com.group_finity.mascot.lumi.ai.Characters;
import com.group_finity.mascot.lumi.ai.ChatService;
import com.group_finity.mascot.lumi.ai.LlmClient;
import com.group_finity.mascot.lumi.ai.TtsClient;
import com.group_finity.mascot.lumi.ai.TtsPlayer;
import com.group_finity.mascot.lumi.ai.VoiceRecorder;
import com.group_finity.mascot.lumi.settings.ProtoUi;
import com.group_finity.mascot.lumi.settings.SettingsUi;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

public final class AiSettingsDialog
extends JDialog {
    private static final String OLLAMA_KEYS_URL = "https://ollama.com/settings/keys";
    private static final String FISH_AUDIO_URL = "https://fish.audio";
    private static AiSettingsDialog instance;
    private final JComboBox<String> llmProvider = new JComboBox();
    private final JTextField llmBase = new JTextField(28);
    private final JTextField llmModel = new JTextField(28);
    private final JPasswordField llmKey = new JPasswordField(28);
    private final Map<String, String[]> llmStash = new HashMap<String, String[]>();
    private String shownProvider;
    private final JCheckBox ttsEnabled = new JCheckBox(SpeechDirector.localized("AiTtsEnabled", "\uc74c\uc131 \ucf1c\uae30 (TTS)"));
    private final JComboBox<String> ttsProvider = new JComboBox<String>(new String[]{"Fish Audio", "ElevenLabs", "GPT-SoVITS"});
    private final JPasswordField fishKey = new JPasswordField(24);
    private final JTextField fishVoice = new JTextField(24);
    private final JComboBox<String> fishModel = new JComboBox<String>(new String[]{"s2.1-pro-free", "s2.1-pro", "s2-pro", "s1"});
    private final JPasswordField elevenKey = new JPasswordField(24);
    private final JTextField elevenVoice = new JTextField(24);
    private final JTextField elevenModel = new JTextField(24);
    private final JTextField gptSovitsBase = new JTextField(24);
    private final JTextField gptSovitsRuntime = new JTextField(24);
    private final JTextField gptSovitsGptWeights = new JTextField(24);
    private final JTextField gptSovitsSovitsWeights = new JTextField(24);
    private final JTextField gptSovitsReferenceAudio = new JTextField(24);
    private final JTextField gptSovitsReferenceText = new JTextField(24);
    private final JTextField gptSovitsTextLanguage = new JTextField(8);
    private final JTextField gptSovitsPromptLanguage = new JTextField(8);
    private final JComboBox<String> gptSovitsPowerMode = new JComboBox<String>(new String[]{"balanced", "ultra_saver"});
    private final JTextField gptSovitsSpeed = new JTextField(8);
    private final JCheckBox externalLines = new JCheckBox(SpeechDirector.localized("AiExternalLines", "\uc0ac\uc774\ub4dc\uce74 \ub300\uc0ac(say.txt)\ub3c4 \uc74c\uc131\uc73c\ub85c \uc77d\uae30"));
    private final JCheckBox ttsSave = new JCheckBox(SpeechDirector.localized("AiTtsSave", "\ub9d0\ud55c \ub300\uc0ac\ub97c wav\ub85c \uc800\uc7a5"));
    private final JSlider ttsVolume = new JSlider(0, 100, 100);
    private final JLabel ttsVolumeValue = new JLabel();
    private final JCheckBox chatterEnabled = new JCheckBox(SpeechDirector.localized("AiChatterEnabled", "\uc790\uc728 \ud63c\uc7a3\ub9d0 \ucf1c\uae30"));
    private final JSpinner chatterMinutes = new JSpinner(new SpinnerNumberModel(20, 3, 240, 1));
    private final JCheckBox screenwatchEnabled = new JCheckBox(SpeechDirector.localized("AiScreenwatchEnabled", "\ud654\uba74 \uad6c\uacbd \ucf1c\uae30"));
    private final JSpinner screenwatchSeconds = new JSpinner(new SpinnerNumberModel(60, 15, 3600, 15));
    private final JComboBox<String> character = new JComboBox();
    private final JTextField characterVoice = new JTextField(24);
    private final Map<String, String> voiceStash = new HashMap<String, String>();
    private String shownCharacter;
    private final JLabel status = new JLabel(" ");
    private final Locale builtFor = AiSettingsDialog.bundleLocale();
    static final int STATUS_WRAP_PX = 510;
    static final int STATUS_MAX_LINES = 4;

    private static Locale bundleLocale() {
        try {
            ResourceBundle resourceBundle = Main.getInstance().getLanguageBundle();
            return resourceBundle == null ? Locale.ROOT : resourceBundle.getLocale();
        }
        catch (Exception exception) {
            return Locale.ROOT;
        }
    }

    public static synchronized void open() {
        AiSettingsDialog.open(null);
    }

    public static synchronized void open(String string) {
        if (!Features.aiChatUnlocked() && !Features.aiVoiceUnlocked()) {
            return;
        }
        if (instance != null && !AiSettingsDialog.instance.builtFor.equals(AiSettingsDialog.bundleLocale())) {
            instance.dispose();
            instance = null;
        }
        if (instance == null) {
            instance = new AiSettingsDialog();
        }
        instance.load();
        if (string != null) {
            instance.selectCharacter(string);
        }
        instance.setVisible(true);
        instance.toFront();
    }

    private AiSettingsDialog() {
        super((Window)null, SpeechDirector.localized("LumiAiSettings", "\ub8e8\ubbf8 AI \uc124\uc815"));
        this.setDefaultCloseOperation(1);
        this.setIconImage(Main.getIcon());
        this.setAlwaysOnTop(true);
        for (String[] provider : AiSettings.LLM_PROVIDERS.values()) {
            this.llmProvider.addItem(provider[0]);
        }
        this.llmProvider.addActionListener(actionEvent -> this.onProviderSwitch());
        this.character.addActionListener(actionEvent -> this.onCharacterSwitch());
        JTabbedPane jTabbedPane = new JTabbedPane();
        ProtoUi.style((JComponent)jTabbedPane, (String)"underlineColor: #4ECCD3; inactiveUnderlineColor: #4ECCD3; tabSelectionHeight: 2; tabHeight: 34; tabInsets: 8,12,8,12; tabAreaInsets: 2,6,0,6; hoverColor: #16233A; selectedBackground: null; contentAreaColor: #354458");
        AiSettingsDialog.addScrollableTab(jTabbedPane, SpeechDirector.localized("AiTabBrain", "\ub450\ub1cc"), this.brainTab());
        AiSettingsDialog.addScrollableTab(jTabbedPane, SpeechDirector.localized("AiTabVoice", "\ubaa9\uc18c\ub9ac"), this.voiceTab());
        AiSettingsDialog.addScrollableTab(jTabbedPane, SpeechDirector.localized("AiTabCharacter", "\uce90\ub9ad\ud130"), this.characterTab());
        AiSettingsDialog.addScrollableTab(jTabbedPane, SpeechDirector.localized("AiTabAuto", "\uc790\ub3d9 \ub3d9\uc791"), this.autoTab());
        AiSettingsDialog.addScrollableTab(jTabbedPane, SpeechDirector.localized("AiTabLink", "\uc5f0\uacb0"), this.linkTab());
        JPanel buttonPanel = new JPanel(new FlowLayout(2, 8, 8));
        buttonPanel.setOpaque(false);
        JButton jButton = new JButton(SpeechDirector.localized("AiSave", "\uc800\uc7a5"));
        jButton.addActionListener(actionEvent -> {
            this.apply();
            this.setStatus(SpeechDirector.localized("AiStatusSaved", "\uc800\uc7a5\ud588\uc5b4\uc694."), false);
        });
        JButton jButton2 = new JButton(SpeechDirector.localized("Close", "\ub2eb\uae30"));
        jButton2.addActionListener(actionEvent -> this.setVisible(false));
        ProtoUi.buttonize((AbstractButton)jButton2, (ProtoUi.ButtonStyle)ProtoUi.ButtonStyle.OUTLINE);
        ProtoUi.buttonize((AbstractButton)jButton, (ProtoUi.ButtonStyle)ProtoUi.ButtonStyle.PRIMARY);
        for (JButton jButton3 : new JButton[]{jButton2, jButton}) {
            jButton3.setFont(jButton3.getFont().deriveFont(12.5f));
            jButton3.setMargin(new Insets(6, 16, 6, 16));
        }
        buttonPanel.add(jButton2);
        buttonPanel.add(jButton);
        this.status.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 12));
        JPanel jPanel = new JPanel(new BorderLayout());
        jPanel.setOpaque(true);
        LumiTheme.speakDress((JComponent)jPanel, () -> jPanel.setBackground(LumiTheme.cardBackground()));
        jPanel.add((Component)this.status, "North");
        jPanel.add((Component)buttonPanel, "South");
        this.setLayout(new BorderLayout());
        this.add((Component)jTabbedPane, "Center");
        this.add((Component)jPanel, "South");
        this.pack();
        int n = Math.min(this.getHeight(), 720);
        Rectangle rectangle = this.workArea();
        if (rectangle != null) {
            n = Math.min(n, rectangle.height - 8);
        }
        this.setSize(552, n);
        LumiWindows.manage((Window)this, (String)"ai-settings");
    }

    private static void addScrollableTab(JTabbedPane jTabbedPane, String string, JComponent jComponent) {
        JScrollPane jScrollPane = new JScrollPane(new TabShell(jComponent), 20, 31);
        jScrollPane.setBorder(BorderFactory.createEmptyBorder());
        JViewport jViewport = jScrollPane.getViewport();
        jViewport.setOpaque(true);
        LumiTheme.speakDress((JComponent)jViewport, () -> jViewport.setBackground(ProtoUi.tone((int)858416)));
        jScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        jTabbedPane.addTab(string, jScrollPane);
    }

    private Rectangle workArea() {
        try {
            GraphicsConfiguration graphicsConfiguration = this.getGraphicsConfiguration();
            if (graphicsConfiguration == null) {
                graphicsConfiguration = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
            }
            Rectangle rectangle = graphicsConfiguration.getBounds();
            Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(graphicsConfiguration);
            return new Rectangle(rectangle.x + insets.left, rectangle.y + insets.top, rectangle.width - insets.left - insets.right, rectangle.height - insets.top - insets.bottom);
        }
        catch (Throwable throwable) {
            return null;
        }
    }

    private JPanel brainTab() {
        JPanel jPanel = AiSettingsDialog.tabPanel();
        GridBagConstraints gridBagConstraints = AiSettingsDialog.tabConstraints();
        int n = 0;
        n = AiSettingsDialog.intro(jPanel, gridBagConstraints, n, SpeechDirector.localized("AiBrainHint", "\ub8e8\ubbf8\uac00 \ud560 \ub9d0\uc744 \ub9cc\ub4dc\ub294 \ubd80\ubd84\uc785\ub2c8\ub2e4. ChatGPT (OAuth)\ub294 LUMI to GPT\uc5d0\uc11c \uacc4\uc815\ub9cc \uc5f0\uacb0\ud558\uba74 \ub418\uba70 API \ud0a4\uac00 \ud544\uc694\ud558\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4. \uae30\ubcf8 \ubaa8\ub378\uc740 \uac00\ubcbc\uc6b4 GPT-5.6 Luna\uc785\ub2c8\ub2e4. \ub2e4\ub978 \ud504\ub85c\ubc14\uc774\ub354\ub97c \uc4f8 \ub54c\ub9cc \ud574\ub2f9 \ud68c\uc0ac\uc758 API \ud0a4\ub97c \ub123\uc73c\uc138\uc694."));
        n = AiSettingsDialog.addRow(jPanel, gridBagConstraints, n, SpeechDirector.localized("AiProvider", "\ud504\ub85c\ubc14\uc774\ub354"), this.llmProvider);
        n = AiSettingsDialog.addRow(jPanel, gridBagConstraints, n, SpeechDirector.localized("AiBaseUrl", "Base URL"), this.llmBase);
        n = AiSettingsDialog.addRow(jPanel, gridBagConstraints, n, SpeechDirector.localized("AiModel", "\ubaa8\ub378"), this.llmModel);
        n = AiSettingsDialog.addRow(jPanel, gridBagConstraints, n, SpeechDirector.localized("AiApiKey", "API \ud0a4"), this.llmKey);
        this.llmProvider.setToolTipText(SpeechDirector.localized("AiProviderHint", "\ud68c\uc0ac\ub97c \ubc14\uafb8\uba74 \uc544\ub798 \uc138 \uce78\uc774 \uadf8 \ud68c\uc0ac \uac12\uc73c\ub85c \ubc14\ub01d\ub2c8\ub2e4. \ud68c\uc0ac\ub9c8\ub2e4 \uac12\uc744 \ub530\ub85c \uae30\uc5b5\ud558\ub2c8, \ub2e4\uc2dc \ub3cc\uc544\uc624\uba74 \uc608\uc804\uc5d0 \ub123\uc5b4 \ub454 \ud0a4\uac00 \uadf8\ub300\ub85c \uc788\uc2b5\ub2c8\ub2e4."));
        this.llmBase.setToolTipText(SpeechDirector.localized("AiBaseUrlHint", "\uc694\uccad\uc744 \ubcf4\ub0bc \uc8fc\uc18c\uc785\ub2c8\ub2e4. \uae30\ubcf8\uac12 \uadf8\ub300\ub85c \ub450\uba74 \ub418\uace0, \uc9c1\uc811 \uc138\uc6b4 \uc11c\ubc84\ub098 \uc911\uacc4 \uc11c\ubc84\ub97c \uc4f8 \ub54c\ub9cc \ubc14\uafb8\uc138\uc694."));
        this.llmModel.setToolTipText(SpeechDirector.localized("AiModelHint", "\uac19\uc740 \ud68c\uc0ac \uc548\uc5d0\uc11c \uace0\ub974\ub294 \ubaa8\ub378 \uc774\ub984\uc785\ub2c8\ub2e4. \ubaa8\ub378\uc5d0 \ub530\ub77c \ub9d0\uc19c\uc528\uc640 \uc18d\ub3c4, \uc694\uae08\uc774 \ub2ec\ub77c\uc9d1\ub2c8\ub2e4."));
        this.llmKey.setToolTipText(SpeechDirector.localized("AiApiKeyHint", "\uace0\ub978 \ud68c\uc0ac \ud648\ud398\uc774\uc9c0\uc5d0\uc11c \ubc1c\uae09\ubc1b\ub294 \ube44\ubc00 \ubb38\uc790\uc5f4\uc774\uba70, \uc694\uae08\ub3c4 \uc774 \ud0a4\uc5d0 \ubd99\uc2b5\ub2c8\ub2e4. conf/ai.properties \ud30c\uc77c\uc5d0 \uc800\uc7a5\ub418\ub2c8 \ud3f4\ub354\ub97c \ud1b5\uc9f8\ub85c \ub0a8\uc5d0\uac8c \ub118\uae30\uc9c0 \ub9c8\uc138\uc694."));
        JPanel jPanel2 = new JPanel(new FlowLayout(0, 6, 0));
        JButton jButton = new JButton(SpeechDirector.localized("AiOllamaKeyPage", "Ollama \ud0a4 \ubc1c\uae09 \ud398\uc774\uc9c0 \uc5f4\uae30"));
        jButton.setToolTipText(AiSettingsDialog.format("AiOllamaKeyPageHint", "Ollama Cloud\uc758 \ud0a4 \ubc1c\uae09 \ud654\uba74(%s)\uc744 \ube0c\ub77c\uc6b0\uc800\ub85c \uc5fd\ub2c8\ub2e4. \ub2e4\ub978 \ud68c\uc0ac\ub97c \uace8\ub790\ub2e4\uba74 \uadf8 \ud68c\uc0ac \ud648\ud398\uc774\uc9c0\uc5d0\uc11c \ud0a4\ub97c \ubc1b\uc73c\uc138\uc694.", OLLAMA_KEYS_URL));
        jButton.addActionListener(actionEvent -> this.browse(OLLAMA_KEYS_URL));
        JButton jButton2 = new JButton(SpeechDirector.localized("AiTestConnection", "\uc800\uc7a5 \ud6c4 \uc5f0\uacb0 \ud14c\uc2a4\ud2b8"));
        jButton2.setToolTipText(SpeechDirector.localized("AiTestConnectionHint", "\uc9c0\uae08 \uc801\uc740 \uac12\uc744 \uc800\uc7a5\ud55c \ub2e4\uc74c \uc2e4\uc81c\ub85c \ud55c \ubc88 \ubb3c\uc5b4\ubd05\ub2c8\ub2e4. \uc131\uacf5\ud558\uba74 \ub2f5\uacfc \uac78\ub9b0 \uc2dc\uac04\uc774, \uc2e4\ud328\ud558\uba74 \uadf8 \uc774\uc720\uac00 \ucc3d \uc544\ub798 \uc904\uc5d0 \ub098\uc635\ub2c8\ub2e4."));
        jButton2.addActionListener(actionEvent -> this.saveAndTest());
        jPanel2.add(jButton);
        jPanel2.add(jButton2);
        n = AiSettingsDialog.addWide(jPanel, gridBagConstraints, n, jPanel2);
        AiSettingsDialog.filler(jPanel, gridBagConstraints, n);
        return jPanel;
    }

    private JPanel voiceTab() {
        JPanel jPanel = AiSettingsDialog.tabPanel();
        GridBagConstraints gridBagConstraints = AiSettingsDialog.tabConstraints();
        int n = 0;
        n = AiSettingsDialog.intro(jPanel, gridBagConstraints, n, SpeechDirector.localized("AiVoiceHint", "\ub8e8\ubbf8\uac00 \ud55c \ub9d0\uc744 \uc18c\ub9ac\ub85c \uc77d\uc2b5\ub2c8\ub2e4. \ub450\ub1cc\uc640\ub294 \ub2e4\ub978 \ud68c\uc0ac\ub97c \uc501\ub2c8\ub2e4. \uc74c\uc131 \ucf1c\uae30\ub97c \uccb4\ud06c\ud558\uace0 \ud68c\uc0ac\ub97c \uace0\ub978 \ub4a4, \uadf8 \ud68c\uc0ac \uc904\uc5d0\ub9cc \ud0a4\uc640 \ubcf4\uc774\uc2a4 ID\ub97c \ub123\uc2b5\ub2c8\ub2e4. Fish Audio\ub294 \uc544\ub798 \ubc84\ud2bc\uc758 \uc0ac\uc774\ud2b8\uc5d0\uc11c \ub458 \ub2e4 \uc5bb\uc2b5\ub2c8\ub2e4. \ub123\uc740 \uac12\uc740 \ubbf8\ub9ac\ub4e3\uae30\ub85c \ud655\uc778\ud569\ub2c8\ub2e4."));
        n = AiSettingsDialog.addWide(jPanel, gridBagConstraints, n, this.ttsEnabled);
        this.ttsVolume.setToolTipText(SpeechDirector.localized("AiTtsVolumeHint", "\ub8e8\ubbf8\uac00 \ub9d0\ud558\ub294 \uc18c\ub9ac\uc758 \ud06c\uae30\uc785\ub2c8\ub2e4. \ud6a8\uacfc\uc74c \uc74c\ub7c9\uacfc\ub294 \ub530\ub85c \uc815\ud558\ubbc0\ub85c, 0\uc73c\ub85c \ub0b4\ub824\ub3c4 \ubc1c\uc18c\ub9ac \uac19\uc740 \ud6a8\uacfc\uc74c\uc740 \uadf8\ub300\ub85c\uc785\ub2c8\ub2e4."));
        this.ttsVolume.addChangeListener(changeEvent -> this.syncTtsVolumeLabel());
        n = AiSettingsDialog.sliderRow(jPanel, gridBagConstraints, n, SpeechDirector.localized("AiTtsVolume", "\ubaa9\uc18c\ub9ac \uc74c\ub7c9"), this.ttsVolume, this.ttsVolumeValue);
        n = AiSettingsDialog.addRow(jPanel, gridBagConstraints, n, SpeechDirector.localized("AiTtsProvider", "TTS \ud504\ub85c\ubc14\uc774\ub354"), this.ttsProvider);
        n = AiSettingsDialog.groupLabel(jPanel, gridBagConstraints, n, "Fish Audio");
        n = AiSettingsDialog.addRow(jPanel, gridBagConstraints, n, SpeechDirector.localized("AiFishKey", "Fish API \ud0a4"), this.fishKey);
        n = AiSettingsDialog.addRow(jPanel, gridBagConstraints, n, SpeechDirector.localized("AiFishVoice", "Fish \ubcf4\uc774\uc2a4 ID"), this.fishVoice);
        n = AiSettingsDialog.addRow(jPanel, gridBagConstraints, n, SpeechDirector.localized("AiFishModel", "Fish \ubaa8\ub378"), this.fishModel);
        JPanel jPanel2 = ((Page)jPanel).lastHolder();
        n = AiSettingsDialog.groupLabel(jPanel, gridBagConstraints, n, "ElevenLabs");
        n = AiSettingsDialog.addRow(jPanel, gridBagConstraints, n, SpeechDirector.localized("AiElevenKey", "ElevenLabs \ud0a4"), this.elevenKey);
        n = AiSettingsDialog.addRow(jPanel, gridBagConstraints, n, SpeechDirector.localized("AiElevenVoice", "ElevenLabs \ubcf4\uc774\uc2a4"), this.elevenVoice);
        n = AiSettingsDialog.addRow(jPanel, gridBagConstraints, n, SpeechDirector.localized("AiElevenModel", "ElevenLabs \ubaa8\ub378"), this.elevenModel);
        JPanel jPanel3 = ((Page)jPanel).lastHolder();
        n = AiSettingsDialog.groupLabel(jPanel, gridBagConstraints, n, "GPT-SoVITS");
        n = AiSettingsDialog.addRow(jPanel, gridBagConstraints, n, "\uc11c\ubc84 \uc8fc\uc18c", this.gptSovitsBase);
        n = AiSettingsDialog.addRow(jPanel, gridBagConstraints, n, "GPT-SoVITS \ud3f4\ub354", this.gptSovitsRuntime);
        n = AiSettingsDialog.addRow(jPanel, gridBagConstraints, n, "GPT \uac00\uc911\uce58", this.gptSovitsGptWeights);
        n = AiSettingsDialog.addRow(jPanel, gridBagConstraints, n, "SoVITS \uac00\uc911\uce58", this.gptSovitsSovitsWeights);
        n = AiSettingsDialog.addRow(jPanel, gridBagConstraints, n, "\ucc38\uc870 WAV", this.gptSovitsReferenceAudio);
        n = AiSettingsDialog.addRow(jPanel, gridBagConstraints, n, "\ucc38\uc870 \uc74c\uc131\uc758 \ub300\uc0ac", this.gptSovitsReferenceText);
        n = AiSettingsDialog.addRow(jPanel, gridBagConstraints, n, "\ubcf8\ubb38 \uc5b8\uc5b4", this.gptSovitsTextLanguage);
        n = AiSettingsDialog.addRow(jPanel, gridBagConstraints, n, "\ucc38\uc870 \uc5b8\uc5b4", this.gptSovitsPromptLanguage);
        n = AiSettingsDialog.addRow(jPanel, gridBagConstraints, n, "\uc74c\uc131 \ub300\uae30 \ubaa8\ub4dc", this.gptSovitsPowerMode);
        n = AiSettingsDialog.addRow(jPanel, gridBagConstraints, n, "\ub9d0\ud558\uae30 \uc18d\ub3c4", this.gptSovitsSpeed);
        JPanel jPanel5 = ((Page)jPanel).lastHolder();
        ((Page)jPanel).closeCard();
        Runnable runnable = () -> {
            int n2 = this.ttsProvider.getSelectedIndex();
            jPanel2.setVisible(n2 == 0);
            jPanel3.setVisible(n2 == 1);
            jPanel5.setVisible(n2 == 2);
            this.characterVoice.setEnabled(n2 != 2);
            jPanel.revalidate();
            jPanel.repaint();
        };
        this.ttsProvider.addActionListener(actionEvent -> runnable.run());
        runnable.run();
        this.ttsEnabled.setToolTipText(SpeechDirector.localized("AiTtsEnabledHint", "\uc774 \uccb4\ud06c\ub97c \ucf1c\uc57c \ub300\ud654 \ub2f5\ubcc0\uc774 \uc18c\ub9ac\ub85c \ub098\uc635\ub2c8\ub2e4. \ubbf8\ub9ac\ub4e3\uae30\ub294 \uc774 \uccb4\ud06c\uc640 \uc0c1\uad00\uc5c6\uc774 \ub4e4\ub9ac\ubbc0\ub85c, \ubbf8\ub9ac\ub4e3\uae30\ub9cc \ub4e4\ub9ac\uace0 \ub300\ud654\ub294 \uc870\uc6a9\ud558\ub2e4\uba74 \uc5ec\uae30\ub97c \ud655\uc778\ud558\uc138\uc694."));
        this.ttsProvider.setToolTipText(SpeechDirector.localized("AiTtsProviderHint", "\uc2e4\uc81c\ub85c \uc18c\ub9ac\ub97c \ub9cc\ub4e4 \ud68c\uc0ac\ub97c \uace0\ub985\ub2c8\ub2e4. \uace0\ub974\uc9c0 \uc54a\uc740 \ud68c\uc0ac\uc758 \uce78\uc740 \ube44\uc6cc \ub46c\ub3c4 \ub429\ub2c8\ub2e4."));
        this.fishKey.setToolTipText(SpeechDirector.localized("AiFishKeyHint", "fish.audio\uc5d0 \ub85c\uadf8\uc778\ud574 \ubc1c\uae09\ubc1b\ub294 \ud0a4\uc785\ub2c8\ub2e4."));
        this.fishVoice.setToolTipText(SpeechDirector.localized("AiFishVoiceHint", "\ubcf4\uc774\uc2a4 ID\ub294 \ubaa9\uc18c\ub9ac \ud558\ub098\ub97c \uac00\ub9ac\ud0a4\ub294 \uae34 \ubb38\uc790\uc5f4\uc785\ub2c8\ub2e4. fish.audio\uc5d0\uc11c \ub9c8\uc74c\uc5d0 \ub4dc\ub294 \ubaa9\uc18c\ub9ac\ub97c \uace0\ub974\uba74 \uadf8 \ubaa9\uc18c\ub9ac \ud398\uc774\uc9c0 \uc8fc\uc18c\uc5d0\uc11c \uc774 \uac12\uc744 \uc5bb\uc744 \uc218 \uc788\uc2b5\ub2c8\ub2e4."));
        this.fishModel.setToolTipText(SpeechDirector.localized("AiFishModelHint", "\ud569\uc131 \uc5d4\uc9c4\uc758 \ubc84\uc804\uc785\ub2c8\ub2e4. \uc798 \ubaa8\ub974\uaca0\uc73c\uba74 \uae30\ubcf8\uac12\uc744 \uadf8\ub300\ub85c \ub450\uc138\uc694. \uc774\ub984\uc5d0 free\uac00 \ubd99\uc740 \uac83\uc774 \ubb34\ub8cc \ub4f1\uae09\uc785\ub2c8\ub2e4."));
        this.elevenKey.setToolTipText(SpeechDirector.localized("AiElevenKeyHint", "ElevenLabs \uacc4\uc815 \uc124\uc815\uc5d0\uc11c \ubc1c\uae09\ubc1b\ub294 \ud0a4\uc785\ub2c8\ub2e4."));
        this.elevenVoice.setToolTipText(SpeechDirector.localized("AiElevenVoiceHint", "ElevenLabs\uc5d0\uc11c \ubaa9\uc18c\ub9ac \ud558\ub098\ub97c \uac00\ub9ac\ud0a4\ub294 ID\uc785\ub2c8\ub2e4. \ubcf4\uad00\ud568(Voice Library)\uc5d0\uc11c \ubaa9\uc18c\ub9ac\ub97c \uace0\ub974\uba74 \ud655\uc778\ud560 \uc218 \uc788\uc2b5\ub2c8\ub2e4."));
        this.elevenModel.setToolTipText(SpeechDirector.localized("AiElevenModelHint", "ElevenLabs\uc758 \ud569\uc131 \ubaa8\ub378 \uc774\ub984\uc785\ub2c8\ub2e4. \uc798 \ubaa8\ub974\uaca0\uc73c\uba74 \uae30\ubcf8\uac12\uc744 \uadf8\ub300\ub85c \ub450\uc138\uc694."));
        this.externalLines.setToolTipText(SpeechDirector.localized("AiExternalLinesHint", "\ub2e4\ub978 \ud504\ub85c\uadf8\ub7a8\uc774\ub098 Claude Code\uac00 speech/say.txt\ub85c \ubcf4\ub0b8 \ub300\uc0ac\uae4c\uc9c0 \uc18c\ub9ac\ub85c \uc77d\uc2b5\ub2c8\ub2e4. \ub300\ud654 \ub2f5\ubcc0\uc740 \uc774 \uccb4\ud06c\uc640 \uc0c1\uad00\uc5c6\uc774 \ud56d\uc0c1 \uc77d\uc2b5\ub2c8\ub2e4."));
        this.ttsSave.setToolTipText(SpeechDirector.localized("AiTtsSaveHint", "\ub8e8\ubbf8\uac00 \ub9d0\ud55c \ub300\uc0ac\ub97c speech/voice \ud3f4\ub354\uc5d0 wav \ud30c\uc77c\ub85c \ub0a8\uae41\ub2c8\ub2e4. \uc790\ub3d9\uc73c\ub85c \uc9c0\uc6b0\uc9c0 \uc54a\uc73c\ubbc0\ub85c \uac00\ub054 \uc9c1\uc811 \uc815\ub9ac\ud574\uc57c \ud569\ub2c8\ub2e4."));
        n = AiSettingsDialog.toggleRow(jPanel, gridBagConstraints, n, this.externalLines, this.externalLines, null);
        JButton jButton = new JButton(SpeechDirector.localized("AiOpenVoiceFolder", "\ud3f4\ub354 \uc5f4\uae30"));
        jButton.setToolTipText(SpeechDirector.localized("AiOpenVoiceFolderHint", "\uc800\uc7a5\ub41c wav\uac00 \uc313\uc774\ub294 \ud3f4\ub354\ub97c \uc5fd\ub2c8\ub2e4. \uac19\uc740 \ud3f4\ub354\uc758 index.tsv\uc5d0 \uc5b4\ub5a4 \ud30c\uc77c\uc774 \uc5b4\ub5a4 \ub300\uc0ac\uc778\uc9c0 \uc801\ud600 \uc788\uc2b5\ub2c8\ub2e4."));
        jButton.addActionListener(actionEvent -> this.openVoiceFolder());
        ProtoUi.buttonize((AbstractButton)jButton, (ProtoUi.ButtonStyle)ProtoUi.ButtonStyle.OUTLINE);
        jButton.setFont(jButton.getFont().deriveFont(12.5f));
        jButton.setMargin(new Insets(6, 12, 6, 12));
        n = AiSettingsDialog.toggleRow(jPanel, gridBagConstraints, n, this.ttsSave, this.ttsSave, jButton);
        JPanel jPanel4 = new JPanel(new FlowLayout(0, 6, 0));
        JButton jButton2 = new JButton(SpeechDirector.localized("AiFishSite", "Fish Audio \uc0ac\uc774\ud2b8 \uc5f4\uae30"));
        jButton2.setToolTipText(AiSettingsDialog.format("AiFishSiteHint", "Fish Audio(%s)\ub97c \ube0c\ub77c\uc6b0\uc800\ub85c \uc5fd\ub2c8\ub2e4. \ud0a4 \ubc1c\uae09\uacfc \ubaa9\uc18c\ub9ac \uace0\ub974\uae30\ub97c \uc5ec\uae30\uc11c \ud569\ub2c8\ub2e4.", FISH_AUDIO_URL));
        jButton2.addActionListener(actionEvent -> this.browse(FISH_AUDIO_URL));
        JButton jButton3 = new JButton(SpeechDirector.localized("AiPreview", "\uc800\uc7a5 \ud6c4 \ubbf8\ub9ac\ub4e3\uae30"));
        jButton3.setToolTipText(SpeechDirector.localized("AiPreviewHint", "\uc9c0\uae08 \uc801\uc740 \uac12\uc744 \uc800\uc7a5\ud55c \ub2e4\uc74c \ud55c \ubb38\uc7a5\uc744 \ud569\uc131\ud574 \ub4e4\ub824\uc90d\ub2c8\ub2e4. \uce90\ub9ad\ud130 \ud0ed\uc5d0\uc11c \uace0\ub978 \uce90\ub9ad\ud130\uc758 \ubaa9\uc18c\ub9ac\ub85c \ub4e4\ub9ac\uba70, \uc74c\uc131 \ucf1c\uae30\uac00 \uaebc\uc838 \uc788\uc5b4\ub3c4 \uc774 \ubc84\ud2bc\uc740 \uc18c\ub9ac\uac00 \ub0a9\ub2c8\ub2e4."));
        jButton3.addActionListener(actionEvent -> this.saveAndPreview());
        jPanel4.add(jButton2);
        jPanel4.add(jButton3);
        n = AiSettingsDialog.addWide(jPanel, gridBagConstraints, n, jPanel4);
        AiSettingsDialog.filler(jPanel, gridBagConstraints, n);
        return jPanel;
    }

    private JPanel characterTab() {
        JPanel jPanel = AiSettingsDialog.tabPanel();
        GridBagConstraints gridBagConstraints = AiSettingsDialog.tabConstraints();
        int n = 0;
        n = AiSettingsDialog.intro(jPanel, gridBagConstraints, n, SpeechDirector.localized("AiCharacterHint", "\uce90\ub9ad\ud130\ub9c8\ub2e4 \uc131\uaca9\uacfc \ubaa9\uc18c\ub9ac\ub97c \ub530\ub85c \uc90d\ub2c8\ub2e4. \uc704\uc5d0\uc11c \uce90\ub9ad\ud130\ub97c \uace0\ub978 \ub4a4, \uc131\uaca9\uc740 \ud398\ub974\uc18c\ub098 \ud3b8\uc9d1 \ubc84\ud2bc\uc5d0\uc11c \uae00\ub85c \uc801\uc2b5\ub2c8\ub2e4. \ubaa9\uc18c\ub9ac \uce78\uc774 \ube44\uc5b4 \uc788\uc73c\uba74 \ubaa9\uc18c\ub9ac \ud0ed\uc758 \uacf5\uc6a9 \ubcf4\uc774\uc2a4\ub97c \uc501\ub2c8\ub2e4."));
        n = AiSettingsDialog.addRow(jPanel, gridBagConstraints, n, SpeechDirector.localized("AiCharacter", "\uce90\ub9ad\ud130"), this.character);
        n = AiSettingsDialog.addRow(jPanel, gridBagConstraints, n, SpeechDirector.localized("AiCharacterVoice", "\uc774 \uce90\ub9ad\ud130 \ubcf4\uc774\uc2a4"), this.characterVoice);
        this.character.setToolTipText(SpeechDirector.localized("AiCharacterPickHint", "\uc544\ub798 \ubcf4\uc774\uc2a4 \uce78\uacfc \ud398\ub974\uc18c\ub098 \ud3b8\uc9d1, \uadf8\ub9ac\uace0 \ubaa9\uc18c\ub9ac \ud0ed\uc758 \ubbf8\ub9ac\ub4e3\uae30\uac00 \ubaa8\ub450 \uc5ec\uae30\uc11c \uace0\ub978 \uce90\ub9ad\ud130\ub97c \ub300\uc0c1\uc73c\ub85c \ud569\ub2c8\ub2e4."));
        this.characterVoice.setToolTipText(SpeechDirector.localized("AiCharacterVoiceHint", "\uc774 \uce90\ub9ad\ud130\uc5d0\uac8c\ub9cc \uc4f8 \ubcf4\uc774\uc2a4 ID\uc785\ub2c8\ub2e4. \ubaa9\uc18c\ub9ac \ud0ed\uc5d0\uc11c \uace0\ub978 \ud68c\uc0ac\uc758 ID \ud615\uc2dd\uc744 \uadf8\ub300\ub85c \uc501\ub2c8\ub2e4. \ud398\ub974\uc18c\ub098 \uc6d0\ubb38\uc740 img/<\uce90\ub9ad\ud130>/persona.txt \ud30c\uc77c\uc5d0 \uc800\uc7a5\ub429\ub2c8\ub2e4."));
        JPanel jPanel2 = new JPanel(new FlowLayout(0, 6, 0));
        JButton jButton = new JButton(SpeechDirector.localized("AiEditPersona", "\uc774 \uce90\ub9ad\ud130 \ud398\ub974\uc18c\ub098 \ud3b8\uc9d1..."));
        jButton.setToolTipText(SpeechDirector.localized("AiEditPersonaHint", "\uc774 \uce90\ub9ad\ud130\uac00 \uc790\uae30\ub97c \ub204\uad6c\ub77c\uace0 \uc5ec\uae30\ub294\uc9c0 \uc801\ub294 \uae00\uc785\ub2c8\ub2e4. \ub9e4\ubc88 \ub300\ud654 \uc55e\uba38\ub9ac\uc5d0 \ubd99\uc5b4 \ub9d0\ud22c\uc640 \uc131\uaca9\uc744 \uc815\ud569\ub2c8\ub2e4."));
        jButton.addActionListener(actionEvent -> this.openPersonaEditor());
        jPanel2.add(jButton);
        n = AiSettingsDialog.addWide(jPanel, gridBagConstraints, n, jPanel2);
        AiSettingsDialog.filler(jPanel, gridBagConstraints, n);
        return jPanel;
    }

    private JPanel autoTab() {
        JPanel jPanel = AiSettingsDialog.tabPanel();
        GridBagConstraints gridBagConstraints = AiSettingsDialog.tabConstraints();
        int n = 0;
        n = AiSettingsDialog.intro(jPanel, gridBagConstraints, n, SpeechDirector.localized("AiAutoHint", "\ub9d0\uc744 \uac78\uc9c0 \uc54a\uc544\ub3c4 \ub8e8\ubbf8\uac00 \uba3c\uc800 \ub9d0\ud558\ub294 \uae30\ub2a5\uc785\ub2c8\ub2e4. \uc790\uc728 \ud63c\uc7a3\ub9d0\uc740 \uc815\ud574\uc9c4 \uac04\uaca9\uc73c\ub85c \ud55c\ub9c8\ub514 \ud558\uace0, \ud654\uba74 \uad6c\uacbd\uc740 \ub8e8\ubbf8\uac00 \uc788\ub294 \ubaa8\ub2c8\ud130\ub97c \ucea1\ucc98\ud574 \ubcf4\uace0 \ud55c\ub9c8\ub514 \ud569\ub2c8\ub2e4. \ub458 \ub2e4 \ub450\ub1cc \ud0ed\uc758 LLM\uc744 \uc4f0\ubbc0\ub85c \uc8fc\uae30\uac00 \uc9e7\uc744\uc218\ub85d \uc694\uae08\uc774 \ub298\uc5b4\ub0a9\ub2c8\ub2e4."));
        this.chatterMinutes.setToolTipText(SpeechDirector.localized("AiChatterEveryHint", "\ud63c\uc7a3\ub9d0 \uc0ac\uc774\uc758 \uac04\uaca9\uc785\ub2c8\ub2e4. \uae30\ubcf8\uac12 20\ubd84\uc774\uba74 \ud55c \uc2dc\uac04\uc5d0 \ub450\uc138 \ubc88 \ub9d0\ud558\ub294 \uc815\ub3c4\uc774\uace0, \ub9d0\ubc84\ub987\ucc98\ub7fc \ub290\uaef4\uc9c0\uc9c0 \uc54a\ub3c4\ub85d \uc2e4\uc81c\ub85c\ub294 \uc774 \uc2dc\uac04\ubcf4\ub2e4 \uc870\uae08\uc529 \ub2a6\uac8c \ub9d0\ud569\ub2c8\ub2e4."));
        n = AiSettingsDialog.toggleRow(jPanel, gridBagConstraints, n, this.chatterEnabled, this.chatterMinutes, AiSettingsDialog.everyCluster(SpeechDirector.localized("AiChatterEvery", "\uc8fc\uae30(\ubd84):"), this.chatterMinutes));
        this.screenwatchSeconds.setToolTipText(SpeechDirector.localized("AiScreenwatchEveryHint", "\ud654\uba74\uc744 \ud55c \ubc88 \ubcf4\uace0 \ub2e4\uc74c\uc5d0 \ubcfc \ub54c\uae4c\uc9c0\uc758 \uac04\uaca9\uc785\ub2c8\ub2e4. \uae30\ubcf8\uac12 60\ucd08\ub294 1\ubd84\uc5d0 \ud55c \uc7a5\uc529 \ud654\uba74\uc744 \ubcf4\ub0b4\ub294 \uc148\uc774\ub77c, \uc774 \uc22b\uc790\ub97c \uc904\uc77c\uc218\ub85d \uc694\uae08\uc774 \uadf8\ub9cc\ud07c \ube68\ub9ac \ub298\uc5b4\ub0a9\ub2c8\ub2e4."));
        n = AiSettingsDialog.toggleRow(jPanel, gridBagConstraints, n, this.screenwatchEnabled, this.screenwatchSeconds, AiSettingsDialog.everyCluster(SpeechDirector.localized("AiScreenwatchEvery", "\uc8fc\uae30(\ucd08):"), this.screenwatchSeconds));
        JLabel jLabel = new JLabel(SettingsUi.hintHtml((String)SpeechDirector.localized("AiScreenwatchWarning", "\uc8fc\uc758: \ud654\uba74 \uad6c\uacbd\uc744 \ucf1c\uba74 \ub8e8\ubbf8\uac00 \uc788\ub294 \ubaa8\ub2c8\ud130\uc758 \ud654\uba74\uc774 \ub450\ub1cc \ud0ed\uc5d0\uc11c \uace0\ub978 LLM\uc73c\ub85c \uc804\uc1a1\ub429\ub2c8\ub2e4. \uadf8\ub9bc\uc744 \uc77d\uc744 \uc218 \uc788\ub294 \ubaa8\ub378\uc774\uc5b4\uc57c \ud558\uba70, Google (Gemini) \ud504\ub9ac\uc14b\uc744 \uad8c\ud569\ub2c8\ub2e4."), (int)340));
        ProtoUi.describe((JLabel)jLabel);
        Page page = (Page)jPanel;
        JPanel jPanel2 = page.openOrNew();
        jPanel2.add(Box.createVerticalStrut(8));
        jLabel.setAlignmentX(0.0f);
        jPanel2.add(jLabel);
        AiSettingsDialog.filler(jPanel, gridBagConstraints, n);
        return jPanel;
    }

    private static int sliderRow(JPanel jPanel, GridBagConstraints gridBagConstraints, int n, String string, JSlider jSlider, JLabel jLabel) {
        Page page = (Page)jPanel;
        JPanel jPanel2 = page.openOrNew();
        page.separate();
        ProtoUi.sliderize((JSlider)jSlider, (boolean)true);
        jSlider.setOpaque(false);
        jLabel.setFont(jLabel.getFont().deriveFont(12.5f));
        JLabel jLabel2 = new JLabel(string);
        jLabel2.setFont(jLabel2.getFont().deriveFont(1, 13.5f));
        JLabel jLabel3 = new JLabel(" ");
        jLabel3.setFont(jLabel3.getFont().deriveFont(12.5f));
        LumiTheme.speakColour((JComponent)jLabel3, () -> ProtoUi.INK62);
        page.remember(jLabel3, jSlider);
        JPanel jPanel3 = SettingsUi.row((LayoutManager)new BorderLayout(12, 0));
        jPanel3.add((Component)jLabel2, "West");
        jPanel3.add((Component)jSlider, "Center");
        JPanel jPanel4 = new JPanel(new GridBagLayout());
        jPanel4.setOpaque(false);
        jPanel4.add(jLabel);
        jPanel3.add((Component)jPanel4, "East");
        jPanel2.add(jPanel3);
        jLabel3.setAlignmentX(0.0f);
        jPanel2.add(jLabel3);
        return n + 1;
    }

    private void syncTtsVolumeLabel() {
        int n = this.ttsVolume.getValue();
        this.ttsVolumeValue.setText((String)(n == 0 ? SpeechDirector.localized("SoundVolumeOff", "\uaebc\uc9d0") : n + "%"));
    }

    private static int toggleRow(JPanel jPanel, GridBagConstraints gridBagConstraints, int n, JCheckBox jCheckBox, Component component, JComponent jComponent) {
        String string;
        JComponent jComponent2;
        Page page = (Page)jPanel;
        page.closeCard();
        JPanel jPanel2 = page.card(null);
        page.separate();
        ProtoUi.toggleize((JCheckBox)jCheckBox);
        jCheckBox.setFont(jCheckBox.getFont().deriveFont(13.5f));
        JLabel jLabel = new JLabel(" ");
        ProtoUi.describe((JLabel)jLabel);
        if (component instanceof JComponent) {
            jComponent2 = (JComponent)component;
            string = jComponent2.getToolTipText();
        } else {
            string = null;
        }
        if (string != null && !string.isBlank()) {
            jLabel.setText(SettingsUi.hintHtml((String)Page.firstSentence(string), (int)(jComponent != null ? 280 : 340)));
        }
        jComponent2 = new JPanel();
        jComponent2.setOpaque(false);
        jComponent2.setLayout(new BoxLayout(jComponent2, 1));
        jCheckBox.setAlignmentX(0.0f);
        jLabel.setAlignmentX(0.0f);
        jComponent2.add(jCheckBox);
        jComponent2.add(jLabel);
        JPanel jPanel3 = SettingsUi.row((LayoutManager)new BorderLayout(12, 0));
        jPanel3.add((Component)jComponent2, "Center");
        if (jComponent != null) {
            JPanel jPanel4 = new JPanel(new GridBagLayout());
            jPanel4.setOpaque(false);
            jPanel4.add(jComponent);
            jPanel3.add((Component)jPanel4, "East");
        }
        jPanel2.add(jPanel3);
        return n + 1;
    }

    private static JComponent everyCluster(String string, JSpinner jSpinner) {
        JLabel jLabel = new JLabel(string);
        jLabel.setFont(jLabel.getFont().deriveFont(12.5f));
        ProtoUi.style((JComponent)jSpinner, (String)"background: #0F1A2E; borderColor: #445468; arc: 8; buttonBackground: #16233A");
        jSpinner.setPreferredSize(new Dimension(64, 26));
        return ProtoUi.gapRow((int)6, (Component[])new Component[]{jLabel, jSpinner});
    }

    private JPanel linkTab() {
        JPanel jPanel = AiSettingsDialog.tabPanel();
        GridBagConstraints gridBagConstraints = AiSettingsDialog.tabConstraints();
        int n = 0;
        n = AiSettingsDialog.intro(jPanel, gridBagConstraints, n, SpeechDirector.localized("AiLinkHint", "Claude Code\uc640 \uc774\uc5b4 \ub450\uba74 \uc791\uc5c5\uc774 \ub05d\ub0ac\uc744 \ub54c Claude\uac00 \ub8e8\ubbf8 \ub9d0\ud48d\uc120\uc73c\ub85c \uc54c\ub9bd\ub2c8\ub2e4. \uc544\ub798 \ubc84\ud2bc\uc774 MCP \uc11c\ubc84 \ub4f1\ub85d \uba85\ub839\uc744 \ud074\ub9bd\ubcf4\ub4dc\uc5d0 \ubcf5\uc0ac\ud569\ub2c8\ub2e4. \uadf8\uac83\uc744 \ud130\ubbf8\ub110\uc5d0 \ubd99\uc5ec\ub123\uc5b4 \uc2e4\ud589\ud558\uba74 \ub429\ub2c8\ub2e4. Python 3\uc774 \ud544\uc694\ud569\ub2c8\ub2e4."));
        JPanel jPanel2 = new JPanel(new FlowLayout(0, 6, 0));
        JButton jButton = new JButton(SpeechDirector.localized("AiCopyMcp", "MCP \ub4f1\ub85d \uba85\ub839 \ubcf5\uc0ac"));
        jButton.setToolTipText(SpeechDirector.localized("AiCopyMcpHint", "\ubcf5\uc0ac\ub418\ub294 \uba85\ub839\uc740 \uc774 \ud3f4\ub354\uc758 tools/lumi_mcp.py\ub97c Claude Code\uc5d0 \ub4f1\ub85d\ud569\ub2c8\ub2e4. \ubcf5\uc0ac\ub41c \uba85\ub839 \uc804\uccb4\ub294 \ucc3d \uc544\ub798 \uc904\uc5d0\ub3c4 \ubcf4\uc5ec \uc8fc\ub2c8 \ub208\uc73c\ub85c \ud655\uc778\ud558\uace0 \uc2e4\ud589\ud558\uc138\uc694."));
        jButton.addActionListener(actionEvent -> this.copyMcpCommand());
        jPanel2.add(jButton);
        n = AiSettingsDialog.addWide(jPanel, gridBagConstraints, n, jPanel2);
        AiSettingsDialog.filler(jPanel, gridBagConstraints, n);
        return jPanel;
    }

    private static JPanel tabPanel() {
        return new Page();
    }

    private static GridBagConstraints tabConstraints() {
        return new GridBagConstraints();
    }

    private static int intro(JPanel jPanel, GridBagConstraints gridBagConstraints, int n, String string) {
        Page page = (Page)jPanel;
        page.closeCard();
        JPanel jPanel2 = page.card(null);
        JLabel jLabel = new JLabel(SettingsUi.hintHtml((String)string).replaceFirst("width:\\d+px", "width:330px"));
        LumiTheme.speakColour((JComponent)jLabel, () -> ProtoUi.INK62);
        jLabel.setAlignmentX(0.0f);
        jPanel2.add(jLabel);
        page.closeCard();
        return n + 1;
    }

    private static int groupLabel(JPanel jPanel, GridBagConstraints gridBagConstraints, int n, String string) {
        Page page = (Page)jPanel;
        page.closeCard();
        page.card(string);
        return n + 1;
    }

    private static int addRow(JPanel jPanel, GridBagConstraints gridBagConstraints, int n, String string, Component component) {
        Page page = (Page)jPanel;
        JPanel jPanel2 = page.openOrNew();
        page.separate();
        AiSettingsDialog.dressInput(component);
        JLabel jLabel = new JLabel(string);
        jLabel.setFont(jLabel.getFont().deriveFont(1, 13.5f));
        JLabel jLabel2 = new JLabel(" ");
        jLabel2.setFont(jLabel2.getFont().deriveFont(12.5f));
        LumiTheme.speakColour((JComponent)jLabel2, () -> ProtoUi.INK62);
        page.remember(jLabel2, component);
        JPanel jPanel3 = SettingsUi.row((LayoutManager)new BorderLayout(12, 0));
        jLabel.setAlignmentX(0.0f);
        jPanel3.add((Component)jLabel, "Center");
        JPanel jPanel4 = SettingsUi.row((LayoutManager)new FlowLayout(2, 0, 0));
        jPanel4.add(component);
        jPanel3.add((Component)jPanel4, "East");
        jPanel2.add(jPanel3);
        jLabel2.setAlignmentX(0.0f);
        jPanel2.add(jLabel2);
        return n + 1;
    }

    private static void dressInput(Component component) {
        if (component instanceof JComboBox) {
            JComboBox jComboBox = (JComboBox)component;
            ProtoUi.style((JComponent)jComboBox, (String)"arc: 8; background: null; buttonBackground: null; borderColor: #445468; focusWidth: 0; arrowType: chevron; padding: 0,8,0,2");
            jComboBox.setFont(jComboBox.getFont().deriveFont(12.5f));
            jComboBox.setPreferredSize(new Dimension(200, 30));
        } else if (component instanceof JTextComponent) {
            JTextComponent jTextComponent = (JTextComponent)component;
            ProtoUi.style((JComponent)((JComponent)component), (String)"arc: 8; background: #0F1A2E; borderColor: #445468; focusedBorderColor: #4ECCD3; margin: 4,10,4,10");
            jTextComponent.setFont(jTextComponent.getFont().deriveFont(12.5f));
            jTextComponent.setPreferredSize(new Dimension(200, 30));
        }
    }

    private static int addWide(JPanel jPanel, GridBagConstraints gridBagConstraints, int n, Component component) {
        JComponent jComponent;
        Page page = (Page)jPanel;
        if (component instanceof JCheckBox) {
            JCheckBox jCheckBox = (JCheckBox)component;
            ProtoUi.toggleize((JCheckBox)jCheckBox);
            jCheckBox.setFont(jCheckBox.getFont().deriveFont(13.5f));
            JPanel jPanel2 = page.openOrNew();
            page.separate();
            JPanel jPanel3 = SettingsUi.row((LayoutManager)new FlowLayout(0, 0, 0));
            jPanel3.add(jCheckBox);
            jPanel2.add(jPanel3);
            return n + 1;
        }
        page.closeCard();
        if (component instanceof JPanel) {
            jComponent = (JPanel)component;
            jComponent.setOpaque(false);
            for (Component component2 : jComponent.getComponents()) {
                if (component2 instanceof JCheckBox) {
                    JCheckBox jCheckBox = (JCheckBox)component2;
                    ProtoUi.toggleize((JCheckBox)jCheckBox);
                    jCheckBox.setFont(jCheckBox.getFont().deriveFont(13.5f));
                    continue;
                }
                if (!(component2 instanceof AbstractButton)) continue;
                AbstractButton abstractButton = (AbstractButton)component2;
                ProtoUi.buttonize((AbstractButton)abstractButton, (ProtoUi.ButtonStyle)ProtoUi.ButtonStyle.OUTLINE);
                abstractButton.setFont(abstractButton.getFont().deriveFont(12.5f));
                abstractButton.setMargin(new Insets(6, 12, 6, 12));
            }
        }
        if (component instanceof JComponent) {
            jComponent = (JComponent)component;
            jComponent.setAlignmentX(0.0f);
        }
        page.add(component);
        page.add(Box.createVerticalStrut(12));
        return n + 1;
    }

    private static void filler(JPanel jPanel, GridBagConstraints gridBagConstraints, int n) {
        ((Page)jPanel).closeCard();
        jPanel.add(Box.createVerticalGlue());
    }

    private List<String> providerIds() {
        return List.copyOf(AiSettings.LLM_PROVIDERS.keySet());
    }

    private String selectedProviderId() {
        return this.providerIds().get(this.llmProvider.getSelectedIndex());
    }

    private void load() {
        AiSettings aiSettings = AiSettings.get();
        this.llmStash.clear();
        this.shownProvider = aiSettings.llmProvider();
        this.llmProvider.setSelectedIndex(this.providerIds().indexOf(this.shownProvider));
        this.llmBase.setText(aiSettings.llmBaseFor(this.shownProvider));
        this.llmModel.setText(aiSettings.llmModelFor(this.shownProvider));
        this.llmKey.setText(aiSettings.llmKeyFor(this.shownProvider));
        this.syncLlmCredentialState();
        this.ttsEnabled.setSelected(aiSettings.ttsEnabled());
        this.ttsProvider.setSelectedIndex("gpt_sovits".equals(aiSettings.ttsProvider()) ? 2 : ("eleven".equals(aiSettings.ttsProvider()) ? 1 : 0));
        this.fishKey.setText(aiSettings.fishKey());
        this.fishVoice.setText(aiSettings.fishVoice());
        this.fishModel.setSelectedItem(aiSettings.fishModel());
        this.elevenKey.setText(aiSettings.elevenKey());
        this.elevenVoice.setText(aiSettings.elevenVoice());
        this.elevenModel.setText(aiSettings.elevenModel());
        this.gptSovitsBase.setText(aiSettings.gptSovitsBase());
        this.gptSovitsRuntime.setText(aiSettings.gptSovitsRuntime());
        this.gptSovitsGptWeights.setText(aiSettings.gptSovitsGptWeights());
        this.gptSovitsSovitsWeights.setText(aiSettings.gptSovitsSovitsWeights());
        this.gptSovitsReferenceAudio.setText(aiSettings.gptSovitsReferenceAudio());
        this.gptSovitsReferenceText.setText(aiSettings.gptSovitsReferenceText());
        this.gptSovitsTextLanguage.setText(aiSettings.gptSovitsTextLanguage());
        this.gptSovitsPromptLanguage.setText(aiSettings.gptSovitsPromptLanguage());
        this.gptSovitsPowerMode.setSelectedItem(aiSettings.gptSovitsPowerMode());
        this.gptSovitsSpeed.setText(aiSettings.gptSovitsSpeed());
        this.externalLines.setSelected(aiSettings.ttsExternalLines());
        this.ttsSave.setSelected(aiSettings.ttsSave());
        this.ttsVolume.setValue(aiSettings.ttsVolume());
        this.syncTtsVolumeLabel();
        this.character.removeAllItems();
        this.voiceStash.clear();
        for (String string : Characters.known()) {
            this.character.addItem(string);
        }
        this.shownCharacter = this.character.getItemCount() > 0 ? this.character.getItemAt(0) : "Lumi";
        this.character.setSelectedItem(this.shownCharacter);
        this.characterVoice.setText(aiSettings.voiceOverride(this.shownCharacter));
        this.chatterEnabled.setSelected(aiSettings.chatterEnabled());
        this.chatterMinutes.setValue(aiSettings.chatterMinutes());
        this.setStatus(" ", false);
        String string = ChatService.lastTtsError();
        if (string != null) {
            this.setStatus(AiSettingsDialog.format("AiStatusLastTtsError", "\uc9c1\uc804 \uc74c\uc131 \uc7ac\uc0dd \uc2e4\ud328: %s", string), true);
        }
        this.screenwatchEnabled.setSelected(aiSettings.screenwatchEnabled());
        this.screenwatchSeconds.setValue(aiSettings.screenwatchSeconds());
    }

    private void onProviderSwitch() {
        String string = this.selectedProviderId();
        if (this.shownProvider == null || string.equals(this.shownProvider)) {
            return;
        }
        this.llmStash.put(this.shownProvider, new String[]{this.llmBase.getText().trim(), this.llmModel.getText().trim(), new String(this.llmKey.getPassword()).trim()});
        this.shownProvider = string;
        AiSettings aiSettings = AiSettings.get();
        String[] stringArray = this.llmStash.get(string);
        if (stringArray != null) {
            this.llmBase.setText(stringArray[0]);
            this.llmModel.setText(stringArray[1]);
            this.llmKey.setText(stringArray[2]);
        } else {
            this.llmBase.setText(aiSettings.llmBaseFor(string));
            this.llmModel.setText(aiSettings.llmModelFor(string));
            this.llmKey.setText(aiSettings.llmKeyFor(string));
        }
        this.syncLlmCredentialState();
    }

    private void syncLlmCredentialState() {
        boolean bl = "gpt_web".equals(this.shownProvider);
        this.llmKey.setEnabled(!bl);
        this.llmKey.setToolTipText(bl ? "ChatGPT (OAuth)\ub294 API \ud0a4 \ub300\uc2e0 LUMI to GPT\uc5d0\uc11c \uc5f0\uacb0\ud55c ChatGPT \uacc4\uc815\uc744 \uc0ac\uc6a9\ud569\ub2c8\ub2e4." : SpeechDirector.localized("AiApiKeyHint", "\uace0\ub978 \ud68c\uc0ac \ud648\ud398\uc774\uc9c0\uc5d0\uc11c \ubc1c\uae09\ubc1b\ub294 \ube44\ubc00 \ubb38\uc790\uc5f4\uc785\ub2c8\ub2e4."));
    }

    private void onCharacterSwitch() {
        Object object = this.character.getSelectedItem();
        if (object == null) {
            return;
        }
        String string = object.toString();
        if (string.equals(this.shownCharacter)) {
            return;
        }
        if (this.shownCharacter != null) {
            this.voiceStash.put(this.shownCharacter, this.characterVoice.getText().trim());
        }
        this.shownCharacter = string;
        String string2 = this.voiceStash.get(string);
        this.characterVoice.setText(string2 != null ? string2 : AiSettings.get().voiceOverride(string));
    }

    private void selectCharacter(String string) {
        for (int i = 0; i < this.character.getItemCount(); ++i) {
            if (!this.character.getItemAt(i).equals(string)) continue;
            this.character.setSelectedIndex(i);
            return;
        }
    }

    private String selectedCharacter() {
        Object object = this.character.getSelectedItem();
        return object == null ? "Lumi" : object.toString();
    }

    private void apply() {
        AiSettings aiSettings = AiSettings.get();
        this.llmStash.put(this.shownProvider, new String[]{this.llmBase.getText().trim(), this.llmModel.getText().trim(), new String(this.llmKey.getPassword()).trim()});
        for (Map.Entry<String, String[]> entry : this.llmStash.entrySet()) {
            aiSettings.set("llm.base." + entry.getKey(), entry.getValue()[0]);
            aiSettings.set("llm.model." + entry.getKey(), entry.getValue()[1]);
            aiSettings.set("llm.key." + entry.getKey(), entry.getValue()[2]);
        }
        aiSettings.set("llm.provider", this.selectedProviderId());
        aiSettings.set("tts.enabled", String.valueOf(this.ttsEnabled.isSelected()));
        aiSettings.set("tts.provider", switch (this.ttsProvider.getSelectedIndex()) {
            case 1 -> "eleven";
            case 2 -> "gpt_sovits";
            default -> "fish";
        });
        aiSettings.set("tts.fish.key", new String(this.fishKey.getPassword()));
        aiSettings.set("tts.fish.voice", this.fishVoice.getText());
        aiSettings.set("tts.fish.model", String.valueOf(this.fishModel.getSelectedItem()));
        aiSettings.set("tts.eleven.key", new String(this.elevenKey.getPassword()));
        aiSettings.set("tts.eleven.voice", this.elevenVoice.getText());
        aiSettings.set("tts.eleven.model", this.elevenModel.getText());
        aiSettings.set("tts.gpt_sovits.base", this.gptSovitsBase.getText());
        aiSettings.set("tts.gpt_sovits.runtime", this.gptSovitsRuntime.getText());
        aiSettings.set("tts.gpt_sovits.gpt_weights", this.gptSovitsGptWeights.getText());
        aiSettings.set("tts.gpt_sovits.sovits_weights", this.gptSovitsSovitsWeights.getText());
        aiSettings.set("tts.gpt_sovits.reference_audio", this.gptSovitsReferenceAudio.getText());
        aiSettings.set("tts.gpt_sovits.reference_text", this.gptSovitsReferenceText.getText());
        aiSettings.set("tts.gpt_sovits.text_language", this.gptSovitsTextLanguage.getText());
        aiSettings.set("tts.gpt_sovits.prompt_language", this.gptSovitsPromptLanguage.getText());
        aiSettings.set("tts.gpt_sovits.power_mode", String.valueOf(this.gptSovitsPowerMode.getSelectedItem()));
        aiSettings.set("tts.gpt_sovits.speed", this.gptSovitsSpeed.getText());
        aiSettings.set("tts.externalLines", String.valueOf(this.externalLines.isSelected()));
        aiSettings.set("tts.save", String.valueOf(this.ttsSave.isSelected()));
        aiSettings.set("tts.volume", String.valueOf(this.ttsVolume.getValue()));
        if (this.shownCharacter != null) {
            this.voiceStash.put(this.shownCharacter, this.characterVoice.getText().trim());
        }
        for (Map.Entry<String, String> entry : this.voiceStash.entrySet()) {
            aiSettings.set(aiSettings.voiceOverrideKey(entry.getKey()), entry.getValue());
        }
        aiSettings.set("chatter.enabled", String.valueOf(this.chatterEnabled.isSelected()));
        aiSettings.set("chatter.minutes", String.valueOf(this.chatterMinutes.getValue()));
        aiSettings.set("screenwatch.enabled", String.valueOf(this.screenwatchEnabled.isSelected()));
        aiSettings.set("screenwatch.seconds", String.valueOf(this.screenwatchSeconds.getValue()));
        aiSettings.save();
    }

    private void copyMcpCommand() {
        File file = new File("tools/lumi_mcp.py").getAbsoluteFile();
        boolean bl = System.getProperty("os.name", "").toLowerCase().contains("win");
        String string = bl ? "python" : "python3";
        String string2 = "claude mcp add --scope user lumi -- " + string + " \"" + file.getPath() + "\"";
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(string2), null);
            this.setStatus(AiSettingsDialog.format("AiStatusCopied", "\ubcf5\uc0ac\ud588\uc5b4\uc694 - \ud130\ubbf8\ub110\uc5d0 \ubd99\uc5ec\ub123\uc5b4 \uc2e4\ud589\ud558\uc138\uc694: %s", string2), false);
        }
        catch (Exception exception) {
            this.setStatus(AiSettingsDialog.format("AiStatusCopyFailed", "\ud074\ub9bd\ubcf4\ub4dc \ubcf5\uc0ac \uc2e4\ud328: %s", exception.getMessage()), true);
        }
    }

    private static void background(String string, Runnable runnable) {
        Thread thread = new Thread(runnable, string);
        thread.setDaemon(true);
        thread.start();
    }

    private void saveAndTest() {
        this.apply();
        this.setStatus(SpeechDirector.localized("AiStatusTesting", "\uc5f0\uacb0 \ud655\uc778 \uc911..."), false);
        AiSettingsDialog.background("Lumi-AI-Test", () -> {
            try {
                long l = System.currentTimeMillis();
                String string = LlmClient.chat((String)"\ud55c \ub2e8\uc5b4\ub85c\ub9cc \ub2f5\ud574\ub77c.", List.of(new LlmClient.Msg("user", "\uc548\ub155\uc774\ub77c\uace0 \ub9d0\ud574\ubd10")));
                long l2 = System.currentTimeMillis() - l;
                AiSettingsDialog.onUi(() -> this.setStatus(AiSettingsDialog.format("AiStatusTestOk", "\uc5f0\uacb0 \uc131\uacf5 (%dms): %s", l2, string), false));
            }
            catch (Exception exception) {
                AiSettingsDialog.onUi(() -> this.setStatus(AiSettingsDialog.format("AiStatusTestFailed", "\uc5f0\uacb0 \uc2e4\ud328: %s", AiSettingsDialog.errorMessage(exception)), true));
            }
        });
    }

    private void saveAndPreview() {
        this.apply();
        String string = this.selectedCharacter();
        this.setStatus(SpeechDirector.localized("AiStatusSynthesizing", "\ubbf8\ub9ac\ub4e3\uae30 \ud569\uc131 \uc911..."), false);
        AiSettingsDialog.background("Lumi-TTS-Preview", () -> {
            try {
                TtsClient.Audio audio = TtsClient.synthesize((String)AiSettingsDialog.format("AiPreviewLine", "\uc548\ub155\ud558\uc138\uc694, %s\uc608\uc694! \uc2dc\uc2a4\ud15c \uc0c1\ud0dc \uc591\ud638\ud574\uc694.", string), (String)string);
                ChatService.clearTtsError();
                TtsPlayer.play((TtsClient.Audio)audio, null, null);
                AiSettingsDialog.onUi(() -> this.setStatus(this.ttsEnabled.isSelected() ? AiSettingsDialog.format("AiStatusPlaying", "\uc7ac\uc0dd \uc911 (%s\ucd08)", (double)audio.millis() / 1000.0) : AiSettingsDialog.localizedPlayingMuted(), !this.ttsEnabled.isSelected()));
            }
            catch (Exception exception) {
                ChatService.noteTtsFailure((String)"preview", (Exception)exception);
                AiSettingsDialog.onUi(() -> this.setStatus(AiSettingsDialog.format("AiStatusPreviewFailed", "\ubbf8\ub9ac\ub4e3\uae30 \uc2e4\ud328: %s", AiSettingsDialog.errorMessage(exception)), true));
            }
        });
    }

    private static String errorMessage(Exception exception) {
        String string = exception.getMessage();
        return string == null || string.isBlank() ? exception.getClass().getSimpleName() : string;
    }

    private void openVoiceFolder() {
        File file = VoiceRecorder.directory();
        try {
            file.mkdirs();
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                this.setStatus(AiSettingsDialog.format("AiStatusNoFolderOpen", "\uc774 \ud658\uacbd\uc5d0\uc11c\ub294 \ud3f4\ub354\ub97c \ubabb \uc5f4\uc5b4\uc694: %s", file.getAbsolutePath()), true);
                return;
            }
            Desktop.getDesktop().open(file);
            long l = AiSettingsDialog.countWavs(file);
            this.setStatus(l == 0L ? AiSettingsDialog.format("AiStatusNoVoiceYet", "\uc544\uc9c1 \uc800\uc7a5\ub41c \uc74c\uc131\uc774 \uc5c6\uc5b4\uc694 (%s)", file.getPath()) : AiSettingsDialog.format("AiStatusVoiceCount", "\uc74c\uc131 %d\uac1c - index.tsv\uc5d0 \ub300\uc0ac\uac00 \ud568\uaed8 \uc801\ud600 \uc788\uc5b4\uc694.", l), false);
        }
        catch (Exception exception) {
            this.setStatus(AiSettingsDialog.format("AiStatusOpenFolderFailed", "\ud3f4\ub354 \uc5f4\uae30 \uc2e4\ud328: %s (%s)", exception.getMessage(), file.getAbsolutePath()), true);
        }
    }

    static String statusHtml(String string, FontMetrics fontMetrics) {
        if (string == null || string.isBlank()) {
            return " ";
        }
        if (fontMetrics == null || fontMetrics.stringWidth(string) <= 510) {
            return string;
        }
        StringBuilder stringBuilder = new StringBuilder("<html>");
        List<String> list = AiSettingsDialog.fold(string, fontMetrics, 510, 4);
        for (int i = 0; i < list.size(); ++i) {
            if (i > 0) {
                stringBuilder.append("<br>");
            }
            stringBuilder.append(AiSettingsDialog.escapeHtml(list.get(i)));
        }
        return stringBuilder.append("</html>").toString();
    }

    static List<String> fold(String string, FontMetrics fontMetrics, int n, int n2) {
        ArrayList<String> arrayList = new ArrayList<String>();
        int n3 = 0;
        while (n3 < string.length() && arrayList.size() < n2) {
            int n4;
            int n5 = -1;
            for (n4 = n3; n4 < string.length() && fontMetrics.stringWidth(string.substring(n3, n4 + 1)) <= n; ++n4) {
                if (string.charAt(n4) != ' ') continue;
                n5 = n4;
            }
            if (n4 >= string.length()) {
                arrayList.add(string.substring(n3));
                return arrayList;
            }
            int n6 = n5 > n3 ? n5 + 1 : Math.max(n4, n3 + 1);
            arrayList.add(string.substring(n3, n6));
            n3 = n6;
        }
        if (n3 < string.length() && !arrayList.isEmpty()) {
            arrayList.set(arrayList.size() - 1, (String)arrayList.get(arrayList.size() - 1) + "\u2026");
        }
        return arrayList;
    }

    private static String escapeHtml(String string) {
        return string.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void browse(String string) {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                this.setStatus(AiSettingsDialog.format("AiStatusNoBrowser", "\uc774 \ud658\uacbd\uc5d0\uc11c\ub294 \ube0c\ub77c\uc6b0\uc800\ub97c \uc5f4 \uc218 \uc5c6\uc5b4\uc694. \uc8fc\uc18c\ub97c \uc9c1\uc811 \uc785\ub825\ud574 \uc8fc\uc138\uc694: %s", string), true);
                return;
            }
            Desktop.getDesktop().browse(new URI(string));
            this.setStatus(AiSettingsDialog.format("AiStatusBrowserOpened", "\ube0c\ub77c\uc6b0\uc800\uc5d0\uc11c \uc5f4\uc5c8\uc5b4\uc694: %s", string), false);
        }
        catch (Exception exception) {
            this.setStatus(AiSettingsDialog.format("AiStatusBrowserFailed", "\ube0c\ub77c\uc6b0\uc800 \uc5f4\uae30 \uc2e4\ud328: %s - \uc8fc\uc18c\ub97c \uc9c1\uc811 \uc785\ub825\ud574 \uc8fc\uc138\uc694: %s", exception.getMessage(), string), true);
        }
    }

    private static long countWavs(File file2) {
        File[] fileArray = file2.listFiles((file, string) -> string.endsWith(".wav"));
        return fileArray == null ? 0L : (long)fileArray.length;
    }

    private void openPersonaEditor() {
        String string = this.selectedCharacter();
        JDialog jDialog = new JDialog(this, AiSettingsDialog.format("AiPersonaTitle", "%s \ud398\ub974\uc18c\ub098 \ud3b8\uc9d1", string), true);
        JTextArea jTextArea = new JTextArea(24, 56);
        jTextArea.setLineWrap(true);
        jTextArea.setWrapStyleWord(true);
        jTextArea.setText(Characters.persona((String)string));
        JButton jButton = new JButton(SpeechDirector.localized("AiSave", "\uc800\uc7a5"));
        jButton.addActionListener(actionEvent -> {
            try {
                Characters.savePersona((String)string, (String)jTextArea.getText());
                jDialog.dispose();
                this.setStatus(AiSettingsDialog.format("AiStatusPersonaSaved", "%s \ud398\ub974\uc18c\ub098\ub97c \uc800\uc7a5\ud588\uc5b4\uc694 (%s)", string, Characters.personaFile((String)string).getPath()), false);
            }
            catch (Exception exception) {
                this.setStatus(AiSettingsDialog.format("AiStatusPersonaFailed", "\ud398\ub974\uc18c\ub098 \uc800\uc7a5 \uc2e4\ud328: %s", exception.getMessage()), true);
            }
        });
        JPanel jPanel = new JPanel(new FlowLayout(2));
        jPanel.add(jButton);
        jDialog.add((Component)new JScrollPane(jTextArea), "Center");
        jDialog.add((Component)jPanel, "South");
        jDialog.pack();
        jDialog.setLocationRelativeTo(this);
        jDialog.setVisible(true);
    }

    private static String format(String string, String string2, Object ... objectArray) {
        String string3 = SpeechDirector.localized(string, string2);
        try {
            return String.format(string3, objectArray);
        }
        catch (Exception exception) {
            StringBuilder stringBuilder = new StringBuilder(string3);
            for (Object object : objectArray) {
                stringBuilder.append(' ').append(object);
            }
            return stringBuilder.toString();
        }
    }

    private static String localizedPlayingMuted() {
        return SpeechDirector.localized("AiStatusPlayingMuted", "\uc7ac\uc0dd \uc911 - \ub2e8 \uc74c\uc131 \ucf1c\uae30\uac00 \uaebc\uc838 \uc788\uc5b4 \ub300\ud654\uc5d0\uc11c\ub294 \uc548 \ub4e4\ub824\uc694. \uccb4\ud06c \ud6c4 \uc800\uc7a5\ud558\uc138\uc694.");
    }

    private void setStatus(String string, boolean bl) {
        this.status.setText(AiSettingsDialog.statusHtml(string, this.status.getFontMetrics(this.status.getFont())));
        this.status.setToolTipText(string == null || string.isBlank() ? null : string);
        LumiTheme.speakColour((JComponent)this.status, () -> bl ? LumiTheme.danger() : LumiTheme.success());
    }

    private static void onUi(Runnable runnable) {
        SwingUtilities.invokeLater(runnable);
    }

    private static final class TabShell
    extends JPanel
    implements Scrollable {
        TabShell(JComponent jComponent) {
            super(new BorderLayout());
            this.setOpaque(false);
            this.add((Component)jComponent, "Center");
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return this.getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle rectangle, int n, int n2) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle rectangle, int n, int n2) {
            return 64;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private static final class Page
    extends JPanel {
        private JPanel openCard;
        private JPanel lastHolder;
        private boolean cardHasRow;
        private final List<Object[]> rows = new ArrayList<Object[]>();
        private boolean descsFilled;

        Page() {
            this.setOpaque(true);
            LumiTheme.speakDress((JComponent)this, () -> this.setBackground(ProtoUi.tone((int)858416)));
            this.setLayout(new BoxLayout(this, 1));
            this.setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));
        }

        void closeCard() {
            this.openCard = null;
            this.cardHasRow = false;
        }

        JPanel card(String string) {
            JPanel jPanel = SettingsUi.card((Component[])new Component[0]);
            if (string != null) {
                jPanel.add(ProtoUi.sectionTitle((String)string));
                jPanel.add(Box.createVerticalStrut(6));
            }
            JPanel jPanel2 = new JPanel();
            jPanel2.setOpaque(false);
            jPanel2.setLayout(new BoxLayout(jPanel2, 1));
            jPanel2.setAlignmentX(0.0f);
            jPanel2.add(jPanel);
            jPanel2.add(Box.createVerticalStrut(12));
            this.add(jPanel2);
            this.lastHolder = jPanel2;
            this.openCard = jPanel;
            this.cardHasRow = false;
            return jPanel;
        }

        JPanel lastHolder() {
            return this.lastHolder;
        }

        JPanel openOrNew() {
            return this.openCard != null ? this.openCard : this.card(null);
        }

        void separate() {
            if (this.cardHasRow) {
                this.openCard.add(Box.createVerticalStrut(7));
                this.openCard.add(ProtoUi.hairline());
                this.openCard.add(Box.createVerticalStrut(7));
            }
            this.cardHasRow = true;
        }

        void remember(JLabel jLabel, Component component) {
            this.rows.add(new Object[]{jLabel, component});
        }

        @Override
        public void addNotify() {
            super.addNotify();
            if (this.descsFilled) {
                return;
            }
            this.descsFilled = true;
            for (Object[] objectArray : this.rows) {
                String string;
                JLabel jLabel = (JLabel)objectArray[0];
                Component component = (Component)objectArray[1];
                if (component instanceof JComponent) {
                    JComponent jComponent = (JComponent)component;
                    string = jComponent.getToolTipText();
                } else {
                    string = null;
                }
                if (string == null || string.isBlank()) {
                    jLabel.setVisible(false);
                    continue;
                }
                jLabel.setText(SettingsUi.hintHtml((String)Page.firstSentence(string), (int)340));
            }
        }

        private static String firstSentence(String string) {
            String string2 = string.replace("<html>", "").replace("</html>", "").trim();
            int n = string2.length();
            for (String string3 : new String[]{"\ub2e4. ", "\uc694. ", ". "}) {
                int n2 = string2.indexOf(string3);
                if (n2 < 0 || n2 + string3.length() >= n) continue;
                n = n2 + string3.length();
            }
            return string2.substring(0, n).trim();
        }
    }
}

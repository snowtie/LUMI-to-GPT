/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  com.group_finity.mascot.LumiBridge
 *  com.group_finity.mascot.LumiBridge$MascotSnapshot
 *  com.group_finity.mascot.Main
 *  com.group_finity.mascot.SettingsWindow
 *  com.group_finity.mascot.TrayMenuPanel
 *  com.group_finity.mascot.lumi.Features
 *  com.group_finity.mascot.lumi.FocusDirector
 *  com.group_finity.mascot.lumi.MouthFlap
 *  com.group_finity.mascot.lumi.SpeechBubbleWindow
 */
package com.group_finity.mascot.lumi;

import com.group_finity.mascot.LumiBridge;
import com.group_finity.mascot.Main;
import com.group_finity.mascot.SettingsWindow;
import com.group_finity.mascot.TrayMenuPanel;
import com.group_finity.mascot.lumi.Features;
import com.group_finity.mascot.lumi.FocusDirector;
import com.group_finity.mascot.lumi.MouthFlap;
import com.group_finity.mascot.lumi.SpeechBubbleWindow;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public final class SpeechDirector {
    private static final Logger log = Logger.getLogger(SpeechDirector.class.getName());
    private static final File SPEECH_DIR = new File("speech");
    private static final File COMMAND_FILE = new File(SPEECH_DIR, "say.txt");
    private static final File ASK_FILE = new File(SPEECH_DIR, "ask.txt");
    private static final File SETTINGS_FILE = new File(SPEECH_DIR, "settings_open.txt");
    private static final File OPTIONS_FILE = new File(SPEECH_DIR, "options_open.txt");
    private static final File PRESS_RESULT_FILE = new File(SPEECH_DIR, "press_result.txt");
    private static final File POSITION_FILE = new File(SPEECH_DIR, "mascot_pos.txt");
    private static final File BEHAVE_FILE = new File(SPEECH_DIR, "behave.txt");
    private static final File FLING_FILE = new File(SPEECH_DIR, "fling.txt");
    private static final File GAME_FILE = new File(SPEECH_DIR, "game.txt");
    private static final File DONATION_FILE = new File(SPEECH_DIR, "donation.txt");
    private static final File HOUSE_FILE = new File(SPEECH_DIR, "house.txt");
    private static final File BACKDROP_FILE = new File(SPEECH_DIR, "backdrop.txt");
    private static final File FENCE_FILE = new File(SPEECH_DIR, "fence.txt");
    private static final File QUERY_FILE = new File(SPEECH_DIR, "query.txt");
    private static final File REPORT_FILE = new File(SPEECH_DIR, "env_report.txt");
    private static final File ALL_POSITIONS_FILE = new File(SPEECH_DIR, "mascots_pos.txt");
    private static final int POLL_MS = 250;
    private static final int FOLLOW_MS = 50;
    private static final int BEACON_TICKS = 40;
    private static final int GREET_TICKS = 4;
    private static final long CLICK_MAX_MS = 500L;
    private static final int CLICK_SLOP_PX = 12;
    private static final String GREETING_KEY = "SpeechGreeting";
    private static final int GREETING_MAX = 8;
    private static final String GREETING_ANY_KEY = "SpeechGreetingAny";
    private static final int GREETING_ANY_MAX = 8;
    private static final int GREETING_LEGACY_MAX = 11;
    private static final String GREETING_FALLBACK = "\uc548\ub155\ud558\uc138\uc694 \ub8e8\ubbf8\uc5d0\uc694";
    private static final String BUNDLED_SET = "Lumi";
    private static final String GREETINGS_FILE = "greetings.txt";
    private static final File IMG_DIR = new File("img");
    private static final long GREET_COOLDOWN_MS = 45000L;
    private static final int HEAD_CLEARANCE = 140;
    private static final int BASE_CANVAS_HEIGHT = 192;
    private static SpeechDirector instance;
    private SpeechBubbleWindow bubble;
    private volatile String speakerSet;
    private volatile int speakerId;
    private long hideAt;
    private int beaconCounter;
    private String lastPositionBeacon;
    private String lastAllPositionsBeacon;
    private int greetCounter;
    private final Map<String, String> lastBehavior = new HashMap<String, String>();
    private long clickPressAt;
    private Point clickPressPoint;
    private String clickPressSet;
    private final Map<String, Long> lastClickGreetAt = new HashMap<String, Long>();
    private static List<String> greetingPool;
    private static List<String> sharedGreetingPool;
    private static String greetingPoolLanguage;
    private static String lastGreeting;
    static volatile List<String> spokenForTest;

    public static synchronized void start() {
        if (instance == null) {
            instance = new SpeechDirector();
        }
    }

    private SpeechDirector() {
        if (!SPEECH_DIR.isDirectory() && !SPEECH_DIR.mkdirs()) {
            log.warning("Could not create speech directory: " + SPEECH_DIR.getAbsolutePath());
        }
        Thread thread = new Thread(this::pollLoop, "Lumi-Speech-Poller");
        thread.setDaemon(true);
        thread.start();
        SwingUtilities.invokeLater(() -> new Timer(50, actionEvent -> {
            this.beacon();
            this.greetOnChatter();
            this.follow();
        }).start());
        this.installClickGreeter();
    }

    private void pollLoop() {
        while (true) {
            try {
                Object object;
                String string;
                if (COMMAND_FILE.isFile()) {
                    string = Files.readString(COMMAND_FILE.toPath(), StandardCharsets.UTF_8);
                    Files.delete(COMMAND_FILE.toPath());
                    this.handle(string);
                }
                if (ASK_FILE.isFile()) {
                    string = Files.readString(ASK_FILE.toPath(), StandardCharsets.UTF_8).strip();
                    Files.delete(ASK_FILE.toPath());
                    if (!string.isEmpty() && Features.aiChatUnlocked()) {
                        Features.call((String)"com.group_finity.mascot.lumi.ai.ChatService", (String)"ask", (Object[])new Object[]{string});
                    }
                }
                if (SETTINGS_FILE.isFile()) {
                    Files.delete(SETTINGS_FILE.toPath());
                    if (Features.aiChatUnlocked() || Features.aiVoiceUnlocked()) {
                        SwingUtilities.invokeLater(() -> Features.call((String)"com.group_finity.mascot.lumi.ai.AiSettingsDialog", (String)"open", (Object[])new Object[0]));
                    }
                }
                if (OPTIONS_FILE.isFile()) {
                    string = Files.readString(OPTIONS_FILE.toPath(), StandardCharsets.UTF_8).strip();
                    Files.delete(OPTIONS_FILE.toPath());
                    String optionsCommand = string;
                    SwingUtilities.invokeLater(() -> {
                        if (optionsCommand.startsWith("tab:")) {
                            SettingsWindow.showTab((String)optionsCommand.substring(4).strip());
                        } else if (optionsCommand.equals("keepone")) {
                            LumiBridge.keepOne();
                        } else if (optionsCommand.startsWith("press:")) {
                            String string2 = optionsCommand.substring(6).strip();
                            boolean bl = SettingsWindow.press((String)string2);
                            try {
                                Files.writeString(PRESS_RESULT_FILE.toPath(), (CharSequence)((bl ? "ok " : "missing ") + string2), StandardCharsets.UTF_8, new OpenOption[0]);
                            }
                            catch (IOException iOException) {}
                        } else if (optionsCommand.startsWith("shot:")) {
                            SettingsWindow.shootTabs((File)new File(optionsCommand.substring(5).strip()));
                        } else {
                            TrayMenuPanel.openSettings(null);
                        }
                    });
                }
                if (BEHAVE_FILE.isFile()) {
                    string = Files.readString(BEHAVE_FILE.toPath(), StandardCharsets.UTF_8).strip();
                    Files.delete(BEHAVE_FILE.toPath());
                    if (!string.isEmpty()) {
                        LumiBridge.setBehaviorAll((String)string);
                    }
                }
                if (FLING_FILE.isFile()) {
                    string = Files.readString(FLING_FILE.toPath(), StandardCharsets.UTF_8).strip();
                    Files.delete(FLING_FILE.toPath());
                    String[] coordinates = string.split(",");
                    if (coordinates.length == 2 && Features.enabled((String)"lumi.game.enabled")) {
                        Features.call((String)"com.group_finity.mascot.lumi.game.GameFling", (String)"fling", (Object[])new Object[]{Integer.parseInt(coordinates[0].trim()), Integer.parseInt(coordinates[1].trim())});
                    }
                }
                if (GAME_FILE.isFile()) {
                    string = Files.readString(GAME_FILE.toPath(), StandardCharsets.UTF_8).strip();
                    Files.delete(GAME_FILE.toPath());
                    if (!string.isEmpty() && Features.enabled((String)"lumi.game.enabled")) {
                        String gameCommand = string;
                        SwingUtilities.invokeLater(() -> Features.call((String)"com.group_finity.mascot.lumi.game.GameDirector", (String)"command", (Object[])new Object[]{gameCommand}));
                    }
                }
                boolean bl = Features.enabled((String)"lumi.broadcast.enabled");
                if (DONATION_FILE.isFile()) {
                    object = Files.readString(DONATION_FILE.toPath(), StandardCharsets.UTF_8).strip();
                    Files.delete(DONATION_FILE.toPath());
                    if (bl) {
                        LumiBridge.setBehavior((String)BUNDLED_SET, (String)LumiBridge.donationJumpBehaviorName());
                        if (!((String)object).isEmpty()) {
                            this.handle((String)object);
                        }
                    }
                }
                if (HOUSE_FILE.isFile()) {
                    object = Files.readString(HOUSE_FILE.toPath(), StandardCharsets.UTF_8);
                    Files.delete(HOUSE_FILE.toPath());
                    if (bl) {
                        Features.call((String)"com.group_finity.mascot.lumi.HouseWindows", (String)"command", (Object[])new Object[]{object});
                    }
                }
                if (BACKDROP_FILE.isFile()) {
                    object = Files.readString(BACKDROP_FILE.toPath(), StandardCharsets.UTF_8);
                    Files.delete(BACKDROP_FILE.toPath());
                    if (bl) {
                        Features.call((String)"com.group_finity.mascot.lumi.BackdropWindow", (String)"command", (Object[])new Object[]{object});
                    }
                }
                if (FENCE_FILE.isFile()) {
                    object = Files.readString(FENCE_FILE.toPath(), StandardCharsets.UTF_8);
                    Files.delete(FENCE_FILE.toPath());
                    Features.call((String)"com.group_finity.mascot.lumi.MascotFence", (String)"command", (Object[])new Object[]{object});
                }
                if (QUERY_FILE.isFile()) {
                    Files.delete(QUERY_FILE.toPath());
                    Files.writeString(REPORT_FILE.toPath(), (CharSequence)LumiBridge.environmentReport(), StandardCharsets.UTF_8, new OpenOption[0]);
                }
            }
            catch (Exception exception) {
                log.log(Level.WARNING, "Speech command handling failed", exception);
            }
            try {
                LumiBridge.rescueStuck();
            }
            catch (Exception exception) {
                log.log(Level.WARNING, "\uac07\ud798 \ucc28\ub2e8\uae30\uac00 \uc2e4\ud328\ud588\ub2e4", exception);
            }
            try {
                Thread.sleep(250L);
            }
            catch (InterruptedException interruptedException) {
                return;
            }
        }
    }

    public static File lineFile(String string) {
        File file = new File("conf", string + "_" + SpeechDirector.languageTag() + ".txt");
        return file.isFile() ? file : new File("conf", string + "_en.txt");
    }

    static String languageTag() {
        Locale locale;
        try {
            locale = Main.getInstance().getSettings().language;
        }
        catch (Exception | LinkageError throwable) {
            return "en";
        }
        if (locale == null) {
            return "en";
        }
        String string = locale.getLanguage();
        if ("zh".equals(string)) {
            return "Hant".equals(locale.getScript()) || "TW".equals(locale.getCountry()) || "HK".equals(locale.getCountry()) || "MO".equals(locale.getCountry()) ? "zh_TW" : "zh";
        }
        return switch (string) {
            case "ko", "ja" -> string;
            default -> "en";
        };
    }

    private void handle(String string) {
        String string2 = string.strip();
        long l = -1L;
        if (string2.startsWith("@")) {
            int n = string2.indexOf(10);
            String string3 = n < 0 ? string2.substring(1) : string2.substring(1, n);
            try {
                l = Long.parseLong(string3.trim());
                string2 = n < 0 ? "" : string2.substring(n + 1).strip();
            }
            catch (NumberFormatException numberFormatException) {
                // empty catch block
            }
        }
        if (string2.isEmpty()) {
            return;
        }
        this.show(null, string2, l);
        if (Features.aiVoiceUnlocked()) {
            Features.call((String)"com.group_finity.mascot.lumi.ai.ChatService", (String)"onExternalLine", (Object[])new Object[]{string2});
        }
    }

    public static String localized(String string, String string2) {
        try {
            ResourceBundle resourceBundle = Main.getInstance().getLanguageBundle();
            if (resourceBundle != null && resourceBundle.containsKey(string)) {
                return resourceBundle.getString(string);
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return string2;
    }

    private static synchronized String greeting(String string) {
        List<String> list = SpeechDirector.greetingsFor(string);
        if (list.isEmpty()) {
            return null;
        }
        if (list.size() > 1 && lastGreeting != null) {
            ArrayList<String> arrayList = new ArrayList<String>(list);
            arrayList.remove(lastGreeting);
            if (!arrayList.isEmpty()) {
                list = arrayList;
            }
        }
        lastGreeting = list.get(ThreadLocalRandom.current().nextInt(list.size()));
        return lastGreeting;
    }

    private static List<String> greetingsFor(String string) {
        if (string == null || string.isBlank() || BUNDLED_SET.equalsIgnoreCase(string)) {
            return SpeechDirector.bundleGreetings();
        }
        List<String> list = SpeechDirector.ownGreetings(string);
        return list.isEmpty() ? SpeechDirector.sharedGreetings() : list;
    }

    private static List<String> bundleGreetings() {
        SpeechDirector.refreshGreetingPools();
        return greetingPool;
    }

    private static List<String> sharedGreetings() {
        SpeechDirector.refreshGreetingPools();
        return sharedGreetingPool;
    }

    private static void refreshGreetingPools() {
        String string = SpeechDirector.languageTag();
        if (greetingPool == null || !string.equals(greetingPoolLanguage)) {
            greetingPool = SpeechDirector.collectGreetings(8);
            sharedGreetingPool = SpeechDirector.collectSharedGreetings();
            greetingPoolLanguage = string;
        }
    }

    private static List<String> ownGreetings(String string) {
        File file = new File(new File(IMG_DIR, string), GREETINGS_FILE);
        if (!file.isFile()) {
            return List.of();
        }
        ArrayList<String> arrayList = new ArrayList<String>();
        try {
            for (String string2 : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                String string3 = string2.strip();
                if (string3.isEmpty() || string3.startsWith("#")) continue;
                arrayList.add(string3);
            }
        }
        catch (Exception exception) {
            log.log(Level.WARNING, "Failed to read greetings " + String.valueOf(file), exception);
            return List.of();
        }
        return arrayList;
    }

    private static List<String> nameFree(List<String> list) {
        String string = SpeechDirector.localized(BUNDLED_SET, BUNDLED_SET);
        if (string.isBlank()) {
            string = BUNDLED_SET;
        }
        ArrayList<String> arrayList = new ArrayList<String>();
        for (String string2 : list) {
            if (string2.contains(string) || string2.contains(BUNDLED_SET)) continue;
            arrayList.add(string2);
        }
        return arrayList;
    }

    private static List<String> collectGreetings(int n) {
        ArrayList<String> arrayList = new ArrayList<String>();
        arrayList.add(SpeechDirector.localized(GREETING_KEY, GREETING_FALLBACK));
        for (int i = 2; i <= n; ++i) {
            String string = SpeechDirector.localized(GREETING_KEY + i, "");
            if (string.isBlank()) continue;
            arrayList.add(string);
        }
        return arrayList;
    }

    private static List<String> collectSharedGreetings() {
        ArrayList<String> arrayList = new ArrayList<String>();
        for (int i = 1; i <= 8; ++i) {
            String string = SpeechDirector.localized(GREETING_ANY_KEY + i, "");
            if (string.isBlank()) continue;
            arrayList.add(string);
        }
        return arrayList.isEmpty() ? SpeechDirector.nameFree(SpeechDirector.collectGreetings(11)) : SpeechDirector.nameFree(arrayList);
    }

    private void installClickGreeter() {
        if (Features.aiChatUnlocked() && Features.present((String)"com.group_finity.mascot.lumi.ai.ChatService")) {
            return;
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(aWTEvent -> {
            MouseEvent mouseEvent;
            if (!(aWTEvent instanceof MouseEvent) || (mouseEvent = (MouseEvent)aWTEvent).getButton() != 1) {
                return;
            }
            try {
                if (mouseEvent.getID() == 501) {
                    Point point = mouseEvent.getLocationOnScreen();
                    LumiBridge.MascotSnapshot mascotSnapshot = LumiBridge.mascotAt((Point)point);
                    this.clickPressAt = System.currentTimeMillis();
                    this.clickPressPoint = mascotSnapshot == null ? null : point;
                    this.clickPressSet = mascotSnapshot == null ? null : mascotSnapshot.imageSet;
                } else if (mouseEvent.getID() == 502 && this.clickPressPoint != null) {
                    Point point = mouseEvent.getLocationOnScreen();
                    boolean bl = System.currentTimeMillis() - this.clickPressAt <= 500L && this.clickPressPoint.distance(point) <= 12.0;
                    String string = this.clickPressSet;
                    this.clickPressPoint = null;
                    this.clickPressSet = null;
                    if (bl && !SpeechDirector.isBubbleVisible()) {
                        String string2 = string == null ? "" : string;
                        long l = System.currentTimeMillis();
                        Long l2 = this.lastClickGreetAt.get(string2);
                        if (l2 != null && l - l2 < 45000L) {
                            SpeechDirector.selfTalkInstead(string);
                        } else {
                            String string3 = SpeechDirector.greeting(string);
                            if (string3 != null) {
                                this.lastClickGreetAt.put(string2, l);
                                SpeechDirector.say(string, string3, 3200L);
                            }
                        }
                    }
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
        }, 16L);
    }

    private static void selfTalkInstead(String string) {
        Integer n;
        if (string != null && !string.isBlank() && !BUNDLED_SET.equalsIgnoreCase(string)) {
            return;
        }
        if (FocusDirector.quietNow()) {
            return;
        }
        if (Boolean.TRUE.equals(Features.call((String)"com.group_finity.mascot.lumi.SelfTalkDirector", (String)"isSpeaking", (Object[])new Object[0]))) {
            return;
        }
        Object object = Features.call((String)"com.group_finity.mascot.lumi.SelfTalkDirector", (String)"frequency", (Object[])new Object[0]);
        if (object instanceof Integer && (n = (Integer)object) == 0) {
            return;
        }
        LumiBridge.setBehavior((String)string, (String)"SelfTalk");
    }

    private void greetOnChatter() {
        if (FocusDirector.quietNow()) {
            return;
        }
        if (++this.greetCounter < 4) {
            return;
        }
        this.greetCounter = 0;
        for (String string : LumiBridge.liveImageSets()) {
            String string2;
            String string3 = LumiBridge.currentBehaviorName((String)string);
            String string4 = this.lastBehavior.put(string, string3);
            if (!"Chatter".equals(string3) || "Chatter".equals(string4) || SpeechDirector.isBubbleVisible() || (string2 = SpeechDirector.greeting(string)) == null) continue;
            SpeechDirector.say(string, string2, 3200L);
            MouthFlap.flapFor((String)string, (long)3200L);
        }
    }

    public static void say(String string, long l) {
        SpeechDirector.say(null, string, l);
    }

    public static void say(String string, String string2, long l) {
        SpeechDirector speechDirector;
        List<String> list = spokenForTest;
        if (list != null && string2 != null && !string2.isBlank()) {
            list.add(string2.strip());
        }
        if ((speechDirector = instance) != null && string2 != null && !string2.isBlank()) {
            speechDirector.show(0, string, string2.strip(), l, false);
        }
    }

    public static void sayPrivate(String string, long l) {
        SpeechDirector speechDirector;
        List<String> list = spokenForTest;
        if (list != null && string != null && !string.isBlank()) {
            list.add(string.strip());
        }
        if ((speechDirector = instance) != null && string != null && !string.isBlank()) {
            speechDirector.show(0, null, string.strip(), l, true);
        }
    }

    public static void sayFor(int n, String string, String string2, long l) {
        SpeechDirector speechDirector = instance;
        if (speechDirector != null && string2 != null && !string2.isBlank()) {
            speechDirector.show(n, string, string2.strip(), l);
        }
    }

    public static void showBusy() {
        SpeechDirector.showBusy(null);
    }

    public static void showBusy(String string) {
        SpeechDirector.showBusy(0, string);
    }

    public static void showBusy(int n, String string) {
        SpeechDirector speechDirector = instance;
        if (speechDirector != null) {
            speechDirector.show(n, string, "\u00b7\u00b7\u00b7", 90000L);
        }
    }

    public static void hideBubble() {
        SpeechDirector speechDirector = instance;
        if (speechDirector != null) {
            SwingUtilities.invokeLater(() -> {
                if (speechDirector.bubble != null) {
                    speechDirector.bubble.setVisible(false);
                }
            });
        }
    }

    public static boolean isBubbleVisible() {
        SpeechDirector speechDirector = instance;
        return speechDirector != null && speechDirector.bubble != null && speechDirector.bubble.isVisible();
    }

    private void show(String string, String string2, long l) {
        this.show(0, string, string2, l);
    }

    private void show(int n, String string, String string2, long l) {
        this.show(n, string, string2, l, false);
    }

    private void show(int n, String string, String string2, long l, boolean bl) {
        long l2 = l > 0L ? l : Math.max(6000L, Math.min(60000L, 4000L + (long)string2.length() * 110L));
        this.speakerId = n;
        this.speakerSet = string;
        SwingUtilities.invokeLater(() -> {
            if (this.bubble == null) {
                this.bubble = new SpeechBubbleWindow();
            }
            this.bubble.setPrivateLine(bl);
            this.bubble.setText(string2);
            this.hideAt = System.currentTimeMillis() + l2;
            this.place();
            this.bubble.showFor(l2);
        });
    }

    private void beacon() {
        Object object;
        if (++this.beaconCounter < 40) {
            return;
        }
        this.beaconCounter = 0;
        LumiBridge.MascotSnapshot mascotSnapshot = LumiBridge.firstMascot();
        if (mascotSnapshot == null) {
            return;
        }
        try {
            object = mascotSnapshot.anchor.x + "," + mascotSnapshot.anchor.y + "\n";
            if (!((String)object).equals(this.lastPositionBeacon)) {
                Files.writeString(POSITION_FILE.toPath(), (CharSequence)object, StandardCharsets.UTF_8, new OpenOption[0]);
                this.lastPositionBeacon = (String)object;
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        try {
            String string;
            object = Features.call((String)"com.group_finity.mascot.LumiBridge", (String)"allMascotsBeacon", (Object[])new Object[0]);
            if (object != null && !(string = object.toString()).equals(this.lastAllPositionsBeacon)) {
                Files.writeString(ALL_POSITIONS_FILE.toPath(), (CharSequence)string, StandardCharsets.UTF_8, new OpenOption[0]);
                this.lastAllPositionsBeacon = string;
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    public static int headClearance(Rectangle rectangle) {
        return rectangle == null ? 140 : Math.round((float)rectangle.height * 0.7291667f);
    }

    private void follow() {
        if (this.bubble == null || !this.bubble.isVisible()) {
            return;
        }
        if (System.currentTimeMillis() > this.hideAt) {
            this.bubble.setVisible(false);
            return;
        }
        if (!this.place()) {
            this.bubble.setVisible(false);
        }
    }

    private boolean place() {
        LumiBridge.MascotSnapshot mascotSnapshot;
        LumiBridge.MascotSnapshot mascotSnapshot2 = mascotSnapshot = this.speakerId != 0 ? LumiBridge.mascotById((int)this.speakerId) : null;
        if (mascotSnapshot == null) {
            LumiBridge.MascotSnapshot mascotSnapshot3 = mascotSnapshot = this.speakerSet == null ? LumiBridge.firstMascot() : LumiBridge.mascotOf((String)this.speakerSet);
        }
        if (mascotSnapshot == null) {
            return false;
        }
        int n = mascotSnapshot.anchor.x - this.bubble.getWidth() / 2;
        int n2 = mascotSnapshot.anchor.y - SpeechDirector.headClearance(mascotSnapshot.bounds) - this.bubble.getHeight();
        Rectangle rectangle = SpeechDirector.screenBoundsFor(mascotSnapshot.anchor);
        n = Math.max(rectangle.x + 4, Math.min(n, rectangle.x + rectangle.width - this.bubble.getWidth() - 4));
        n2 = Math.max(rectangle.y + 4, n2);
        this.bubble.setLocation(n, n2);
        return true;
    }

    private static Rectangle screenBoundsFor(Point point) {
        GraphicsEnvironment graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        ArrayList<Rectangle> arrayList = new ArrayList<Rectangle>();
        for (GraphicsDevice graphicsDevice : graphicsEnvironment.getScreenDevices()) {
            arrayList.add(graphicsDevice.getDefaultConfiguration().getBounds());
        }
        Rectangle rectangle = SpeechDirector.screenBoundsFor(point, arrayList);
        return rectangle != null ? rectangle : graphicsEnvironment.getMaximumWindowBounds();
    }

    static Rectangle screenBoundsFor(Point point, List<Rectangle> list) {
        Rectangle rectangle = null;
        double d = Double.MAX_VALUE;
        for (Rectangle rectangle2 : list) {
            double d2;
            if (rectangle2.contains(point)) {
                return rectangle2;
            }
            double d3 = rectangle2.getCenterX() - (double)point.x;
            double d4 = d3 * d3 + (d2 = rectangle2.getCenterY() - (double)point.y) * d2;
            if (!(d4 < d)) continue;
            d = d4;
            rectangle = rectangle2;
        }
        return rectangle;
    }
}

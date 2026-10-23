package com.swift.radiohome;

import java.io.IOException;

/**
 * The source the head unit resumes when the car starts. ADAYO's midware service saves the last
 * system source (FM, Bluetooth, USB...) and AppSwitcher.resumeSavedSources() starts it again at
 * boot. RadioTube plays outside that source manager, so after using the FM once, every start
 * played FM static.
 *
 * The quiet start is Bluetooth audio, NOT the midware's "NULL" (no source): Android apps sound
 * through the APP channel, which the midware only MIXES over the active front source
 * (MediaChannelsUpdate.playMPUAudio). With no front source nothing was heard (tested 22/10).
 *
 * The midware keeps a copy in memory and only reads the database at boot, which is every time the
 * car starts on this unit, so a change here takes effect on the next start. Never "" (that means
 * "use the default", which is FM) and never an invented name (MEDIA_SOURCE_ID.valueOf throws).
 */
final class StartSource {

    private static final String DB = "/data/data/com.adayo.midware.service/databases/params.db";
    private static final String QUIET = "BTAUDIO";

    private StartSource() {
    }

    /**
     * The noisy source the next car start will open (FM, AM, an empty default...), or null if it
     * is quiet (Bluetooth) or can't be read.
     */
    static String read() {
        String s;
        try {
            s = LocalAdb.shell("sqlite3 " + DB
                    + " \"select front_real_source from SourceBean where cid=1;\"").trim();
        } catch (IOException e) {
            return null;
        }
        if (s.contains(" ") || s.startsWith("BT")) return null; // an error message, or already quiet
        return s.length() == 0 || "NULL".equals(s) ? "FM" : s; // "" and NULL: FM, or no sound at all
    }

    /** The next car start resumes Bluetooth audio: quiet, and RadioTube can sound. */
    static boolean clear() {
        try {
            LocalAdb.shell("sqlite3 " + DB
                    + " \"update SourceBean set front_real_source='" + QUIET + "' where cid=1;\"");
        } catch (IOException e) {
            return false;
        }
        return read() == null;
    }

    /** "FM" → "FM", "BTAUDIO" → "Bluetooth"... for the chip. */
    static String label(String source) {
        if (source.startsWith("USB")) return "USB";
        if (source.startsWith("AUX")) return "AUX";
        return source;
    }
}

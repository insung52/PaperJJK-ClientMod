package com.justheare.paperjjk_client;

/**
 * Global debug configuration for PaperJJK Client
 * Set DEBUG_ENABLED to true to enable verbose logging
 */
public class DebugConfig {
    /**
     * Master debug flag - set to true to enable all debug logging
     * Set to false to disable all debug output
     */
    public static final boolean DEBUG_ENABLED = true;

    /**
     * Debug log helper - only prints if DEBUG_ENABLED is true
     */
    public static void log(String message) {
        if (DEBUG_ENABLED) {
            System.out.println("[PaperJJK-DEBUG] " + message);
        }
    }

    /**
     * Debug log helper with custom tag
     */
    public static void log(String tag, String message) {
        if (DEBUG_ENABLED) {
            System.out.println("[PaperJJK-DEBUG/" + tag + "] " + message);
        }
    }
}

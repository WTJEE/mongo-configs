package xyz.wtje.mongoconfigs.api.event;

/**
 * 
 * Listener for language update events.
 * This is a platform-agnostic listener that works on both Paper and Velocity.
 * Register via
 * {@link xyz.wtje.mongoconfigs.api.LanguageManager#registerListener(LanguageUpdateListener)}.
 */
@FunctionalInterface
public interface LanguageUpdateListener {

    /**
     * Called when a player's language is updated.
     * 
     * @param playerId    The UUID of the player (as string)
     * @param oldLanguage The previous language (may be null if not cached)
     * @param newLanguage The new language
     */
    void onLanguageUpdate(String playerId, String oldLanguage, String newLanguage);
}

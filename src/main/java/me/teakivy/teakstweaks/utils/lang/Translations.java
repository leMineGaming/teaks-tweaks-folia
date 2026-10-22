package me.teakivy.teakstweaks.utils.lang;

import me.teakivy.teakstweaks.utils.config.Config;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;

/**
 * Resolves the plugin's translations on the server.
 * <p>
 * Custom translation keys are not known to the client, so translatable components have to be
 * resolved before they are sent. This utility always returns a fully resolved component.
 */
public final class Translations {

    private Translations() {
    }

    public static Component get(String key, ComponentLike... args) {
        return OldTranslationManager.getComponent(Config.getLanguage(), key, args);
    }
}

package me.teakivy.teakstweaks.utils;

import me.teakivy.teakstweaks.utils.lang.Translations;
import net.kyori.adventure.text.Component;

public enum ErrorType {
    UNKNOWN_ERROR("error.unknown"),
    PACK_NOT_ENABLED("error.pack_not_enabled"),
    NOT_PLAYER("error.not_player"),
    NOT_OP("error.not_op"),
    MISSING_COMMAND_PERMISSION("error.missing_command_permission"),
    MISSING_PERMISSION("error.missing_permission"),
    CANT_GET_LATEST("error.cant_get_latest_version"),
    COMMAND_DISABLED("error.command_disabled"),
    MISSING_ACTION("error.missing_action"),
    PLAYER_DNE("error.player_dne");

    private final String key;

    ErrorType(String key) {
        this.key = key;
    }

    /**
     * Gets the error message
     * @return The error message
     */
    public Component m() {
        return Translations.get(key);
    }

    /**
     * Converts the error message key to a string
     * @return The translation key
     */
    @Override
    public String toString() {
        return key;
    }
}

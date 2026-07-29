package org.wildeprojekt.modpacksettings.i18n;

import lombok.extern.slf4j.Slf4j;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Resolves user-facing messages from the bundled ResourceBundle.
 */
@Slf4j
public final class Messages {

    /** Base name of the bundled messages ResourceBundle. */
    private static final String BUNDLE = "assets.modpacksettings.lang.messages";

    /** Lazily initialized ResourceBundle for the user's default locale. */
    private static ResourceBundle bundle;

    /** Private constructor for this utility class. */
    private Messages() {
    }

    /**
     * Returns a localized message for the supplied key.
     *
     * @param key  message key to resolve
     * @param args optional MessageFormat arguments
     * @return resolved message, or the key itself when the bundle entry is missing
     */
    public static String get(String key, Object... args) {

        try {

            String value = bundle().getString(key);
            return args.length == 0 ? value : MessageFormat.format(value, args);

        } catch (MissingResourceException exception) {

            LOGGER.warn("Missing Modpack Settings message key {}.", key, exception);
            return key;
        }
    }

    /**
     * Returns the loaded ResourceBundle, resolving it on first use.
     *
     * @return messages ResourceBundle
     */
    private static ResourceBundle bundle() {

        if (bundle == null)
            bundle = ResourceBundle.getBundle(BUNDLE, Locale.getDefault(), Messages.class.getClassLoader());

        return bundle;
    }
}

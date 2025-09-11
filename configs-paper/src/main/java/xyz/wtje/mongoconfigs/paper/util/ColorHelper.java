package xyz.wtje.mongoconfigs.paper.util;

import net.kyori.adventure.text.Component;

public final class ColorHelper {

    private ColorHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String colorize(String message) {
        return ColorUtils.colorize(message);
    }

    public static String stripColors(String message) {
        return ColorUtils.stripColors(message);
    }

    public static Component parseComponent(String message) {
        return ColorUtils.parseComponent(message);
    }
}

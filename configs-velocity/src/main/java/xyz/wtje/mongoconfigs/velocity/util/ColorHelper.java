package xyz.wtje.mongoconfigs.velocity.util;

import net.kyori.adventure.text.Component;

public final class ColorHelper {
    private ColorHelper() {}

    public static Component parseComponent(String message) {
        return ColorUtils.parseComponent(message);
    }
}

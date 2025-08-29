package xyz.wtje.mongoconfigs.paper.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ChatColor;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtils {

    private static final Cache<String, String> COLOR_CACHE = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .recordStats()
            .build();

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
            LegacyComponentSerializer.legacySection();

    private ColorUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String colorize(String message) {
        if (message == null || message.isEmpty()) return message;
        String cached = COLOR_CACHE.getIfPresent(message);
        if (cached != null) return cached;
        String result = processColors(message);
        COLOR_CACHE.put(message, result);
        return result;
    }

    private static String processColors(String input) {
        if (input == null || input.isEmpty()) return input;

        String preprocessed = preprocessToMiniMessage(input);
        try {
            Component component = MINI_MESSAGE.deserialize(preprocessed);
            return LEGACY_SERIALIZER.serialize(component);
        } catch (Exception e) {
            String sanitized = removeAngleBrackets(preprocessed);
            try {
                Component component = MINI_MESSAGE.deserialize(sanitized);
                return LEGACY_SERIALIZER.serialize(component);
            } catch (Exception ignored) {
                return ChatColor.translateAlternateColorCodes('&', input);
            }
        }
    }


    public static String stripColors(String message) {
        if (message == null || message.isEmpty()) return message;
        try {
            String processed = preprocessToMiniMessage(message);
            Component component = MINI_MESSAGE.deserialize(processed);
            return PlainTextComponentSerializer.plainText().serialize(component);
        } catch (Exception e) {
            String sanitized = removeAngleBrackets(message);
            return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', sanitized));
        }
    }

    public static Component parseComponent(String message) {
        if (message == null || message.isEmpty()) return Component.empty();
        try {
            String processed = preprocessToMiniMessage(message);
            return MINI_MESSAGE.deserialize(processed);
        } catch (Exception e) {
            String sanitized = removeAngleBrackets(message);
            return Component.text(ChatColor.translateAlternateColorCodes('&', sanitized));
        }
    }

    public static String componentToLegacy(Component component) {
        return LEGACY_SERIALIZER.serialize(component);
    }

    public static com.github.benmanes.caffeine.cache.stats.CacheStats getCacheStats() {
        return COLOR_CACHE.stats();
    }

    public static void clearCache() {
        COLOR_CACHE.invalidateAll();
    }

    public static long getCacheSize() {
        return COLOR_CACHE.estimatedSize();
    }

    private static final Pattern RGB_FUNC = Pattern.compile("&\\{(\\d{1,3}),(\\d{1,3}),(\\d{1,3})}");


    private static final Pattern AMP_HEX = Pattern.compile("(?i)&#([0-9a-f]{6})");


    private static final Pattern BUKKIT_HEX = Pattern.compile(
        "(?i)&x(?:&([0-9a-f]))(?:&([0-9a-f]))(?:&([0-9a-f]))(?:&([0-9a-f]))(?:&([0-9a-f]))(?:&([0-9a-f]))"
    );


    private static final Pattern GRADIENT_ALIAS = Pattern.compile("(?i)gradient:?((?:#[0-9a-f]{6})(?::#[0-9a-f]{6})+)");

    private static String preprocessToMiniMessage(String input) {
        if (input == null || input.isEmpty()) return input;

    String out = input;


        out = out.replace('§', '&');


        out = expandGradientAliases(out);


        {
            Matcher m = BUKKIT_HEX.matcher(out);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                int groups = m.groupCount();
                StringBuilder hex = new StringBuilder(6);
                for (int i = 1; i <= groups; i++) {
                    String g = null;
                    try { g = m.group(i); } catch (IndexOutOfBoundsException ignored) { }
                    if (g == null) { hex.setLength(0); break; }
                    hex.append(g);
                }
                if (hex.length() == 6) {
                    m.appendReplacement(sb, Matcher.quoteReplacement("<#" + hex.toString().toLowerCase() + ">"));
                } else {
                    m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                }
            }
            m.appendTail(sb);
            out = sb.toString();
        }


        out = AMP_HEX.matcher(out).replaceAll("<#$1>");


        {
            Matcher m = RGB_FUNC.matcher(out);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                int r = clamp255(parseIntSafe(m.group(1)));
                int g = clamp255(parseIntSafe(m.group(2)));
                int b = clamp255(parseIntSafe(m.group(3)));
                String hex = String.format("<#%02x%02x%02x>", r, g, b);
                m.appendReplacement(sb, Matcher.quoteReplacement(hex));
            }
            m.appendTail(sb);
            out = sb.toString();
        }


        out = out.replace("&l", "<bold>")
                .replace("&o", "<italic>")
                .replace("&n", "<underlined>")
                .replace("&m", "<strikethrough>")
                .replace("&k", "<obfuscated>")
                .replace("&r", "<reset>");


        out = replaceLegacyColorCodes(out);


        out = out.replace("<>", "").replace("< >", " ");

        return out;
    }


    private static String expandGradientAliases(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder out = new StringBuilder(s.length() + 32);
        int i = 0;
        while (i < s.length()) {
            char ch = s.charAt(i);

            if (ch == '<') {
                int close = s.indexOf('>', i + 1);
                if (close == -1) {
                    out.append(s.substring(i));
                    break;
                }
                out.append(s, i, close + 1);
                i = close + 1;
                continue;
            }


            if (regionMatchesIgnoreCase(s, i, "gradient")) {
                int pos = i + 8;
                if (pos < s.length() && s.charAt(pos) == ':') pos++;

                if (pos < s.length() && s.charAt(pos) == '#') {
                    int j = pos;
                    int k = j;
                    boolean ok = true;
                    while (true) {
                        if (k + 7 > s.length()) { ok = false; break; }
                        if (!isHex6(s, k + 1)) { ok = false; break; }
                        k += 7;
                        if (k < s.length() && s.charAt(k) == ':') {
                            k++;
                            if (k >= s.length() || s.charAt(k) != '#') { ok = false; break; }
                            continue;
                        }
                        break;
                    }
                    if (ok) {
                        String stops = s.substring(j, k).toLowerCase();

                        int next = indexOfIgnoreCase(s, "gradient", k);
                        int end = (next == -1 ? s.length() : next);
                        String content = s.substring(k, end);
                        out.append("<gradient:").append(stops).append(">");
                        out.append(content);
                        out.append("</gradient>");
                        i = end;
                        continue;
                    }
                }
            }

            out.append(ch);
            i++;
        }
        return out.toString();
    }

    private static boolean regionMatchesIgnoreCase(String s, int offset, String needle) {
        int n = needle.length();
        if (offset + n > s.length()) return false;
        for (int k = 0; k < n; k++) {
            char a = s.charAt(offset + k);
            char b = needle.charAt(k);
            if (Character.toLowerCase(a) != Character.toLowerCase(b)) return false;
        }
        return true;
    }

    private static int indexOfIgnoreCase(String s, String needle, int fromIndex) {
        int n = needle.length();
        for (int i = Math.max(0, fromIndex); i + n <= s.length(); i++) {
            if (regionMatchesIgnoreCase(s, i, needle)) return i;
        }
        return -1;
    }

    private static boolean isHex6(String s, int startExclusiveHash) {
        int end = startExclusiveHash + 6;
        if (end > s.length()) return false;
        for (int i = startExclusiveHash; i < end; i++) {
            char c = s.charAt(i);
            boolean hex = (c >= '0' && c <= '9') ||
                          (c >= 'a' && c <= 'f') ||
                          (c >= 'A' && c <= 'F');
            if (!hex) return false;
        }
        return true;
    }

    private static String removeAngleBrackets(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.replace("<>", "").replace("< >", " ").replace("<", "").replace(">", "");
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }
    private static int clamp255(int v) { return Math.max(0, Math.min(255, v)); }

    private static String replaceLegacyColorCodes(String in) {
        StringBuilder out = new StringBuilder(in.length() * 2);
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            if (c == '&' && i + 1 < in.length()) {
                char code = Character.toLowerCase(in.charAt(i + 1));
                String tag = switch (code) {
                    case '0' -> "<black>";
                    case '1' -> "<dark_blue>";
                    case '2' -> "<dark_green>";
                    case '3' -> "<dark_aqua>";
                    case '4' -> "<dark_red>";
                    case '5' -> "<dark_purple>";
                    case '6' -> "<gold>";
                    case '7' -> "<gray>";
                    case '8' -> "<dark_gray>";
                    case '9' -> "<blue>";
                    case 'a' -> "<green>";
                    case 'b' -> "<aqua>";
                    case 'c' -> "<red>";
                    case 'd' -> "<light_purple>";
                    case 'e' -> "<yellow>";
                    case 'f' -> "<white>";
                    default -> null;
                };
                if (tag != null) {
                    out.append(tag);
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }
}

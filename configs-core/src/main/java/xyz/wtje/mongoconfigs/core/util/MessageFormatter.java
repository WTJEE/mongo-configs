package xyz.wtje.mongoconfigs.core.util;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageFormatter {

    private static final Logger LOGGER = Logger.getLogger(MessageFormatter.class.getName());
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)\\}");
    private ColorProcessor colorProcessor = new NoOpColorProcessor();

    public void setColorProcessor(ColorProcessor colorProcessor) {
        this.colorProcessor = colorProcessor != null ? colorProcessor : new NoOpColorProcessor();
    }

    public String format(String message, Object... placeholders) {
        if (message == null) {
            return message;
        }

        String formatted = message;
        if (placeholders.length > 0) {
            if (message.contains("{")) {
                formatted = replacePlaceholders(message, placeholders);
            }
        }

        return colorProcessor.colorize(formatted);
    }


    private String replacePlaceholders(String message, Object... placeholders) {
        if (placeholders == null || placeholders.length == 0) {
            return message;
        }

        Object[] pairs = deriveNamedPairs(message, placeholders);
        if (pairs == null) {
            pairs = derivePositionalPairs(message, placeholders);
            if (pairs.length == 0) {
                return message;
            }
        }

        StringBuilder result = new StringBuilder(message.length() + 64);
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(message);
        int lastEnd = 0;

        while (matcher.find()) {
            result.append(message, lastEnd, matcher.start());

            String key = matcher.group(1);
            String replacement = findReplacement(key, pairs);

            if (replacement != null) {
                result.append(replacement);
            } else {
                result.append(matcher.group(0));
            }

            lastEnd = matcher.end();
        }

        result.append(message, lastEnd, message.length());

        return result.toString();
    }

    private Object[] deriveNamedPairs(String message, Object[] placeholders) {
        if (placeholders.length < 2) {
            return null;
        }

        int usableLength = (placeholders.length / 2) * 2;
        if (usableLength == 0) {
            return null;
        }

        for (int i = 0; i < usableLength; i += 2) {
            if (!matchesPlaceholder(message, placeholders[i])) {
                return null;
            }
        }

        if (placeholders.length % 2 != 0) {
            Object trailing = placeholders[placeholders.length - 1];
            LOGGER.log(Level.WARNING, "Odd number of placeholder arguments for message "{0}"; ignoring trailing value "{1}".", new Object[]{message, trailing});
        }

        if (usableLength == placeholders.length) {
            return placeholders;
        }

        return Arrays.copyOf(placeholders, usableLength);
    }

    private Object[] derivePositionalPairs(String message, Object[] values) {
        String[] keys = extractPlaceholders(message);
        if (keys.length == 0) {
            LOGGER.log(Level.FINE, "Received placeholder values for message "{0}" but it defines no placeholders.", message);
            return new Object[0];
        }

        int pairCount = Math.min(keys.length, values.length);
        Object[] pairs = new Object[pairCount * 2];
        for (int i = 0; i < pairCount; i++) {
            pairs[i * 2] = keys[i];
            pairs[i * 2 + 1] = values[i];
        }

        if (values.length < keys.length) {
            LOGGER.log(Level.WARNING, "Not enough placeholder values for message "{0}"; expected {1}, received {2}.", new Object[]{message, keys.length, values.length});
        } else if (values.length > keys.length) {
            LOGGER.log(Level.WARNING, "Too many placeholder values provided for message "{0}"; expected {1}, received {2}.", new Object[]{message, keys.length, values.length});
        }

        return pairs;
    }

    private boolean matchesPlaceholder(String message, Object key) {
        if (key == null) {
            return false;
        }

        String token = "{" + key + "}";
        if (message.contains(token)) {
            return true;
        }

        if (key instanceof Enum<?> enumKey) {
            String lowerToken = "{" + enumKey.name().toLowerCase() + "}";
            return message.contains(lowerToken);
        }

        return false;
    }

    public String format(String message, String key, Object value) {
        if (message == null) {
            return message;
        }

        String formatted = message;
        if (key != null && value != null) {
            String placeholder = "{" + key + "}";
            if (message.contains(placeholder)) {
                formatted = message.replace(placeholder, value.toString());
            }
        }

        return colorProcessor.colorize(formatted);
    }

    public String formatPlain(String message, Object... placeholders) {
        if (message == null) {
            return message;
        }

        if (placeholders.length > 0 && message.contains("{")) {
            return replacePlaceholders(message, placeholders);
        }

        return message;
    }

    public String[] formatBatch(String[] messages, Object... placeholders) {
        if (messages == null || messages.length == 0) {
            return messages;
        }

        String[] result = new String[messages.length];
        for (int i = 0; i < messages.length; i++) {
            result[i] = format(messages[i], placeholders);
        }

        return result;
    }

    private String findReplacement(String key, Object[] placeholders) {
        for (int i = 0; i < placeholders.length; i += 2) {
            if (key.equals(String.valueOf(placeholders[i]))) {
                Object value = placeholders[i + 1];
                return value != null ? value.toString() : null;
            }
        }
        return null;
    }

    public boolean hasPlaceholders(String message) {
        return message != null && message.contains("{") && PLACEHOLDER_PATTERN.matcher(message).find();
    }

    public String[] extractPlaceholders(String message) {
        if (message == null) {
            return new String[0];
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(message);
        return matcher.results()
                .map(matchResult -> matchResult.group(1))
                .distinct()
                .toArray(String[]::new);
    }
}


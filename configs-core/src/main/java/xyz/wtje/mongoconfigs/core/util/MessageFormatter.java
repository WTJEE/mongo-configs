package xyz.wtje.mongoconfigs.core.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageFormatter {

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
        if (placeholders.length % 2 != 0) {
            throw new IllegalArgumentException("Placeholders must be provided in key-value pairs");
        }

        StringBuilder result = new StringBuilder(message.length() + 64);
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(message);
        int lastEnd = 0;

        while (matcher.find()) {
            result.append(message, lastEnd, matcher.start());

            String key = matcher.group(1);
            String replacement = findReplacement(key, placeholders);

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

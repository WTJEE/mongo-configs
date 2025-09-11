package xyz.wtje.mongoconfigs.core.util;

public class NoOpColorProcessor implements ColorProcessor {

    @Override
    public String colorize(String message) {
        return message;
    }

    @Override
    public String stripColors(String message) {
        return message;
    }

    @Override
    public void clearCache() {
    }
}

package xyz.wtje.mongoconfigs.paper.util;

import xyz.wtje.mongoconfigs.core.util.ColorProcessor;

public class BukkitColorProcessor implements ColorProcessor {
    
    @Override
    public String colorize(String message) {
        return ColorUtils.colorize(message);
    }
    
    @Override
    public String stripColors(String message) {
        return ColorUtils.stripColors(message);
    }
    
    @Override
    public Object getCacheStats() {
        return ColorUtils.getCacheStats();
    }
    
    @Override
    public void clearCache() {
        ColorUtils.clearCache();
    }
    
    @Override
    public long getCacheSize() {
        return ColorUtils.getCacheSize();
    }
}
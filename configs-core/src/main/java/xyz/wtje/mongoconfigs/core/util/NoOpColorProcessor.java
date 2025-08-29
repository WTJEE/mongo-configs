package xyz.wtje.mongoconfigs.core.util;


public class NoOpColorProcessor implements ColorProcessor {
    
    @Override
    public String colorize(String message) {
        return message; // No color processing
    }
    
    @Override
    public String stripColors(String message) {
        return message; // No colors to strip
    }
    
    @Override
    public Object getCacheStats() {
        return "No color processing available";
    }
    
    @Override
    public void clearCache() {
    }
    
    @Override
    public long getCacheSize() {
        return 0;
    }
}
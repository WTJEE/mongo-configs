# Economy System Example

Advanced economy system implementation with multiple currencies, exchange rates, banking, and transaction analytics using MongoDB Configs API.

## 📋 Overview

This example demonstrates a sophisticated economy system featuring:
- Multiple currencies with exchange rates
- Banking system with interest and loans
- Transaction analytics and reporting
- Admin controls for economy management
- Real-time market data via Change Streams

## 🏗️ Project Structure

```
economy-system/
├── src/main/java/xyz/wtje/economy/
│   ├── EconomyPlugin.java           # Main plugin class
│   ├── commands/                    # Command handlers
│   │   ├── EconomyCommand.java
│   │   ├── BankCommand.java
│   │   └── AdminEconomyCommand.java
│   ├── gui/                         # GUI components
│   │   ├── BankGUI.java
│   │   ├── ExchangeGUI.java
│   │   └── TransactionHistoryGUI.java
│   ├── models/                      # Data models
│   │   ├── Currency.java
│   │   ├── Account.java
│   │   ├── Transaction.java
│   │   ├── ExchangeRate.java
│   │   └── BankLoan.java
│   ├── managers/                    # Business logic
│   │   ├── CurrencyManager.java
│   │   ├── AccountManager.java
│   │   ├── ExchangeManager.java
│   │   └── AnalyticsManager.java
│   └── messages/                    # Multilingual messages
│       ├── EconomyMessages.java
│       └── BankMessages.java
└── src/main/resources/
    └── plugin.yml
```

## 🔧 Configuration Classes

### Currency.java

```java
@ConfigsFileProperties
@ConfigsDatabase(database = "economy_system")
@ConfigsCollection(collection = "currencies")
public class Currency {
    
    @ConfigsField
    private String id;  // e.g., "coins", "gems", "tokens"
    
    @ConfigsField
    private String nameKey;  // Message key for display name
    
    @ConfigsField
    private String symbol;  // e.g., "¢", "♦", "★"
    
    @ConfigsField
    private String icon;  // Material name for GUI
    
    @ConfigsField
    private boolean tradeable;  // Can be exchanged
    
    @ConfigsField
    private double defaultBalance;  // Starting balance for new players
    
    @ConfigsField
    private boolean taxable;  // Subject to transaction taxes
    
    @ConfigsField
    private double taxRate;  // Tax percentage (0.0 - 1.0)
    
    // Getters and setters...
}
```

### Account.java

```java
@ConfigsFileProperties
@ConfigsDatabase(database = "economy_system")
@ConfigsCollection(collection = "accounts")
public class Account {
    
    @ConfigsField
    private String playerId;
    
    @ConfigsField
    private Map<String, Double> balances;  // currency_id -> amount
    
    @ConfigsField
    private long createdAt;
    
    @ConfigsField
    private long lastActivity;
    
    @ConfigsField
    private AccountStatus status;  // ACTIVE, FROZEN, SUSPENDED
    
    @ConfigsField
    private List<String> permissions;  // Special permissions
    
    public enum AccountStatus {
        ACTIVE, FROZEN, SUSPENDED
    }
    
    // Getters and setters...
    
    public double getBalance(String currencyId) {
        return balances.getOrDefault(currencyId, 0.0);
    }
    
    public void setBalance(String currencyId, double amount) {
        balances.put(currencyId, amount);
    }
    
    public boolean hasBalance(String currencyId, double amount) {
        return getBalance(currencyId) >= amount;
    }
}
```

### ExchangeRate.java

```java
@ConfigsFileProperties
@ConfigsDatabase(database = "economy_system")
@ConfigsCollection(collection = "exchange_rates")
public class ExchangeRate {
    
    @ConfigsField
    private String id;  // "fromCurrency_toCurrency"
    
    @ConfigsField
    private String fromCurrency;
    
    @ConfigsField
    private String toCurrency;
    
    @ConfigsField
    private double rate;  // 1 fromCurrency = X toCurrency
    
    @ConfigsField
    private double fee;  // Exchange fee percentage
    
    @ConfigsField
    private long lastUpdated;
    
    @ConfigsField
    private boolean active;
    
    // Getters and setters...
    
    public double calculateExchange(double amount) {
        return amount * rate * (1.0 - fee);
    }
    
    public double getReverseRate() {
        return 1.0 / rate;
    }
}
```

### BankLoan.java

```java
@ConfigsFileProperties
@ConfigsDatabase(database = "economy_system")
@ConfigsCollection(collection = "bank_loans")
public class BankLoan {
    
    @ConfigsField
    private String id;
    
    @ConfigsField
    private String playerId;
    
    @ConfigsField
    private String currencyId;
    
    @ConfigsField
    private double principalAmount;
    
    @ConfigsField
    private double interestRate;  // Annual rate
    
    @ConfigsField
    private double outstandingBalance;
    
    @ConfigsField
    private long issuedAt;
    
    @ConfigsField
    private long dueDate;
    
    @ConfigsField
    private LoanStatus status;
    
    @ConfigsField
    private List<Payment> paymentHistory;
    
    public enum LoanStatus {
        ACTIVE, PAID_OFF, DEFAULTED, FORGIVEN
    }
    
    public static class Payment {
        private long timestamp;
        private double amount;
        private String note;
        
        // Getters and setters...
    }
    
    // Getters and setters...
    
    public double calculateInterest(long currentTime) {
        long daysElapsed = (currentTime - issuedAt) / (1000 * 60 * 60 * 24);
        return outstandingBalance * interestRate * daysElapsed / 365.0;
    }
}
```

## 💬 Message Classes

### EconomyMessages.java

```java
@ConfigsFileProperties
@ConfigsDatabase(database = "economy_system")
@ConfigsCollection(collection = "economy_messages")
@SupportedLanguages({"en", "pl", "es", "de", "fr"})
public class EconomyMessages extends MongoMessages {
    
    // Balance messages
    public String getBalanceDisplay(String currencyName, double amount, String symbol) {
        return get("en", "economy.balance.display", currencyName, amount, symbol);
    }
    
    public String getBalanceDisplay(String lang, String currencyName, double amount, String symbol) {
        return get(lang, "economy.balance.display", currencyName, amount, symbol);
    }
    
    // Transaction messages
    public String getTransactionSuccess(String type, double amount, String currency) {
        return get("en", "economy.transaction." + type + ".success", amount, currency);
    }
    
    public String getTransactionSuccess(String lang, String type, double amount, String currency) {
        return get(lang, "economy.transaction." + type + ".success", amount, currency);
    }
    
    // Exchange messages
    public String getExchangeRate(String fromCurrency, String toCurrency, double rate, double fee) {
        return get("en", "economy.exchange.rate", fromCurrency, toCurrency, rate, fee * 100);
    }
    
    public String getExchangeRate(String lang, String fromCurrency, String toCurrency, double rate, double fee) {
        return get(lang, "economy.exchange.rate", fromCurrency, toCurrency, rate, fee * 100);
    }
    
    // Error messages
    public String getInsufficientFunds(String currency, double needed, double available) {
        return get("en", "economy.error.insufficient_funds", currency, needed, available);
    }
    
    public String getInsufficientFunds(String lang, String currency, double needed, double available) {
        return get(lang, "economy.error.insufficient_funds", currency, needed, available);
    }
}
```

## 🎯 Main Plugin Class

### EconomyPlugin.java

```java
public class EconomyPlugin extends JavaPlugin {
    
    private ConfigManager configManager;
    private CurrencyManager currencyManager;
    private AccountManager accountManager;
    private ExchangeManager exchangeManager;
    private AnalyticsManager analyticsManager;
    private EconomyMessages economyMessages;
    private BankMessages bankMessages;
    
    @Override
    public void onEnable() {
        try {
            // Initialize MongoDB Configs API
            configManager = MongoConfigsAPI.createConfigManager(
                getConfig().getString("mongodb.uri", "mongodb://localhost:27017"),
                getConfig().getString("mongodb.database", "economy_system")
            );
            
            // Initialize message systems
            economyMessages = configManager.messagesOf(EconomyMessages.class);
            bankMessages = configManager.messagesOf(BankMessages.class);
            
            // Initialize managers
            currencyManager = new CurrencyManager(this);
            accountManager = new AccountManager(this);
            exchangeManager = new ExchangeManager(this);
            analyticsManager = new AnalyticsManager(this);
            
            // Register commands
            getCommand("economy").setExecutor(new EconomyCommand(this));
            getCommand("bank").setExecutor(new BankCommand(this));
            getCommand("admineconomy").setExecutor(new AdminEconomyCommand(this));
            
            // Register events
            getServer().getPluginManager().registerEvents(new EconomyGUIListener(this), this);
            
            // Start background tasks
            startInterestCalculationTask();
            startMarketUpdateTask();
            
            getLogger().info("Economy System enabled successfully!");
            
        } catch (Exception e) {
            getLogger().severe("Failed to initialize Economy System: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    private void startInterestCalculationTask() {
        // Calculate interest on loans every hour
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                accountManager.calculateLoanInterest();
            } catch (Exception e) {
                getLogger().severe("Failed to calculate loan interest: " + e.getMessage());
            }
        }, 20 * 60 * 60, 20 * 60 * 60); // Every hour
    }
    
    private void startMarketUpdateTask() {
        // Update exchange rates every 5 minutes
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                exchangeManager.updateMarketRates();
            } catch (Exception e) {
                getLogger().severe("Failed to update market rates: " + e.getMessage());
            }
        }, 20 * 60 * 5, 20 * 60 * 5); // Every 5 minutes
    }
    
    // Getters...
}
```

## 🛠️ Managers

### CurrencyManager.java

```java
public class CurrencyManager {
    
    private final EconomyPlugin plugin;
    private final ConfigManager configManager;
    private final Map<String, Currency> currencyCache = new ConcurrentHashMap<>();
    
    public CurrencyManager(EconomyPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        
        loadCurrencies();
        setupChangeStreams();
    }
    
    private void loadCurrencies() {
        try {
            List<Currency> currencies = configManager.getAll(Currency.class);
            currencies.forEach(currency -> currencyCache.put(currency.getId(), currency));
            plugin.getLogger().info("Loaded " + currencies.size() + " currencies");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load currencies: " + e.getMessage());
        }
    }
    
    private void setupChangeStreams() {
        configManager.watchCollection(Currency.class, changeEvent -> {
            Currency currency = changeEvent.getDocument();
            if (currency != null) {
                currencyCache.put(currency.getId(), currency);
                plugin.getLogger().info("Currency updated: " + currency.getId());
            }
        });
    }
    
    public Currency getCurrency(String currencyId) {
        return currencyCache.get(currencyId);
    }
    
    public Collection<Currency> getAllCurrencies() {
        return currencyCache.values();
    }
    
    public List<Currency> getTradeableCurrencies() {
        return currencyCache.values().stream()
            .filter(Currency::isTradeable)
            .collect(Collectors.toList());
    }
    
    public boolean isValidCurrency(String currencyId) {
        return currencyCache.containsKey(currencyId);
    }
    
    public double applyTax(String currencyId, double amount) {
        Currency currency = currencyCache.get(currencyId);
        if (currency == null || !currency.isTaxable()) {
            return amount;
        }
        
        double tax = amount * currency.getTaxRate();
        return amount - tax; // Return amount after tax
    }
    
    public double calculateTax(String currencyId, double amount) {
        Currency currency = currencyCache.get(currencyId);
        if (currency == null || !currency.isTaxable()) {
            return 0.0;
        }
        
        return amount * currency.getTaxRate();
    }
}
```

### AccountManager.java

```java
public class AccountManager {
    
    private final EconomyPlugin plugin;
    private final ConfigManager configManager;
    private final Map<String, Account> accountCache = new ConcurrentHashMap<>();
    
    public AccountManager(EconomyPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        
        loadAccounts();
        setupChangeStreams();
    }
    
    private void loadAccounts() {
        try {
            List<Account> accounts = configManager.getAll(Account.class);
            accounts.forEach(account -> accountCache.put(account.getPlayerId(), account));
            plugin.getLogger().info("Loaded " + accounts.size() + " accounts");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load accounts: " + e.getMessage());
        }
    }
    
    private void setupChangeStreams() {
        configManager.watchCollection(Account.class, changeEvent -> {
            Account account = changeEvent.getDocument();
            if (account != null) {
                accountCache.put(account.getPlayerId(), account);
            }
        });
    }
    
    public Account getAccount(String playerId) {
        return accountCache.computeIfAbsent(playerId, this::createAccount);
    }
    
    private Account createAccount(String playerId) {
        Account account = new Account();
        account.setPlayerId(playerId);
        account.setBalances(new HashMap<>());
        account.setCreatedAt(System.currentTimeMillis());
        account.setLastActivity(System.currentTimeMillis());
        account.setStatus(Account.AccountStatus.ACTIVE);
        account.setPermissions(new ArrayList<>());
        
        // Set default balances for all currencies
        plugin.getCurrencyManager().getAllCurrencies().forEach(currency -> {
            account.setBalance(currency.getId(), currency.getDefaultBalance());
        });
        
        try {
            configManager.save(account);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to create account for " + playerId + ": " + e.getMessage());
        }
        
        return account;
    }
    
    public boolean transfer(String fromPlayerId, String toPlayerId, String currencyId, double amount) {
        Account fromAccount = getAccount(fromPlayerId);
        Account toAccount = getAccount(toPlayerId);
        
        if (!fromAccount.hasBalance(currencyId, amount)) {
            return false;
        }
        
        // Apply tax if applicable
        double tax = plugin.getCurrencyManager().calculateTax(currencyId, amount);
        double finalAmount = amount - tax;
        
        try {
            // Perform transfer
            fromAccount.setBalance(currencyId, fromAccount.getBalance(currencyId) - amount);
            toAccount.setBalance(currencyId, toAccount.getBalance(currencyId) + finalAmount);
            
            // Update activity timestamps
            fromAccount.setLastActivity(System.currentTimeMillis());
            toAccount.setLastActivity(System.currentTimeMillis());
            
            // Save changes
            configManager.save(fromAccount);
            configManager.save(toAccount);
            
            // Record transaction
            plugin.getAnalyticsManager().recordTransaction(
                fromPlayerId, toPlayerId, currencyId, finalAmount, 
                Transaction.TransactionType.TRANSFER
            );
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to transfer " + amount + " " + currencyId + 
                                    " from " + fromPlayerId + " to " + toPlayerId + ": " + e.getMessage());
            return false;
        }
    }
    
    public void calculateLoanInterest() {
        List<BankLoan> activeLoans = configManager.find(BankLoan.class, 
            "status", BankLoan.LoanStatus.ACTIVE);
        
        long currentTime = System.currentTimeMillis();
        
        for (BankLoan loan : activeLoans) {
            try {
                double interest = loan.calculateInterest(currentTime);
                if (interest > 0) {
                    Account account = getAccount(loan.getPlayerId());
                    
                    // Check if player can afford interest
                    if (account.hasBalance(loan.getCurrencyId(), interest)) {
                        account.setBalance(loan.getCurrencyId(), 
                                         account.getBalance(loan.getCurrencyId()) - interest);
                        loan.setOutstandingBalance(loan.getOutstandingBalance() + interest);
                        
                        configManager.save(account);
                        configManager.save(loan);
                        
                        // Notify player
                        notifyInterestCharged(loan.getPlayerId(), interest, loan.getCurrencyId());
                    } else {
                        // Mark loan as defaulted
                        loan.setStatus(BankLoan.LoanStatus.DEFAULTED);
                        configManager.save(loan);
                        
                        notifyLoanDefaulted(loan.getPlayerId());
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to calculate interest for loan " + 
                                        loan.getId() + ": " + e.getMessage());
            }
        }
    }
    
    private void notifyInterestCharged(String playerId, double interest, String currencyId) {
        Player player = plugin.getServer().getPlayer(UUID.fromString(playerId));
        if (player != null && player.isOnline()) {
            String message = plugin.getBankMessages().getInterestCharged(
                getPlayerLanguage(player), interest, currencyId
            );
            player.sendMessage(ColorHelper.parseComponent(message));
        }
    }
    
    private void notifyLoanDefaulted(String playerId) {
        Player player = plugin.getServer().getPlayer(UUID.fromString(playerId));
        if (player != null && player.isOnline()) {
            String message = plugin.getBankMessages().getLoanDefaulted(getPlayerLanguage(player));
            player.sendMessage(ColorHelper.parseComponent(message));
        }
    }
    
    private String getPlayerLanguage(Player player) {
        return MongoConfigsAPI.getLanguageManager().getPlayerLanguageOrDefault(
            player.getUniqueId().toString()
        );
    }
}
```

### ExchangeManager.java

```java
public class ExchangeManager {
    
    private final EconomyPlugin plugin;
    private final ConfigManager configManager;
    private final Map<String, ExchangeRate> rateCache = new ConcurrentHashMap<>();
    
    public ExchangeManager(EconomyPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        
        loadExchangeRates();
        setupChangeStreams();
    }
    
    private void loadExchangeRates() {
        try {
            List<ExchangeRate> rates = configManager.getAll(ExchangeRate.class);
            rates.forEach(rate -> rateCache.put(rate.getId(), rate));
            plugin.getLogger().info("Loaded " + rates.size() + " exchange rates");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load exchange rates: " + e.getMessage());
        }
    }
    
    private void setupChangeStreams() {
        configManager.watchCollection(ExchangeRate.class, changeEvent -> {
            ExchangeRate rate = changeEvent.getDocument();
            if (rate != null) {
                rateCache.put(rate.getId(), rate);
            }
        });
    }
    
    public boolean exchangeCurrency(String playerId, String fromCurrency, String toCurrency, double amount) {
        String rateId = fromCurrency + "_" + toCurrency;
        ExchangeRate rate = rateCache.get(rateId);
        
        if (rate == null || !rate.isActive()) {
            return false;
        }
        
        Account account = plugin.getAccountManager().getAccount(playerId);
        
        if (!account.hasBalance(fromCurrency, amount)) {
            return false;
        }
        
        double exchangedAmount = rate.calculateExchange(amount);
        
        try {
            // Perform exchange
            account.setBalance(fromCurrency, account.getBalance(fromCurrency) - amount);
            account.setBalance(toCurrency, account.getBalance(toCurrency) + exchangedAmount);
            
            configManager.save(account);
            
            // Record transaction
            plugin.getAnalyticsManager().recordExchange(
                playerId, fromCurrency, toCurrency, amount, exchangedAmount
            );
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to exchange currency for " + playerId + ": " + e.getMessage());
            return false;
        }
    }
    
    public void updateMarketRates() {
        // Simulate market fluctuations
        Random random = new Random();
        
        for (ExchangeRate rate : rateCache.values()) {
            if (rate.isActive()) {
                // Random fluctuation between -2% and +2%
                double fluctuation = (random.nextDouble() - 0.5) * 0.04;
                double newRate = rate.getRate() * (1.0 + fluctuation);
                
                rate.setRate(Math.max(0.01, newRate)); // Minimum rate of 0.01
                rate.setLastUpdated(System.currentTimeMillis());
                
                try {
                    configManager.save(rate);
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to update rate " + rate.getId() + ": " + e.getMessage());
                }
            }
        }
        
        plugin.getLogger().info("Updated market exchange rates");
    }
    
    public ExchangeRate getExchangeRate(String fromCurrency, String toCurrency) {
        return rateCache.get(fromCurrency + "_" + toCurrency);
    }
    
    public List<ExchangeRate> getAllActiveRates() {
        return rateCache.values().stream()
            .filter(ExchangeRate::isActive)
            .collect(Collectors.toList());
    }
}
```

## 🎨 GUI Components

### ExchangeGUI.java

```java
public class ExchangeGUI {
    
    private final EconomyPlugin plugin;
    private final EconomyMessages messages;
    
    public ExchangeGUI(EconomyPlugin plugin) {
        this.plugin = plugin;
        this.messages = plugin.getEconomyMessages();
    }
    
    public void openExchangeGUI(Player player) {
        String language = getPlayerLanguage(player);
        String title = messages.getExchangeGUITitle(language);
        
        Inventory inventory = Bukkit.createInventory(null, 54, ColorHelper.parseString(title));
        
        // Add exchange rate items
        List<ExchangeRate> rates = plugin.getExchangeManager().getAllActiveRates();
        int slot = 0;
        
        for (ExchangeRate rate : rates) {
            if (slot >= 45) break;
            addExchangeRateItem(inventory, player, rate, slot++, language);
        }
        
        // Add player balance display
        addBalanceDisplay(inventory, player, language);
        
        player.openInventory(inventory);
    }
    
    private void addExchangeRateItem(Inventory inventory, Player player, ExchangeRate rate, int slot, String language) {
        Currency fromCurrency = plugin.getCurrencyManager().getCurrency(rate.getFromCurrency());
        Currency toCurrency = plugin.getCurrencyManager().getCurrency(rate.getToCurrency());
        
        if (fromCurrency == null || toCurrency == null) return;
        
        ItemStack item = new ItemStack(Material.valueOf(fromCurrency.getIcon()));
        ItemMeta meta = item.getItemMeta();
        
        String displayName = messages.getExchangeRateItemName(language, 
            fromCurrency.getNameKey(), toCurrency.getNameKey());
        meta.setDisplayName(ColorHelper.parseString(displayName));
        
        List<String> lore = new ArrayList<>();
        lore.add(ColorHelper.parseString("&7Rate: &f" + String.format("%.4f", rate.getRate())));
        lore.add(ColorHelper.parseString("&7Fee: &f" + String.format("%.1f%%", rate.getFee() * 100)));
        
        double playerBalance = plugin.getAccountManager().getAccount(player.getUniqueId().toString())
            .getBalance(rate.getFromCurrency());
        lore.add(ColorHelper.parseString("&7Your balance: &f" + String.format("%.2f", playerBalance)));
        
        lore.add("");
        lore.add(ColorHelper.parseString("&eClick to exchange"));
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }
    
    private void addBalanceDisplay(Inventory inventory, Player player, String language) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        
        String displayName = messages.getBalanceDisplayTitle(language);
        meta.setDisplayName(ColorHelper.parseString(displayName));
        
        List<String> lore = new ArrayList<>();
        Account account = plugin.getAccountManager().getAccount(player.getUniqueId().toString());
        
        for (Currency currency : plugin.getCurrencyManager().getAllCurrencies()) {
            double balance = account.getBalance(currency.getId());
            String balanceLine = messages.getCurrencyBalanceLine(language, 
                currency.getNameKey(), balance, currency.getSymbol());
            lore.add(ColorHelper.parseString(balanceLine));
        }
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        inventory.setItem(49, item);
    }
    
    private String getPlayerLanguage(Player player) {
        return MongoConfigsAPI.getLanguageManager().getPlayerLanguageOrDefault(
            player.getUniqueId().toString()
        );
    }
}
```

## 📊 Analytics Manager

### AnalyticsManager.java

```java
public class AnalyticsManager {
    
    private final EconomyPlugin plugin;
    private final ConfigManager configManager;
    
    public AnalyticsManager(EconomyPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }
    
    public void recordTransaction(String fromPlayerId, String toPlayerId, String currencyId, 
                                double amount, Transaction.TransactionType type) {
        Transaction transaction = new Transaction();
        transaction.setId(UUID.randomUUID().toString());
        transaction.setFromPlayerId(fromPlayerId);
        transaction.setToPlayerId(toPlayerId);
        transaction.setCurrencyId(currencyId);
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setTimestamp(System.currentTimeMillis());
        
        try {
            configManager.save(transaction);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to record transaction: " + e.getMessage());
        }
    }
    
    public void recordExchange(String playerId, String fromCurrency, String toCurrency, 
                             double fromAmount, double toAmount) {
        // Record as two transactions: spend and receive
        recordTransaction(playerId, null, fromCurrency, -fromAmount, Transaction.TransactionType.EXCHANGE);
        recordTransaction(null, playerId, toCurrency, toAmount, Transaction.TransactionType.EXCHANGE);
    }
    
    public List<Transaction> getPlayerTransactions(String playerId, int limit) {
        try {
            return configManager.find(Transaction.class, 
                Filters.or(
                    Filters.eq("fromPlayerId", playerId),
                    Filters.eq("toPlayerId", playerId)
                ))
                .stream()
                .sorted((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()))
                .limit(limit)
                .collect(Collectors.toList());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to get transactions for " + playerId + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }
    
    public Map<String, Double> getCurrencyTotals() {
        Map<String, Double> totals = new HashMap<>();
        
        try {
            List<Account> accounts = configManager.getAll(Account.class);
            
            for (Account account : accounts) {
                for (Map.Entry<String, Double> balance : account.getBalances().entrySet()) {
                    totals.merge(balance.getKey(), balance.getValue(), Double::sum);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to calculate currency totals: " + e.getMessage());
        }
        
        return totals;
    }
    
    public double getTotalTransactionsVolume(String currencyId, long sinceTimestamp) {
        try {
            return configManager.find(Transaction.class, 
                Filters.and(
                    Filters.eq("currencyId", currencyId),
                    Filters.gte("timestamp", sinceTimestamp)
                ))
                .stream()
                .mapToDouble(Transaction::getAmount)
                .sum();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to calculate transaction volume: " + e.getMessage());
            return 0.0;
        }
    }
}
```

## 🔄 Advanced Features

### Dynamic Exchange Rates

```java
public void updateMarketRates() {
    // Simulate realistic market fluctuations
    for (ExchangeRate rate : rateCache.values()) {
        if (rate.isActive()) {
            // Base fluctuation
            double baseFluctuation = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.04;
            
            // Add trend influence
            double trendInfluence = calculateTrendInfluence(rate);
            
            // Add volatility based on trading volume
            double volumeInfluence = calculateVolumeInfluence(rate);
            
            double totalFluctuation = baseFluctuation + trendInfluence + volumeInfluence;
            double newRate = rate.getRate() * (1.0 + Math.max(-0.1, Math.min(0.1, totalFluctuation)));
            
            rate.setRate(Math.max(0.01, newRate));
            rate.setLastUpdated(System.currentTimeMillis());
            
            configManager.save(rate);
        }
    }
}
```

### Loan System

```java
public boolean applyForLoan(String playerId, String currencyId, double amount, int termDays) {
    Account account = getAccount(playerId);
    Currency currency = plugin.getCurrencyManager().getCurrency(currencyId);
    
    if (currency == null) return false;
    
    // Credit score check (simplified)
    double creditScore = calculateCreditScore(playerId);
    if (creditScore < 0.5) return false;
    
    // Interest rate based on credit score and amount
    double interestRate = calculateInterestRate(amount, termDays, creditScore);
    
    BankLoan loan = new BankLoan();
    loan.setId(UUID.randomUUID().toString());
    loan.setPlayerId(playerId);
    loan.setCurrencyId(currencyId);
    loan.setPrincipalAmount(amount);
    loan.setOutstandingBalance(amount);
    loan.setInterestRate(interestRate);
    loan.setIssuedAt(System.currentTimeMillis());
    loan.setDueDate(System.currentTimeMillis() + (termDays * 24 * 60 * 60 * 1000L));
    loan.setStatus(BankLoan.LoanStatus.ACTIVE);
    loan.setPaymentHistory(new ArrayList<>());
    
    try {
        // Credit funds to account
        account.setBalance(currencyId, account.getBalance(currencyId) + amount);
        
        configManager.save(account);
        configManager.save(loan);
        
        return true;
    } catch (Exception e) {
        plugin.getLogger().severe("Failed to create loan for " + playerId + ": " + e.getMessage());
        return false;
    }
}
```

## 📈 Performance Optimizations

- **Memory Caching**: All frequently accessed data cached in memory
- **Async Operations**: Database operations run asynchronously
- **Batch Updates**: Multiple operations batched together
- **Connection Pooling**: Efficient MongoDB connection management
- **Indexing**: Proper database indexes for fast queries

## 🔒 Security Features

- **Transaction Validation**: Double-verification of all transfers
- **Audit Logging**: Complete transaction history
- **Fraud Detection**: Pattern analysis for suspicious activity
- **Account Freezing**: Administrative controls for compromised accounts

---

*Next: Learn about [[Parkour Plugin Example]] for leaderboard and timing systems.*
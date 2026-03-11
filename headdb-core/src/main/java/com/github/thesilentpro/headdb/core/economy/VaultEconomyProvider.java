package com.github.thesilentpro.headdb.core.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * EconomyProvider implementation that uses Vault for economy operations.
 */
public class VaultEconomyProvider implements EconomyProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(VaultEconomyProvider.class);
    private static final String MODERN_ECONOMY_CLASS = "net.milkbowl.vault2.economy.Economy";
    private static final String DEFAULT_PLUGIN_NAME = "HeadDB";

    private final String pluginName;
    private Economy legacyEconomy;
    private Object modernEconomy;
    private Method modernHasMethod;
    private Method modernWithdrawMethod;
    private Method modernDepositMethod;
    private Method modernTransactionSuccessMethod;

    public VaultEconomyProvider() {
        this(DEFAULT_PLUGIN_NAME);
    }

    public VaultEconomyProvider(String pluginName) {
        this.pluginName = pluginName == null || pluginName.isBlank() ? DEFAULT_PLUGIN_NAME : pluginName;
    }

    @Override
    public boolean init() {
        resetProviders();

        if (Bukkit.getServer().getPluginManager().getPlugin("Vault") == null) {
            LOGGER.error("Vault is not installed but is enabled in the config.yml!");
            return false;
        }

        boolean modernHooked = hookModernEconomy();
        boolean legacyHooked = hookLegacyEconomy();

        if (!modernHooked && !legacyHooked) {
            LOGGER.error("Could not find a Vault economy provider!");
            return false;
        }

        if (modernHooked) {
            LOGGER.info("Vault modern economy provider hooked: {}", getModernProviderName());
            if (!legacyHooked) {
                LOGGER.debug("No legacy Vault economy provider found; using modern Vault provider only.");
            }
            return true;
        }

        LOGGER.info("Vault legacy economy provider hooked: {}", legacyEconomy.getName());
        return true;
    }

    @Override
    public CompletableFuture<Boolean> canAfford(Player player, double amount) {
        Objects.requireNonNull(player, "Player cannot be null");
        if (amount < 0) {
            return failed("Amount must be non-negative");
        }
        if (modernEconomy != null) {
            return completeModernAction(() -> invokeModernHas(player.getUniqueId(), amount), "check balance");
        }
        if (legacyEconomy == null) {
            return failed("Economy provider not initialized");
        }

        return CompletableFuture.completedFuture(legacyEconomy.has(player, amount));
    }

    @Override
    public CompletableFuture<Boolean> withdraw(Player player, double amount) {
        Objects.requireNonNull(player, "Player cannot be null");
        if (amount < 0) {
            return failed("Amount must be non-negative");
        }
        if (modernEconomy != null) {
            return completeModernAction(() -> invokeModernWithdraw(player.getUniqueId(), amount), "withdraw funds");
        }
        if (legacyEconomy == null) {
            return failed("Economy provider not initialized");
        }

        net.milkbowl.vault.economy.EconomyResponse response = legacyEconomy.withdrawPlayer(player, amount);
        return CompletableFuture.completedFuture(response != null && response.transactionSuccess());
    }

    @Override
    public CompletableFuture<Boolean> deposit(Player player, double amount) {
        Objects.requireNonNull(player, "Player cannot be null");
        if (amount < 0) {
            return failed("Amount must be non-negative");
        }
        if (modernEconomy != null) {
            return completeModernAction(() -> invokeModernDeposit(player.getUniqueId(), amount), "deposit funds");
        }
        if (legacyEconomy == null) {
            return failed("Economy provider not initialized");
        }

        net.milkbowl.vault.economy.EconomyResponse response = legacyEconomy.depositPlayer(player, amount);
        return CompletableFuture.completedFuture(response != null && response.transactionSuccess());
    }

    private void resetProviders() {
        this.legacyEconomy = null;
        this.modernEconomy = null;
        this.modernHasMethod = null;
        this.modernWithdrawMethod = null;
        this.modernDepositMethod = null;
        this.modernTransactionSuccessMethod = null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean hookModernEconomy() {
        try {
            Class<?> modernClass = Class.forName(MODERN_ECONOMY_CLASS);
            RegisteredServiceProvider<?> provider = Bukkit.getServer()
                    .getServicesManager()
                    .getRegistration((Class) modernClass);

            if (provider == null || provider.getProvider() == null) {
                return false;
            }

            this.modernEconomy = provider.getProvider();
            this.modernHasMethod = modernClass.getMethod("has", String.class, UUID.class, BigDecimal.class);
            this.modernWithdrawMethod = modernClass.getMethod("withdraw", String.class, UUID.class, BigDecimal.class);
            this.modernDepositMethod = modernClass.getMethod("deposit", String.class, UUID.class, BigDecimal.class);
            this.modernTransactionSuccessMethod = null;
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (ReflectiveOperationException ex) {
            LOGGER.warn("Failed to initialize Vault modern economy support. Falling back to legacy API: {}", ex.getMessage());
            return false;
        }
    }

    private boolean hookLegacyEconomy() {
        RegisteredServiceProvider<Economy> provider = Bukkit.getServer()
                .getServicesManager()
                .getRegistration(Economy.class);
        if (provider == null || provider.getProvider() == null) {
            return false;
        }
        this.legacyEconomy = provider.getProvider();
        return true;
    }

    private String getModernProviderName() {
        if (modernEconomy == null) {
            return "unknown";
        }
        try {
            Object name = modernEconomy.getClass().getMethod("getName").invoke(modernEconomy);
            if (name instanceof String providerName && !providerName.isBlank()) {
                return providerName;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return modernEconomy.getClass().getSimpleName();
    }

    private CompletableFuture<Boolean> completeModernAction(CheckedBooleanAction action, String operationName) {
        try {
            return CompletableFuture.completedFuture(action.execute());
        } catch (ReflectiveOperationException ex) {
            LOGGER.error("Failed to {} through Vault modern API: {}", operationName, ex.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    private boolean invokeModernHas(UUID playerId, double amount) throws ReflectiveOperationException {
        Object result = modernHasMethod.invoke(modernEconomy, pluginName, playerId, BigDecimal.valueOf(amount));
        return result instanceof Boolean afford && afford;
    }

    private boolean invokeModernWithdraw(UUID playerId, double amount) throws ReflectiveOperationException {
        Object response = modernWithdrawMethod.invoke(modernEconomy, pluginName, playerId, BigDecimal.valueOf(amount));
        return modernTransactionSuccess(response);
    }

    private boolean invokeModernDeposit(UUID playerId, double amount) throws ReflectiveOperationException {
        Object response = modernDepositMethod.invoke(modernEconomy, pluginName, playerId, BigDecimal.valueOf(amount));
        return modernTransactionSuccess(response);
    }

    private boolean modernTransactionSuccess(Object response) throws ReflectiveOperationException {
        if (response == null) {
            return false;
        }
        if (modernTransactionSuccessMethod == null || modernTransactionSuccessMethod.getDeclaringClass() != response.getClass()) {
            modernTransactionSuccessMethod = response.getClass().getMethod("transactionSuccess");
        }
        Object result = modernTransactionSuccessMethod.invoke(response);
        return result instanceof Boolean success && success;
    }

    private CompletableFuture<Boolean> failed(String message) {
        LOGGER.error(message);
        return CompletableFuture.completedFuture(false);
    }

    @FunctionalInterface
    private interface CheckedBooleanAction {
        boolean execute() throws ReflectiveOperationException;
    }

}

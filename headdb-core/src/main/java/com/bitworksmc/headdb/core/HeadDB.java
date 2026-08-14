package com.bitworksmc.headdb.core;

import com.github.thesilentpro.grim.listener.PageListeners;
import com.github.thesilentpro.grim.page.registry.PageRegistry;
import com.bitworksmc.headdb.api.HeadAPI;
import com.bitworksmc.headdb.api.HeadDatabase;
import com.bitworksmc.headdb.api.model.Head;
import com.bitworksmc.headdb.core.api.LegacyHeadAPIAdapter;
import com.bitworksmc.headdb.core.command.HDBMainCommand;
import com.bitworksmc.headdb.core.command.HDBSubCommandManager;
import com.bitworksmc.headdb.core.config.Config;
import com.bitworksmc.headdb.core.config.ConfigManager;
import com.bitworksmc.headdb.core.config.SoundConfig;
import com.bitworksmc.headdb.core.economy.EconomyProvider;
import com.bitworksmc.headdb.core.economy.VaultEconomyProvider;
import com.bitworksmc.headdb.core.factory.ItemFactoryRegistry;
import com.bitworksmc.headdb.core.menu.MenuManager;
import com.bitworksmc.headdb.core.storage.PlayerStorage;
import com.bitworksmc.headdb.core.util.Compatibility;
import com.bitworksmc.headdb.core.util.HDBLocalization;
import com.bitworksmc.headdb.core.util.Utils;
import com.bitworksmc.headdb.implementation.BaseHeadAPI;
import com.bitworksmc.headdb.implementation.BaseHeadDatabase;
import com.github.thesilentpro.inputs.paper.PaperInputListener;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class HeadDB extends JavaPlugin {

    // Avoid using class for this logger: it will use the fully qualified name with the default logger cfg.
    private static final Logger LOGGER = LoggerFactory.getLogger("HeadDB");
    private static final int PRELOAD_BATCH_SIZE = 250;

    private ConfigManager configManager;
    private HeadDatabase headDatabase;
    private HeadAPI headApi;
    private ExecutorService databaseExecutor;
    private HDBSubCommandManager subCommandManager;
    private MenuManager menuManager;
    private PlayerStorage playerStorage;
    private HDBLocalization localization;
    private EconomyProvider economyProvider;

    @SuppressWarnings("removal")
    @Override
    public void onEnable() {
        saveDefaultConfig();

        ItemFactoryRegistry.init(this);

        this.configManager = new ConfigManager(this);
        this.configManager.loadAll(this);
        this.localization = new HDBLocalization(this);
        this.localization.init();

        Config config = this.configManager.getConfig();
        String econProvider = config.getEconomyProvider();
        if (econProvider != null) {
            if (econProvider.equalsIgnoreCase("NONE") || econProvider.isEmpty()) {
                LOGGER.debug("Economy is disabled.");
            } else if (config.getEconomyProvider().equalsIgnoreCase("VAULT")) {
                VaultEconomyProvider vaultProvider = new VaultEconomyProvider(getName());
                if (vaultProvider.init()) {
                    this.economyProvider = vaultProvider;
                    LOGGER.debug("Economy Provider: Vault");
                } else {
                    LOGGER.warn("Vault economy was enabled but no compatible provider was found. Economy features are disabled.");
                }
            } else {
                LOGGER.warn("Unknown economy provider in the config.yml!");
            }
        }

        // Init database
        int databaseThreads = config.getDatabaseThreads();
        this.databaseExecutor = Utils.executorService(databaseThreads, "Head Database Worker");
        this.headDatabase = new BaseHeadDatabase(
                databaseExecutor,
                config.getDatabaseSourceUrls(),
                config.resolveEnabledIndexes()
        );
        this.headDatabase.update().thenAcceptAsync(heads -> handleDatabaseUpdate(config, heads), Compatibility.getMainThreadExecutor(this));
        this.headApi = new BaseHeadAPI(config.getApiThreads(), headDatabase);
        this.menuManager = new MenuManager(this);
        this.headDatabase.onReady().thenAcceptAsync(heads -> this.menuManager.registerDefaults(this, heads), Compatibility.getMainThreadExecutor(this));
        this.playerStorage = new PlayerStorage(getDataFolder());
        this.playerStorage.load();
        Compatibility.runAsyncRepeating(this, this.playerStorage::save, config.getPlayerStorageSaveInterval() * 20L, config.getPlayerStorageSaveInterval() * 20L);

        getServer().getServicesManager().register(HeadAPI.class, headApi, this, ServicePriority.Normal);
        getServer().getServicesManager().register(com.github.thesilentpro.headdb.api.HeadAPI.class, new LegacyHeadAPIAdapter(headApi), this, ServicePriority.Normal);

        // Command handling
        this.subCommandManager = new HDBSubCommandManager(this);
        this.subCommandManager.registerDefaults();

        PluginCommand command = getCommand("headdb");
        if (command == null) {
            throw new RuntimeException("Missing HeadDB command in plugin.yml"); // Should never reach this
        }

        HDBMainCommand mainCommand = new HDBMainCommand(this);
        command.setExecutor(mainCommand);
        command.setTabCompleter(mainCommand);

        new PageListeners().register(this);
        if (Compatibility.IS_PAPER) {
            new PaperInputListener().register(this);
        }

        // Start updater task
        if (config.isUpdaterEnabled()) {
            Compatibility.runAsyncRepeating(this, () ->
                    this.headDatabase.update().thenAcceptAsync(heads -> {
                        handleDatabaseUpdate(config, heads);
                        this.menuManager.registerDefaults(this, heads);
                    }, Compatibility.getMainThreadExecutor(this)),
                    86400L * 20L,
                    86400L * 20L
            );
        }

        if (!Compatibility.IS_PAPER) {
            String bukkitName = Bukkit.getName();
            String fullBukkitVersion = Bukkit.getBukkitVersion();
            int qualifierSeparator = fullBukkitVersion.indexOf('-');
            String bukkitVersion = qualifierSeparator > 0
                    ? fullBukkitVersion.substring(0, qualifierSeparator)
                    : fullBukkitVersion;
            LOGGER.warn("""
                    \s
                    \s
                        You're running a non-Paper server implementation (detected: {}).
                        For best performance and full compatibility with this plugin, it is highly recommended switching to Paper.
                        > Download it here: https://papermc.io/downloads
                    \s
                    """, bukkitName + " " + bukkitVersion);
        }

        new Metrics(this, 30043);
        LOGGER.info("Done! Database is {}", !this.headDatabase.isReady() ? "loading..." : "ready");
    }

    @Override
    public void onDisable() {
        // Closing a view fires InventoryCloseEvent, which removes that view from the
        // registry. Iterate a snapshot so the listener cannot mutate our iterator.
        for (InventoryView view : new ArrayList<>(PageRegistry.INSTANCE.getPages().keySet())) {
            view.close();
            PageRegistry.INSTANCE.remove(view);
        }
        if (this.playerStorage != null) {
            this.playerStorage.save();
        }
        if (this.headApi != null) {
            this.headApi.getExecutor().shutdownNow();
        }
        if (this.databaseExecutor != null) {
            this.databaseExecutor.shutdownNow();
        }
    }

    public EconomyProvider getEconomyProvider() {
        return economyProvider;
    }

    public HDBLocalization getLocalization() {
        return localization;
    }

    public PlayerStorage getPlayerStorage() {
        return playerStorage;
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }

    public HDBSubCommandManager getSubCommandManager() {
        return subCommandManager;
    }

    public SoundConfig getSoundConfig() {
        return this.configManager.getSoundConfig();
    }

    public Config getCfg() {
        return this.configManager.getConfig();
    }

    public HeadAPI getHeadApi() {
        return headApi;
    }

    private void handleDatabaseUpdate(Config config, List<Head> heads) {
        LOGGER.info("Loaded {} heads!", heads.size());

        if (!config.isPreloadHeads()) {
            return;
        }
        if (heads.isEmpty()) {
            LOGGER.info("No heads to preload.");
            return;
        }

        // Item creation must happen on the server thread, but doing 86,000+
        // items in one tick freezes the server. Spread opt-in preloading out.
        preloadBatch(heads, 0, 0);
    }

    private void preloadBatch(List<Head> heads, int start, int nextMilestoneIndex) {
        if (!isEnabled()) {
            return;
        }

        int total = heads.size();
        int end = Math.min(start + PRELOAD_BATCH_SIZE, total);
        for (int i = start; i < end; i++) {
            heads.get(i).getItem();
        }

        int[] milestones = {25, 50, 75, 100};
        int milestoneIndex = nextMilestoneIndex;
        int percent = (int) ((end / (double) total) * 100);
        while (milestoneIndex < milestones.length && percent >= milestones[milestoneIndex]) {
            LOGGER.info("Preloading heads... {}%", milestones[milestoneIndex]);
            milestoneIndex++;
        }

        if (end < total) {
            int nextIndex = milestoneIndex;
            Compatibility.runGlobalTaskLater(this, () -> preloadBatch(heads, end, nextIndex), 1L);
        }
    }

}

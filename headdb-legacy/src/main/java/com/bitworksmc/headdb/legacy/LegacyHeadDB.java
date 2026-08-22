package com.bitworksmc.headdb.legacy;

import com.bitworksmc.headdb.api.HeadAPI;
import com.bitworksmc.headdb.api.model.Head;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bstats.bukkit.Metrics;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class LegacyHeadDB extends JavaPlugin implements CommandExecutor, TabCompleter {
    private ExecutorService executor;
    private LegacyDatabase database;
    private LegacyHeadAPI api;
    private LegacyPlayerStorage playerStorage;
    private LegacyMessages messages;
    private LegacySounds sounds;
    private LegacyMenuManager menus;
    private LegacyEconomy economy;
    private LegacyApiAdapter deprecatedApi;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureResource("categories.yml");
        ensureResource("sounds.yml");
        ensureResource("messages/en.yml");
        playerStorage = new LegacyPlayerStorage(getDataFolder(), getLogger());
        playerStorage.load();
        messages = new LegacyMessages(getDataFolder());
        sounds = new LegacySounds(getDataFolder(), playerStorage);

        int threads = Math.max(1, getConfig().getInt("database.threads",
                getConfig().getInt("database-threads", 2)));
        executor = Executors.newFixedThreadPool(threads, new ThreadFactory() {
            private int sequence;

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "HeadDB Legacy Worker-" + (++sequence));
                thread.setDaemon(true);
                return thread;
            }
        });

        String databaseUrl = getConfig().getString("database.sourceUrl",
                getConfig().getString("database-url"));
        List<String> databaseUrls = new ArrayList<String>();
        databaseUrls.add(databaseUrl);
        databaseUrls.addAll(getConfig().getStringList("database.fallbackSourceUrls"));
        database = new LegacyDatabase(databaseUrls, executor);
        api = new LegacyHeadAPI(executor, database);
        economy = new LegacyEconomy(getConfig());
        LegacyItemFactory.configure(getConfig(), economy.isEnabled());
        menus = new LegacyMenuManager(this, database, api, playerStorage, messages, sounds, economy);
        getServer().getServicesManager().register(HeadAPI.class, api, this, ServicePriority.Normal);
        deprecatedApi = new LegacyApiAdapter(api);
        getServer().getServicesManager().register(com.github.thesilentpro.headdb.api.HeadAPI.class,
                deprecatedApi, this, ServicePriority.Normal);

        PluginCommand command = getCommand("headdb");
        if (command == null) {
            throw new IllegalStateException("Missing headdb command in plugin.yml");
        }
        command.setExecutor(this);
        command.setTabCompleter(this);

        database.update().whenComplete((heads, failure) -> Bukkit.getScheduler().runTask(this, () -> {
            if (!isEnabled()) {
                return;
            }
            if (failure != null) {
                getLogger().severe("Could not load the head database: " + rootMessage(failure));
            } else {
                getLogger().info("Loaded " + heads.size() + " heads.");
                if (getConfig().getBoolean("preloadHeads", false)) preload(heads);
            }
        }));
        long saveSeconds = Math.max(60L, getConfig().getLong("storage.player.saveInterval", 1800L));
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, playerStorage::save,
                saveSeconds * 20L, saveSeconds * 20L);
        if (getConfig().getBoolean("updater", true)) {
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, database::update,
                    24L * 60L * 60L * 20L, 24L * 60L * 60L * 20L);
        }
        if (getConfig().getBoolean("updateChecker.enabled", true)) new LegacyUpdateChecker(this);
        new Metrics(this, 30043);
        getLogger().info("Enabled the Minecraft 1.8.8-1.20.6 legacy implementation.");
    }

    private void ensureResource(String path) {
        if (!new java.io.File(getDataFolder(), path).isFile()) saveResource(path, false);
    }

    private void preload(final List<Head> heads) {
        new BukkitRunnable() {
            private int index;
            @Override public void run() {
                int end = Math.min(index + 100, heads.size());
                while (index < end) heads.get(index++).getItem();
                if (index >= heads.size() || !isEnabled()) cancel();
            }
        }.runTaskTimer(this, 1L, 1L);
    }

    @Override
    public void onDisable() {
        if (api != null) {
            getServer().getServicesManager().unregister(HeadAPI.class, api);
        }
        if (deprecatedApi != null) {
            getServer().getServicesManager().unregister(com.github.thesilentpro.headdb.api.HeadAPI.class, deprecatedApi);
        }
        if (playerStorage != null) {
            playerStorage.save();
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return open(sender, args);
        }
        if (args[0].equalsIgnoreCase("info")) {
            if (!sender.hasPermission("headdb.command.info")) {
                return denied(sender);
            }
            sender.sendMessage(ChatColor.GOLD + "HeadDB " + getDescription().getVersion()
                    + ChatColor.GRAY + " (legacy)");
            sender.sendMessage(ChatColor.GRAY + "Server: " + Bukkit.getName() + " " + Bukkit.getBukkitVersion());
            sender.sendMessage(ChatColor.GRAY + "Database: "
                    + (database.isReady() ? database.getHeads().size() + " heads" : "loading"));
            return true;
        }

        if (args[0].equalsIgnoreCase("search")) {
            return search(sender, args);
        }
        if (args[0].equalsIgnoreCase("give")) {
            return give(sender, args);
        }
        if (args[0].equalsIgnoreCase("sounds")) {
            return toggleSounds(sender);
        }
        if (args[0].equalsIgnoreCase("submit")) {
            return submit(sender);
        }
        if (args[0].equalsIgnoreCase("open")) {
            return open(sender, args);
        }

        sender.sendMessage(ChatColor.RED + "Usage: /" + label + " [info|search <name>|give <id> [player]|submit]");
        return true;
    }

    private boolean submit(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.get("noConsole", "Only players can use this command."));
            return true;
        }
        if (!sender.hasPermission("headdb.command.submit")) return denied(sender);

        String url = LegacyWebsiteLinks.submissionUrl(getConfig().getString("website.url", "https://headdb.net"));
        sendWebsiteLink(
                (Player) sender,
                messages.get("command.submit.link", "Have a head to share? Submit it on headdb.net for review."),
                url,
                "Open " + url
        );
        return true;
    }

    private boolean open(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.get("noConsole", "Only players can use this command."));
            return true;
        }
        if (!sender.hasPermission("headdb.command.open")) return denied(sender);
        if (!database.isReady()) {
            sender.sendMessage(messages.get("databaseLoading", "The head database is still loading."));
            return true;
        }
        Player player = (Player) sender;
        if (args.length >= 2) menus.openCategory(player, join(args, 1), 0);
        else menus.openMain(player);
        return true;
    }

    private boolean toggleSounds(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.get("noConsole", "Only players can use this command."));
            return true;
        }
        if (!sender.hasPermission("headdb.command.sounds")) return denied(sender);
        Player player = (Player) sender;
        LegacyPlayerData data = playerStorage.get(player.getUniqueId());
        data.setSoundsEnabled(!data.isSoundsEnabled());
        player.sendMessage(messages.get(data.isSoundsEnabled() ? "command.sounds.enabled" : "command.sounds.disabled",
                data.isSoundsEnabled() ? "HeadDB interface sounds enabled." : "HeadDB interface sounds disabled."));
        if (data.isSoundsEnabled()) sounds.play(player, "success");
        return true;
    }

    private boolean search(final CommandSender sender, String[] args) {
        if (!sender.hasPermission("headdb.command.search")) {
            return denied(sender);
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /hdb search <name>");
            return true;
        }
        if (!database.isReady()) {
            sender.sendMessage(ChatColor.YELLOW + "The head database is still loading.");
            return true;
        }

        final String query = join(args, 1);
        java.util.concurrent.CompletableFuture.supplyAsync(() -> filterSearch(args, sender), executor).whenComplete((matches, failure) ->
                Bukkit.getScheduler().runTask(this, () -> {
                    if (failure != null) {
                        sender.sendMessage(ChatColor.RED + "Search failed: " + rootMessage(failure));
                        return;
                    }
                    int limit = Math.max(1, getConfig().getInt("search-limit", 20));
                    if (sender instanceof Player) {
                        menus.openSearch((Player) sender, query, matches);
                        sender.sendMessage(messages.get("command.search.found", "Found {amount} heads!",
                                "amount", String.valueOf(matches.size())));
                        sendSearchWebsiteHint((Player) sender, args);
                        return;
                    }
                    sender.sendMessage(ChatColor.GOLD + "HeadDB matches for '" + query + "':");
                    for (int i = 0; i < Math.min(limit, matches.size()); i++) {
                        Head head = matches.get(i);
                        sender.sendMessage(ChatColor.YELLOW + "#" + head.getId() + ChatColor.GRAY + " "
                                + head.getName() + " (" + head.getCategory() + ")");
                    }
                    if (matches.isEmpty()) {
                        sender.sendMessage(ChatColor.GRAY + "No matching heads.");
                    } else if (matches.size() > limit) {
                        sender.sendMessage(ChatColor.GRAY + "...and " + (matches.size() - limit) + " more.");
                    }
                }));
        return true;
    }

    private List<Head> filterSearch(String[] args, CommandSender sender) {
        String category = null;
        Set<String> tags = new HashSet<String>();
        Set<Integer> ids = new HashSet<Integer>();
        List<String> names = new ArrayList<String>();
        boolean any = false;
        for (int i = 1; i < args.length; i++) {
            String token = args[i];
            String lower = token.toLowerCase(Locale.ROOT);
            if (lower.equals("--any")) any = true;
            else if (lower.startsWith("category:")) category = lower.substring(9);
            else if (lower.startsWith("tags:")) {
                for (String tag : lower.substring(5).split(",")) if (!tag.trim().isEmpty()) tags.add(tag.trim());
            } else if (lower.startsWith("ids:")) {
                for (String id : lower.substring(4).split(",")) {
                    try { ids.add(Integer.parseInt(id.trim())); } catch (NumberFormatException ignored) { }
                }
            } else names.add(token);
        }
        String name = join(names.toArray(new String[names.size()]), 0).toLowerCase(Locale.ROOT);
        List<Head> result = new ArrayList<Head>();
        List<Head> all = database.getHeads();
        if (all == null) return result;
        for (Head head : all) {
            boolean nameMatch = !name.isEmpty() && head.getName().toLowerCase(Locale.ROOT).contains(name);
            boolean categoryMatch = category != null && head.getCategory().equalsIgnoreCase(category);
            boolean idMatch = !ids.isEmpty() && ids.contains(head.getId());
            Set<String> headTags = new HashSet<String>();
            for (String tag : head.getTags()) headTags.add(tag.toLowerCase(Locale.ROOT));
            boolean tagAny = !tags.isEmpty() && !Collections.disjoint(tags, headTags);
            boolean tagAll = headTags.containsAll(tags);
            boolean matches;
            if (any) matches = nameMatch || categoryMatch || idMatch || tagAny;
            else matches = (name.isEmpty() || nameMatch) && (category == null || categoryMatch)
                    && (ids.isEmpty() || idMatch) && (tags.isEmpty() || tagAll);
            String permission = "headdb.category." + head.getCategory().toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9._-]+", "_").replaceAll("^_+|_+$", "");
            if (matches && (sender.hasPermission("headdb.category.*") || sender.hasPermission(permission))) result.add(head);
        }
        return result;
    }

    private boolean give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("headdb.command.give")) {
            return denied(sender);
        }
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /hdb give <player> <amount> <head>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(messages.get("invalidTarget", "Could not find player: {target}", "target", args[1]));
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(messages.get("invalidNumber", "Invalid number: {number}", "number", args[2]));
            return true;
        }
        int maximum = Math.max(1, getConfig().getInt("maxBuyAmount", 2304));
        if (amount < 1 || amount > maximum) {
            sender.sendMessage(messages.get("command.give.invalidAmount", "Amount must be between 1 and {max}",
                    "max", String.valueOf(maximum)));
            return true;
        }
        String identifier = join(args, 3);
        Head head = null;
        if (identifier.toLowerCase(Locale.ROOT).startsWith("id:")) {
            try { head = database.getById(Integer.parseInt(identifier.substring(3))); } catch (NumberFormatException ignored) { }
        }
        if (head == null) head = database.getByTexture(identifier);
        if (head == null) {
            List<Head> all = database.getHeads();
            if (all != null) for (Head candidate : all) {
                if (candidate.getName().equalsIgnoreCase(identifier)) { head = candidate; break; }
            }
        }
        if (head == null) {
            sender.sendMessage(messages.get("command.give.invalidId", "Unknown head: {id}", "id", identifier));
            return true;
        }
        int remaining = amount;
        while (remaining > 0) {
            ItemStack item = head.getItem();
            item = LegacyItemFactory.prepareForGive(item);
            int stackSize = Math.min(remaining, item.getMaxStackSize());
            item.setAmount(stackSize);
            java.util.Map<Integer, ItemStack> leftovers = target.getInventory().addItem(item);
            for (ItemStack leftover : leftovers.values()) target.getWorld().dropItemNaturally(target.getLocation(), leftover);
            remaining -= stackSize;
        }
        sender.sendMessage(messages.get("command.give.success", "Gave {amount}x {name} to {target}",
                "amount", String.valueOf(amount), "name", head.getName(), "target", target.getName()));
        if (sender instanceof Player) sounds.play((Player) sender, "success");
        return true;
    }

    private boolean denied(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "You do not have permission to use that command.");
        return true;
    }

    private void sendSearchWebsiteHint(Player player, String[] args) {
        if (!getConfig().getBoolean("website.searchHint.enabled", true)) {
            return;
        }
        String url = LegacyWebsiteLinks.searchUrl(
                getConfig().getString("website.url", "https://headdb.net"),
                args
        );
        sendWebsiteLink(
                player,
                messages.get("command.search.website",
                        "Want to refine this search faster? Open it on headdb.net to filter results and copy ready-to-use commands."),
                url,
                "Open this search on headdb.net"
        );
    }

    private void sendWebsiteLink(Player player, String text, String url, String hoverText) {
        BaseComponent[] components = TextComponent.fromLegacyText(text);
        ClickEvent clickEvent = new ClickEvent(ClickEvent.Action.OPEN_URL, url);
        HoverEvent hoverEvent = new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(hoverText).color(net.md_5.bungee.api.ChatColor.AQUA).create()
        );
        for (BaseComponent component : components) {
            component.setClickEvent(clickEvent);
            component.setHoverEvent(hoverEvent);
        }
        player.spigot().sendMessage(components);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return prefix(Arrays.asList("open", "info", "search", "give", "sounds", "submit"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> players = new ArrayList<String>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                players.add(player.getName());
            }
            return prefix(players, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return prefix(Arrays.asList("1", "32", "64"), args[2]);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("search")) {
            return prefix(Arrays.asList("tags:", "category:", "ids:", "--any"), args[args.length - 1]);
        }
        return Collections.emptyList();
    }

    private static List<String> prefix(List<String> values, String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<String>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                result.add(value);
            }
        }
        return result;
    }

    private static String join(String[] values, int start) {
        StringBuilder result = new StringBuilder();
        for (int i = start; i < values.length; i++) {
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(values[i]);
        }
        return result.toString();
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}

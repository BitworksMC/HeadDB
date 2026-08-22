package com.bitworksmc.headdb.core.command.sub;

import com.bitworksmc.headdb.api.model.Head;
import com.bitworksmc.headdb.core.HeadDB;
import com.bitworksmc.headdb.core.command.HDBSubCommand;
import com.bitworksmc.headdb.core.menu.gui.HeadsGUI;
import com.bitworksmc.headdb.core.util.Compatibility;
import com.bitworksmc.headdb.core.util.PermissionUtil;
import com.bitworksmc.headdb.core.util.WebsiteLinks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class HDBCommandSearch extends HDBSubCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(HDBCommandSearch.class);
    private final HeadDB plugin;
    private final List<String> completions = List.of("tags:", "category:", "ids:", "--any");

    public HDBCommandSearch(HeadDB plugin) {
        super("search", "Search for specific heads.", "[tags:|category:|ids:] [head]", "s", "find");
        this.plugin = plugin;
    }

    @Override
    public void handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            this.plugin.getLocalization().sendMessage(sender, "noConsole");
            return;
        }

        this.plugin.getLocalization().sendMessage(sender, "command.search.start");
        plugin.getHeadApi().onReady().thenApplyAsync(allHeads -> {
            // detect & strip --any
            // Enables loose search (match if any filter passes instead of all).
            boolean any = Arrays.stream(args).anyMatch(a -> a.equalsIgnoreCase("--any"));
            List<String> parts = Arrays.stream(args, 1, args.length).filter(a -> !a.equalsIgnoreCase("--any")).toList();

            // parse filters
            String category = null;
            List<String> tags = new ArrayList<>();
            List<Integer> ids = new ArrayList<>();
            List<String> nameParts = new ArrayList<>();

            for (String token : parts) {
                String lower = token.toLowerCase(Locale.ROOT);
                if (lower.startsWith("category:")) {
                    category = token.substring("category:".length());
                } else if (lower.startsWith("tags:")) {
                    String raw = token.substring("tags:".length());
                    if (!raw.isEmpty()) {
                        Arrays.stream(raw.split(","))
                                .map(String::trim)
                                .filter(tag -> !tag.isEmpty())
                                .forEach(tags::add);
                    }
                } else if (lower.startsWith("ids:")) {
                    String raw = token.substring("ids:".length());
                    if (!raw.isEmpty()) {
                        for (String part : raw.split(",")) {
                            String trimmed = part.trim();
                            if (!trimmed.isEmpty()) {
                                try {
                                    ids.add(Integer.parseInt(trimmed));
                                } catch (NumberFormatException e) {
                                    Compatibility.getSenderExecutor(plugin, sender).execute(() ->
                                            plugin.getLocalization().sendMessage(sender, "command.search.invalidId", msg ->
                                                    msg.replaceText(builder -> builder.matchLiteral("{id}").replacement(trimmed))));
                                    return SearchResult.invalid();
                                }
                            }
                        }
                    }
                } else {
                    nameParts.add(token);
                }
            }
            String nameQuery = String.join(" ", nameParts);

            if ((category == null || category.isBlank())
                    && tags.isEmpty()
                    && ids.isEmpty()
                    && nameQuery.isBlank()) {
                Compatibility.getSenderExecutor(plugin, sender).execute(() ->
                        plugin.getLocalization().sendMessage(sender, "command.search.empty"));
                return SearchResult.invalid();
            }

            // Echo filters to player
            String finalCategory = category;
            Compatibility.getSenderExecutor(plugin, sender).execute(() -> {
                plugin.getLocalization().sendMessage(sender, "command.search.filter", msg -> msg.replaceText(builder ->
                                builder.matchLiteral("{name}").replacement(!nameQuery.isEmpty() ? nameQuery : "/"))
                                .replaceText(builder -> builder.matchLiteral("{category}").replacement(finalCategory != null ? finalCategory : "/"))
                                .replaceText(builder -> builder.matchLiteral("{tags}").replacement(!tags.isEmpty() ? String.join(",", tags) : "/"))
                                .replaceText(builder -> builder.matchLiteral("{ids}").replacement(!ids.isEmpty() ? String.join(",", ids.stream().map(String::valueOf).toArray(String[]::new)) : "/"))
                                .replaceText(builder -> builder.matchLiteral("{mode}").replacement(any ? "ANY" : "ALL"))
                );
            });

            // lower all your query bits once
            String qCat = category == null ? null : category.toLowerCase(Locale.ROOT);
            String qName = nameQuery.trim().toLowerCase(Locale.ROOT);
            Set<String> tagSet = tags.stream().map(t -> t.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
            Set<Integer> idSet = new HashSet<>(ids);

            List<Head> result   = new ArrayList<>();

            if (any) {
                // ANY‑mode: match if _one_ of the filters hits
                for (Head h : allHeads) {
                    // grab once per head
                    String headCat  = h.getCategory().toLowerCase(Locale.ROOT);
                    String headName = h.getName().toLowerCase(Locale.ROOT);
                    List<String> headTags = h.getTags(); // assume a few tags only

                    boolean matchCat = (headCat.equals(qCat));
                    boolean matchTag = (!tagSet.isEmpty() && headTags.stream().anyMatch(t -> tagSet.contains(t.toLowerCase(Locale.ROOT))));
                    boolean matchId = (!idSet.isEmpty() && idSet.contains(h.getId()));
                    boolean matchName = (!qName.isEmpty() && headName.contains(qName));

                    if (matchCat || matchTag || matchId || matchName) {
                        result.add(h);
                    }
                }
            } else {
                // ALL‑mode: only add if _every_ non‑empty filter passes
                for (Head h : allHeads) {
                    // category
                    if (qCat != null &&
                            !h.getCategory().equalsIgnoreCase(qCat)) {
                        continue;
                    }
                    // tags
                    if (!tagSet.isEmpty()) {
                        // must contain all query tags
                        List<String> headTags = h.getTags();
                        boolean allTagsMatch = true;
                        for (String tq : tagSet) {
                            boolean found = false;
                            for (String ht : headTags) {
                                if (ht.equalsIgnoreCase(tq)) {
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                allTagsMatch = false;
                                break;
                            }
                        }
                        if (!allTagsMatch) continue;
                    }
                    // ids
                    if (!idSet.isEmpty() && !idSet.contains(h.getId())) {
                        continue;
                    }
                    // name
                    if (!qName.isEmpty() &&
                            !h.getName().toLowerCase(Locale.ROOT).contains(qName)) {
                        continue;
                    }

                    // made it through all checks
                    result.add(h);
                }
            }

            return new SearchResult(
                    result,
                    qName,
                    WebsiteLinks.searchUrl(plugin.getCfg().getWebsiteUrl(), nameQuery, category, tags, ids),
                    true
            );
        }).thenAcceptAsync(searchResult -> {
            if (!searchResult.valid) {
                Compatibility.playSound(player, plugin.getSoundConfig().get("failure"));
                return;
            }
            List<Head> heads = searchResult.heads.stream()
                    .filter(head -> PermissionUtil.hasCategoryPermission(player, head.getCategory()))
                    .toList();
            if (heads == null || heads.isEmpty()) {
                this.plugin.getLocalization().sendMessage(player, "command.search.none");
                sendWebsiteHint(player, searchResult.websiteUrl);
                return;
            }

            plugin.getLocalization().sendMessage(player, "command.search.found", msg -> msg.replaceText(builder -> builder.matchLiteral("{amount}").replacement(String.valueOf(heads.size()))).replaceText(builder -> builder.matchLiteral("{name}").replacement(searchResult.name)));
            sendWebsiteHint(player, searchResult.websiteUrl);

            HeadsGUI gui = new HeadsGUI(
                    plugin,
                    "search_" + player.getUniqueId(),
                    plugin.getLocalization().getMessage(player.getUniqueId(), "menu.search.name")
                            .orElseGet(() -> Component.text("HeadDB » Search » " + searchResult.name))
                            .replaceText(builder -> builder.matchLiteral("{name}").replacement(searchResult.name)),
                    heads
            );
            gui.getGuiRegistry().setCurrentPage(player.getUniqueId(), gui.getKey(), 0);
            gui.open(player);
            Compatibility.playSound(player, plugin.getSoundConfig().get("menu.open"));
        }, Compatibility.getEntityExecutor(plugin, player)).exceptionally(ex -> {
            LOGGER.error("Failed to search the head database for {}", player.getUniqueId(), ex);
            Compatibility.getEntityExecutor(plugin, player).execute(() -> {
                plugin.getLocalization().sendMessage(player, "command.search.failed");
                Compatibility.playSound(player, plugin.getSoundConfig().get("failure"));
            });
            return null;
        });
    }

    @Override
    public List<String> handleCompletions(CommandSender sender, String[] args) {
        return completions;
    }

    private void sendWebsiteHint(Player player, String url) {
        if (!plugin.getCfg().isWebsiteSearchHintEnabled()) {
            return;
        }
        Component message = plugin.getLocalization().getMessage(player.getUniqueId(), "command.search.website")
                .orElseGet(() -> Component.text()
                        .append(Component.text("Want to refine this search faster? ", NamedTextColor.GRAY))
                        .append(Component.text("Open it on headdb.net", NamedTextColor.AQUA))
                        .append(Component.text(" to filter results and copy ready-to-use commands.", NamedTextColor.GRAY))
                        .build());
        Compatibility.sendMessage(player, WebsiteLinks.makeClickable(
                message,
                url,
                Component.text("Open this search on headdb.net", NamedTextColor.AQUA)
        ));
    }

    private record SearchResult(List<Head> heads, String name, String websiteUrl, boolean valid) {
        private static SearchResult invalid() {
            return new SearchResult(List.of(), "", "", false);
        }
    }

}

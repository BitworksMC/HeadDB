package com.bitworksmc.headdb.core.update;

import com.bitworksmc.headdb.core.HeadDB;
import com.bitworksmc.headdb.core.util.Compatibility;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class UpdateChecker implements Listener, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("HeadDB");
    private static final URI RELEASE_API = URI.create("https://api.github.com/repos/BitworksMC/HeadDB/releases/latest");
    private static final URI DOWNLOAD_URL = URI.create("https://github.com/BitworksMC/HeadDB/releases/latest");
    private static final String NOTIFY_PERMISSION = "headdb.update.notify";

    private final HeadDB plugin;
    private final String currentVersion;
    private final GitHubReleaseClient releaseClient;
    private final ScheduledExecutorService executor;
    private final long intervalHours;
    private final boolean notifyConsole;
    private final boolean notifyPlayers;
    private final Object lifecycleLock = new Object();
    private final AtomicReference<AvailableUpdate> availableUpdate = new AtomicReference<>();
    private final Set<UUID> notifiedPlayers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    private volatile String announcedTag;
    private volatile boolean failureLogged;
    private int unspecifiedRateLimitFailures;

    public UpdateChecker(
            HeadDB plugin,
            long intervalHours,
            boolean notifyConsole,
            boolean notifyPlayers
    ) {
        this.plugin = plugin;
        this.currentVersion = Compatibility.getPluginVersion(plugin);
        this.intervalHours = Math.max(1L, intervalHours);
        this.notifyConsole = notifyConsole;
        this.notifyPlayers = notifyPlayers;
        this.releaseClient = new GitHubReleaseClient(
                RELEASE_API,
                "HeadDB/" + currentVersion + " (+https://github.com/BitworksMC/HeadDB)"
        );
        this.executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "HeadDB Update Checker");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        synchronized (lifecycleLock) {
            if (!started.compareAndSet(false, true) || closed.get()) {
                return;
            }
            if (notifyPlayers) {
                plugin.getServer().getPluginManager().registerEvents(this, plugin);
            }
            scheduleNext(Duration.ZERO);
        }
    }

    private void checkSafely() {
        if (closed.get()) {
            return;
        }

        Duration nextDelay = Duration.ofHours(intervalHours);
        try {
            Optional<GitHubReleaseClient.ReleaseInfo> latest = releaseClient.fetchLatest();
            if (closed.get()) {
                return;
            }
            unspecifiedRateLimitFailures = 0;
            handleRelease(latest);
        } catch (GitHubReleaseClient.RateLimitException ex) {
            if (closed.get()) {
                return;
            }
            Duration retryDelay;
            if (ex.retrySpecifiedByServer()) {
                unspecifiedRateLimitFailures = 0;
                retryDelay = Duration.between(Instant.now(), ex.retryAt());
            } else {
                retryDelay = fallbackRateLimitDelay(unspecifiedRateLimitFailures++);
            }
            nextDelay = retryDelay.compareTo(Duration.ofMinutes(1)) < 0
                    ? Duration.ofMinutes(1)
                    : retryDelay;
            logFailure(
                    "GitHub rate limit reached; the next update check will wait until " + Instant.now().plus(nextDelay),
                    ex
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            unspecifiedRateLimitFailures = 0;
            if (!closed.get()) {
                logFailure("Could not check GitHub for HeadDB updates: " + ex.getMessage(), ex);
            }
        } finally {
            scheduleNext(nextDelay);
        }
    }

    static Duration fallbackRateLimitDelay(int previousFailures) {
        int exponent = Math.min(Math.max(0, previousFailures), 5);
        return Duration.ofHours(Math.min(1L << exponent, 24L));
    }

    private void scheduleNext(Duration delay) {
        if (closed.get()) {
            return;
        }
        try {
            executor.schedule(this::checkSafely, Math.max(0L, delay.toMillis()), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ex) {
            if (!closed.get()) {
                throw ex;
            }
        }
    }

    private void handleRelease(Optional<GitHubReleaseClient.ReleaseInfo> latest) {
        if (latest.isEmpty()) {
            markSuccessfulCheck();
            clearAvailableUpdate();
            return;
        }
        String latestTag = latest.get().tagName();
        switch (compareVersions(currentVersion, latestTag)) {
            case UPDATE_AVAILABLE -> {
                markSuccessfulCheck();
                publishUpdate(new AvailableUpdate(currentVersion, latestTag, DOWNLOAD_URL));
            }
            case UP_TO_DATE, CURRENT_AHEAD -> {
                markSuccessfulCheck();
                clearAvailableUpdate();
            }
            case INVALID -> logFailure(
                    "Cannot compare HeadDB versions (current: " + currentVersion + ", latest: " + latestTag + ")",
                    null
            );
        }
    }

    private void markSuccessfulCheck() {
        synchronized (lifecycleLock) {
            if (!closed.get()) {
                failureLogged = false;
            }
        }
    }

    static VersionComparison compareVersions(String current, String latest) {
        Optional<SemanticVersion> currentVersion = SemanticVersion.parse(current);
        Optional<SemanticVersion> latestVersion = SemanticVersion.parse(latest);
        if (currentVersion.isEmpty() || latestVersion.isEmpty()) {
            return VersionComparison.INVALID;
        }

        int comparison = latestVersion.get().compareTo(currentVersion.get());
        if (comparison > 0) {
            return VersionComparison.UPDATE_AVAILABLE;
        }
        if (comparison < 0) {
            return VersionComparison.CURRENT_AHEAD;
        }
        return VersionComparison.UP_TO_DATE;
    }

    private void publishUpdate(AvailableUpdate update) {
        synchronized (lifecycleLock) {
            if (closed.get()) {
                return;
            }
            AvailableUpdate previous = availableUpdate.getAndSet(update);
            if (previous != null && Objects.equals(previous.latestVersion(), update.latestVersion())) {
                return;
            }

            notifiedPlayers.clear();
            if (!Objects.equals(announcedTag, update.latestVersion())) {
                announcedTag = update.latestVersion();
                if (notifyConsole) {
                    LOGGER.warn(
                            "A new HeadDB version is available: {} (current: {}). Download it at {}",
                            update.latestVersion(),
                            update.currentVersion(),
                            update.downloadUrl()
                    );
                }
            }

            if (notifyPlayers && plugin.isEnabled()) {
                Compatibility.getMainThreadExecutor(plugin).execute(() -> {
                    synchronized (lifecycleLock) {
                        if (!closed.get() && plugin.isEnabled() && update.equals(availableUpdate.get())) {
                            Bukkit.getOnlinePlayers().forEach(player -> notifyPlayer(player, update));
                        }
                    }
                });
            }
        }
    }

    private void clearAvailableUpdate() {
        synchronized (lifecycleLock) {
            if (availableUpdate.getAndSet(null) != null) {
                notifiedPlayers.clear();
                announcedTag = null;
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        AvailableUpdate update = availableUpdate.get();
        if (update != null) {
            notifyPlayer(event.getPlayer(), update);
        }
    }

    private void notifyPlayer(Player player, AvailableUpdate update) {
        synchronized (lifecycleLock) {
            if (closed.get() || !plugin.isEnabled() || !update.equals(availableUpdate.get())) {
                return;
            }

            Compatibility.getEntityExecutor(plugin, player).execute(() -> {
                synchronized (lifecycleLock) {
                    if (closed.get()
                            || !player.isOnline()
                            || !player.hasPermission(NOTIFY_PERMISSION)
                            || !update.equals(availableUpdate.get())
                            || !notifiedPlayers.add(player.getUniqueId())) {
                        return;
                    }

                    Component message = plugin.getLocalization()
                            .getMessage(player.getUniqueId(), "update.available")
                            .map(template -> applyUpdateValues(template, update))
                            .orElseGet(() -> fallbackMessage(update));
                    Compatibility.sendMessage(player, message);
                }
            });
        }
    }

    private static Component applyUpdateValues(Component template, AvailableUpdate update) {
        return template
                .replaceText(builder -> builder.matchLiteral("{current}").replacement(Component.text(update.currentVersion())))
                .replaceText(builder -> builder.matchLiteral("{latest}").replacement(Component.text(update.latestVersion())))
                .replaceText(builder -> builder.matchLiteral("{url}").replacement(downloadLink(update.downloadUrl())));
    }

    private static Component fallbackMessage(AvailableUpdate update) {
        return Component.empty()
                .append(Component.text("A new HeadDB version is available: ", NamedTextColor.YELLOW))
                .append(Component.text(update.latestVersion(), NamedTextColor.GREEN))
                .append(Component.text(" (current: " + update.currentVersion() + ")", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("Download: ", NamedTextColor.GRAY))
                .append(downloadLink(update.downloadUrl()));
    }

    private static Component downloadLink(URI url) {
        return Component.text(url.toString(), NamedTextColor.AQUA)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(url.toString()))
                .hoverEvent(HoverEvent.showText(Component.text("Open the HeadDB releases page", NamedTextColor.YELLOW)));
    }

    private void logFailure(String message, Exception exception) {
        synchronized (lifecycleLock) {
            if (closed.get()) {
                return;
            }
            if (!failureLogged) {
                failureLogged = true;
                LOGGER.warn(message);
                if (exception != null) {
                    LOGGER.debug("Update checker failure details", exception);
                }
                return;
            }
            LOGGER.debug(message);
        }
    }

    @Override
    public void close() {
        synchronized (lifecycleLock) {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            availableUpdate.set(null);
            notifiedPlayers.clear();
        }
        HandlerList.unregisterAll(this);
        releaseClient.close();
        executor.shutdownNow();
    }

    private record AvailableUpdate(String currentVersion, String latestVersion, URI downloadUrl) {
    }

    enum VersionComparison {
        UPDATE_AVAILABLE,
        UP_TO_DATE,
        CURRENT_AHEAD,
        INVALID
    }
}

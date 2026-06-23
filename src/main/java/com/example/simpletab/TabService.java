package com.example.simpletab;

import com.example.simpletab.bridge.ArcartXBridge;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * TAB 核心服务：排序玩家、渲染 pack、定时发包。
 */
public final class TabService implements Listener {

    private final JavaPlugin plugin;
    private final TabConfig config;
    private final ArcartXBridge bridge;
    private final PlaceholderResolver placeholderResolver;
    private final RefreshGuard refreshGuard;

    private BukkitTask refreshTask;
    private final Map<UUID, List<String>> lastPayloads = new LinkedHashMap<>();
    private volatile boolean globalRefreshNeeded = false;

    public TabService(JavaPlugin plugin, TabConfig config, ArcartXBridge bridge) {
        this.plugin = plugin;
        this.config = config;
        this.bridge = bridge;
        this.placeholderResolver = new PlaceholderResolver();
        this.refreshGuard = new RefreshGuard(config.guardConfig);
    }

    public void start() {
        if (!config.enabled) {
            plugin.getLogger().info("Tab 定义已禁用，服务未启动。");
            return;
        }
        stop();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh,
            config.refreshIntervalTicks, config.refreshIntervalTicks);
        plugin.getLogger().info("TabService 已启动，刷新间隔 " + config.refreshIntervalTicks + " ticks");
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        HandlerList.unregisterAll(this);
        lastPayloads.clear();
        refreshGuard.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        lastPayloads.remove(event.getPlayer().getUniqueId());
        requestGlobalRefresh();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastPayloads.remove(event.getPlayer().getUniqueId());
    }

    public void requestGlobalRefresh() {
        globalRefreshNeeded = true;
    }

    public void handleClientPacket(Player player, String packetId, List<String> data) {
        if (!config.enabled || !bridge.isAvailable()) return;
        if (config.clientRefreshPacketId.isBlank() || packetId == null) return;
        if (!config.clientRefreshPacketId.equalsIgnoreCase(packetId)) return;
        if (!config.clientRefreshAction.isBlank()) {
            if (data == null || data.isEmpty() || !config.clientRefreshAction.equalsIgnoreCase(data.get(0))) {
                return;
            }
        }
        if (!refreshGuard.allow(player)) return;
        refreshPlayer(player);
    }

    private void refresh() {
        if (!bridge.isAvailable()) return;
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        // 清除已离线玩家的缓存
        lastPayloads.entrySet().removeIf(e -> Bukkit.getPlayer(e.getKey()) == null);
        // 排序
        List<Player> sorted = sort(online);
        // 截断
        if (config.maxEntries > 0 && sorted.size() > config.maxEntries) {
            sorted = sorted.subList(0, config.maxEntries);
        }
        // 构建并发送 payload（按 viewer 维度）
        for (Player viewer : online) {
            List<String> payload = buildPayload(sorted, viewer);
            List<String> previous = lastPayloads.get(viewer.getUniqueId());
            if (globalRefreshNeeded || !Objects.equals(payload, previous)) {
                boolean sent = bridge.sendPacket(viewer, config.uiId, config.packetHandler, payload);
                if (sent) {
                    lastPayloads.put(viewer.getUniqueId(), new ArrayList<>(payload));
                }
                if (config.debug) {
                    plugin.getLogger().info("发包 -> " + viewer.getName() + " | lines=" + payload.size()
                        + " | changed=" + (!Objects.equals(payload, previous)));
                }
            }
        }
        globalRefreshNeeded = false;
        refreshGuard.cleanup();
    }

    private void refreshPlayer(Player viewer) {
        if (!bridge.isAvailable() || !viewer.isOnline()) return;
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        List<Player> sorted = sort(online);
        if (config.maxEntries > 0 && sorted.size() > config.maxEntries) {
            sorted = sorted.subList(0, config.maxEntries);
        }
        List<String> payload = buildPayload(sorted, viewer);
        boolean sent = bridge.sendPacket(viewer, config.uiId, config.packetHandler, payload);
        if (sent) {
            lastPayloads.put(viewer.getUniqueId(), new ArrayList<>(payload));
        }
        if (config.debug) {
            plugin.getLogger().info("客户端刷新 -> " + viewer.getName() + " | lines=" + payload.size());
        }
    }

    private List<Player> sort(List<Player> players) {
        List<Player> copy = new ArrayList<>(players);
        Comparator<Player> comparator;
        switch (config.sortMode.toLowerCase()) {
            default:
                comparator = Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER);
                break;
        }
        if (config.sortDescending) {
            comparator = comparator.reversed();
        }
        copy.sort(comparator);
        return copy;
    }

    private List<String> buildPayload(List<Player> sorted, Player viewer) {
        List<String> lines = new ArrayList<>(sorted.size());
        for (Player target : sorted) {
            String rendered = renderPack(config.pack, target, viewer);
            if (config.omitBlankValues && (rendered == null || rendered.isBlank())) {
                continue;
            }
            lines.add(rendered != null ? rendered : "");
        }
        return lines;
    }

    private String renderPack(String template, Player target, Player viewer) {
        // target 提供 player 上下文
        // viewer 在需要时提供 viewer 上下文（当前简化中未使用）
        String result = placeholderResolver.resolve(template, target);
        return result;
    }
}

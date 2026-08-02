package com.example.simpletab.bridge;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import priv.seventeen.artist.arcartx.api.ArcartXAPI;
import priv.seventeen.artist.arcartx.core.ui.ArcartXUIRegistry;
import priv.seventeen.artist.arcartx.core.ui.adapter.ArcartXUI;
import priv.seventeen.artist.arcartx.event.client.ClientCustomPacketEvent;

import java.io.File;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * 简化的 ArcartX 桥接。
 * 直接调用 ArcartX 公开 API（ArcartXAPI / ArcartXUIRegistry / ClientCustomPacketEvent），
 * 不再使用反射。
 */
public final class ArcartXBridge {

    private final JavaPlugin plugin;
    private final Logger logger;

    private ArcartXUIRegistry uiRegistry;
    private ClientPacketListener clientPacketListener;

    public ArcartXBridge(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public boolean initialize() {
        shutdown();
        Plugin arcartX = Bukkit.getPluginManager().getPlugin("ArcartX");
        if (arcartX == null) {
            logger.severe("SimpleArcartXTab 需要前置插件 ArcartX，请确保已安装。");
            return false;
        }
        if (!arcartX.isEnabled()) {
            logger.severe("前置插件 ArcartX 未启用，桥接初始化中止。");
            return false;
        }
        try {
            uiRegistry = ArcartXAPI.getUIRegistry();
            if (uiRegistry == null) {
                logger.severe("ArcartXUIRegistry 尚未初始化，桥接初始化失败。");
                return false;
            }
            logger.info("ArcartX 桥接初始化成功。");
            return true;
        } catch (Throwable e) {
            logger.severe("ArcartX 桥接初始化失败: " + e.getMessage());
            return false;
        }
    }

    public void shutdown() {
        uiRegistry = null;
        if (clientPacketListener != null) {
            HandlerList.unregisterAll(clientPacketListener);
            clientPacketListener = null;
        }
    }

    public boolean isAvailable() {
        return uiRegistry != null;
    }

    /**
     * 先查 uiRegistry.get(uiId) 判断 UI 是否已注册：
     * - 已存在 → reload(uiId, file) 热重载
     * - 不存在 → register(uiId, file)，用返回 ArcartXUI == null 判定失败
     * 不依赖异常控制流，避免 ArcartX reload 对未知 id 静默无操作导致首次注册永远不触发。
     */
    public boolean registerOrReloadUi(String uiId, File file) {
        if (!isAvailable()) return false;
        try {
            ArcartXUI existing = uiRegistry.get(uiId);
            if (existing != null) {
                uiRegistry.reload(uiId, file);
                return true;
            }
            ArcartXUI ui = uiRegistry.register(uiId, file);
            if (ui == null) {
                logger.warning("注册 UI " + uiId + " 失败：register 返回 null。");
                return false;
            }
            return true;
        } catch (Throwable e) {
            logger.warning("注册 UI " + uiId + " 失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * ArcartXUIRegistry.sendPacket 返回 void，无失败信号。
     * 乐观放行：调用不抛异常即返回 true。
     */
    public boolean sendPacket(Player player, String uiId, String handler, Object payload) {
        if (!isAvailable()) return false;
        try {
            uiRegistry.sendPacket(player, uiId, handler, payload);
            return true;
        } catch (Throwable e) {
            logger.warning("向 " + player.getName() + " 发包失败: " + e.getMessage());
            return false;
        }
    }

    public void listenClientPackets(BiConsumer<Player, ClientPacket> callback) {
        if (!isAvailable()) return;
        if (clientPacketListener != null) {
            HandlerList.unregisterAll(clientPacketListener);
        }
        clientPacketListener = new ClientPacketListener(plugin, callback);
        Bukkit.getPluginManager().registerEvents(clientPacketListener, plugin);
    }

    /**
     * 直接监听 ClientCustomPacketEvent（标准 Bukkit Event）。
     */
    private static final class ClientPacketListener implements Listener {
        private final JavaPlugin plugin;
        private final BiConsumer<Player, ClientPacket> callback;

        ClientPacketListener(JavaPlugin plugin, BiConsumer<Player, ClientPacket> callback) {
            this.plugin = plugin;
            this.callback = callback;
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onClientCustomPacket(ClientCustomPacketEvent event) {
            Player player = event.getPlayer();
            String packetId = event.getId();
            List<String> data = event.getData();
            if (player == null || packetId == null) return;
            List<String> safeData = data != null ? data : List.of();
            Runnable dispatch = () -> callback.accept(player, new ClientPacket(packetId, safeData));
            if (Bukkit.isPrimaryThread()) {
                dispatch.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, dispatch);
            }
        }
    }

    public record ClientPacket(String packetId, List<String> data) {}
}

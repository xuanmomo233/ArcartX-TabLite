package com.example.simpletab.bridge;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * 简化的 ArcartX 反射桥接。
 * 仅保留 UI 注册 / 发包 / 客户端自定义包监听，不依赖 axs-api。
 */
public final class ArcartXBridge {

    private final JavaPlugin plugin;
    private final Logger logger;

    private boolean available;
    private Object uiRegistry;
    private Method registerMethod;
    private Method reloadMethod;
    private Method sendPacketMethod;
    private Listener clientPacketListener;

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
        try {
            ClassLoader cl = arcartX.getClass().getClassLoader();
            Class<?> apiClass = Class.forName("priv.seventeen.artist.arcartx.api.ArcartXAPI", true, cl);
            uiRegistry = apiClass.getMethod("getUIRegistry").invoke(null);

            registerMethod = findMethod(uiRegistry.getClass(), "register", String.class, File.class);
            reloadMethod = findMethod(uiRegistry.getClass(), "reload", String.class, File.class);
            sendPacketMethod = findSendPacketMethod(uiRegistry.getClass());

            if (sendPacketMethod == null) {
                throw new NoSuchMethodException("未找到 UIRegistry.sendPacket(Player,String,String,...) 方法");
            }
            available = true;
            logger.info("ArcartX 桥接初始化成功。");
            return true;
        } catch (ReflectiveOperationException e) {
            logger.severe("ArcartX 桥接初始化失败: " + e.getMessage());
            return false;
        }
    }

    public void shutdown() {
        available = false;
        uiRegistry = null;
        registerMethod = null;
        reloadMethod = null;
        sendPacketMethod = null;
        if (clientPacketListener != null) {
            HandlerList.unregisterAll(clientPacketListener);
            clientPacketListener = null;
        }
    }

    public boolean isAvailable() {
        return available && uiRegistry != null;
    }

    public boolean registerOrReloadUi(String uiId, File file) {
        if (!isAvailable()) return false;
        try {
            if (reloadMethod != null) {
                Object result = reloadMethod.invoke(uiRegistry, uiId, file);
                if (Boolean.TRUE.equals(result)) {
                    return true;
                }
            }
            if (registerMethod != null) {
                Object result = registerMethod.invoke(uiRegistry, uiId, file);
                if (Boolean.TRUE.equals(result)) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException e) {
            logger.warning("注册 UI " + uiId + " 失败: " + e.getMessage());
        }
        return false;
    }

    public boolean sendPacket(Player player, String uiId, String handler, Object payload) {
        if (!isAvailable() || sendPacketMethod == null) return false;
        try {
            Object result = sendPacketMethod.invoke(uiRegistry, player, uiId, handler, payload);
            if (result instanceof Boolean b) return b;
            return true;
        } catch (ReflectiveOperationException e) {
            logger.warning("向 " + player.getName() + " 发包失败: " + e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public void listenClientPackets(BiConsumer<Player, ClientPacket> callback) {
        Plugin arcartX = Bukkit.getPluginManager().getPlugin("ArcartX");
        if (arcartX == null) return;
        try {
            ClassLoader cl = arcartX.getClass().getClassLoader();
            Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName(
                "priv.seventeen.artist.arcartx.event.client.ClientCustomPacketEvent", true, cl);
            if (!Event.class.isAssignableFrom(eventClass)) {
                logger.warning("ClientCustomPacketEvent 不是 Bukkit Event");
                return;
            }
            Method getPlayer = eventClass.getMethod("getPlayer");
            Method getId = eventClass.getMethod("getId");
            Method getData = eventClass.getMethod("getData");

            clientPacketListener = new Listener() {};
            Bukkit.getPluginManager().registerEvent(
                eventClass,
                clientPacketListener,
                EventPriority.MONITOR,
                (listener, event) -> {
                    try {
                        Object rawPlayer = getPlayer.invoke(event);
                        Object rawId = getId.invoke(event);
                        Object rawData = getData.invoke(event);
                        if (!(rawPlayer instanceof Player player) || !(rawId instanceof String packetId)) {
                            return;
                        }
                        List<String> data = rawData instanceof List<?> list
                            ? list.stream().map(String::valueOf).toList()
                            : List.of();
                        Runnable dispatch = () -> callback.accept(player, new ClientPacket(packetId, data));
                        if (Bukkit.isPrimaryThread()) {
                            dispatch.run();
                        } else {
                            Bukkit.getScheduler().runTask(plugin, dispatch);
                        }
                    } catch (ReflectiveOperationException ignored) {
                    }
                },
                plugin,
                true
            );
        } catch (ReflectiveOperationException e) {
            logger.warning("监听客户端自定义包失败: " + e.getMessage());
        }
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        try {
            return clazz.getMethod(name, paramTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Method findSendPacketMethod(Class<?> registryClass) {
        for (Method m : registryClass.getMethods()) {
            if (!"sendPacket".equals(m.getName())) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 4
                && Player.class.isAssignableFrom(p[0])
                && String.class.equals(p[1])
                && String.class.equals(p[2])) {
                return m;
            }
        }
        return null;
    }

    public record ClientPacket(String packetId, List<String> data) {}
}

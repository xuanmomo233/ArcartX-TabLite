package com.example.simpletab;

import com.example.simpletab.bridge.ArcartXBridge;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * SimpleArcartXTab 主插件入口。
 */
public final class SimpleTabPlugin extends JavaPlugin {

    private ArcartXBridge arcartXBridge;
    private TabService tabService;
    private TabConfig tabConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("tab.yml", false);
        File tabsDir = new File(getDataFolder(), "tabs");
        if (!tabsDir.exists()) tabsDir.mkdirs();
        saveResource("tabs/online-tab.yml", false);

        arcartXBridge = new ArcartXBridge(this);
        if (!arcartXBridge.initialize()) {
            getLogger().severe("ArcartX 桥接初始化失败，插件已禁用。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        File uiFile = new File(getDataFolder(), "tab.yml");
        if (!arcartXBridge.registerOrReloadUi("tab", uiFile)) {
            getLogger().warning("注册 UI 'tab' 失败，尝试继续使用...");
        } else {
            getLogger().info("UI 'tab' 已注册。");
        }

        loadConfigAndStartService();

        // 检测并注册 fallback PAPI 扩展（若原生 player/server 扩展缺失）
        registerFallbackPapiExpansionsIfNeeded();

        // 监听客户端刷新包
        arcartXBridge.listenClientPackets((player, packet) -> {
            if (tabService != null) {
                tabService.handleClientPacket(player, packet.packetId(), packet.data());
            }
        });

        getLogger().info("SimpleArcartXTab 已启用。");
    }

    @Override
    public void onDisable() {
        if (tabService != null) {
            tabService.stop();
            tabService = null;
        }
        if (arcartXBridge != null) {
            arcartXBridge.shutdown();
            arcartXBridge = null;
        }
        getLogger().info("SimpleArcartXTab 已禁用。");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!"simpletab".equalsIgnoreCase(command.getName())) {
            return false;
        }
        if (!sender.hasPermission("simpletab.admin")) {
            sender.sendMessage("§c你没有权限。");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§e用法: /simpletab reload");
            return true;
        }
        if ("reload".equalsIgnoreCase(args[0])) {
            reloadConfig();
            if (tabService != null) {
                tabService.stop();
            }
            loadConfigAndStartService();
            sender.sendMessage("§aSimpleArcartXTab 配置已重载。");
            return true;
        }
        sender.sendMessage("§e用法: /simpletab reload");
        return true;
    }

    private void loadConfigAndStartService() {
        File tabDefFile = new File(getDataFolder(), "tabs/online-tab.yml");
        tabConfig = TabConfig.load(tabDefFile, getLogger());
        if (tabService != null) {
            tabService.stop();
        }
        tabService = new TabService(this, tabConfig, arcartXBridge);
        tabService.start();
    }

    private void registerFallbackPapiExpansionsIfNeeded() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        if (!isPapiExpansionRegistered("player")) {
            getLogger().info("PlaceholderAPI 未检测到 player 扩展，已注入内置 player 占位符。");
            new SimplePlayerExpansion(this).register();
        }
        if (!isPapiExpansionRegistered("server")) {
            getLogger().info("PlaceholderAPI 未检测到 server 扩展，已注入内置 server 占位符。");
            new SimpleServerExpansion(this).register();
        }
    }

    private boolean isPapiExpansionRegistered(String identifier) {
        try {
            Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            java.lang.reflect.Method method = papiClass.getMethod("getRegisteredPlaceholderExpansion", String.class);
            Object result = method.invoke(null, identifier);
            return result != null;
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }
}

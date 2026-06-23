package com.example.simpletab;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.logging.Logger;

/**
 * 从 tabs/online-tab.yml 加载的简化 Tab 配置。
 */
public final class TabConfig {

    public boolean enabled = true;
    public String uiId = "tab";
    public String packetHandler = "tab";
    public String clientRefreshPacketId = "TAB";
    public String clientRefreshAction = "update";
    public RefreshGuard.GuardConfig guardConfig = RefreshGuard.GuardConfig.disabled();
    public int maxEntries = -1;
    public String sortMode = "name";
    public boolean sortDescending = false;
    public boolean omitBlankValues = false;
    public String pack = "%player_name%";
    public int refreshIntervalTicks = 20;
    public boolean debug = false;

    public static TabConfig load(File file, Logger logger) {
        TabConfig cfg = new TabConfig();
        if (!file.exists()) {
            logger.warning("Tab 定义文件不存在: " + file.getAbsolutePath());
            return cfg;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        cfg.enabled = yaml.getBoolean("enabled", true);
        cfg.uiId = nullToEmpty(yaml.getString("ui-id", "tab"));
        cfg.packetHandler = nullToEmpty(yaml.getString("packet-handler", "tab"));
        cfg.clientRefreshPacketId = nullToEmpty(yaml.getString("client-refresh-packet-id", "TAB"));
        cfg.clientRefreshAction = nullToEmpty(yaml.getString("client-refresh-action", "update"));
        cfg.guardConfig = loadGuard(yaml.getConfigurationSection("client-refresh-guard"));
        cfg.maxEntries = yaml.getInt("max-entries", -1);
        cfg.sortMode = nullToEmpty(yaml.getString("sort-mode", "name"));
        cfg.sortDescending = yaml.getBoolean("sort-descending", false);
        cfg.omitBlankValues = yaml.getBoolean("omit-blank-values", false);
        cfg.pack = yaml.getString("pack", "%player_name%");
        if (cfg.pack == null || cfg.pack.isBlank()) {
            cfg.pack = "%player_name%";
        }
        return cfg;
    }

    private static RefreshGuard.GuardConfig loadGuard(ConfigurationSection section) {
        if (section == null) {
            return RefreshGuard.GuardConfig.disabled();
        }
        boolean enabled = section.getBoolean("enabled", true);
        long windowMs = Math.max(100L, section.getLong("window-ms", 1500L));
        int maxHits = Math.max(1, section.getInt("max-hits", 1));
        String modeStr = section.getString("mode", "silent");
        RefreshGuard.Mode mode = "notify".equalsIgnoreCase(modeStr) ? RefreshGuard.Mode.NOTIFY : RefreshGuard.Mode.SILENT;
        String notifyMessage = nullToEmpty(section.getString("notify-message", "&cTAB 刷新过快，请稍后再试。"));
        long notifyCooldownMs = Math.max(0L, section.getLong("notify-cooldown-ms", 3000L));
        return new RefreshGuard.GuardConfig(enabled, windowMs, maxHits, mode, notifyMessage, notifyCooldownMs);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}

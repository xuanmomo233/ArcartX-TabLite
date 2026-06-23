package com.example.simpletab;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

import java.lang.reflect.Method;

/**
 * 内置 PAPI server 占位符回退扩展。
 * <p>
 * 当 PlaceholderAPI 未安装 {@code Expansion-server.jar} 时，由插件主动注入，
 * 提供与 PAPI Server 扩展对齐的 {@code %server_xxx%} 占位符。
 */
public final class SimpleServerExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;

    public SimpleServerExpansion(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "server";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().isEmpty()
            ? "ArcartX-TabLite" : plugin.getDescription().getAuthors().get(0);
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    private static final double[] EMPTY_TPS = new double[]{20.0, 20.0, 20.0};
    private static volatile double[] cachedTps = null;
    private static volatile long lastTpsFetch = 0L;

    @Override
    public @Nullable String onRequest(@Nullable org.bukkit.OfflinePlayer player, @NotNull String identifier) {
        return switch (identifier) {
            case "online" -> String.valueOf(Bukkit.getOnlinePlayers().size());
            case "max_players" -> String.valueOf(Bukkit.getMaxPlayers());
            case "name" -> Bukkit.getServer().getName();
            case "version" -> Bukkit.getVersion();
            case "motd" -> Bukkit.getServer().getMotd();
            case "tps", "tps_1" -> formatNumber(fetchTps()[0]);
            case "tps_5" -> formatNumber(fetchTps()[1]);
            case "tps_15" -> formatNumber(fetchTps()[2]);
            default -> null;
        };
    }

    private static double[] fetchTps() {
        long now = System.currentTimeMillis();
        double[] cached = cachedTps;
        long fetchedAt = lastTpsFetch;
        if (cached != null && now - fetchedAt < 1000L) {
            return cached;
        }
        try {
            Method getTPS = Bukkit.class.getMethod("getTPS");
            double[] fresh = (double[]) getTPS.invoke(null);
            cachedTps = fresh;
            lastTpsFetch = now;
            return fresh;
        } catch (Exception ignored) {
            return EMPTY_TPS;
        }
    }

    private static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.000001D) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}

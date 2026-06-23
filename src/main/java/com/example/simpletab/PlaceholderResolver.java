package com.example.simpletab;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 占位符解析器：先解析内置 %player_xxx% / %server_xxx%，
 * 若 PlaceholderAPI 存在则继续解析剩余外部占位符。
 */
public final class PlaceholderResolver {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%([a-zA-Z0-9_]+)%");
    private static final double[] EMPTY_TPS = new double[]{20.0, 20.0, 20.0};
    private static volatile double[] cachedTps = null;
    private static volatile long lastTpsFetch = 0L;

    private final boolean papiAvailable;
    private Object placeholderApi;
    private Method setPlaceholdersMethod;

    public PlaceholderResolver() {
        boolean available = false;
        Object api = null;
        Method method = null;
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                Class<?> clipsClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                method = clipsClass.getMethod("setPlaceholders", Player.class, String.class);
                api = clipsClass;
                available = true;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        this.papiAvailable = available;
        this.placeholderApi = api;
        this.setPlaceholdersMethod = method;
    }

    public String resolve(String input, Player player) {
        if (input == null || input.isEmpty() || !input.contains("%")) {
            return input == null ? "" : input;
        }
        String result = resolveBuiltin(input, player);
        if (papiAvailable && player != null && setPlaceholdersMethod != null) {
            try {
                result = (String) setPlaceholdersMethod.invoke(null, player, result);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return result;
    }

    private String resolveBuiltin(String input, Player player) {
        StringBuilder sb = new StringBuilder();
        Matcher m = PLACEHOLDER_PATTERN.matcher(input);
        int last = 0;
        while (m.find()) {
            sb.append(input, last, m.start());
            String key = m.group(1);
            String replacement = resolveBuiltinKey(key, player);
            sb.append(replacement != null ? replacement : m.group());
            last = m.end();
        }
        sb.append(input, last, input.length());
        return sb.toString();
    }

    private String resolveBuiltinKey(String key, Player player) {
        if (key.startsWith("player_")) {
            return resolvePlayerKey(key.substring(7), player);
        }
        if (key.startsWith("server_")) {
            return resolveServerKey(key.substring(7));
        }
        return resolvePlayerKey(key, player);
    }

    private String resolvePlayerKey(String key, Player player) {
        if (player == null) return null;
        switch (key) {
            case "name": return player.getName();
            case "displayname":
            case "display_name": return nullToEmpty(player.getDisplayName());
            case "uuid": return player.getUniqueId().toString();
            case "world": return player.getWorld().getName();
            case "x": return String.valueOf(player.getLocation().getBlockX());
            case "y": return String.valueOf(player.getLocation().getBlockY());
            case "z": return String.valueOf(player.getLocation().getBlockZ());
            case "health": return formatNumber(player.getHealth());
            case "max_health": return formatNumber(resolveMaxHealth(player));
            case "ping": return String.valueOf(Math.max(0, player.getPing()));
            case "gamemode": return player.getGameMode().name();
            case "scoreboardteam":
            case "scoreboard_team":
                Team team = player.getScoreboard().getEntryTeam(player.getName());
                return team != null ? team.getName() : "";
            default: return null;
        }
    }

    private String resolveServerKey(String key) {
        switch (key) {
            case "online": return String.valueOf(Bukkit.getOnlinePlayers().size());
            case "max_players": return String.valueOf(Bukkit.getMaxPlayers());
            case "name": return Bukkit.getServer().getName();
            case "version": return Bukkit.getVersion();
            case "motd": return Bukkit.getServer().getMotd();
            case "tps":
            case "tps_1": return formatNumber(fetchTps()[0]);
            case "tps_5": return formatNumber(fetchTps()[1]);
            case "tps_15": return formatNumber(fetchTps()[2]);
            default: return null;
        }
    }

    private static double[] fetchTps() {
        long now = System.currentTimeMillis();
        double[] cached = cachedTps;
        long fetchedAt = lastTpsFetch;
        if (cached != null && now - fetchedAt < 1000L) return cached;
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

    private static double resolveMaxHealth(Player player) {
        var attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return attr != null ? attr.getValue() : 20.0D;
    }

    private static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.000001D) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}

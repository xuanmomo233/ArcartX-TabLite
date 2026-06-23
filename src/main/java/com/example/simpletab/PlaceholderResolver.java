package com.example.simpletab;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * 占位符解析器：完全委托给 PlaceholderAPI。
 * <p>
 * 插件启动时会检测 player/server 扩展是否存在；若缺失则主动注入回退扩展。
 */
public final class PlaceholderResolver {

    private final boolean papiAvailable;
    private Method setPlaceholdersMethod;

    public PlaceholderResolver() {
        boolean available = false;
        Method method = null;
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                Class<?> clipsClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                method = clipsClass.getMethod("setPlaceholders", Player.class, String.class);
                available = true;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        this.papiAvailable = available;
        this.setPlaceholdersMethod = method;
    }

    public String resolve(String input, Player player) {
        if (input == null || input.isEmpty() || !input.contains("%")) {
            return input == null ? "" : input;
        }
        if (papiAvailable && player != null && setPlaceholdersMethod != null) {
            try {
                return (String) setPlaceholdersMethod.invoke(null, player, input);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return input;
    }
}

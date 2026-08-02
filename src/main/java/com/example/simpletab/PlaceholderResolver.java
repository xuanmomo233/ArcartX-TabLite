package com.example.simpletab;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * 占位符解析器：直接调用 PlaceholderAPI.setPlaceholders。
 * <p>
 * PlaceholderAPI 已声明为 compileOnly 依赖，无需反射。
 */
public final class PlaceholderResolver {

    private final boolean papiAvailable;

    public PlaceholderResolver() {
        this.papiAvailable = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    public String resolve(String input, Player player) {
        if (input == null || input.isEmpty() || !input.contains("%")) {
            return input == null ? "" : input;
        }
        if (papiAvailable && player != null) {
            try {
                return PlaceholderAPI.setPlaceholders(player, input);
            } catch (Throwable ignored) {
            }
        }
        return input;
    }
}

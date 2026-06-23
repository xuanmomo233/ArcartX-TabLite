package com.example.simpletab;

import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端刷新限流器。
 * 按 (playerUuid) 维度在滑动窗口内限制刷新次数。
 */
public final class RefreshGuard {

    private final GuardConfig config;
    private final Map<UUID, GuardState> states = new ConcurrentHashMap<>();

    public RefreshGuard(GuardConfig config) {
        this.config = config != null ? config : GuardConfig.disabled();
    }

    public boolean allow(Player player) {
        if (player == null) return false;
        if (!config.enabled) return true;
        long now = System.currentTimeMillis();
        GuardState state = states.computeIfAbsent(player.getUniqueId(), k -> new GuardState());
        synchronized (state) {
            state.prune(now, config.windowMs);
            if (state.hits.size() >= config.maxHits) {
                if (config.mode == Mode.NOTIFY && (state.lastNotify <= 0L || now - state.lastNotify >= config.notifyCooldownMs)) {
                    player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', config.notifyMessage));
                    state.lastNotify = now;
                }
                return false;
            }
            state.hits.addLast(now);
            state.lastTouch = now;
            return true;
        }
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        long retention = Math.max(config.windowMs, config.notifyCooldownMs);
        states.entrySet().removeIf(e -> {
            GuardState s = e.getValue();
            synchronized (s) {
                s.prune(now, config.windowMs);
                return s.hits.isEmpty() && now - Math.max(s.lastTouch, s.lastNotify) > retention;
            }
        });
    }

    public void clear() {
        states.clear();
    }

    public enum Mode { SILENT, NOTIFY }

    public record GuardConfig(boolean enabled, long windowMs, int maxHits, Mode mode,
                              String notifyMessage, long notifyCooldownMs) {
        public static GuardConfig disabled() {
            return new GuardConfig(false, 1500L, 1, Mode.SILENT, "", 3000L);
        }
    }

    private static final class GuardState {
        final ArrayDeque<Long> hits = new ArrayDeque<>();
        long lastNotify;
        long lastTouch;

        void prune(long now, long windowMs) {
            while (!hits.isEmpty() && now - hits.peekFirst() >= windowMs) {
                hits.removeFirst();
            }
        }
    }
}

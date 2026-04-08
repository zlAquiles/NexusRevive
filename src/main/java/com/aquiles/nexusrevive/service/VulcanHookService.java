package com.aquiles.nexusrevive.service;

import com.aquiles.nexusrevive.NexusRevivePlugin;
import com.aquiles.nexusrevive.config.PluginSettings;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Method;
import java.util.Locale;

public final class VulcanHookService {
    private final NexusRevivePlugin plugin;
    private final Listener bridgeListener = new Listener() {
    };
    private boolean registered;

    public VulcanHookService(NexusRevivePlugin plugin) {
        this.plugin = plugin;
    }

    public void registerIfAvailable() {
        if (registered || !plugin.getServer().getPluginManager().isPluginEnabled("Vulcan")) {
            return;
        }

        registerEvent("me.frep.vulcan.api.event.VulcanFlagEvent", VulcanEventKind.FLAG);
        registerEvent("me.frep.vulcan.api.event.VulcanPostFlagEvent", VulcanEventKind.POST_FLAG);
        registerEvent("me.frep.vulcan.api.event.VulcanSetbackEvent", VulcanEventKind.SETBACK);
        registerEvent("me.frep.vulcan.api.event.VulcanPunishEvent", VulcanEventKind.PUNISH);
        registered = true;
    }

    @SuppressWarnings("unchecked")
    private void registerEvent(String className, VulcanEventKind kind) {
        try {
            Class<?> rawClass = Class.forName(className);
            if (!Event.class.isAssignableFrom(rawClass)) {
                return;
            }

            PluginManager pluginManager = plugin.getServer().getPluginManager();
            pluginManager.registerEvent(
                    (Class<? extends Event>) rawClass,
                    bridgeListener,
                    EventPriority.LOWEST,
                    (listener, event) -> handleEvent(event, kind),
                    plugin,
                    true
            );
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void handleEvent(Event event, VulcanEventKind kind) {
        PluginSettings.VulcanHook settings = plugin.getPluginSettings().hooks().vulcan();
        if (!settings.enabled()) {
            return;
        }

        Player player = extractPlayer(event);
        if (player == null || !plugin.getDownedService().isDowned(player)) {
            return;
        }

        if (kind == VulcanEventKind.SETBACK && !settings.cancelSetbacksWhileDowned()) {
            return;
        }

        if (kind == VulcanEventKind.PUNISH && !settings.cancelPunishmentsWhileDowned()) {
            return;
        }

        if ((kind == VulcanEventKind.FLAG || kind == VulcanEventKind.POST_FLAG || kind == VulcanEventKind.PUNISH)
                && (!settings.ignoreMovementChecksWhileDowned() || !isMovementRelated(event))) {
            return;
        }

        setCancelled(event, true);
    }

    private Player extractPlayer(Object event) {
        Object result = invokeNoArg(event, "getPlayer");
        return result instanceof Player player ? player : null;
    }

    private boolean isMovementRelated(Object event) {
        Object check = invokeNoArg(event, "getCheck");
        if (check == null) {
            return true;
        }

        String category = valueOf(invokeNoArg(check, "getCategory"));
        String name = valueOf(invokeNoArg(check, "getName"));
        String displayName = valueOf(invokeNoArg(check, "getDisplayName"));
        String description = valueOf(invokeNoArg(check, "getDescription"));
        String info = valueOf(invokeNoArg(event, "getInfo"));

        String combined = String.join(
                " ",
                category.toLowerCase(Locale.ROOT),
                name.toLowerCase(Locale.ROOT),
                displayName.toLowerCase(Locale.ROOT),
                description.toLowerCase(Locale.ROOT),
                info.toLowerCase(Locale.ROOT)
        );

        return combined.contains("movement")
                || combined.contains("jump")
                || combined.contains("motion")
                || combined.contains("speed")
                || combined.contains("velocity")
                || combined.contains("fly");
    }

    private void setCancelled(Object event, boolean cancelled) {
        try {
            Method method = event.getClass().getMethod("setCancelled", boolean.class);
            method.invoke(event, cancelled);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private Object invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private String valueOf(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private enum VulcanEventKind {
        FLAG,
        POST_FLAG,
        SETBACK,
        PUNISH
    }
}

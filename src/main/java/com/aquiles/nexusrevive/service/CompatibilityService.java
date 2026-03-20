package com.aquiles.nexusrevive.service;

import com.aquiles.nexusrevive.NexusRevivePlugin;
import com.aquiles.nexusrevive.config.PluginSettings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;

public final class CompatibilityService {
    private static final String WORLD_GUARD_REVIVE_FLAG = "nexus-revive";
    private static final String WORLD_GUARD_CARRY_FLAG = "nexus-revive-carry";
    private static final String WORLD_GUARD_LOOT_FLAG = "nexus-revive-loot";
    private final NexusRevivePlugin plugin;
    private Object vaultEconomy;

    public CompatibilityService(NexusRevivePlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public static void registerWorldGuardFlagEarly(NexusRevivePlugin plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("WorldGuard") == null) {
            return;
        }

        try {
            Object flagRegistry = getWorldGuardFlagRegistryStatic();
            if (flagRegistry == null) {
                return;
            }

            Class<?> flagClass = Class.forName("com.sk89q.worldguard.protection.flags.Flag");
            Class<?> stateFlagClass = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
            Method registerMethod = flagRegistry.getClass().getMethod("register", flagClass);
            registerWorldGuardStateFlag(flagRegistry, stateFlagClass, registerMethod, WORLD_GUARD_REVIVE_FLAG);
            registerWorldGuardStateFlag(flagRegistry, stateFlagClass, registerMethod, WORLD_GUARD_CARRY_FLAG);
            registerWorldGuardStateFlag(flagRegistry, stateFlagClass, registerMethod, WORLD_GUARD_LOOT_FLAG);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void reload() {
        vaultEconomy = null;

        if (plugin.getPluginSettings().hooks().vault().enabled()
                && plugin.getServer().getPluginManager().isPluginEnabled("Vault")) {
            try {
                Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
                RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) economyClass);
                if (registration != null) {
                    vaultEconomy = registration.getProvider();
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        registerWorldGuardReviveFlag();
    }

    public boolean canDown(Player victim, Player attacker) {
        if (attacker == null) {
            return true;
        }

        PluginSettings.Hooks hooks = plugin.getPluginSettings().hooks();
        return !hooks.deluxeCombat().enabled()
                || !hooks.deluxeCombat().respectPvpProtection()
                || !hasDeluxeProtection(victim);
    }

    public BlockReason canStartRevive(Player reviver, Player victim) {
        PluginSettings.Hooks hooks = plugin.getPluginSettings().hooks();

        if (hooks.worldGuard().enabled()
                && !isReviveAllowedInRegion(reviver, victim.getLocation())) {
            return BlockReason.REGION_BLOCKED;
        }

        if (hooks.deluxeCombat().enabled()
                && !hooks.deluxeCombat().allowReviveInCombat()
                && (isInDeluxeCombat(reviver) || isInDeluxeCombat(victim))) {
            return BlockReason.IN_COMBAT;
        }

        if (!hasEnoughFunds(reviver, hooks.vault().reviveCost())) {
            return BlockReason.NO_MONEY;
        }

        return BlockReason.NONE;
    }

    public BlockReason canCarry(Player picker, Player victim) {
        PluginSettings.Hooks hooks = plugin.getPluginSettings().hooks();

        if (hooks.worldGuard().enabled()
                && !isCarryAllowedInRegion(picker, victim.getLocation())) {
            return BlockReason.REGION_BLOCKED;
        }

        if (hooks.deluxeCombat().enabled()
                && !hooks.deluxeCombat().allowCarryInCombat()
                && (isInDeluxeCombat(picker) || isInDeluxeCombat(victim))) {
            return BlockReason.IN_COMBAT;
        }

        return BlockReason.NONE;
    }

    public BlockReason canLoot(Player robber, Player victim) {
        PluginSettings.Hooks hooks = plugin.getPluginSettings().hooks();

        if (hooks.worldGuard().enabled()
                && !isLootAllowedInRegion(robber, victim.getLocation())) {
            return BlockReason.REGION_BLOCKED;
        }

        return BlockReason.NONE;
    }

    public boolean chargeRevive(Player reviver) {
        double amount = plugin.getPluginSettings().hooks().vault().reviveCost();
        if (amount <= 0.0D || vaultEconomy == null) {
            return true;
        }
        if (!hasEnoughFunds(reviver, amount)) {
            return false;
        }

        try {
            Object response = invokeEconomyMethod("withdrawPlayer", reviver, amount);
            if (response == null) {
                return true;
            }

            Method successMethod = response.getClass().getMethod("transactionSuccess");
            Object success = successMethod.invoke(response);
            return success instanceof Boolean flag && flag;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public String reviveCostText() {
        return String.format(Locale.US, "%.2f", plugin.getPluginSettings().hooks().vault().reviveCost());
    }

    private boolean hasEnoughFunds(Player player, double amount) {
        if (!plugin.getPluginSettings().hooks().vault().enabled() || amount <= 0.0D) {
            return true;
        }
        if (vaultEconomy == null) {
            return true;
        }

        try {
            Object result = invokeEconomyMethod("has", player, amount);
            return result instanceof Boolean flag && flag;
        } catch (ReflectiveOperationException ignored) {
            return true;
        }
    }

    private Object invokeEconomyMethod(String methodName, Player player, double amount) throws ReflectiveOperationException {
        for (Method method : vaultEconomy.getClass().getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            if (method.getParameterCount() != 2) {
                continue;
            }

            Class<?>[] parameterTypes = method.getParameterTypes();
            if ((parameterTypes[0].isAssignableFrom(player.getClass())
                    || parameterTypes[0].getName().equals("org.bukkit.OfflinePlayer")
                    || parameterTypes[0] == String.class)
                    && (parameterTypes[1] == double.class || parameterTypes[1] == Double.class)) {
                Object target = parameterTypes[0] == String.class ? player.getName() : player;
                return method.invoke(vaultEconomy, target, amount);
            }
        }

        return null;
    }

    private boolean isInDeluxeCombat(Player player) {
        return invokeDeluxeCombatBoolean("isInCombat", player);
    }

    private boolean hasDeluxeProtection(Player player) {
        return invokeDeluxeCombatBoolean("hasProtection", player);
    }

    private boolean invokeDeluxeCombatBoolean(String methodName, Player player) {
        if (!plugin.getPluginSettings().hooks().deluxeCombat().enabled()) {
            return false;
        }
        if (!plugin.getServer().getPluginManager().isPluginEnabled("DeluxeCombat")) {
            return false;
        }

        try {
            Class<?> apiClass = Class.forName("nl.marido.deluxecombat.api.DeluxeCombatAPI");
            Method method = apiClass.getMethod(methodName, Player.class);
            Object target = Modifier.isStatic(method.getModifiers()) ? null : apiClass.getDeclaredConstructor().newInstance();
            Object result = method.invoke(target, player);
            return result instanceof Boolean flag && flag;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private boolean isReviveAllowedInRegion(Player actor, Location location) {
        return isWorldGuardStateFlagAllowed(actor, location, WORLD_GUARD_REVIVE_FLAG);
    }

    private boolean isCarryAllowedInRegion(Player actor, Location location) {
        return isWorldGuardStateFlagAllowed(actor, location, WORLD_GUARD_CARRY_FLAG);
    }

    private boolean isLootAllowedInRegion(Player actor, Location location) {
        return isWorldGuardStateFlagAllowed(actor, location, WORLD_GUARD_LOOT_FLAG);
    }

    private boolean isWorldGuardStateFlagAllowed(Player actor, Location location, String flagName) {
        if (!plugin.getPluginSettings().hooks().worldGuard().enabled()) {
            return true;
        }
        if (plugin.getServer().getPluginManager().getPlugin("WorldGuard") == null) {
            return true;
        }

        try {
            Object targetFlag = getWorldGuardFlag(flagName);
            if (targetFlag == null) {
                return true;
            }

            Object protectionQuery = getWorldGuardRegionQuery();
            Object adaptedLocation = adaptWorldGuardLocation(location);
            if (protectionQuery == null || adaptedLocation == null) {
                return true;
            }

            Object localPlayer = actor == null ? null : wrapWorldGuardPlayer(actor);
            Boolean queryStateResult = invokeWorldGuardFlagQuery(protectionQuery, "queryState", adaptedLocation, localPlayer, targetFlag, true);
            if (queryStateResult != null) {
                return queryStateResult;
            }
            Boolean testStateResult = invokeWorldGuardFlagQuery(protectionQuery, "testState", adaptedLocation, localPlayer, targetFlag, false);
            if (testStateResult != null) {
                return testStateResult;
            }
        } catch (ReflectiveOperationException ignored) {
        }

        return true;
    }

    private void registerWorldGuardReviveFlag() {
        if (!plugin.getPluginSettings().hooks().worldGuard().enabled()) {
            return;
        }
        registerWorldGuardFlagEarly(plugin);
    }

    private Object getWorldGuardReviveFlag() throws ReflectiveOperationException {
        return getWorldGuardFlag(WORLD_GUARD_REVIVE_FLAG);
    }

    private Object getWorldGuardFlag(String flagName) throws ReflectiveOperationException {
        return getWorldGuardFlagStatic(getWorldGuardFlagRegistryStatic(), flagName);
    }

    private Boolean invokeWorldGuardFlagQuery(
            Object protectionQuery,
            String methodName,
            Object adaptedLocation,
            Object localPlayer,
            Object flag,
            boolean nullMeansAllowed
    ) throws ReflectiveOperationException {
        for (Method method : protectionQuery.getClass().getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }

            Object[] arguments = buildWorldGuardArguments(method.getParameterTypes(), adaptedLocation, localPlayer, flag);
            if (arguments == null) {
                continue;
            }

            Object result = method.invoke(protectionQuery, arguments);
            if (result == null) {
                return nullMeansAllowed ? Boolean.TRUE : null;
            }
            if (result instanceof Boolean allowed) {
                return allowed;
            }

            String state = result.toString();
            if ("ALLOW".equalsIgnoreCase(state)) {
                return true;
            }
            if ("DENY".equalsIgnoreCase(state)) {
                return false;
            }
            return nullMeansAllowed ? Boolean.TRUE : null;
        }
        return null;
    }

    private Object[] buildWorldGuardArguments(Class<?>[] parameterTypes, Object adaptedLocation, Object localPlayer, Object flag) {
        Object[] arguments = new Object[parameterTypes.length];

        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            String typeName = parameterType.getName();

            if (adaptedLocation != null && parameterType.isInstance(adaptedLocation)) {
                arguments[i] = adaptedLocation;
                continue;
            }

            if (typeName.contains("LocalPlayer") || typeName.contains("RegionAssociable")) {
                arguments[i] = localPlayer;
                continue;
            }

            if (parameterType.isArray()) {
                Object array = Array.newInstance(parameterType.getComponentType(), 1);
                try {
                    Array.set(array, 0, flag);
                } catch (IllegalArgumentException exception) {
                    return null;
                }
                arguments[i] = array;
                continue;
            }

            if (parameterType.isInstance(flag)) {
                arguments[i] = flag;
                continue;
            }

            return null;
        }

        return arguments;
    }

    private static Object getWorldGuardFlagRegistryStatic() throws ReflectiveOperationException {
        Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
        Object worldGuard = invokeStaticNoArg(worldGuardClass, "getInstance");
        if (worldGuard == null) {
            return null;
        }
        return invokeNoArg(worldGuard, "getFlagRegistry");
    }

    private static Object getWorldGuardFlagStatic(Object flagRegistry, String flagName) throws ReflectiveOperationException {
        if (flagRegistry == null) {
            return null;
        }

        for (Method method : flagRegistry.getClass().getMethods()) {
            if (!method.getName().equals("get") || method.getParameterCount() != 1 || method.getParameterTypes()[0] != String.class) {
                continue;
            }
            return method.invoke(flagRegistry, flagName);
        }
        return null;
    }

    private static void registerWorldGuardStateFlag(Object flagRegistry, Class<?> stateFlagClass, Method registerMethod, String flagName)
            throws ReflectiveOperationException {
        if (getWorldGuardFlagStatic(flagRegistry, flagName) != null) {
            return;
        }
        Object stateFlag = stateFlagClass.getConstructor(String.class, boolean.class)
                .newInstance(flagName, true);
        registerMethod.invoke(flagRegistry, stateFlag);
    }

    private Object getWorldGuardRegionQuery() throws ReflectiveOperationException {
        Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
        Object worldGuard = invokeStaticNoArg(worldGuardClass, "getInstance");
        if (worldGuard == null) {
            return null;
        }

        Object platform = invokeNoArg(worldGuard, "getPlatform");
        if (platform == null) {
            return null;
        }

        Object regionContainer = invokeNoArg(platform, "getRegionContainer");
        return regionContainer == null ? null : invokeNoArg(regionContainer, "createQuery");
    }

    private Object adaptWorldGuardLocation(Location location) throws ReflectiveOperationException {
        Class<?> bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
        return invokeCompatibleStaticMethod(bukkitAdapterClass, "adapt", location);
    }

    private Object wrapWorldGuardPlayer(Player player) throws ReflectiveOperationException {
        Class<?> wgPluginClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
        Object wgPlugin = invokeStaticNoArg(wgPluginClass, "inst");
        if (wgPlugin == null) {
            wgPlugin = invokeStaticNoArg(wgPluginClass, "getInstance");
        }
        if (wgPlugin == null) {
            return null;
        }
        return invokeCompatibleMethod(wgPlugin, "wrapPlayer", player);
    }

    private static Object invokeCompatibleMethod(Object target, String methodName, Object argument) throws ReflectiveOperationException {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
                continue;
            }
            if (!method.getParameterTypes()[0].isAssignableFrom(argument.getClass())) {
                continue;
            }
            return method.invoke(target, argument);
        }
        return null;
    }

    private static Object invokeCompatibleStaticMethod(Class<?> type, String methodName, Object argument) throws ReflectiveOperationException {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 1 || !Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (!method.getParameterTypes()[0].isAssignableFrom(argument.getClass())) {
                continue;
            }
            return method.invoke(null, argument);
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private static Object invokeStaticNoArg(Class<?> type, String methodName) {
        try {
            Method method = type.getMethod(methodName);
            if (!Modifier.isStatic(method.getModifiers())) {
                return null;
            }
            return method.invoke(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    public enum BlockReason {
        NONE,
        REGION_BLOCKED,
        IN_COMBAT,
        NO_MONEY
    }
}


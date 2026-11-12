package dev.war.pluginRules.platform;

import dev.war.pluginRules.PluginRules;

/** Picks the platform implementation: the Paper one only if the server really is Paper. */
public final class Platforms {

    private Platforms() {
    }

    public static boolean has(String className) {
        try {
            Class.forName(className, false, Platforms.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    public static Platform detect(PluginRules plugin) {
        if (has("io.papermc.paper.event.player.AsyncChatEvent")) {
            try {
                boolean folia = has("io.papermc.paper.threadedregions.RegionizedServer");
                Class<?> c = Class.forName("dev.war.pluginRules.paper.PaperPlatform");
                return (Platform) c.getConstructor(PluginRules.class, boolean.class).newInstance(plugin, folia);
            } catch (Throwable t) {
                plugin.getLogger().warning("Paper support could not be loaded, using the Spigot code path: " + t);
            }
        }
        return new BukkitPlatform(plugin);
    }
}

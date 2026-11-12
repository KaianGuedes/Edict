package dev.war.edict.platform;

import dev.war.edict.Edict;

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

    public static Platform detect(Edict plugin) {
        if (has("io.papermc.paper.event.player.AsyncChatEvent")) {
            try {
                boolean folia = has("io.papermc.paper.threadedregions.RegionizedServer");
                Class<?> c = Class.forName("dev.war.edict.paper.PaperPlatform");
                return (Platform) c.getConstructor(Edict.class, boolean.class).newInstance(plugin, folia);
            } catch (Throwable t) {
                plugin.getLogger().warning("Paper support could not be loaded, using the Spigot code path: " + t);
            }
        }
        return new BukkitPlatform(plugin);
    }
}

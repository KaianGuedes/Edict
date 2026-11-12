package dev.war.edict.config;

import dev.war.edict.Edict;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds the overworld that matches an End world: the explicit mapping in config.yml first, then the
 * Bukkit/Multiverse naming (world_the_end to world, name_the_end to name), then the main world with a warning.
 */
public final class WorldResolver {

    public static final String END_SUFFIX = "_the_end";

    private final Edict plugin;
    private final Set<String> warned = new HashSet<>();

    public WorldResolver(Edict plugin) {
        this.plugin = plugin;
    }

    public void clearWarnings() {
        warned.clear();
    }

    public World overworldFor(World end) {
        // 1. explicit mapping
        ConfigurationSection map = plugin.getConfig().getConfigurationSection("corresponding-overworld");
        if (map != null && map.isString(end.getName())) {
            String target = map.getString(end.getName());
            World w = target == null ? null : Bukkit.getWorld(target);
            if (w != null && w != end) return w;
            warnOnce("map:" + end.getName(), "corresponding-overworld maps '" + end.getName() + "' to '" + target
                    + "' but that world is not loaded; falling back to the naming convention.");
        }
        // 2. naming convention
        String name = end.getName();
        if (name.endsWith(END_SUFFIX) && name.length() > END_SUFFIX.length()) {
            World w = Bukkit.getWorld(name.substring(0, name.length() - END_SUFFIX.length()));
            if (w != null && w.getEnvironment() == World.Environment.NORMAL) return w;
        }
        // 3. main world
        List<World> worlds = Bukkit.getWorlds();
        World main = null;
        for (World w : worlds) {
            if (w.getEnvironment() == World.Environment.NORMAL) {
                main = w;
                break;
            }
        }
        if (main == null && !worlds.isEmpty()) main = worlds.get(0);
        if (main != null) {
            warnOnce("fallback:" + name, "No overworld matches the End world '" + name + "'; using the main world '"
                    + main.getName() + "'. Add it under corresponding-overworld in config.yml to choose another one.");
        }
        return main;
    }

    private void warnOnce(String key, String message) {
        if (warned.add(key)) plugin.getLogger().warning(message);
    }

    /** Logs a warning only once per key. */
    public void warn(String key, String message) {
        warnOnce(key, message);
    }
}

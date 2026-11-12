package dev.war.pluginRules.config;

import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.List;
import java.util.Locale;

/**
 * Where a rule value applies: everywhere (global), one environment group, or one world.
 * Resolution order is world, then environment group, then global.
 */
public record Scope(Kind kind, String name) {

    public enum Kind { GLOBAL, ENVIRONMENT, WORLD }

    public static final Scope GLOBAL = new Scope(Kind.GLOBAL, "global");
    public static final List<String> ENVIRONMENTS = List.of("overworld", "nether", "the_end");

    public static Scope environment(String name) {
        return new Scope(Kind.ENVIRONMENT, name);
    }

    public static Scope world(String name) {
        return new Scope(Kind.WORLD, name);
    }

    /** The environment group name of a world. */
    public static String groupOf(World world) {
        if (world == null) return null;
        switch (world.getEnvironment()) {
            case NETHER:
                return "nether";
            case THE_END:
                return "the_end";
            case NORMAL:
                return "overworld";
            default:
                return null;
        }
    }

    /** Parses {@code global}, {@code overworld}, {@code nether}, {@code the_end}, {@code world:<name>} or a loaded world name. */
    public static Scope parse(String input) {
        if (input == null) return null;
        String s = input.trim();
        String low = s.toLowerCase(Locale.ROOT);
        if (low.equals("global")) return GLOBAL;
        if (ENVIRONMENTS.contains(low)) return environment(low);
        if (low.startsWith("world:") && s.length() > 6) return world(s.substring(6));
        if (Bukkit.getWorld(s) != null) return world(s);
        return null;
    }

    /** The text accepted by {@link #parse(String)}. */
    public String id() {
        return kind == Kind.WORLD ? "world:" + name : name;
    }

    /** Config path of the section that holds the rules of this scope. */
    public String path() {
        switch (kind) {
            case ENVIRONMENT:
                return "environments." + name;
            case WORLD:
                return "worlds." + name;
            default:
                return "rules";
        }
    }
}

package dev.war.edict.config;

import dev.war.edict.Edict;
import dev.war.edict.model.ParamDef;
import dev.war.edict.model.RuleDef;
import dev.war.edict.model.Rules;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * Holds every rule value per scope and resolves them: world override, then environment group, then global,
 * then the built-in default. Each parameter resolves on its own, so a world can override only one of them.
 */
public final class RuleStore {

    private final Edict plugin;
    private final Map<String, Object> global = new HashMap<>();
    private final Map<String, Map<String, Object>> environments = new HashMap<>();
    private final Map<String, Map<String, Object>> worlds = new HashMap<>();

    public RuleStore(Edict plugin) {
        this.plugin = plugin;
    }

    private static String key(String rule, String param) {
        return rule + "." + param;
    }

    /** (Re)reads all scopes from the plugin config. */
    public synchronized void load() {
        FileConfiguration cfg = plugin.getConfig();
        global.clear();
        environments.clear();
        worlds.clear();

        readScope(cfg.getConfigurationSection("rules"), global, "rules");

        ConfigurationSection envs = cfg.getConfigurationSection("environments");
        if (envs != null) {
            for (String name : envs.getKeys(false)) {
                if (!Scope.ENVIRONMENTS.contains(name)) {
                    plugin.getLogger().warning("Unknown environment group '" + name + "' in config (use overworld, nether or the_end).");
                    continue;
                }
                Map<String, Object> values = new HashMap<>();
                readScope(envs.getConfigurationSection(name), values, "environments." + name);
                environments.put(name, values);
            }
        }

        ConfigurationSection ws = cfg.getConfigurationSection("worlds");
        if (ws != null) {
            for (String name : ws.getKeys(false)) {
                Map<String, Object> values = new HashMap<>();
                readScope(ws.getConfigurationSection(name), values, "worlds." + name);
                worlds.put(name, values);
            }
        }
    }

    private void readScope(ConfigurationSection section, Map<String, Object> out, String where) {
        if (section == null) return;
        for (String ruleId : section.getKeys(false)) {
            RuleDef rule = Rules.get(ruleId);
            if (rule == null) {
                plugin.getLogger().warning("Unknown rule '" + ruleId + "' in " + where + ", ignored.");
                continue;
            }
            Object raw = section.get(ruleId);
            if (raw instanceof Boolean) {
                // short form: rule: false
                out.put(key(ruleId, RuleDef.ENABLED), raw);
                continue;
            }
            ConfigurationSection sub = section.getConfigurationSection(ruleId);
            if (sub == null) {
                plugin.getLogger().warning("Invalid value for " + where + "." + ruleId + ", ignored.");
                continue;
            }
            for (String paramKey : sub.getKeys(false)) {
                ParamDef p = rule.param(paramKey);
                if (p == null) {
                    plugin.getLogger().warning("Unknown parameter '" + paramKey + "' for rule " + ruleId + " in " + where + ", ignored.");
                    continue;
                }
                Object v = p.coerce(sub.get(paramKey));
                if (v == null) {
                    plugin.getLogger().warning("Invalid value '" + sub.get(paramKey) + "' for " + where + "." + ruleId + "." + paramKey + ", ignored.");
                    continue;
                }
                out.put(key(ruleId, paramKey), v);
            }
        }
    }

    private static ParamDef def(String rule, String param) {
        RuleDef r = Rules.get(rule);
        ParamDef p = r == null ? null : r.param(param);
        if (p == null) throw new IllegalArgumentException("Unknown rule parameter " + rule + "." + param);
        return p;
    }

    /** Resolves the value that applies in a world (null world means global only). */
    public synchronized Resolved resolve(String rule, String param, World world) {
        ParamDef p = def(rule, param);
        String k = key(rule, param);
        if (world != null) {
            Map<String, Object> w = worlds.get(world.getName());
            if (w != null && w.containsKey(k)) return new Resolved(w.get(k), Scope.world(world.getName()));
            String group = Scope.groupOf(world);
            if (group != null) {
                Map<String, Object> e = environments.get(group);
                if (e != null && e.containsKey(k)) return new Resolved(e.get(k), Scope.environment(group));
            }
        }
        if (global.containsKey(k)) return new Resolved(global.get(k), Scope.GLOBAL);
        return new Resolved(p.def(), null);
    }

    /** Resolves what the given scope shows: its own value, else the next wider one. */
    public synchronized Resolved resolve(String rule, String param, Scope scope) {
        ParamDef p = def(rule, param);
        String k = key(rule, param);
        if (scope.kind() == Scope.Kind.WORLD) {
            Map<String, Object> w = worlds.get(scope.name());
            if (w != null && w.containsKey(k)) return new Resolved(w.get(k), scope);
            World loaded = plugin.getServer().getWorld(scope.name());
            String group = Scope.groupOf(loaded);
            if (group != null) {
                Map<String, Object> e = environments.get(group);
                if (e != null && e.containsKey(k)) return new Resolved(e.get(k), Scope.environment(group));
            }
        } else if (scope.kind() == Scope.Kind.ENVIRONMENT) {
            Map<String, Object> e = environments.get(scope.name());
            if (e != null && e.containsKey(k)) return new Resolved(e.get(k), scope);
        }
        if (global.containsKey(k)) return new Resolved(global.get(k), Scope.GLOBAL);
        return new Resolved(p.def(), null);
    }

    /** True when the scope itself holds an explicit value for the parameter. */
    public synchronized boolean hasOwn(String rule, String param, Scope scope) {
        String k = key(rule, param);
        switch (scope.kind()) {
            case WORLD: {
                Map<String, Object> w = worlds.get(scope.name());
                return w != null && w.containsKey(k);
            }
            case ENVIRONMENT: {
                Map<String, Object> e = environments.get(scope.name());
                return e != null && e.containsKey(k);
            }
            default:
                return global.containsKey(k);
        }
    }

    public boolean bool(String rule, String param, World world) {
        return (Boolean) resolve(rule, param, world).value();
    }

    public int integer(String rule, String param, World world) {
        return (Integer) resolve(rule, param, world).value();
    }

    public String string(String rule, String param, World world) {
        return (String) resolve(rule, param, world).value();
    }

    public boolean enabled(String rule, World world) {
        return bool(rule, RuleDef.ENABLED, world);
    }

    private Map<String, Object> mapFor(Scope scope, boolean create) {
        switch (scope.kind()) {
            case WORLD:
                return create ? worlds.computeIfAbsent(scope.name(), n -> new HashMap<>()) : worlds.get(scope.name());
            case ENVIRONMENT:
                return create ? environments.computeIfAbsent(scope.name(), n -> new HashMap<>()) : environments.get(scope.name());
            default:
                return global;
        }
    }

    /** Sets a value in a scope and saves the config immediately. The value must already be of the right type. */
    public synchronized void set(String rule, String param, Object value, Scope scope) {
        def(rule, param);
        mapFor(scope, true).put(key(rule, param), value);
        plugin.getConfig().set(scope.path() + "." + rule + "." + param, value);
        plugin.saveConfig();
    }

    /**
     * Removes an override. For a non-global scope the value goes back to inheriting; for global it goes
     * back to the built-in default.
     */
    public synchronized void reset(String rule, String param, Scope scope) {
        ParamDef p = def(rule, param);
        Map<String, Object> m = mapFor(scope, false);
        if (m != null) m.remove(key(rule, param));
        if (scope.kind() == Scope.Kind.GLOBAL) {
            plugin.getConfig().set(scope.path() + "." + rule + "." + param, p.def());
            global.put(key(rule, param), p.def());
        } else {
            plugin.getConfig().set(scope.path() + "." + rule + "." + param, null);
        }
        plugin.saveConfig();
    }
}

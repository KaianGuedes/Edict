package dev.war.edict.i18n;

import dev.war.edict.Edict;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Translations from lang/&lt;code&gt;.yml. A file in the plugin folder overrides the one in the jar key by key,
 * and en_US is the last resort so a missing key never shows up as a raw key to a player.
 */
public final class Lang {

    public static final String DEFAULT = "en_US";
    public static final List<String> BUNDLED = List.of("en_US", "pt_BR");

    private final Edict plugin;
    private final Map<String, YamlConfiguration> jar = new LinkedHashMap<>();
    private final Map<String, YamlConfiguration> disk = new LinkedHashMap<>();
    private String configured = "auto";

    public Lang(Edict plugin) {
        this.plugin = plugin;
    }

    public void load() {
        jar.clear();
        disk.clear();
        configured = plugin.getConfig().getString("language", "auto");
        for (String code : BUNDLED) {
            YamlConfiguration y = read(plugin.getResource("lang/" + code + ".yml"));
            if (y != null) jar.put(code, y);
            File f = new File(plugin.getDataFolder(), "lang/" + code + ".yml");
            if (!f.exists()) {
                try {
                    plugin.saveResource("lang/" + code + ".yml", false);
                } catch (IllegalArgumentException ignored) {
                    // not bundled
                }
            }
            if (f.isFile()) {
                try (Reader r = new InputStreamReader(new java.io.FileInputStream(f), StandardCharsets.UTF_8)) {
                    disk.put(code, YamlConfiguration.loadConfiguration(r));
                } catch (IOException e) {
                    plugin.getLogger().warning("Could not read " + f.getName() + ": " + e.getMessage());
                }
            }
        }
        if (!BUNDLED.contains(configured) && !configured.equalsIgnoreCase("auto")) {
            plugin.getLogger().warning("Unknown language '" + configured + "' in config.yml, using auto.");
            configured = "auto";
        }
    }

    private static YamlConfiguration read(InputStream in) {
        if (in == null) return null;
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(r);
        } catch (IOException e) {
            return null;
        }
    }

    /** The language code to use for a sender. */
    public String localeOf(CommandSender sender) {
        if (!configured.equalsIgnoreCase("auto")) return configured;
        String raw = null;
        if (sender instanceof Player p) {
            try {
                raw = p.getLocale();
            } catch (Throwable ignored) {
                raw = null;
            }
        }
        if (raw == null || raw.isEmpty()) raw = Locale.getDefault().toString();
        return match(raw);
    }

    /** Maps a client locale such as {@code pt_br} or {@code pt-PT} to a bundled language code. */
    public static String match(String raw) {
        String s = raw.toLowerCase(Locale.ROOT).replace('-', '_');
        if (s.startsWith("pt")) return "pt_BR";
        return DEFAULT;
    }

    private String lookup(String code, String key) {
        YamlConfiguration d = disk.get(code);
        if (d != null && d.isString(key)) return d.getString(key);
        YamlConfiguration j = jar.get(code);
        if (j != null && j.isString(key)) return j.getString(key);
        return null;
    }

    /** Raw text for a key in a language, falling back to en_US, then to the key itself. */
    public String raw(String code, String key) {
        String v = lookup(code, key);
        if (v == null && !DEFAULT.equals(code)) v = lookup(DEFAULT, key);
        return v == null ? key : v;
    }

    private static String fill(String text, Object... pairs) {
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            text = text.replace("{" + pairs[i] + "}", String.valueOf(pairs[i + 1]));
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /** Colored text for a sender; pairs are placeholder name, value. */
    public String get(CommandSender sender, String key, Object... pairs) {
        return fill(raw(localeOf(sender), key), pairs);
    }

    public String get(String code, String key, Object... pairs) {
        return fill(raw(code, key), pairs);
    }

    /** A list entry (a YAML list or a single string split on newlines). */
    public List<String> list(CommandSender sender, String key, Object... pairs) {
        String code = localeOf(sender);
        List<String> out = new ArrayList<>();
        List<String> src = null;
        YamlConfiguration d = disk.get(code);
        if (d != null && d.isList(key)) src = d.getStringList(key);
        if (src == null) {
            YamlConfiguration j = jar.get(code);
            if (j != null && j.isList(key)) src = j.getStringList(key);
        }
        if (src == null && !DEFAULT.equals(code)) {
            YamlConfiguration j = jar.get(DEFAULT);
            if (j != null && j.isList(key)) src = j.getStringList(key);
        }
        if (src == null) {
            out.add(get(sender, key, pairs));
            return out;
        }
        for (String line : src) out.add(fill(line, pairs));
        return out;
    }

    public void send(CommandSender sender, String key, Object... pairs) {
        sender.sendMessage(get(sender, key, pairs));
    }

    /** All keys defined for a bundled language in the jar (scalar and list leaves). */
    public Set<String> keys(String code) {
        YamlConfiguration y = jar.get(code);
        if (y == null) return Collections.emptySet();
        return new TreeSet<>(y.getKeys(true).stream().filter(k -> !y.isConfigurationSection(k)).toList());
    }

    public String configured() {
        return configured;
    }
}

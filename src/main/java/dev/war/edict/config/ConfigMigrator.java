package dev.war.edict.config;

import dev.war.edict.Brand;
import dev.war.edict.Edict;
import dev.war.edict.model.RuleDef;
import dev.war.edict.model.Rules;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Upgrades a 0.1 config (flat {@code rules.<name>: true/false}) to the structured format.
 * The old file is kept as a backup and nothing the old file said is lost.
 */
public final class ConfigMigrator {

    public static final int CONFIG_VERSION = 2;

    private ConfigMigrator() {
    }

    /**
     * On the first start under the new name, copies plugins/PluginRules/config.yml (0.1) into this plugin's data
     * folder. The old folder is never touched. The normal migration then converts the copy.
     */
    public static void importLegacyFolder(Edict plugin) {
        File mine = new File(plugin.getDataFolder(), "config.yml");
        if (mine.exists()) return;
        File old = new File(plugin.getDataFolder().getParentFile(), Brand.LEGACY_NAME + "/config.yml");
        if (!old.isFile()) return;
        try {
            plugin.getDataFolder().mkdirs();
            Files.copy(old.toPath(), mine.toPath());
            plugin.getLogger().info("Imported the config of " + Brand.LEGACY_NAME + " (the old folder is left untouched).");
        } catch (IOException e) {
            plugin.getLogger().warning("Could not import the old " + Brand.LEGACY_NAME + " config: " + e.getMessage());
        }
    }

    /** Migrates config.yml in the data folder when needed. Returns true if a migration happened. */
    public static boolean migrate(Edict plugin) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.isFile()) return false;
        YamlConfiguration old = YamlConfiguration.loadConfiguration(file);
        if (old.getInt("config-version", 1) >= CONFIG_VERSION) return false;

        Map<String, Boolean> legacy = new LinkedHashMap<>();
        ConfigurationSection rules = old.getConfigurationSection("rules");
        if (rules != null) {
            for (String id : rules.getKeys(false)) {
                if (rules.isBoolean(id)) legacy.put(id, rules.getBoolean(id));
            }
        }

        try {
            File backup = backupFile(file);
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            plugin.getLogger().info("Migrating config.yml from the PluginRules 0.1 format to the current format (backup: " + backup.getName() + ").");

            String text = defaultConfigText(plugin);
            for (Map.Entry<String, Boolean> e : legacy.entrySet()) {
                if (Rules.get(e.getKey()) == null) {
                    plugin.getLogger().warning("Unknown rule '" + e.getKey() + "' in the old config, kept only in the backup.");
                    continue;
                }
                text = setEnabled(text, e.getKey(), e.getValue());
            }
            Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().severe("Config migration failed, the old config is left untouched: " + ex.getMessage());
            return false;
        }
    }

    private static File backupFile(File file) {
        File dir = file.getParentFile();
        File backup = new File(dir, "config.yml.v0.1.bak");
        int n = 2;
        while (backup.exists()) {
            backup = new File(dir, "config.yml.v0.1." + n + ".bak");
            n++;
        }
        return backup;
    }

    private static String defaultConfigText(Edict plugin) throws IOException {
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in == null) throw new IOException("config.yml is missing from the jar");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Replaces the first {@code enabled:} value after the rule's header, keeping every comment. */
    static String setEnabled(String text, String rule, boolean value) {
        Matcher m = Pattern.compile("(?m)^  " + Pattern.quote(rule) + ":\\s*$").matcher(text);
        if (!m.find()) return text;
        Matcher en = Pattern.compile("(?m)^(    " + Pattern.quote(RuleDef.ENABLED) + ": )(true|false)").matcher(text);
        if (!en.find(m.end())) return text;
        return text.substring(0, en.start(2)) + value + text.substring(en.end(2));
    }
}

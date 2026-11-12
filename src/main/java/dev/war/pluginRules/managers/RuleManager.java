package dev.war.pluginRules;

import org.bukkit.configuration.file.FileConfiguration;
import dev.war.physarum.PluginRules;

import java.util.HashMap;
import java.util.Map;

public class RuleManager {
    private final PluginRules plugin;
    private final Map<String, Boolean> booleanRules;

    public RuleManager(PluginRules plugin) {
        this.plugin = plugin;
        this.booleanRules = new HashMap<>();
        plugin.saveDefaultConfig();
        loadRules();
    }

    // Carrega as regras da config para a memória
    public void loadRules() {
        booleanRules.clear();
        FileConfiguration config = plugin.getConfig();

        // Define as regras padrão caso não existam no arquivo
        addDefaultRule("creeper_block_damage", true);

        // Carrega para o cache
        for (String key : config.getConfigurationSection("rules").getKeys(false)) {
            booleanRules.put(key, config.getBoolean("rules." + key));
        }
    }

    private void addDefaultRule(String name, boolean defaultValue) {
        if (!plugin.getConfig().contains("rules." + name)) {
            plugin.getConfig().set("rules." + name, defaultValue);
            plugin.saveConfig();
        }
    }

    // Retorna o valor da regra (rápido, direto do cache)
    public boolean getRule(String ruleName) {
        return booleanRules.getOrDefault(ruleName, true);
    }

    // Altera uma regra, salva no arquivo e atualiza o cache
    public void setRule(String ruleName, boolean value) {
        booleanRules.put(ruleName, value);
        plugin.getConfig().set("rules." + ruleName, value);
        plugin.saveConfig();
    }

    public Map<String, Boolean> getAllRules() {
        return booleanRules;
    }
}
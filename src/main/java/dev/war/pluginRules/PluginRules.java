package dev.war.pluginRules;

import dev.war.pluginRules.commands.PluginRulesCommand;
import dev.war.pluginRules.config.ConfigMigrator;
import dev.war.pluginRules.config.RuleStore;
import dev.war.pluginRules.config.WorldResolver;
import dev.war.pluginRules.gui.Gui;
import dev.war.pluginRules.gui.PromptManager;
import dev.war.pluginRules.i18n.Lang;
import dev.war.pluginRules.listeners.CreeperListener;
import dev.war.pluginRules.listeners.EndPortalListener;
import dev.war.pluginRules.listeners.EndermanListener;
import dev.war.pluginRules.listeners.GuiListener;
import dev.war.pluginRules.listeners.VoidFallListener;
import dev.war.pluginRules.platform.Platform;
import dev.war.pluginRules.platform.Platforms;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class PluginRules extends JavaPlugin {

    public static final String ADMIN_PERMISSION = "pluginrules.admin";

    private RuleStore store;
    private Lang lang;
    private Platform platform;
    private WorldResolver resolver;
    private PromptManager prompts;
    private Gui gui;
    private VoidFallListener voidFall;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ConfigMigrator.migrate(this);
        reloadConfig();

        store = new RuleStore(this);
        store.load();
        lang = new Lang(this);
        lang.load();
        platform = Platforms.detect(this);
        resolver = new WorldResolver(this);
        prompts = new PromptManager(this);
        gui = new Gui(this);

        PluginRulesCommand commandHandler = new PluginRulesCommand(this);
        PluginCommand command = getCommand("pluginrules");
        if (command != null) {
            command.setExecutor(commandHandler);
            command.setTabCompleter(commandHandler);
        }

        voidFall = new VoidFallListener(this);
        getServer().getPluginManager().registerEvents(new CreeperListener(this), this);
        getServer().getPluginManager().registerEvents(new EndermanListener(this), this);
        getServer().getPluginManager().registerEvents(new EndPortalListener(this), this);
        getServer().getPluginManager().registerEvents(voidFall, this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        platform.registerChatPrompt(prompts);

        getLogger().info(lang.get(getServer().getConsoleSender(), "log.enabled", "platform", platform.name()));
    }

    @Override
    public void onDisable() {
        if (lang != null) {
            getLogger().info(lang.get(getServer().getConsoleSender(), "log.disabled"));
        }
    }

    /** Re-reads config.yml and the language files. */
    public void reloadAll() {
        reloadConfig();
        store.load();
        lang.load();
        resolver.clearWarnings();
    }

    public RuleStore store() {
        return store;
    }

    public Lang lang() {
        return lang;
    }

    public Platform platform() {
        return platform;
    }

    public WorldResolver resolver() {
        return resolver;
    }

    public PromptManager prompts() {
        return prompts;
    }

    public Gui gui() {
        return gui;
    }

    public VoidFallListener voidFall() {
        return voidFall;
    }
}

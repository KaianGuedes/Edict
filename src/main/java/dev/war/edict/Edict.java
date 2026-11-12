package dev.war.edict;

import dev.war.edict.commands.EdictCommand;
import dev.war.edict.config.ConfigMigrator;
import dev.war.edict.config.RuleStore;
import dev.war.edict.config.WorldResolver;
import dev.war.edict.gui.Gui;
import dev.war.edict.gui.PromptManager;
import dev.war.edict.i18n.Lang;
import dev.war.edict.listeners.CreeperListener;
import dev.war.edict.listeners.EndPortalListener;
import dev.war.edict.listeners.EndermanListener;
import dev.war.edict.listeners.GuiListener;
import dev.war.edict.listeners.VoidFallListener;
import dev.war.edict.platform.Platform;
import dev.war.edict.platform.Platforms;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class Edict extends JavaPlugin {

    public static final String ADMIN_PERMISSION = "edict.admin";
    /** Permission of 0.1 (PluginRules); still accepted. */
    public static final String LEGACY_ADMIN_PERMISSION = "pluginrules.admin";

    public static boolean isAdmin(org.bukkit.command.CommandSender sender) {
        return sender.hasPermission(ADMIN_PERMISSION) || sender.hasPermission(LEGACY_ADMIN_PERMISSION);
    }

    private RuleStore store;
    private Lang lang;
    private Platform platform;
    private WorldResolver resolver;
    private PromptManager prompts;
    private Gui gui;
    private VoidFallListener voidFall;

    @Override
    public void onEnable() {
        ConfigMigrator.importLegacyFolder(this);
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

        EdictCommand commandHandler = new EdictCommand(this);
        PluginCommand command = getCommand("edict");
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

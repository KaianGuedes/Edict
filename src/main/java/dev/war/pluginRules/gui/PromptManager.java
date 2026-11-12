package dev.war.pluginRules.gui;

import dev.war.pluginRules.PluginRules;
import dev.war.pluginRules.config.Scope;
import dev.war.pluginRules.model.ParamDef;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Chat prompts for exact values: the next chat line of the player is taken as the new value. */
public final class PromptManager {

    private record Prompt(String rule, ParamDef param, Scope scope) {
    }

    private final PluginRules plugin;
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

    public PromptManager(PluginRules plugin) {
        this.plugin = plugin;
    }

    public void start(Player player, String rule, ParamDef param, Scope scope) {
        prompts.put(player.getUniqueId(), new Prompt(rule, param, scope));
        player.closeInventory();
        plugin.lang().send(player, "prompt.ask", "param", plugin.lang().get(player, "param." + rule + "." + param.key() + ".name"));
    }

    public boolean pending(UUID id) {
        return prompts.containsKey(id);
    }

    public void cancel(UUID id) {
        prompts.remove(id);
    }

    /** Called from the chat event (any thread). Returns true if the message was a prompt answer. */
    public boolean consume(Player player, String message) {
        Prompt prompt = prompts.remove(player.getUniqueId());
        if (prompt == null) return false;
        plugin.platform().runForPlayer(player, () -> apply(player, prompt, message));
        return true;
    }

    /** Applies an answer; separated from the chat event so tests can call it directly. */
    public void apply(Player player, String rule, ParamDef param, Scope scope, String message) {
        apply(player, new Prompt(rule, param, scope), message);
    }

    private void apply(Player player, Prompt prompt, String message) {
        String text = message.trim();
        if (text.equalsIgnoreCase("cancel") || text.equalsIgnoreCase("cancelar")) {
            plugin.lang().send(player, "prompt.cancelled");
        } else {
            try {
                Object value = prompt.param().parse(text);
                plugin.store().set(prompt.rule(), prompt.param().key(), value, prompt.scope());
                plugin.lang().send(player, "prompt.saved", "param", prompt.param().key(), "value", value);
            } catch (IllegalArgumentException e) {
                plugin.lang().send(player, e.getMessage(), "min", prompt.param().min(), "max", prompt.param().max());
            }
        }
        plugin.gui().openRule(player, prompt.rule(), prompt.scope());
    }
}

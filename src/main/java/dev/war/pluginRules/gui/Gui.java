package dev.war.pluginRules.gui;

import dev.war.pluginRules.PluginRules;
import dev.war.pluginRules.config.Resolved;
import dev.war.pluginRules.config.Scope;
import dev.war.pluginRules.model.ParamDef;
import dev.war.pluginRules.model.ParamType;
import dev.war.pluginRules.model.RuleDef;
import dev.war.pluginRules.model.Rules;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The chest GUI: main menu (rules), rule menu (parameters) and scope selector. Every change goes through the
 * rule store and is saved to config.yml right away.
 */
public final class Gui {

    private static final int MAIN_SIZE = 45;
    private static final int RULE_SIZE = 45;
    private static final int SCOPE_SIZE = 54;
    private static final int[] RULE_SLOTS = {19, 21, 23, 25};
    private static final int SLOT_SCOPE = 4;
    private static final int SLOT_CLOSE = 40;
    private static final int SLOT_BACK = 36;

    private final PluginRules plugin;
    private final Map<UUID, Scope> scopes = new HashMap<>();

    public Gui(PluginRules plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ public API

    public Scope scopeOf(Player player) {
        return scopes.getOrDefault(player.getUniqueId(), Scope.GLOBAL);
    }

    public void forget(UUID id) {
        scopes.remove(id);
    }

    public void openMain(Player player) {
        openMain(player, scopeOf(player));
    }

    public void openMain(Player player, Scope scope) {
        scopes.put(player.getUniqueId(), scope);
        GuiHolder holder = new GuiHolder();
        Inventory inv = plugin.platform().createInventory(holder, MAIN_SIZE, plugin.lang().get(player, "gui.title.main"));
        holder.bind(inv, () -> renderMain(player, holder, inv, scope));
        holder.refresh();
        player.openInventory(inv);
    }

    public void openRule(Player player, String ruleId, Scope scope) {
        RuleDef rule = Rules.get(ruleId);
        if (rule == null) return;
        scopes.put(player.getUniqueId(), scope);
        GuiHolder holder = new GuiHolder();
        Inventory inv = plugin.platform().createInventory(holder, RULE_SIZE,
                plugin.lang().get(player, "gui.title.rule", "rule", plugin.lang().get(player, "rule." + ruleId + ".name")));
        holder.bind(inv, () -> renderRule(player, holder, inv, rule, scope));
        holder.refresh();
        player.openInventory(inv);
    }

    /** Opens the scope selector; {@code ruleId} is where to return to (null for the main menu). */
    public void openScopes(Player player, String ruleId) {
        Scope current = scopeOf(player);
        GuiHolder holder = new GuiHolder();
        Inventory inv = plugin.platform().createInventory(holder, SCOPE_SIZE, plugin.lang().get(player, "gui.title.scope"));
        holder.bind(inv, () -> renderScopes(player, holder, inv, current, ruleId));
        holder.refresh();
        player.openInventory(inv);
    }

    /**
     * Handles a click in a menu. Returns true if the inventory is a PluginRules menu (the click must then be
     * cancelled by the caller).
     */
    public boolean handleClick(Player player, Inventory top, int rawSlot, ClickType click) {
        if (top == null || !(top.getHolder() instanceof GuiHolder holder)) return false;
        if (rawSlot < 0 || rawSlot >= top.getSize()) return true;
        GuiHolder.SlotAction action = holder.actionAt(rawSlot);
        if (action != null) action.click(click);
        return true;
    }

    // ------------------------------------------------------------------ rendering

    private void renderMain(Player p, GuiHolder h, Inventory inv, Scope scope) {
        fill(p, inv);
        inv.setItem(SLOT_SCOPE, scopeButton(p, scope));
        h.action(SLOT_SCOPE, c -> {
            click(p);
            openScopes(p, null);
        });

        int i = 0;
        for (RuleDef rule : Rules.all().values()) {
            if (i >= RULE_SLOTS.length) break;
            int slot = RULE_SLOTS[i++];
            inv.setItem(slot, ruleIcon(p, rule, scope));
            h.action(slot, c -> {
                if (c.isRightClick()) {
                    toggleEnabled(p, rule, scope);
                    h.refresh();
                } else {
                    click(p);
                    openRule(p, rule.id(), scope);
                }
            });
        }

        inv.setItem(SLOT_CLOSE, simple(p, Material.BARRIER, "gui.close", null));
        h.action(SLOT_CLOSE, c -> {
            click(p);
            p.closeInventory();
        });
    }

    private void renderRule(Player p, GuiHolder h, Inventory inv, RuleDef rule, Scope scope) {
        fill(p, inv);
        inv.setItem(SLOT_SCOPE, scopeButton(p, scope));
        h.action(SLOT_SCOPE, c -> {
            click(p);
            openScopes(p, rule.id());
        });

        // the rule itself, top centre
        inv.setItem(13, ruleIcon(p, rule, scope));

        List<ParamDef> params = rule.params();
        int count = params.size();
        int start = 19 + (7 - Math.min(count, 7)) / 2;
        for (int i = 0; i < count && i < 7; i++) {
            ParamDef param = params.get(i);
            int slot = start + i;
            inv.setItem(slot, paramItem(p, rule, param, scope));
            h.action(slot, c -> {
                boolean changed = editParam(p, rule, param, scope, c);
                if (changed) h.refresh();
            });
        }

        inv.setItem(SLOT_BACK, simple(p, Material.ARROW, "gui.back", null));
        h.action(SLOT_BACK, c -> {
            click(p);
            openMain(p, scope);
        });
        inv.setItem(SLOT_CLOSE, simple(p, Material.BARRIER, "gui.close", null));
        h.action(SLOT_CLOSE, c -> {
            click(p);
            p.closeInventory();
        });
    }

    private void renderScopes(Player p, GuiHolder h, Inventory inv, Scope current, String returnRule) {
        fill(p, inv);
        inv.setItem(SLOT_SCOPE, simple(p, Material.COMPASS, "gui.scope.header", plugin.lang().list(p, "gui.scope.header.lore")));

        List<Scope> entries = new ArrayList<>();
        entries.add(Scope.GLOBAL);
        for (String env : Scope.ENVIRONMENTS) entries.add(Scope.environment(env));
        int[] topSlots = {19, 21, 23, 25};
        for (int i = 0; i < entries.size(); i++) {
            Scope s = entries.get(i);
            inv.setItem(topSlots[i], scopeEntry(p, s, current));
            h.action(topSlots[i], c -> choose(p, s, returnRule));
        }

        int slot = 28;
        int shown = 0;
        for (World w : plugin.getServer().getWorlds()) {
            if (shown >= 15) break; // fits rows 4 and 5 (28..34, 37..43)
            if (slot == 35) slot = 37;
            Scope s = Scope.world(w.getName());
            inv.setItem(slot, scopeEntry(p, s, current));
            h.action(slot, c -> choose(p, s, returnRule));
            slot++;
            shown++;
        }

        inv.setItem(49, simple(p, Material.ARROW, "gui.back", null));
        h.action(49, c -> {
            click(p);
            if (returnRule == null) openMain(p, current);
            else openRule(p, returnRule, current);
        });
    }

    private void choose(Player p, Scope scope, String returnRule) {
        click(p);
        if (returnRule == null) openMain(p, scope);
        else openRule(p, returnRule, scope);
    }

    // ------------------------------------------------------------------ editing

    private void toggleEnabled(Player p, RuleDef rule, Scope scope) {
        boolean now = (Boolean) plugin.store().resolve(rule.id(), RuleDef.ENABLED, scope).value();
        plugin.store().set(rule.id(), RuleDef.ENABLED, !now, scope);
        success(p);
    }

    /** Applies a click to a parameter. Returns true if the menu should be redrawn. */
    private boolean editParam(Player p, RuleDef rule, ParamDef param, Scope scope, ClickType click) {
        String id = rule.id();
        Object current = plugin.store().resolve(id, param.key(), scope).value();

        if (click == ClickType.MIDDLE) {
            plugin.store().reset(id, param.key(), scope);
            success(p);
            return true;
        }

        switch (param.type()) {
            case BOOLEAN:
                plugin.store().set(id, param.key(), !((Boolean) current), scope);
                success(p);
                return true;
            case INT: {
                if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
                    click(p);
                    plugin.prompts().start(p, id, param, scope);
                    return false;
                }
                int step = param.step() * (click.isShiftClick() ? 10 : 1);
                if (click.isRightClick()) step = -step;
                else if (!click.isLeftClick()) return false;
                int next = param.clamp((long) (Integer) current + step);
                plugin.store().set(id, param.key(), next, scope);
                success(p);
                return true;
            }
            default: {
                if (click.isRightClick()) {
                    plugin.store().set(id, param.key(), "", scope);
                    success(p);
                    return true;
                }
                click(p);
                plugin.prompts().start(p, id, param, scope);
                return false;
            }
        }
    }

    // ------------------------------------------------------------------ items

    private ItemStack ruleIcon(Player p, RuleDef rule, Scope scope) {
        List<String> lore = new ArrayList<>();
        for (String line : plugin.lang().list(p, "rule." + rule.id() + ".desc")) lore.add(line);
        lore.add("");
        Resolved r = plugin.store().resolve(rule.id(), RuleDef.ENABLED, scope);
        lore.add(plugin.lang().get(p, "gui.current", "scope", scopeLabel(p, scope), "value", valueText(p, r.value()))
                + " " + sourceTag(p, r, scope));
        lore.add("");
        lore.add(plugin.lang().get(p, "gui.per-scope"));
        for (String env : new String[]{null, "overworld", "nether", "the_end"}) {
            Scope s = env == null ? Scope.GLOBAL : Scope.environment(env);
            Resolved rr = plugin.store().resolve(rule.id(), RuleDef.ENABLED, s);
            lore.add(plugin.lang().get(p, "gui.per-scope.line", "scope", scopeLabel(p, s), "value", valueText(p, rr.value())));
        }
        lore.add("");
        lore.addAll(plugin.lang().list(p, "gui.rule.controls"));
        return item(p, material(rule.icon(), Material.PAPER), plugin.lang().get(p, "rule." + rule.id() + ".name"), lore);
    }

    private ItemStack paramItem(Player p, RuleDef rule, ParamDef param, Scope scope) {
        Resolved r = plugin.store().resolve(rule.id(), param.key(), scope);
        boolean own = plugin.store().hasOwn(rule.id(), param.key(), scope);
        List<String> lore = new ArrayList<>();
        String descKey = param.key().equals(RuleDef.ENABLED)
                ? "param.enabled.desc" : "param." + rule.id() + "." + param.key() + ".desc";
        lore.addAll(plugin.lang().list(p, descKey));
        lore.add("");
        lore.add(plugin.lang().get(p, "gui.current", "scope", scopeLabel(p, scope), "value", valueText(p, r.value()))
                + " " + sourceTag(p, r, scope));
        lore.add(plugin.lang().get(p, "gui.default", "value", valueText(p, param.def())));
        if (param.type() == ParamType.INT) {
            lore.add(plugin.lang().get(p, "gui.range", "min", param.min(), "max", param.max()));
        }
        if (scope.kind() != Scope.Kind.GLOBAL) {
            lore.add(plugin.lang().get(p, own ? "gui.override.yes" : "gui.override.no"));
        }
        lore.add("");
        switch (param.type()) {
            case BOOLEAN -> lore.addAll(plugin.lang().list(p, "gui.controls.bool"));
            case INT -> lore.addAll(plugin.lang().list(p, "gui.controls.int", "step", param.step(), "big", param.step() * 10));
            default -> lore.addAll(plugin.lang().list(p, "gui.controls.string"));
        }
        lore.addAll(plugin.lang().list(p, scope.kind() == Scope.Kind.GLOBAL ? "gui.controls.reset-global" : "gui.controls.reset"));

        Material icon;
        if (param.type() == ParamType.BOOLEAN) {
            boolean on = (Boolean) r.value();
            icon = param.key().equals(RuleDef.ENABLED)
                    ? material(on ? "LIME_CONCRETE" : "RED_CONCRETE", Material.PAPER)
                    : material(on ? "LIME_DYE" : "GRAY_DYE", Material.PAPER);
        } else if (param.type() == ParamType.INT) {
            icon = material("CLOCK", Material.PAPER);
        } else {
            icon = material("NAME_TAG", Material.PAPER);
        }
        String nameKey = param.key().equals(RuleDef.ENABLED) ? "param.enabled.name" : "param." + rule.id() + "." + param.key() + ".name";
        return item(p, icon, plugin.lang().get(p, nameKey), lore);
    }

    private ItemStack scopeButton(Player p, Scope scope) {
        List<String> lore = new ArrayList<>();
        lore.add(plugin.lang().get(p, "gui.scope.current", "scope", scopeLabel(p, scope)));
        lore.addAll(plugin.lang().list(p, "gui.scope.lore"));
        return item(p, Material.COMPASS, plugin.lang().get(p, "gui.scope.button"), lore);
    }

    private ItemStack scopeEntry(Player p, Scope s, Scope current) {
        boolean selected = s.equals(current);
        Material icon;
        switch (s.kind()) {
            case GLOBAL -> icon = material("NETHER_STAR", Material.PAPER);
            case ENVIRONMENT -> icon = material(s.name().equals("nether") ? "NETHERRACK"
                    : s.name().equals("the_end") ? "END_STONE" : "GRASS_BLOCK", Material.PAPER);
            default -> {
                World w = plugin.getServer().getWorld(s.name());
                String g = w == null ? "overworld" : Scope.groupOf(w);
                icon = material("nether".equals(g) ? "NETHERRACK" : "the_end".equals(g) ? "END_STONE" : "GRASS_BLOCK", Material.PAPER);
            }
        }
        List<String> lore = new ArrayList<>();
        lore.addAll(plugin.lang().list(p, "gui.scope.entry." + s.kind().name().toLowerCase()));
        lore.add("");
        lore.add(plugin.lang().get(p, selected ? "gui.scope.selected" : "gui.scope.select"));
        String name = (selected ? ChatColor.GREEN : ChatColor.YELLOW) + ChatColor.stripColor(scopeLabel(p, s));
        return item(p, icon, name, lore);
    }

    private void fill(Player p, Inventory inv) {
        ItemStack pane = item(p, material("GRAY_STAINED_GLASS_PANE", Material.GLASS_PANE), " ", null);
        ItemStack edge = item(p, material("BLACK_STAINED_GLASS_PANE", Material.GLASS_PANE), " ", null);
        int size = inv.getSize();
        for (int i = 0; i < size; i++) {
            boolean border = i < 9 || i >= size - 9 || i % 9 == 0 || i % 9 == 8;
            inv.setItem(i, border ? edge : pane);
        }
    }

    private ItemStack simple(Player p, Material m, String nameKey, List<String> lore) {
        return item(p, m, plugin.lang().get(p, nameKey), lore);
    }

    private ItemStack item(Player p, Material m, String name, List<String> lore) {
        ItemStack stack = new ItemStack(m);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            plugin.platform().applyText(meta, name, lore == null ? new ArrayList<>() : lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static Material material(String name, Material fallback) {
        Material m = Material.matchMaterial(name);
        return m == null ? fallback : m;
    }

    // ------------------------------------------------------------------ text

    public String scopeLabel(Player p, Scope s) {
        switch (s.kind()) {
            case GLOBAL:
                return plugin.lang().get(p, "scope.global");
            case ENVIRONMENT:
                return plugin.lang().get(p, "scope." + s.name());
            default:
                return plugin.lang().get(p, "scope.world", "name", s.name());
        }
    }

    private String valueText(Player p, Object value) {
        if (value instanceof Boolean b) return plugin.lang().get(p, b ? "value.on" : "value.off");
        if (value instanceof String s && s.isEmpty()) return plugin.lang().get(p, "value.empty");
        return "&f" + value;
    }

    private String sourceTag(Player p, Resolved r, Scope viewing) {
        Scope src = r.source();
        if (src == null) return plugin.lang().get(p, "source.default");
        if (src.equals(viewing)) return plugin.lang().get(p, "source.own", "scope", scopeLabel(p, src));
        return plugin.lang().get(p, "source.inherited", "scope", scopeLabel(p, src));
    }

    // ------------------------------------------------------------------ sounds

    private void click(Player p) {
        play(p, 0);
    }

    private void success(Player p) {
        play(p, 1);
    }

    private void play(Player p, int kind) {
        try {
            Sound s = kind == 0 ? Sound.UI_BUTTON_CLICK : Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            p.playSound(p.getLocation(), s, 0.7f, kind == 0 ? 1.2f : 1.5f);
        } catch (Throwable ignored) {
            // sounds are cosmetic
        }
    }
}

package dev.war.edict.commands;

import dev.war.edict.Edict;
import dev.war.edict.config.Resolved;
import dev.war.edict.config.Scope;
import dev.war.edict.model.ParamDef;
import dev.war.edict.model.ParamType;
import dev.war.edict.model.RuleDef;
import dev.war.edict.model.Rules;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** /edict (gui, list, get, set, reset, reload, help). */
public class EdictCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("gui", "list", "get", "set", "reset", "reload", "help");

    private final Edict plugin;

    public EdictCommand(Edict plugin) {
        this.plugin = plugin;
    }

    private void msg(CommandSender s, String key, Object... pairs) {
        plugin.lang().send(s, key, pairs);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!Edict.isAdmin(sender)) {
            msg(sender, "cmd.no-permission");
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player p) plugin.gui().openMain(p);
            else help(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (sub) {
            case "gui":
                if (sender instanceof Player p) plugin.gui().openMain(p);
                else msg(sender, "cmd.players-only");
                return true;
            case "list":
                list(sender, rest);
                return true;
            case "get":
                get(sender, rest);
                return true;
            case "set":
                set(sender, rest);
                return true;
            case "reset":
                reset(sender, rest);
                return true;
            case "reload":
                plugin.reloadAll();
                msg(sender, "cmd.reload");
                return true;
            case "help":
            default:
                help(sender, label);
                return true;
        }
    }

    private void help(CommandSender sender, String label) {
        msg(sender, "cmd.help.header");
        for (String line : plugin.lang().list(sender, "cmd.help.lines", "label", label)) sender.sendMessage(line);
    }

    // ------------------------------------------------------------------ list / get

    private void list(CommandSender sender, String[] a) {
        Scope scope = Scope.GLOBAL;
        if (a.length >= 1) {
            scope = Scope.parse(a[0]);
            if (scope == null) {
                msg(sender, "cmd.bad-scope", "scope", a[0]);
                return;
            }
        }
        msg(sender, "cmd.list.header", "scope", label(sender, scope));
        for (RuleDef rule : Rules.all().values()) {
            sender.sendMessage(plugin.lang().get(sender, "cmd.list.rule", "rule", rule.id()));
            for (ParamDef p : rule.params()) {
                Resolved r = plugin.store().resolve(rule.id(), p.key(), scope);
                sender.sendMessage(plugin.lang().get(sender, "cmd.list.param", "param", p.key(),
                        "value", show(sender, r.value()), "source", source(sender, r)));
            }
        }
    }

    private void get(CommandSender sender, String[] a) {
        if (a.length < 1) {
            msg(sender, "cmd.usage.get");
            return;
        }
        RuleDef rule = rule(sender, a[0]);
        if (rule == null) return;
        Scope scope = Scope.GLOBAL;
        String param = RuleDef.ENABLED;
        int i = 1;
        if (a.length > i && rule.param(a[i].toLowerCase(Locale.ROOT)) != null) {
            param = a[i].toLowerCase(Locale.ROOT);
            i++;
        }
        if (a.length > i) {
            scope = Scope.parse(a[i]);
            if (scope == null) {
                msg(sender, "cmd.bad-scope", "scope", a[i]);
                return;
            }
        }
        Resolved r = plugin.store().resolve(rule.id(), param, scope);
        msg(sender, "cmd.get", "rule", rule.id(), "param", param, "value", show(sender, r.value()),
                "scope", label(sender, scope), "source", source(sender, r));
    }

    // ------------------------------------------------------------------ set / reset

    private void set(CommandSender sender, String[] a) {
        if (a.length < 2) {
            msg(sender, "cmd.usage.set");
            return;
        }
        RuleDef rule = rule(sender, a[0]);
        if (rule == null) return;

        List<String> t = new ArrayList<>(Arrays.asList(a).subList(1, a.length));
        ParamDef param = rule.param(RuleDef.ENABLED);
        if (rule.param(t.get(0).toLowerCase(Locale.ROOT)) != null) {
            param = rule.param(t.remove(0).toLowerCase(Locale.ROOT));
            if (t.isEmpty()) {
                msg(sender, "cmd.usage.set");
                return;
            }
        }

        // an optional trailing scope; a string value may contain spaces
        Scope scope = Scope.GLOBAL;
        String last = t.get(t.size() - 1);
        boolean hasScope = t.size() >= 2 && (param.type() != ParamType.STRING || Scope.parse(last) != null);
        if (hasScope) {
            Scope parsed = Scope.parse(last);
            if (parsed == null && param.type() != ParamType.STRING) {
                msg(sender, "cmd.bad-scope", "scope", last);
                return;
            }
            if (parsed != null) {
                scope = parsed;
                t.remove(t.size() - 1);
            }
        }
        if (t.size() > 1 && param.type() != ParamType.STRING) {
            msg(sender, "cmd.usage.set");
            return;
        }
        String valueText = String.join(" ", t);

        Object value;
        try {
            value = param.parse(valueText);
        } catch (IllegalArgumentException e) {
            msg(sender, e.getMessage(), "min", param.min(), "max", param.max());
            return;
        }
        plugin.store().set(rule.id(), param.key(), value, scope);
        msg(sender, "cmd.set.ok", "rule", rule.id(), "param", param.key(), "value", show(sender, value), "scope", label(sender, scope));
    }

    private void reset(CommandSender sender, String[] a) {
        if (a.length < 1) {
            msg(sender, "cmd.usage.reset");
            return;
        }
        RuleDef rule = rule(sender, a[0]);
        if (rule == null) return;
        String param = null;
        int i = 1;
        if (a.length > i && rule.param(a[i].toLowerCase(Locale.ROOT)) != null) {
            param = a[i].toLowerCase(Locale.ROOT);
            i++;
        }
        Scope scope = Scope.GLOBAL;
        if (a.length > i) {
            scope = Scope.parse(a[i]);
            if (scope == null) {
                msg(sender, "cmd.bad-scope", "scope", a[i]);
                return;
            }
        }
        if (param == null) {
            for (ParamDef p : rule.params()) plugin.store().reset(rule.id(), p.key(), scope);
        } else {
            plugin.store().reset(rule.id(), param, scope);
        }
        msg(sender, "cmd.reset.ok", "rule", rule.id(), "param", param == null ? "*" : param, "scope", label(sender, scope));
    }

    private RuleDef rule(CommandSender sender, String id) {
        RuleDef rule = Rules.get(id.toLowerCase(Locale.ROOT));
        if (rule == null) msg(sender, "cmd.rule-not-found", "rule", id);
        return rule;
    }

    // ------------------------------------------------------------------ text

    private String label(CommandSender s, Scope scope) {
        switch (scope.kind()) {
            case GLOBAL:
                return plugin.lang().get(s, "scope.global");
            case ENVIRONMENT:
                return plugin.lang().get(s, "scope." + scope.name());
            default:
                return plugin.lang().get(s, "scope.world", "name", scope.name());
        }
    }

    private String show(CommandSender s, Object v) {
        if (v instanceof Boolean b) return plugin.lang().get(s, b ? "value.on" : "value.off");
        if (v instanceof String str && str.isEmpty()) return plugin.lang().get(s, "value.empty");
        return "&f" + v;
    }

    private String source(CommandSender s, Resolved r) {
        if (r.source() == null) return plugin.lang().get(s, "source.default");
        return label(s, r.source());
    }

    // ------------------------------------------------------------------ tab completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!Edict.isAdmin(sender)) return out;

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], SUBCOMMANDS, out);
        } else {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String cur = args[args.length - 1];
            if (sub.equals("list") && args.length == 2) {
                StringUtil.copyPartialMatches(cur, scopes(), out);
            } else if (sub.equals("get") || sub.equals("reset") || sub.equals("set")) {
                complete(sub, args, cur, out);
            }
        }
        Collections.sort(out);
        return out;
    }

    private void complete(String sub, String[] args, String cur, List<String> out) {
        if (args.length == 2) {
            StringUtil.copyPartialMatches(cur, Rules.all().keySet(), out);
            return;
        }
        RuleDef rule = Rules.get(args[1].toLowerCase(Locale.ROOT));
        if (rule == null) return;
        ParamDef given = args.length >= 3 ? rule.param(args[2].toLowerCase(Locale.ROOT)) : null;
        List<String> options = new ArrayList<>();
        if (args.length == 3) {
            for (ParamDef p : rule.params()) options.add(p.key());
            if (sub.equals("set")) options.addAll(List.of("true", "false"));
            else options.addAll(scopes());
        } else if (args.length == 4) {
            if (given != null) {
                if (sub.equals("set")) options.addAll(valuesOf(given));
                else options.addAll(scopes());
            } else if (sub.equals("set")) {
                options.addAll(scopes());
            }
        } else if (args.length == 5 && given != null && sub.equals("set")) {
            options.addAll(scopes());
        }
        StringUtil.copyPartialMatches(cur, options, out);
    }

    private List<String> valuesOf(ParamDef p) {
        switch (p.type()) {
            case BOOLEAN:
                return List.of("true", "false");
            case INT:
                return List.of(String.valueOf(p.def()));
            default:
                return List.of();
        }
    }

    private List<String> scopes() {
        List<String> s = new ArrayList<>();
        s.add("global");
        s.addAll(Scope.ENVIRONMENTS);
        for (World w : plugin.getServer().getWorlds()) s.add("world:" + w.getName());
        return s;
    }
}

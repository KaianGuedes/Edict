package dev.war.edict.selftest;

import dev.war.edict.Edict;
import dev.war.edict.config.Resolved;
import dev.war.edict.config.Scope;
import dev.war.edict.gui.GuiHolder;
import dev.war.edict.i18n.Lang;
import dev.war.edict.model.ParamDef;
import dev.war.edict.model.RuleDef;
import dev.war.edict.model.Rules;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Test harness. Does nothing unless started with -Dedict.selftest=&lt;report file&gt;.
 * -Dedict.selftest.expect=fresh|flat|legacy-folder says how the config was seeded before the start.
 */
public final class SelfTestPlugin extends JavaPlugin {

    private final List<String> report = new ArrayList<>();
    private int pass;
    private int fail;
    private Edict ed;
    private final Pattern rawKey = Pattern.compile("^[a-z_]+(\\.[a-z0-9_\\-]+)+$");
    private final List<String> allTexts = new ArrayList<>();

    @Override
    public void onEnable() {
        String path = System.getProperty("edict.selftest");
        if (path == null) return;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                run();
            } catch (Throwable t) {
                check("harness did not crash", false, t.toString());
                for (StackTraceElement e : t.getStackTrace()) {
                    report.add("    at " + e);
                    if (report.size() > 400) break;
                }
            }
            finish(path);
        }, 60L);
    }

    private void check(String name, boolean ok, String detail) {
        if (ok) pass++;
        else fail++;
        report.add((ok ? "PASS " : "FAIL ") + name + (detail == null || detail.isEmpty() ? "" : "  [" + detail + "]"));
    }

    private void check(String name, boolean ok) {
        check(name, ok, "");
    }

    private void finish(String path) {
        String summary = "SELFTEST RESULT pass=" + pass + " fail=" + fail + " server=" + Bukkit.getVersion();
        report.add(summary);
        try {
            Files.write(new File(path).toPath(), report, StandardCharsets.UTF_8);
        } catch (IOException e) {
            getLogger().severe("cannot write report: " + e);
        }
        for (String l : report) getLogger().info(l);
        getLogger().info(summary);
        Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 20L);
    }

    private World normal() {
        for (World w : Bukkit.getWorlds()) if (w.getEnvironment() == World.Environment.NORMAL) return w;
        return Bukkit.getWorlds().get(0);
    }

    private World env(World.Environment e) {
        for (World w : Bukkit.getWorlds()) if (w.getEnvironment() == e) return w;
        return null;
    }

    private void run() throws Exception {
        ed = (Edict) Bukkit.getPluginManager().getPlugin("Edict");
        check("plugin enabled", ed != null && ed.isEnabled());
        if (ed == null) return;
        report.add("platform=" + ed.platform().name() + " worlds=" + Bukkit.getWorlds().stream().map(World::getName).toList());

        migration();
        i18n();
        scopes();
        creeper();
        enderman();
        portal();
        voidFall();
        commands();
        gui();
        reloadTest();
        scanTexts();
    }

    // ------------------------------------------------------------------ migration

    private void migration() throws IOException {
        String expect = System.getProperty("edict.selftest.expect", "fresh");
        File data = ed.getDataFolder();
        File cfg = new File(data, "config.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(cfg);
        check("config-version is 2", y.getInt("config-version") == 2);
        String text = Files.readString(cfg.toPath(), StandardCharsets.UTF_8);
        check("config keeps comments", text.contains("ONLY THE END"));
        check("no flat boolean rules left", !y.isBoolean("rules.creeper_block_damage"));
        File bak = new File(data, "config.yml.v0.1.bak");
        if (expect.equals("fresh")) {
            check("fresh install has no backup", !bak.exists());
            return;
        }
        check("backup of the 0.1 config exists", bak.isFile());
        String old = bak.isFile() ? Files.readString(bak.toPath(), StandardCharsets.UTF_8) : "";
        check("backup is the old flat format", old.contains("creeper_block_damage: false"));
        check("migrated creeper=false", !ed.store().enabled(Rules.CREEPER, null));
        check("migrated enderman=true", ed.store().enabled(Rules.ENDERMAN, null));
        check("migrated portal=false", !ed.store().enabled(Rules.END_PORTAL, null));
        check("migrated void=true", ed.store().enabled(Rules.VOID_FALL, null));
        check("migrated defaults of new params", ed.store().integer(Rules.VOID_FALL, "the-end-y", null) == -200
                && ed.store().integer(Rules.VOID_FALL, "overworld-y", null) == 1624);
        if (expect.equals("legacy-folder")) {
            File oldDir = new File(data.getParentFile(), "PluginRules");
            File oldCfg = new File(oldDir, "config.yml");
            String oldText = oldCfg.isFile() ? Files.readString(oldCfg.toPath(), StandardCharsets.UTF_8) : "";
            check("old PluginRules folder untouched (still flat)", oldText.contains("creeper_block_damage: false")
                    && !oldText.contains("config-version"));
            check("old folder has no backup or new files", !new File(oldDir, "config.yml.v0.1.bak").exists()
                    && !new File(oldDir, "lang").exists());
        }
        for (RuleDef r : Rules.all().values()) for (ParamDef p : r.params()) ed.store().reset(r.id(), p.key(), Scope.GLOBAL);
        ed.store().set(Rules.VOID_FALL, RuleDef.ENABLED, false, Scope.GLOBAL);
    }

    // ------------------------------------------------------------------ i18n

    private void i18n() {
        Lang lang = ed.lang();
        Set<String> en = lang.keys("en_US");
        Set<String> pt = lang.keys("pt_BR");
        check("en_US and pt_BR define the same keys", en.equals(pt), "onlyEn=" + diff(en, pt) + " onlyPt=" + diff(pt, en));
        List<String> need = new ArrayList<>(List.of("log.enabled", "log.disabled", "err.bool", "err.number", "err.range", "value.on",
                "value.off", "value.empty", "source.default", "source.own", "source.inherited", "scope.global", "scope.overworld",
                "scope.nether", "scope.the_end", "scope.world", "cmd.no-permission", "cmd.players-only", "cmd.reload", "cmd.bad-scope",
                "cmd.rule-not-found", "cmd.usage.get", "cmd.usage.set", "cmd.usage.reset", "cmd.help.header", "cmd.help.lines",
                "cmd.list.header", "cmd.list.rule", "cmd.list.param", "cmd.get", "cmd.set.ok", "cmd.reset.ok", "prompt.ask",
                "prompt.cancelled", "prompt.saved", "param.enabled.name", "param.enabled.desc", "gui.title.main", "gui.title.rule",
                "gui.title.scope", "gui.close", "gui.back", "gui.current", "gui.default", "gui.range", "gui.per-scope",
                "gui.per-scope-line", "gui.override.yes", "gui.override.no", "gui.rule.controls", "gui.controls.bool",
                "gui.controls.int", "gui.controls.string", "gui.controls.reset", "gui.controls.reset-global", "gui.scope.button",
                "gui.scope.current", "gui.scope.lore", "gui.scope.header", "gui.scope.header-lore", "gui.scope.select",
                "gui.scope.selected", "gui.scope.entry.global", "gui.scope.entry.environment", "gui.scope.entry.world",
                "rule.enter_end_portal.blocked"));
        for (RuleDef r : Rules.all().values()) {
            need.add("rule." + r.id() + ".name");
            need.add("rule." + r.id() + ".desc");
            for (ParamDef p : r.params()) {
                if (p.key().equals(RuleDef.ENABLED)) continue;
                need.add("param." + r.id() + "." + p.key() + ".name");
                need.add("param." + r.id() + "." + p.key() + ".desc");
            }
        }
        List<String> missing = new ArrayList<>();
        for (String k : need) if (!en.contains(k) || !pt.contains(k)) missing.add(k);
        check("every key used by the plugin exists in both languages", missing.isEmpty(), missing.toString());
        check("locale pt_br -> pt_BR", Lang.match("pt_br").equals("pt_BR") && Lang.match("pt-PT").equals("pt_BR"));
        check("locale en_us / de_de -> en_US", Lang.match("en_us").equals("en_US") && Lang.match("de_de").equals("en_US"));
        check("pt_BR keeps the old Portuguese text", lang.get("pt_BR", "cmd.no-permission").contains("Você não tem permissão"));
        check("en_US text differs", lang.get("en_US", "cmd.no-permission").contains("do not have permission"));
    }

    private static Set<String> diff(Set<String> a, Set<String> b) {
        Set<String> d = new TreeSet<>(a);
        d.removeAll(b);
        return d;
    }

    // ------------------------------------------------------------------ scopes

    private void scopes() {
        var s = ed.store();
        World w = normal();
        World nether = env(World.Environment.NETHER);
        World end = env(World.Environment.THE_END);
        String R = Rules.ENDERMAN;
        check("default resolves to the default value", s.resolve(R, "enabled", w).value().equals(true));
        s.set(R, "enabled", false, Scope.GLOBAL);
        Resolved r = s.resolve(R, "enabled", w);
        check("global applies", !s.enabled(R, w) && r.source().equals(Scope.GLOBAL));
        s.set(R, "enabled", true, Scope.environment("overworld"));
        r = s.resolve(R, "enabled", w);
        check("environment group beats global", s.enabled(R, w) && r.source().equals(Scope.environment("overworld")));
        if (nether != null) check("other environment still uses global", !s.enabled(R, nether));
        s.set(R, "enabled", false, Scope.world(w.getName()));
        r = s.resolve(R, "enabled", w);
        check("world beats environment group", !s.enabled(R, w) && r.source().equals(Scope.world(w.getName())));
        s.set(R, "allow-place", false, Scope.environment("overworld"));
        check("params resolve independently", !s.bool(R, "allow-place", w)
                && s.resolve(R, "allow-place", w).source().kind() == Scope.Kind.ENVIRONMENT);
        s.reset(R, "enabled", Scope.world(w.getName()));
        check("reset world override falls back to the group", s.enabled(R, w));
        s.reset(R, "enabled", Scope.environment("overworld"));
        s.reset(R, "allow-place", Scope.environment("overworld"));
        check("reset env override falls back to global", !s.enabled(R, w));
        s.reset(R, "enabled", Scope.GLOBAL);
        check("reset global gives the default", s.enabled(R, w) && s.resolve(R, "enabled", w).value().equals(true));
        s.set(R, "enabled", false, Scope.world("zzz_test"));
        YamlConfiguration disk = YamlConfiguration.loadConfiguration(new File(ed.getDataFolder(), "config.yml"));
        check("changes are saved to config.yml right away", !disk.getBoolean("worlds.zzz_test.enderman_block_theft.enabled", true));
        s.reset(R, "enabled", Scope.world("zzz_test"));
        check("scope parse", Scope.parse("world:abc").equals(Scope.world("abc")) && Scope.parse("the_end").equals(Scope.environment("the_end"))
                && Scope.parse("global").equals(Scope.GLOBAL) && Scope.parse("nothing_like_this") == null);
        if (end != null) check("End world group name", "the_end".equals(Scope.groupOf(end)));
    }

    // ------------------------------------------------------------------ explosions

    private int platformDirt(World w, int cx, int cy, int cz) {
        int n = 0;
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            Block b = w.getBlockAt(cx + x, cy, cz + z);
            b.setType(Material.DIRT, false);
            n++;
        }
        return n;
    }

    private int countDirt(World w, int cx, int cy, int cz) {
        int n = 0;
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            if (w.getBlockAt(cx + x, cy, cz + z).getType() == Material.DIRT) n++;
        }
        return n;
    }

    private void creeper() {
        World w = normal();
        Location sp = w.getSpawnLocation();
        int cx = sp.getBlockX(), cz = sp.getBlockZ(), cy = Math.min(w.getMaxHeight() - 20, 150);
        w.getChunkAt(cx >> 4, cz >> 4).load(true);
        var s = ed.store();
        for (boolean enabled : new boolean[]{true, false}) {
            s.set(Rules.CREEPER, "enabled", enabled, Scope.GLOBAL);
            int total = platformDirt(w, cx, cy, cz);
            Creeper c = (Creeper) w.spawnEntity(new Location(w, cx + 0.5, cy + 1, cz + 0.5), EntityType.CREEPER);
            c.setPowered(true);
            c.explode();
            int left = countDirt(w, cx, cy, cz);
            if (enabled) check("creeper enabled=true: explosion breaks blocks", left < total, left + "/" + total);
            else check("creeper enabled=false: blocks stay", left == total, left + "/" + total);
        }
        for (boolean also : new boolean[]{false, true}) {
            s.set(Rules.CREEPER, "also-other-explosions", also, Scope.GLOBAL);
            int total = platformDirt(w, cx, cy, cz);
            TNTPrimed tnt = (TNTPrimed) w.spawnEntity(new Location(w, cx + 0.5, cy + 1, cz + 0.5), EntityType.PRIMED_TNT);
            w.createExplosion(tnt.getLocation(), 4f, false, true, tnt);
            tnt.remove();
            int left = countDirt(w, cx, cy, cz);
            if (also) check("also-other-explosions=true: TNT blocks stay", left == total, left + "/" + total);
            else check("also-other-explosions=false: TNT still breaks blocks", left < total, left + "/" + total);
        }
        s.reset(Rules.CREEPER, "also-other-explosions", Scope.GLOBAL);
        s.set(Rules.CREEPER, "enabled", true, Scope.GLOBAL);
        s.set(Rules.CREEPER, "enabled", false, Scope.world(w.getName()));
        int total = platformDirt(w, cx, cy, cz);
        Creeper c = (Creeper) w.spawnEntity(new Location(w, cx + 0.5, cy + 1, cz + 0.5), EntityType.CREEPER);
        c.explode();
        check("creeper: world override beats global", countDirt(w, cx, cy, cz) == total);
        s.reset(Rules.CREEPER, "enabled", Scope.world(w.getName()));
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) w.getBlockAt(cx + x, cy, cz + z).setType(Material.AIR, false);
    }

    private void enderman() {
        World w = normal();
        Location sp = w.getSpawnLocation();
        Block b = w.getBlockAt(sp.getBlockX(), 150, sp.getBlockZ());
        b.setType(Material.DIRT, false);
        Enderman e = (Enderman) w.spawnEntity(b.getLocation().add(0.5, 2, 0.5), EntityType.ENDERMAN);
        var s = ed.store();
        s.reset(Rules.ENDERMAN, "enabled", Scope.GLOBAL);
        EntityChangeBlockEvent take = new EntityChangeBlockEvent(e, b, Material.AIR.createBlockData());
        Bukkit.getPluginManager().callEvent(take);
        check("enderman enabled=true: taking allowed", !take.isCancelled());
        s.set(Rules.ENDERMAN, "enabled", false, Scope.GLOBAL);
        take = new EntityChangeBlockEvent(e, b, Material.AIR.createBlockData());
        Bukkit.getPluginManager().callEvent(take);
        check("enderman enabled=false: taking cancelled", take.isCancelled());
        EntityChangeBlockEvent place = new EntityChangeBlockEvent(e, b, Material.DIRT.createBlockData());
        Bukkit.getPluginManager().callEvent(place);
        check("enderman enabled=false: placing still allowed", !place.isCancelled());
        s.set(Rules.ENDERMAN, "allow-place", false, Scope.GLOBAL);
        place = new EntityChangeBlockEvent(e, b, Material.DIRT.createBlockData());
        Bukkit.getPluginManager().callEvent(place);
        check("allow-place=false: placing cancelled", place.isCancelled());
        s.reset(Rules.ENDERMAN, "allow-place", Scope.GLOBAL);
        s.reset(Rules.ENDERMAN, "enabled", Scope.GLOBAL);
        s.set(Rules.ENDERMAN, "enabled", false, Scope.world(w.getName()));
        take = new EntityChangeBlockEvent(e, b, Material.AIR.createBlockData());
        Bukkit.getPluginManager().callEvent(take);
        check("enderman: world override cancels", take.isCancelled());
        s.reset(Rules.ENDERMAN, "enabled", Scope.world(w.getName()));
        Creeper cr = (Creeper) w.spawnEntity(b.getLocation().add(0.5, 2, 0.5), EntityType.CREEPER);
        s.set(Rules.ENDERMAN, "enabled", false, Scope.GLOBAL);
        EntityChangeBlockEvent other = new EntityChangeBlockEvent(cr, b, Material.AIR.createBlockData());
        Bukkit.getPluginManager().callEvent(other);
        check("enderman rule ignores other entities", !other.isCancelled());
        s.reset(Rules.ENDERMAN, "enabled", Scope.GLOBAL);
        e.remove();
        cr.remove();
        b.setType(Material.AIR, false);
    }

    // ------------------------------------------------------------------ portal

    private void portal() {
        World w = normal();
        FakePlayer fp = new FakePlayer("PortalTester", w.getSpawnLocation());
        var s = ed.store();
        s.reset(Rules.END_PORTAL, "enabled", Scope.GLOBAL);
        PlayerPortalEvent ev = new PlayerPortalEvent(fp.player, fp.location, fp.location, PlayerTeleportEvent.TeleportCause.END_PORTAL);
        Bukkit.getPluginManager().callEvent(ev);
        check("portal enabled=true: allowed", !ev.isCancelled());
        s.set(Rules.END_PORTAL, "enabled", false, Scope.GLOBAL);
        ev = new PlayerPortalEvent(fp.player, fp.location, fp.location, PlayerTeleportEvent.TeleportCause.END_PORTAL);
        Bukkit.getPluginManager().callEvent(ev);
        check("portal enabled=false: cancelled", ev.isCancelled());
        check("portal: default message sent (en_US)", fp.messages.stream().anyMatch(m -> m.contains("End portal is disabled")), fp.messages.toString());
        PlayerPortalEvent nether = new PlayerPortalEvent(fp.player, fp.location, fp.location, PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);
        Bukkit.getPluginManager().callEvent(nether);
        check("portal rule ignores nether portals", !nether.isCancelled());
        FakePlayer pt = new FakePlayer("PortalPt", w.getSpawnLocation());
        pt.locale = "pt_br";
        ev = new PlayerPortalEvent(pt.player, pt.location, pt.location, PlayerTeleportEvent.TeleportCause.END_PORTAL);
        Bukkit.getPluginManager().callEvent(ev);
        check("portal: default message sent (pt_BR)", pt.messages.stream().anyMatch(m -> m.contains("portal do End est")), pt.messages.toString());
        FakePlayer cu = new FakePlayer("PortalCustom", w.getSpawnLocation());
        s.set(Rules.END_PORTAL, "message", "&cNo End today", Scope.GLOBAL);
        ev = new PlayerPortalEvent(cu.player, cu.location, cu.location, PlayerTeleportEvent.TeleportCause.END_PORTAL);
        Bukkit.getPluginManager().callEvent(ev);
        check("portal: custom message", cu.messages.stream().anyMatch(m -> m.contains("No End today")), cu.messages.toString());
        s.reset(Rules.END_PORTAL, "message", Scope.GLOBAL);
        s.reset(Rules.END_PORTAL, "enabled", Scope.GLOBAL);
        s.set(Rules.END_PORTAL, "enabled", false, Scope.environment("overworld"));
        FakePlayer o = new FakePlayer("PortalEnv", w.getSpawnLocation());
        ev = new PlayerPortalEvent(o.player, o.location, o.location, PlayerTeleportEvent.TeleportCause.END_PORTAL);
        Bukkit.getPluginManager().callEvent(ev);
        check("portal: environment override cancels", ev.isCancelled());
        s.reset(Rules.END_PORTAL, "enabled", Scope.environment("overworld"));
    }

    // ------------------------------------------------------------------ void fall

    private World create(String name, World.Environment env) {
        World w = Bukkit.getWorld(name);
        if (w != null) return w;
        return new WorldCreator(name).environment(env).generateStructures(false).createWorld();
    }

    private void move(FakePlayer p, double y) {
        Location to = p.location.clone();
        to.setY(y);
        PlayerMoveEvent e = new PlayerMoveEvent(p.player, p.location.clone(), to);
        Bukkit.getPluginManager().callEvent(e);
    }

    private void voidFall() {
        var s = ed.store();
        World main = normal();
        World end = env(World.Environment.THE_END);
        check("a default End world exists", end != null);
        if (end == null) return;
        s.set(Rules.VOID_FALL, "enabled", true, Scope.GLOBAL);

        FakePlayer p = new FakePlayer("VoidA", new Location(end, 10.5, -250, 20.5, 33f, 5f));
        move(p, -250);
        Location t = p.teleports.isEmpty() ? null : p.teleports.get(0);
        check("End void: teleported to the matching overworld", t != null && t.getWorld() != null, String.valueOf(t));
        World expected = expectedOverworldOf(end, main);
        if (t != null) {
            check("destination is " + expected.getName(), t.getWorld().equals(expected), t.getWorld().getName());
            int top = t.getWorld().getMaxHeight() - 8;
            check("Y clamped to max height minus margin", t.getBlockY() == Math.min(1624, top), "y=" + t.getY() + " top=" + top);
            check("x, z, yaw, pitch kept", t.getX() == 10.5 && t.getZ() == 20.5 && t.getYaw() == 33f && t.getPitch() == 5f);
        }
        check("fall distance reset on arrival", p.fallDistance == 0f);
        check("velocity zeroed (keep-velocity=false)", !p.velocitySets.isEmpty() && p.velocitySets.get(0).lengthSquared() == 0);
        check("fall-damage=true (default): no shield", !ed.voidFall().hasShield(p.id));
        EntityDamageEvent fall = new EntityDamageEvent(p.player, EntityDamageEvent.DamageCause.FALL, 10.0);
        Bukkit.getPluginManager().callEvent(fall);
        check("fall-damage=true: fall damage not cancelled", !fall.isCancelled());

        FakePlayer above = new FakePlayer("VoidAbove", new Location(end, 0, -100, 0));
        move(above, -100);
        check("above the trigger Y: nothing happens", above.teleports.isEmpty());

        FakePlayer vd = new FakePlayer("VoidDmg", new Location(end, 0, -70, 0));
        EntityDamageEvent voidDmg = new EntityDamageEvent(vd.player, EntityDamageEvent.DamageCause.VOID, 4.0);
        Bukkit.getPluginManager().callEvent(voidDmg);
        check("void damage cancelled in the End when enabled", voidDmg.isCancelled());
        s.set(Rules.VOID_FALL, "enabled", false, Scope.GLOBAL);
        voidDmg = new EntityDamageEvent(vd.player, EntityDamageEvent.DamageCause.VOID, 4.0);
        Bukkit.getPluginManager().callEvent(voidDmg);
        check("void damage not cancelled when disabled", !voidDmg.isCancelled());
        FakePlayer off = new FakePlayer("VoidOff", new Location(end, 0, -250, 0));
        move(off, -250);
        check("disabled: no teleport", off.teleports.isEmpty());
        s.set(Rules.VOID_FALL, "enabled", true, Scope.GLOBAL);
        FakePlayer ow = new FakePlayer("VoidOw", new Location(main, 0, -250, 0));
        move(ow, -250);
        check("not in the End: rule never applies", ow.teleports.isEmpty());

        s.set(Rules.VOID_FALL, "fall-damage", false, Scope.GLOBAL);
        s.set(Rules.VOID_FALL, "keep-velocity", true, Scope.GLOBAL);
        FakePlayer nf = new FakePlayer("VoidNoFall", new Location(end, 1, -300, 1));
        move(nf, -300);
        check("keep-velocity=true keeps the speed", !nf.velocitySets.isEmpty() && nf.velocitySets.get(0).getY() == -3.0);
        check("fall-damage=false: shield active", ed.voidFall().hasShield(nf.id));
        EntityDamageEvent nofall = new EntityDamageEvent(nf.player, EntityDamageEvent.DamageCause.FALL, 10.0);
        Bukkit.getPluginManager().callEvent(nofall);
        check("fall-damage=false: fall damage cancelled once", nofall.isCancelled());
        EntityDamageEvent again = new EntityDamageEvent(nf.player, EntityDamageEvent.DamageCause.FALL, 10.0);
        Bukkit.getPluginManager().callEvent(again);
        check("shield is one-shot", !again.isCancelled());
        s.reset(Rules.VOID_FALL, "fall-damage", Scope.GLOBAL);
        s.reset(Rules.VOID_FALL, "keep-velocity", Scope.GLOBAL);

        s.set(Rules.VOID_FALL, "the-end-y", -20, Scope.world(end.getName()));
        s.set(Rules.VOID_FALL, "overworld-y", 100, Scope.GLOBAL);
        FakePlayer cy = new FakePlayer("VoidY", new Location(end, 0, -30, 0));
        move(cy, -30);
        check("per-world the-end-y trigger", cy.teleports.size() == 1);
        check("overworld-y 100 used without clamping", cy.teleports.size() == 1 && cy.teleports.get(0).getBlockY() == 100);
        s.reset(Rules.VOID_FALL, "the-end-y", Scope.world(end.getName()));
        s.set(Rules.VOID_FALL, "overworld-y", 30000, Scope.GLOBAL);
        FakePlayer hi = new FakePlayer("VoidHigh", new Location(end, 0, -250, 0));
        move(hi, -250);
        check("huge overworld-y is clamped", !hi.teleports.isEmpty() && hi.teleports.get(0).getY() <= expected.getMaxHeight() - 8);
        s.reset(Rules.VOID_FALL, "overworld-y", Scope.GLOBAL);

        World arena = create("arena", World.Environment.NORMAL);
        World arenaEnd = create("arena_the_end", World.Environment.THE_END);
        World lonelyEnd = create("lonely_the_end", World.Environment.THE_END);
        check("test worlds created", arena != null && arenaEnd != null && lonelyEnd != null);
        if (arena == null || arenaEnd == null || lonelyEnd == null) return;
        FakePlayer a = new FakePlayer("VoidArena", new Location(arenaEnd, 0, -250, 0));
        move(a, -250);
        check("<name>_the_end -> <name>", a.teleports.size() == 1 && a.teleports.get(0).getWorld().equals(arena),
                a.teleports.isEmpty() ? "none" : a.teleports.get(0).getWorld().getName());
        if (!a.teleports.isEmpty()) {
            int top = arena.getMaxHeight() - 8;
            check("clamp uses the destination world's height", a.teleports.get(0).getBlockY() == Math.min(1624, top),
                    "y=" + a.teleports.get(0).getBlockY() + " top=" + top);
        }
        ed.getConfig().set("corresponding-overworld.arena_the_end", main.getName());
        FakePlayer m = new FakePlayer("VoidMap", new Location(arenaEnd, 0, -250, 0));
        move(m, -250);
        check("explicit mapping overrides the naming", m.teleports.size() == 1 && m.teleports.get(0).getWorld().equals(main));
        ed.getConfig().set("corresponding-overworld.arena_the_end", "no_such_world");
        FakePlayer bad = new FakePlayer("VoidBadMap", new Location(arenaEnd, 0, -250, 0));
        move(bad, -250);
        check("mapping to a missing world falls back to the naming", bad.teleports.size() == 1 && bad.teleports.get(0).getWorld().equals(arena));
        ed.getConfig().set("corresponding-overworld", null);
        FakePlayer l = new FakePlayer("VoidLonely", new Location(lonelyEnd, 0, -250, 0));
        move(l, -250);
        check("no matching overworld: main world fallback", l.teleports.size() == 1 && l.teleports.get(0).getWorld().equals(main));
        s.set(Rules.VOID_FALL, "enabled", false, Scope.GLOBAL);
    }

    private World expectedOverworldOf(World end, World main) {
        String n = end.getName();
        if (n.endsWith("_the_end")) {
            World w = Bukkit.getWorld(n.substring(0, n.length() - 8));
            if (w != null) return w;
        }
        return main;
    }

    // ------------------------------------------------------------------ commands

    private String run(FakePlayer p, String... args) {
        int before = p.messages.size();
        Command c = ed.getCommand("edict");
        c.execute(p.player, "edict", args);
        return String.join("\n", p.messages.subList(before, p.messages.size()));
    }

    private void commands() {
        World w = normal();
        FakePlayer p = new FakePlayer("Admin", w.getSpawnLocation());
        var s = ed.store();
        String out = run(p, "set", "creeper_block_damage", "false");
        allTexts.add(out);
        check("set <rule> <bool> (0.1 syntax)", !s.enabled(Rules.CREEPER, null) && out.contains("creeper_block_damage"), out);
        out = run(p, "set", "creeper_block_damage", "also-other-explosions", "true", "nether");
        allTexts.add(out);
        check("set <rule> <param> <value> <scope>", s.resolve(Rules.CREEPER, "also-other-explosions", Scope.environment("nether")).value().equals(true)
                && !s.bool(Rules.CREEPER, "also-other-explosions", null), out);
        out = run(p, "set", "safe_void_fall", "the-end-y", "-120", "world:" + w.getName());
        check("set int param on a world scope", s.hasOwn(Rules.VOID_FALL, "the-end-y", Scope.world(w.getName())), out);
        out = run(p, "set", "safe_void_fall", "the-end-y", "abc");
        allTexts.add(out);
        check("bad number rejected", out.contains("whole number") && s.integer(Rules.VOID_FALL, "the-end-y", null) == -200, out);
        out = run(p, "set", "safe_void_fall", "the-end-y", "999999");
        allTexts.add(out);
        check("out of range rejected", out.contains("between"), out);
        out = run(p, "set", "creeper_block_damage", "maybe");
        allTexts.add(out);
        check("bad boolean rejected", out.contains("true or false"), out);
        out = run(p, "set", "enter_end_portal", "message", "&eHello there world", "global");
        check("string value with spaces", s.string(Rules.END_PORTAL, "message", null).equals("&eHello there world"), out);
        out = run(p, "get", "creeper_block_damage");
        allTexts.add(out);
        check("get shows value and source", out.contains("OFF"), out);
        out = run(p, "get", "creeper_block_damage", "also-other-explosions", "nether");
        allTexts.add(out);
        check("get with param and scope", out.contains("ON"), out);
        out = run(p, "list");
        allTexts.add(out);
        check("list shows all rules", out.contains("safe_void_fall") && out.contains("the-end-y") && out.contains("overworld-y"), out);
        out = run(p, "list", "world:" + w.getName());
        check("list with scope", out.contains("-120"), out);
        out = run(p, "reset", "creeper_block_damage", "also-other-explosions", "nether");
        allTexts.add(out);
        check("reset removes an override", !s.hasOwn(Rules.CREEPER, "also-other-explosions", Scope.environment("nether")), out);
        run(p, "reset", "safe_void_fall", "world:" + w.getName());
        run(p, "reset", "creeper_block_damage");
        run(p, "reset", "enter_end_portal", "message");
        check("reset global to defaults", s.enabled(Rules.CREEPER, null) && !s.hasOwn(Rules.VOID_FALL, "the-end-y", Scope.world(w.getName())));
        out = run(p, "get", "nope");
        allTexts.add(out);
        check("unknown rule message", out.contains("not found"), out);
        out = run(p, "get", "creeper_block_damage", "nowhere");
        allTexts.add(out);
        check("unknown scope message", out.contains("Unknown scope"), out);
        out = run(p, "help");
        allTexts.add(out);
        check("help prints", out.contains("reload"), out);
        FakePlayer pt = new FakePlayer("AdminPt", w.getSpawnLocation());
        pt.locale = "pt_br";
        out = run(pt, "get", "nope");
        check("command output in pt_BR for a pt_br client", out.contains("não encontrada"), out);
        FakePlayer nope = new FakePlayer("NotAdmin", w.getSpawnLocation());
        nope.admin = false;
        out = run(nope, "reload");
        check("no permission is refused", out.contains("permission"), out);
        Command c = ed.getCommand("edict");
        check("tab: subcommands", c.tabComplete(p.player, "edict", new String[]{""}).containsAll(
                List.of("gui", "list", "get", "set", "reset", "reload")));
        check("tab: rules", c.tabComplete(p.player, "edict", new String[]{"set", "safe"}).contains("safe_void_fall"));
        check("tab: params and bool", c.tabComplete(p.player, "edict", new String[]{"set", "safe_void_fall", ""}).containsAll(
                List.of("the-end-y", "fall-damage", "true")));
        check("tab: scopes", c.tabComplete(p.player, "edict", new String[]{"get", "safe_void_fall", "enabled", ""}).containsAll(
                List.of("global", "overworld", "nether", "the_end", "world:" + w.getName())));
        check("tab: nothing for non-admins", c.tabComplete(nope.player, "edict", new String[]{""}).isEmpty());
        check("aliases registered", c.getAliases().containsAll(List.of("ed", "pluginrules", "prules", "pr")), c.getAliases().toString());
        var legacy = Bukkit.getPluginManager().getPermission("pluginrules.admin");
        check("legacy permission grants edict.admin", legacy != null && legacy.getChildren().getOrDefault("edict.admin", false));
    }

    // ------------------------------------------------------------------ reload

    private void reloadTest() throws IOException {
        World w = normal();
        FakePlayer p = new FakePlayer("Reloader", w.getSpawnLocation());
        File f = new File(ed.getDataFolder(), "config.yml");
        String text = Files.readString(f.toPath(), StandardCharsets.UTF_8);
        String edited = text.replaceFirst("(?m)^  enter_end_portal:\\s*\\r?\\n(\\s*#.*\\r?\\n)*    enabled: true",
                "  enter_end_portal:\n    enabled: false");
        check("test edit applied to the file", !edited.equals(text));
        Files.writeString(f.toPath(), edited, StandardCharsets.UTF_8);
        check("not yet applied before reload", ed.store().enabled(Rules.END_PORTAL, null));
        String out = run(p, "reload");
        check("reload applies file edits", !ed.store().enabled(Rules.END_PORTAL, null), out);
        check("reload message", out.contains("reloaded"), out);
        Files.writeString(f.toPath(), text, StandardCharsets.UTF_8);
        run(p, "reload");
        check("reload back", ed.store().enabled(Rules.END_PORTAL, null));
        File lang = new File(ed.getDataFolder(), "lang/en_US.yml");
        check("lang files are written to the data folder", lang.isFile() && new File(ed.getDataFolder(), "lang/pt_BR.yml").isFile());
    }

    // ------------------------------------------------------------------ GUI

    private String name(ItemStack i) {
        return i == null || i.getItemMeta() == null ? "" : i.getItemMeta().getDisplayName();
    }

    private String lore(ItemStack i) {
        if (i == null || i.getItemMeta() == null || i.getItemMeta().getLore() == null) return "";
        return String.join("\n", i.getItemMeta().getLore());
    }

    private void collect(Inventory inv) {
        for (ItemStack i : inv.getContents()) {
            if (i == null) continue;
            allTexts.add(name(i));
            allTexts.addAll(List.of(lore(i).split("\n")));
        }
    }

    private void gui() {
        World w = normal();
        FakePlayer p = new FakePlayer("GuiUser", w.getSpawnLocation());
        var s = ed.store();
        ed.gui().openMain(p.player);
        Inventory main = p.lastInventory();
        check("GUI main menu opens", main != null && main.getHolder() instanceof GuiHolder && main.getSize() == 45);
        if (main == null) return;
        collect(main);
        check("main menu has the 4 rules with names", name(main.getItem(19)).contains("Creeper") && name(main.getItem(21)).contains("Enderman")
                && name(main.getItem(23)).contains("portal") && name(main.getItem(25)).contains("void"), name(main.getItem(19)));
        check("rule lore shows the value per scope", lore(main.getItem(19)).contains("Global") && lore(main.getItem(19)).contains("Nether"));
        check("filler glass in the border", main.getItem(0) != null && main.getItem(0).getType().name().contains("GLASS_PANE"));
        boolean before = s.enabled(Rules.CREEPER, null);
        ed.gui().handleClick(p.player, main, 19, ClickType.RIGHT);
        check("right click toggles the rule", s.enabled(Rules.CREEPER, null) != before);
        YamlConfiguration disk = YamlConfiguration.loadConfiguration(new File(ed.getDataFolder(), "config.yml"));
        check("GUI change saved to disk immediately", disk.getBoolean("rules.creeper_block_damage.enabled") == s.enabled(Rules.CREEPER, null));
        check("menu redrawn after the toggle", lore(main.getItem(19)).contains(before ? "OFF" : "ON"));
        ed.gui().handleClick(p.player, main, 19, ClickType.RIGHT);
        check("clicks outside the menu are swallowed", ed.gui().handleClick(p.player, main, 70, ClickType.LEFT));
        check("non-menu inventory not handled", !ed.gui().handleClick(p.player, Bukkit.createInventory(null, 9), 0, ClickType.LEFT));

        ed.gui().handleClick(p.player, main, 25, ClickType.LEFT);
        Inventory rule = p.lastInventory();
        check("left click opens the rule menu", rule != main && rule.getHolder() instanceof GuiHolder);
        collect(rule);
        check("rule menu shows the parameters", name(rule.getItem(20)).contains("Trigger") && name(rule.getItem(21)).contains("Arrival"), name(rule.getItem(20)));
        int y = s.integer(Rules.VOID_FALL, "the-end-y", null);
        ed.gui().handleClick(p.player, rule, 20, ClickType.LEFT);
        check("numeric: left click +step", s.integer(Rules.VOID_FALL, "the-end-y", null) == y + 10);
        ed.gui().handleClick(p.player, rule, 20, ClickType.SHIFT_LEFT);
        check("numeric: shift-left +10 steps", s.integer(Rules.VOID_FALL, "the-end-y", null) == y + 110);
        ed.gui().handleClick(p.player, rule, 20, ClickType.RIGHT);
        check("numeric: right click -step", s.integer(Rules.VOID_FALL, "the-end-y", null) == y + 100);
        ed.gui().handleClick(p.player, rule, 20, ClickType.SHIFT_RIGHT);
        check("numeric: shift-right -10 steps", s.integer(Rules.VOID_FALL, "the-end-y", null) == y);
        for (int i = 0; i < 100; i++) ed.gui().handleClick(p.player, rule, 20, ClickType.SHIFT_LEFT);
        check("numeric: clamped to the maximum", s.integer(Rules.VOID_FALL, "the-end-y", null) == 256);
        ed.gui().handleClick(p.player, rule, 20, ClickType.MIDDLE);
        check("middle click resets to the default", s.integer(Rules.VOID_FALL, "the-end-y", null) == -200);
        ed.gui().handleClick(p.player, rule, 23, ClickType.LEFT);
        check("boolean param toggles", !s.bool(Rules.VOID_FALL, "fall-damage", null));
        ed.gui().handleClick(p.player, rule, 23, ClickType.LEFT);
        check("boolean param toggles back", s.bool(Rules.VOID_FALL, "fall-damage", null));

        ed.gui().handleClick(p.player, rule, 20, ClickType.DROP);
        check("drop opens the chat prompt", ed.prompts().pending(p.id) && p.closes > 0);
        ParamDef def = Rules.get(Rules.VOID_FALL).param("the-end-y");
        ed.prompts().apply(p.player, Rules.VOID_FALL, def, Scope.GLOBAL, "-150");
        check("prompt sets an exact value", s.integer(Rules.VOID_FALL, "the-end-y", null) == -150);
        ed.prompts().apply(p.player, Rules.VOID_FALL, def, Scope.GLOBAL, "nonsense");
        check("prompt rejects bad input", s.integer(Rules.VOID_FALL, "the-end-y", null) == -150);
        ed.prompts().apply(p.player, Rules.VOID_FALL, def, Scope.GLOBAL, "cancel");
        allTexts.addAll(p.messages);
        s.reset(Rules.VOID_FALL, "the-end-y", Scope.GLOBAL);

        Inventory ruleNow = p.lastInventory();
        ed.gui().handleClick(p.player, ruleNow, 4, ClickType.LEFT);
        Inventory scopes = p.lastInventory();
        check("scope selector opens", scopes.getSize() == 54 && scopes.getHolder() instanceof GuiHolder);
        collect(scopes);
        check("scope selector lists the worlds", java.util.stream.IntStream.range(0, 54).anyMatch(i -> name(scopes.getItem(i)).contains(w.getName())));
        ed.gui().handleClick(p.player, scopes, 23, ClickType.LEFT);
        Inventory rule2 = p.lastInventory();
        check("choosing a scope returns to the rule menu", rule2 != scopes && ed.gui().scopeOf(p.player).equals(Scope.environment("nether")));
        ed.gui().handleClick(p.player, rule2, 20, ClickType.LEFT);
        check("edit in nether scope writes an environment override only",
                s.hasOwn(Rules.VOID_FALL, "the-end-y", Scope.environment("nether"))
                        && s.integer(Rules.VOID_FALL, "the-end-y", null) == -200);
        collect(rule2);
        ed.gui().handleClick(p.player, rule2, 20, ClickType.MIDDLE);
        check("override removed again", !s.hasOwn(Rules.VOID_FALL, "the-end-y", Scope.environment("nether")));
        ed.gui().handleClick(p.player, rule2, 36, ClickType.LEFT);
        check("back returns to the main menu", p.lastInventory().getSize() == 45 && p.lastInventory() != rule2);
        int closes = p.closes;
        ed.gui().handleClick(p.player, p.lastInventory(), 40, ClickType.LEFT);
        check("close button closes", p.closes == closes + 1);

        FakePlayer pt = new FakePlayer("GuiPt", w.getSpawnLocation());
        pt.locale = "pt_br";
        ed.gui().openMain(pt.player);
        Inventory ptMain = pt.lastInventory();
        collect(ptMain);
        check("pt_BR GUI text", name(ptMain.getItem(19)).contains("Dano de blocos") && lore(ptMain.getItem(19)).contains("Clique"), name(ptMain.getItem(19)));
        ed.gui().handleClick(pt.player, ptMain, 25, ClickType.LEFT);
        collect(pt.lastInventory());
        for (int i = 19; i < 25; i++) check("rule menu slot " + i + " has an item", pt.lastInventory().getItem(i) != null
                && !pt.lastInventory().getItem(i).getType().name().contains("GLASS_PANE"));
    }

    // ------------------------------------------------------------------ untranslated text

    private void scanTexts() {
        List<String> raw = new ArrayList<>();
        for (String t : allTexts) {
            for (String line : t.split("\n")) {
                String plain = org.bukkit.ChatColor.stripColor(line).trim();
                if (!plain.isEmpty() && rawKey.matcher(plain).matches()) raw.add(plain);
            }
        }
        check("no raw language keys in messages or GUI items", raw.isEmpty(), raw.toString());
        check("collected texts to scan", allTexts.size() > 50, String.valueOf(allTexts.size()));
    }
}

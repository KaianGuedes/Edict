package dev.war.edict.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The built-in rules. */
public final class Rules {

    public static final String CREEPER = "creeper_block_damage";
    public static final String ENDERMAN = "enderman_block_theft";
    public static final String END_PORTAL = "enter_end_portal";
    public static final String VOID_FALL = "safe_void_fall";

    private static final Map<String, RuleDef> ALL;

    static {
        Map<String, RuleDef> m = new LinkedHashMap<>();
        add(m, new RuleDef(CREEPER, "CREEPER_HEAD", List.of(
                ParamDef.bool(RuleDef.ENABLED, true),
                ParamDef.bool("also-other-explosions", false))));
        add(m, new RuleDef(ENDERMAN, "ENDER_PEARL", List.of(
                ParamDef.bool(RuleDef.ENABLED, true),
                ParamDef.bool("allow-place", true))));
        add(m, new RuleDef(END_PORTAL, "END_PORTAL_FRAME", List.of(
                ParamDef.bool(RuleDef.ENABLED, true),
                ParamDef.string("message", ""))));
        add(m, new RuleDef(VOID_FALL, "FEATHER", List.of(
                ParamDef.bool(RuleDef.ENABLED, false),
                ParamDef.integer("the-end-y", -200, -4096, 256, 10),
                ParamDef.integer("overworld-y", 1624, -2048, 30000, 100),
                ParamDef.integer("max-height-margin", 8, 0, 512, 1),
                ParamDef.bool("fall-damage", true),
                ParamDef.bool("keep-velocity", false))));
        ALL = Collections.unmodifiableMap(m);
    }

    private Rules() {
    }

    private static void add(Map<String, RuleDef> m, RuleDef r) {
        m.put(r.id(), r);
    }

    public static Map<String, RuleDef> all() {
        return ALL;
    }

    public static RuleDef get(String id) {
        return ALL.get(id);
    }
}

package dev.war.edict.model;

import java.util.List;

/**
 * A rule and its parameters. The first parameter is always {@code enabled}.
 *
 * @param id     rule id as used in config and commands
 * @param icon   preferred GUI icon material name
 * @param params parameters, {@code enabled} first
 */
public record RuleDef(String id, String icon, List<ParamDef> params) {

    public static final String ENABLED = "enabled";

    public ParamDef param(String key) {
        for (ParamDef p : params) {
            if (p.key().equals(key)) return p;
        }
        return null;
    }
}

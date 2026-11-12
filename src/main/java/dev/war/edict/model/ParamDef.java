package dev.war.edict.model;

import java.util.Locale;

/**
 * One configurable parameter of a rule (for example {@code the-end-y} of {@code safe_void_fall}).
 *
 * @param key  config key
 * @param type value type
 * @param def  default value (Boolean, Integer or String)
 * @param min  inclusive minimum (INT only)
 * @param max  inclusive maximum (INT only)
 * @param step base click step in the GUI (INT only)
 */
public record ParamDef(String key, ParamType type, Object def, int min, int max, int step) {

    public static ParamDef bool(String key, boolean def) {
        return new ParamDef(key, ParamType.BOOLEAN, def, 0, 0, 0);
    }

    public static ParamDef integer(String key, int def, int min, int max, int step) {
        return new ParamDef(key, ParamType.INT, def, min, max, step);
    }

    public static ParamDef string(String key, String def) {
        return new ParamDef(key, ParamType.STRING, def, 0, 0, 0);
    }

    /** Converts a value read from YAML to the right type, or returns null when it does not fit. */
    public Object coerce(Object raw) {
        if (raw == null) return null;
        switch (type) {
            case BOOLEAN:
                if (raw instanceof Boolean) return raw;
                if (raw instanceof String s) {
                    String v = s.trim().toLowerCase(Locale.ROOT);
                    if (v.equals("true")) return Boolean.TRUE;
                    if (v.equals("false")) return Boolean.FALSE;
                }
                return null;
            case INT:
                long n;
                if (raw instanceof Number num) {
                    if (num.doubleValue() != Math.rint(num.doubleValue())) return null;
                    n = num.longValue();
                } else if (raw instanceof String s) {
                    try {
                        n = Long.parseLong(s.trim());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                } else {
                    return null;
                }
                if (n < min || n > max) return null;
                return (int) n;
            case STRING:
                return raw.toString();
            default:
                return null;
        }
    }

    /**
     * Parses user input (command or chat).
     *
     * @throws IllegalArgumentException whose message is a lang key: err.bool, err.number or err.range
     */
    public Object parse(String input) {
        String s = input.trim();
        switch (type) {
            case BOOLEAN:
                switch (s.toLowerCase(Locale.ROOT)) {
                    case "true", "on", "yes", "enable", "enabled", "sim", "ligado" -> {
                        return Boolean.TRUE;
                    }
                    case "false", "off", "no", "disable", "disabled", "nao", "não", "desligado" -> {
                        return Boolean.FALSE;
                    }
                    default -> throw new IllegalArgumentException("err.bool");
                }
            case INT:
                long n;
                try {
                    n = Long.parseLong(s);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("err.number");
                }
                if (n < min || n > max) throw new IllegalArgumentException("err.range");
                return (int) n;
            default:
                return input;
        }
    }

    /** Clamps an integer into the allowed range. */
    public int clamp(long value) {
        return (int) Math.max(min, Math.min(max, value));
    }
}

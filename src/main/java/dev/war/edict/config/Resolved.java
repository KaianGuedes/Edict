package dev.war.edict.config;

/** A resolved value and the scope it came from (null source means the built-in default). */
public record Resolved(Object value, Scope source) {
}

package com.saumya.pos.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/** Reads the one approved permission catalogue used by runtime authorization. */
public final class PermissionCatalogue {
    private static final JsonNode ROOT = load();
    private static final Set<String> VENDOR = values("vendorPermissions");
    private static final Set<String> PLATFORM = values("platformPermissions");
    private static final Set<String> CUSTOMER = values("customerPermissions");
    private static final Set<String> RESERVED_VENDOR = values("reservedVendorPermissions");
    private static final Set<String> ORPHANED = values("orphanedPermissions");
    private static final Set<String> CONFLICTING = conflictingValues();

    static {
        Set<String> classified = new LinkedHashSet<>();
        for (Set<String> group : Set.of(VENDOR, PLATFORM, CUSTOMER, RESERVED_VENDOR, ORPHANED, CONFLICTING)) {
            for (String permission : group) {
                if (!classified.add(permission)) {
                    throw new IllegalStateException("Permission appears in multiple catalogue classifications: " + permission);
                }
            }
        }
    }

    private PermissionCatalogue() { }

    public static Set<String> vendorPermissions() { return VENDOR; }
    public static Set<String> platformPermissions() { return PLATFORM; }
    public static Set<String> customerPermissions() { return CUSTOMER; }
    public static Set<String> reservedVendorPermissions() { return RESERVED_VENDOR; }
    public static Set<String> orphanedPermissions() { return ORPHANED; }
    public static Set<String> conflictingPermissions() { return CONFLICTING; }
    public static int version() { return ROOT.path("version").asInt(); }
    public static JsonNode json() { return ROOT.deepCopy(); }

    private static JsonNode load() {
        try (InputStream input = PermissionCatalogue.class.getResourceAsStream("/authorization-policy-registry.json")) {
            if (input == null) throw new IllegalStateException("Canonical authorization-policy-registry.json is missing");
            return new ObjectMapper().readTree(input);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load canonical permission catalogue", e);
        }
    }

    private static Set<String> values(String property) {
        JsonNode array = ROOT.path(property);
        if (!array.isArray()) throw new IllegalStateException("Permission catalogue field is not an array: " + property);
        TreeSet<String> values = new TreeSet<>();
        array.forEach(node -> {
            String value = node.asText("");
            if (value.isBlank() || !value.equals(value.strip()) || !value.equals(value.toUpperCase(java.util.Locale.ROOT))) {
                throw new IllegalStateException("Invalid canonical permission identifier in " + property + ": " + value);
            }
            if (!values.add(value)) throw new IllegalStateException("Duplicate permission identifier in " + property + ": " + value);
        });
        return Collections.unmodifiableSet(values);
    }

    private static Set<String> conflictingValues() {
        JsonNode array = ROOT.path("conflictingPermissions");
        if (!array.isArray()) throw new IllegalStateException("Permission catalogue conflictingPermissions must be an array");
        TreeSet<String> values = new TreeSet<>();
        array.forEach(node -> {
            String value = node.path("id").asText("");
            if (value.isBlank() || !values.add(value)) throw new IllegalStateException("Invalid or duplicate conflicting permission: " + value);
        });
        return Collections.unmodifiableSet(values);
    }
}

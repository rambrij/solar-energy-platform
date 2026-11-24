package com.saumya.pos.subscription;

/** Supplies the operator-configurable wording for a local mock billing reminder. */
public interface GraceReminderTemplate {
    record Wording(String subject, String body) {}

    Wording load();
}

package com.saumya.pos.subscription;

import java.util.Optional;

/** Supplies the operator-configurable wording for a local mock billing reminder. */
public interface GraceReminderTemplate {
    record Wording(String subject, String body) {}

    /** Returns no wording when reminder delivery is disabled. */
    Optional<Wording> load();
}

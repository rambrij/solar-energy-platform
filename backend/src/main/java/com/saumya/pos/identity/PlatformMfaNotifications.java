package com.saumya.pos.identity;

/** Activity notices only. Enrollment material never enters this contract. */
public interface PlatformMfaNotifications {
    enum Activity {
        ENROLLMENT_ISSUED,
        ENROLLMENT_COMPLETED
    }

    void afterCommit(String recipient, Activity activity);
}

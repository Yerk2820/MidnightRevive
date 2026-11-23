package dev.yerk2820.revive;

import java.util.UUID;

final class PlayerRecord {
    final UUID uuid;
    String name;
    boolean dead;
    long deathTime;          // epoch millis, 0 = never died
    boolean pendingRestore;  // revived, but not yet moved to their respawn point

    PlayerRecord(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }
}

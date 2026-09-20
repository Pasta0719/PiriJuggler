package jp.pirijuggler.paper.machine;

import java.util.UUID;

public record Machine(int id, Location location, MachineType type, int setting, boolean enabled, boolean autoSetting,
                      boolean deleted, int left, int center, int right, String runtimeJson,
                      long createdAt, long updatedAt) {
    /** Compatibility constructor for callers that do not use machine runtime state. */
    public Machine(int id, Location location, MachineType type, int setting, boolean enabled, boolean autoSetting,
                   boolean deleted, int left, int center, int right, long createdAt, long updatedAt) {
        this(id, location, type, setting, enabled, autoSetting, deleted, left, center, right, null, createdAt, updatedAt);
    }

    /** Compatibility constructor for existing callers: legacy machines are JUGGLER. */
    public Machine(int id, Location location, int setting, boolean enabled, boolean autoSetting,
                   boolean deleted, int left, int center, int right, long createdAt, long updatedAt) {
        this(id, location, MachineType.JUGGLER, setting, enabled, autoSetting, deleted, left, center, right, null, createdAt, updatedAt);
    }

    public Machine {
        if (type == null) type = MachineType.JUGGLER;
    }

    public record Location(UUID world, String worldName, int x, int y, int z, String facing) {
        public boolean sameBlock(Location other) {
            return world.equals(other.world) && x == other.x && y == other.y && z == other.z;
        }
    }
}

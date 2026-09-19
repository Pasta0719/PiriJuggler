package jp.pirijuggler.paper.machine;

import java.util.UUID;

public record Machine(int id, Location location, int setting, boolean enabled, boolean autoSetting,
                      boolean deleted, int left, int center, int right, long createdAt, long updatedAt) {
    /** Schema-v4 machines are all legacy Juggler machines until machine_type persistence is introduced. */
    public MachineType type() { return MachineType.JUGGLER; }
    public record Location(UUID world, String worldName, int x, int y, int z, String facing) {
        public boolean sameBlock(Location other) {
            return world.equals(other.world) && x == other.x && y == other.y && z == other.z;
        }
    }
}

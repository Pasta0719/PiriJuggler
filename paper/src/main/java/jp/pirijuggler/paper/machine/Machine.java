package jp.pirijuggler.paper.machine;

import java.util.UUID;

public record Machine(int id, Location location, int setting, boolean enabled, boolean autoSetting,
                      boolean deleted, int left, int center, int right, long createdAt, long updatedAt) {
    public record Location(UUID world, String worldName, int x, int y, int z, String facing) {
        public boolean sameBlock(Location other) {
            return world.equals(other.world) && x == other.x && y == other.y && z == other.z;
        }
    }
}

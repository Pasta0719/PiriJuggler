package jp.pirijuggler.fabric.render;

/** Pure placement math for the entity-free Phase13 cabinet plane. */
public final class CabinetPlacement {
    public static final double WIDTH = 1.00;
    public static final double HEIGHT = 1.00;
    public static final double FACE_OFFSET = 0.505;
    public static final double HORIZONTAL_UP_OFFSET = 1.05;
    public static final double DEPTH_BIAS = 0.002;

    public record Vec(double x, double y, double z) {
        public Vec add(Vec other) { return new Vec(x + other.x, y + other.y, z + other.z); }
        public Vec scale(double amount) { return new Vec(x * amount, y * amount, z * amount); }
    }

    public record Basis(Vec center, Vec front, Vec right, Vec up) {}

    private CabinetPlacement() {}

    public static Basis basis(int x, int y, int z, String facing) {
        Vec anchor = new Vec(x + 0.5, y + 0.5, z + 0.5);
        Vec front = switch (facing) {
            case "NORTH" -> new Vec(0, 0, -1);
            case "SOUTH" -> new Vec(0, 0, 1);
            case "EAST" -> new Vec(1, 0, 0);
            case "WEST" -> new Vec(-1, 0, 0);
            case "UP" -> new Vec(0, 1, 0);
            case "DOWN" -> new Vec(0, -1, 0);
            default -> throw new IllegalArgumentException("facing");
        };
        boolean vertical = facing.equals("UP") || facing.equals("DOWN");
        Vec up = vertical ? new Vec(0, 0, -1) : new Vec(0, 1, 0);
        Vec right;
        if (vertical) {
            right = new Vec(1, 0, 0);
        } else {
            // up x front gives screen-right as seen by a viewer standing in front.
            right = new Vec(
                    up.y * front.z - up.z * front.y,
                    up.z * front.x - up.x * front.z,
                    up.x * front.y - up.y * front.x);
        }
        Vec center = anchor.add(front.scale(FACE_OFFSET + DEPTH_BIAS));
        if (!vertical) center = center.add(up.scale(HORIZONTAL_UP_OFFSET));
        return new Basis(center, front, right, up);
    }

    public static Vec point(Basis basis, double localX, double localY) {
        return basis.center
                .add(basis.right.scale(localX))
                .add(basis.up.scale(localY));
    }
}

package jp.pirijuggler.fabric.render;

import jp.pirijuggler.fabric.PiriJugglerClient;
import jp.pirijuggler.fabric.network.RemoteMachineViewState;
import jp.pirijuggler.fabric.ui.UiConstants;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Single entity-free world renderer for all public remote Piri cabinets.
 * No entity, BlockEntity, per-machine tick callback, or network traffic is created here.
 */
public final class WorldCabinetRenderer {
    private static final double MAX_DISTANCE_SQ = 32.0 * 32.0;
    private static final int FULL_LIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;
    private static final Identifier WHITE = Identifier.of("piri", "textures/world/white.png");
    private static final Identifier LAMP_ON = Identifier.of("piri", "textures/lamp/piri_chance_on.png");
    private static final Identifier LAMP_OFF = Identifier.of("piri", "textures/lamp/piri_chance_off.png");

    private WorldCabinetRenderer() {}

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(WorldCabinetRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || context.world() == null || context.camera() == null || context.consumers() == null) return;

        Vec3d camera = context.camera().getPos();
        String dimension = context.world().getRegistryKey().getValue().toString();
        long now = System.nanoTime();

        for (RemoteMachineViewState state : PiriJugglerClient.remoteMachines().viewSnapshot()) {
            if (!state.dimension().isEmpty() && !dimension.equals(state.dimension())) continue;

            double ax = state.x() + 0.5;
            double ay = state.y() + 0.5;
            double az = state.z() + 0.5;
            double dx = camera.x - ax, dy = camera.y - ay, dz = camera.z - az;
            if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE_SQ) continue;

            CabinetPlacement.Basis basis = CabinetPlacement.basis(state.x(), state.y(), state.z(), state.facing());
            if (context.frustum() != null) {
                CabinetPlacement.Vec c = basis.center();
                Box bounds = new Box(c.x() - 1.1, c.y() - 1.1, c.z() - 1.1,
                        c.x() + 1.1, c.y() + 1.1, c.z() + 1.1);
                if (!context.frustum().isVisible(bounds)) continue;
            }

            drawCabinet(context, state, basis, camera, now, client);
        }
    }

    private static void drawCabinet(WorldRenderContext context, RemoteMachineViewState state,
                                    CabinetPlacement.Basis basis, Vec3d camera, long now,
                                    MinecraftClient client) {
        VertexConsumerProvider consumers = context.consumers();

        // One integrated cabinet face.
        quad(consumers, WHITE, basis, camera, 0, 0, 1.60, 1.00, 0xff17191d, 0, 0, 1, 1);

        // Header / lamp region.
        quad(consumers, WHITE, basis, camera, 0, 0.385, 1.48, 0.16,
                state.enabled() ? 0xff292d34 : 0xff171717, 0, 0, 1, 1);
        boolean lamp = state.lampVisible(now);
        quad(consumers, lamp ? LAMP_ON : LAMP_OFF, basis, camera, 0.52, 0.385,
                0.34, 0.14, 0xffffffff, 0, 0, 1, 1);

        // Three reel windows and smooth client-side reel motion.
        final double[] reelX = {-0.49, 0.0, 0.49};
        for (int reel = 0; reel < 3; reel++) {
            double cx = reelX[reel];
            double windowY = 0.045;
            double windowW = 0.43;
            double windowH = 0.56;
            quad(consumers, WHITE, basis, camera, cx, windowY, windowW, windowH,
                    0xff050505, 0, 0, 1, 1);

            double phase = state.phase(reel, now);
            int middle = (int)Math.floor(phase);
            double fraction = phase - middle;
            for (int row = -2; row <= 2; row++) {
                String symbol = UiConstants.symbol(reel, middle + row);
                double symbolY = windowY + (row - fraction) * 0.185;
                double symbolW = (symbol.equals("seven") || symbol.equals("bar")) ? 0.31 : 0.20;
                double symbolH = symbol.equals("bar") ? 0.115 : 0.17;
                clippedSymbol(consumers, symbol, basis, camera, cx, symbolY, symbolW, symbolH,
                        windowY - windowH / 2.0, windowY + windowH / 2.0);
            }
        }

        // Lower status strip stays public-only.
        quad(consumers, WHITE, basis, camera, 0, -0.355, 1.48, 0.18, 0xff22262c, 0, 0, 1, 1);
        String status = "CREDIT " + state.credit() + "   PAY " + state.pay();
        String bonus = "NONE".equals(state.bonusMode()) ? "" : state.bonusMode() + " " + state.bonusCount();
        drawText(consumers, client.textRenderer, basis, camera, -0.69, -0.318, status, 0xfff2f2f2);
        if (!bonus.isEmpty()) drawText(consumers, client.textRenderer, basis, camera, 0.20, -0.318, bonus, 0xffffd45a);
    }

    private static void clippedSymbol(VertexConsumerProvider consumers, String symbol,
                                      CabinetPlacement.Basis basis, Vec3d camera,
                                      double cx, double cy, double width, double height,
                                      double clipMinY, double clipMaxY) {
        double bottom = cy - height / 2.0;
        double top = cy + height / 2.0;
        double clippedBottom = Math.max(bottom, clipMinY);
        double clippedTop = Math.min(top, clipMaxY);
        if (clippedBottom >= clippedTop) return;

        double visibleHeight = clippedTop - clippedBottom;
        double visibleCenter = (clippedBottom + clippedTop) / 2.0;
        float vTop = (float)((top - clippedTop) / height);
        float vBottom = (float)((top - clippedBottom) / height);
        Identifier texture = Identifier.of("piri", "textures/symbols/" + symbol + ".png");
        quad(consumers, texture, basis, camera, cx, visibleCenter, width, visibleHeight,
                0xffffffff, 0, vTop, 1, vBottom);
    }

    private static void quad(VertexConsumerProvider consumers, Identifier texture,
                             CabinetPlacement.Basis basis, Vec3d camera,
                             double cx, double cy, double width, double height,
                             int color, float u0, float v0, float u1, float v1) {
        double left = cx - width / 2.0, right = cx + width / 2.0;
        double bottom = cy - height / 2.0, top = cy + height / 2.0;

        CabinetPlacement.Vec p0 = cameraRelative(CabinetPlacement.point(basis, left, bottom), camera);
        CabinetPlacement.Vec p1 = cameraRelative(CabinetPlacement.point(basis, right, bottom), camera);
        CabinetPlacement.Vec p2 = cameraRelative(CabinetPlacement.point(basis, right, top), camera);
        CabinetPlacement.Vec p3 = cameraRelative(CabinetPlacement.point(basis, left, top), camera);
        CabinetPlacement.Vec n = basis.front();

        VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
        vertex(vc, p0, n, color, u0, v1);
        vertex(vc, p1, n, color, u1, v1);
        vertex(vc, p2, n, color, u1, v0);
        vertex(vc, p3, n, color, u0, v0);
    }

    private static void vertex(VertexConsumer vc, CabinetPlacement.Vec p, CabinetPlacement.Vec n,
                               int color, float u, float v) {
        int a = color >>> 24 & 255;
        int r = color >>> 16 & 255;
        int g = color >>> 8 & 255;
        int b = color & 255;
        vc.vertex((float)p.x(), (float)p.y(), (float)p.z())
                .color(r, g, b, a)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(FULL_LIGHT)
                .normal((float)n.x(), (float)n.y(), (float)n.z());
    }

    private static void drawText(VertexConsumerProvider consumers, TextRenderer textRenderer,
                                 CabinetPlacement.Basis basis, Vec3d camera,
                                 double localX, double localY, String text, int color) {
        final float scale = 0.00235f;
        CabinetPlacement.Vec origin = CabinetPlacement.point(basis, localX, localY)
                .add(basis.front().scale(0.003));
        CabinetPlacement.Vec t = cameraRelative(origin, camera);
        CabinetPlacement.Vec r = basis.right();
        CabinetPlacement.Vec u = basis.up();
        CabinetPlacement.Vec f = basis.front();

        Matrix4f matrix = new Matrix4f().identity();
        matrix.m00((float)(r.x() * scale)).m01((float)(r.y() * scale)).m02((float)(r.z() * scale));
        matrix.m10((float)(-u.x() * scale)).m11((float)(-u.y() * scale)).m12((float)(-u.z() * scale));
        matrix.m20((float)f.x()).m21((float)f.y()).m22((float)f.z());
        matrix.m30((float)t.x()).m31((float)t.y()).m32((float)t.z());

        textRenderer.draw(text, 0, 0, color, false, matrix, consumers,
                TextRenderer.TextLayerType.NORMAL, 0, FULL_LIGHT);
    }

    private static CabinetPlacement.Vec cameraRelative(CabinetPlacement.Vec p, Vec3d camera) {
        return new CabinetPlacement.Vec(p.x() - camera.x, p.y() - camera.y, p.z() - camera.z);
    }
}

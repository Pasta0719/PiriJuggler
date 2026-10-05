package jp.pirijuggler.paper.mobile;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import jp.pirijuggler.common.protocol.PacketType;
import jp.pirijuggler.paper.PiriJugglerPlugin;
import jp.pirijuggler.paper.machine.Machine;
import jp.pirijuggler.paper.machine.MachineService;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Minimal iPhone/Safari remote controller.
 *
 * HTTP is multiplexed onto the already-open Minecraft TCP port by sniffing the first
 * bytes of each accepted connection. Normal Minecraft connections are passed through
 * untouched. Browser requests are removed from the Minecraft packet pipeline and
 * handled as short-lived HTTP requests.
 */
public final class MobileRemoteGateway implements AutoCloseable {
    private static final Key LISTENER_KEY = Key.key("piri", "mobile-http");
    private static final long PAIR_TTL_MS = 5 * 60_000L;
    private static final Set<PacketType> REMOTE_ACTIONS = Set.of(
            PacketType.SPACE_ACTION, PacketType.STOP_LEFT, PacketType.STOP_CENTER, PacketType.STOP_RIGHT);
    private static final Gson GSON = new Gson();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PiriJugglerPlugin plugin;
    private final MachineService machines;
    private final Map<String, Pairing> pairings = new ConcurrentHashMap<>();
    /** SHA-256(token) -> player UUID. Raw browser tokens are never stored server-side. */
    private final Map<String, UUID> tokenHashes = new ConcurrentHashMap<>();
    private final RemoteNpcService npcs = new RemoteNpcService();

    private Class<?> listenerHolder;
    private Object listenerProxy;
    private volatile boolean closed;
    private boolean pairingsLoadRequested;
    private boolean pairingsLoaded;

    private record Pairing(UUID owner, long expiresAt) {}

    public MobileRemoteGateway(PiriJugglerPlugin plugin, MachineService machines) {
        this.plugin = plugin;
        this.machines = machines;
        installSamePortListener();
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!closed && machines.ready()) {
                npcs.retain(machines.mobileActiveOwners());
                if (!pairingsLoaded && !pairingsLoadRequested) {
                    pairingsLoadRequested = true;
                    machines.mobileLoadPairings((loaded, failure) -> {
                        pairingsLoadRequested = false;
                        if (failure == null) {
                            tokenHashes.clear();
                            tokenHashes.putAll(loaded);
                            pairingsLoaded = true;
                            plugin.getLogger().info("PIRI_MOBILE_PAIRINGS_LOADED count=" + loaded.size());
                        }
                    });
                }
            }
            long now = System.currentTimeMillis();
            pairings.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
        }, 1L, 20L);
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("mobile")) return false;
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("この操作はプレイヤーから実行してください。"));
            return true;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("pair")) {
            pairings.entrySet().removeIf(e -> e.getValue().owner().equals(player.getUniqueId()));
            String code;
            do code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
            while (pairings.containsKey(code));
            pairings.put(code, new Pairing(player.getUniqueId(), System.currentTimeMillis() + PAIR_TTL_MS));
            player.sendMessage(Component.text("スマホ接続コード: " + code + "  (5分間有効)"));
            player.sendMessage(Component.text("Safariで http://<サーバーアドレス>:<Minecraftポート>/ を開いて入力してください。"));
            return true;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("revoke")) {
            UUID owner = player.getUniqueId();
            revokeLocal(owner);
            machines.mobileRevokePairing(owner, failure -> {
                if (!player.isOnline()) return;
                if (failure == null) player.sendMessage(Component.text("スマホ接続を解除しました。"));
                else player.sendMessage(Component.text("スマホ接続の解除に失敗しました: " + failure));
            });
            return true;
        }
        player.sendMessage(Component.text("/piri mobile pair | /piri mobile revoke"));
        return true;
    }

    private void revokeLocal(UUID owner) {
        pairings.entrySet().removeIf(e -> e.getValue().owner().equals(owner));
        tokenHashes.entrySet().removeIf(e -> e.getValue().equals(owner));
        npcs.remove(owner);
    }

    private void installSamePortListener() {
        try {
            listenerHolder = Class.forName("io.papermc.paper.network.ChannelInitializeListenerHolder");
            Class<?> listenerType = Class.forName("io.papermc.paper.network.ChannelInitializeListener");
            listenerProxy = Proxy.newProxyInstance(
                    listenerType.getClassLoader(),
                    new Class<?>[]{listenerType},
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return switch (method.getName()) {
                                case "toString" -> "PiriMobileChannelListener";
                                case "hashCode" -> System.identityHashCode(proxy);
                                case "equals" -> proxy == args[0];
                                default -> null;
                            };
                        }
                        if (method.getName().equals("afterInitChannel") && args != null && args.length == 1 && args[0] instanceof Channel channel) {
                            installSniffer(channel);
                        }
                        return null;
                    });
            Method add = listenerHolder.getMethod("addListener", Key.class, listenerType);
            add.invoke(null, LISTENER_KEY, listenerProxy);
            plugin.getLogger().info("PIRI_MOBILE_HTTP_READY samePort=true");
        } catch (ReflectiveOperationException | LinkageError failure) {
            listenerHolder = null;
            listenerProxy = null;
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Piri mobile HTTP disabled: this Paper build does not expose the channel initialization hook", failure);
        }
    }

    private void installSniffer(Channel channel) {
        try {
            if (closed || channel.pipeline().get("piri-mobile-sniffer") != null) return;
            channel.pipeline().addFirst("piri-mobile-sniffer", new ProtocolSniffer());
        } catch (RuntimeException failure) {
            plugin.getLogger().fine("Could not install mobile protocol sniffer: " + failure.getMessage());
        }
    }

    private final class ProtocolSniffer extends ByteToMessageDecoder {
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            if (in.readableBytes() < 4) return;
            int i = in.readerIndex();
            boolean http = matches(in, i, "GET ") || matches(in, i, "POST") || matches(in, i, "HEAD") || matches(in, i, "OPTI");
            if (!http) {
                ByteBuf passthrough = in.readRetainedSlice(in.readableBytes());
                ctx.pipeline().remove(this);
                out.add(passthrough);
                return;
            }

            ByteBuf first = in.readRetainedSlice(in.readableBytes());
            ChannelPipeline pipeline = ctx.pipeline();
            String self = ctx.name();
            for (String name : new ArrayList<>(pipeline.names())) {
                if (!name.equals(self) && pipeline.get(name) != null) pipeline.remove(name);
            }
            if (pipeline.get(self) != null) pipeline.remove(self);
            pipeline.addLast("piri-mobile-http-codec", new HttpServerCodec());
            pipeline.addLast("piri-mobile-http-aggregate", new HttpObjectAggregator(64 * 1024));
            pipeline.addLast("piri-mobile-http-handler", new HttpHandler());
            pipeline.fireChannelRead(first);
        }

        private boolean matches(ByteBuf in, int start, String text) {
            if (in.readableBytes() < text.length()) return false;
            for (int x = 0; x < text.length(); x++) {
                if ((char) in.getUnsignedByte(start + x) != text.charAt(x)) return false;
            }
            return true;
        }
    }

    private final class HttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
            QueryStringDecoder query = new QueryStringDecoder(request.uri());
            String path = query.path();
            if (request.method().equals(HttpMethod.GET) && path.equals("/")) {
                respond(ctx, HttpResponseStatus.OK, "text/html; charset=utf-8", PAGE);
                return;
            }
            if (request.method().equals(HttpMethod.GET) && path.equals("/favicon.ico")) {
                respond(ctx, HttpResponseStatus.NO_CONTENT, "text/plain", "");
                return;
            }
            if (request.method().equals(HttpMethod.OPTIONS)) {
                respond(ctx, HttpResponseStatus.NO_CONTENT, "text/plain", "");
                return;
            }
            if (path.equals("/api/pair") && request.method().equals(HttpMethod.POST)) {
                String code = one(query, "code");
                Pairing pairing = code == null ? null : pairings.remove(code);
                if (pairing == null || pairing.expiresAt() < System.currentTimeMillis()) {
                    json(ctx, HttpResponseStatus.UNAUTHORIZED, error("PAIR_CODE_INVALID"));
                    return;
                }
                String token = token();
                String tokenHash = hashToken(token);
                onMain(ctx, done -> machines.mobileSavePairing(pairing.owner(), tokenHash, failure -> {
                    if (failure != null) {
                        done.accept(null, failure);
                        return;
                    }
                    tokenHashes.entrySet().removeIf(e -> e.getValue().equals(pairing.owner()));
                    tokenHashes.put(tokenHash, pairing.owner());
                    pairingsLoaded = true;
                    JsonObject body = ok();
                    body.addProperty("token", token);
                    body.addProperty("player", playerName(pairing.owner()));
                    done.accept(body, null);
                }));
                return;
            }

            if (!pairingsLoaded) {
                json(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, error("AUTH_LOADING"));
                return;
            }
            String token = request.headers().get("X-Piri-Token");
            UUID owner = token == null ? null : tokenHashes.get(hashToken(token));
            if (owner == null) {
                json(ctx, HttpResponseStatus.UNAUTHORIZED, error("AUTH_REQUIRED"));
                return;
            }

            if (request.method().equals(HttpMethod.GET) && path.equals("/api/machines")) {
                onMain(ctx, done -> done.accept(machines.mobileMachines(owner), null));
                return;
            }
            if (request.method().equals(HttpMethod.GET) && path.equals("/api/state")) {
                onMain(ctx, done -> {
                    JsonObject state = machines.mobileState(owner);
                    if (!state.has("seated") || !state.get("seated").getAsBoolean()) npcs.remove(owner);
                    done.accept(state, null);
                });
                return;
            }
            if (request.method().equals(HttpMethod.POST) && path.equals("/api/seat")) {
                Integer machineId = integer(one(query, "id"));
                if (machineId == null) {
                    json(ctx, HttpResponseStatus.BAD_REQUEST, error("INVALID_MACHINE"));
                    return;
                }
                onMain(ctx, done -> machines.mobileSeat(owner, machineId, (state, failure) -> {
                    if (failure == null) {
                        Machine machine = machines.mobileMachine(machineId);
                        if (machine != null) npcs.seat(owner, machine);
                    }
                    done.accept(state, failure);
                }));
                return;
            }
            if (request.method().equals(HttpMethod.POST) && path.equals("/api/action")) {
                PacketType type;
                try {
                    type = PacketType.valueOf(String.valueOf(one(query, "type")).toUpperCase(Locale.ROOT));
                } catch (RuntimeException invalid) {
                    json(ctx, HttpResponseStatus.BAD_REQUEST, error("INVALID_ACTION"));
                    return;
                }
                if (!REMOTE_ACTIONS.contains(type)) {
                    json(ctx, HttpResponseStatus.BAD_REQUEST, error("INVALID_ACTION"));
                    return;
                }
                Integer pressed = integer(one(query, "pressed"));
                onMain(ctx, done -> machines.mobileAction(owner, type, pressed, done));
                return;
            }
            if (request.method().equals(HttpMethod.POST) && path.equals("/api/loan")) {
                onMain(ctx, done -> machines.mobileLoan(owner, done));
                return;
            }
            if (request.method().equals(HttpMethod.POST) && path.equals("/api/leave")) {
                onMain(ctx, done -> machines.mobileLeave(owner, (state, failure) -> {
                    if (failure == null) npcs.remove(owner);
                    done.accept(state, failure);
                }));
                return;
            }
            if (request.method().equals(HttpMethod.POST) && path.equals("/api/revoke")) {
                onMain(ctx, done -> machines.mobileRevokePairing(owner, failure -> {
                    if (failure == null) {
                        revokeLocal(owner);
                        done.accept(ok(), null);
                    } else done.accept(null, failure);
                }));
                return;
            }
            json(ctx, HttpResponseStatus.NOT_FOUND, error("NOT_FOUND"));
        }
    }

    @FunctionalInterface
    private interface MainRequest {
        void run(BiConsumer<JsonObject, String> done);
    }

    private void onMain(ChannelHandlerContext ctx, MainRequest work) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (closed) {
                complete(ctx, null, "SHUTDOWN");
                return;
            }
            try {
                work.run((body, failure) -> complete(ctx, body, failure));
            } catch (RuntimeException failure) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Mobile request failed", failure);
                complete(ctx, null, "DB_ERROR");
            }
        });
    }

    private void complete(ChannelHandlerContext ctx, JsonObject body, String failure) {
        ctx.executor().execute(() -> {
            if (failure != null) {
                json(ctx, HttpResponseStatus.CONFLICT, error(failure));
            } else {
                JsonObject result = body == null ? ok() : body;
                if (!result.has("ok")) result.addProperty("ok", true);
                json(ctx, HttpResponseStatus.OK, result);
            }
        });
    }

    private static JsonObject ok() {
        JsonObject json = new JsonObject();
        json.addProperty("ok", true);
        return json;
    }

    private static JsonObject error(String code) {
        JsonObject json = new JsonObject();
        json.addProperty("ok", false);
        json.addProperty("error", code);
        return json;
    }

    private void json(ChannelHandlerContext ctx, HttpResponseStatus status, JsonObject json) {
        respond(ctx, status, "application/json; charset=utf-8", GSON.toJson(json));
    }

    private void respond(ChannelHandlerContext ctx, HttpResponseStatus status, String contentType, String body) {
        if (!ctx.channel().isActive()) return;
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-store");
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        response.headers().set("X-Content-Type-Options", "nosniff");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static String one(QueryStringDecoder query, String key) {
        List<String> values = query.parameters().get(key);
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    private static Integer integer(String value) {
        try { return value == null ? null : Integer.valueOf(value); }
        catch (NumberFormatException invalid) { return null; }
    }

    private static String token() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String playerName(UUID owner) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(owner);
        return player.getName() == null ? owner.toString() : player.getName();
    }

    @Override
    public void close() {
        closed = true;
        pairings.clear();
        tokenHashes.clear();
        npcs.clear();
        if (listenerHolder != null) {
            try {
                Method remove = listenerHolder.getMethod("removeListener", Key.class);
                remove.invoke(null, LISTENER_KEY);
            } catch (ReflectiveOperationException failure) {
                plugin.getLogger().fine("Could not remove mobile channel listener: " + failure.getMessage());
            }
        }
    }

    private static final class RemoteNpcService {
        private final Map<UUID, UUID> entities = new java.util.HashMap<>();

        void seat(UUID owner, Machine machine) {
            remove(owner);
            World world = Bukkit.getWorld(machine.location().world());
            if (world == null) world = Bukkit.getWorld(machine.location().worldName());
            if (world == null) return;

            BlockFace front = face(machine.location().facing());
            Vector offset = front.getDirection().multiply(1.10);
            Location location = new Location(
                    world,
                    machine.location().x() + 0.5 + offset.getX(),
                    machine.location().y() - 0.80,
                    machine.location().z() + 0.5 + offset.getZ(),
                    yaw(front.getOppositeFace()),
                    0.0f);

            ArmorStand stand = world.spawn(location, ArmorStand.class, npc -> {
                npc.setPersistent(false);
                npc.setGravity(false);
                npc.setInvulnerable(true);
                npc.setCollidable(false);
                npc.setBasePlate(false);
                npc.setArms(true);
                npc.setCustomNameVisible(true);
                npc.customName(Component.text(playerName(owner) + " [REMOTE]"));
                npc.setBodyPose(new EulerAngle(0.18, 0, 0));
                npc.setLeftLegPose(new EulerAngle(-1.10, 0, 0));
                npc.setRightLegPose(new EulerAngle(-1.10, 0, 0));
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
                head.setItemMeta(meta);
                if (npc.getEquipment() != null) {
                    npc.getEquipment().setHelmet(head);
                    npc.getEquipment().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
                    npc.getEquipment().setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
                    npc.getEquipment().setBoots(new ItemStack(Material.LEATHER_BOOTS));
                }
            });
            entities.put(owner, stand.getUniqueId());
        }

        void remove(UUID owner) {
            UUID id = entities.remove(owner);
            if (id == null) return;
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) entity.remove();
        }

        void retain(Set<UUID> activeOwners) {
            for (UUID owner : List.copyOf(entities.keySet())) {
                if (!activeOwners.contains(owner)) remove(owner);
            }
        }

        void clear() {
            for (UUID owner : List.copyOf(entities.keySet())) remove(owner);
        }

        private static BlockFace face(String value) {
            try {
                BlockFace result = BlockFace.valueOf(value == null ? "SOUTH" : value.toUpperCase(Locale.ROOT));
                return result.getModX() == 0 && result.getModZ() == 0 ? BlockFace.SOUTH : result;
            } catch (IllegalArgumentException invalid) {
                return BlockFace.SOUTH;
            }
        }

        private static float yaw(BlockFace face) {
            return switch (face) {
                case SOUTH -> 0.0f;
                case WEST -> 90.0f;
                case NORTH -> 180.0f;
                case EAST -> -90.0f;
                default -> 0.0f;
            };
        }
    }

    private static final String PAGE = """
<!doctype html>
<html lang="ja">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover,user-scalable=no">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
<title>Piri Remote</title>
<style>
:root{color-scheme:dark;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}
*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}
body{margin:0;background:#0b0c10;color:#f5f5f5;min-height:100vh}
main{max-width:560px;margin:auto;padding:18px 16px 40px}
h1{font-size:22px;margin:4px 0 16px}.muted{color:#9da3ae;font-size:13px}
.card{background:#16181e;border:1px solid #292d36;border-radius:18px;padding:16px;margin:12px 0}
input,button{font:inherit;font-size:16px}input{width:100%;padding:14px;border-radius:12px;border:1px solid #3a3f49;background:#0f1116;color:white;margin:8px 0}
button{border:0;border-radius:14px;min-height:52px;padding:12px 16px;background:#343944;color:#fff;font-weight:700}
button.primary{background:#f0c24a;color:#17130a}button.danger{background:#5a2525}button:disabled{opacity:.4}
.row{display:flex;gap:10px}.row>*{flex:1}.hidden{display:none!important}
.machine{display:flex;align-items:center;gap:12px;border-bottom:1px solid #292d36;padding:12px 0}.machine:last-child{border-bottom:0}.machine .info{flex:1}
.badge{font-size:11px;padding:4px 7px;border:1px solid #444b57;border-radius:999px;color:#c9ced7}
.top{display:flex;justify-content:space-between;gap:12px;align-items:flex-start}.value{font-size:28px;font-variant-numeric:tabular-nums;font-weight:800}.label{font-size:11px;color:#9da3ae}
.reels{display:grid;grid-template-columns:repeat(3,1fr);gap:6px;background:#050607;border-radius:14px;padding:8px;margin:16px 0}
.reel{background:#f5f0dd;color:#121212;border-radius:8px;text-align:center;padding:26px 4px;font-weight:900;font-size:24px;min-width:0}
.controls{display:grid;grid-template-columns:repeat(3,1fr);gap:10px}.stop{min-height:64px;font-size:18px}
.space{width:100%;min-height:62px;margin:10px 0;font-size:18px}
#msg{min-height:20px;color:#ffd36b;font-size:13px;margin-top:8px}
</style>
</head>
<body><main>
<h1>Piri Remote</h1>
<section id="pair" class="card">
<div>スマホ接続</div><div class="muted">Minecraftで <b>/piri mobile pair</b> を実行し、6桁コードを入力</div>
<input id="code" inputmode="numeric" maxlength="6" placeholder="000000">
<button id="pairBtn" class="primary" style="width:100%">接続</button>
<div id="pairMsg" class="muted"></div>
</section>

<section id="lobby" class="hidden">
<div class="card top"><div><div class="label">PLAYER</div><div id="player">-</div></div><button id="logout">接続解除</button></div>
<div class="card"><div class="top"><b>台一覧</b><button id="refresh">更新</button></div><div id="machines"></div></div>
</section>

<section id="game" class="hidden">
<div class="card">
<div class="top"><div><div id="machineTitle">-</div><span id="gameState" class="badge">-</span></div><button id="leave" class="danger">離席</button></div>
<div class="row" style="margin-top:16px">
<div><div class="label">CREDIT</div><div id="credit" class="value">0</div></div>
<div><div class="label">PAY</div><div id="pay" class="value">0</div></div>
<div><div class="label">BET</div><div id="bet" class="value">0</div></div>
</div>
<div class="reels"><div id="r0" class="reel">--</div><div id="r1" class="reel">--</div><div id="r2" class="reel">--</div></div>
<button id="space" class="space primary">BET / LEVER</button>
<div class="controls"><button class="stop" data-action="STOP_LEFT">左 STOP</button><button class="stop" data-action="STOP_CENTER">中 STOP</button><button class="stop" data-action="STOP_RIGHT">右 STOP</button></div>
<div class="row" style="margin-top:10px"><button id="loan">貸出</button><button id="back">台一覧</button></div>
<div id="msg"></div>
</div>
</section>
</main>
<script>
(()=> {
const $=id=>document.getElementById(id);
let token=localStorage.getItem("piriToken")||"", player=localStorage.getItem("piriPlayer")||"", timer=0, busy=false;
const pair=$("pair"),lobby=$("lobby"),game=$("game");
function show(name){pair.classList.toggle("hidden",name!=="pair");lobby.classList.toggle("hidden",name!=="lobby");game.classList.toggle("hidden",name!=="game")}
async function api(path,method="GET"){
 const r=await fetch(path,{method,headers:token?{"X-Piri-Token":token}:{}});
 let j={};try{j=await r.json()}catch{}
 if(r.status===401&&path!=="/api/pair"){token="";localStorage.removeItem("piriToken");show("pair");throw new Error("接続が無効です")}
 if(!r.ok||j.ok===false)throw new Error(j.error||("HTTP "+r.status));
 return j;
}
function errorText(e){const m={BUSY:"処理中です",INVALID_STATE:"今は操作できません",NOT_ENOUGH_CREDIT:"クレジットが足りません",NOT_ENOUGH_VAULT:"所持金が足りません",MACHINE_OCCUPIED:"ほかのプレイヤーが遊技中です",MACHINE_DISABLED:"この台は利用できません",STOP_TOO_EARLY:"まだ停止できません",ALREADY_STOPPED:"停止済みです",SESSION_MISMATCH:"台との接続状態が変わりました",VAULT_ERROR:"所持金処理に失敗しました",ECONOMY_UNAVAILABLE:"貸出を利用できません",AUTH_LOADING:"サーバー起動中です。少しして自動再接続します"};return m[e.message]||e.message}
async function pairNow(){
 $("pairMsg").textContent="接続中…";
 try{const code=$("code").value.trim();const j=await api("/api/pair?code="+encodeURIComponent(code),"POST");token=j.token;player=j.player||"";localStorage.setItem("piriToken",token);localStorage.setItem("piriPlayer",player);$("player").textContent=player;show("lobby");await loadMachines()}
 catch(e){$("pairMsg").textContent=errorText(e)}
}
async function loadMachines(){
 try{const j=await api("/api/machines");$("player").textContent=player||j.player||"-";const box=$("machines");box.textContent="";
 (j.machines||[]).forEach(m=>{const row=document.createElement("div");row.className="machine";const info=document.createElement("div");info.className="info";info.innerHTML="<b>台"+m.id+"</b> "+m.type+"<br><span class=muted>設定 "+m.setting+" / "+(m.busy?(m.owned?"自分が遊技中":"遊技中"):"空き")+"</span>";const b=document.createElement("button");b.textContent=m.owned?"再開":"遊ぶ";b.disabled=!m.supported||(!m.enabled)|| (m.busy&&!m.owned);b.addEventListener("click",()=>seat(m.id));row.append(info,b);box.append(row)});
 }catch(e){$("machines").textContent=errorText(e)}
}
async function seat(id){try{await api("/api/seat?id="+id,"POST");show("game");await state()}catch(e){alert(errorText(e))}}
function render(j){
 if(!j.seated){clearInterval(timer);show("lobby");loadMachines();return}
 $("machineTitle").textContent="台"+j.machineId+" / "+(j.machineType||"");
 $("gameState").textContent=j.gameState||"-";$("credit").textContent=j.credit??0;$("pay").textContent=j.pay??0;$("bet").textContent=j.bet??0;
 const stops=j.displayStops||{};const spinning=String(j.gameState||"").includes("SPINNING");
 $("r0").textContent=spinning&&!(j.stoppedMask&1)?"◌":("STOP "+(stops.left??"-"));
 $("r1").textContent=spinning&&!(j.stoppedMask&2)?"◌":("STOP "+(stops.center??"-"));
 $("r2").textContent=spinning&&!(j.stoppedMask&4)?"◌":("STOP "+(stops.right??"-"));
 $("msg").textContent=j.godFreeze?"GOD FREEZE":(j.lampOn?"BONUS":"");
}
async function state(){try{render(await api("/api/state"))}catch(e){$("msg").textContent=errorText(e)}}
async function action(type){if(busy)return;busy=true;try{render(await api("/api/action?type="+type,"POST"))}catch(e){$("msg").textContent=errorText(e)}finally{busy=false}}
async function startGame(){show("game");await state();clearInterval(timer);timer=setInterval(state,350)}
$("pairBtn").addEventListener("click",pairNow);
$("refresh").addEventListener("click",loadMachines);
$("space").addEventListener("click",()=>action("SPACE_ACTION"));
document.querySelectorAll(".stop").forEach(b=>b.addEventListener("click",()=>action(b.dataset.action)));
$("loan").addEventListener("click",async()=>{try{render(await api("/api/loan","POST"))}catch(e){$("msg").textContent=errorText(e)}});
$("leave").addEventListener("click",async()=>{try{await api("/api/leave","POST");clearInterval(timer);show("lobby");loadMachines()}catch(e){$("msg").textContent=errorText(e)}});
$("back").addEventListener("click",()=>{clearInterval(timer);show("lobby");loadMachines()});
$("logout").addEventListener("click",async()=>{try{await api("/api/revoke","POST")}catch{} token="";localStorage.removeItem("piriToken");localStorage.removeItem("piriPlayer");show("pair")});
if(token){$("player").textContent=player;api("/api/state").then(j=>{if(j.seated){startGame()}else{show("lobby");loadMachines()}}).catch(e=>{if(e.message==="AUTH_LOADING")setTimeout(()=>location.reload(),1000);else show("pair")})}else show("pair");
})();
</script>
</body>
</html>
""";
}

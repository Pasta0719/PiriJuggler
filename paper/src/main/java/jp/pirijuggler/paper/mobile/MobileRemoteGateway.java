package jp.pirijuggler.paper.mobile;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
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
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
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
 * Mobile HTTP runs on the server's separately allocated web port. This keeps the
 * Minecraft listener untouched while still using the same AGAMES server/process.
 */
public final class MobileRemoteGateway implements AutoCloseable, Listener {
    private static final int HTTP_PORT = 10271;
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
    /** Owners whose authoritative seat originated from mobile, including an in-flight seat request. */
    private final Set<UUID> remoteOwners = ConcurrentHashMap.newKeySet();
    private final RemoteNpcService npcs = new RemoteNpcService();

    private EventLoopGroup httpGroup;
    private volatile Channel httpServerChannel;
    private volatile String httpBindError;
    private volatile boolean closed;
    private boolean pairingsLoadRequested;
    private boolean pairingsLoaded;

    private record Pairing(UUID owner, long expiresAt) {}

    public MobileRemoteGateway(PiriJugglerPlugin plugin, MachineService machines) {
        this.plugin = plugin;
        this.machines = machines;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTask(plugin, npcs::sweepLoaded);
        installDedicatedHttpListener();
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
        if (args.length == 2 && args[1].equalsIgnoreCase("status")) {
            if (httpServerChannel != null && httpServerChannel.isActive()) {
                player.sendMessage(Component.text("Piri Mobile HTTP: READY http://02.jpn.gg:10271/"));
            } else if (httpBindError != null) {
                player.sendMessage(Component.text("Piri Mobile HTTP: FAILED " + httpBindError));
            } else {
                player.sendMessage(Component.text("Piri Mobile HTTP: STARTING"));
            }
            return true;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("pair")) {
            pairings.entrySet().removeIf(e -> e.getValue().owner().equals(player.getUniqueId()));
            String code;
            do code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
            while (pairings.containsKey(code));
            pairings.put(code, new Pairing(player.getUniqueId(), System.currentTimeMillis() + PAIR_TTL_MS));
            player.sendMessage(Component.text("スマホ接続コード: " + code + "  (5分間有効)"));
            player.sendMessage(Component.text("Safariで http://02.jpn.gg:10271/ を開いて入力してください。"));
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
        player.sendMessage(Component.text("/piri mobile pair | /piri mobile status | /piri mobile revoke"));
        return true;
    }


    private void revokeLocal(UUID owner) {
        pairings.entrySet().removeIf(e -> e.getValue().owner().equals(owner));
        tokenHashes.entrySet().removeIf(e -> e.getValue().equals(owner));
        remoteOwners.remove(owner);
        npcs.remove(owner);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player=event.getPlayer();UUID owner=player.getUniqueId();
        if(!remoteOwners.remove(owner))return;
        npcs.remove(owner);
        forceRemoteExitAfterJoin(player,0);
    }

    private void forceRemoteExitAfterJoin(Player player,int attempt) {
        machines.mobileForceExitForOnline(player,failure->{
            if("BUSY".equals(failure)&&attempt<100){
                plugin.getServer().getScheduler().runTaskLater(plugin,()->forceRemoteExitAfterJoin(player,attempt+1),1L);
                return;
            }
            if(failure!=null){
                plugin.getLogger().warning("REMOTE login force-exit failed player="+player.getUniqueId()+" code="+failure);
                if(player.isOnline())player.sendMessage(Component.text("REMOTE遊技の強制清算に失敗しました: "+failure));
            }
        });
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        npcs.removeStale(event.getChunk());
    }

    private void installDedicatedHttpListener() {
        httpGroup = new NioEventLoopGroup(1, runnable -> {
            Thread thread = new Thread(runnable, "piri-mobile-http");
            thread.setDaemon(true);
            return thread;
        });

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(httpGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline().addLast("piri-mobile-http-codec", new HttpServerCodec());
                        channel.pipeline().addLast("piri-mobile-http-aggregate", new HttpObjectAggregator(64 * 1024));
                        channel.pipeline().addLast("piri-mobile-http-handler", new HttpHandler());
                    }
                });

        bootstrap.bind("0.0.0.0", HTTP_PORT).addListener(future -> {
            if (future.isSuccess()) {
                httpServerChannel = ((io.netty.channel.ChannelFuture) future).channel();
                httpBindError = null;
                plugin.getLogger().info("PIRI_MOBILE_HTTP_READY port=" + HTTP_PORT);
            } else {
                httpBindError = future.cause() == null ? "UNKNOWN" :
                        future.cause().getClass().getSimpleName() + ": " + String.valueOf(future.cause().getMessage());
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Piri mobile HTTP failed to bind port " + HTTP_PORT, future.cause());
                if (httpGroup != null) {
                    httpGroup.shutdownGracefully();
                    httpGroup = null;
                }
            }
        });
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
            if (request.method().equals(HttpMethod.GET) && path.startsWith("/assets/")) {
                serveAsset(ctx, path.substring("/assets/".length()));
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
            if (request.method().equals(HttpMethod.GET) && path.equals("/api/data")) {
                Integer machineId = integer(one(query, "id"));
                if (machineId == null) {
                    json(ctx, HttpResponseStatus.BAD_REQUEST, error("INVALID_MACHINE"));
                    return;
                }
                onMain(ctx, done -> machines.mobileData(machineId, done));
                return;
            }
            if (request.method().equals(HttpMethod.GET) && path.equals("/api/state")) {
                onMain(ctx, done -> {
                    JsonObject state = machines.mobileState(owner);
                    if (!state.has("seated") || !state.get("seated").getAsBoolean()) {npcs.remove(owner);remoteOwners.remove(owner);}
                    done.accept(state, null);
                });
                return;
            }
            if (request.method().equals(HttpMethod.GET) && path.equals("/api/events")) {
                onMain(ctx, done -> done.accept(machines.mobileDrainEvents(owner), null));
                return;
            }
            if (request.method().equals(HttpMethod.POST) && path.equals("/api/resume")) {
                onMain(ctx, done -> machines.mobileResume(owner, done));
                return;
            }
            if (request.method().equals(HttpMethod.POST) && path.equals("/api/seat")) {
                Integer machineId = integer(one(query, "id"));
                if (machineId == null) {
                    json(ctx, HttpResponseStatus.BAD_REQUEST, error("INVALID_MACHINE"));
                    return;
                }
                onMain(ctx, done -> {
                    Player online = Bukkit.getPlayer(owner);
                    if (online != null && online.isOnline()) {
                        done.accept(null, "MINECRAFT_ONLINE");
                        return;
                    }
                    remoteOwners.add(owner);
                    machines.mobileSeat(owner, machineId, (state, failure) -> {
                        if (failure != null) remoteOwners.remove(owner);
                        else if(remoteOwners.contains(owner)) {
                            Player nowOnline=Bukkit.getPlayer(owner);
                            if(nowOnline!=null&&nowOnline.isOnline()){
                                remoteOwners.remove(owner);npcs.remove(owner);forceRemoteExitAfterJoin(nowOnline,0);
                            }else{
                                Machine machine = machines.mobileMachine(machineId);
                                if (machine != null) npcs.seat(owner, machine);
                            }
                        }
                        done.accept(state, failure);
                    });
                });
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
            if (request.method().equals(HttpMethod.POST) && path.equals("/api/insert")) {
                onMain(ctx, done -> machines.mobileInsert(owner, done));
                return;
            }
            if (request.method().equals(HttpMethod.POST) && path.equals("/api/cashout")) {
                onMain(ctx, done -> machines.mobileCashout(owner, done));
                return;
            }
            if (request.method().equals(HttpMethod.GET) && path.equals("/api/prizes")) {
                onMain(ctx, done -> plugin.prizes().mobilePrizeState(owner, done));
                return;
            }
            if (request.method().equals(HttpMethod.POST) && path.equals("/api/prize/buy")) {
                var type = jp.pirijuggler.paper.economy.PrizeItem.Type.parse(one(query, "type"));
                Integer count = integer(one(query, "count"));
                if (type == null || count == null || count < 1) {
                    json(ctx, HttpResponseStatus.BAD_REQUEST, error("INVALID_STATE"));
                    return;
                }
                onMain(ctx, done -> plugin.prizes().mobileBuyPrize(owner, type, count, done));
                return;
            }
            if (request.method().equals(HttpMethod.POST) && path.equals("/api/prize/cash")) {
                String rawType = one(query, "type");
                var type = rawType == null || rawType.equalsIgnoreCase("all") ? null
                        : jp.pirijuggler.paper.economy.PrizeItem.Type.parse(rawType);
                Integer count = integer(one(query, "count"));
                if (rawType != null && !rawType.equalsIgnoreCase("all") && type == null) {
                    json(ctx, HttpResponseStatus.BAD_REQUEST, error("INVALID_STATE"));
                    return;
                }
                onMain(ctx, done -> plugin.prizes().mobileCashPrizes(owner, type, type == null ? 0 : (count == null ? 1 : count), done));
                return;
            }
            if (request.method().equals(HttpMethod.POST) && path.equals("/api/leave")) {
                onMain(ctx, done -> machines.mobileLeave(owner, (state, failure) -> {
                    if (failure == null) {npcs.remove(owner);remoteOwners.remove(owner);}
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

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Piri mobile HTTP request failed from " + ctx.channel().remoteAddress(), cause);
            ctx.close();
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

    private void serveAsset(ChannelHandlerContext ctx, String relative) {
        if (relative == null || relative.isBlank() || relative.contains("..") || relative.startsWith("/")) {
            respond(ctx, HttpResponseStatus.NOT_FOUND, "text/plain", "");
            return;
        }
        final boolean sound = relative.startsWith("sounds/");
        String leaf = sound ? relative.substring("sounds/".length()) : relative;
        String resource = sound ? "/mobile-assets/sounds/" + leaf : "/mobile-assets/textures/" + leaf;
        byte[] bytes = readResource(resource);
        if (!sound && bytes == null && leaf.startsWith("juggler_god/")) {
            bytes = readResource("/mobile-assets/textures/" + leaf.substring("juggler_god/".length()));
        }
        if (bytes == null) {
            respond(ctx, HttpResponseStatus.NOT_FOUND, "text/plain", "");
            return;
        }
        String contentType = sound ? "audio/ogg" : leaf.endsWith(".png") ? "image/png" : "application/octet-stream";
        respondBytes(ctx, HttpResponseStatus.OK, contentType, bytes);
    }

    private static byte[] readResource(String path) {
        try (var input = MobileRemoteGateway.class.getResourceAsStream(path)) {
            return input == null ? null : input.readAllBytes();
        } catch (java.io.IOException failure) {
            return null;
        }
    }

    private void json(ChannelHandlerContext ctx, HttpResponseStatus status, JsonObject json) {
        respond(ctx, status, "application/json; charset=utf-8", GSON.toJson(json));
    }

    private void respond(ChannelHandlerContext ctx, HttpResponseStatus status, String contentType, String body) {
        respondBytes(ctx, status, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    private void respondBytes(ChannelHandlerContext ctx, HttpResponseStatus status, String contentType, byte[] bytes) {
        if (!ctx.channel().isActive()) return;
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
        remoteOwners.clear();
        npcs.clear();
        Channel server = httpServerChannel;
        httpServerChannel = null;
        if (server != null) server.close();
        EventLoopGroup group = httpGroup;
        httpGroup = null;
        if (group != null) group.shutdownGracefully();
    }

    private static final class RemoteNpcService {
        private record RemoteNpc(UUID entityId, UUID worldId, int chunkX, int chunkZ) {}
        private final Map<UUID, RemoteNpc> entities = new java.util.HashMap<>();

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
                npc.addScoreboardTag("piri_remote");
                npc.addScoreboardTag("piri_remote_" + owner);
            });
            entities.put(owner, new RemoteNpc(
                    stand.getUniqueId(),
                    world.getUID(),
                    location.getBlockX() >> 4,
                    location.getBlockZ() >> 4));
        }

        void remove(UUID owner) {
            RemoteNpc ref = entities.remove(owner);
            String ownerTag = "piri_remote_" + owner;
            if (ref != null) {
                World world = Bukkit.getWorld(ref.worldId());
                if (world != null) {
                    var chunk = world.getChunkAt(ref.chunkX(), ref.chunkZ());
                    Entity entity = Bukkit.getEntity(ref.entityId());
                    if (entity != null) entity.remove();
                    for (Entity candidate : chunk.getEntities()) {
                        if (candidate.getScoreboardTags().contains(ownerTag)) candidate.remove();
                    }
                }
            }
            for (World world : Bukkit.getWorlds()) {
                for (Entity candidate : world.getEntities()) {
                    if (candidate.getScoreboardTags().contains(ownerTag)) candidate.remove();
                }
            }
        }

        void retain(Set<UUID> activeOwners) {
            for (UUID owner : List.copyOf(entities.keySet())) {
                if (!activeOwners.contains(owner)) remove(owner);
            }
        }

        void clear() {
            for (UUID owner : List.copyOf(entities.keySet())) remove(owner);
            sweepLoaded();
        }

        void sweepLoaded() {
            for (World world : Bukkit.getWorlds()) {
                for (Chunk chunk : world.getLoadedChunks()) removeStale(chunk);
            }
        }

        void removeStale(Chunk chunk) {
            Set<UUID> currentIds = new java.util.HashSet<>();
            for (RemoteNpc ref : entities.values()) currentIds.add(ref.entityId());
            for (Entity candidate : chunk.getEntities()) {
                if (candidate.getScoreboardTags().contains("piri_remote")
                        && !currentIds.contains(candidate.getUniqueId())) candidate.remove();
            }
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
.graphSurface{display:block;overflow:hidden;background:#090b0e!important}\ncanvas{background:#090b0e}
html,body{margin:0;min-height:100%;background:#0b0c10;color:#F6F1E7}
button,input{font:inherit}
button{border:0;color:#fff;background:#343944;font-weight:800;cursor:pointer}
button:disabled{opacity:.35;cursor:default}
.hidden{display:none!important}
#normal{max-width:680px;margin:auto;padding:18px 16px 48px}
h1{font-size:22px;margin:4px 0 16px}
.card{background:#16181e;border:1px solid #292d36;border-radius:18px;padding:16px;margin:12px 0}
.top{display:flex;justify-content:space-between;gap:12px;align-items:center}
.muted{color:#9da3ae;font-size:13px}
input{width:100%;padding:14px;border-radius:12px;border:1px solid #3a3f49;background:#0f1116;color:white;margin:8px 0;font-size:16px}
.normalBtn{border-radius:14px;min-height:48px;padding:10px 15px}
.primary{background:#f0c24a!important;color:#17130a!important}
.danger{background:#5a2525!important}
.machine{display:grid;grid-template-columns:minmax(0,1fr) auto auto;gap:8px;align-items:center;padding:13px 0;border-bottom:1px solid #292d36}
.machine:last-child{border-bottom:0}
.machineName{font-weight:800;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.status{font-size:12px;color:#aab0ba;margin-top:3px}
#dataPanel{position:fixed;inset:0;z-index:40;background:#08090c;overflow:auto;padding:18px}
#dataInner{max-width:760px;margin:auto}
.dataGrid{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}
.metric{background:#090B0E;border:1px solid #292d36;border-radius:12px;padding:12px}
.metric .k{font-size:11px;color:#9da3ae}.metric .v{font-size:22px;font-weight:900;margin-top:4px}
#preGraph{display:block;width:100%;height:180px;background:#090b0e!important;border:1px solid #B68A42;border-radius:10px;margin-top:12px}
.histRow{display:grid;grid-template-columns:1fr 1fr;gap:8px;padding:7px 0;border-bottom:1px solid #252932}
#game{position:fixed;inset:0;background:#000000;z-index:30;overflow:hidden;touch-action:manipulation}
#stageWrap{position:absolute;inset:0;overflow:hidden}
#stage{position:absolute;width:1920px;height:1080px;transform-origin:0 0;background:#000000;color:#F6F1E7;font-family:Arial,sans-serif;user-select:none}
.panel{position:absolute;background:#090B0E;border:4px solid #B68A42;border-radius:18px;z-index:2;box-shadow:0 8px 24px rgba(0,0,0,.28)}
#dataTop{left:20px;top:12px;width:1880px;height:270px}
#dataLeft{left:18px;top:325px;width:275px;height:430px}
#dataRight{left:1645px;top:325px;width:257px;height:430px}

#cabinet{position:absolute;left:290px;top:205px;width:1340px;height:835px;background:#3C0A10;border:4px solid #B68A42;border-radius:18px;overflow:hidden;z-index:1}
#cabinet.godlike{
 background:linear-gradient(to bottom,#E9D07A,#64410D);
 border-color:#E4C174
}
#cabinet.godlike:before{
 content:"";position:absolute;left:14px;right:14px;top:20px;height:18px;pointer-events:none;
 background:linear-gradient(to bottom,rgba(255,241,184,.40) 0 10px,transparent 10px 14px,rgba(255,247,214,.14) 14px 18px)
}
#cabinet.godlike:after{
 content:"";position:absolute;left:14px;right:14px;bottom:20px;height:10px;pointer-events:none;background:rgba(74,44,8,.23)
}

#reelBacking{position:absolute;left:670px;top:300px;width:900px;height:390px;background:#8A8175;z-index:3}
.reelWindow{position:absolute;top:300px;width:270px;height:390px;overflow:hidden;background:#F4F1E8;z-index:4}
#reel0{left:670px}#reel1{left:985px}#reel2{left:1300px}
.reelWindow.godlike{background:#fff}
#stage.skillstop #cabinet{background:#111015!important;border-color:#B68A42!important;box-shadow:inset 0 0 0 4px #E4C174!important}
#stage.skillstop #reelBacking{background:#8A8175!important}
#stage.skillstop .reelWindow{background:#F4F1E8!important}
#stage.skillstop #statusPanel{background:#090B0E!important;border-color:#B68A42!important}
#stage.skillstop #dataTop,#stage.skillstop #dataLeft,#stage.skillstop #dataRight{background:#090B0E!important;border-color:#B68A42!important}
.sym{position:absolute;pointer-events:none}
.godReveal{position:absolute;display:none;pointer-events:none;object-fit:contain;z-index:7}
#replayText{position:absolute;left:1050px;top:816px;transform:translateX(-50%);font-size:18px;line-height:18px;font-weight:900;color:#F6F1E7;z-index:7;white-space:nowrap}
#countText{position:absolute;left:1090px;top:816px;transform:translateX(-50%);font-size:18px;line-height:18px;font-weight:900;color:#F6F1E7;z-index:7;white-space:nowrap}
#stockLamp{position:absolute;left:1390px;top:835px;width:150px;height:58px;border-radius:14px;background:#34363b;display:none;align-items:center;justify-content:center;z-index:7;padding:6px}
#stockInner{width:138px;height:46px;border-radius:10px;background:#090B0E;display:flex;align-items:center;justify-content:center}
#stockText{font-size:16px;line-height:16px;font-weight:900;color:#190406}
#stockLamp.on{background:#58e36a}
#stockLamp.on #stockText{color:#58e36a;text-shadow:0 0 8px #58e36a}
#cabinet.godlike{overflow:hidden}

#lamp{position:absolute;left:350px;top:390px;width:300px;height:170px;object-fit:contain;z-index:5}
#skillChallenge{position:absolute;left:410px;top:580px;width:180px;height:140px;display:flex;align-items:center;justify-content:center;z-index:5}
#skillChallengeImg{display:none;max-width:100%;max-height:100%;object-fit:contain}
#skillRemaining{display:none}

#statusPanel{position:absolute;left:670px;top:710px;width:900px;height:95px;background:#090B0E;border:4px solid #B68A42;border-radius:18px;z-index:5}
#statusPanel>div{position:absolute;top:11px;width:170px}
#statusPanel>div:nth-child(1){left:16px}#statusPanel>div:nth-child(2){left:214px}#statusPanel>div:nth-child(3){left:412px}#statusPanel>div:nth-child(4){left:610px}
.statLabel{font-size:18px;line-height:18px;color:#F6F1E7}.statValue{font-size:27px;line-height:27px;font-weight:900;margin-top:15px}
#mobileMoney{position:absolute;left:1664px;top:697px;width:220px;height:45px;text-align:center;color:#F6F1E7;z-index:3;border-top:1px solid #292d36;padding-top:6px}
#mobileMoney .statLabel{font-size:13px;line-height:13px}#mobileMoney .statValue{font-size:20px;line-height:20px;margin-top:4px}

.machineControl{position:absolute;z-index:8;border-radius:14px;background:#666A72;padding:4px}
.machineControl>span{display:flex;width:100%;height:100%;align-items:center;justify-content:center;border-radius:10px;background:#C92734;font-size:22px;font-weight:900;text-shadow:2px 2px #190406}
.machineControl:active>span{background:#8A1720;transform:translateY(2px)}
#betBtn{left:440px;top:860px;width:150px;height:100px}
#leverBtn{left:300px;top:780px;width:130px;height:260px;background:transparent;padding:0}
#leverStem{position:absolute;left:54px;top:35px;width:22px;height:190px;background:#666A72}
#leverKnob{position:absolute;left:30px;top:27px;width:70px;height:70px;border-radius:50%;background:#C92734;border:5px solid #666A72;transition:transform .09s}
#leverBtn:active #leverKnob{transform:translateY(18px)}
#leverLabel{position:absolute;left:0;right:0;bottom:25px;text-align:center;font-size:26px;font-weight:900}
.stopBtn{width:180px;height:110px;background:transparent;padding:0}
.stopBtn>span{width:88px;height:88px;border-radius:50%;border:6px solid #B9BCC2;background:#C92734;font-size:0;margin:auto}
.stopBtn>span:after{content:"";display:block}
.stopBtn:active>span{background:#8A1720}
.stopBtn:disabled>span{background:#666A72;border-color:#666A72}
.stopText{position:absolute;left:0;right:0;bottom:-12px;text-align:center;font-size:24px;font-weight:900}
#leftBtn{left:720px;top:865px}#centerBtn{left:990px;top:865px}#rightBtn{left:1260px;top:865px}

.sideBtn{left:1664px;width:220px;height:55px}
#loanBtn{top:780px}#insertBtn{top:845px}#cashBtn{top:910px;height:65px}#exchangeBtn{display:none}
#gameMessage{position:absolute;left:840px;top:990px;width:400px;text-align:center;font-size:24px;font-weight:800;color:#F6F1E7;z-index:10}
#leaveBtn{position:fixed;right:max(12px,env(safe-area-inset-right));top:max(12px,env(safe-area-inset-top));z-index:50;background:#5a2525;border-radius:12px;padding:10px 14px;font-size:14px;opacity:.9}

.dataTitle{position:absolute;font-size:20px;font-weight:900;z-index:3}
#machineLabel{left:38px;top:27px}
#graphLabel{left:55px;top:55px}
#gameGraph{position:absolute;left:55px;top:88px;width:825px;height:146px;background:#090b0e!important;z-index:3;border-radius:6px;overflow:hidden}

.topMetric{position:absolute;width:205px;text-align:center;z-index:3}.topMetric .t{font-size:23px;font-weight:900}.topMetric .n{font-size:36px;font-weight:900;margin-top:6px}
#currentBox{left:958px;top:43px}#totalBox{left:1268px;top:43px}#maxBox{left:1578px;top:43px}
#bigBox{left:1108px;top:145px;color:#ff4242}#regBox{left:1438px;top:145px;color:#3a78ff}

#graphStart{position:absolute;left:55px;top:241px;font-size:17px;font-weight:900;z-index:3}
#graphEnd{position:absolute;left:820px;top:241px;width:120px;text-align:center;font-size:17px;font-weight:900;z-index:3}
#diffTitle{left:42px;top:345px}.sideLarge{position:absolute;left:62px;width:188px;text-align:center;font-size:42px;font-weight:900;z-index:3}
#diffValue{top:382px}
#historyTitle{left:42px;top:465px}.historyList{position:absolute;left:44px;top:521px;width:210px;height:220px;font-size:17px;overflow-y:auto;overscroll-behavior:contain;scrollbar-width:thin;z-index:3}
.historyItem{display:flex;justify-content:space-between;height:22px}.big{color:#ff4242}.reg{color:#3a78ff}.god{color:#58e36a}
#oddsTitle{left:1672px;top:345px}.odds{position:absolute;left:1672px;width:210px;font-size:19px;z-index:3}.odds b{float:right;font-size:24px}
#bigOdds{top:385px;color:#ff4242}#regOdds{top:455px;color:#3a78ff}#allOdds{top:525px}
#chain{position:absolute;left:1664px;top:592px;width:220px;height:88px;border:4px solid #58e36a;border-radius:12px;text-align:center;padding-top:9px;font-size:20px;font-weight:900;color:#58e36a;z-index:3}
@media(max-width:700px){
 #normal{padding:12px 10px 30px}.machine{grid-template-columns:minmax(0,1fr) auto auto}.normalBtn{padding:8px 10px;font-size:14px}.dataGrid{grid-template-columns:repeat(2,1fr)}
}
</style>
</head>
<body>
<div id="normal">
<h1>Piri Remote</h1>
<section id="pair" class="card">
<div>スマホ接続</div>
<div class="muted">Minecraftで <b>/piri mobile pair</b> を実行し、6桁コードを入力</div>
<input id="code" inputmode="numeric" maxlength="6" placeholder="000000">
<button id="pairBtn" class="normalBtn primary" style="width:100%">接続</button>
<div id="pairMsg" class="muted"></div>
</section>
<section id="lobby" class="hidden">
<div class="card top"><div><div class="muted">PLAYER</div><div id="player">-</div></div><button id="logout" class="normalBtn">接続解除</button></div>
<div class="card" id="prizeShop">
<div class="top"><b>景品交換所</b><button id="prizeRefresh" class="normalBtn">更新</button></div>
<div class="muted">台から離席中のみ利用できます</div>
<div style="margin-top:10px">所持メダル <b id="walletMedals">0</b>枚 / 所持金 <b id="lobbyMoney">---</b></div>
<div class="dataGrid" style="margin-top:10px">
<div class="metric"><div class="k">小景品</div><div id="smallPrizes" class="v">0</div><button id="buySmall" class="normalBtn">交換</button></div>
<div class="metric"><div class="k">中景品</div><div id="mediumPrizes" class="v">0</div><button id="buyMedium" class="normalBtn">交換</button></div>
<div class="metric"><div class="k">大景品</div><div id="largePrizes" class="v">0</div><button id="buyLarge" class="normalBtn">交換</button></div>
</div>
<div style="margin-top:10px"><button id="cashPrizes" class="normalBtn primary" style="width:100%">所持景品を換金</button></div>
<div id="prizeMsg" class="muted" style="margin-top:8px"></div>
</div>
<div class="card"><div class="top"><b>台一覧</b><button id="refresh" class="normalBtn">更新</button></div><div id="machines"></div></div>
</section>
</div>

<section id="dataPanel" class="hidden">
<div id="dataInner">
<div class="top"><div><b id="dataTitle">台データ</b><div id="dataType" class="muted"></div></div><button id="dataClose" class="normalBtn">戻る</button></div>
<div class="dataGrid" style="margin-top:14px">
<div class="metric"><div class="k">CURRENT G</div><div id="dCurrent" class="v">0</div></div>
<div class="metric"><div class="k">TOTAL G</div><div id="dTotal" class="v">0</div></div>
<div class="metric"><div class="k">DIFF</div><div id="dDiff" class="v">0</div></div>
<div class="metric"><div class="k">MAX DIFF</div><div id="dMax" class="v">0</div></div>
<div class="metric"><div class="k">BIG</div><div id="dBig" class="v">0</div></div>
<div class="metric"><div class="k">REG</div><div id="dReg" class="v">0</div></div>
</div>
<div id="preGraph" class="graphSurface"></div>
<div class="card"><b>BONUS HISTORY</b><div id="preHistory"></div></div>
</div>
</section>

<section id="game" class="hidden">
<button id="leaveBtn">離席</button>
<div id="stageWrap"><div id="stage">
<div id="dataTop" class="panel"></div><div id="dataLeft" class="panel"></div><div id="dataRight" class="panel"></div>
<div id="cabinet"></div><div id="reelBacking"></div>
<div id="reel0" class="reelWindow"></div><div id="reel1" class="reelWindow"></div><div id="reel2" class="reelWindow"></div>
<img id="lamp" src="/assets/lamp/piri_chance_off.png" alt="">
<div id="skillChallenge"><img id="skillChallengeImg" alt=""></div><div id="skillRemaining"></div>
<div id="replayText"></div><div id="countText"></div><div id="stockLamp"><div id="stockInner"><span id="stockText">STOCK</span></div></div>
<div id="statusPanel">
<div><div class="statLabel">CREDIT</div><div id="credit" class="statValue">0</div></div>
<div><div class="statLabel">BET</div><div id="bet" class="statValue">0</div></div>
<div><div class="statLabel">PAY</div><div id="pay" class="statValue">0</div></div>
<div><div class="statLabel">MEDALS</div><div id="medals" class="statValue">0</div></div>
</div>
<div id="mobileMoney"><div class="statLabel">MONEY</div><div id="money" class="statValue">---</div></div>
<button id="betBtn" class="machineControl"><span>BET</span></button>
<button id="leverBtn" class="machineControl"><i id="leverStem"></i><i id="leverKnob"></i><b id="leverLabel">LEVER</b></button>
<button id="leftBtn" class="machineControl stopBtn" data-action="STOP_LEFT" data-reel="0"><span></span><b class="stopText">LEFT</b></button>
<button id="centerBtn" class="machineControl stopBtn" data-action="STOP_CENTER" data-reel="1"><span></span><b class="stopText">CENTER</b></button>
<button id="rightBtn" class="machineControl stopBtn" data-action="STOP_RIGHT" data-reel="2"><span></span><b class="stopText">RIGHT</b></button>
<button id="loanBtn" class="machineControl sideBtn"><span>LOAN</span></button>
<button id="insertBtn" class="machineControl sideBtn"><span>INSERT</span></button>
<button id="cashBtn" class="machineControl sideBtn"><span>CASH OUT</span></button>
<div id="machineLabel" class="dataTitle">MACHINE -</div><div id="graphLabel" class="dataTitle">DIFF GRAPH</div>
<div id="gameGraph" class="graphSurface"></div><div id="graphStart">1G</div><div id="graphEnd">-</div>
<div id="currentBox" class="topMetric"><div class="t">CURRENT G</div><div id="gCurrent" class="n">0</div></div>
<div id="totalBox" class="topMetric"><div class="t">TOTAL G</div><div id="gTotal" class="n">0</div></div>
<div id="maxBox" class="topMetric"><div class="t">MAX DIFF</div><div id="gMax" class="n">0</div></div>
<div id="bigBox" class="topMetric"><div class="t">BIG</div><div id="gBig" class="n">0</div></div>
<div id="regBox" class="topMetric"><div class="t">REG</div><div id="gReg" class="n">0</div></div>
<div id="diffTitle" class="dataTitle">DIFF</div><div id="diffValue" class="sideLarge">0</div>
<div id="historyTitle" class="dataTitle">BONUS HISTORY</div><div id="gameHistory" class="historyList"></div>
<div id="oddsTitle" class="dataTitle">ODDS</div>
<div id="bigOdds" class="odds">BIG ODDS <b>---</b></div>
<div id="regOdds" class="odds">REG ODDS <b>---</b></div>
<div id="allOdds" class="odds">COMBINED <b>---</b></div>
<div id="chain" class="hidden">PIRI CHAIN<br><span id="chainCount">CHAIN x1</span></div>
<div id="gameMessage"></div>
</div></div>
</section>

<script>
(function(){
const $=function(id){return document.getElementById(id)};
let token=localStorage.getItem("piriToken")||"";
let player=localStorage.getItem("piriPlayer")||"";
let stateTimer=0,dataTimer=0,eventTimer=0,currentState=null,currentData=null,currentType="",busy=false;
let motion=null,pendingState=null,nextGameAt=0,queuedLeverTimer=0,godPresentationUntil=0,godPresentation=null,godBigAudioPending=false,godBigAudioActive=false,pendingBigBgmAt=0,pendingBigBgmName="",pendingGodHitSound="",noticeOn=false,noticeBlink=false,noticeAt=0,resumeInFlight=false,lastResumeAt=0,lastEventAt=0,lastSnapshotAt=0,godFreezeVisual=false,godFreezeAt=-1,godRevealed=[false,false,false],godRevealAt=[-1,-1,-1],godImpactAt=-1;let skillNoticePlayed=[];

const fixed=[
["grape","replay","grape","seven","piero","grape","replay","grape","cherry","bar","grape","replay","grape","bell","seven","replay","grape","replay","grape","bar","cherry"],
["cherry","piero","replay","seven","grape","cherry","replay","bell","grape","cherry","replay","bar","grape","cherry","replay","bell","grape","cherry","replay","bar","grape"],
["bell","replay","grape","seven","bar","bell","replay","grape","piero","bell","replay","grape","piero","bell","replay","grape","piero","bell","replay","grape","piero"]
];
const skillNumbered=[
["replay","grape","bar","cherry","grape","replay","grape","replay","bell","seven","piero","replay","grape","cherry","bar","grape","replay","grape","piero","seven","grape"],
["cherry","grape","piero","replay","cherry","grape","bar","replay","cherry","grape","replay","cherry","grape","piero","bar","replay","cherry","grape","bell","seven","replay"],
["bell","replay","piero","grape","bell","replay","piero","grape","bell","replay","piero","grape","bell","replay","piero","grape","bell","replay","bar","seven","grape"]
];
function mod(n,m){return((n%m)+m)%m}
function symbolAt(reel,index){
 if(currentType==="SKILL_STOP")return skillNumbered[reel][20-mod(index,21)];
 return fixed[reel][mod(index,21)];
}
function asset(path){
 return "/assets/"+((currentType==="JUGGLER_GOD"||currentType==="JUGGLER_GOD_EXTREME")?"juggler_god/":"")+path;
}
let audioLoop=null,audioLoopName="",audioUnlocked=false,audioEpoch=0;const audioOneShots=new Set(),audioTimers=new Set();
function soundUrl(name){return "/assets/sounds/"+name+".ogg"}
function machineSound(base){
 return (currentType==="JUGGLER_GOD"||currentType==="JUGGLER_GOD_EXTREME")?"juggler_god_"+base:base;
}
function unlockAudio(){audioUnlocked=true}
function trackOneShot(a){
 audioOneShots.add(a);
 const clear=function(){audioOneShots.delete(a)};
 a.addEventListener("ended",clear,{once:true});a.addEventListener("abort",clear,{once:true});
 return a;
}
function playNamed(name,fallback){
 if(!audioUnlocked)return;
 const epoch=audioEpoch,a=trackOneShot(new Audio(soundUrl(name)));a.preload="auto";a.volume=1;
 if(fallback&&fallback!==name)a.addEventListener("error",function(){
  audioOneShots.delete(a);if(epoch!==audioEpoch)return;
  const b=trackOneShot(new Audio(soundUrl(fallback)));b.preload="auto";b.volume=1;b.play().catch(function(){audioOneShots.delete(b)});
 },{once:true});
 a.play().catch(function(){audioOneShots.delete(a)});
}
function playLater(name,fallback,delay){
 if(!audioUnlocked)return;
 const timer=setTimeout(function(){audioTimers.delete(timer);playNamed(name,fallback)},Math.max(0,delay||0));audioTimers.add(timer);
}
function playSound(base){playNamed(machineSound(base),base)}
function stopLoop(){
 if(audioLoop){audioLoop.pause();audioLoop.currentTime=0}audioLoop=null;audioLoopName="";
}
function startLoopNamed(name,fallback){
 if(!audioUnlocked||audioLoopName===name)return;stopLoop();
 const a=new Audio(soundUrl(name));a.loop=true;a.volume=.45;audioLoop=a;audioLoopName=name;
 if(fallback&&fallback!==name)a.addEventListener("error",function(){if(audioLoop!==a)return;const b=new Audio(soundUrl(fallback));b.loop=true;b.volume=.45;audioLoop=b;audioLoopName=fallback;b.play().catch(function(){})},{once:true});
 a.play().catch(function(){});
}
function startLoop(base){startLoopNamed(machineSound(base),base)}
function stopAllAudio(){
 audioEpoch++;
 for(const timer of audioTimers)clearTimeout(timer);audioTimers.clear();
 for(const a of audioOneShots){try{a.pause();a.currentTime=0}catch(e){}}audioOneShots.clear();
 stopLoop();
}
function resetSlotRuntime(){
 if(queuedLeverTimer){clearTimeout(queuedLeverTimer);queuedLeverTimer=0}
 stopAllAudio();
 motion=null;pendingState=null;nextGameAt=0;godPresentationUntil=0;godPresentation=null;
 godBigAudioPending=false;godBigAudioActive=false;pendingBigBgmAt=0;pendingBigBgmName="";pendingGodHitSound="";
 noticeOn=false;noticeBlink=false;noticeAt=0;resumeInFlight=false;lastResumeAt=0;lastEventAt=0;lastSnapshotAt=0;
 godFreezeVisual=false;godFreezeAt=-1;godRevealed=[false,false,false];godRevealAt=[-1,-1,-1];godImpactAt=-1;
 skillNoticePlayed=[];
}

function show(name){
 $("pair").classList.toggle("hidden",name!=="pair");
 $("lobby").classList.toggle("hidden",name!=="lobby");
 $("dataPanel").classList.toggle("hidden",name!=="data");
 $("game").classList.toggle("hidden",name!=="game");
 $("normal").classList.toggle("hidden",name==="game"||name==="data");
 if(name==="game")resizeStage();
}
async function api(path,method){
 const r=await fetch(path,{method:method||"GET",cache:"no-store",headers:token?{"X-Piri-Token":token}:{}});
 let j={};try{j=await r.json()}catch(e){}
 if(r.status===401&&path.indexOf("/api/pair")!==0){token="";localStorage.removeItem("piriToken");show("pair");throw new Error("接続が無効です")}
 if(!r.ok||j.ok===false)throw new Error(j.error||("HTTP "+r.status));
 return j;
}
function errorText(e){
 const m={BUSY:"処理中です",INVALID_STATE:"今は操作できません",INVALID_MACHINE:"この台は利用できません",NOT_ENOUGH_CREDIT:"クレジットが足りません",NOT_ENOUGH_VAULT:"所持金が足りません",MACHINE_OCCUPIED:"ほかのプレイヤーが遊技中です",MACHINE_DISABLED:"この台は利用できません",STOP_TOO_EARLY:"まだ停止できません",ALREADY_STOPPED:"停止済みです",SESSION_MISMATCH:"台との接続状態が変わりました",VAULT_ERROR:"所持金処理に失敗しました",ECONOMY_UNAVAILABLE:"貸出を利用できません",AUTH_LOADING:"サーバー起動中です",NOT_ENOUGH_MEDALS:"投入できるメダルがありません",NOT_ENOUGH_PRIZES:"景品が足りません",MUST_LEAVE_MACHINE:"景品交換・換金は台から離席してから利用してください",MINECRAFT_ONLINE:"Minecraftにログイン中のため、リモート着席できません。ゲームからログアウトしてからお試しください"};
 return m[e.message]||e.message;
}
async function pairNow(){
 $("pairMsg").textContent="接続中…";
 try{
  const j=await api("/api/pair?code="+encodeURIComponent($("code").value.trim()),"POST");
  token=j.token;player=j.player||"";localStorage.setItem("piriToken",token);localStorage.setItem("piriPlayer",player);
  $("player").textContent=player;show("lobby");await loadMachines();
 }catch(e){$("pairMsg").textContent=errorText(e)}
}
function machineLabel(type){
 return type==="JUGGLER_GOD_EXTREME"?"JUGGLER GOD EXTREME":type==="JUGGLER_GOD"?"JUGGLER GOD":type==="SKILL_STOP"?"SKILL STOP":type;
}
async function loadMachines(){
 loadPrizes();
 try{
  const j=await api("/api/machines");$("player").textContent=player||j.player||"-";const box=$("machines");box.textContent="";
  (j.machines||[]).forEach(function(m){
   const row=document.createElement("div");row.className="machine";
   const info=document.createElement("div");info.className="info";
   info.innerHTML='<div class="machineName">台'+m.id+' '+machineLabel(m.type)+'</div><div class="status">'+(m.busy?(m.owned?"自分が遊技中":"遊技中"):"空き")+'</div>';
   const data=document.createElement("button");data.className="normalBtn";data.textContent="データ";data.onclick=function(){openData(m.id,m.type)};
   const play=document.createElement("button");play.className="normalBtn";play.textContent=m.owned?"再開":"遊ぶ";play.disabled=!m.supported||!m.enabled||(m.busy&&!m.owned);play.onclick=function(){seat(m.id)};
   row.append(info,data,play);box.append(row);
  });
 }catch(e){$("machines").textContent=errorText(e)}
}
async function openData(id,type){
 try{
  const d=await api("/api/data?id="+id);$("dataTitle").textContent="台"+id+" データ";$("dataType").textContent=machineLabel(type);renderPreData(d);show("data");
 }catch(e){alert(errorText(e))}
}
function signed(v){v=Number(v||0);return v>0?"+"+v:String(v)}
function odds(g,h){g=Number(g||0);h=Number(h||0);return g>0&&h>0?"1/"+Math.max(1,Math.round(g/h)):"---"}
function renderPreData(d){
 $("dCurrent").textContent=d.currentGames||0;$("dTotal").textContent=d.totalGames||0;$("dDiff").textContent=signed(d.todayDifference);$("dMax").textContent=signed(d.todayMaxDifference);$("dBig").textContent=d.bigCount||0;$("dReg").textContent=d.regCount||0;
 drawGraph($("preGraph"),d.graph||[],d.totalGames||0);
 const h=$("preHistory");h.textContent="";(d.history||[]).slice(0,20).forEach(function(x){const r=document.createElement("div");r.className="histRow";r.innerHTML="<b>"+x.type+"</b><span>"+x.games+"G</span>";h.append(r)});if(!(d.history||[]).length)h.textContent="-- no bonus yet --";
}
async function seat(id){
 try{
  const j=await api("/api/seat?id="+id,"POST");startGame(j);
 }catch(e){alert(errorText(e))}
}
function resizeStage(){
 const baseW=1920,baseH=1080,scale=Math.min(innerWidth/baseW,innerHeight/baseH);
 const x=(innerWidth-baseW*scale)/2,y=(innerHeight-baseH*scale)/2,stage=$("stage");
 stage.style.width=baseW+"px";stage.style.height=baseH+"px";stage.style.left="0";stage.style.top="0";
 stage.style.transform="translate("+x+"px,"+y+"px) scale("+scale+")";
}
function delta(profile,e){
 e=Math.max(0,e);
 if(profile==="NORMAL"){if(e<=.150)return 0;if(e<.500)return-.5*(28/.350)*(e-.150)*(e-.150);return-4.9-28*(e-.500)}
 if(profile==="REVERSE_500MS"){if(e<.500)return 12*e;if(e<.800)return 6-.5*(28/.300)*(e-.500)*(e-.500);return 1.8-28*(e-.800)}
 return-28*e;
}
function endpoint(from,target){let e=target;while(e>from)e-=21;return e}
function currentPhase(reel,now){
 if(!currentState)return 0;
 if((!motion||!motion.spinning)&&godPresentation){const p=presentationPhase(reel,now);if(p!==null)return p}
 if(motion){
  const st=motion.stops[reel];
  if(st){
   const p=st.duration<=0?1:Math.min(1,Math.max(0,(now-st.at)/st.duration));
   return p>=1?mod(st.target,21):mod(st.from+(st.end-st.from)*p,21);
  }
  if(motion.spinning)return mod(motion.starts[reel]+delta(motion.animation,(now-motion.at)/1000),21);
 }
 const names=["left","center","right"],stops=currentState.displayStops||{};
 return Number(stops[names[reel]]||0);
}
function symbolSize(sym){
 const god=currentType==="JUGGLER_GOD"||currentType==="JUGGLER_GOD_EXTREME";
 if(god){if(sym==="bar")return[230,150];if(sym==="seven"||sym==="grape"||sym==="replay")return[230,150];return[130,130]}
 if(sym==="bar")return[230,150];if(sym==="seven")return[230,130];return[130,130];
}
function ensureReels(){
 for(let r=0;r<3;r++){
  const box=$("reel"+r);if(box.children.length===8)continue;box.textContent="";
  for(let i=0;i<5;i++){const im=document.createElement("img");im.className="sym";box.append(im)}
  for(let i=0;i<3;i++){const reveal=document.createElement("img");reveal.className="godReveal";box.append(reveal)}
 }
}
function presentationPhase(reel,now){
 if(!godPresentation)return null;
 const elapsed=now-godPresentation.at,start=godPresentation.starts[reel];
 if(elapsed<=400)return mod(start,21);if(elapsed>=12700)return 3;
 let target=3;while(target<=start)target+=21;target+=42;
 const t=Math.max(0,Math.min(1,(elapsed-400)/(12700-400))),eased=Math.sin(t*Math.PI/2);
 return mod(start+(target-start)*eased,21);
}
function drawReels(now){
 ensureReels();
 const god=currentType==="JUGGLER_GOD"||currentType==="JUGGLER_GOD_EXTREME";
 for(let r=0;r<3;r++){
  const phase=currentPhase(r,now),middle=Math.floor(phase),frac=phase-middle,box=$("reel"+r),imgs=box.children;
  const scale=Math.max(.01,Math.min(box.clientWidth/270,box.clientHeight/390)),sy=scale;
  const freeze=god&&godFreezeVisual,freezeMs=freeze?Math.max(0,now-godFreezeAt):-1;
  box.style.background=freeze?"#141414":"";
  for(let row=-2;row<=2;row++){
   const im=imgs[row+2],sym=symbolAt(r,middle+row),sz=symbolSize(sym),w=sz[0]*scale,h=sz[1]*scale,src=asset("symbols/"+sym+".png");
   if(im.getAttribute("src")!==src)im.setAttribute("src",src);
   im.style.width=w+"px";im.style.height=h+"px";im.style.left=((box.clientWidth-w)/2)+"px";im.style.top=((130+(row-frac)*130)*sy-(h-130*sy)/2)+"px";
   im.style.objectFit=god?"contain":"fill";im.style.transformOrigin="50% 50%";
   if(freeze&&freezeMs<120){
    const p=Math.max(0,Math.min(1,(freezeMs-35)/85)),s=Math.max(.035,1-p*p);
    im.style.opacity=".34";im.style.transform="scaleY("+s+")";
   }else if(freeze&&freezeMs<165){
    im.style.opacity="0";im.style.transform="none";
   }else if(freeze){
    im.style.opacity=".10";im.style.transform="none";
   }else{im.style.opacity="1";im.style.transform="none"}
  }
  if(freeze&&freezeMs>=120&&freezeMs<165){
   const p=(freezeMs-120)/45,half=135*(1-p),th=Math.max(1,5*(1-p));
   box.style.background="linear-gradient(#d8d8d8,#d8d8d8) center / "+(half*2*scale)+"px "+(th*2*scale)+"px no-repeat,#141414";
  }
  const glow1=imgs[5],glow2=imgs[6],reveal=imgs[7],revealed=freeze&&godRevealed[r]&&godRevealAt[r]>=0&&now>=godRevealAt[r];
  if(god&&revealed){
   const ox=(box.clientWidth-270*scale)/2,oy=(box.clientHeight-390*scale)/2,src=asset("symbols/bar.png"),age=Math.max(0,now-godRevealAt[r]),glow=age<120?Math.max(0,.24*(1-age/120)):0;
   [[glow1,14,114,242,162,glow],[glow2,18,118,234,154,glow],[reveal,20,120,230,150,1]].forEach(function(v){
    const im=v[0];im.src=src;im.style.display=v[5]>0?"block":"none";im.style.opacity=String(v[5]);im.style.objectFit="contain";
    im.style.left=(ox+v[1]*scale)+"px";im.style.top=(oy+v[2]*scale)+"px";im.style.width=(v[3]*scale)+"px";im.style.height=(v[4]*scale)+"px";
   });
  }else{glow1.style.display="none";glow2.style.display="none";reveal.style.display="none"}
 }
}
function nextPendingReel(){
 if(!motion||!motion.spinning)return-1;
 const names=["left","center","right"];
 for(let r=0;r<3;r++)if(!motion.stops[r]&&motion.hints&&motion.hints[names[r]])return r;
 return-1;
}
function canStop(reel){
 if(!motion||!motion.spinning||reel<0||Date.now()<godPresentationUntil)return false;
 if((motion.presses||[]).some(Boolean))return false;
 const lock=Math.max(Number(motion.stopEnableAfterMs||0),motion.godFreeze?1200:0);
 if(performance.now()-motion.at<lock||motion.stops[reel])return false;
 const names=["left","center","right"];
 return !!(motion.hints&&motion.hints[names[reel]]);
}
function localStop(reel,pressed){
 if(!canStop(reel))return null;
 const names=["left","center","right"],list=motion.hints[names[reel]],hint=list&&list[pressed];
 if(!hint)return null;
 const now=performance.now(),from=currentPhase(reel,now),target=Number(hint.stopIndex),end=endpoint(from,target);
 const exact=(from-end)/28*1000,duration=Math.max(Number(hint.durationMs||0),Math.ceil(exact-1e-9));
 motion.stops[reel]={from:from,end:end,target:target,at:now,duration:duration};
 motion.presses[reel]={pressedIndex:pressed,stopIndex:target,at:now};
 if(motion.godFreeze){
  const n=motion.stops.filter(Boolean).length;
  playNamed("juggler_god_god_stop_"+Math.max(1,Math.min(3,n)),machineSound("stop"));
 }else playSound("stop");
 return pressed;
}
function handleEvents(events){
 (events||[]).forEach(function(ev){
  const p=ev.payload||{},type=ev.type||"",now=performance.now();
  lastEventAt=now;
  if(type==="PUBLIC_STATE"){
   applyState(Object.assign({},currentState||{},p,{seated:true,machineType:currentType}));
   return;
  }
  if(type==="SPIN_START"){
   const freeze=!!p.godFreeze,resumed=p.animation==="RESUME_NORMAL";
   if(queuedLeverTimer){clearTimeout(queuedLeverTimer);queuedLeverTimer=0}
   if(freeze){
    stopAllAudio();godBigAudioPending=true;pendingBigBgmAt=0;pendingBigBgmName="";pendingGodHitSound="";
   }
   godFreezeVisual=freeze;godFreezeAt=freeze?(resumed?now-165:now):-1;godRevealed=[false,false,false];godRevealAt=[-1,-1,-1];godImpactAt=-1;
   motion={
    spinId:String(p.spinId||""),animation:String(p.animation||"NORMAL"),godFreeze:freeze,at:now,
    starts:[Number((p.startPhase||{}).left||0),Number((p.startPhase||{}).center||0),Number((p.startPhase||{}).right||0)],
    stopEnableAfterMs:Number(p.stopEnableAfterMs||0),hints:Object.assign({},p.stopHints||{}),
    stops:[null,null,null],presses:[null,null,null],spinning:true
   };
   if(resumed&&currentState){
    const mask=Number(currentState.stoppedMask||0),ds=currentState.displayStops||{},vals=[Number(ds.left||0),Number(ds.center||0),Number(ds.right||0)];
    for(let r=0;r<3;r++)if((mask&(1<<r))!==0){
     motion.stops[r]={from:vals[r],end:vals[r],target:vals[r],at:now,duration:0};
     if(freeze){godRevealed[r]=true;godRevealAt[r]=now-250}
    }
   }
   if(!resumed)playNamed(freeze?"juggler_god_god_freeze":machineSound("lever"),freeze?"god_freeze":"lever");
   if(godBigAudioActive&&currentState&&String(currentState.gameState||"")==="BIG_SPINNING")
    startLoopNamed("juggler_god_god_big_bgm",machineSound("big_bgm"));
   return;
  }
  if(type==="REEL_STOP"){
   if(!motion||!motion.spinning||!p.spinId||String(p.spinId)!==String(motion.spinId))return;
   const map={LEFT:0,CENTER:1,RIGHT:2},r=map[p.reel];if(r===undefined)return;
   const target=Number(p.stopIndex),pressed=p.pressedIndex==null?-1:Number(p.pressedIndex),press=motion.presses[r];
   if(press&&press.pressedIndex===pressed&&press.stopIndex===target){
    motion.presses[r]=null;
    if(motion.stops[r])motion.stops[r].target=target;
   }else{
    motion.presses[r]=null;
    const from=currentPhase(r,now),end=endpoint(from,target),duration=Math.max(Number(p.durationMs||0),Math.ceil((from-end)/28*1000-1e-9));
    motion.stops[r]={from:from,end:end,target:target,at:now,duration:duration};
   }
   if(p.nextStopHints)motion.hints=Object.assign({},p.nextStopHints);
   if(motion.godFreeze){
    godRevealed[r]=true;
    const st=motion.stops[r];godRevealAt[r]=st?st.at+st.duration:now;
   }
   if(motion.stops.filter(Boolean).length===3){
    nextGameAt=Math.max(nextGameAt,motion.at+2000);
    godImpactAt=Math.max.apply(null,[now].concat(godRevealAt.filter(function(v){return v>=0})));
   }
   return;
  }
  if(type==="NOTICE"){
   if(motion&&p.spinId&&String(p.spinId)!==String(motion.spinId))return;
   noticeOn=p.lamp==="ON";noticeBlink=p.pattern==="FAST_BLINK_1S";noticeAt=now;
   if(currentState)currentState=Object.assign({},currentState,{lampOn:noticeOn});
   return;
  }
  if(type==="ACTION_REJECTED"||type==="ERROR"){
   if(motion){
    for(let r=0;r<3;r++)if(motion.presses[r]){motion.presses[r]=null;motion.stops[r]=null}
   }
   return;
  }
 });
}
function visualBusy(){
 if(!motion)return false;const now=performance.now();for(let r=0;r<3;r++){const st=motion.stops[r];if(st&&now<st.at+st.duration)return true}return false;
}
function effectiveLampOn(now){
 if(!noticeOn)return false;
 if(!noticeBlink)return true;
 const e=now-noticeAt;
 return e>=1000||Math.floor(e/100)%2===0;
}
function renderLamp(){
 const on=effectiveLampOn(performance.now()),godlike=currentType==="JUGGLER_GOD"||currentType==="JUGGLER_GOD_EXTREME",img=$("lamp");
 const src=asset("lamp/piri_chance_"+(on?"on":"off")+".png");if(img.getAttribute("src")!==src)img.setAttribute("src",src);
 img.style.objectFit=godlike?"contain":"fill";
 img.style.filter=on?"drop-shadow(-4px 0 rgba(255,255,255,.18)) drop-shadow(4px 0 rgba(255,255,255,.18)) drop-shadow(0 4px rgba(255,255,255,.18))":"none";
}
function acceptSkillSuccess(p){
 if(currentType!=="SKILL_STOP"||!motion||!p||!p.spinId||String(p.spinId)!==String(motion.spinId))return false;
 const id=String(p.spinId);if(skillNoticePlayed.includes(id))return false;
 skillNoticePlayed.push(id);if(skillNoticePlayed.length>128)skillNoticePlayed.shift();return true;
}
function reconcileStoppedFromSnapshot(j){
 if(!motion||!motion.spinning||!j||!String(j.gameState||"").includes("SPINNING"))return;
 if(j.spinId&&String(j.spinId)!==String(motion.spinId))return;
 const mask=Number(j.stoppedMask||0),ds=j.displayStops||{},vals=[Number(ds.left||0),Number(ds.center||0),Number(ds.right||0)],now=performance.now();
 for(let r=0;r<3;r++)if((mask&(1<<r))!==0){
  if(motion.presses&&motion.presses[r])motion.presses[r]=null;
  if(!motion.stops[r])motion.stops[r]={from:vals[r],end:vals[r],target:vals[r],at:now,duration:0};
  else if(motion.stops[r].duration<=0)motion.stops[r].target=vals[r];
  if(motion.godFreeze){godRevealed[r]=true;if(godRevealAt[r]<0)godRevealAt[r]=now-250}
 }
 if(mask===7)nextGameAt=Math.max(nextGameAt,motion.at+2000);
}
function needsResume(j){
 return !!j&&String(j.gameState||"").includes("SPINNING")&&(!motion||!motion.spinning||!j.spinId||String(j.spinId)!==String(motion.spinId));
}
async function requestResume(reason){
 if(resumeInFlight||!currentState||!String(currentState.gameState||"").includes("SPINNING"))return;
 const now=performance.now();if(now-lastResumeAt<300)return;lastResumeAt=now;resumeInFlight=true;
 try{
  const j=await api("/api/resume","POST");
  if(j&&j.seated){applyState(j);handleEvents(j.events);reconcileStoppedFromSnapshot(j);$("gameMessage").textContent=""}
 }catch(e){$("gameMessage").textContent=errorText(e)}
 finally{resumeInFlight=false}
}
function applyState(j){
 if(!j)return;
 currentState=Object.assign({},currentState||{},j);currentType=currentState.machineType||currentType;lastSnapshotAt=performance.now();
 noticeOn=!!currentState.lampOn;
 const firstGodBig=!!currentState.godFirstBigAudio&&(currentType==="JUGGLER_GOD"||currentType==="JUGGLER_GOD_EXTREME");
 if(firstGodBig)godBigAudioPending=true;
 const gs=String(currentState.gameState||""),presentationEpoch=Number(currentState.godPresentationStartMs||0);
 if(presentationEpoch>0){
  godPresentationUntil=Math.max(godPresentationUntil,presentationEpoch+15000);
  if(!godPresentation||godPresentation.epoch!==presentationEpoch){
   const ds=currentState.displayStops||{},starts=[Number(ds.left||0),Number(ds.center||0),Number(ds.right||0)];
   let at=performance.now()-Math.max(0,Date.now()-presentationEpoch);
   if(godFreezeVisual){for(const t of godRevealAt)if(t>=0)at=Math.max(at,t)}
   godPresentation={epoch:presentationEpoch,at:at,starts:starts};
  }
 }else if(Date.now()>=godPresentationUntil){godPresentationUntil=0;godPresentation=null}
 reconcileStoppedFromSnapshot(currentState);
 const nowVisual=performance.now(),authoritativeGod=!!currentState.godFreeze,maskVisual=Number(currentState.stoppedMask||0),completedGod=gs==="BIG_READY";
 if(authoritativeGod){
  godFreezeVisual=true;if(godFreezeAt<0)godFreezeAt=nowVisual-165;
  for(let r=0;r<3;r++)if(completedGod||((maskVisual&(1<<r))!==0)){godRevealed[r]=true;if(godRevealAt[r]<0)godRevealAt[r]=nowVisual-250}
 }else if(!gs.includes("SPINNING")){
  godFreezeVisual=false;godFreezeAt=-1;godRevealed=[false,false,false];godRevealAt=[-1,-1,-1];godImpactAt=-1;
 }
 $("machineLabel").textContent="MACHINE "+currentState.machineId;
 $("credit").textContent=currentState.credit||0;$("bet").textContent=currentState.bet||0;$("pay").textContent=currentState.pay||0;$("medals").textContent=currentState.heldMedals||0;
 $("money").textContent=currentState.vaultBalance==null?"---":Number(currentState.vaultBalance).toLocaleString();
 if(currentState.loanAmount!=null)$("loanBtn").querySelector("span").textContent="LOAN "+Number(currentState.loanAmount).toLocaleString();
 const godlike=currentType==="JUGGLER_GOD"||currentType==="JUGGLER_GOD_EXTREME",skillstop=currentType==="SKILL_STOP";
 $("cabinet").classList.toggle("godlike",godlike);for(let r=0;r<3;r++)$("reel"+r).classList.toggle("godlike",godlike);$("stage").classList.toggle("skillstop",skillstop);
 $("skillChallenge").style.display=skillstop?"flex":"none";
 $("replayText").textContent=gs==="REPLAY_READY"?"REPLAY":"";
 $("countText").textContent=(gs.startsWith("BIG_")||gs.startsWith("REG_"))?(skillstop&&currentState.skillRemaining!=null?"残り "+currentState.skillRemaining+"G":"COUNT "+Number(currentState.bonusCount||0)):"";
 $("stockLamp").style.display=godlike?"flex":"none";$("stockLamp").classList.toggle("on",godlike&&!!currentState.stockLampOn);
 if(skillstop){
  const challenge=currentState.skillChallenge&&currentState.skillChallenge!=="AUTO"?String(currentState.skillChallenge).toLowerCase():"",img=$("skillChallengeImg");
  if(challenge){img.src=asset("symbols/"+challenge+".png");img.style.display="block"}else{img.removeAttribute("src");img.style.display="none"}
 }else{$("skillChallengeImg").removeAttribute("src");$("skillChallengeImg").style.display="none"}
 if(!gs.includes("SPINNING")&&!visualBusy()&&(!godPresentation||Date.now()>=godPresentationUntil)){if(motion)motion.spinning=false}
 if(!presentationEpoch&&Date.now()>=godPresentationUntil){godPresentationUntil=0;godPresentation=null}
 renderLamp();updateControlState();
}
function renderState(j){
 const events=j&&j.events||[];
 if(!currentState||events.length===0)applyState(j);
 else{
  currentState=Object.assign({},currentState,{
   loanAmount:j.loanAmount,loanMedals:j.loanMedals,loanAvailable:j.loanAvailable,vaultBalance:j.vaultBalance
  });
 }
 if(events.length)handleEvents(events);
 if(j&&needsResume(j)){applyState(j);requestResume("render")}
}
function updateControlState(){
 const spinning=currentState&&String(currentState.gameState||"").includes("SPINNING"),presentation=Date.now()<godPresentationUntil;
 const leverReady=currentState&&["NORMAL_BETTED","REPLAY_READY","BONUS_ENTRY_BETTED_BIG","BONUS_ENTRY_BETTED_REG","BIG_BETTED","REG_BETTED"].includes(String(currentState.gameState||""));
 $("betBtn").disabled=presentation||spinning;
 $("leverBtn").disabled=presentation||spinning||(!leverReady&&currentState&&String(currentState.gameState||"")!=="SEATED_READY");
 $("loanBtn").disabled=presentation||!currentState||currentState.loanAvailable===false;
 [["leftBtn",0],["centerBtn",1],["rightBtn",2]].forEach(function(x){$(x[0]).disabled=presentation||(spinning?!canStop(x[1]):true)});
}
async function pollState(force){
 try{
  const j=await api("/api/state");
  if(!j.seated){stopTimers();stopAllAudio();motion=null;currentState=null;show("lobby");loadMachines();return}
  const now=performance.now();
  if(!currentState){applyState(j);if(needsResume(j))requestResume("initial");return}
  currentState=Object.assign({},currentState,{loanAmount:j.loanAmount,loanMedals:j.loanMedals,loanAvailable:j.loanAvailable,vaultBalance:j.vaultBalance});
  if(needsResume(j)){applyState(j);requestResume("snapshot");return}
  reconcileStoppedFromSnapshot(j);
  const mismatch=String(currentState.gameState||"")!==String(j.gameState||"")||String(currentState.spinId||"")!==String(j.spinId||"");
  if(force||(mismatch&&!visualBusy()&&now-lastEventAt>750))applyState(j);
  else{renderLamp();updateControlState()}
 }catch(e){$("gameMessage").textContent=errorText(e)}
}
async function pollEvents(){
 try{
  const j=await api("/api/events");
  if(j.events&&j.events.length){
   handleEvents(j.events);
   j.events.forEach(function(ev){
    const p=ev.payload||{},type=ev.type||"";
    if(type==="NOTICE"){
     const snd=p.sound||"",skillSuccess=!!p.skillChallengeSuccess;
     if(skillSuccess&&!acceptSkillSuccess(p))return;
     if(snd==="NOTICE")playSound("notice");else if(snd==="NOTICE_STRONG")playSound("notice_strong");else if(snd==="NOTICE_X5")for(let n=0;n<5;n++)playLater(machineSound("notice"),"notice",n*100);
    }else if(type==="TENPAI_SOUND"){
     if(!motion||!p.spinId||String(p.spinId)===String(motion.spinId))playSound("tenpai");
    }else if(type==="PAYOUT")playSound("payout");
    else if(type==="BONUS_START"){
     const bonus=p.bonusType||"BONUS";$("gameMessage").textContent=bonus+" START";
     if(bonus==="BIG"){
      const godMachine=currentType==="JUGGLER_GOD"||currentType==="JUGGLER_GOD_EXTREME",godBig=godMachine&&(godBigAudioPending||!!(currentState&&currentState.godFirstBigAudio));
      godBigAudioPending=false;godBigAudioActive=godBig;
      const start=godBig?"juggler_god_god_bonus_start":machineSound("bonus_start"),fallback=machineSound("bonus_start");
      if(godBig){
       pendingBigBgmAt=0;pendingBigBgmName="";
       if(godPresentation){
        const delay=Math.max(0,godPresentation.at-performance.now());playLater(start,fallback,delay);
       }else pendingGodHitSound=start;
      }else{
       playNamed(start,fallback);pendingBigBgmName=machineSound("big_bgm");pendingBigBgmAt=performance.now()+4500;
      }
     }else if(bonus==="REG"){godBigAudioPending=false;godBigAudioActive=false;pendingBigBgmAt=0;pendingBigBgmName="";startLoop("reg_bgm")}
    }else if(type==="BONUS_END"){
     const bonus=p.bonusType||"BONUS";$("gameMessage").textContent=bonus+" END";pendingBigBgmAt=0;pendingBigBgmName="";pendingGodHitSound="";stopLoop();
     if(bonus==="BIG"){
      const godFirstEnd=godBigAudioActive&&(currentType==="JUGGLER_GOD"||currentType==="JUGGLER_GOD_EXTREME");
      playNamed(godFirstEnd?"juggler_god_god_bonus_end":machineSound("bonus_end"),machineSound("bonus_end"));
     }
     godBigAudioPending=false;godBigAudioActive=false;
    }
   });
  }
 }catch(e){}
}
async function pollData(){
 if(!currentState||!currentState.machineId)return;
 try{currentData=await api("/api/data?id="+currentState.machineId);renderGameData(currentData)}catch(e){}
}
function renderGameData(d){
 $("gCurrent").textContent=d.currentGames||0;$("gTotal").textContent=d.totalGames||0;$("gMax").textContent=signed(d.todayMaxDifference);$("gBig").textContent=d.bigCount||0;$("gReg").textContent=d.regCount||0;$("diffValue").textContent=signed(d.todayDifference);
 $("bigOdds").querySelector("b").textContent=odds(d.totalGames,d.bigCount);$("regOdds").querySelector("b").textContent=odds(d.totalGames,d.regCount);$("allOdds").querySelector("b").textContent=odds(d.totalGames,Number(d.bigCount||0)+Number(d.regCount||0));
 $("graphEnd").textContent=Number(d.totalGames||0)>0?Number(d.totalGames).toLocaleString()+"G":"-";
 const h=$("gameHistory");h.textContent="";(d.history||[]).forEach(function(x){const row=document.createElement("div");row.className="historyItem";let c=x.type==="BIG"?"big":x.type==="REG"?"reg":x.type==="GOD"?"god":"";row.innerHTML='<span class="'+c+'">'+x.type+'</span><span>'+x.games+'G</span>';h.append(row)});
 $("chain").classList.toggle("hidden",!d.piriChain);$("chainCount").textContent="CHAIN x"+Math.max(1,Number(d.piriChainCount||1));
 drawGraph($("gameGraph"),d.graph||[],d.totalGames||0);
}
function drawGraph(surface,graph,total){
 const rect=surface.getBoundingClientRect(),w=Math.max(1,Math.round(rect.width||surface.clientWidth||300)),h=Math.max(1,Math.round(rect.height||surface.clientHeight||150));
 surface.style.background="#090b0e";
 surface.textContent="";
 const ns="http://www.w3.org/2000/svg",svg=document.createElementNS(ns,"svg");
 svg.setAttribute("viewBox","0 0 "+w+" "+h);svg.setAttribute("width","100%");svg.setAttribute("height","100%");svg.setAttribute("preserveAspectRatio","none");
 const bg=document.createElementNS(ns,"rect");bg.setAttribute("x","0");bg.setAttribute("y","0");bg.setAttribute("width",String(w));bg.setAttribute("height",String(h));bg.setAttribute("fill","#090b0e");svg.append(bg);
 let vals=(graph||[]).filter(function(p){return Number(p.game)>=1&&Number(p.game)<=Number(total||0)});
 if(vals.length&&Number(total||0)>0){
  let min=Math.min.apply(null,vals.map(function(p){return Number(p.difference||0)})),max=Math.max.apply(null,vals.map(function(p){return Number(p.difference||0)}));
  if(min===max){min-=200;max+=200}else if(max-min<400){let mid=(min+max)/2;min=mid-200;max=mid+200}else{let pad=(max-min)*.05;min-=pad;max+=pad}
  if(min<=0&&max>=0){const zy=h-(0-min)/(max-min)*h,line=document.createElementNS(ns,"line");line.setAttribute("x1","0");line.setAttribute("x2",String(w));line.setAttribute("y1",String(zy));line.setAttribute("y2",String(zy));line.setAttribute("stroke","#777");line.setAttribute("stroke-width","1");svg.append(line)}
  const points=vals.map(function(p){const x=Number(total)<=1?w:(Number(p.game)-1)/(Number(total)-1)*w,y=h-(Number(p.difference||0)-min)/(max-min)*h;return x+","+y}).join(" ");
  const poly=document.createElementNS(ns,"polyline");poly.setAttribute("points",points);poly.setAttribute("fill","none");poly.setAttribute("stroke","#f4f4f4");poly.setAttribute("stroke-width","2");poly.setAttribute("vector-effect","non-scaling-stroke");svg.append(poly);
 }
 surface.append(svg);
}
async function doAction(type,reel){
 if(busy||!currentState)return;
 unlockAudio();
 if(Date.now()<godPresentationUntil)return;
 const beforeState=String(currentState.gameState||""),leverReady=["NORMAL_BETTED","REPLAY_READY","BONUS_ENTRY_BETTED_BIG","BONUS_ENTRY_BETTED_REG","BIG_BETTED","REG_BETTED"].includes(beforeState);
 if(type==="SPACE_ACTION"&&!beforeState.includes("SPINNING")&&leverReady&&performance.now()<nextGameAt){
  if(!queuedLeverTimer)queuedLeverTimer=setTimeout(function(){queuedLeverTimer=0;doAction("SPACE_ACTION",-1)},Math.max(0,nextGameAt-performance.now()));
  return;
 }
 let pressed=null;
 if(type.indexOf("STOP_")===0){
  if(!canStop(reel))return;pressed=Math.floor(currentPhase(reel,performance.now()));if(localStop(reel,pressed)===null)return;
 }else if(type==="SPACE_ACTION"&&beforeState.includes("SPINNING")){
  reel=nextPendingReel();if(!canStop(reel))return;pressed=Math.floor(currentPhase(reel,performance.now()));if(localStop(reel,pressed)===null)return;
 }
 busy=true;
 try{
  let path="/api/action?type="+encodeURIComponent(type);if(pressed!==null)path+="&pressed="+pressed;
  const j=await api(path,"POST");
  if(type==="SPACE_ACTION"&&!beforeState.includes("SPINNING")&&!beforeState.includes("BETTED")&&beforeState!=="REPLAY_READY")playSound("bet");
  renderState(j);$("gameMessage").textContent="";
 }catch(e){
  if(motion)for(let r=0;r<3;r++)if(motion.presses[r]){motion.presses[r]=null;motion.stops[r]=null}
  playSound("error");$("gameMessage").textContent=errorText(e);await pollState(true);
 }finally{busy=false}
}
async function loan(){unlockAudio();if(busy)return;busy=true;try{renderState(await api("/api/loan","POST"));$("gameMessage").textContent=""}catch(e){$("gameMessage").textContent=errorText(e)}finally{busy=false}}
async function insertMedals(){unlockAudio();if(busy)return;busy=true;try{renderState(await api("/api/insert","POST"));$("gameMessage").textContent=""}catch(e){$("gameMessage").textContent=errorText(e)}finally{busy=false}}
async function cashout(){unlockAudio();if(busy)return;busy=true;try{const j=await api("/api/cashout","POST");renderState(j);const pending=Number(j.cashoutPending||0);$("gameMessage").textContent=pending>0?("清算 "+j.cashoutAmount+"枚 / "+pending+"枚は回収待ち"):("清算 "+(j.cashoutAmount||0)+"枚")}catch(e){$("gameMessage").textContent=errorText(e)}finally{busy=false}}
async function loadPrizes(){
 try{
  const j=await api("/api/prizes");
  $("walletMedals").textContent=Number(j.walletMedals||0).toLocaleString();
  $("lobbyMoney").textContent=j.vaultBalance==null?"---":Number(j.vaultBalance).toLocaleString();
  $("smallPrizes").textContent=j.smallPrizes||0;$("mediumPrizes").textContent=j.mediumPrizes||0;$("largePrizes").textContent=j.largePrizes||0;
  $("buySmall").textContent="交換 "+Number(j.smallCost||0).toLocaleString()+"枚";
  $("buyMedium").textContent="交換 "+Number(j.mediumCost||0).toLocaleString()+"枚";
  $("buyLarge").textContent="交換 "+Number(j.largeCost||0).toLocaleString()+"枚";
 }catch(e){$("prizeMsg").textContent=errorText(e)}
}
async function buyPrize(type){
 if(busy)return;busy=true;$("prizeMsg").textContent="";
 try{await api("/api/prize/buy?type="+encodeURIComponent(type)+"&count=1","POST");$("prizeMsg").textContent="景品へ交換しました";await loadPrizes()}
 catch(e){$("prizeMsg").textContent=errorText(e)}finally{busy=false}
}
async function cashPrizes(){
 if(busy)return;busy=true;$("prizeMsg").textContent="";
 try{const j=await api("/api/prize/cash?type=all","POST");$("prizeMsg").textContent="景品を換金しました"+(j.vaultAdded!=null?" +"+Number(j.vaultAdded).toLocaleString():"");await loadPrizes()}
 catch(e){$("prizeMsg").textContent=errorText(e)}finally{busy=false}
}
function startGame(j){
 stopTimers();resetSlotRuntime();currentState=null;currentData=null;currentType=j.machineType||"";noticeOn=!!j.lampOn;noticeAt=performance.now();lastEventAt=performance.now();show("game");resizeStage();
 applyState(j);handleEvents(j.events);pollData();
 const gs=String(j.gameState||"");
 if(gs.startsWith("BIG_")){
  godBigAudioActive=!!j.godFirstBigAudio&&(currentType==="JUGGLER_GOD"||currentType==="JUGGLER_GOD_EXTREME");godBigAudioPending=false;
  startLoopNamed(godBigAudioActive?"juggler_god_god_big_bgm":machineSound("big_bgm"),machineSound("big_bgm"));
 }else if(gs.startsWith("REG_")){godBigAudioPending=false;godBigAudioActive=false;startLoop("reg_bgm")}
 else{godBigAudioPending=false;godBigAudioActive=false}
 if(needsResume(j))requestResume("start");
 stateTimer=setInterval(function(){pollState(false)},250);eventTimer=setInterval(pollEvents,100);dataTimer=setInterval(pollData,1500);
}
function stopTimers(){if(stateTimer)clearInterval(stateTimer);if(eventTimer)clearInterval(eventTimer);if(dataTimer)clearInterval(dataTimer);if(queuedLeverTimer)clearTimeout(queuedLeverTimer);stateTimer=0;eventTimer=0;dataTimer=0;queuedLeverTimer=0}
async function leave(){
 try{await api("/api/leave","POST")}catch(e){$("gameMessage").textContent=errorText(e);return}
 stopTimers();resetSlotRuntime();currentState=null;currentData=null;currentType="";show("lobby");loadMachines();
}
function frame(now){
 if(!$("game").classList.contains("hidden")){
  if(pendingGodHitSound&&godPresentation){
   const hit=pendingGodHitSound;pendingGodHitSound="";
   const delay=Math.max(0,godPresentation.at-now);playLater(hit,machineSound("bonus_start"),delay);
  }
  if(pendingBigBgmAt>0&&now>=pendingBigBgmAt){
   pendingBigBgmAt=0;const name=pendingBigBgmName;pendingBigBgmName="";if(name)startLoopNamed(name,"big_bgm");
  }
  drawReels(now);
  if(pendingState&&!visualBusy()){const j=pendingState;pendingState=null;applyState(j)}
  if(motion&&!visualBusy()&&currentState&&!String(currentState.gameState||"").includes("SPINNING"))motion.spinning=false;
  renderLamp();updateControlState();
 }
 requestAnimationFrame(frame);
}
$("pairBtn").onclick=pairNow;$("refresh").onclick=loadMachines;$("dataClose").onclick=function(){show("lobby")};
$("betBtn").onclick=function(){doAction("SPACE_ACTION",-1)};$("leverBtn").onclick=function(){doAction("SPACE_ACTION",-1)};
document.querySelectorAll(".stopBtn").forEach(function(b){b.onclick=function(){doAction(b.dataset.action,Number(b.dataset.reel))}});
$("loanBtn").onclick=loan;$("insertBtn").onclick=insertMedals;$("cashBtn").onclick=cashout;$("leaveBtn").onclick=leave;
$("prizeRefresh").onclick=loadPrizes;$("buySmall").onclick=function(){buyPrize("small")};$("buyMedium").onclick=function(){buyPrize("medium")};$("buyLarge").onclick=function(){buyPrize("large")};$("cashPrizes").onclick=cashPrizes;
$("logout").onclick=async function(){try{await api("/api/revoke","POST")}catch(e){}stopTimers();resetSlotRuntime();currentState=null;currentData=null;currentType="";token="";localStorage.removeItem("piriToken");localStorage.removeItem("piriPlayer");show("pair")};
window.addEventListener("resize",resizeStage);window.addEventListener("orientationchange",function(){setTimeout(resizeStage,50)});
window.addEventListener("online",function(){pollState(true)});
window.addEventListener("pageshow",function(){pollState(true)});
document.addEventListener("visibilitychange",function(){if(!document.hidden)pollState(true)});
window.addEventListener("focus",function(){pollState(true)});
requestAnimationFrame(frame);
if(token){
 $("player").textContent=player;
 api("/api/state").then(function(j){if(j.seated)startGame(j);else{show("lobby");loadMachines()}}).catch(function(e){if(e.message==="AUTH_LOADING")setTimeout(function(){location.reload()},1000);else show("pair")});
}else show("pair");
})();
</script>
</body>
</html>
""";
}

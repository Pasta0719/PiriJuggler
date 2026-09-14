package jp.pirijuggler.common.protocol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class SpecLockTest {
    private static final Path ROOT = Path.of(System.getProperty("piri.specRoot"));

    @Test void protocolAndPacketConstantsMatchExtractedSpecification() throws Exception {
        JsonObject lock = JsonParser.parseString(Files.readString(ROOT.resolve("docs/spec-lock.json"))).getAsJsonObject();
        assertEquals(lock.get("protocolVersion").getAsInt(), Protocol.VERSION);
        assertEquals(lock.get("channel").getAsString(), Protocol.CHANNEL);
        assertEquals(lock.get("magicHex").getAsString(), String.format("%08x", Protocol.MAGIC));
        assertEquals(lock.get("maxPayloadBytes").getAsInt(), Protocol.MAX_PAYLOAD_BYTES);
        assertEquals(lock.get("modVersion").getAsString(), Protocol.MOD_VERSION);
        assertEquals(lock.get("serverVersion").getAsString(), Protocol.SERVER_VERSION);
        JsonObject packetIds = lock.getAsJsonObject("packetIds");
        assertEquals(PacketType.values().length, packetIds.size());
        for (PacketType type : PacketType.values()) assertEquals(packetIds.get(type.name()).getAsInt(), type.id());
        var errors = lock.getAsJsonArray("errorCodes").asList().stream().map(e -> e.getAsString()).toList();
        assertEquals(errors, Arrays.stream(ErrorCode.values()).map(Enum::name).toList());
        assertEquals(PacketType.values().length, Arrays.stream(PacketType.values()).map(PacketType::id).collect(Collectors.toSet()).size());
    }

    @Test void lockIsTiedToTheActualSpecChapters() throws Exception {
        String spec = Files.readString(ROOT.resolve("SPEC.md")).replace("\r\n", "\n");
        Map<String, String> chapters = new LinkedHashMap<>();
        var matcher = Pattern.compile("^# (\\d+)\\. .*?(?=^# \\d+\\. |\\z)", Pattern.MULTILINE | Pattern.DOTALL).matcher(spec);
        while (matcher.find()) chapters.put(matcher.group(1), matcher.group());
        JsonObject lock = JsonParser.parseString(Files.readString(ROOT.resolve("docs/spec-lock.json"))).getAsJsonObject();
        for (var entry : lock.getAsJsonObject("sectionSha256").entrySet()) {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(chapters.get(entry.getKey()).getBytes(StandardCharsets.UTF_8)));
            assertEquals(entry.getValue().getAsString(), hash, "SPEC chapter " + entry.getKey() + " changed; audit and regenerate lock");
        }
    }
}

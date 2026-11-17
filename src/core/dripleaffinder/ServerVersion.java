package dripleaffinder;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;

final class ServerVersion {
    private final Map<String, Query> queries = new ConcurrentHashMap<>();
    private String joinedKey;
    private ClientPacketListener connection;
    private int ticks;

    private static final class Query {
        volatile String version;
        volatile boolean asking = true;
    }

    void tick(Minecraft mc) {
        if (ticks++ % 20 != 0) return;
        String now = Finder.connectionKey(mc);
        if (now != null && (!now.equals(joinedKey) || connection != mc.getConnection())) joined(now, Compat.serverAddress(mc));
        joinedKey = now;
        connection = mc.getConnection();
    }

    String reported(String key) {
        Query query = queries.get(key);
        return query == null ? null : query.version;
    }

    boolean ready(String key, ClientPacketListener current) {
        Query query = queries.get(key);
        return current != null && current == connection && key.equals(joinedKey) && query != null && !query.asking;
    }

    private void joined(String key, String address) {
        if (address == null || key == null) return;
        Query query = new Query();
        queries.put(key, query);
        Thread thread = new Thread(() -> {
            try {
                String version = detect(address);
                query.version = version == null ? "" : version;
            } finally {
                query.asking = false;
            }
        }, "Dripleaf server version");
        thread.setDaemon(true);
        thread.start();
    }

    static String detect(String address) {
        try {
            String[] reply = status(address);
            return Versions.fromStatus(reply[0], Integer.parseInt(reply[1]));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    static String[] status(String address) throws IOException {
        ServerAddress target = address(address);
        String host = target.getHost();
        int port = target.getPort();
        var resolved = ServerNameResolver.DEFAULT.resolveAddress(target).map(ResolvedServerAddress::asInetSocketAddress)
            .orElseThrow(() -> new IOException("Could not resolve the server address"));
        try (Socket socket = new Socket()) {
            socket.connect(resolved, 5000);
            socket.setSoTimeout(5000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());
            ByteArrayOutputStream handshake = new ByteArrayOutputStream();
            DataOutputStream h = new DataOutputStream(handshake);
            h.writeByte(0x00);
            varInt(h, -1);
            byte[] name = host.getBytes(StandardCharsets.UTF_8);
            varInt(h, name.length);
            h.write(name);
            h.writeShort(port);
            varInt(h, 1);
            varInt(out, handshake.size());
            out.write(handshake.toByteArray());
            out.writeByte(1);
            out.writeByte(0x00);
            out.flush();
            readVarInt(in);
            if (readVarInt(in) != 0x00) throw new IOException("not a status reply");
            int length = readVarInt(in);
            if (length < 0 || length > 1 << 20) throw new IOException("status reply of " + length + " bytes");
            byte[] json = new byte[length];
            in.readFully(json);
            JsonObject version = new Gson().fromJson(new String(json, StandardCharsets.UTF_8), JsonObject.class).getAsJsonObject("version");
            return new String[] {version.get("name").getAsString(), String.valueOf(version.get("protocol").getAsInt())};
        }
    }

    static ServerAddress address(String text) throws IOException {
        if (text == null || text.isBlank()) throw new IOException("Empty server address");
        ServerAddress address = ServerAddress.parseString(text.trim());
        if (address.getHost().isEmpty()) throw new IOException("Invalid server address");
        return address;
    }

    private static void varInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int b = in.readUnsignedByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
        }
        throw new IOException("varint too long");
    }
}

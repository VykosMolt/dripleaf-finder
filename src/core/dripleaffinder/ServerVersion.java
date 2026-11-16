package dripleaffinder;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;
import javax.naming.directory.Attribute;
import javax.naming.directory.InitialDirContext;

final class ServerVersion {
    static String detect(String address) {
        try {
            String[] reply = status(address);
            return Versions.fromStatus(reply[0], Integer.parseInt(reply[1]));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    static String[] status(String address) throws IOException {
        String host = address.trim();
        int port = 25565;
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) {
            port = Integer.parseInt(host.substring(colon + 1));
            host = host.substring(0, colon);
        } else {
            String[] srv = srv(host);
            if (srv != null) {
                host = srv[0];
                port = Integer.parseInt(srv[1]);
            }
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
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

    private static String[] srv(String host) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            Attribute record = new InitialDirContext(env).getAttributes("_minecraft._tcp." + host, new String[] {"SRV"}).get("SRV");
            if (record == null) return null;
            String[] f = record.get().toString().split(" ");
            return new String[] {f[3].endsWith(".") ? f[3].substring(0, f[3].length() - 1) : f[3], f[2]};
        } catch (Exception | LinkageError e) {
            return null;
        }
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

    private ServerVersion() {
    }
}

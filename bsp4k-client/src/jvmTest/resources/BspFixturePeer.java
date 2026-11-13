import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Independent literal BSP peer, launched in a child JVM with private standard streams. */
public class BspFixturePeer {
    private static final Pattern ID = Pattern.compile("\\\"id\\\"\\s*:\\s*(\\d+)");

    private static String readFrame(InputStream in) throws Exception {
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        int state = 0;
        while (state < 4) {
            int value = in.read();
            if (value < 0) throw new EOFException("incomplete header");
            header.write(value);
            if (header.size() > 16_384) throw new IllegalStateException("header too large");
            int expected = new int[] {13, 10, 13, 10}[state];
            state = value == expected ? state + 1 : (value == 13 ? 1 : 0);
        }
        String text = header.toString(StandardCharsets.US_ASCII);
        Matcher length = Pattern.compile("(?i)content-length:\\s*(\\d+)").matcher(text);
        if (!length.find()) throw new IllegalStateException("missing length");
        int count = Integer.parseInt(length.group(1));
        if (count < 0 || count > 65_536) throw new IllegalStateException("invalid length");
        byte[] body = in.readNBytes(count);
        if (body.length != count) throw new EOFException("incomplete body");
        return new String(body, StandardCharsets.UTF_8);
    }

    private static void requireMethod(String body, String method) {
        if (!body.contains("\"method\":\"" + method + "\"")) throw new IllegalStateException("unexpected request: " + body);
    }

    private static void reply(OutputStream out, String request, String result) throws Exception {
        Matcher matcher = ID.matcher(request);
        if (!matcher.find()) throw new IllegalStateException("missing id");
        byte[] body = ("{\"jsonrpc\":\"2.0\",\"id\":" + matcher.group(1) + ",\"result\":" + result + "}").getBytes(StandardCharsets.UTF_8);
        out.write(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    public static void main(String[] args) throws Exception {
        InputStream in = System.in;
        OutputStream out = System.out;
        String initialize = readFrame(in);
        requireMethod(initialize, "build/initialize");
        if (!initialize.contains("file:///fixture")) throw new IllegalStateException("missing root URI");
        reply(out, initialize, "{\"displayName\":\"child-π\",\"version\":\"1\",\"bspVersion\":\"2.1.0\",\"capabilities\":{}}");
        requireMethod(readFrame(in), "build/initialized");
        String targets = readFrame(in);
        requireMethod(targets, "workspace/buildTargets");
        if (args.length != 0 && args[0].equals("eof")) return;
        if (args.length != 0 && args[0].equals("hold")) {
            byte[] ready = "{\"jsonrpc\":\"2.0\",\"method\":\"fixture/ready\"}".getBytes(StandardCharsets.UTF_8);
            out.write(("Content-Length: " + ready.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(ready);
            out.flush();
            while (in.read() != -1) { /* Keep stdout open while waiting for the owner. */ }
            return;
        }
        reply(out, targets, "{\"targets\":[]}");
        String shutdown = readFrame(in);
        requireMethod(shutdown, "build/shutdown");
        reply(out, shutdown, "null");
        requireMethod(readFrame(in), "build/exit");
    }
}

package dev.shubham.transcoder.prepare;

import dev.shubham.transcoder.config.ClamAvProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ClamAV adapter for {@link VirusScanner}. Streams the source to {@code clamd} over the
 * INSTREAM protocol before splitting. A positive detection returns {@code false} (a permanent
 * rejection at the handler); a connection/protocol failure throws (transient → dead-lettered).
 */
@Service
public class ClamAvVirusScanner implements VirusScanner {

    private static final int CHUNK = 8192;
    private static final int SOCKET_TIMEOUT_MS = 60_000;

    private final ClamAvProperties properties;

    public ClamAvVirusScanner(ClamAvProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isClean(Path file) {
        try (Socket socket = new Socket(properties.host(), properties.port())) {
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            try (OutputStream out = socket.getOutputStream();
                 InputStream in = socket.getInputStream();
                 InputStream fileIn = Files.newInputStream(file)) {

                out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
                byte[] buffer = new byte[CHUNK];
                int read;
                while ((read = fileIn.read(buffer)) != -1) {
                    out.write(ByteBuffer.allocate(4).putInt(read).array()); // 4-byte BE length
                    out.write(buffer, 0, read);
                }
                out.write(new byte[]{0, 0, 0, 0}); // zero-length chunk = end of stream
                out.flush();

                String response = new String(in.readAllBytes(), StandardCharsets.US_ASCII).trim();
                if (response.endsWith("OK")) {
                    return true;
                }
                if (response.contains("FOUND")) {
                    return false;
                }
                throw new IllegalStateException("unexpected clamd response: " + response);
            }
        } catch (IOException e) {
            throw new IllegalStateException("clamd scan failed", e); // transient
        }
    }
}

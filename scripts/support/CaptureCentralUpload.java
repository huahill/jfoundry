import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

public final class CaptureCentralUpload {
    private CaptureCentralUpload() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: CaptureCentralUpload <port-file> <request-file>");
        }

        Path portFile = Path.of(args[0]);
        Path requestFile = Path.of(args[1]);
        CountDownLatch requestHandled = new CountDownLatch(1);
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/api/v1/publisher/upload", exchange -> {
            try {
                captureRequest(exchange, requestFile);
            } finally {
                requestHandled.countDown();
            }
        });
        server.start();
        Files.writeString(
                portFile,
                Integer.toString(server.getAddress().getPort()),
                StandardCharsets.UTF_8);
        requestHandled.await();
        server.stop(0);
    }

    private static void captureRequest(HttpExchange exchange, Path requestFile)
            throws java.io.IOException {
        long requestBytes;
        try (var body = exchange.getRequestBody()) {
            requestBytes = body.transferTo(java.io.OutputStream.nullOutputStream());
        }
        String evidence = String.join(
                "\n",
                "method=" + exchange.getRequestMethod(),
                "path=" + exchange.getRequestURI().getPath(),
                "query=" + exchange.getRequestURI().getRawQuery(),
                "contentType=" + exchange.getRequestHeaders().getFirst("Content-Type"),
                "authorizationPresent="
                        + (exchange.getRequestHeaders().getFirst("Authorization") != null),
                "requestBytes=" + requestBytes,
                "");
        Files.writeString(requestFile, evidence, StandardCharsets.UTF_8);

        byte[] response = "mason-poc-deployment".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(201, response.length);
        try (var responseBody = exchange.getResponseBody()) {
            responseBody.write(response);
        }
    }
}

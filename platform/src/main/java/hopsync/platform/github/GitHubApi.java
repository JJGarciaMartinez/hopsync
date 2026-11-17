package hopsync.platform.github;

import hopsync.core.failure.SyncException;
import hopsync.core.failure.SyncFailure;
import hopsync.core.util.Json;
import hopsync.core.util.JsonParseException;
import hopsync.platform.http.NetworkConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** The GitHub REST API with the player's token, answering parsed JSON. */
public class GitHubApi {
    static final String API = "https://api.github.com";

    /** One answer: its HTTP status and body. */
    public record Response(int status, String body) {
    }

    /** Sends a request; replaced in tests. */
    public interface Transport {
        /** @param json the request body, or {@code null} */
        Response send(String method, String url, String json, String token) throws IOException;
    }

    private final Transport transport;

    public GitHubApi(NetworkConfig network) {
        this(new HttpTransport(network));
    }

    GitHubApi(Transport transport) {
        this.transport = transport;
    }

    /** @return the answer's status and JSON; 401 means the token no longer works */
    Answer call(String method, String path, Object body, String token) throws IOException {
        Response response = transport.send(method, path.startsWith("https://") ? path : API + path,
            body == null ? null : Json.write(body), token);
        if (response.status() == 401) {
            throw new SyncException(new SyncFailure.GitHubSignedOut());
        }
        Object json = null;
        if (response.body() != null && !response.body().isBlank()) {
            try {
                json = Json.parse(response.body());
            } catch (JsonParseException e) {
                throw new SyncException(new SyncFailure.Unexpected("GitHub answered " + method + " " + path
                    + " with something that is not JSON"), e);
            }
        }
        return new Answer(response.status(), json, method + " " + path);
    }

    /** A parsed answer. */
    record Answer(int status, Object json, String request) {
        boolean ok() {
            return status >= 200 && status < 300;
        }

        /** The answer as an object, or a failure naming the request when GitHub refused it. */
        Map<?, ?> object() throws SyncException {
            if (!ok() || !(json instanceof Map<?, ?> map)) {
                throw new SyncException(new SyncFailure.HttpStatus(status, request));
            }
            return map;
        }

        String text(String key) throws SyncException {
            if (object().get(key) instanceof String value) return value;
            throw new SyncException(new SyncFailure.Unexpected(request + " answered without " + key));
        }
    }

    private static final class HttpTransport implements Transport {
        private final NetworkConfig network;
        private final HttpClient client;

        HttpTransport(NetworkConfig network) {
            this.network = network;
            this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(network.connectTimeoutMillis()))
                .build();
        }

        @Override
        public Response send(String method, String url, String json, String token) throws IOException {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + token)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", network.userAgent())
                .header("Content-Type", "application/json")
                // Uploads of own jars can take a while; the network timeout is for a stalled connection
                .timeout(Duration.ofMillis(Math.max(network.readTimeoutMillis(), 120_000)))
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                return new Response(response.statusCode(), response.body());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while calling GitHub", e);
            } catch (IOException e) {
                throw new SyncException(new SyncFailure.Network(URI.create(url).getHost(), e.toString()), e);
            }
        }
    }
}

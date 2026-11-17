package hopsync.platform.github;

import hopsync.core.failure.SyncException;
import hopsync.core.failure.SyncFailure;
import hopsync.core.util.CancellationToken;
import hopsync.core.util.Json;
import hopsync.core.util.JsonParseException;
import hopsync.platform.http.NetworkConfig;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Signs the player in to GitHub with the OAuth device flow: HopSync shows a short code, the player types it on
 * github.com and approves, and HopSync receives a token. No secret ships with the app; the token can create and
 * write public repositories only ({@value #SCOPE}).
 */
public class GitHubAuth {
    static final String DEVICE_CODE_URL = "https://github.com/login/device/code";
    static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    static final String USER_URL = "https://api.github.com/user";
    static final String SCOPE = "public_repo";
    /** GitHub asks to wait this much longer between polls each time it answers "slow_down". */
    private static final Duration SLOW_DOWN = Duration.ofSeconds(5);

    /** The HTTP calls the flow makes; replaced in tests. */
    public interface Transport {
        /** POSTs a form and returns the JSON answer. */
        String postForm(String url, Map<String, String> form) throws IOException;

        /** GETs a GitHub API document with the token. */
        String getWithToken(String url, String token) throws IOException;
    }

    /** Waits between polls; replaced in tests so they need not sleep. */
    public interface Sleeper {
        void sleep(Duration duration, CancellationToken cancel) throws IOException;
    }

    /**
     * What the player needs to sign in.
     *
     * @param userCode        the code to type on GitHub, e.g. "WDJB-MJHT"
     * @param verificationUri where to type it
     * @param interval        how long to wait between polls, as GitHub asks
     */
    public record DeviceCode(String deviceCode, String userCode, String verificationUri, Duration expiresIn,
                             Duration interval) {
    }

    private final String clientId;
    private final Transport transport;
    private final Sleeper sleeper;

    /** @param clientId the GitHub OAuth app's client id; blank when this build has none */
    public GitHubAuth(String clientId, NetworkConfig network) {
        this(clientId, new HttpTransport(network), GitHubAuth::sleepUnlessCancelled);
    }

    GitHubAuth(String clientId, Transport transport, Sleeper sleeper) {
        this.clientId = clientId == null ? "" : clientId.strip();
        this.transport = transport;
        this.sleeper = sleeper;
    }

    public boolean configured() {
        return !clientId.isEmpty();
    }

    /** @throws SyncException with {@link SyncFailure.GitHubNotConfigured} when this build has no client id */
    public DeviceCode start() throws IOException {
        if (!configured()) throw new SyncException(new SyncFailure.GitHubNotConfigured());
        Map<?, ?> answer = parse(transport.postForm(DEVICE_CODE_URL, Map.of("client_id", clientId, "scope", SCOPE)));
        return new DeviceCode(text(answer, "device_code"), text(answer, "user_code"), text(answer, "verification_uri"),
            Duration.ofSeconds(number(answer, "expires_in", 900)), Duration.ofSeconds(number(answer, "interval", 5)));
    }

    /**
     * Waits until the player approves on GitHub and returns the token.
     *
     * @throws SyncException with {@link SyncFailure.GitHubCodeExpired} or {@link SyncFailure.GitHubDenied}
     */
    public String awaitToken(DeviceCode code, CancellationToken cancel) throws IOException {
        Duration interval = code.interval();
        while (true) {
            sleeper.sleep(interval, cancel);
            Map<?, ?> answer = parse(transport.postForm(TOKEN_URL, Map.of("client_id", clientId,
                "device_code", code.deviceCode(), "grant_type", "urn:ietf:params:oauth:grant-type:device_code")));
            if (answer.get("access_token") instanceof String token && !token.isEmpty()) {
                return token;
            }
            switch (answer.get("error") instanceof String error ? error : "") {
                case "authorization_pending" -> { }
                case "slow_down" -> interval = interval.plus(SLOW_DOWN);
                case "expired_token" -> throw new SyncException(new SyncFailure.GitHubCodeExpired());
                case "access_denied" -> throw new SyncException(new SyncFailure.GitHubDenied());
                default -> throw new SyncException(new SyncFailure.Unexpected("GitHub device flow: " + answer.get("error")));
            }
        }
    }

    /** The GitHub user name the token belongs to. */
    public String login(String token) throws IOException {
        return text(parse(transport.getWithToken(USER_URL, token)), "login");
    }

    private static Map<?, ?> parse(String json) throws IOException {
        try {
            return (Map<?, ?>) Json.parse(json);
        } catch (JsonParseException | ClassCastException e) {
            throw new SyncException(new SyncFailure.Unexpected("GitHub answered with something that is not JSON"), e);
        }
    }

    private static String text(Map<?, ?> map, String key) throws SyncException {
        if (map.get(key) instanceof String value && !value.isEmpty()) return value;
        throw new SyncException(new SyncFailure.Unexpected("GitHub answer without " + key));
    }

    private static long number(Map<?, ?> map, String key, long fallback) {
        return map.get(key) instanceof Number value ? value.longValue() : fallback;
    }

    private static void sleepUnlessCancelled(Duration duration, CancellationToken cancel) throws IOException {
        long until = System.nanoTime() + duration.toNanos();
        while (System.nanoTime() < until) {
            cancel.throwIfCancelled();
            try {
                Thread.sleep(Math.min(200, Math.max(1, (until - System.nanoTime()) / 1_000_000)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for GitHub", e);
            }
        }
        cancel.throwIfCancelled();
    }

    /** The real calls, with the app's user agent and timeouts. */
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
        public String postForm(String url, Map<String, String> form) throws IOException {
            String body = form.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
            return send(HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)), url);
        }

        @Override
        public String getWithToken(String url, String token) throws IOException {
            return send(HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + token)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET(), url);
        }

        private String send(HttpRequest.Builder request, String url) throws IOException {
            HttpResponse<String> response;
            try {
                response = client.send(request.header("User-Agent", network.userAgent())
                    .timeout(Duration.ofMillis(network.readTimeoutMillis())).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while calling GitHub", e);
            } catch (IOException e) {
                throw new SyncException(new SyncFailure.Network(URI.create(url).getHost(), e.toString()), e);
            }
            if (response.statusCode() >= 400) {
                throw new SyncException(new SyncFailure.HttpStatus(response.statusCode(), url));
            }
            return response.body();
        }

        private static String encode(String value) {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        }
    }
}

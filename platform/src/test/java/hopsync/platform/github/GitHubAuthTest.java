package hopsync.platform.github;

import hopsync.core.failure.SyncException;
import hopsync.core.failure.SyncFailure;
import hopsync.core.util.CancellationToken;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitHubAuthTest {
    private final Deque<String> answers = new ArrayDeque<>();
    private final List<String> asked = new ArrayList<>();
    private final List<Duration> waits = new ArrayList<>();

    private final GitHubAuth.Transport transport = new GitHubAuth.Transport() {
        @Override
        public String postForm(String url, Map<String, String> form) {
            asked.add(url + " " + form.get("client_id") + " " + form.getOrDefault("scope", form.get("grant_type")));
            return answers.poll();
        }

        @Override
        public String getWithToken(String url, String token) {
            asked.add(url + " " + token);
            return answers.poll();
        }
    };

    private GitHubAuth auth(String clientId) {
        return new GitHubAuth(clientId, transport, (duration, cancel) -> waits.add(duration));
    }

    @Test
    void theDeviceFlowWaitsForThePlayerAndSlowsDownWhenAsked() throws Exception {
        answers.add("{\"device_code\":\"dev\",\"user_code\":\"WDJB-MJHT\",\"verification_uri\":\"https://github.com/login/device\","
            + "\"expires_in\":900,\"interval\":5}");
        answers.add("{\"error\":\"authorization_pending\"}");
        answers.add("{\"error\":\"slow_down\"}");
        answers.add("{\"access_token\":\"gho_token\",\"token_type\":\"bearer\",\"scope\":\"public_repo\"}");
        answers.add("{\"login\":\"player\"}");
        GitHubAuth auth = auth("client");

        GitHubAuth.DeviceCode code = auth.start();
        String token = auth.awaitToken(code, CancellationToken.none());

        assertEquals("WDJB-MJHT", code.userCode());
        assertEquals("gho_token", token);
        assertEquals("player", auth.login(token));
        assertEquals(List.of(Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofSeconds(10)), waits);
        assertEquals(GitHubAuth.DEVICE_CODE_URL + " client public_repo", asked.getFirst());
        assertEquals(GitHubAuth.USER_URL + " gho_token", asked.getLast());
    }

    @Test
    void anExpiredCodeOrARefusalEndTheWait() throws Exception {
        GitHubAuth.DeviceCode code = new GitHubAuth.DeviceCode("dev", "C", "https://github.com/login/device",
            Duration.ofMinutes(15), Duration.ofSeconds(5));
        answers.add("{\"error\":\"expired_token\"}");
        assertEquals(new SyncFailure.GitHubCodeExpired(),
            assertThrows(SyncException.class, () -> auth("client").awaitToken(code, CancellationToken.none())).failure());
        answers.add("{\"error\":\"access_denied\"}");
        assertEquals(new SyncFailure.GitHubDenied(),
            assertThrows(SyncException.class, () -> auth("client").awaitToken(code, CancellationToken.none())).failure());
    }

    @Test
    void aBuildWithoutAClientIdCannotSignIn() {
        GitHubAuth auth = auth("  ");
        assertFalse(auth.configured());
        assertEquals(new SyncFailure.GitHubNotConfigured(), assertThrows(SyncException.class, auth::start).failure());
        assertEquals(List.of(), asked);
    }
}

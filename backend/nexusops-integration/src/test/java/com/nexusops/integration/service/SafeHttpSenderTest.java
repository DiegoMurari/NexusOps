package com.nexusops.integration.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SafeHttpSenderTest {

    private static final String URL = "https://hooks.example.com/in";

    @Mock
    private HttpClient client;
    @Mock
    private HttpResponse<Void> response;

    private SafeHttpSender sender;

    private static InetAddress ip(String literal) throws UnknownHostException {
        return InetAddress.getByName(literal);
    }

    @BeforeEach
    void setUp() {
        sender = new SafeHttpSender(host -> new InetAddress[] {ip("93.184.216.34")}, client);
    }

    @SuppressWarnings("unchecked")
    private void givenResponse(int status) throws Exception {
        when(response.statusCode()).thenReturn(status);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }

    @SuppressWarnings("unchecked")
    private void givenFailure(Exception e) throws Exception {
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(e);
    }

    @Test
    void post_sendsToAPublicAddress_andReportsTheStatus() throws Exception {
        givenResponse(204);

        var result = sender.post(URL, "{}", Map.of("X-Test", "1"), 10);

        assertThat(result.responded()).isTrue();
        assertThat(result.delivered()).isTrue();
        assertThat(result.httpStatus()).isEqualTo(204);
        assertThat(result.summary()).isEqualTo("HTTP 204");
        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(), any());
        assertThat(request.getValue().method()).isEqualTo("POST");
        assertThat(request.getValue().headers().firstValue("X-Test")).contains("1");
        assertThat(request.getValue().headers().firstValue("User-Agent")).contains("NexusOps-Integrations/1");
    }

    @Test
    void redirectsAreReportedAsTheyAre_notFollowed() throws Exception {
        givenResponse(302);

        var result = sender.get(URL, 5);

        assertThat(result.responded()).isTrue();
        assertThat(result.delivered()).isFalse();
        assertThat(result.reachable()).isTrue();
        assertThat(result.httpStatus()).isEqualTo(302);
    }

    @Test
    void serverErrorsResponded_butAreNotReachable() throws Exception {
        givenResponse(503);

        var result = sender.get(URL, 5);

        assertThat(result.responded()).isTrue();
        assertThat(result.reachable()).isFalse();
        assertThat(result.delivered()).isFalse();
    }

    @Test
    void blocksNamesThatResolveToInternalAddresses_withoutConnecting() throws Exception {
        for (String internal : new String[] {"127.0.0.1", "10.1.2.3", "192.168.0.10", "172.16.5.5", "169.254.169.254",
            "0.0.0.0", "::1", "fd00::1"}) {
            var blocked = new SafeHttpSender(host -> new InetAddress[] {ip(internal)}, client);

            var result = blocked.post(URL, "{}", Map.of(), 5);

            assertThat(result.responded()).as(internal).isFalse();
            assertThat(result.failure()).as(internal).isEqualTo("BLOCKED_ADDRESS");
        }
        verifyNoInteractions(client);
    }

    @Test
    void blocksWhenAnyResolvedAddressIsInternal_evenIfOthersArePublic() throws Exception {
        var mixed = new SafeHttpSender(host -> new InetAddress[] {ip("93.184.216.34"), ip("10.0.0.7")}, client);

        var result = mixed.get(URL, 5);

        assertThat(result.failure()).isEqualTo("BLOCKED_ADDRESS");
        verifyNoInteractions(client);
    }

    @Test
    void rejectsUnsafeUrlsBeforeResolvingOrConnecting() {
        for (String url : new String[] {"http://hooks.example.com/in", "https://user:pw@hooks.example.com/in",
            "https://localhost/in", "https://169.254.169.254/latest", "not a url", ""}) {
            var result = sender.get(url, 5);

            assertThat(result.failure()).as(url).isEqualTo("INVALID_URL");
        }
        verifyNoInteractions(client);
    }

    @Test
    void reportsDnsFailureByCategory() {
        var unresolved = new SafeHttpSender(host -> {
            throw new UnknownHostException("secret-internal-name");
        }, client);

        var result = unresolved.get(URL, 5);

        assertThat(result.failure()).isEqualTo("DNS_FAILURE");
        assertThat(result.summary()).doesNotContain("secret-internal-name");
    }

    @Test
    void mapsNetworkErrorsToFixedCategories_neverTheExceptionText() throws Exception {
        givenFailure(new HttpTimeoutException("request timed out to 10.0.0.1"));
        assertThat(sender.get(URL, 5).failure()).isEqualTo("TIMEOUT");

        givenFailure(new ConnectException("refused 10.0.0.1:443"));
        assertThat(sender.get(URL, 5).failure()).isEqualTo("CONNECTION_FAILED");

        givenFailure(new SSLHandshakeException("PKIX path building failed"));
        assertThat(sender.get(URL, 5).failure()).isEqualTo("TLS_ERROR");

        givenFailure(new IOException("wrapped", new SSLHandshakeException("bad cert")));
        assertThat(sender.get(URL, 5).failure()).isEqualTo("TLS_ERROR");

        givenFailure(new IOException("boom 10.0.0.1"));
        var generic = sender.get(URL, 5);
        assertThat(generic.failure()).isEqualTo("IO_ERROR");
        assertThat(generic.summary()).doesNotContain("10.0.0.1");
    }

    @Test
    void interruptionRestoresTheFlagAndReportsACategory() throws Exception {
        givenFailure(new InterruptedException());

        var result = sender.get(URL, 5);

        assertThat(result.failure()).isEqualTo("INTERRUPTED");
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void timeoutIsClampedToTheMaximum() throws Exception {
        givenResponse(200);

        sender.get(URL, 600);

        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(), any());
        assertThat(request.getValue().timeout().orElseThrow().toSeconds()).isEqualTo(SafeHttpSender.MAX_TIMEOUT_SECONDS);
    }
}

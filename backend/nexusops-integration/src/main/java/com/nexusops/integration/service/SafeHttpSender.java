package com.nexusops.integration.service;

import com.nexusops.shared.exception.ValidationException;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

/**
 * Única porta de saída HTTP das integrações. Cada chamada: só https, sem credenciais na URL, DNS resolvido
 * e <b>todos</b> os endereços conferidos contra a rede interna logo antes de conectar, sem seguir
 * redirecionamentos (um 302 poderia levar a um alvo interno), tempo limite curto e corpo da resposta
 * descartado (nada do que o destino responde é lido ou guardado).
 *
 * <p>Limite conhecido: o JDK resolve o nome de novo ao conectar, então uma janela estreita de DNS rebinding
 * entre a conferência e a conexão não é eliminada aqui; a mitigação é o acesso restrito a quem tem
 * {@code INTEGRATION:UPDATE} e a exigência de https.
 *
 * <p>Os erros voltam como categorias fixas (TIMEOUT, DNS_FAILURE...), nunca o texto da exceção, que pode
 * revelar endereços internos.
 */
@Component
public class SafeHttpSender {

    static final int MAX_TIMEOUT_SECONDS = 15;
    private static final String USER_AGENT = "NexusOps-Integrations/1";

    /** Resultado de uma chamada. {@code failure} é uma categoria curta e só existe quando não houve resposta. */
    public record Result(boolean responded, Integer httpStatus, long durationMs, String failure) {

        /** Entrega bem-sucedida: o destino respondeu 2xx. */
        public boolean delivered() {
            return responded && httpStatus != null && httpStatus >= 200 && httpStatus < 300;
        }

        /** O endereço está de pé: respondeu e não com erro de servidor. */
        public boolean reachable() {
            return responded && httpStatus != null && httpStatus < 500;
        }

        /** Texto curto e seguro para mostrar ao usuário e gravar no log. */
        public String summary() {
            if (!responded) {
                return failure;
            }
            return "HTTP " + httpStatus;
        }
    }

    @FunctionalInterface
    interface Resolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final Resolver resolver;
    private final HttpClient client;

    public SafeHttpSender() {
        this(InetAddress::getAllByName, HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build());
    }

    SafeHttpSender(Resolver resolver, HttpClient client) {
        this.resolver = resolver;
        this.client = client;
    }

    public Result post(String url, String body, Map<String, String> headers, int timeoutSeconds) {
        return send(url, timeoutSeconds, headers, HttpRequest.BodyPublishers.ofString(body), "POST");
    }

    public Result get(String url, int timeoutSeconds) {
        return send(url, timeoutSeconds, Map.of(), HttpRequest.BodyPublishers.noBody(), "GET");
    }

    private Result send(String url, int timeoutSeconds, Map<String, String> headers,
                        HttpRequest.BodyPublisher body, String method) {
        long start = System.nanoTime();
        URI uri;
        try {
            uri = URI.create(WebhookUrlValidator.validate(url));
        } catch (ValidationException | IllegalArgumentException e) {
            return failed("INVALID_URL", start);
        }
        try {
            for (InetAddress address : resolver.resolve(uri.getHost())) {
                if (WebhookUrlValidator.isForbiddenAddress(address)) {
                    return failed("BLOCKED_ADDRESS", start);
                }
            }
        } catch (UnknownHostException e) {
            return failed("DNS_FAILURE", start);
        }

        int timeout = Math.min(Math.max(timeoutSeconds, 1), MAX_TIMEOUT_SECONDS);
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(timeout))
            .header("User-Agent", USER_AGENT)
            .method(method, body);
        headers.forEach(request::header);
        try {
            HttpResponse<Void> response = client.send(request.build(), HttpResponse.BodyHandlers.discarding());
            return new Result(true, response.statusCode(), elapsedMs(start), null);
        } catch (HttpTimeoutException e) {
            return failed("TIMEOUT", start);
        } catch (ConnectException e) {
            return failed("CONNECTION_FAILED", start);
        } catch (SSLException e) {
            return failed("TLS_ERROR", start);
        } catch (IOException e) {
            return failed(e.getCause() instanceof SSLException ? "TLS_ERROR" : "IO_ERROR", start);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failed("INTERRUPTED", start);
        }
    }

    private static Result failed(String category, long start) {
        return new Result(false, null, elapsedMs(start), category);
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}

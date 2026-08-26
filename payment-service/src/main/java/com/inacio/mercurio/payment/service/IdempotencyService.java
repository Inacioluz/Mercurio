package com.inacio.mercurio.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Guarda, por chave de idempotencia, o pagamento criado e a impressao digital do
 * corpo que o originou.
 *
 * <p>O Redis aqui e um atalho: a garantia real e o indice unico em
 * {@code payments.idempotency_key}. Se o Redis estiver frio ou fora do ar, o
 * banco ainda impede a duplicata — o cache so evita a ida ao Postgres no caminho
 * quente dos retries, que e quando eles mais acontecem.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String KEY_PREFIX = "mercurio:idem:";
    private static final Duration TTL = Duration.ofHours(24);
    private static final String SEPARATOR = "|";

    private final StringRedisTemplate redisTemplate;

    public void remember(String idempotencyKey, UUID paymentId, String requestFingerprint) {
        try {
            redisTemplate.opsForValue().set(
                    redisKey(idempotencyKey),
                    paymentId + SEPARATOR + requestFingerprint,
                    TTL);
        } catch (RuntimeException ex) {
            // O indice unico do banco continua valendo; seguir sem cache e seguro.
            log.warn("Nao foi possivel gravar a chave de idempotencia no Redis: {}", ex.getMessage());
        }
    }

    public Optional<CachedResult> lookup(String idempotencyKey) {
        try {
            String stored = redisTemplate.opsForValue().get(redisKey(idempotencyKey));
            if (stored == null) {
                return Optional.empty();
            }
            int separator = stored.indexOf(SEPARATOR);
            if (separator < 0) {
                return Optional.empty();
            }
            return Optional.of(new CachedResult(
                    UUID.fromString(stored.substring(0, separator)),
                    stored.substring(separator + 1)));
        } catch (RuntimeException ex) {
            log.warn("Nao foi possivel ler a chave de idempotencia no Redis: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Impressao digital do corpo da requisicao. Permite distinguir um retry
     * legitimo (mesmo corpo) de um reuso indevido da chave (corpo diferente).
     */
    public String fingerprint(String payerAccount, String payeeAccount, String amount, String currency) {
        String canonical = String.join(SEPARATOR, payerAccount, payeeAccount, amount, currency);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponivel nesta JVM", ex);
        }
    }

    private String redisKey(String idempotencyKey) {
        return KEY_PREFIX + idempotencyKey;
    }

    public record CachedResult(UUID paymentId, String fingerprint) {
    }
}

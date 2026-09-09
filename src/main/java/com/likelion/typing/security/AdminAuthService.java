package com.likelion.typing.security;

import com.likelion.typing.admin.AdminDtos;
import com.likelion.typing.common.exception.AppException;
import com.likelion.typing.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

@Service
public class AdminAuthService {
    private final PasswordEncoder encoder;
    private final String passwordHash;
    private final byte[] tokenSecret;
    private final long ttlSeconds;

    public AdminAuthService(PasswordEncoder encoder,
                            @Value("${app.admin.password-hash}") String passwordHash,
                            @Value("${app.admin.token-secret}") String tokenSecret,
                            @Value("${app.admin.token-ttl-seconds}") long ttlSeconds) {
        if (tokenSecret.length() < 32) throw new IllegalStateException("ADMIN_TOKEN_SECRET must be at least 32 characters");
        this.encoder = encoder;
        this.passwordHash = passwordHash;
        this.tokenSecret = tokenSecret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
    }

    public AdminDtos.LoginResponse login(String password) {
        if (!encoder.matches(password, passwordHash)) throw new AppException(ErrorCode.ADMIN_UNAUTHORIZED);
        var expiresAt = Instant.now().plusSeconds(ttlSeconds);
        var payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(Long.toString(expiresAt.getEpochSecond()).getBytes(StandardCharsets.UTF_8));
        return new AdminDtos.LoginResponse(payload + "." + sign(payload), expiresAt);
    }

    public boolean valid(String token) {
        try {
            var parts = token.split("\\.", -1);
            if (parts.length != 2 || !MessageDigest.isEqual(
                sign(parts[0]).getBytes(StandardCharsets.US_ASCII), parts[1].getBytes(StandardCharsets.US_ASCII))) return false;
            var expires = Long.parseLong(new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8));
            return expires >= Instant.now().getEpochSecond();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String sign(String payload) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(tokenSecret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot create admin token", exception);
        }
    }
}

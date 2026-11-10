package com.todo.todo.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

/**
 * Access JWTs support either legacy HS256 or opt-in RS256.
 *
 * RS256 requires an X.509 DER public key only.
 * There is no HS256 fallback in RSA mode and no key material is logged.
 */
@Component
public class JwtTokenProvider {
    private static final String ISSUER = "todo-user-service";
    private static final String AUDIENCE = "todo-api";

    @Value("${jwt.accept-legacy-without-claims:true}")
    private boolean acceptLegacyWithoutClaims = true;

    private final SecretKey hmacKey;
    private final PublicKey rsaPublicKey;

    /** Legacy constructor retained for isolated unit tests. */
    public JwtTokenProvider(String jwtSecret) {
        this(jwtSecret, "");
    }

    @Autowired
    public JwtTokenProvider(
            @Value("${jwt.secret:}") String jwtSecret,
            @Value("${jwt.rsa.public-key-base64:}") String rsaPublicKeyBase64) {
        boolean rsa = rsaPublicKeyBase64 != null && !rsaPublicKeyBase64.isBlank();
        if (rsa) {
            try {
                KeyFactory factory = KeyFactory.getInstance("RSA");
                this.rsaPublicKey = factory.generatePublic(
                        new X509EncodedKeySpec(Base64.getDecoder().decode(rsaPublicKeyBase64)));
            } catch (Exception invalid) {
                throw new IllegalArgumentException("Invalid RS256 key configuration", invalid);
            }
            if (!(this.rsaPublicKey instanceof java.security.interfaces.RSAPublicKey rsaKey)
                    || rsaKey.getModulus().bitLength() < 2048) {
                throw new IllegalArgumentException("RS256 requires an RSA public key of at least 2048 bits");
            }
            this.hmacKey = null;
        } else {
            if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalArgumentException("Legacy HS256 requires a secret of at least 32 bytes");
            }
            this.hmacKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            this.rsaPublicKey = null;
        }
    }

    public UUID getUserIdFromToken(String token) {
        return UUID.fromString(parse(token).getPayload().getSubject());
    }

    private Jws<Claims> parse(String token) {
        Jws<Claims> parsed = rsaPublicKey != null
                ? Jwts.parser().verifyWith(rsaPublicKey).build().parseSignedClaims(token)
                : Jwts.parser().verifyWith(hmacKey).build().parseSignedClaims(token);
        // Pin the signature algorithm; never trust the algorithm named by the client.
        String algorithm = parsed.getHeader().getAlgorithm();
        boolean permitted = rsaPublicKey != null
                ? "RS256".equals(algorithm)
                : ("HS256".equals(algorithm) || "HS384".equals(algorithm)
                        || "HS512".equals(algorithm));
        if (!permitted) {
            throw new UnsupportedJwtException("Unexpected JWT signature algorithm");
        }
        return parsed;
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = parse(token).getPayload();
            if (claims.getExpiration() == null || claims.getSubject() == null) {
                return false;
            }
            UUID.fromString(claims.getSubject());
            String issuer = claims.getIssuer();
            var audience = claims.getAudience();
            if (issuer == null && (audience == null || audience.isEmpty())) {
                return acceptLegacyWithoutClaims;
            }
            return ISSUER.equals(issuer)
                    && audience != null
                    && audience.contains(AUDIENCE);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}

package ma.ensias.zerotrust.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

public class ServiceJwtProvider {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public ServiceJwtProvider(String privateKeyClasspath, String publicKeyClasspath) {
        try {
            this.publicKey = loadPublicKey(publicKeyClasspath);
            this.privateKey = (privateKeyClasspath != null && !privateKeyClasspath.isBlank())
                    ? loadPrivateKey(privateKeyClasspath)
                    : null;
        } catch (Exception e) {
            throw new IllegalStateException("Erreur lors du chargement des cles RSA JWT", e);
        }
    }

    public String generateToken(String subject, Map<String, Object> claims, long ttlSeconds) {
        if (privateKey == null) {
            throw new IllegalStateException("Cle privee non configuree sur ce service");
        }
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .issuer("zero-trust-share")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .claims(claims)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public Claims validateAndParse(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private PrivateKey loadPrivateKey(String classpathLocation) throws Exception {
        String pem = readResource(classpathLocation)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(pem);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    private PublicKey loadPublicKey(String classpathLocation) throws Exception {
        String pem = readResource(classpathLocation)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(pem);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
    }

    private String readResource(String classpathLocation) throws Exception {
        String cleanPath = classpathLocation.replace("classpath:", "");
        try (InputStream is = new ClassPathResource(cleanPath).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
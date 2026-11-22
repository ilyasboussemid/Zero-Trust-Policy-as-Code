package ma.ensias.zerotrust.metadata.controller;

import ma.ensias.zerotrust.metadata.model.ShareMetadata;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Microservice 2 (Port 8442) : Registre des metadonnees et cycle de vie 24h.
 * Protege par mTLS obligatoire (client-auth: need) + JWT RS256 + Policy-as-Code ABAC.
 */
@RestController
@RequestMapping("/api/metadata/shares")
public class MetadataController {

    private final Map<String, ShareMetadata> registry = new ConcurrentHashMap<>();

    @PostMapping
    public ResponseEntity<Map<String, Object>> registerShare(@RequestBody Map<String, Object> body) {
        String shareId = UUID.randomUUID().toString();
        String ownerId = String.valueOf(body.getOrDefault("ownerId", "unknown"));
        String userTier = String.valueOf(body.getOrDefault("userTier", "STANDARD"));
        String folderName = String.valueOf(body.getOrDefault("folderName", "archive.zip"));
        long fileSizeBytes = ((Number) body.getOrDefault("fileSizeBytes", 0L)).longValue();
        int ttlHours = ((Number) body.getOrDefault("ttlHours", 24)).intValue();

        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttlHours, ChronoUnit.HOURS);
        String objectKey = "uploads/" + shareId + "/" + folderName;

        ShareMetadata meta = new ShareMetadata();
        meta.setShareId(shareId);
        meta.setOwnerId(ownerId);
        meta.setUserTier(userTier);
        meta.setFolderName(folderName);
        meta.setObjectKey(objectKey);
        meta.setFileSizeBytes(fileSizeBytes);
        meta.setCreatedAt(now);
        meta.setExpiresAt(expiresAt);
        meta.setRevoked(false);

        registry.put(shareId, meta);

        return ResponseEntity.ok(Map.of(
                "shareId", shareId,
                "objectKey", objectKey,
                "expiresAt", expiresAt.toString(),
                "quotaApproved", true
        ));
    }

    @GetMapping("/{shareId}")
    public ResponseEntity<?> getShareMetadata(@PathVariable String shareId) {
        ShareMetadata meta = registry.get(shareId);
        if (meta == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Partage introuvable : " + shareId));
        }
        if (meta.isRevoked()) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .body(Map.of("error", "Ce lien de partage a ete revoque par son proprietaire"));
        }
        if (Instant.now().isAfter(meta.getExpiresAt())) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .body(Map.of("error", "Lien expire : la duree maximale de 24h est depassee"));
        }

        return ResponseEntity.ok(Map.of(
                "shareId", meta.getShareId(),
                "folderName", meta.getFolderName(),
                "objectKey", meta.getObjectKey(),
                "expiresAt", meta.getExpiresAt().toString(),
                "status", "ACTIVE"
        ));
    }

    @DeleteMapping("/{shareId}")
    public ResponseEntity<?> revokeShare(@PathVariable String shareId) {
        ShareMetadata meta = registry.get(shareId);
        if (meta == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Partage introuvable"));
        }
        meta.setRevoked(true);
        return ResponseEntity.ok(Map.of(
                "shareId", shareId,
                "status", "REVOKED"
        ));
    }
}

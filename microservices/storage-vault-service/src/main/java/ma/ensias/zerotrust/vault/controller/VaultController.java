package ma.ensias.zerotrust.vault.controller;

import ma.ensias.zerotrust.vault.service.S3PresignService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Microservice 3 (Port 8443) : Coffre S3 et generation des URLs pre-signees (24h).
 * Protege par mTLS obligatoire + JWT RS256 + Policy-as-Code PBAC (quotaApproved=true).
 */
@RestController
@RequestMapping("/api/vault")
public class VaultController {

    private final S3PresignService presignService;
    private final Map<String, byte[]> localObjectStore = new ConcurrentHashMap<>();

    public VaultController(S3PresignService presignService) {
        this.presignService = presignService;
    }

    @PostMapping("/presign")
    public ResponseEntity<Map<String, Object>> createPresignedUrls(@RequestBody Map<String, Object> request) {
        String shareId = String.valueOf(request.get("shareId"));
        String objectKey = String.valueOf(request.get("objectKey"));
        int ttlHours = ((Number) request.getOrDefault("ttlHours", 24)).intValue();

        return ResponseEntity.ok(presignService.generateUploadAndShareUrls(shareId, objectKey, ttlHours));
    }

    @PostMapping("/presign-download")
    public ResponseEntity<Map<String, Object>> createDownloadUrl(@RequestBody Map<String, Object> request) {
        String shareId = String.valueOf(request.get("shareId"));
        String objectKey = String.valueOf(request.get("objectKey"));
        return ResponseEntity.ok(Map.of(
                "downloadUrl", presignService.generateDownloadUrlOnly(shareId, objectKey)
        ));
    }

    @PutMapping("/local-s3/{shareId}")
    public ResponseEntity<?> uploadToLocalVault(@PathVariable String shareId,
                                                @RequestParam long expires,
                                                @RequestParam String sig,
                                                @RequestBody byte[] content) {
        if (!presignService.verifyLocalSignature(shareId, "PUT", expires, sig)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "URL pre-signee invalide ou expiree (>24h)"));
        }
        localObjectStore.put(shareId, content);
        return ResponseEntity.ok(Map.of(
                "status", "UPLOADED",
                "shareId", shareId,
                "bytesStored", content.length
        ));
    }

    @GetMapping("/local-s3/{shareId}")
    public ResponseEntity<?> downloadFromLocalVault(@PathVariable String shareId,
                                                    @RequestParam long expires,
                                                    @RequestParam String sig) {
        if (!presignService.verifyLocalSignature(shareId, "GET", expires, sig)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Lien de telechargement expire apres 24h ou signature invalide"));
        }
        byte[] data = localObjectStore.getOrDefault(shareId,
                "Contenu de demonstration Zero-Trust Share (valide 24h)".getBytes());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }
}

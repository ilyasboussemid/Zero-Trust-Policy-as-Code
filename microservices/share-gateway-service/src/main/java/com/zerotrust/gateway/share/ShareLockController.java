package com.zerotrust.gateway.share;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shares")
@CrossOrigin(origins = "*")
public class ShareLockController {

    public record CreateProtectedShareRequest(String folderName, long fileSize, String sharePassword, String uploadUrl, String downloadUrl) {}
    public record UnlockShareRequest(String password) {}

    private final ShareAccessStore accessStore;

    public ShareLockController(ShareAccessStore accessStore) {
        this.accessStore = accessStore;
    }

    @PostMapping("/protected")
    public ResponseEntity<?> createProtected(
            @RequestHeader(value = "X-User-Email", defaultValue = "ilyas@ensias.ma") String owner,
            @RequestBody CreateProtectedShareRequest req
    ) {
        if (req.sharePassword() == null || req.sharePassword().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "sharePassword is required"));
        }
        ShareAccessStore.ShareEntry entry = accessStore.create(
                owner,
                req.folderName(),
                req.fileSize(),
                req.sharePassword(),
                req.uploadUrl(),
                req.downloadUrl()
        );
        return ResponseEntity.ok(Map.of(
                "shareId", entry.shareId(),
                "fileName", entry.fileName(),
                "expiresAt", entry.expiresAt().toString(),
                "uploadUrl", entry.uploadUrl() != null ? entry.uploadUrl() : "",
                "downloadUrl", entry.downloadUrl() != null ? entry.downloadUrl() : ""
        ));
    }

    @PostMapping("/{shareId}/unlock")
    public ResponseEntity<?> unlock(@PathVariable String shareId, @RequestBody UnlockShareRequest req) {
        ShareAccessStore.ShareEntry entry = accessStore.unlock(shareId, req != null ? req.password() : "");
        if (entry == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid password or expired share link"));
        }
        return ResponseEntity.ok(Map.of(
                "shareId", entry.shareId(),
                "fileName", entry.fileName(),
                "fileSize", entry.fileSize(),
                "expiresAt", entry.expiresAt().toString(),
                "downloadUrl", entry.downloadUrl()
        ));
    }
}

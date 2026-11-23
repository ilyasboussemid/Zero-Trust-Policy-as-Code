package com.zerotrust.gateway.share;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class ShareAccessStore {

    public record ShareEntry(
            String shareId,
            String owner,
            String fileName,
            long fileSize,
            String passwordHash,
            String uploadUrl,
            String downloadUrl,
            Instant expiresAt
    ) {
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private final Map<String, ShareEntry> store = new ConcurrentHashMap<>();

    public ShareEntry create(String owner, String fileName, long fileSize, String rawPassword, String uploadUrl, String downloadUrl) {
        String id = UUID.randomUUID().toString();
        String hash = sha256(rawPassword == null ? "" : rawPassword);
        ShareEntry entry = new ShareEntry(
                id,
                owner,
                fileName,
                fileSize,
                hash,
                uploadUrl,
                downloadUrl,
                Instant.now().plusSeconds(86400)
        );
        store.put(id, entry);
        return entry;
    }

    public ShareEntry unlock(String shareId, String rawPassword) {
        ShareEntry entry = store.get(shareId);
        if (entry == null || entry.isExpired()) {
            return null;
        }
        String candidateHash = sha256(rawPassword == null ? "" : rawPassword);
        if (!MessageDigest.isEqual(entry.passwordHash().getBytes(StandardCharsets.UTF_8), candidateHash.getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        return entry;
    }

    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}

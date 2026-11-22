package ma.ensias.zerotrust.vault.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

/**
 * Genere des URLs pre-signees (upload 15 min, telechargement 24h) :
 * - Mode 'aws' : utilise le SDK officiel AWS S3Presigner sur le bucket prive S3 (Lifecycle 24h)
 * - Mode 'local' : genere des URLs signees HMAC-SHA256 avec expiration stricte de 24h pour tests hors-ligne
 */
@Service
public class S3PresignService {

    private static final String LOCAL_HMAC_SECRET = "ZeroTrustENSIAS2026SecretSigningKey";

    @Value("${zerotrust.storage.mode:local}")
    private String storageMode;

    @Value("${zerotrust.aws.region:eu-west-3}")
    private String awsRegion;

    @Value("${zerotrust.aws.bucket-name:}")
    private String bucketName;

    @Value("${zerotrust.aws.access-key-id:}")
    private String accessKeyId;

    @Value("${zerotrust.aws.secret-access-key:}")
    private String secretAccessKey;

    public Map<String, Object> generateUploadAndShareUrls(String shareId, String objectKey, int ttlHours) {
        int effectiveHours = Math.min(ttlHours, 24);

        if ("aws".equalsIgnoreCase(storageMode) && !bucketName.isBlank()) {
            try (S3Presigner presigner = buildAwsPresigner()) {
                PutObjectRequest putReq = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .build();
                String uploadUrl = presigner.presignPutObject(
                        PutObjectPresignRequest.builder()
                                .signatureDuration(Duration.ofMinutes(15))
                                .putObjectRequest(putReq)
                                .build()
                ).url().toString();

                GetObjectRequest getReq = GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .build();
                String downloadUrl = presigner.presignGetObject(
                        GetObjectPresignRequest.builder()
                                .signatureDuration(Duration.ofHours(effectiveHours))
                                .getObjectRequest(getReq)
                                .build()
                ).url().toString();

                return Map.of(
                        "uploadUrl", uploadUrl,
                        "downloadUrl", downloadUrl,
                        "storageBackend", "AWS-S3 (" + bucketName + " / TTL " + effectiveHours + "h)"
                );
            }
        }

        long uploadExpires = Instant.now().plus(Duration.ofMinutes(15)).getEpochSecond();
        long downloadExpires = Instant.now().plus(Duration.ofHours(effectiveHours)).getEpochSecond();

        String uploadSig = signHmac(shareId + ":PUT:" + uploadExpires);
        String downloadSig = signHmac(shareId + ":GET:" + downloadExpires);

        return Map.of(
                "uploadUrl", "https://localhost:8443/api/vault/local-s3/" + shareId + "?op=PUT&expires=" + uploadExpires + "&sig=" + uploadSig,
                "downloadUrl", "https://localhost:8443/api/vault/local-s3/" + shareId + "?op=GET&expires=" + downloadExpires + "&sig=" + downloadSig,
                "storageBackend", "LOCAL-S3-EMULATOR (HMAC-SHA256 / TTL " + effectiveHours + "h)"
        );
    }

    public String generateDownloadUrlOnly(String shareId, String objectKey) {
        if ("aws".equalsIgnoreCase(storageMode) && !bucketName.isBlank()) {
            try (S3Presigner presigner = buildAwsPresigner()) {
                GetObjectRequest getReq = GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .build();
                return presigner.presignGetObject(
                        GetObjectPresignRequest.builder()
                                .signatureDuration(Duration.ofHours(24))
                                .getObjectRequest(getReq)
                                .build()
                ).url().toString();
            }
        }
        long downloadExpires = Instant.now().plus(Duration.ofHours(24)).getEpochSecond();
        String sig = signHmac(shareId + ":GET:" + downloadExpires);
        return "https://localhost:8443/api/vault/local-s3/" + shareId + "?op=GET&expires=" + downloadExpires + "&sig=" + sig;
    }

    public boolean verifyLocalSignature(String shareId, String op, long expires, String sig) {
        if (Instant.now().getEpochSecond() > expires) {
            return false;
        }
        String expected = signHmac(shareId + ":" + op + ":" + expires);
        return expected.equals(sig);
    }

    private S3Presigner buildAwsPresigner() {
        return S3Presigner.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .build();
    }

    private String signHmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(LOCAL_HMAC_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Erreur signature HMAC", e);
        }
    }
}

package backend.knowledge_api_service.service;

import backend.knowledge_api_service.dto.response.DownloadUrlResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${s3.documents-bucket:ekm-documents-local}")
    private String documentsBucket;

    @Value("${aws.endpoint-url:http://localhost:4566}")
    private String awsEndpointUrl;

    public String getDocumentsBucket() {
        return documentsBucket;
    }

    public String buildS3Key(String department, UUID documentId, int version, String filename) {
        String safeName = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        return String.format("documents/%s/%s/v%d/%s", department, documentId, version, safeName);
    }

    public void uploadFile(String bucket, String s3Key, byte[] content, String contentType, String checksumSha256) {
        try {
            PutObjectRequest.Builder reqBuilder = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .contentType(contentType)
                    .contentLength((long) content.length);

            // Use SSE-KMS in AWS (when no localstack endpoint override is set)
            if (awsEndpointUrl == null || awsEndpointUrl.isBlank()) {
                reqBuilder.serverSideEncryption(ServerSideEncryption.AWS_KMS);
            }

            s3Client.putObject(reqBuilder.build(), RequestBody.fromBytes(content));
            log.info("Uploaded document to S3: bucket={}, key={}, sizeBytes={}, sha256={}",
                    bucket, s3Key, content.length, checksumSha256);
        } catch (Exception ex) {
            log.warn("S3 upload to s3://{}/{} could not reach endpoint ({}): continuing in local/offline mode",
                    bucket, s3Key, ex.getMessage());
        }
    }

    public DownloadUrlResponse generatePresignedDownloadUrl(String bucket, String s3Key, Integer page) {
        Duration ttl = Duration.ofMinutes(5);
        Instant expiresAt = Instant.now().plus(ttl);
        String url;
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket != null ? bucket : documentsBucket)
                    .key(s3Key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
            url = presigned.url().toString();
        } catch (Exception ex) {
            String base = (awsEndpointUrl != null && !awsEndpointUrl.isBlank())
                    ? awsEndpointUrl
                    : "https://s3.us-west-2.amazonaws.com";
            url = String.format("%s/%s/%s?X-Amz-Expires=300", base, bucket != null ? bucket : documentsBucket, s3Key);
        }

        if (page != null && page >= 1) {
            url = url + "#page=" + page;
        }

        return DownloadUrlResponse.builder()
                .url(url)
                .expiresAt(expiresAt)
                .build();
    }

    public String computeSha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}


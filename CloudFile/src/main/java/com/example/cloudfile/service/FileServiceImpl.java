
package com.example.cloudfile.service;

import com.example.cloudfile.dto.FileResponse;
import com.example.cloudfile.dto.FileUploadedMessage;
import com.example.cloudfile.entity.FileMetaData;
import com.example.cloudfile.exception.UserNotFound;
import com.example.cloudfile.repository.FileMetaRepo;
import com.example.cloudfile.repository.UserRepo;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final S3Client s3Client;
    private final FileMetaRepo fileMetaRepo;
    private final UserRepo userRepo;
    private final SqsProducer sqsProducer;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final Duration LOCK_TTL =
            Duration.ofMinutes(5);

    private static final Duration RESULT_TTL =
            Duration.ofHours(24);

    private static final Duration DUPLICATE_TTL =
            Duration.ofHours(1);

    @Value("${aws.s3.bucket}")
    private String bucketName;


    @Override
    @Transactional
    public FileResponse uploadFile(
            String idempotencyKey,
            String email,
            MultipartFile file) {

        // 1. Validate request.

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key is required"
            );
        }

        if (idempotencyKey.length() > 128) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key is too long"
            );
        }

        if (email == null || email.isBlank()
                || !userRepo.existsByEmail(email.trim())) {
            throw new UserNotFound("User Not Found");
        }

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "File cannot be empty"
            );
        }

        String normalizedEmail = email.trim().toLowerCase();

        // 2. Generate request fingerprint.

        String fingerprint = generateFingerprint(
                normalizedEmail,
                file
        );

        String baseKey = "cloudfile:upload:" + idempotencyKey;

        String lockKey = baseKey + ":lock";
        String resultKey = baseKey + ":result";

        // 3. Check previously completed request.

        String previousResult =
                redisTemplate.opsForValue().get(resultKey);

        if (previousResult != null) {
            return replayResult(previousResult, fingerprint);
        }

        // 4. Reserve request idempotency key.

        String ownerToken = UUID.randomUUID().toString();

        Boolean reserved = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, ownerToken, LOCK_TTL);

        if (!Boolean.TRUE.equals(reserved)) {

            previousResult =
                    redisTemplate.opsForValue().get(resultKey);

            if (previousResult != null) {
                return replayResult(previousResult, fingerprint);
            }

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Upload with this idempotency key is already processing"
            );
        }

        String duplicateKey = null;
        String duplicateOwnerToken = null;
        String objectKey = null;

        boolean uploadedToS3 = false;

        try {

            // 5. Generate user and file hashes.

            String userHash = sha256Hex(normalizedEmail);

            String fileHash = generateFileHash(file);

            duplicateKey =
                    "cloudfile:upload:duplicate:"
                            + userHash + ":" + fileHash;

            duplicateOwnerToken = UUID.randomUUID().toString();

            // 6. Atomically reserve duplicate-file key.

            Boolean duplicateReserved =
                    redisTemplate.opsForValue().setIfAbsent(
                            duplicateKey,
                            duplicateOwnerToken,
                            DUPLICATE_TTL
                    );

            if (!Boolean.TRUE.equals(duplicateReserved)) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "This file has already been uploaded within the last hour"
                );
            }

            // 7. Upload file to S3.

            String originalName = Paths.get(
                    file.getOriginalFilename() == null
                            ? "file"
                            : file.getOriginalFilename()
            ).getFileName().toString();

            objectKey =
                    "uploads/" + UUID.randomUUID() + "-" + originalName;

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(
                    request,
                    RequestBody.fromInputStream(
                            file.getInputStream(),
                            file.getSize()
                    )
            );

            uploadedToS3 = true;

            // 8. Save metadata in MySQL.

            FileMetaData metadata = FileMetaData.builder()
                    .originalFileName(originalName)
                    .objectKey(objectKey)
                    .ContentType(file.getContentType())
                    .FileSize(file.getSize())
                    .uploadedAt(LocalDateTime.now())
                    .build();

            FileMetaData saved = fileMetaRepo.save(metadata);

            FileResponse response = new FileResponse(
                    saved.getId(),
                    saved.getOriginalFileName(),
                    saved.getObjectKey(),
                    saved.getContentType(),
                    saved.getFileSize(),
                    saved.getUploadedAt()
            );

            // 9. Publish event to SQS.
            // Transactional outbox will replace this direct call later.

            FileUploadedMessage message =
                    new FileUploadedMessage(
                            UUID.randomUUID().toString(),
                            "FILE_UPLOADED",
                            bucketName,
                            objectKey,
                            email
                    );

            sqsProducer.sendMessage(message);

            // 10. Serialize response for idempotency replay.

            String serializedResult =
                    objectMapper.writeValueAsString(
                            new IdempotencyResult(
                                    fingerprint,
                                    response
                            )
                    );

            // 11. Complete Redis reservations after DB commit.

            registerCompletion(
                    lockKey,
                    resultKey,
                    ownerToken,
                    serializedResult,
                    duplicateKey,
                    duplicateOwnerToken
            );

            return response;

        } catch (Exception exception) {

            // 12. Clean up S3 object if processing failed.

            if (uploadedToS3 && objectKey != null) {

                try {
                    s3Client.deleteObject(
                            DeleteObjectRequest.builder()
                                    .bucket(bucketName)
                                    .key(objectKey)
                                    .build()
                    );

                } catch (RuntimeException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
            }

            // 13. Release reservations owned by this request.

            if (duplicateKey != null && duplicateOwnerToken != null) {
                releaseLock(duplicateKey, duplicateOwnerToken);
            }

            releaseLock(lockKey, ownerToken);

            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }

            throw new RuntimeException(
                    "File upload failed",
                    exception
            );
        }
    }


    // Generate request fingerprint using email and file content.

    private String generateFingerprint(
            String email,
            MultipartFile file) {

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            digest.update(email.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(file.getBytes());

            return HexFormat.of().formatHex(digest.digest());

        } catch (Exception exception) {
            throw new RuntimeException(
                    "Unable to generate request fingerprint",
                    exception
            );
        }
    }


    // Generate SHA-256 hash of file contents.

    private String generateFileHash(MultipartFile file) {

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            return HexFormat.of()
                    .formatHex(digest.digest(file.getBytes()));

        } catch (Exception exception) {
            throw new RuntimeException(
                    "Unable to generate file hash",
                    exception
            );
        }
    }


    // Generate SHA-256 hash of a string.

    private String sha256Hex(String value) {

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8))
            );

        } catch (Exception exception) {
            throw new RuntimeException(
                    "Unable to generate SHA-256 hash",
                    exception
            );
        }
    }


    // Return previous response for an already completed operation.

    private FileResponse replayResult(
            String storedResult,
            String fingerprint) {

        try {
            IdempotencyResult result =
                    objectMapper.readValue(
                            storedResult,
                            IdempotencyResult.class
                    );

            if (!result.fingerprint().equals(fingerprint)) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Idempotency key reused with different request data"
                );
            }

            return result.response();

        } catch (ResponseStatusException exception) {
            throw exception;

        } catch (Exception exception) {
            throw new RuntimeException(
                    "Unable to retrieve previous upload response",
                    exception
            );
        }
    }


    // Register Redis completion after MySQL transaction.

    private void registerCompletion(
            String lockKey,
            String resultKey,
            String ownerToken,
            String serializedResult,
            String duplicateKey,
            String duplicateOwnerToken) {

        Runnable completion = () -> {

            try {

                // Save original response for 24 hours.

                redisTemplate.opsForValue().set(
                        resultKey,
                        serializedResult,
                        RESULT_TTL
                );

                // Keep duplicate-file reservation for one hour
                // after successful database commit.

                extendDuplicateReservation(
                        duplicateKey,
                        duplicateOwnerToken
                );

            } finally {

                releaseLock(lockKey, ownerToken);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {

            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {

                        @Override
                        public void afterCommit() {
                            completion.run();
                        }

                        @Override
                        public void afterCompletion(int status) {

                            if (status != STATUS_COMMITTED) {

                                releaseLock(lockKey, ownerToken);

                                releaseLock(
                                        duplicateKey,
                                        duplicateOwnerToken
                                );
                            }
                        }
                    }
            );

        } else {
            completion.run();
        }
    }


    // Extend duplicate reservation only if this request owns it.

    private void extendDuplicateReservation(
            String duplicateKey,
            String duplicateOwnerToken) {

        String script = """
                if redis.call('GET', KEYS[1]) == ARGV[1] then
                    return redis.call('EXPIRE', KEYS[1], ARGV[2])
                end
                return 0
                """;

        redisTemplate.execute(
                new org.springframework.data.redis.core.script
                        .DefaultRedisScript<>(script, Long.class),
                List.of(duplicateKey),
                duplicateOwnerToken,
                String.valueOf(DUPLICATE_TTL.toSeconds())
        );
    }


    // Release Redis lock only if the current request owns it.

    private void releaseLock(
            String lockKey,
            String ownerToken) {

        if (lockKey == null || ownerToken == null) {
            return;
        }

        String script = """
                if redis.call('GET', KEYS[1]) == ARGV[1] then
                    return redis.call('DEL', KEYS[1])
                end
                return 0
                """;

        redisTemplate.execute(
                new org.springframework.data.redis.core.script
                        .DefaultRedisScript<>(script, Long.class),
                List.of(lockKey),
                ownerToken
        );
    }


    // Cached idempotency result.

    private record IdempotencyResult(
            String fingerprint,
            FileResponse response
    ) {}

}
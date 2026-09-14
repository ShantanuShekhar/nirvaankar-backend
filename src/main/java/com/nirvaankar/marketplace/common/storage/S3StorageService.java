package com.nirvaankar.marketplace.common.storage;

import com.nirvaankar.marketplace.common.config.S3Properties;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageService {

    private final ObjectProvider<S3Client> s3Client;
    private final S3Properties properties;

    public void put(String key, byte[] bytes, String contentType) {
        S3Client client = requireClient();
        try {
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(key)
                            .contentType(contentType)
                            .contentLength((long) bytes.length)
                            .cacheControl("private, max-age=31536000")
                            .build(),
                    RequestBody.fromBytes(bytes));
        } catch (S3Exception e) {
            log.error("S3 put failed for key {}", key, e);
            throw new ApiException(ErrorCode.UPLOAD_FAILED);
        }
    }

    public boolean exists(String key) {
        S3Client client = requireClient();
        try {
            client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            log.error("S3 head failed for key {}", key, e);
            throw new ApiException(ErrorCode.UPLOAD_FAILED);
        }
    }

    public S3ObjectStream get(String key) {
        S3Client client = requireClient();
        try {
            ResponseInputStream<GetObjectResponse> stream = client.getObject(GetObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build());
            GetObjectResponse meta = stream.response();
            String contentType = Optional.ofNullable(meta.contentType()).orElse("application/octet-stream");
            long length = meta.contentLength() == null ? -1L : meta.contentLength();
            return new S3ObjectStream(contentType, length, stream);
        } catch (NoSuchKeyException e) {
            throw ApiException.notFound("Image");
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw ApiException.notFound("Image");
            }
            log.error("S3 get failed for key {}", key, e);
            throw new ApiException(ErrorCode.UPLOAD_FAILED, "Product image could not be loaded");
        }
    }

    public void deleteQuietly(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        S3Client client = s3Client.getIfAvailable();
        if (client == null) {
            return;
        }
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build());
        } catch (S3Exception e) {
            log.warn("S3 delete skipped for key {}: {}", key, e.getMessage());
        }
    }

    private S3Client requireClient() {
        S3Client client = s3Client.getIfAvailable();
        if (client == null || !properties.configured()) {
            throw new ApiException(ErrorCode.STORAGE_UNAVAILABLE);
        }
        return client;
    }

    public record S3ObjectStream(String contentType, long contentLength, InputStream inputStream) {
    }
}

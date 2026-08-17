package com.netflix.streamingservice.service;

import com.netflix.streamingservice.dto.StreamingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class StreamingService {
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.presigned-url-expiry")
    private long presignedUrlExpiry;

    // Redis key for the cache streaming the presigned URLs
    private final static String STREAMING_URL_PREFIX = "streaming:url:";

    /**
     * Get streaming URL for a movie
     *
     * FLOW:
     * 1. Check redis cache for existing presignedUrl
     * 2. If the cached - return immediately
     * 3. If not cached - generate the new presigned url from S3
     * 4. Cache the URL in Redis
     * 5. Return streaming URL
     *
     * Why Presigned URL?
     * - S3 bucket is private locker room - videos are not publicly accessible
     * - Presigned URL gives temporary access (X minutes)
     * - Prevents unauthorized video downloads
     */
    public StreamingResponse getStreamingUrl(String movieId, String playlistKey) {
        log.info("Getting streaming URL for movie: {}", movieId);
        String cacheKey = STREAMING_URL_PREFIX + movieId;

        String cachedUrl = redisTemplate.opsForValue().get(cacheKey);
        if (cachedUrl != null) {
            log.info("Returning streaming URL for movie: {}", movieId);
            return new StreamingResponse(
                    movieId,
                    cachedUrl,
                    "1080, 720, 480, 360",
                    presignedUrlExpiry
            );
        }

        log.info("Returning new presigned URL for movie: {}", movieId);
        String presignedUrl = generatePresignedUrl(playlistKey);

        // We will be caching the url 5 mins less due to the edge case handling
        // For Example: if the actual expiry is 60 mins then we will do it for 55 mins
        redisTemplate.opsForValue().set(
                cacheKey,
                presignedUrl,
                55,
                TimeUnit.MINUTES
        );

        log.info("Streaming URL generated and cached for movie: {}", movieId);
        return new StreamingResponse(
                movieId,
                presignedUrl,
                "1080, 720, 480, 360",
                presignedUrlExpiry
        );
    }

    /**
     * Key method that makes each url secure
     * @param movieId
     * @param playlistPath
     * @return
     */
    public String getSignedPlaylist(String movieId, String playlistPath) {
        String basePath = playlistPath.substring(0,
                playlistPath.lastIndexOf('/') + 1);

        String m2u8Content = readFromS3(playlistPath);
        String signedContent = rewriteM3u8SignedUrls(
                m2u8Content,
                basePath
        );

        return signedContent;
    }

    /**
     * Helper function for generating the presigned url with the playlist key
     *
     * @param key
     * @return
     */
    private String generatePresignedUrl(String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignedUrlExpiry))
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    /**
     * Invalidate cache streaming URL
     * Called when video is re-encoded or updated
     */
    public void invalidateCache(String movieId){
        String cacheKey = STREAMING_URL_PREFIX + movieId;

        redisTemplate.delete(cacheKey);
        log.info("Streaming URL cache invalidated for movie: {}", movieId);
    }

    /**
     *
     * @param m3u8Content
     * @param basePath
     * @return
     */
    private String rewriteM3u8SignedUrls(String m3u8Content, String basePath) {
        StringBuilder rewritten = new StringBuilder();
        for(String line : m3u8Content.split("\n")) {
            String trimmed = line.trim();
            if(trimmed.isEmpty() || trimmed.startsWith("#")){
                rewritten.append(line).append("\n");
                continue;
            }

            String fullKey = basePath + trimmed;
            String signedUrl  = generatePresignedUrl(fullKey);
            rewritten.append(signedUrl).append("\n");
        }
        return rewritten.toString();
    }

    private String readFromS3(String s3Key){
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

        ResponseInputStream<GetObjectResponse> responseInputStream = s3Client.getObject(getObjectRequest);
        return new BufferedReader(new InputStreamReader(responseInputStream)).lines().collect(Collectors.joining("\n"));
    }



}

package com.netflix.videoservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event Published to Kafka when a video is uploaded to S3
 * Encoding Service consumes this to start FFmpeg processing
 *
 * TOPIC: video.uploaded
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoUploadedEvent {
    private String movieId;
    private String videoKey;
    private String bucketName;
    private String originalFilename;
    private Long fileSizeBytes;
}

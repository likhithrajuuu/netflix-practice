package com.netflix.encodingservice.service;

import com.netflix.encodingservice.event.VideoUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class VideoEventConsumer {
    private final EncodingService encodingService;

    /**
     *  Listens to video.uploaded kafka topic.
     *  Triggered when video service uploads a raw video to S3
     *
     *  FLOW:
     *  Video Service -> S3 -> Kafka publish event (video.uploaded) ->
     *                      -> This consumer
     *                      -> Encoding Service -> FFmpeg -> S3
     *                      -> Kafka(video.encoded)
     */
    @KafkaListener(
            topics = "video.uploaded",
            groupId = "encoding-service-group"
    )
    public void consumeVideoUploadedEvent(VideoUploadedEvent event) {
        log.info("Received video uploaded event for movie: {} file: {}", event.getMovieId(), event.getOriginalFilename());
        try{
            encodingService.encodeVideo(event);
        } catch (Exception e){
            log.error("Failed to process encoding for movie: {} {}", event.getMovieId(), e.getMessage());
        }
    }
}

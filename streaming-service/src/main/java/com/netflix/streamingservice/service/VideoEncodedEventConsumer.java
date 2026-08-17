package com.netflix.streamingservice.service;

import com.netflix.streamingservice.event.VideoEncodedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class VideoEncodedEventConsumer {
    private final RedisTemplate<String, String> redisTemplate;
    private static final String MASTER_PLAYLIST_KEY_PREFIX = "streaming:playlist:";

    /**
     * Listens to video.encoded Kafka Topic and store the playlist
     * key in the Redis when the encoding is complete
     * This provisions the streaming-service to quickly find the playlist key by movieId
     */
    @KafkaListener(
            topics = "video.encoded",
            groupId = "streaming-service-group"
    )
    public void consumeVideoEncodedEvent(VideoEncodedEvent event){
        log.info("Consumed video encoded event for movie: {} success: {}", event.getMovieId(), event.isSuccess());
        if(event.isSuccess()){
            String cacheKey = MASTER_PLAYLIST_KEY_PREFIX + event.getMovieId();
            redisTemplate.opsForValue().set(cacheKey, event.getMasterPlaylistKey());
            log.info("Master playlist key stored in Redis for movie: {}", event.getMovieId());
        }
        else{
            log.error("Encoding failed for movie: {} - {}", event.getMovieId(), event.isSuccess());
        }
    }
}

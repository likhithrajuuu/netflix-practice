package com.netflix.streamingservice.controller;

import com.netflix.streamingservice.dto.StreamingResponse;
import com.netflix.streamingservice.service.StreamingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stream")
@Slf4j
@RequiredArgsConstructor
public class StreamingController {
    private final StreamingService streamingService;
    private final RedisTemplate<String, String> redisTemplate;
    private static final String MASTER_PLAYLIST_KEY_PREFIX = "Streaming:playlist";

    /**
     * Get streaming URL for a movie
     * Returns presigned HLS master playlist URL
     *
     * GET /api/v1/stream/{movieId}
     */
    public ResponseEntity<StreamingResponse> getStreamingUrl(
            @PathVariable String movieId
    ){
        log.info("Streaming request for movie: {}", movieId);
        //Fetching the master playlist key from Redis
        String playlistKey = redisTemplate.opsForValue()
                .get(MASTER_PLAYLIST_KEY_PREFIX + movieId);

        if(playlistKey == null){
            return ResponseEntity.notFound().build();
        }

        StreamingResponse response = streamingService.getStreamingUrl(movieId, playlistKey);

        return ResponseEntity.ok(response);

    }

    /**
     * Server signed .m3u8 playlist content
     * Called by HLS Player for serving each quality to the end users
     * @param movieId
     * @param path
     * @return
     */
    @GetMapping("/{movieId}/playlist")
    public ResponseEntity<String> getSignedPlaylist(
            @PathVariable String movieId,
            @RequestParam String path
    ) {
        String signedPlaylist = streamingService.getSignedPlaylist(movieId, path);
        return ResponseEntity.ok()
                .header("Content-Type", "application/x-mpegURL")
                .body(signedPlaylist);
    }
}

package com.netflix.videoservice.config;


import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration

public class KafkaConfig {
    /**
     * Published when video is uploaded to S3
     * Encoding Service consumes this
     * @return
     */
    @Bean
    public NewTopic videoUploadedTopic(){
        return TopicBuilder.name("video.uploaded")
                .partitions(3)
                .replicas(1)
                .build();
    }


    /**
     * Published when encoding is complete
     *
     * @return
     */
    @Bean
    public NewTopic videoEncodedTopic(){
        return TopicBuilder.name("video.encoded")
                .partitions(3)
                .replicas(1)
                .build();
    }
}

package com.netflix.encodingservice.service;

import com.netflix.encodingservice.event.VideoEncodedEvent;
import com.netflix.encodingservice.event.VideoUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class EncodingService {
    private final S3Client s3Client;
    private final KafkaTemplate<String, VideoEncodedEvent> kafkaTemplate;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${ffmpeg.path")
    private String ffmepgPath;

    @Value("${encoding.base-path}")
    private String basePath;

    private static final String VIDEO_ENCODED_TOPIC = "video.encoded";


    /**
     * Video Qualities to encode
     * Format: resolution, bitrate and height
     */
    private static final List<int[]> VIDEO_QUALITIES = Arrays.asList(
            new int[]{1920, 5000, 1080}, // 1080p - 5000kbps - Bitrate
            new int[]{1280, 2800, 720}, // 720p - 2800 kbps - bitrate
            new int[]{854, 1200, 480}, // 480p - 1200kbps - bitrate
            new int[]{640, 800, 360} // 360p - 800kbps - bitrate
    );


    /**
     * Main encoding pipeline
     * Steps:
     * 1. Download the raw video that is uploaded to the S3
     * 2. Encode to multiple qualities using FFmpeg processing
     * 3. Generate HLS Playlist(.m3u8) for each quality
     * 4. Create master playlist
     * 5. Upload all the encoded files back to the S3
     * 6. Publish the video encoded event to Kafka
     * @param event
     */
    public void encodeVideo(VideoUploadedEvent event) throws IOException {
        log.info("Encoding video event {}", event.getMovieId());

        //Create a unique path for the movie
        String jobPath = basePath + "/" + event.getMovieId();

        try{
            // Create temp directories
            Files.createDirectories(Paths.get(jobPath));
            Files.createDirectories(Paths.get(jobPath + "/encoded"));

            // Step 1: Download the raw video from S3
            String localVideoPath = jobPath + "/raw_video.mp4";
            downloadFromS3(event.getVideoKey(), localVideoPath);
            log.info("Raw Video Downloaded to: {}", localVideoPath);

            // Encode to multiple qualities using FFmpeg processing
            for(int[] quality : VIDEO_QUALITIES){
                int width = quality[0];
                int height = quality[2];
                int bitRate = quality[1];

                String qualityDir = jobPath + "/encoded" + height + "p";
                Files.createDirectories(Paths.get(qualityDir));

                encodeToHLS(localVideoPath, qualityDir, width, height, bitRate);
                log.info("Encoded Video Quality Downloaded to: {}", qualityDir);
            }

            // Step 4 : Generate the master HLS Playlist
            String masterPlaylistPath = jobPath + "/encoded/master.m3u8";
            generateMasterPlaylist(masterPlaylistPath);
            log.info("Master Playlist generated to: {}", masterPlaylistPath);

            // Step 5 : Upload all the encoded files back to S3
            String encodedPrefix = "encoded/" + event.getMovieId() + "/";
            uploadEncodedFilesToS3(jobPath, encodedPrefix);
            log.info("All encoded files uploaded to S3");

            //Step 6: Publish the video encoded event to Kafka
            String masterPlaylistKey = encodedPrefix + "master.m3u8";
            String hlsUrl = "https://" + bucketName + ".s3.amazonaws.com/" + masterPlaylistKey;

            VideoEncodedEvent videoEncodedEvent = new VideoEncodedEvent(
                    event.getMovieId(),
                    hlsUrl,
                    masterPlaylistKey,
                    true,
                    null
            );

            kafkaTemplate.send(VIDEO_ENCODED_TOPIC, event.getMovieId(), videoEncodedEvent);
            log.info("VideoEncodedEvent published for movie: {}", event.getMovieId());


        }catch (Exception e){
            log.error("Encoding failed for movie: {}", event.getMovieId(), e.getMessage());

            //Publish Failure event to Kafka
            VideoEncodedEvent failure = new VideoEncodedEvent(
                    event.getMovieId(),
                    null,
                    null,
                    false,
                    e.getMessage()
            );
            kafkaTemplate.send(VIDEO_ENCODED_TOPIC, event.getMovieId(), failure);
        }

        finally {
            //cleanup the temporary files and directories
            cleanupTempFiles(jobPath);
        }
    }


    /**
     * Download file from S3 to local path
     * @param s3Key
     * @param localPath
     * @throws IOException
     */
    private void downloadFromS3(String s3Key, String localPath) throws IOException{
//        Path tempFile = Files.createTempFile("video-", ".mp4");
        log.info("Downloading video from s3 to {}", localPath);
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

        s3Client.getObject(getObjectRequest, Paths.get(localPath));
//        return tempFile;
    }

    /**
     *  Encode video to HLS using Ffmpeg
     *
     *  FFmpeg command is created
     *  - multiple .ts segment files (10 seconds each)
     *  - .m3u8 playlist file for this quality
     * @param inputPath
     * @param outputDir
     * @param width
     * @param height
     * @param bitRate
     * @throws IOException
     */
    private void encodeToHLS(String inputPath, String outputDir, int width, int height, int bitRate) throws IOException, InterruptedException{
        String playlistPath = outputDir + "/playlist.m3u8";
        String segmentPattern = outputDir + "/segment_%03d.ts";


        //FFmpeg Command for the HLS encoding
        List<String> command = Arrays.asList(
                "ffmpeg",
                "-i", inputPath,    //Input File
                "-vf", "scale=" + width + ":" + height, //Scale to resolution
                "-c:v", "libx264", // video codec
                "-b:v", bitRate + "k", // bit-RATE of the video
                "-c:a", "aac", // audio codec
                "-b:a", "128k", //Audio bitrate
                "-hls_time", "10", //10 second segments
                "-hls_list_size", "0",  //Keep all the segments
                "-hls_segment_filename", segmentPattern, // segment naming
                "-f", "hls",    // output format is HLS
                playlistPath    // output playlist Path
        );

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Failed to encode video. ExitCode: " + exitCode);
        }
    }

    /**
     * Generate master HLS Playlist that references all quality playlists
     * This is the file video player download first
     * @param masterPlaylistPath
     * @throws IOException
     */
    private void generateMasterPlaylist(String masterPlaylistPath) throws IOException {
        StringBuilder master = new StringBuilder();
        master.append("#EXTM3U\n");
        master.append("#EXT-X-VERSION:3\n\n");

        //Add each quality to master playlist
        int[][] qualities = {
                {1920, 5000, 1080},
                {1280, 2800, 720},
                {854, 1200, 480},
                {640, 800, 360},
        };

        for(int [] quality : qualities){
            int width = quality[0];
            int height = quality[2];
            int bitRate = quality[1];


            master.append("#EXT-X-STREAM_INF:BANDWIDTH=").append(bitRate*1000).append(", RESOLUTION=").append(width).append("x").append(height).append(", CODECS=\"avc1.42e01e,mp4a.40.2\"\n");
            master.append(height).append("p/playlist.m3u8\n\n");
        }

        Files.writeString(Paths.get(masterPlaylistPath), master.toString());


    }

    private void uploadEncodedFilesToS3(String localDir, String s3Prefix){
        File directory = new File(localDir);
        uploadDirectoryToS3(directory, localDir, s3Prefix);
    }


    /**
     * Upload all encoded files from local directory back to s3
     * @param directory
     * @param localDir
     * @param s3Prefix
     */
    private void uploadDirectoryToS3(File directory, String localDir, String s3Prefix){
        for(File file : directory.listFiles()){
            if(file.isDirectory()){
                uploadDirectoryToS3(file, localDir, s3Prefix);
            }
            else{
                String relativePath = file.getAbsolutePath()
                        .substring(localDir.length() + 1)
                        .replace("\\", "/");
                String s3Key = s3Prefix + relativePath;
                String contentType = file.getName().endsWith(".m3u8")
                        ? "application/x-mpegURL"
                        : "video/MP2T";

                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3Key)
                        .contentType(contentType)
                        .build();

                s3Client.putObject(putObjectRequest, RequestBody.fromFile(file));
                log.debug("uploaded {} to S3", s3Key);
            }
        }
    }

    /**
     *
     * @param jobPath
     */
    private void cleanupTempFiles(String jobPath) {
        try{
            Path dirPath = Paths.get(jobPath);
            if(Files.exists(dirPath)){
                Files.walk(dirPath)
                        .sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                log.info("Cleaned up temp files for job: {}", jobPath);
            }
        } catch(IOException e){
            log.warn("Failed to clean up temp files for job: {}", jobPath, e.getMessage());
        }
    }


}

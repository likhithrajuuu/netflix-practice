package com.netflix.contentservice.service;

import com.netflix.contentservice.dto.MovieRequest;
import com.netflix.contentservice.dto.MovieResponse;
import com.netflix.contentservice.model.Genre;
import com.netflix.contentservice.model.Movie;
import com.netflix.contentservice.model.VideoStatus;
import com.netflix.contentservice.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ContentService {

    private final ContentRepository contentRepository;

    /**
     * Add a new movie to the catalog
     * Video is not uploaded yet at this stage
     * @param movieRequest
     * @return
     */
    public MovieResponse addMovie(MovieRequest movieRequest) {
        log.info("Adding new movie: {}", movieRequest.getTitle());
        Movie movie = Movie.builder()
                .title(movieRequest.getTitle())
                .description(movieRequest.getDescription())
                .genre(movieRequest.getGenre())
                .rating(movieRequest.getRating())
                .releaseYear(movieRequest.getReleaseYear())
                .director(movieRequest.getDirector())
                .cast(movieRequest.getCast())
                .thumbnailUrl(movieRequest.getThumbnailUrl())
                .durationInMinutes(movieRequest.getDurationInMinutes())
                .videoStatus(VideoStatus.PENDING)
                .build();

        Movie savedMovie = contentRepository.save(movie);
        log.info("Movie added with ID: {}", savedMovie.getId());

        return mapToResponse(savedMovie);

    }

    /**
     * Get all the movies in the catalog
     *
     */
    public List<MovieResponse> getAllMovies() {
        return contentRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }


    /**
     * Get movie by ID
     *
     */
    public MovieResponse getMoviesById(String id){
        Movie movie = contentRepository.findById(id).orElseThrow(() -> new RuntimeException("Movie not found: " + id));

        return mapToResponse(movie);
    }

    /**
     * Get Movies by Genre
     *
     */
    public List<MovieResponse> getMoviesByGenre(Genre genre){
        return contentRepository.findByGenre(genre)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Search Movies by the title
     */
    public List<MovieResponse> searchMovies(String title){
        return contentRepository.findByTitleContainingIgnoreCase(title)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Updating the Video Key upon receiving it from the video-service
     * @param movieId
     * @param videoKey
     */
    public void updateVideoKey(String movieId, String videoKey){
        log.info("Updating video key for movie: {}", movieId);
        Movie movie = contentRepository.findById(movieId).orElseThrow(() -> new RuntimeException("Movie not found: " + movieId));
        movie.setVideoKey(videoKey);
        movie.setVideoStatus(VideoStatus.UPLOADED);
        contentRepository.save(movie);
    }

    /**
     * Updating the HLS URL upon encoding completion from the encoding-service
     * @param movieId
     * @param hlsUrl
     */
    public void updateHlsUrl(String movieId, String hlsUrl){
        log.info("Updating HLS url for movie: {}", movieId);
        Movie movie = contentRepository.findById(movieId).orElseThrow(() -> new RuntimeException("Movie not found: " + movieId));
        movie.setHlsUrl(hlsUrl);
        movie.setVideoStatus(VideoStatus.READY);
        contentRepository.save(movie);

        log.info("Movie is now ready for streaming", movieId);
    }

    private MovieResponse mapToResponse(Movie movie){
        MovieResponse movieResponse = new MovieResponse();
        movieResponse.setId(movie.getId());
        movieResponse.setTitle(movie.getTitle());
        movieResponse.setDescription(movie.getDescription());
        movieResponse.setGenre(movie.getGenre());
        movieResponse.setRating(movie.getRating());
        movieResponse.setReleaseYear(movie.getReleaseYear());
        movieResponse.setDirector(movie.getDirector());
        movieResponse.setCast(movie.getCast());
        movieResponse.setThumbnailUrl(movie.getThumbnailUrl());
        movieResponse.setDurationInMinutes(movie.getDurationInMinutes());
        movieResponse.setVideoStatus(movie.getVideoStatus());
        movieResponse.setVideoKey(movie.getVideoKey());
        movieResponse.setHlsUrl(movie.getHlsUrl());
        movieResponse.setCreatedAt(movie.getCreatedAt());

        return  movieResponse;
    }
}

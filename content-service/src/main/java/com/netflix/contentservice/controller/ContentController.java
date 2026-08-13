package com.netflix.contentservice.controller;

import com.netflix.contentservice.dto.MovieRequest;
import com.netflix.contentservice.dto.MovieResponse;
import com.netflix.contentservice.model.Genre;
import com.netflix.contentservice.service.ContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/movies")
@Slf4j
@RequiredArgsConstructor
public class ContentController {

    private final ContentService contentService;

    // Adding a new movie to the catalog
    @PostMapping
    public ResponseEntity<MovieResponse> addMovie(
            @Valid @RequestBody MovieRequest movieRequest
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contentService.addMovie(movieRequest));
    }

    // Fetch all the movies
    @GetMapping
    public ResponseEntity<List<MovieResponse>> getAllMovies(){
        return ResponseEntity.status(HttpStatus.OK).body(contentService.getAllMovies());
    }

    // Get Movies by genre
    @GetMapping("/genre/{genre}")
    public ResponseEntity<List<MovieResponse>> getMoviesByGenre(
            @PathVariable Genre genre
    ){
        return ResponseEntity.ok(contentService.getMoviesByGenre(genre));
    }

    //Get Movie by ID
    @GetMapping("/{movieId}")
    public ResponseEntity<MovieResponse> getMoviesByGenre(
            @PathVariable String movieId
    ){
        return ResponseEntity.ok(contentService.getMoviesById(movieId));
    }


    // Searching for the movie titles
    @GetMapping("/search")
    public ResponseEntity<List<MovieResponse>> searchMovies(
            @RequestParam String title
    ){
        return ResponseEntity.ok(contentService.searchMovies(title));
    }
}

package io.javabrains.moviecatalogservice.resources;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import io.javabrains.moviecatalogservice.models.CatalogItem;
import io.javabrains.moviecatalogservice.models.Movie;
import io.javabrains.moviecatalogservice.models.Rating;
import io.javabrains.moviecatalogservice.models.UserRating;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.List;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

@RestController
@RequestMapping("/catalogs")
public class MovieCatalogResource
{
  @Autowired
  private RestTemplate restTemplate;
  @Autowired
  private WebClient.Builder builder;

  @RequestMapping("/{userId}")
  public List<CatalogItem> getCatalog(@PathVariable("userId") String userId)
  {
    UserRating userRating = getUserRating(userId);
    // this call is synchronous. wait until the rest template gives the output
    return userRating.getRatings().stream().map(this::getCatalogItem).collect(toList());
  }

  @HystrixCommand(fallbackMethod = "getFallbackCatalogItem")
  private CatalogItem getCatalogItem(Rating rating) {
    Movie movie = restTemplate.getForObject("http://movie-info-service/movies/" + rating.getMovieId(), Movie.class);
    assert movie != null;
    return new CatalogItem(movie.getName(), "Romantic", rating.getRating());
  }

  @HystrixCommand(fallbackMethod = "getFallbackUserRating")
  private UserRating getUserRating(String userId) {
    UserRating userRating = restTemplate.getForObject("http://ratings-data-service/ratings/users/" + userId, UserRating.class);
    return userRating;
  }

  @RequestMapping("/v2/{userId}")
  public List<CatalogItem> getCatalogAsynchronously(@PathVariable("userId") String userId)
  {
    UserRating rating =
      builder.build().get().uri("http://ratings-data-service/ratings/users/" + userId).retrieve().bodyToMono(UserRating.class)
        .block();
    assert rating != null;
    return rating.getRatings().stream().map(r -> {
      // since http method is get, after builder.build() get
      // bodyToMono - Mono means this will be returned at some point. not immediately
      // but here no way. need to wait until result come. then block()
      Movie movie =
        builder.build().get().uri("http://movie-info-service/movies/" + r.getMovieId()).retrieve().bodyToMono(Movie.class)
          .block();
      assert movie != null;
      return new CatalogItem(movie.getName(), "Romantic", r.getRating());
    }).collect(toList());
  }

  //same signature as getCatalog method
  public List<CatalogItem> getFallbackCatalog(@PathVariable("userId") String userId)
  {
    return singletonList(new CatalogItem("No movie", "", 0));
  }

  private CatalogItem getFallbackCatalogItem(Rating rating)
  {
    return new CatalogItem("Movie not found", "", 0);
  }

  private UserRating getFallbackUserRating(String userId)
  {
    UserRating userRating = new UserRating();
    userRating.setRatings(singletonList(new Rating("", 0)));
    return userRating;
  }
}

package com.example.cinema;

import com.example.cinema.model.CinemaApiModel;
import com.example.cinema.model.Show;
import com.example.wallet.model.WalletApiModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;

import static com.example.wallet.model.WalletApiModel.WalletCommand.*;

@Component
public class Calls {

  @Autowired
  private WebClient webClient;

  private Duration timeout = Duration.ofSeconds(10);

  public void createShow(String showId, String title) {
     createShow(showId,title,100);
  }
  public void createShow(String showId, String title, int maxSeats) {

    var response = webClient.post().uri("/cinema-show/" + showId)
      .bodyValue(new CinemaApiModel.ShowCommand.CreateShow(title, maxSeats))
      .retrieve()
      .toBodilessEntity()
      .block();

    assertThat(response.getStatusCode()).isEqualTo(OK);
  }

  public Show.SeatStatus getSeatStatus(String showId, int seatNumber) {
    return webClient.get().uri("/cinema-show/" + showId + "/seat-status/" + seatNumber)
      .retrieve()
      .bodyToMono(Show.SeatStatus.class)
      .block(timeout);
  }

  public ResponseEntity<Void> reserveSeat(String showId, String walletId, String reservationId, int seatNumber) {
    return webClient.patch().uri("/cinema-show/" + showId + "/reserve")
      .bodyValue(new CinemaApiModel.ShowCommand.ReserveSeat(walletId, reservationId, seatNumber))
      .retrieve()
      .toBodilessEntity()
      .block(timeout);
  }

  public ResponseEntity<Void> cancelSeatReservation(String showId, String reservationId) {
    return webClient.patch().uri("/cinema-show/" + showId + "/cancel-reservation/" + reservationId)
      .retrieve()
      .toBodilessEntity()
      .block(timeout);
  }

  public ResponseEntity<CinemaApiModel.ShowsByAvailableSeatsRecordList> getShowsByAvailableSeats(int requestedSeatCount) {
    return webClient.get().uri("/show/by-available-seats/" + requestedSeatCount)
            .retrieve()
            .toEntity(CinemaApiModel.ShowsByAvailableSeatsRecordList.class)
            .onErrorResume(WebClientResponseException.class, error -> {
              if (error.getStatusCode().is4xxClientError()) {
                return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(null));
              } else {
                return Mono.error(error);
              }
            })
            .block();
  }

  public void createWallet(String walletId, int amount) {
    ResponseEntity<Void> response = webClient.post().uri("/wallet/" + walletId + "/create/" + amount)
      .retrieve()
      .toBodilessEntity()
      .block(timeout);

    assertThat(response.getStatusCode()).isEqualTo(OK);
  }

  public WalletApiModel.WalletResponse getWallet(String walletId) {
    return webClient.get().uri("/wallet/" + walletId)
      .retrieve()
      .bodyToMono(WalletApiModel.WalletResponse.class)
      .block(timeout);
  }

  public void chargeWallet(String walletId,ChargeWallet chargeWallet) {
    ResponseEntity<Void> response = webClient.patch().uri("/wallet/" + walletId + "/charge")
      .bodyValue(chargeWallet)
      .header("skip-failure-simulation", "true")
      .retrieve()
      .toBodilessEntity()
      .block(timeout);

    assertThat(response.getStatusCode()).isEqualTo(OK);
  }

}

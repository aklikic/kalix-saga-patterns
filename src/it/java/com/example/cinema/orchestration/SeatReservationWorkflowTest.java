package com.example.cinema.orchestration;

import com.example.Main;
import com.example.cinema.Calls;
import com.example.cinema.CinemaDomainModel;
import com.example.cinema.orchestration.SeatReservationWorkflow.ReserveSeat;
import com.example.wallet.WalletApiModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.example.cinema.TestUtils.randomId;
import static com.example.cinema.CinemaDomainModel.SeatReservationStatus.SEAT_RESERVATION_REFUNDED;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import static com.example.wallet.WalletApiModel.WalletCommand.*;


@DirtiesContext
@SpringBootTest(classes = Main.class)
@ActiveProfiles("orchestration")
class SeatReservationWorkflowTest {

  @Autowired
  private WebClient webClient;
  @Autowired
  private Calls calls;

  private Duration timeout = Duration.ofSeconds(10);

  @Test
  public void shouldCompleteSeatReservation() {
    //given
    var walletId = randomId();
    var showId = randomId();
    var reservationId = randomId();
    var seatNumber = 10;

    calls.createWallet(walletId, 200);
    calls.createShow(showId, "pulp fiction");

    ReserveSeat reserveSeat = new ReserveSeat(showId, seatNumber, new BigDecimal(100), walletId);

    //when
    ResponseEntity<Void> reservationResponse = reserveSeat(reservationId, reserveSeat);
    assertThat(reservationResponse.getStatusCode()).isEqualTo(OK);

    //then
    await()
      .atMost(10, TimeUnit.of(SECONDS))
      .ignoreExceptions()
      .untilAsserted(() -> {
        CinemaDomainModel.SeatReservationStatus status = getReservationStatus(reservationId);
        assertThat(status).isEqualTo(CinemaDomainModel.SeatReservationStatus.COMPLETED);

        WalletApiModel.WalletResponse walletResponse = calls.getWallet(walletId);
        assertThat(walletResponse.balance()).isEqualTo(new BigDecimal(200 - 100));

        CinemaDomainModel.SeatStatus seatStatus = calls.getSeatStatus(showId, seatNumber);
        assertThat(seatStatus).isEqualTo(CinemaDomainModel.SeatStatus.PAID);
      });
  }

  @Test
  public void shouldRejectReservationIfCaseOfInsufficientWalletBalance() {
    //given
    var walletId = randomId();
    var showId = randomId();
    var reservationId = randomId();
    var seatNumber = 10;

    calls.createWallet(walletId, 50);
    calls.createShow(showId, "pulp fiction");

    ReserveSeat reserveSeat = new ReserveSeat(showId, seatNumber, new BigDecimal(100), walletId);

    //when
    ResponseEntity<Void> reservationResponse = reserveSeat(reservationId, reserveSeat);
    assertThat(reservationResponse.getStatusCode()).isEqualTo(OK);

    //then
    await()
      .atMost(10, TimeUnit.of(SECONDS))
      .ignoreExceptions()
      .untilAsserted(() -> {
        CinemaDomainModel.SeatReservationStatus status = getReservationStatus(reservationId);
        assertThat(status).isEqualTo(CinemaDomainModel.SeatReservationStatus.SEAT_RESERVATION_FAILED);

        WalletApiModel.WalletResponse walletResponse = calls.getWallet(walletId);
        assertThat(walletResponse.balance()).isEqualTo(new BigDecimal(50));

        CinemaDomainModel.SeatStatus seatStatus = calls.getSeatStatus(showId, seatNumber);
        assertThat(seatStatus).isEqualTo(CinemaDomainModel.SeatStatus.AVAILABLE);
      });
  }

  @Test
  public void shouldCancelReservationInCaseOfWalletTimeoutAndRefundMoney() {
    //given
    var walletId = randomId();
    var showId = randomId();
    var reservationId = "42";
    var seatNumber = 10;

    calls.createWallet(walletId, 200);
    calls.createShow(showId, "pulp fiction");

    ReserveSeat reserveSeat = new ReserveSeat(showId, seatNumber, new BigDecimal(100), walletId);

    //when
    ResponseEntity<Void> reservationResponse = reserveSeat(reservationId, reserveSeat);
    assertThat(reservationResponse.getStatusCode()).isEqualTo(OK);

    //simulating charging after timeout
    calls.chargeWallet(walletId, new ChargeWallet(new BigDecimal(100), reservationId, randomId()));

    //then
    await()
      .atMost(30, TimeUnit.of(SECONDS))
      .ignoreExceptions()
      .pollInterval(Duration.ofSeconds(1))
      .untilAsserted(() -> {
        CinemaDomainModel.SeatReservationStatus status = getReservationStatus(reservationId);
        assertThat(status).isEqualTo(SEAT_RESERVATION_REFUNDED);

        WalletApiModel.WalletResponse walletResponse = calls.getWallet(walletId);
        assertThat(walletResponse.balance()).isEqualTo(new BigDecimal(200));

        CinemaDomainModel.SeatStatus seatStatus = calls.getSeatStatus(showId, seatNumber);
        assertThat(seatStatus).isEqualTo(CinemaDomainModel.SeatStatus.AVAILABLE);
      });
  }

  private ResponseEntity<Void> reserveSeat(String reservationId, ReserveSeat reserveSeat) {
    return webClient.post().uri("/seat-reservation/" + reservationId)
      .bodyValue(reserveSeat)
      .retrieve()
      .toBodilessEntity()
      .block(timeout);
  }

  private CinemaDomainModel.SeatReservationStatus getReservationStatus(String reservationId) {
    return webClient.get().uri("/seat-reservation/" + reservationId)
      .retrieve()
      .bodyToMono(CinemaDomainModel.SeatReservationStatus.class)
      .block(timeout);
  }

}
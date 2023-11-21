package com.example.cinema.choreography;

import com.example.Main;
import com.example.cinema.Calls;
import com.example.cinema.TestUtils;
import com.example.cinema.model.Show;
import com.example.wallet.model.WalletApiModel;
import kalix.spring.testkit.KalixIntegrationTestKitSupport;
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

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;
import static com.example.wallet.model.WalletApiModel.WalletCommand.*;


@DirtiesContext
@SpringBootTest(classes = Main.class)
@ActiveProfiles("choreography")
public class ShowSeatReservationIntegrationTest extends KalixIntegrationTestKitSupport {

  @Autowired
  private WebClient webClient;
  @Autowired
  private Calls calls;

  private Duration timeout = Duration.ofSeconds(10);

  @Test
  public void shouldCompleteSeatReservation() {
    //given
    var walletId = TestUtils.randomId();
    var showId = TestUtils.randomId();
    var reservationId = TestUtils.randomId();
    var seatNumber = 10;

    calls.createWallet(walletId, 200);
    calls.createShow(showId, "pulp fiction");

    //when
    ResponseEntity<Void> reservationResponse = calls.reserveSeat(showId, walletId, reservationId, seatNumber);
    assertThat(reservationResponse.getStatusCode()).isEqualTo(OK);

    //then
    await()
      .atMost(10, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        Show.SeatStatus seatStatus = calls.getSeatStatus(showId, seatNumber);
        assertThat(seatStatus).isEqualTo(Show.SeatStatus.PAID);

        WalletApiModel.WalletResponse wallet = calls.getWallet(walletId);
        assertThat(wallet.balance()).isEqualTo(new BigDecimal(100));
      });
  }

  @Test
  public void shouldRejectReservationIfCaseOfInsufficientWalletBalance() {
    //given
    var walletId = TestUtils.randomId();
    var showId = TestUtils.randomId();
    var reservationId = TestUtils.randomId();
    var seatNumber = 11;

    calls.createWallet(walletId, 1);
    calls.createShow(showId, "pulp fiction");

    //when
    ResponseEntity<Void> reservationResponse = calls.reserveSeat(showId, walletId, reservationId, seatNumber);
    assertThat(reservationResponse.getStatusCode()).isEqualTo(OK);

    //then
    await()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        Show.SeatStatus seatStatus = calls.getSeatStatus(showId, seatNumber);
        assertThat(seatStatus).isEqualTo(Show.SeatStatus.AVAILABLE);
      });
  }

  @Test
  public void shouldConfirmCancelledReservationAndRefund() {
    //given
    var walletId = TestUtils.randomId();
    var showId = TestUtils.randomId();
    var reservationId = "42";
    var seatNumber = 11;

    calls.createWallet(walletId, 300);
    calls.createShow(showId, "pulp fiction");

    //when
    ResponseEntity<Void> reservationResponse = calls.reserveSeat(showId, walletId, reservationId, seatNumber);
    assertThat(reservationResponse.getStatusCode()).isEqualTo(OK);

    //then
    await()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        Show.SeatStatus seatStatus = calls.getSeatStatus(showId, seatNumber);
        assertThat(seatStatus).isEqualTo(Show.SeatStatus.AVAILABLE);
      });

    //simulating that the wallet was actually charged
    calls.chargeWallet(walletId, new ChargeWallet(new BigDecimal(100), reservationId, TestUtils.randomId()));

    await()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        WalletApiModel.WalletResponse wallet = calls.getWallet(walletId);
        assertThat(wallet.balance()).isEqualTo(new BigDecimal(300));
      });
  }

  @Test
  public void shouldAllowToCancelAlreadyCancelledReservation() {
    //given
    var walletId = TestUtils.randomId();
    var showId = TestUtils.randomId();
    var reservationId = "42";
    var seatNumber = 11;

    calls.createWallet(walletId, 300);
    calls.createShow(showId, "pulp fiction");

    //when
    ResponseEntity<Void> reservationResponse = calls.reserveSeat(showId, walletId, reservationId, seatNumber);
    assertThat(reservationResponse.getStatusCode()).isEqualTo(OK);

    //then
    await()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        Show.SeatStatus seatStatus = calls.getSeatStatus(showId, seatNumber);
        assertThat(seatStatus).isEqualTo(Show.SeatStatus.AVAILABLE);
      });

    //simulating that the wallet charging was rejected for this reservation
    calls.chargeWallet(walletId, new ChargeWallet(new BigDecimal(400), reservationId, TestUtils.randomId()));

    await()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        WalletApiModel.WalletResponse wallet = calls.getWallet(walletId);
        assertThat(wallet.balance()).isEqualTo(new BigDecimal(300));
      });
  }
}
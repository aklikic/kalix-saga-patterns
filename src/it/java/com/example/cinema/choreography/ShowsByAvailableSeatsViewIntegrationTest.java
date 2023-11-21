package com.example.cinema.choreography;

import com.example.Main;
import com.example.cinema.Calls;
import com.example.cinema.TestUtils;
import com.example.cinema.model.CinemaApiModel;
import kalix.spring.testkit.KalixIntegrationTestKitSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

@DirtiesContext
@SpringBootTest(classes = Main.class)
@ActiveProfiles("choreography")
class ShowsByAvailableSeatsViewIntegrationTest extends KalixIntegrationTestKitSupport {

  @Autowired
  private Calls calls;

  private Duration timeout = Duration.ofSeconds(10);

  @Test
  public void shouldUpdateShowByAvailableSeatsEntry() {
    //given
    var showId = TestUtils.randomId();
    var showTitle = "title";
    var showTitleSearch = showTitle;
    var maxSeats = 100;
    var reservationId1 = TestUtils.randomId();
    var reservationId2 = TestUtils.randomId();
    var walletId = TestUtils.randomId();
    calls.createShow(showId, showTitle, maxSeats);
    calls.createWallet(walletId, 500);

    //when
    calls.reserveSeat(showId, walletId, reservationId1, 3);
    calls.reserveSeat(showId, walletId, reservationId2, 4);

    //then
    List<CinemaApiModel.ShowsByAvailableSeatsViewRecord> list = new ArrayList<>();
    list.add(new CinemaApiModel.ShowsByAvailableSeatsViewRecord(showId,showTitle,maxSeats-2));
    CinemaApiModel.ShowsByAvailableSeatsRecordList expected = new CinemaApiModel.ShowsByAvailableSeatsRecordList(list);
    await()
      .atMost(10, TimeUnit.of(SECONDS))
      .ignoreExceptions()
      .untilAsserted(() -> {
        CinemaApiModel.ShowsByAvailableSeatsRecordList result = calls.getShowsByAvailableSeats(1).getBody();
        assertThat(expected).isEqualTo(result);

      });
  }

}
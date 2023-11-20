package com.example.cinema;

import kalix.javasdk.testkit.EventSourcedResult;
import kalix.javasdk.testkit.EventSourcedTestKit;
import org.junit.jupiter.api.Test;

import static com.example.cinema.DomainGenerators.randomReservationId;
import static com.example.cinema.DomainGenerators.randomShowId;
import static com.example.cinema.DomainGenerators.randomWalletId;
import static com.example.cinema.CinemaDomainModel.SeatStatus.PAID;
import static org.assertj.core.api.Assertions.assertThat;

class ShowEntityTest {

  @Test
  public void shouldReserveAndConfirmSeat() {
    //given
    var showId = randomShowId();
    var walletId = randomWalletId();
    var reservationId = randomReservationId();
    int seatNumber = 1;
    EventSourcedTestKit<CinemaDomainModel.Show, CinemaDomainModel.ShowEvent, ShowEntity> testKit = EventSourcedTestKit.of(ShowEntity::new);
    var createShow = new CinemaApiModel.ShowCommand.CreateShow("title", 100);
    var reserveSeat = new CinemaApiModel.ShowCommand.ReserveSeat(walletId, reservationId, seatNumber);

    //when
    testKit.call(s -> s.create(showId, createShow));
    testKit.call(s -> s.reserve(reserveSeat));
    EventSourcedResult<CinemaApiModel.Response> result = testKit.call(s -> s.confirmPayment(reservationId));

    //then
    var confirmedSeat = testKit.getState().seats().get(seatNumber).get();
    assertThat(confirmedSeat.number()).isEqualTo(seatNumber);
    assertThat(confirmedSeat.status()).isEqualTo(PAID);
  }
}
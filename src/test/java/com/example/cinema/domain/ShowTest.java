package com.example.cinema.domain;

import com.example.cinema.domain.ShowCommand.ConfirmReservationPayment;
import com.example.cinema.domain.ShowEvent.SeatReservationCancelled;
import com.example.cinema.domain.ShowEvent.SeatReservationPaid;
import com.example.cinema.domain.ShowEvent.SeatReserved;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.example.cinema.domain.DomainGenerators.randomReservationId;
import static com.example.cinema.domain.DomainGenerators.randomShow;
import static com.example.cinema.domain.DomainGenerators.randomShowId;
import static com.example.cinema.domain.DomainGenerators.randomWalletId;
import static com.example.cinema.domain.SeatStatus.AVAILABLE;
import static com.example.cinema.domain.SeatStatus.PAID;
import static com.example.cinema.domain.SeatStatus.RESERVED;
import static com.example.cinema.domain.ShowBuilder.showBuilder;
import static com.example.cinema.domain.ShowCommandError.RESERVATION_NOT_FOUND;
import static com.example.cinema.domain.ShowCommandError.SEAT_NOT_AVAILABLE;
import static com.example.cinema.domain.ShowCommandError.SEAT_NOT_EXISTS;
import static com.example.cinema.domain.ShowCommandError.SHOW_ALREADY_EXISTS;
import static com.example.cinema.domain.ShowCommandGenerators.randomCreateShow;
import static com.example.cinema.domain.ShowCommandGenerators.randomReserveSeat;
import static org.assertj.core.api.Assertions.assertThat;

class ShowTest {

  @Test
  public void shouldCreateTheShow() {
    //given
    String showId = randomShowId();
    var createShow = randomCreateShow();

    //when
    var showCreated = ShowCreator.create(showId, createShow).get();
    var show = Show.create(showCreated);

    //then
    assertThat(show.id()).isEqualTo(showId);
    assertThat(show.title()).isEqualTo(createShow.title());
    assertThat(show.seats()).hasSize(createShow.maxSeats());
  }

  @Test
  public void shouldNotProcessCreateShowCommandForExistingShow() {
    //given
    var show = randomShow();
    var createShow = randomCreateShow();

    //when
    var error = show.process(createShow).getLeft();

    //then
    assertThat(error).isEqualTo(SHOW_ALREADY_EXISTS);
  }

  @Test
  public void shouldReserveTheSeat() {
    //given
    var show = randomShow();
    var reserveSeat = randomReserveSeat();
    var seatToReserve = show.getSeat(reserveSeat.seatNumber()).get();

    //when
    var event = show.process(reserveSeat).get();

    //then
    assertThat(event).isEqualTo(new SeatReserved(show.id(), reserveSeat.walletId(), reserveSeat.reservationId(), reserveSeat.seatNumber(), seatToReserve.price()));
  }

  @Test
  public void shouldReserveTheSeatWithApplyingEvent() {
    //given
    var show = randomShow();
    var reserveSeat = randomReserveSeat();

    //when
    var event = show.process(reserveSeat).get();
    var updatedShow = show.apply(event);

    //then
    var reservedSeat = updatedShow.seats().get(reserveSeat.seatNumber()).get();
    assertThat(event).isInstanceOf(SeatReserved.class);
    assertThat(reservedSeat.status()).isEqualTo(RESERVED);
    assertThat(updatedShow.pendingReservations()).contains(new Tuple2<>(reserveSeat.reservationId(), reserveSeat.seatNumber()));
  }

  @Test
  public void shouldNotReserveAlreadyReservedSeat() {
    //given
    var show = randomShow();
    var reserveSeat = randomReserveSeat();

    //when
    var event = show.process(reserveSeat).get();
    var updatedShow = show.apply(event);

    //then
    assertThat(event).isInstanceOf(SeatReserved.class);

    //when
    ShowCommandError result = updatedShow.process(reserveSeat).getLeft();

    //then
    assertThat(result).isEqualTo(SEAT_NOT_AVAILABLE);
  }

  @Test
  public void shouldNotReserveNotExistingSeat() {
    //given
    var show = randomShow();
    var reserveSeat = new ShowCommand.ReserveSeat(randomWalletId(), randomReservationId(), ShowBuilder.MAX_SEATS + 1);

    //when
    ShowCommandError result = show.process(reserveSeat).getLeft();

    //then
    assertThat(result).isEqualTo(SEAT_NOT_EXISTS);
  }

  @Test
  public void shouldCancelSeatReservation() {
    //given
    var reservedSeat = new Seat(2, SeatStatus.RESERVED, new BigDecimal("123"));
    var reservationId = randomReservationId();
    var show = showBuilder().withRandomSeats().withSeatReservation(reservedSeat, reservationId).build();
    var cancelSeatReservation = new ShowCommand.CancelSeatReservation(reservationId);

    //when
    var event = show.process(cancelSeatReservation).get();
    var updatedShow = show.apply(event);

    //then
    assertThat(event).isEqualTo(new SeatReservationCancelled(show.id(), reservationId, reservedSeat.number()));
    assertThat(updatedShow.getSeat(reservedSeat.number()).get().status()).isEqualTo(AVAILABLE);
    assertThat(updatedShow.pendingReservations().get(reservationId).isEmpty()).isTrue();
  }

  @Test
  public void shouldConfirmSeatReservation() {
    //given
    var reservedSeat = new Seat(2, SeatStatus.RESERVED, new BigDecimal("123"));
    var reservationId = randomReservationId();
    var show = showBuilder().withRandomSeats().withSeatReservation(reservedSeat, reservationId).build();
    var confirmReservationPayment = new ConfirmReservationPayment(reservationId);

    //when
    var event = show.process(confirmReservationPayment).get();
    var updatedShow = show.apply(event);

    //then
    assertThat(event).isEqualTo(new SeatReservationPaid(show.id(), reservationId, reservedSeat.number()));
    assertThat(updatedShow.getSeat(reservedSeat.number()).get().status()).isEqualTo(PAID);
    assertThat(updatedShow.pendingReservations().get(reservationId).isEmpty()).isTrue();
  }

  @Test
  public void shouldNotCancelReservationOfAvailableSeat() {
    //given
    var show = randomShow();
    var cancelSeatReservation = new ShowCommand.CancelSeatReservation(randomReservationId());

    //when
    var result = show.process(cancelSeatReservation).getLeft();

    //then
    assertThat(result).isEqualTo(RESERVATION_NOT_FOUND);
  }

  private Show apply(Show show, List<ShowEvent> events) {
    return events.foldLeft(show, Show::apply);
  }
}
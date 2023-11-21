package com.example.cinema;

import com.example.cinema.model.CinemaApiModel;
import com.example.cinema.model.Show;
import com.example.cinema.model.ShowEvent;
import io.vavr.Tuple2;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.example.cinema.DomainGenerators.randomReservationId;
import static com.example.cinema.DomainGenerators.randomShow;
import static com.example.cinema.DomainGenerators.randomShowId;
import static com.example.cinema.DomainGenerators.randomWalletId;
import static com.example.cinema.model.Show.SeatStatus.AVAILABLE;
import static com.example.cinema.model.Show.SeatStatus.PAID;
import static com.example.cinema.model.Show.SeatStatus.RESERVED;
import static com.example.cinema.ShowBuilder.showBuilder;
import static com.example.cinema.model.CinemaApiModel.ShowCommandError.CANCELLING_CONFIRMED_RESERVATION;
import static com.example.cinema.model.CinemaApiModel.ShowCommandError.DUPLICATED_COMMAND;
import static com.example.cinema.model.CinemaApiModel.ShowCommandError.RESERVATION_NOT_FOUND;
import static com.example.cinema.model.CinemaApiModel.ShowCommandError.SEAT_NOT_AVAILABLE;
import static com.example.cinema.model.CinemaApiModel.ShowCommandError.SEAT_NOT_FOUND;
import static com.example.cinema.model.CinemaApiModel.ShowCommandError.SHOW_ALREADY_EXISTS;
import static com.example.cinema.ShowCommandGenerators.randomCreateShow;
import static com.example.cinema.ShowCommandGenerators.randomReserveSeat;
import static org.assertj.core.api.Assertions.assertThat;
import static com.example.cinema.model.CinemaApiModel.ShowCommand.*;
import static com.example.cinema.model.ShowEvent.*;

class ShowTest {

  @Test
  public void shouldCreateTheShow() {
    //given
    String showId = randomShowId();
    var createShow = randomCreateShow();

    //when
    var showCreated = Show.ShowCreator.create(showId, createShow).get();
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
    assertThat(event).isEqualTo(new SeatReserved(show.id(), reserveSeat.walletId(), reserveSeat.reservationId(), reserveSeat.seatNumber(), seatToReserve.price(),show.seats().size()-1));
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
    var reserveTheSameSeat = new CinemaApiModel.ShowCommand.ReserveSeat(randomWalletId(), randomReservationId(), reserveSeat.seatNumber());

    //when
    var event = show.process(reserveSeat).get();
    var updatedShow = show.apply(event);

    //then
    assertThat(event).isInstanceOf(SeatReserved.class);

    //when
    CinemaApiModel.ShowCommandError result = updatedShow.process(reserveTheSameSeat).getLeft();

    //then
    assertThat(result).isEqualTo(SEAT_NOT_AVAILABLE);
  }

  @Test
  public void shouldRejectReservationDuplicate() {
    //given
    var show = randomShow();
    var reserveSeat = randomReserveSeat();

    //when
    var event = show.process(reserveSeat).get();
    var updatedShow = show.apply(event);

    //then
    assertThat(event).isInstanceOf(SeatReserved.class);

    //when
    CinemaApiModel.ShowCommandError result = updatedShow.process(reserveSeat).getLeft();

    //then
    assertThat(result).isEqualTo(DUPLICATED_COMMAND);
  }

  @Test
  public void shouldNotReserveNotExistingSeat() {
    //given
    var show = randomShow();
    var reserveSeat = new CinemaApiModel.ShowCommand.ReserveSeat(randomWalletId(), randomReservationId(), ShowBuilder.MAX_SEATS + 1);

    //when
    CinemaApiModel.ShowCommandError result = show.process(reserveSeat).getLeft();

    //then
    assertThat(result).isEqualTo(SEAT_NOT_FOUND);
  }

  @Test
  public void shouldCancelSeatReservation() {
    //given
    var reservedSeat = new Show.Seat(2, Show.SeatStatus.RESERVED, new BigDecimal("123"));
    var reservationId = randomReservationId();
    var show = showBuilder().withRandomSeats().withSeatReservation(reservedSeat, reservationId).build();
    var cancelSeatReservation = new CancelSeatReservation(reservationId);

    //when
    var event = show.process(cancelSeatReservation).get();
    var updatedShow = show.apply(event);

    //then
    assertThat(event).isEqualTo(new SeatReservationCancelled(show.id(), reservationId, reservedSeat.number(),show.seats().size()+1));
    assertThat(updatedShow.getSeat(reservedSeat.number()).get().status()).isEqualTo(AVAILABLE);
    assertThat(updatedShow.pendingReservations().get(reservationId).isEmpty()).isTrue();
  }

  @Test
  public void shouldRejectCancellationDuplicate() {
    //given
    var reservedSeat = new Show.Seat(2, Show.SeatStatus.RESERVED, new BigDecimal("123"));
    var reservationId = randomReservationId();
    var show = showBuilder().withRandomSeats().withSeatReservation(reservedSeat, reservationId).build();
    var cancelSeatReservation = new CancelSeatReservation(reservationId);

    //when
    var event = show.process(cancelSeatReservation).get();
    var updatedShow = show.apply(event);

    //then
    assertThat(event).isInstanceOf(SeatReservationCancelled.class);

    //when
    CinemaApiModel.ShowCommandError result = updatedShow.process(cancelSeatReservation).getLeft();

    //then
    assertThat(result).isEqualTo(DUPLICATED_COMMAND);
  }

  @Test
  public void shouldConfirmAfterCancellation() {
    //given
    var reservedSeat = new Show.Seat(2, Show.SeatStatus.RESERVED, new BigDecimal("123"));
    var reservationId = randomReservationId();
    var show = showBuilder().withRandomSeats().withSeatReservation(reservedSeat, reservationId).build();
    var cancelSeatReservation = new CancelSeatReservation(reservationId);
    var confirmReservationPayment = new CinemaApiModel.ShowCommand.ConfirmReservationPayment(reservationId);
    var event = show.process(cancelSeatReservation).get();
    var updatedShow = show.apply(event);

    //when
    var result = updatedShow.process(confirmReservationPayment).get();

    //then
    assertThat(result).isEqualTo(new CancelledReservationConfirmed(show.id(), reservationId, reservedSeat.number()));
  }

  @Test
  public void shouldConfirmSeatReservation() {
    //given
    var reservedSeat = new Show.Seat(2, Show.SeatStatus.RESERVED, new BigDecimal("123"));
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
  public void shouldRejectConfirmationDuplicate() {
    //given
    var reservedSeat = new Show.Seat(2, Show.SeatStatus.RESERVED, new BigDecimal("123"));
    var reservationId = randomReservationId();
    var show = showBuilder().withRandomSeats().withSeatReservation(reservedSeat, reservationId).build();
    var confirmReservationPayment = new ConfirmReservationPayment(reservationId);

    //when
    var event = show.process(confirmReservationPayment).get();
    var updatedShow = show.apply(event);

    //then
    assertThat(event).isInstanceOf(SeatReservationPaid.class);

    //when
    CinemaApiModel.ShowCommandError result = updatedShow.process(confirmReservationPayment).getLeft();

    //then
    assertThat(result).isEqualTo(DUPLICATED_COMMAND);
  }

  @Test
  public void shouldRejectCancellationAfterConfirmation() {
    //given
    var reservedSeat = new Show.Seat(2, Show.SeatStatus.RESERVED, new BigDecimal("123"));
    var reservationId = randomReservationId();
    var show = showBuilder().withRandomSeats().withSeatReservation(reservedSeat, reservationId).build();
    var confirmReservationPayment = new ConfirmReservationPayment(reservationId);
    var cancelSeatReservation = new CancelSeatReservation(reservationId);
    var event = show.process(confirmReservationPayment).get();
    var updatedShow = show.apply(event);

    //when
    CinemaApiModel.ShowCommandError result = updatedShow.process(cancelSeatReservation).getLeft();

    //then
    assertThat(result).isEqualTo(CANCELLING_CONFIRMED_RESERVATION);
  }

  @Test
  public void shouldNotCancelReservationOfAvailableSeat() {
    //given
    var show = randomShow();
    var cancelSeatReservation = new CancelSeatReservation(randomReservationId());

    //when
    var result = show.process(cancelSeatReservation).getLeft();

    //then
    assertThat(result).isEqualTo(RESERVATION_NOT_FOUND);
  }

  private Show apply(Show show, List<ShowEvent> events) {
    return io.vavr.collection.List.ofAll(events).foldLeft(show, Show::apply);
  }
}
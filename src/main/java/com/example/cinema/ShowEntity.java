package com.example.cinema;

import com.example.cinema.model.Show;
import com.example.cinema.model.ShowEvent;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.function.Predicate;

import static com.example.cinema.model.CinemaApiModel.ShowCommandError.CANCELLING_CONFIRMED_RESERVATION;
import static com.example.cinema.model.CinemaApiModel.ShowCommandError.DUPLICATED_COMMAND;
import static com.example.cinema.model.CinemaApiModel.ShowCommandError.RESERVATION_NOT_FOUND;
import static kalix.javasdk.StatusCode.ErrorCode.BAD_REQUEST;
import static kalix.javasdk.StatusCode.ErrorCode.NOT_FOUND;

import static com.example.cinema.model.CinemaApiModel.ShowCommand.*;
import static com.example.cinema.model.CinemaApiModel.*;
import static com.example.cinema.model.CinemaApiModel.Response.*;
import static com.example.cinema.model.ShowEvent.*;

@Id("id")
@TypeId("cinema-show")
@RequestMapping("/cinema-show/{id}")
public class ShowEntity extends EventSourcedEntity<Show, ShowEvent> {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  @PostMapping
  public Effect<Response> create(@PathVariable String id, @RequestBody CreateShow createShow) {
    if (currentState() != null) {
      return effects().error("show already exists", BAD_REQUEST);
    } else {
      return Show.ShowCreator.create(id, createShow).fold(
        error -> errorEffect(error, createShow),
        showCreated -> persistEffect(showCreated, "show created")
      );
    }
  }

  @PatchMapping("/reserve")
  public Effect<Response> reserve(@RequestBody ReserveSeat reserveSeat) {
    if (currentState() == null) {
      return effects().error("show not found", NOT_FOUND);
    } else {
      return currentState().handleReservation(reserveSeat).fold(
        error -> errorEffect(error, reserveSeat),
        showEvent -> persistEffect(showEvent, "reserved")
      );
    }
  }

  @PatchMapping("/cancel-reservation/{reservationId}")
  public Effect<Response> cancelReservation(@PathVariable String reservationId) {
    if (currentState() == null) {
      return effects().error("show not found", NOT_FOUND);
    } else {
      CancelSeatReservation cancelSeatReservation = new CancelSeatReservation(reservationId);
      return currentState().handleCancellation(cancelSeatReservation).fold(
        error -> errorEffect(error, cancelSeatReservation, e -> e == DUPLICATED_COMMAND
          || e == CANCELLING_CONFIRMED_RESERVATION
          || e == RESERVATION_NOT_FOUND),
        showEvent -> persistEffect(showEvent, "reservation cancelled")
      );
    }
  }

  @PatchMapping("/confirm-payment/{reservationId}")
  public Effect<Response> confirmPayment(@PathVariable String reservationId) {
    if (currentState() == null) {
      return effects().error("show not found", NOT_FOUND);
    } else {
      ConfirmReservationPayment confirmReservationPayment = new ConfirmReservationPayment(reservationId);
      return currentState().handleConfirmation(confirmReservationPayment).fold(
        error -> errorEffect(error, confirmReservationPayment),
        showEvent -> persistEffect(showEvent, "payment confirmed")
      );
    }
  }

  private Effect<Response> persistEffect(ShowEvent showEvent, String message) {
    return effects()
      .emitEvent(showEvent)
      .thenReply(__ -> Success.of(message));
  }

  private Effect<Response> errorEffect(ShowCommandError error, ShowCommand showCommand) {
    return errorEffect(error, showCommand, e -> e == DUPLICATED_COMMAND);
  }

  private Effect<Response> errorEffect(ShowCommandError error, ShowCommand showCommand, Predicate<ShowCommandError> shouldBeSuccessful) {
    if (shouldBeSuccessful.test(error)) {
      return effects().reply(Success.of("ok"));
    } else {
      logger.error("processing command {} failed with {}", showCommand, error);
      return effects().reply(Failure.of(error.name()));
    }
  }

  @GetMapping
  public Effect<ShowResponse> get() {
    if (currentState() == null) {
      return effects().error("show not found", NOT_FOUND);
    } else {
      return effects().reply(ShowResponse.from(currentState()));
    }
  }

  @GetMapping("/seat-status/{seatNumber}")
  public Effect<Show.SeatStatus> getSeatStatus(@PathVariable int seatNumber) {
    if (currentState() == null) {
      return effects().error("show not found", NOT_FOUND);
    } else {
      return currentState().seats().get(seatNumber).fold(
        () -> effects().error("seat not found", NOT_FOUND),
        seat -> effects().reply(seat.status())
      );
    }
  }

  @EventHandler
  public Show onEvent(ShowCreated showCreated) {
    return Show.create(showCreated);
  }

  @EventHandler
  public Show onEvent(SeatReserved seatReserved) {
    return currentState().applyReserved(seatReserved);
  }

  @EventHandler
  public Show onEvent(SeatReservationCancelled seatReservationCancelled) {
    return currentState().applyReservationCancelled(seatReservationCancelled);
  }

  @EventHandler
  public Show onEvent(SeatReservationPaid seatReservationPaid) {
    return currentState().applyReservationPaid(seatReservationPaid);
  }
}

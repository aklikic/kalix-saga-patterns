package com.example.cinema.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import static com.example.cinema.model.CinemaApiModel.ShowCommandError.*;
import static com.example.cinema.model.Show.SeatStatus.AVAILABLE;
import static io.vavr.control.Either.left;
import static io.vavr.control.Either.right;

public record Show(String id, String title, Map<Integer, Seat> seats,
                   Map<String, Integer> pendingReservations,
                   Map<String, FinishedReservation> finishedReservations, int availableSeats) {

    public static Show create(ShowEvent.ShowCreated showCreated) {
        InitialShow initialShow = showCreated.initialShow();
        List<Tuple2<Integer, Seat>> seats = initialShow.seats().stream().map(seat -> new Tuple2<>(seat.number(), seat)).toList();
        return new Show(initialShow.id(), initialShow.title(), HashMap.ofEntries(seats), HashMap.empty(), HashMap.empty(), initialShow.seats().size());
    }

    public Either<CinemaApiModel.ShowCommandError, ShowEvent> process(CinemaApiModel.ShowCommand command) {
        return switch (command) {
            case CinemaApiModel.ShowCommand.CreateShow ignored -> left(SHOW_ALREADY_EXISTS);
            case CinemaApiModel.ShowCommand.ReserveSeat reserveSeat -> handleReservation(reserveSeat);
            case CinemaApiModel.ShowCommand.ConfirmReservationPayment confirmReservationPayment ->
                    handleConfirmation(confirmReservationPayment);
            case CinemaApiModel.ShowCommand.CancelSeatReservation cancelSeatReservation ->
                    handleCancellation(cancelSeatReservation);
        };
    }

    public Either<CinemaApiModel.ShowCommandError, ShowEvent> handleReservation(CinemaApiModel.ShowCommand.ReserveSeat reserveSeat) {
        int seatNumber = reserveSeat.seatNumber();
        if (isDuplicate(reserveSeat.reservationId())) {
            return left(DUPLICATED_COMMAND);
        } else {
            return seats.get(seatNumber).<Either<CinemaApiModel.ShowCommandError, ShowEvent>>map(seat -> {
                if (seat.isAvailable()) {
                    return right(new ShowEvent.SeatReserved(id, reserveSeat.walletId(), reserveSeat.reservationId(), seatNumber, seat.price(), availableSeats()-1));
                } else {
                    return left(SEAT_NOT_AVAILABLE);
                }
            }).getOrElse(left(SEAT_NOT_FOUND));
        }
    }

    public Either<CinemaApiModel.ShowCommandError, ShowEvent> handleCancellation(CinemaApiModel.ShowCommand.CancelSeatReservation cancelSeatReservation) {
        String reservationId = cancelSeatReservation.reservationId();
        return pendingReservations.get(reservationId).fold(
                () ->left(RESERVATION_NOT_FOUND),
                /*matching reservation*/
                seatNumber -> seats.get(seatNumber).<Either<CinemaApiModel.ShowCommandError, ShowEvent>>map(seat ->
                        right(new ShowEvent.SeatReservationCancelled(id, reservationId, seatNumber, availableSeats()+1))
                ).getOrElse(left(SEAT_NOT_FOUND))
        );
    }

    public Either<CinemaApiModel.ShowCommandError, ShowEvent> handleConfirmation(CinemaApiModel.ShowCommand.ConfirmReservationPayment confirmReservationPayment) {
        String reservationId = confirmReservationPayment.reservationId();
        return pendingReservations.get(reservationId).fold(
                () -> left(RESERVATION_NOT_FOUND),
                seatNumber ->
                        seats.get(seatNumber).<Either<CinemaApiModel.ShowCommandError, ShowEvent>>map(seat ->
                                right(new ShowEvent.SeatReservationPaid(id, reservationId, seatNumber))
                        ).getOrElse(left(SEAT_NOT_FOUND)));
    }


    private boolean isDuplicate(String reservationId) {
        return pendingReservations.containsKey(reservationId) ||
                finishedReservations.get(reservationId).isDefined();
    }

    public Show apply(ShowEvent event) {
        return switch (event) {
            case ShowEvent.ShowCreated ignored ->
                    throw new IllegalStateException("Show is already created, use Show.create instead.");
            case ShowEvent.SeatReserved seatReserved -> applyReserved(seatReserved);
            case ShowEvent.SeatReservationPaid seatReservationPaid ->
                    applyReservationPaid(seatReservationPaid);
            case ShowEvent.SeatReservationCancelled seatReservationCancelled ->
                    applyReservationCancelled(seatReservationCancelled);
//            case ShowEvent.CancelledReservationConfirmed __ -> this;
        };
    }

    public Show applyReserved(ShowEvent.SeatReserved seatReserved) {
        Seat seat = getSeatOrThrow(seatReserved.seatNumber());
        return new Show(id, title, seats.put(seat.number(), seat.reserved()),
                pendingReservations.put(seatReserved.reservationId(), seatReserved.seatNumber()),
                finishedReservations,seatReserved.availableSeatsCount());
    }

    public Show applyReservationPaid(ShowEvent.SeatReservationPaid seatReservationPaid) {
        Seat seat = getSeatOrThrow(seatReservationPaid.seatNumber());
        String reservationId = seatReservationPaid.reservationId();
        FinishedReservation finishedReservation = new FinishedReservation(reservationId, seat.number()/*, CONFIRMED*/);
        return new Show(id, title, seats.put(seat.number(), seat.paid()),
                pendingReservations.remove(reservationId),
                finishedReservations.put(reservationId, finishedReservation),availableSeats());

    }

    public Show applyReservationCancelled(ShowEvent.SeatReservationCancelled seatReservationCancelled) {
        Seat seat = getSeatOrThrow(seatReservationCancelled.seatNumber());
        String reservationId = seatReservationCancelled.reservationId();
        FinishedReservation finishedReservation = new FinishedReservation(reservationId, seat.number()/*, CANCELLED*/);
        return new Show(id, title, seats.put(seat.number(), seat.available()),
                pendingReservations.remove(reservationId),
                finishedReservations.put(reservationId, finishedReservation),seatReservationCancelled.availableSeatsCount());
    }


    private Seat getSeatOrThrow(int seatNumber) {
        return seats.get(seatNumber).getOrElseThrow(() -> new IllegalStateException("Seat not found %s".formatted(seatNumber)));
    }

    public Option<Seat> getSeat(int seatNumber) {
        return seats.get(seatNumber);
    }

    public enum SeatReservationStatus {
      STARTED, SEAT_RESERVED, WALLET_CHARGE_REJECTED, WALLET_CHARGED, COMPLETED, SEAT_RESERVATION_FAILED, WALLET_REFUNDED, SEAT_RESERVATION_REFUNDED
    }

    public enum SeatStatus {
      AVAILABLE, RESERVED, PAID
    }

    public static final record FinishedReservation(String reservationId, int seatNumber) { }

    public static final record InitialShow(String id, String title, List<Seat> seats) implements Serializable {
    }

    public static final record Reservation(String reservationId, String showId, String walletId, BigDecimal price) {
    }

    public static final record Seat(int number, SeatStatus status, BigDecimal price) {
      @JsonIgnore
      public boolean isAvailable() {
        return status == AVAILABLE;
      }

      public Seat reserved() {
        return new Seat(number, SeatStatus.RESERVED, price);
      }

      public Seat paid() {
        return new Seat(number, SeatStatus.PAID, price);
      }

      public Seat available() {
        return new Seat(number, AVAILABLE, price);
      }
    }

    public static final record SeatReservation(String reservationId, String showId, int seatNumber, String walletId, BigDecimal price,
                                               SeatReservationStatus status) {

      public SeatReservation asSeatReservationFailed() {
        return new SeatReservation(reservationId, showId, seatNumber, walletId, price, SeatReservationStatus.SEAT_RESERVATION_FAILED);
      }

      public SeatReservation asSeatReserved() {
        return new SeatReservation(reservationId, showId, seatNumber, walletId, price, SeatReservationStatus.SEAT_RESERVED);
      }

      public SeatReservation asWalletChargeRejected() {
        return new SeatReservation(reservationId, showId, seatNumber, walletId, price, SeatReservationStatus.WALLET_CHARGE_REJECTED);
      }

      public SeatReservation asWalletCharged() {
        return new SeatReservation(reservationId, showId, seatNumber, walletId, price, SeatReservationStatus.WALLET_CHARGED);
      }

      public SeatReservation asCompleted() {
        return new SeatReservation(reservationId, showId, seatNumber, walletId, price, SeatReservationStatus.COMPLETED);
      }

      public SeatReservation asSeatReservationRefunded() {
        return new SeatReservation(reservationId, showId, seatNumber, walletId, price, SeatReservationStatus.SEAT_RESERVATION_REFUNDED);
      }

      public SeatReservation asWalletRefunded() {
        return new SeatReservation(reservationId, showId, seatNumber, walletId, price, SeatReservationStatus.WALLET_REFUNDED);
      }

      public SeatReservation asFailed() {
        if (status == SeatReservationStatus.WALLET_CHARGE_REJECTED || status == SeatReservationStatus.STARTED) {
          return asSeatReservationFailed();
        } else if (status == SeatReservationStatus.WALLET_REFUNDED) {
          return asSeatReservationRefunded();
        } else {
          throw new IllegalStateException("not supported failed state transition from: " + status);
        }
      }
    }

    public static class ShowCreator {

        public static final BigDecimal INITIAL_PRICE = new BigDecimal("100");

        public static Either<CinemaApiModel.ShowCommandError, ShowEvent.ShowCreated> create(String showId, CinemaApiModel.ShowCommand.CreateShow createShow) {
          //more domain validation here
          if (createShow.maxSeats() > 100) {
            return left(TOO_MANY_SEATS);
          } else {
            var initialShow = new InitialShow(showId, createShow.title(), createSeats(INITIAL_PRICE, createShow.maxSeats()));
            var showCreated = new ShowEvent.ShowCreated(showId, initialShow);
            return right(showCreated);
          }
        }

        public static List<Seat> createSeats(BigDecimal seatPrice, int maxSeats) {
          return IntStream.range(0, maxSeats).mapToObj(seatNum -> new Seat(seatNum, AVAILABLE, seatPrice)).toList();
        }
      }
}

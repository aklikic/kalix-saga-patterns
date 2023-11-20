package com.example.cinema;


import static com.example.cinema.DomainGenerators.randomReservationId;
import static com.example.cinema.DomainGenerators.randomSeatNumber;
import static com.example.cinema.DomainGenerators.randomTitle;
import static com.example.cinema.DomainGenerators.randomWalletId;
import static com.example.cinema.CinemaApiModel.ShowCommand.*;

public class ShowCommandGenerators {

  public static CreateShow randomCreateShow() {
    return new CreateShow(randomTitle(), ShowBuilder.MAX_SEATS);
  }

  public static ReserveSeat randomReserveSeat() {
    return new ReserveSeat(randomWalletId(), randomReservationId(), randomSeatNumber());
  }

  public static CancelSeatReservation randomCancelSeatReservation() {
    return new CancelSeatReservation(randomReservationId());
  }
}

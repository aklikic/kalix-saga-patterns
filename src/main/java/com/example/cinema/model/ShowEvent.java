package com.example.cinema.model;

import kalix.javasdk.annotations.TypeName;

import java.math.BigDecimal;

sealed public interface ShowEvent {
    String showId();

    @TypeName("show-created")
    record ShowCreated(String showId, Show.InitialShow initialShow) implements ShowEvent {
    }

    @TypeName("seat-reserved")
    record SeatReserved(String showId, String walletId, String reservationId, int seatNumber, BigDecimal price,
                        int availableSeatsCount) implements ShowEvent {
    }

    @TypeName("seat-reservation-paid")
    record SeatReservationPaid(String showId, String reservationId, int seatNumber) implements ShowEvent {
    }

    @TypeName("seat-reservation-cancelled")
    record SeatReservationCancelled(String showId, String reservationId, int seatNumber,
                                    int availableSeatsCount) implements ShowEvent {
    }
}

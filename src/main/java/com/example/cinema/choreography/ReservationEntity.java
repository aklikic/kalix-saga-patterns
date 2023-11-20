package com.example.cinema.choreography;

import com.example.cinema.CinemaDomainModel;
import io.grpc.Status;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.valueentity.ValueEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;

@Profile("choreography")
@Id("id")
@TypeId("reservation")
@RequestMapping("/reservation/{id}")
public class ReservationEntity extends ValueEntity<CinemaDomainModel.Reservation> {

  public record CreateReservation(String showId, String walletId, BigDecimal price) {
  }

  @GetMapping
  public Effect<CinemaDomainModel.Reservation> get() {
    if (currentState() == null) {
      return effects().error("reservation not found", Status.Code.NOT_FOUND);
    } else {
      return effects().reply(currentState());
    }
  }

  @PostMapping
  public Effect<String> create(@RequestBody CreateReservation createReservation) {
    String reservationId = commandContext().entityId();
    CinemaDomainModel.Reservation reservation = new CinemaDomainModel.Reservation(reservationId, createReservation.showId, createReservation.walletId, createReservation.price);
    return effects().updateState(reservation).thenReply("reservation created");
  }

  @DeleteMapping
  public Effect<String> delete() {
    return effects().deleteEntity().thenReply("reservation deleted");
  }
}

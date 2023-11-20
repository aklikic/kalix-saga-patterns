package com.example.cinema.choreography;

import com.example.cinema.CinemaDomainModel;
import com.example.cinema.ShowEntity;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;
import kalix.javasdk.view.View;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;

import static com.example.cinema.CinemaDomainModel.ShowEvent.*;
@Profile("choreography")
@ViewId("show_by_reservation_view")
@Table("show_by_reservation")
@Subscribe.EventSourcedEntity(value = ShowEntity.class)
public class ShowByReservationView extends View<CinemaDomainModel.ShowByReservation> {

  @GetMapping("/show/by-reservation-id/{reservationId}")
  @Query("SELECT * FROM show_by_reservation WHERE :reservationId = ANY(reservationIds)")
  public CinemaDomainModel.ShowByReservation getShow(String name) {
    return null;
  }

  public UpdateEffect<CinemaDomainModel.ShowByReservation> onEvent(ShowCreated created) {
    return effects().updateState(new CinemaDomainModel.ShowByReservation(created.showId()));
  }

  public UpdateEffect<CinemaDomainModel.ShowByReservation> onEvent(SeatReserved reserved) {
    return effects().updateState(viewState().add(reserved.reservationId()));
  }

  public UpdateEffect<CinemaDomainModel.ShowByReservation> onEvent(SeatReservationPaid paid) {
    return effects().ignore();
  }

  public UpdateEffect<CinemaDomainModel.ShowByReservation> onEvent(SeatReservationCancelled cancelled) {
    return effects().ignore();
  }

  public UpdateEffect<CinemaDomainModel.ShowByReservation> onEvent(CancelledReservationConfirmed confirmed) {
    return effects().ignore();
  }
}

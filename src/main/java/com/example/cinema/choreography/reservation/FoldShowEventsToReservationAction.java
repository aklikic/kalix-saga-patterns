package com.example.cinema.choreography.reservation;

import com.example.cinema.ShowEntity;
import com.google.protobuf.any.Any;
import kalix.javasdk.DeferredCall;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;

import static com.example.cinema.model.ShowEvent.*;

@Profile("choreography")
@Subscribe.EventSourcedEntity(value = ShowEntity.class, ignoreUnknown = true)
public class FoldShowEventsToReservationAction extends Action {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final ComponentClient componentClient;

  public FoldShowEventsToReservationAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> onEvent(SeatReserved reserved) {
    return effects().forward(createReservation(reserved));
  }

  public Effect<String> onEvent(SeatReservationPaid paid) {
    return effects().forward(deleteReservation(paid.reservationId()));
  }

//  alternatively we can use dedicated event for the cancellation after a failure
//  public Effect<String> onEvent(SeatReservationCancelled cancelled) {
//    return effects().forward(deleteReservation(cancelled.reservationId()));
//  }

  private DeferredCall<Any, String> createReservation(SeatReserved reserved) {
    return componentClient.forValueEntity(reserved.reservationId())
      .call(ReservationEntity::create)
      .params(new ReservationEntity.CreateReservation(reserved.showId(), reserved.walletId(), reserved.price()));
  }

  private DeferredCall<Any, String> deleteReservation(String reservationId) {
    return componentClient.forValueEntity(reservationId).call(ReservationEntity::delete);
  }
}

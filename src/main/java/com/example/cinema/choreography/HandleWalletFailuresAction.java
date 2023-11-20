package com.example.cinema.choreography;

import com.example.cinema.*;
import com.example.cinema.choreography.WalletFailureEntity.WalletChargeFailureOccurred;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.CompletionStage;

@Profile("choreography")
@Subscribe.EventSourcedEntity(value = WalletFailureEntity.class)
public class HandleWalletFailuresAction extends Action {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final ComponentClient componentClient;

  public HandleWalletFailuresAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<CinemaApiModel.Response> handle(WalletChargeFailureOccurred walletChargeFailureOccurred) {
    logger.info("handling failure: " + walletChargeFailureOccurred);

    String reservationId = walletChargeFailureOccurred.source().expenseId();

    return effects().asyncReply(getShowIdBy(reservationId).thenCompose(showId ->
      cancelReservation(reservationId, showId)
    ));
  }

  private CompletionStage<CinemaApiModel.Response> cancelReservation(String reservationId, String showId) {
    return componentClient.forEventSourcedEntity(showId)
      .call(ShowEntity::cancelReservation)
      .params(reservationId)
      .execute();
  }

  private CompletionStage<String> getShowIdBy(String reservationId) {
    return componentClient.forValueEntity(reservationId).call(ReservationEntity::get).execute()
      .thenApply(CinemaDomainModel.Reservation::showId);
  }
}

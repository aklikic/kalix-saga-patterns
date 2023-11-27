package com.example.cinema.choreography;

import com.example.cinema.choreography.reservation.ReservationEntity;
import com.example.cinema.model.CinemaApiModel;
import com.example.cinema.ShowEntity;
import com.example.cinema.model.Show;
import com.example.wallet.WalletEntity;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import static java.nio.charset.StandardCharsets.UTF_8;
import static com.example.cinema.model.ShowEvent.*;
import static com.example.wallet.model.WalletApiModel.WalletCommand.*;

@Profile("choreography")
@Subscribe.EventSourcedEntity(value = ShowEntity.class, ignoreUnknown = true)
public class RefundForReservationAction extends Action {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final ComponentClient componentClient;

  public RefundForReservationAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<CinemaApiModel.Response> refund(CancelledReservationConfirmed cancelledReservationConfirmed) {
    logger.info("refunding for reservation, triggered by " + cancelledReservationConfirmed);

    String sequenceNum = contextForComponents().metadata().get("ce-sequence").orElseThrow();
    String commandId = UUID.nameUUIDFromBytes(sequenceNum.getBytes(UTF_8)).toString();

    return effects().asyncReply(
      getReservation(cancelledReservationConfirmed.reservationId())
              .thenCompose(reservation ->
                refund(reservation.walletId(), reservation.price(), commandId)
              )
    );
  }

  private CompletionStage<Show.Reservation> getReservation(String reservationId) {
    return componentClient.forValueEntity(reservationId)
      .call(ReservationEntity::get)
      .execute();
  }

  private CompletionStage<CinemaApiModel.Response> refund(String walletId, BigDecimal amount, String commandId) {
    return componentClient.forEventSourcedEntity(walletId)
      .call(WalletEntity::deposit)
      .params(new DepositFunds(amount, commandId))
      .execute();
  }
}

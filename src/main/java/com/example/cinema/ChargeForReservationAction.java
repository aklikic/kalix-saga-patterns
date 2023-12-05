package com.example.cinema;

import akka.actor.ActorSystem;
import akka.pattern.Patterns;
import com.example.wallet.WalletEntity;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Duration.ofSeconds;

import static com.example.cinema.model.ShowEvent.*;
import static com.example.wallet.model.WalletApiModel.WalletCommand.*;

@Profile("choreography")
@Subscribe.EventSourcedEntity(value = ShowEntity.class, ignoreUnknown = true)
public class ChargeForReservationAction extends Action {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final ComponentClient componentClient;
  
  public ChargeForReservationAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> charge(SeatReserved seatReserved) {
    logger.info("charging for reservation, triggered by " + seatReserved);
    String expenseId = seatReserved.reservationId();

    String sequenceNum = contextForComponents().metadata().get("ce-sequence").orElseThrow();
    String walletId = seatReserved.walletId();

    String commandId = UUID.nameUUIDFromBytes(sequenceNum.getBytes(UTF_8)).toString();
    var chargeWallet = new ChargeWallet(seatReserved.price(),commandId);

    var attempts = 3;
    var retryDelay = Duration.ofSeconds(1);
    ActorSystem actorSystem = actionContext().materializer().system();

    return effects().asyncReply(
      Patterns.retry(() -> chargeWallet(walletId, expenseId, chargeWallet),
          attempts,
          retryDelay,
          actorSystem)
        .exceptionallyComposeAsync(throwable ->
            registerFailure(seatReserved.showId(), expenseId, throwable)
        )
    );
  }

  private CompletionStage<String> chargeWallet(String walletId, String expenseId, ChargeWallet chargeWallet) {
    return componentClient.forEventSourcedEntity(walletId)
      .call(WalletEntity::charge)
      .params(expenseId, chargeWallet)
      .execute()
      .thenApply(response -> "done");
  }
  private CompletionStage<String> registerFailure(String showId, String reservationId, Throwable throwable) {
    var msg = getMessage(throwable);
    return componentClient.forEventSourcedEntity(showId).call(ShowEntity::cancelReservation).params(reservationId).execute().thenApply(res -> msg);
  }

  private String getMessage(Throwable throwable) {
    if (throwable.getCause() != null &&
      throwable.getCause() instanceof WebClientResponseException.BadRequest badRequest) {
      return badRequest.getStatusCode() + "-" + badRequest.getResponseBodyAsString();
    } else {
      return throwable.getMessage();
    }
  }
}

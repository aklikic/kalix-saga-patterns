package com.example.cinema;

import com.example.cinema.model.CinemaApiModel;
import com.example.cinema.ShowEntity;
import com.example.cinema.model.Show;
import com.example.wallet.WalletEntity;
import com.google.protobuf.any.Any;
import kalix.javasdk.DeferredCall;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.client.ComponentClient;
import kalix.javasdk.workflow.Workflow;
import kalix.javasdk.workflow.Workflow.Effect.TransitionalEffect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static com.example.cinema.model.Show.SeatReservationStatus.STARTED;
import static io.grpc.Status.Code.INVALID_ARGUMENT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static kalix.javasdk.workflow.Workflow.RecoverStrategy.maxRetries;
import static com.example.wallet.model.WalletApiModel.WalletCommand.*;

@Profile("orchestration")
@Id("id")
@TypeId("seat-reservation")
@RequestMapping("/seat-reservation/{id}")
public class SeatReservationWorkflow extends Workflow<Show.SeatReservation> {

  public static final String RESERVE_SEAT_STEP = "reserve-seat";
  public static final String CHARGE_WALLET_STEP = "charge-wallet";
  public static final String CANCEL_RESERVATION_STEP = "cancel-reservation";
  public static final String CONFIRM_RESERVATION_STEP = "confirm-reservation";
  public static final String REFUND_STEP = "refund";
  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private final ComponentClient componentClient;

  public SeatReservationWorkflow(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  record ReserveSeat(String showId, int seatNumber, BigDecimal price, String walletId) {
  }

  @Override
  public WorkflowDef<Show.SeatReservation> definition() {
    var reserveSeat = step(RESERVE_SEAT_STEP)
      .call(this::reserveSeat)
      .andThen(CinemaApiModel.Response.class, this::chargeWalletOrStop);

    var chargeWallet = step(CHARGE_WALLET_STEP)
      .call(this::chargeWallet)
      .andThen(CinemaApiModel.Response.class, this::confirmOrCancelReservation);

    var confirmReservation = step(CONFIRM_RESERVATION_STEP)
      .call(this::confirmReservation)
      .andThen(CinemaApiModel.Response.class, this::endAsCompleted);

    var cancelReservation = step(CANCEL_RESERVATION_STEP)
      .call(this::cancelReservation)
      .andThen(CinemaApiModel.Response.class, this::endAsFailed);

    var refund = step(REFUND_STEP)
      .call(this::refund)
      .andThen(CinemaApiModel.Response.class, this::cancelReservation);

    return workflow()
      .defaultStepTimeout(Duration.ofSeconds(3))
      .addStep(reserveSeat, maxRetries(3).failoverTo(CANCEL_RESERVATION_STEP))
      .addStep(chargeWallet, maxRetries(3).failoverTo(REFUND_STEP))
      .addStep(confirmReservation)
      .addStep(cancelReservation)
      .addStep(refund);
  }

  private DeferredCall<Any, CinemaApiModel.Response> refund() {
    logger.info("refunding");
    //we can't use reservationId for refund, because it was used for charging.
    var commandId = UUID.nameUUIDFromBytes(currentState().reservationId().getBytes(UTF_8)).toString();
    return componentClient.forEventSourcedEntity(currentState().walletId())
      .call(WalletEntity::refund)
      .params(currentState().reservationId(),new Refund(commandId));
  }

  private TransitionalEffect<Void> cancelReservation(CinemaApiModel.Response response) {
    return switch (response) {
      case CinemaApiModel.Response.Failure failure -> throw new IllegalStateException("Expecting successful response, but got: " + failure);
      case CinemaApiModel.Response.Success __ -> effects()
        .updateState(currentState().asWalletRefunded())
        .transitionTo(CANCEL_RESERVATION_STEP);
    };
  }

  private DeferredCall<Any, CinemaApiModel.Response> cancelReservation() {
    logger.info("cancelling reservation");
    return componentClient.forEventSourcedEntity(currentState().showId())
      .call(ShowEntity::cancelReservation)
      .params(currentState().reservationId());
  }

  private TransitionalEffect<Void> endAsFailed(CinemaApiModel.Response response) {
    return switch (response) {
      case CinemaApiModel.Response.Failure failure -> throw new IllegalStateException("Expecting successful response, but got: " + failure);
      case CinemaApiModel.Response.Success __ -> effects()
        .updateState(currentState().asFailed())
        .end();
    };
  }

  private DeferredCall<Any, CinemaApiModel.Response> confirmReservation() {
    logger.info("confirming reservation");
    return componentClient.forEventSourcedEntity(currentState().showId())
      .call(ShowEntity::confirmPayment)
      .params(currentState().reservationId());
  }

  private TransitionalEffect<Void> endAsCompleted(CinemaApiModel.Response response) {
    return switch (response) {
      case CinemaApiModel.Response.Failure failure -> throw new IllegalStateException("Expecting successful response, but got: " + failure);
      case CinemaApiModel.Response.Success __ -> effects()
        .updateState(currentState().asCompleted())
        .end();
    };
  }

  private DeferredCall<Any, CinemaApiModel.Response> chargeWallet() {
    logger.info("charging wallet");
    var expenseId = currentState().reservationId();
    var commandId = expenseId; //reusing the same id, since we know that it will be unique
    return componentClient.forEventSourcedEntity(currentState().walletId())
      .call(WalletEntity::charge)
      .params(expenseId, new ChargeWallet(currentState().price(), commandId));
  }

  private TransitionalEffect<Void> confirmOrCancelReservation(CinemaApiModel.Response response) {
    return switch (response) {
      case CinemaApiModel.Response.Failure failure -> {
        //Here we know that wallet was not charged. We can just cancel reservation as compensation action
        logger.warn("charging wallet failed with: " + failure);
        yield effects()
          .updateState(currentState().asWalletChargeRejected())
          .transitionTo(CANCEL_RESERVATION_STEP);
      }
      case CinemaApiModel.Response.Success __ -> effects()
        .updateState(currentState().asWalletCharged())
        .transitionTo(CONFIRM_RESERVATION_STEP);
    };
  }

  private DeferredCall<Any, CinemaApiModel.Response> reserveSeat() {
    logger.info("reserving seat");
    return componentClient.forEventSourcedEntity(currentState().showId())
      .call(ShowEntity::reserve)
      .params(new CinemaApiModel.ShowCommand.ReserveSeat(currentState().walletId(), currentState().reservationId(), currentState().seatNumber()));
  }

  private TransitionalEffect<Void> chargeWalletOrStop(CinemaApiModel.Response response) {
    return switch (response) {
      case CinemaApiModel.Response.Failure failure -> {
        logger.warn("seat reservation failed with: " + failure);
        yield effects()
          .updateState(currentState().asSeatReservationFailed())
          .end();
      }
      case CinemaApiModel.Response.Success __ -> effects()
        .updateState(currentState().asSeatReserved())
        .transitionTo(CHARGE_WALLET_STEP);
    };
  }

  @PostMapping
  public Effect<String> start(@RequestBody ReserveSeat reserveSeat) {
    if (currentState() != null) {
      return effects().error("seat reservation already exists", INVALID_ARGUMENT);
    } else {
      return effects()
        .updateState(new Show.SeatReservation(reservationId(), reserveSeat.showId, reserveSeat.seatNumber, reserveSeat.walletId, reserveSeat.price, STARTED))
        .transitionTo(RESERVE_SEAT_STEP)
        .thenReply("reservation workflow started");
    }
  }

  private String reservationId() {
    return commandContext().workflowId();
  }

  @GetMapping()
  public Effect<String> getState() {
    if (currentState() == null) {
      return effects().error("seat reservation not found");
    } else {
      return effects().reply(currentState().status().name());
    }
  }
}

package com.example.cinema;

import com.example.cinema.model.CinemaApiModel;
import kalix.javasdk.action.Action;
import kalix.javasdk.client.ComponentClient;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import org.springframework.web.bind.annotation.*;


@RequestMapping("/cinema-show-api")
public class ShowApiControllerAction extends Action {

    private final ComponentClient componentClient;

    public ShowApiControllerAction(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @PostMapping("/{id}/create")
    public Effect<CinemaApiModel.Response> create(@PathVariable String id, @RequestBody CinemaApiModel.ShowCommand.CreateShow createShow) {
        return effects().forward(componentClient.forEventSourcedEntity(id).call(ShowEntity::create).params(id, createShow));
    }

    @PatchMapping("/{id}/reserve")
    public Effect<CinemaApiModel.Response> reserve(@PathVariable String id, @RequestBody CinemaApiModel.ShowCommand.ReserveSeat reserveSeat) {
        return effects().forward(componentClient.forEventSourcedEntity(id).call(ShowEntity::reserve).params(reserveSeat));
    }

    @PatchMapping("/{id}/cancel-reservation/{reservationId}")
    public Effect<CinemaApiModel.Response> cancelReservation(@PathVariable String id, @PathVariable String reservationId) {
        return effects().forward(componentClient.forEventSourcedEntity(id).call(ShowEntity::confirmPayment).params(reservationId));
    }

    @PatchMapping("/{id}/confirm-payment/{reservationId}")
    public Effect<CinemaApiModel.Response> confirmPayment(@PathVariable String id, @PathVariable String reservationId) {
        return effects().forward(componentClient.forEventSourcedEntity(id).call(ShowEntity::confirmPayment).params(reservationId));
    }
    @GetMapping("/{id}/get")
    public Effect<CinemaApiModel.ShowResponse> get(@PathVariable String id) {
        return effects().forward(componentClient.forEventSourcedEntity(id).call(ShowEntity::get));
    }

    @GetMapping("/cinema-shows/by-available-seats/{requestedSeatCount}")
    public Effect<CinemaApiModel.ShowsByAvailableSeatsRecordList> getShowsByAvailableSeats(@PathVariable Integer requestedSeatCount) {
        return effects().forward(componentClient.forView().call(ShowsByAvailableSeatsView::getShows).params(requestedSeatCount));
    }
}

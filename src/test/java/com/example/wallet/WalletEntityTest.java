package com.example.wallet;

import com.example.cinema.model.CinemaApiModel;
import com.example.wallet.model.Wallet;
import com.example.wallet.model.WalletEvent;
import kalix.javasdk.testkit.EventSourcedResult;
import kalix.javasdk.testkit.EventSourcedTestKit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.example.cinema.DomainGenerators.randomWalletId;
import static com.example.wallet.DomainGenerators.randomCommandId;
import static org.assertj.core.api.Assertions.assertThat;

import static com.example.wallet.model.WalletEvent.*;
import static com.example.wallet.model.WalletApiModel.WalletCommand.*;

class WalletEntityTest {


  @Test
  public void shouldCreateWallet() {
    //given
    var walletId = randomWalletId();
    var initialAmount = 100;
    EventSourcedTestKit<Wallet, WalletEvent, WalletEntity> testKit = EventSourcedTestKit.of(WalletEntity::new);

    //when
    EventSourcedResult<CinemaApiModel.Response> result = testKit.call(wallet -> wallet.create(walletId, initialAmount));

    //then
    assertThat(result.isReply()).isTrue();
    assertThat(result.getNextEventOfType(WalletCreated.class).initialAmount()).isEqualTo(BigDecimal.valueOf(initialAmount));
    assertThat(testKit.getState().id()).isEqualTo(walletId);
    assertThat(testKit.getState().balance()).isEqualTo(BigDecimal.valueOf(initialAmount));
  }

  @Test
  public void shouldChargeWallet() {
    //given
    var walletId = randomWalletId();
    var expenseId = "r1";
    var initialAmount = 100;
    EventSourcedTestKit<Wallet, WalletEvent, WalletEntity> testKit = EventSourcedTestKit.of(WalletEntity::new);
    testKit.call(wallet -> wallet.create(walletId, initialAmount));
    var chargeWallet = new ChargeWallet(new BigDecimal(10),  randomCommandId());

    //when
    EventSourcedResult<CinemaApiModel.Response> result = testKit.call(wallet -> wallet.charge(expenseId, chargeWallet));

    //then
    assertThat(result.isReply()).isTrue();
    assertThat(result.getNextEventOfType(WalletCharged.class)).isEqualTo(new WalletCharged(walletId, chargeWallet.amount(), expenseId, chargeWallet.commandId()));
    assertThat(testKit.getState().balance()).isEqualTo(new BigDecimal(90));
  }

  @Test
  public void shouldIgnoreChargeDuplicate() {
    //given
    var walletId = randomWalletId();
    var expenseId = "r1";
    var initialAmount = 100;
    EventSourcedTestKit<Wallet, WalletEvent, WalletEntity> testKit = EventSourcedTestKit.of(WalletEntity::new);
    testKit.call(wallet -> wallet.create(walletId, initialAmount));
    var chargeWallet = new ChargeWallet(new BigDecimal(10), randomCommandId());
    testKit.call(wallet -> wallet.charge(expenseId, chargeWallet));

    //when
    EventSourcedResult<CinemaApiModel.Response> result = testKit.call(wallet -> wallet.charge(expenseId, chargeWallet));

    //then
    assertThat(result.isReply()).isTrue();
    assertThat(result.didEmitEvents()).isFalse();
    assertThat(testKit.getState().balance()).isEqualTo(new BigDecimal(90));
  }

  @Test
  public void shouldRefundWallet() {
    //given
    var walletId = randomWalletId();
    var expenseId = "r1";
    var initialAmount = 100;
    EventSourcedTestKit<Wallet, WalletEvent, WalletEntity> testKit = EventSourcedTestKit.of(WalletEntity::new);
    testKit.call(wallet -> wallet.create(walletId, initialAmount));
    var chargeWallet = new ChargeWallet(new BigDecimal(10), randomCommandId());
    testKit.call(wallet -> wallet.charge(expenseId, chargeWallet));

    //when
    EventSourcedResult<CinemaApiModel.Response> result = testKit.call(wallet -> wallet.charge(expenseId, chargeWallet));

    //then
    assertThat(result.isReply()).isTrue();
    assertThat(result.didEmitEvents()).isFalse();
    assertThat(testKit.getState().balance()).isEqualTo(new BigDecimal(90));
  }
}
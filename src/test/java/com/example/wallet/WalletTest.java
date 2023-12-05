package com.example.wallet;

import com.example.wallet.model.Wallet;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.example.wallet.DomainGenerators.randomCommandId;
import static com.example.wallet.model.WalletApiModel.WalletCommandError.DUPLICATED_COMMAND;
import static com.example.wallet.model.WalletApiModel.WalletCommandError.WALLET_ALREADY_EXISTS;
import static com.example.wallet.model.WalletApiModel.WalletCommand.*;
import static org.assertj.core.api.Assertions.assertThat;

class WalletTest {

  @Test
  public void shouldCreateWallet() {
    //given
    var wallet = Wallet.EMPTY_WALLET;
    var createWallet = new CreateWallet("1", BigDecimal.TEN);

    //when
    var event = wallet.handleCreate(createWallet).get();
    var updatedWallet = wallet.apply(event);

    //then
    assertThat(updatedWallet.id()).isEqualTo(createWallet.walletId());
    assertThat(updatedWallet.balance()).isEqualTo(createWallet.initialAmount());
  }

  @Test
  public void shouldRejectCommandIfWalletExists() {
    //given
    var wallet = new Wallet("1", BigDecimal.TEN);
    var createWallet = new CreateWallet("1", BigDecimal.TEN);

    //when
    var error = wallet.handleCreate(createWallet).getLeft();

    //then
    assertThat(error).isEqualTo(WALLET_ALREADY_EXISTS);
  }

  @Test
  public void shouldChargeWallet() {
    //given
    var wallet = new Wallet("1", BigDecimal.TEN);
    var chargeWallet = new ChargeWallet(BigDecimal.valueOf(3), randomCommandId());

    //when
    var event = wallet.handleCharge("abc", chargeWallet).get();
    var updatedWallet = wallet.apply(event);

    //then
    assertThat(updatedWallet.balance()).isEqualTo(BigDecimal.valueOf(7));
  }

  @Test
  public void shouldRejectDuplicatedCharge() {
    //given
    var wallet = new Wallet("1", BigDecimal.TEN);
    var chargeWallet = new ChargeWallet(BigDecimal.valueOf(3), randomCommandId());

    var event = wallet.handleCharge("abc", chargeWallet).get();
    var updatedWallet = wallet.apply(event);

    //when
    var error = updatedWallet.handleCharge("abc", chargeWallet).getLeft();

    //then
    assertThat(error).isEqualTo(DUPLICATED_COMMAND);
  }
}
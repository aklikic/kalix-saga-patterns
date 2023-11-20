package com.example.wallet;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Either;
import kalix.javasdk.annotations.TypeName;

import java.math.BigDecimal;
import java.util.function.Supplier;

import static com.example.wallet.WalletApiModel.*;
import static com.example.wallet.WalletApiModel.WalletCommand.*;
import static com.example.wallet.WalletApiModel.WalletCommandError.*;
import static io.vavr.control.Either.left;
import static io.vavr.control.Either.right;

public interface WalletDomainModel {

    sealed interface WalletEvent {

      @TypeName("wallet-created")
      record WalletCreated(String walletId, BigDecimal initialAmount) implements WalletEvent {
      }

      @TypeName("wallet-charged")
      record WalletCharged(String walletId, BigDecimal amount, String expenseId, String commandId) implements WalletEvent {
      }

      @TypeName("wallet-refunded")
      record WalletRefunded(String walletId, BigDecimal amount, String expenseId, String commandId) implements WalletEvent {
      }

      @TypeName("funds-deposited")
      record FundsDeposited(String walletId, BigDecimal amount, String commandId) implements WalletEvent {
      }

      @TypeName("wallet-charge-rejected")
      record WalletChargeRejected(String walletId, String expenseId, String commandId) implements WalletEvent {
      }
    }

    record Expense(String expenseId, BigDecimal amount) {
    }

    record Wallet(String id, BigDecimal balance, Map<String, Expense> expenses, Set<String> commandIds) {

      public Wallet(String id, BigDecimal balance) {
        this(id, balance, HashMap.empty(), HashSet.empty());
      }

      public static final String EMPTY_WALLET_ID = "";
      public static Wallet EMPTY_WALLET = new Wallet(EMPTY_WALLET_ID, BigDecimal.ZERO, HashMap.empty(), HashSet.empty());

      public Either<WalletCommandError, WalletEvent> process(WalletCommand command) {
        if (isDuplicate(command)) {
          return Either.left(DUPLICATED_COMMAND);
        } else {
          return switch (command) {
            case CreateWallet create -> handleCreate(create);
            case ChargeWallet charge -> ifExists(() -> handleCharge(charge));
            case Refund refund -> ifExists(() -> handleRefund(refund));
            case DepositFunds depositFunds -> ifExists(() -> handleDeposit(depositFunds));
          };
        }
      }

      private boolean isDuplicate(WalletCommand command) {
        if (command instanceof RequiresDeduplicationCommand c) {
          return commandIds.contains(c.commandId());
        } else {
          return false;
        }
      }

      private Either<WalletCommandError, WalletEvent> ifExists(Supplier<Either<WalletCommandError, WalletEvent>> processingResultSupplier) {
        if (isEmpty()) {
          return left(WALLET_NOT_FOUND);
        } else {
          return processingResultSupplier.get();
        }
      }

      private Either<WalletCommandError, WalletEvent> handleCreate(CreateWallet createWallet) {
        if (isEmpty()) {
          return right(new WalletDomainModel.WalletEvent.WalletCreated(createWallet.walletId(), createWallet.initialAmount()));
        } else {
          return left(WALLET_ALREADY_EXISTS);
        }
      }

      private Either<WalletCommandError, WalletEvent> handleDeposit(DepositFunds depositFunds) {
        if (depositFunds.amount().compareTo(BigDecimal.ZERO) <= 0) {
          return left(DEPOSIT_LE_ZERO);
        } else {
          return right(new WalletDomainModel.WalletEvent.FundsDeposited(id, depositFunds.amount(), depositFunds.commandId()));
        }
      }

      private Either<WalletCommandError, WalletEvent> handleCharge(ChargeWallet charge) {
        if (balance.compareTo(charge.amount()) < 0) {
          return right(new WalletDomainModel.WalletEvent.WalletChargeRejected(id, charge.expenseId(), charge.commandId()));
        } else {
          return right(new WalletDomainModel.WalletEvent.WalletCharged(id, charge.amount(), charge.expenseId(), charge.commandId()));
        }
      }

      private Either<WalletCommandError, WalletEvent> handleRefund(Refund refund) {
        return expenses.get(refund.expenseId()).fold(
          () -> left(EXPENSE_NOT_FOUND),
          expense -> right(new WalletDomainModel.WalletEvent.WalletRefunded(id, expense.amount(), expense.expenseId(), refund.commandId()))
        );
      }

      public Wallet apply(WalletEvent event) {
        return switch (event) {
          case WalletDomainModel.WalletEvent.WalletCreated walletCreated -> new Wallet(walletCreated.walletId(), walletCreated.initialAmount(), expenses, commandIds);
          case WalletDomainModel.WalletEvent.WalletCharged charged -> {
            Expense expense = new Expense(charged.expenseId(), charged.amount());
            yield new Wallet(id, balance.subtract(charged.amount()), expenses.put(expense.expenseId(), expense), commandIds.add(charged.commandId()));
          }
          case WalletDomainModel.WalletEvent.WalletRefunded refunded ->
            new Wallet(id, balance.add(refunded.amount()), expenses.remove(refunded.expenseId()), commandIds.add(refunded.commandId()));
          case WalletDomainModel.WalletEvent.FundsDeposited deposited -> new Wallet(id, balance.add(deposited.amount()), expenses, commandIds.add(deposited.commandId()));
          case WalletDomainModel.WalletEvent.WalletChargeRejected __ -> this;
        };
      }

      public boolean isEmpty() {
        return id.equals(EMPTY_WALLET_ID);
      }
    }

}

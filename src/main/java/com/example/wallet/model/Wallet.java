package com.example.wallet.model;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Either;

import java.math.BigDecimal;
import java.util.function.Supplier;

import static com.example.wallet.model.WalletApiModel.WalletCommandError.*;
import static io.vavr.control.Either.left;
import static io.vavr.control.Either.right;

public record Wallet(String id, BigDecimal balance, Map<String, Expense> expenses,
                     Set<String> commandIds) {

    public Wallet(String id, BigDecimal balance) {
        this(id, balance, HashMap.empty(), HashSet.empty());
    }

    public static final String EMPTY_WALLET_ID = "";
    public static Wallet EMPTY_WALLET = new Wallet(EMPTY_WALLET_ID, BigDecimal.ZERO, HashMap.empty(), HashSet.empty());

//    public Either<WalletApiModel.WalletCommandError, WalletEvent> process(String expenseId, WalletApiModel.WalletCommand command) {
//        if (isDuplicate(command)) {
//            return Either.left(DUPLICATED_COMMAND);
//        } else {
//            return switch (command) {
//                case WalletApiModel.WalletCommand.CreateWallet create -> handleCreate(create);
//                case WalletApiModel.WalletCommand.ChargeWallet charge -> ifExists(() -> handleCharge(expenseId,charge));
//                case WalletApiModel.WalletCommand.Refund refund -> ifExists(() -> handleRefund(expenseId,refund));
//                case WalletApiModel.WalletCommand.DepositFunds depositFunds ->
//                        ifExists(() -> handleDeposit(depositFunds));
//            };
//        }
//    }

    private boolean isDuplicate(WalletApiModel.WalletCommand command) {
        if (command instanceof WalletApiModel.WalletCommand.RequiresDeduplicationCommand c) {
            return commandIds.contains(c.commandId());
        } else {
            return false;
        }
    }

    private Either<WalletApiModel.WalletCommandError, WalletEvent> ifExists(Supplier<Either<WalletApiModel.WalletCommandError, WalletEvent>> processingResultSupplier) {
        if (isEmpty()) {
            return left(WALLET_NOT_FOUND);
        } else {
            return processingResultSupplier.get();
        }
    }

    public Either<WalletApiModel.WalletCommandError, WalletEvent> handleCreate(WalletApiModel.WalletCommand.CreateWallet createWallet) {
        if (isDuplicate(createWallet)) {
            return Either.left(DUPLICATED_COMMAND);
        } else {
            if (isEmpty()) {
                return right(new WalletEvent.WalletCreated(createWallet.walletId(), createWallet.initialAmount()));
            } else {
                return left(WALLET_ALREADY_EXISTS);
            }
        }
    }

    public Either<WalletApiModel.WalletCommandError, WalletEvent> handleCharge(String expenseId, WalletApiModel.WalletCommand.ChargeWallet charge) {
        if (isDuplicate(charge)) {
            return Either.left(DUPLICATED_COMMAND);
        } else {
            if (balance.compareTo(charge.amount()) < 0) {
                return right(new WalletEvent.WalletChargeRejected(id, expenseId, charge.commandId()));
            } else {
                return right(new WalletEvent.WalletCharged(id, charge.amount(), expenseId, charge.commandId()));
            }
        }
    }

    public Either<WalletApiModel.WalletCommandError, WalletEvent> handleRefund(String expenseId, WalletApiModel.WalletCommand.Refund refund) {
        return expenses.get(expenseId).fold(
                () -> left(EXPENSE_NOT_FOUND),
                expense -> right(new WalletEvent.WalletRefunded(id, expense.amount(), expense.expenseId(), refund.commandId()))
        );
    }

    public Wallet apply(WalletEvent event) {
        return switch (event) {
            case WalletEvent.WalletCreated walletCreated ->
                    new Wallet(walletCreated.walletId(), walletCreated.initialAmount(), expenses, commandIds);
            case WalletEvent.WalletCharged charged -> {
                Expense expense = new Expense(charged.expenseId(), charged.amount());
                yield new Wallet(id, balance.subtract(charged.amount()), expenses.put(expense.expenseId(), expense), commandIds.add(charged.commandId()));
            }
            case WalletEvent.WalletRefunded refunded ->
                    new Wallet(id, balance.add(refunded.amount()), expenses.remove(refunded.expenseId()), commandIds.add(refunded.commandId()));
//            case WalletEvent.FundsDeposited deposited ->
//                    new Wallet(id, balance.add(deposited.amount()), expenses, commandIds.add(deposited.commandId()));
            case WalletEvent.WalletChargeRejected __ -> this;
        };
    }

    public boolean isEmpty() {
        return id.equals(EMPTY_WALLET_ID);
    }

    public static final record Expense(String expenseId, BigDecimal amount) {
      }
}

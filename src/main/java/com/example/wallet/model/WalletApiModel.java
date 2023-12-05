package com.example.wallet.model;

import com.example.wallet.model.Wallet;

import java.math.BigDecimal;

public interface WalletApiModel {
    sealed interface WalletCommand {

        sealed interface RequiresDeduplicationCommand extends WalletCommand {
            String commandId();
        }

        record CreateWallet(String walletId, BigDecimal initialAmount) implements WalletCommand {
        }

        record ChargeWallet(BigDecimal amount/*, String expenseId*/, String commandId) implements RequiresDeduplicationCommand {
        }

        record Refund(/*String expenseId,*/ String commandId) implements RequiresDeduplicationCommand {
        }

//        record DepositFunds(BigDecimal amount, String commandId) implements RequiresDeduplicationCommand {
//        }
    }

    enum WalletCommandError {
        WALLET_ALREADY_EXISTS, WALLET_NOT_FOUND, NOT_SUFFICIENT_FUNDS, DEPOSIT_LE_ZERO, DUPLICATED_COMMAND, EXPENSE_NOT_FOUND
    }

    record WalletResponse(String id, BigDecimal balance) {
      public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(wallet.id(), wallet.balance());
      }
    }
}

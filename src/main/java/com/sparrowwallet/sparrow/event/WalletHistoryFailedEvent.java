package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.wallet.Wallet;

public class WalletHistoryFailedEvent extends WalletHistoryStatusEvent {
    private final Throwable exception;

    public WalletHistoryFailedEvent(Wallet wallet, Throwable exception) {
        super(wallet, exception.getCause() == null ? exception.getMessage() : exception.getCause().getMessage());
        this.exception = exception;
    }

    public Throwable getException() {
        return exception;
    }
}

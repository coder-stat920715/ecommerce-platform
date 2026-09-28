package com.shop.payment.psp;

/**
 * DEFERRED = we chose not to call the PSP (circuit open); UNKNOWN = the call may or may not have gone through.
 * In both cases the intent stays PROCESSING and the reconciliation job resolves it. Never fail on a timeout.
 */
public record PspChargeResult(Status status, String pspReference, String failureCode) {

    public enum Status { SUCCEEDED, DECLINED, DEFERRED, UNKNOWN }

    public static PspChargeResult deferred() { return new PspChargeResult(Status.DEFERRED, null, "CIRCUIT_OPEN"); }

    public static PspChargeResult unknown() { return new PspChargeResult(Status.UNKNOWN, null, "OUTCOME_UNKNOWN"); }

    public static PspChargeResult declined(String code) { return new PspChargeResult(Status.DECLINED, null, code); }
}

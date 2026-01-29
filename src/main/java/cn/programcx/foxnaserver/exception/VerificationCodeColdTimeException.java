package cn.programcx.foxnaserver.exception;

public class VerificationCodeColdTimeException extends RuntimeException {
    public VerificationCodeColdTimeException(String message) {
        super(message);
    }

    public VerificationCodeColdTimeException(Long secLeft) {
        super(" Please wait " + secLeft + " seconds before requesting a new code.");
    }
}

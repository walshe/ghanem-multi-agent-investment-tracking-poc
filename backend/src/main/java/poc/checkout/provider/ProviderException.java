package poc.checkout.provider;

/**
 * <b>Responsibility:</b> signal that a call to the provider didn't work: it couldn't be reached, it
 * timed out, or it gave an unexpected answer. Callers treat it as "try again later".
 */
public class ProviderException extends RuntimeException {

    public ProviderException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProviderException(String message) {
        super(message);
    }
}

package poc.checkout.provider;

/** The provider could not be reached or gave an unexpected answer. */
public class ProviderException extends RuntimeException {

    public ProviderException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProviderException(String message) {
        super(message);
    }
}

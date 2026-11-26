package ch.bonflow.masterdata;

/** The import file is not usable; the message is meant for the user. Rolls the import back. */
public class InvalidTransferException extends RuntimeException {

    public InvalidTransferException(String message) {
        super(message);
    }

    public InvalidTransferException(String message, Throwable cause) {
        super(message, cause);
    }
}

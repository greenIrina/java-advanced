package info.kgeorgiy.ja.tkachenko.walk;

public class WalkException extends Exception {
    WalkException(String message) {
        super(message);
    }

    WalkException(String message, Exception e) {
        super(message + ": " + e.getMessage());
    }
}

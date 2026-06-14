package top.egon.mario.agent.model.api;

/**
 * Raised when a model cannot be resolved from the upstream request.
 */
public class ModelFactoryException extends RuntimeException {

    public ModelFactoryException(String message) {
        super(message);
    }

}

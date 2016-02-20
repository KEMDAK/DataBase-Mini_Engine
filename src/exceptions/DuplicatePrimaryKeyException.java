package exceptions;

public class DuplicatePrimaryKeyException extends DBEngineException{

	public DuplicatePrimaryKeyException() {
		super("The givin primary key has to be unique.");
	}
}

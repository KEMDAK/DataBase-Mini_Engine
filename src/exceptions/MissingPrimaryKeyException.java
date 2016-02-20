package exceptions;

public class MissingPrimaryKeyException extends DBEngineException{

	public MissingPrimaryKeyException() {
		super("The entered data is missing the primary key.");
	}
}

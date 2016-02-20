package exceptions;

public class TypeMismatchException extends DBEngineException {

	public TypeMismatchException() {
		super("The entered data types mismatch the table's data types.");
	}
}

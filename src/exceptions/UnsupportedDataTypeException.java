package exceptions;

public class UnsupportedDataTypeException extends DBEngineException {

	public UnsupportedDataTypeException(String dateType) {
		super(dateType + " is Unsupported as a data type.");
	}
}

package exceptions;

public class NotPrimaryKeyException extends DBEngineException {
	
	public NotPrimaryKeyException(String tableName, String colName) {
		super(tableName + "." + colName + " is not the primary key.");
	}
}

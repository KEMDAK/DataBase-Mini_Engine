package exceptions;

public class DuplicateTableNameException extends DBEngineException{

	public DuplicateTableNameException(String TableName) {
		super(TableName + " already exists in the database.");
	}
}

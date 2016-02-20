package exceptions;

public class TableNotFoundException extends DBEngineException{

	public TableNotFoundException(String TableName) {
		super(TableName + " doesn't exists in the database.");
	}
}

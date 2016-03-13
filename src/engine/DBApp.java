package engine;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TreeMap;

import exceptions.DBAppException;
import exceptions.DBEngineException;
import exceptions.DuplicatePrimaryKeyException;
import exceptions.DuplicateTableNameException;
import exceptions.MissingPrimaryKeyException;
import exceptions.NotPrimaryKeyException;
import exceptions.TableNotFoundException;
import exceptions.TypeMismatchException;
import exceptions.UnsupportedDataTypeException;

public class DBApp {

	private TreeMap<String, Table> tables;
	private static int MaximumRowsCountinPage;
	private static int BPlusTreeN;

	public void init(){
		try {
			Properties properties = new Properties();
			properties.load(new FileReader("config/DBApp.properties"));

			MaximumRowsCountinPage = Integer.parseInt(properties.getProperty("MaximumRowsCountinPage"));
			BPlusTreeN = Integer.parseInt(properties.getProperty("BPlusTreeN"));

			boolean exists = new File("data/tables.class").exists();

			if(exists){
				ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(new File("data/tables.class")));
				tables = (TreeMap<String, Table>) objectInputStream.readObject();
				objectInputStream.close();
			}
			else{
				tables = new TreeMap<String, Table>();
			}

		} catch (FileNotFoundException e) {
			System.err.println("tables.class not found");
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void createTable(String strTableName,    Hashtable<String,String> htblColNameType, 
			Hashtable<String,String> htblColNameRefs, String strKeyColName)  throws DBAppException{

		htblColNameType.put("TouchDate", "Date");

		try {
			if(tables.containsKey(strTableName))
				throw new DuplicateTableNameException(strTableName);

			PrintWriter out = new PrintWriter(new FileWriter("data/metadata.csv", true));

			StringBuilder sb = new StringBuilder();
			for (Entry<String, String> entry : htblColNameType.entrySet()) {
				sb.append(strTableName);
				sb.append(",");
				sb.append(entry.getKey());
				sb.append(",");
				sb.append(getClass(entry.getValue()));
				sb.append(",");
				sb.append((strKeyColName.equals(entry.getKey())) + "");
				sb.append(",");

				//not considering compounded primary key

				if(entry.getKey().equals(strKeyColName)){
					createIndex(strTableName, strKeyColName);
					sb.append(true + "");
				}
				else{
					sb.append(false + "");
				}
				sb.append(",");

				String ref = htblColNameRefs.get(entry.getKey());
				if(ref != null){
					StringTokenizer st = new StringTokenizer(ref, ".");
					String refTableName = st.nextToken();
					String refTableColumn = st.nextToken();
					if (!tables.containsKey(refTableName))
						throw new TableNotFoundException("The Referenced Table " + refTableName);
					Table refTable = tables.get(refTableName);
					if (!refTable.getPrimarykey().equals(refTableColumn))
						throw new NotPrimaryKeyException(refTableName, refTableColumn);
				}

				sb.append(ref + "");
				sb.append("\n");
			}

			out.write(sb.toString());

			Table table = new Table(strTableName, strKeyColName);
			tables.put(strTableName, table);

			saveTables();

			out.close();
		} catch (FileNotFoundException e) {
			System.err.println("metadata.csv not found...!");
			e.printStackTrace();
		}
		catch(UnsupportedDataTypeException e){
			System.err.println(e.getMessage());
			e.printStackTrace();
		} catch (DuplicateTableNameException e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (TableNotFoundException e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
		} catch (NotPrimaryKeyException e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
		}
	}

	public void createIndex(String strTableName, String strColName)  throws DBAppException{
		try {
			if (!tables.containsKey(strTableName))
				throw new TableNotFoundException(strTableName);

			Table table = tables.get(strTableName);

			BPlusTree tree = new BPlusTree(BPlusTreeN);
			
			int totalNumberOfPages = (table.getNextFree() / DBApp.getMaximumRowsCountinPage()) + 1;
			if(table.getNextFree() % DBApp.getMaximumRowsCountinPage() == 0)
				totalNumberOfPages--;

			for (int i = 0; i < totalNumberOfPages; i++) {
				Page page = table.loadPage(i);

				for (int j = 0; j < page.getRows().length; j++) {
					Row row = page.getRows()[j];
					if(row == null)
						continue;
					
					tree.insert(row.getValues().get(strColName), (strTableName + "_" + i + ".class"), j);
					
				}
			}
			
			
		} catch (TableNotFoundException e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
		}
	}

	public void insertIntoTable(String strTableName, Hashtable<String,Object> htblColNameValue) throws DBAppException, DBEngineException {
		try {
			if(!tables.containsKey(strTableName))
				throw new TableNotFoundException(strTableName);
			if(!checkTable(strTableName, htblColNameValue))
				throw new TypeMismatchException();

			Hashtable<String, Object> key = new Hashtable<>();
			String PK = tables.get(strTableName).getPrimarykey();
			Object PKValue = htblColNameValue.get(PK);
			if(PKValue == null)
				throw new MissingPrimaryKeyException();

			key.put(PK, PKValue);
			if(((RowIterator) selectFromTable(strTableName, key, "OR")).size() != 0)
				throw new DuplicatePrimaryKeyException();

			// NOT considering Integrity constraints 

			Table table = tables.get(strTableName);
			table.addRecord(htblColNameValue);

			saveTables();
		} catch (TableNotFoundException e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
		} catch (TypeMismatchException e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
		} catch (MissingPrimaryKeyException e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
		} catch (DuplicatePrimaryKeyException e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
		}
	}

	public void updateTable(String strTableName, Object strKey,
			Hashtable<String,Object> htblColNameValue) {
		try {
			if(!tables.containsKey(strTableName))
				throw new TableNotFoundException(strTableName);
			if(!checkTable(strTableName, htblColNameValue))
				throw new TypeMismatchException();

			Table table = tables.get(strTableName);

			table.updateRecord(strKey, htblColNameValue);

		} catch (TableNotFoundException e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
		} catch (TypeMismatchException e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
		}
	}

	public void deleteFromTable(String strTableName, Hashtable<String,Object> htblColNameValue, 
			String strOperator) {
		try {
			if (!tables.containsKey(strTableName))
				throw new TableNotFoundException(strTableName);
			if(!checkTable(strTableName, htblColNameValue))
				throw new TypeMismatchException();

			Table table = tables.get(strTableName);
			table.deleteRecord(htblColNameValue, strOperator);
		} catch (TableNotFoundException e) {
			e.printStackTrace();
		} catch(TypeMismatchException e) {
			e.printStackTrace();
		}
	}

	public Iterator selectFromTable(String strTable,  Hashtable<String,Object> htblColNameValue, 
			String strOperator) {
		try {
			if (!tables.containsKey(strTable))
				throw new TableNotFoundException(strTable);
			if(!checkTable(strTable, htblColNameValue))
				throw new TypeMismatchException();

			Table table = tables.get(strTable);
			return table.selectRecords(htblColNameValue, strOperator);

		} catch(TableNotFoundException e) {
			e.printStackTrace();
		} catch(TypeMismatchException e) {
			e.printStackTrace();
		}

		return null;
	}


	public boolean checkTable(String strTableName, Hashtable<String,Object> htblColNameValue){
		if (htblColNameValue == null)
			return true;
		try {
			BufferedReader in = new BufferedReader(new FileReader("data/metadata.csv"));

			ArrayList<String> columns = new ArrayList<>();

			while(in.ready()){
				String line = in.readLine();

				StringTokenizer s = new StringTokenizer(line, ",");

				String tableName = s.nextToken();
				String columnName = s.nextToken();
				String columnType = s.nextToken();
				s.nextToken();
				s.nextToken();
				s.nextToken();

				if(tableName.equals(strTableName)){
					columns.add(columnName);
					columns.add(columnType);
				}
			}

			for (Entry<String, Object> entry : htblColNameValue.entrySet()) {
				String colName = entry.getKey();
				Object colValue = entry.getValue();

				boolean valid = false;
				for (int i = 0; i < columns.size() && !valid; i += 2) {
					String name = columns.get(i);
					String type = columns.get(i+1);

					if (name.equals(colName) && sameType(type, colValue))
						valid = true;
				}

				if (!valid) {
					in.close();
					return false;
				}
			}

			in.close();
			return true;

		} catch (FileNotFoundException e) {
			System.err.println("metadata.csv not found...!");
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}

	public void saveTables() {
		try {
			ObjectOutputStream objectInputStream = new ObjectOutputStream(new FileOutputStream(new File("data/tables.class")));
			objectInputStream.writeObject(tables);
			objectInputStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}    	
	}

	public static boolean sameType(String dataType, Object target){
		if(target == null)
			return true;

		if(dataType.contains("Integer"))
			return target instanceof Integer;
		else if(dataType.contains("String"))
			return target instanceof String;
		else if(dataType.contains("Double"))
			return target instanceof Double;
		else if(dataType.contains("Boolean"))
			return target instanceof Boolean;
		else if(dataType.contains("Date"))
			return target instanceof Date;

		return false;
	}

	public static String getClass(String className) throws UnsupportedDataTypeException{
		if(className.equals("String"))
			return String.class + "";
		else if(className.equals("Integer"))
			return Integer.class + "";
		else if(className.equals("Double"))
			return Double.class + "";
		else if(className.equals("Boolean"))
			return Boolean.class + "";
		else if(className.equals("Date"))
			return Date.class + "";

		throw new UnsupportedDataTypeException(className);
	}

	public static int getMaximumRowsCountinPage() {
		return MaximumRowsCountinPage;
	}

	public static int getBPlusTreeN() {
		return BPlusTreeN;
	}
}

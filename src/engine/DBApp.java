package engine;
import java.awt.image.BufferedImage;
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

			createIndex(strTableName, strKeyColName);
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

			saveIndex(tree, ("indices/" + strTableName + "::" + strColName + ".class"));


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

			int pageNumber = table.getNextFree() / DBApp.getMaximumRowsCountinPage();
			int index = table.getNextFree() % DBApp.getMaximumRowsCountinPage();

			table.addRecord(htblColNameValue);

			for (Entry<String, Object> entry : htblColNameValue.entrySet()) {
				String colName = entry.getKey();
				Object value = entry.getValue();

				BPlusTree tree = loadIndex("indices/" + strTableName + "::" + colName + ".class");

				if(tree == null)
					continue;

				tree.insert(value, (strTableName + "_" + pageNumber + ".class"), index);				

				saveIndex(tree, ("indices/" + strTableName + "::" + colName + ".class"));
			}


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
			BPlusTree tree = loadIndex("indices/" + strTableName + "::" + table.getPrimarykey() + ".class");

			ArrayList<Record> records = tree.find(strKey);
			Hashtable<String, Object> oldValues = null;
			for (Record record : records) {
				if(record == null)
					continue;

				oldValues = table.updateRecordImmediate(record.getPageName(), record.getIndex(), strKey, htblColNameValue);
			}

			for (Entry<String, Object> entry : htblColNameValue.entrySet()) {
				String name = entry.getKey();
				Object value = entry.getValue();

				tree = loadIndex("indices/" + strTableName + "::" + name + ".class");

				if (tree != null) {
					records = tree.find(oldValues.get(name));
					tree.delete(oldValues.get(name));

					for (Record record : records) {
						if(record == null)
							continue;
						tree.insert(value, record.getPageName(), record.getIndex());
					}

					saveIndex(tree, ("indices/" + strTableName + "::" + name + ".class"));
				}
			}

		} catch (TableNotFoundException e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
		} catch (TypeMismatchException e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
		}
	}

	public ArrayList<Row> getMatchingRows(String strTableName, Hashtable<String, Object> htblColNameValue, String strOperator) {
		ArrayList<Row> result = new ArrayList<>();

		Table table = tables.get(strTableName);
		for (Entry<String, Object> entry : htblColNameValue.entrySet()) {
			String colName = entry.getKey();
			Object value = entry.getValue();

			ArrayList<Row> acc = new ArrayList<>();
			ArrayList<Row> temp = new ArrayList<>();
			BPlusTree tree = loadIndex("indices/" + (strTableName + "::" + colName + ".class"));
			if (tree != null) { // we have an index for this column
				ArrayList<Record> rec = tree.find(value);

				for (int i = 0; i < rec.size(); i++) {
					Record cur = rec.get(i);

					if (cur == null) continue;
					StringTokenizer st = new StringTokenizer(cur.getPageName(), "_");
					st.nextToken();
					st = new StringTokenizer(st.nextToken(), ".");

					int pageNumber = Integer.parseInt(st.nextToken());

					Page page = table.loadPage(pageNumber);

					Row row = page.getRows()[cur.getIndex()];

					temp.add(row);
				}

			}
			else {
				int totalNumberOfPages = (table.getNextFree() / DBApp.getMaximumRowsCountinPage()) + 1;
				if(table.getNextFree() % DBApp.getMaximumRowsCountinPage() == 0)
					totalNumberOfPages--;

				for (int i = 0; i < totalNumberOfPages; i++) {
					Page page = table.loadPage(i);

					for (Row row : page.getRows()) {
						if(row == null)
							continue;

						if (Table.equalObject(value, row.getValues().get(colName))) 
							temp.add(row);
					}
				}
			}

			if (result.isEmpty()) 
				acc = temp;
			else {
				if (strOperator.equals("AND")) {
					for (int i = 0; i < temp.size(); i++) {
						Row cur = temp.get(i);
						boolean valid = false;
						for (int j = 0; j < result.size() && !valid; j++) {
							Row r = result.get(j);
							if (cur.equals(r))
								valid = true;
						}

						if (valid)
							acc.add(cur);
					}
				}
				else {
					acc = result;
					for (int i = 0; i < temp.size(); i++) {
						Row cur = temp.get(i);
						boolean valid = true;
						for (int j = 0; j < result.size() && valid; j++) {
							Row r = result.get(j);
							if (cur.equals(r))
								valid = false;
						}

						if (valid)
							acc.add(cur);
					}
				}
			}

			result = acc;
		}

		return result;

	}

	public void deleteFromTable(String strTableName, Hashtable<String,Object> htblColNameValue, 
			String strOperator) {
		try {
			if (!tables.containsKey(strTableName))
				throw new TableNotFoundException(strTableName);
			if(!checkTable(strTableName, htblColNameValue))
				throw new TypeMismatchException();

			ArrayList<Row> result = getMatchingRows(strTableName, htblColNameValue, strOperator);

			Table table = tables.get(strTableName);
			for (Row row : result) {

				for (Entry<String, Object> entry : row.getValues().entrySet()) {
					String colName = entry.getKey();
					Object colValue = entry.getValue();

					BPlusTree tree = loadIndex("indices/" + (strTableName + "::" + colName + ".class"));
					if (tree != null) { // we have an index for this column
						tree.delete(colValue);

						for (Object o : tree.getLastDeleted()) {
							Record record = (Record) o;

							table.deleteRecord(record);
						}

						saveIndex(tree, "indices/" + (strTableName + "::" + colName + ".class"));
					}

				}
			}

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

			ArrayList<Row> result = getMatchingRows(strTable, htblColNameValue, strOperator);

			RowIterator it = new RowIterator();
			it.addAll(result);

			return it;

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
			e.printStackTrace();
		}

		return false;
	}

	public BPlusTree loadIndex(String indexName) {
		BPlusTree result = null;

		try {
			boolean exists = new File(indexName).exists();

			if(exists){
				ObjectInputStream objectInputStream;
				objectInputStream = new ObjectInputStream(new FileInputStream(new File(indexName)));
				result = (BPlusTree) objectInputStream.readObject();
				objectInputStream.close();
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

		return result;
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

	public void saveIndex(BPlusTree tree, String name) {
		try {
			ObjectOutputStream objectInputStream = new ObjectOutputStream(new FileOutputStream(new File(name)));
			objectInputStream.writeObject(tree);
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

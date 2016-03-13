package engine;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Map.Entry;

public class Table implements Comparable<Table>, Serializable {

	private String tableName;
	private int nextFree;
	private String primarykey;

	public Table(String tableName, String primarykey) {
		this.tableName = tableName;
		this.primarykey = primarykey;
		nextFree = 0;
	}

	public Page loadPage(int pageNumber){
		try {
			ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(new File("data/" + tableName + "_" + pageNumber + ".class")));
			Page page = (Page) objectInputStream.readObject();
			objectInputStream.close();
			return page;
		} catch (FileNotFoundException e) {
			System.err.println(tableName + "_" + pageNumber + ".class not found");
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return null;
	}

	public void updatePage(Page page){
		try {
			ObjectOutputStream objectInputStream = new ObjectOutputStream(new FileOutputStream(new File("data/" + page.getPageName() + ".class")));
			objectInputStream.writeObject(page);
			objectInputStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void addRecord(Hashtable<String, Object> values){
		values.put("TouchDate", new Date());

		int pageNumber = nextFree / DBApp.getMaximumRowsCountinPage();
		int index = nextFree % DBApp.getMaximumRowsCountinPage();

		Page curPage;
		if(index != 0)
			curPage = loadPage(pageNumber);
		else
			curPage = new Page(tableName + "_" + pageNumber);

		curPage.addRecord(index, values);

		updatePage(curPage);

		nextFree++;
	}


	public void updateRecord(Object strKey, Hashtable<String,Object> htblColNameValue){
		int totalNumberOfPages = (nextFree / DBApp.getMaximumRowsCountinPage()) + 1;
		if(nextFree % DBApp.getMaximumRowsCountinPage() == 0)
			totalNumberOfPages--;

		for (int i = 0; i < totalNumberOfPages; i++) {
			Page page = loadPage(i);

			boolean found = false;

			for (Row row : page.getRows()) {
				if(row == null)
					continue;

				if(equalObject(strKey, row.getValues().get(primarykey))){
					for (Entry<String, Object> entry : htblColNameValue.entrySet()) {
						row.getValues().put(entry.getKey(), entry.getValue());
					}

					row.getValues().put("TouchDate", new Date());

					found = true;
				}
			}

			if(found){
				updatePage(page);
			}
		}
	}


	public void updateRecordImmediate(String pageName, int index, Object strKey, Hashtable<String,Object> htblColNameValue){
		StringTokenizer s = new StringTokenizer(pageName, "_");
		s.nextToken();
		s = new StringTokenizer(s.nextToken(), ".");
		int pageNumber = Integer.parseInt(s.nextToken());

		Page page = loadPage(pageNumber);

		Row row = page.getRows()[index];

		if(equalObject(strKey, row.getValues().get(primarykey))){
			for (Entry<String, Object> entry : htblColNameValue.entrySet()) {
				row.getValues().put(entry.getKey(), entry.getValue());
			}

			row.getValues().put("TouchDate", new Date());

			updatePage(page);
		}
	}

	public void deleteRecord(Record record) {	
		StringTokenizer st = new StringTokenizer(record.getPageName(), "_");
		st.nextToken();
		st = new StringTokenizer(st.nextToken(), ".");
		int pageNumber = Integer.parseInt(st.nextToken());
		
		Page page = loadPage(pageNumber);
		page.getRows()[record.getIndex()] = null;
		
		updatePage(page);
	}


	public RowIterator selectRecords(Hashtable<String,Object> htblColNameValue, String strOperator){
		int totalNumberOfPages = (nextFree / DBApp.getMaximumRowsCountinPage()) + 1;
		if(nextFree % DBApp.getMaximumRowsCountinPage() == 0)
			totalNumberOfPages--;

		RowIterator result = new RowIterator();

		for (int i = 0; i < totalNumberOfPages; i++) {
			Page page = loadPage(i);

			for (Row row : page.getRows()) {
				if(row == null)
					continue;

				ArrayList<Boolean> truthValues = new ArrayList<>();

				if (htblColNameValue != null) {
					for (Entry<String, Object> entry : htblColNameValue.entrySet()) {
						truthValues.add(equalObject(entry.getValue(), row.getValues().get(entry.getKey())));
					}
				}

				if(evaluate(truthValues, strOperator))
					result.addRow(row);
			}
		}

		return result;
	}

	@Override
	public boolean equals(Object o) {
		return this.tableName.equalsIgnoreCase(((Table) o).tableName);
	}

	@Override
	public int compareTo(Table o) {
		return this.tableName.compareTo(o.tableName);
	}

	public static boolean evaluate(ArrayList<Boolean> truthValues, String operator){
		if (truthValues.isEmpty()) 
			return true;
		if(operator.equals("OR")){
			boolean res = false;

			for (Boolean value : truthValues) {
				res |= value;

				if(res)
					break;
			}

			return res;
		}
		else{
			boolean res = true;

			for (Boolean value : truthValues) {
				res &= value;

				if(!res)
					break;
			}

			return res;
		}
	}

	public static boolean equalObject(Object x, Object y){
		if(!x.getClass().equals(y.getClass()))
			return false;

		if(x instanceof Integer)
			return ((Integer) x).equals((Integer) y);
		if(x instanceof String)
			return ((String) x).equals((String) y);
		if(x instanceof Double)
			return ((Double) x).equals((Double) y);
		if(x instanceof Boolean)
			return ((Boolean) x).equals((Boolean) y);
		if(x instanceof Date)
			return ((Date) x).equals((Date) y);

		return false;
	}

	@Override
	public String toString() {
		return "Table name: " + tableName + ", " + "index: " + nextFree;
	}

	public String getPrimarykey() {
		return primarykey;
	}

	public int getNextFree() {
		return nextFree;
	}
}

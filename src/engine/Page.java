package engine;
import java.io.Serializable;
import java.util.Hashtable;

public class Page implements Serializable {

	private String pageName;
	private Row[] rows;

	public Page(String pageName) {
		this.pageName = pageName;
		rows = new Row[DBApp.getMaximumRowsCountinPage()];
	}
	
	public void addRecord(int index, Hashtable<String, Object> values){
		rows[index] = new Row(values);
	}

	public String getPageName() {
		return pageName;
	}

	public Row[] getRows() {
		return rows;
	}
	
	
}

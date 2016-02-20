package engine;

import java.util.ArrayList;
import java.util.Iterator;

public class RowIterator implements Iterator<Row>{
	
	private ArrayList<Row> result;
	private int totalSize, curIndex;
	
	public RowIterator() {
		this.result = new ArrayList<>();
		this.totalSize = 0;
		this.curIndex = 0;
	}
	
	public void addRow(Row row){
		result.add(row);
		totalSize++;
	}
	
	public int size(){
		return totalSize;
	}

	@Override
	public boolean hasNext() {
		if(curIndex < totalSize && totalSize > 0)
			return true;
		
		return false;
	}

	@Override
	public Row next() {
		return result.get(curIndex++);
	}

}

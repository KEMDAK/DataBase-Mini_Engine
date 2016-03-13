package engine;

import java.io.Serializable;

public class Record implements Serializable{
	String pageName;
	int index;
	
	public Record(String pageName, int index) {
		this.pageName = pageName;
		this.index = index;
	}
	
	@Override
	public boolean equals(Object o) {
		Record r = (Record) o;
		return (pageName.equals(r.pageName) && index == r.index);
	}
	
	@Override
	public String toString() {
		return "(" + pageName + ", " + index + ")";
	}
}


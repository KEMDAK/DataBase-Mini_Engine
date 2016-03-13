package engine;
import java.io.Serializable;
import java.util.Date;
import java.util.Hashtable;
import java.util.Map.Entry;

public class Row implements Serializable {

	private Hashtable<String, Object> values;

	public Row(Hashtable<String, Object> values) {
		this.values = values;
	}

	public Hashtable<String, Object> getValues() {
		return values;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("{");
		int i = 0;
		for (Entry<String, Object> entry : values.entrySet()) {
			if (i > 0)
				sb.append(", ");
			sb.append("\"" + entry.getKey() + "\" : \"" + toStringObject(entry.getValue()) + "\"");
			i++;
		}

		sb.append("}");
		return sb.toString();
	}

	public static String toStringObject(Object o){
		if(o instanceof Integer)
			return ((Integer) o).toString();
		if(o instanceof String)
			return ((String) o).toString();
		if(o instanceof Double)
			return ((Double) o).toString();
		if(o instanceof Boolean)
			return ((Boolean) o).toString();
		if(o instanceof Date)
			return ((Date) o).toString();

		return "Presentation error";
	}

	@Override
	public boolean equals(Object o) {
		Row other = (Row) o;
		for (Entry<String, Object> entry : values.entrySet()) {
			String colName = entry.getKey();
			Object colValue = entry.getValue();
			
			if (other.getValues().get(colName) == null || !(colValue.equals(other.getValues().get(colName))))
				return false;
		}
		
		return true;
	}
}

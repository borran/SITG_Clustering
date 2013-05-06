package sitg;

import java.util.Comparator;
import java.util.Map;

public class TopTermComparator implements Comparator {

	@Override
	public int compare(Object arg1, Object arg2) {
		// TODO Auto-generated method stub
		int result = 0;
		Map.Entry e1 = (Map.Entry) arg1;
		Map.Entry e2 = (Map.Entry) arg2;
		double value1 = (Double) e1.getValue();
		double value2 = (Double) e2.getValue();
		if (value1 == value2) {
			String key1 = (String) e1.getKey();
			String key2 = (String) e2.getKey();
			result = key1.compareToIgnoreCase(key2);
		}
		else {
			result = Double.compare(value2, value1);
		}
		return result;
	}

	
}

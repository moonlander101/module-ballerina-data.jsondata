package io.ballerina.lib.data.jsondata.utils;

import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;

public class JsonEqualityUtils {
    
    public static boolean deepEquals(Object value1, Object value2) {
//        System.out.println("Comparing: " + value1 + " and " + value2);
        if (value1 == value2) {
            return true;
        }
        
        if (value1 == null || value2 == null) {
            return false;
        }

        if (value1 instanceof BMap<?, ?> map1 && value2 instanceof BMap<?, ?> map2) {
            return mapsEqual((BMap<BString, Object>) map1, (BMap<BString, Object>) map2);
        }
        
        if (value1 instanceof BArray array1 && value2 instanceof BArray array2) {
            return arraysEqual(array1, array2);
        }

        Class<?> class1 = value1.getClass();
        Class<?> class2 = value2.getClass();

        if (class1 != class2) {
            if (class1 == Integer.class && class2 == Long.class) {
                return ((Integer) value1).longValue() == (Long) value2;
            } else if (class1 == Long.class && class2 == Integer.class) {
                return (Long) value1 == ((Integer) value2).longValue();
            }
            if (class1 == Long.class && class2 == Double.class) {
                return ((Long) value1).doubleValue() == (Double) value2;
            } else if (class1 == Double.class && class2 == Long.class) {
                return (Double) value1 == ((Long) value2).doubleValue();
            }

            return false;
        }
        return value1.equals(value2);
    }
    
    private static boolean mapsEqual(BMap<BString, Object> map1, BMap<BString, Object> map2) {
        if (map1.size() != map2.size()) {
            return false;
        }
        
        for (BString key : map1.getKeys()) {
            if (!map2.containsKey(key)) {
                return false;
            }
            
            Object value1 = map1.get(key);
            Object value2 = map2.get(key);
            
            if (!deepEquals(value1, value2)) {
                return false;
            }
        }
        
        return true;
    }
    
    private static boolean arraysEqual(BArray array1, BArray array2) {
        int length1 = array1.size();
        int length2 = array2.size();
        
        if (length1 != length2) {
            return false;
        }
        
        for (int i = 0; i < length1; i++) {
            Object element1 = array1.get(i);
            Object element2 = array2.get(i);
            
            if (!deepEquals(element1, element2)) {
                return false;
            }
        }
        
        return true;
    }
}

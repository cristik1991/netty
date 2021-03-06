package com.cristik.netty.proxy.mysql.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;


/**
 * @author cristik
 */
public class ParameterMapping {

    private static final Logger logger = LoggerFactory.getLogger(ParameterMapping.class);
    private static final Map<Class<?>, PropertyDescriptor[]> descriptors = new HashMap<Class<?>, PropertyDescriptor[]>();

    public static void mapping(Object object, Map<String, ? extends Object> parameter)
            throws IllegalAccessException, InvocationTargetException {
        PropertyDescriptor[] pds = getDescriptors(object.getClass());
        for (int i = 0; i < pds.length; i++) {
            PropertyDescriptor pd = pds[i];
            Object obj = parameter.get(pd.getName());
            Object value = obj;
            Class<?> cls = pd.getPropertyType();
            if (obj instanceof String) {
                String string = (String) obj;
                if (!isNullOrEmpty(string)) {
                    string = filter(string);
                }
                if (isPrimitiveType(cls)) {
                    value = convert(cls, string);
                }
            }
            if (cls != null) {
                if (value != null) {
                    Method method = pd.getWriteMethod();
                    if (method != null) {
                        method.invoke(object, new Object[] { value });
                    }
                }
            }
        }
    }
    
    private static PropertyDescriptor[] getDescriptors(Class<?> clazz) {
        PropertyDescriptor[] pds;
        List<PropertyDescriptor> list;
        PropertyDescriptor[] pds2 = descriptors.get(clazz);
        if (null == pds2) {
            try {
                BeanInfo beanInfo = Introspector.getBeanInfo(clazz);
                pds = beanInfo.getPropertyDescriptors();
                list = new ArrayList<>();
                for (int i = 0; i < pds.length; i++) {
                    if (null != pds[i].getPropertyType()) {
                        list.add(pds[i]);
                    }
                }
                pds2 = new PropertyDescriptor[list.size()];
                list.toArray(pds2);
            } catch (IntrospectionException ie) {
                logger.error("ParameterMappingError", ie);
                pds2 = new PropertyDescriptor[0];
            }
        }
        descriptors.put(clazz, pds2);
        return (pds2);
    }
    
    private static Object convert(Class<?> cls, String string) {
        Method method;
        Object value = null;
        if (cls.equals(String.class)) {
            value = string;
        } else if (cls.equals(Boolean.TYPE)) {
            value = Boolean.valueOf(string);
        } else if (cls.equals(Byte.TYPE)) {
            value = Byte.valueOf(string);
        } else if (cls.equals(Short.TYPE)) {
            value = Short.valueOf(string);
        } else if (cls.equals(Integer.TYPE)) {
            value = Integer.valueOf(string);
        } else if (cls.equals(Long.TYPE)) {
            value = Long.valueOf(string);
        } else if (cls.equals(Double.TYPE)) {
            value = Double.valueOf(string);
        } else if (cls.equals(Float.TYPE)) {
            value = Float.valueOf(string);
        } else if ((cls.equals(Boolean.class)) || (cls.equals(Byte.class)) || (cls.equals(Short.class))
                || (cls.equals(Integer.class)) || (cls.equals(Long.class)) || (cls.equals(Float.class))
                || (cls.equals(Double.class))) {
            try {
                method = cls.getMethod("valueOf", new Class[] { String.class });
                value = method.invoke(null, new Object[] { string });
            } catch (Exception t) {
                logger.error("valueofError", t);
                value = null;
            }
        } else if (cls.equals(Class.class)) {
            try {
                value = Class.forName(string);
            } catch (ClassNotFoundException e) {
                logger.error("valueofError", e);
            }
        } else {
            value = null;
        }
        return (value);
    }
    
    private static boolean isPrimitiveType(Class<?> cls) {
        if (cls.equals(String.class) || cls.equals(Boolean.TYPE) || cls.equals(Byte.TYPE) || cls.equals(Short.TYPE)
                || cls.equals(Integer.TYPE) || cls.equals(Long.TYPE) || cls.equals(Double.TYPE)
                || cls.equals(Float.TYPE) || cls.equals(Boolean.class) || cls.equals(Byte.class)
                || cls.equals(Short.class) || cls.equals(Integer.class) || cls.equals(Long.class)
                || cls.equals(Float.class) || cls.equals(Double.class) || cls.equals(Class.class)) {
            return true;
        } else {
            return false;
        }
    }
    
    private static boolean isNullOrEmpty(String str) {
        return (str == null || str.isEmpty());
    } 
    
    public static String filter(String text) {
        return filter(text, System.getProperties());
    }

    public static String filter(String text, Properties properties) {
        StringBuilder s = new StringBuilder();
        int cur = 0;
        int textLen = text.length();
        int propStart;
        int propStop;
        String propName;
        String propValue;
        for (; cur < textLen; cur = propStop + 1) {
            propStart = text.indexOf("${", cur);
            if (propStart < 0) {
                break;
            }
            s.append(text, cur, propStart);
            propStop = text.indexOf("}", propStart);
            if (propStop < 0) {
                throw new IllegalAccessError("Unterminated property: " + text.substring(propStart));
            }
            propName = text.substring(propStart + 2, propStop);
            propValue = properties.getProperty(propName);
            if (propValue == null) {
                s.append("${").append(propName).append('}');
            } else {
                s.append(propValue);
            }
        }
        return s.append(text.substring(cur)).toString();
    }
	
}

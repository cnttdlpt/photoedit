package org.json;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Triển khai {@code org.json.JSONObject} thuần Java dùng cho bản máy tính Windows Desktop.
 */
public class JSONObject {

    public static final Object NULL = new Object() {
        @Override
        public boolean equals(Object o) {
            return o == this || o == null;
        }

        @Override
        public String toString() {
            return "null";
        }
    };

    private final LinkedHashMap<String, Object> map = new LinkedHashMap<>();

    public JSONObject() {
    }

    public JSONObject(String source) throws JSONException {
        JSONObject parsed = new JSONTokener(source).parseObject();
        this.map.putAll(parsed.map);
    }

    public JSONObject(Map<?, ?> copyFrom) {
        if (copyFrom != null) {
            for (Map.Entry<?, ?> entry : copyFrom.entrySet()) {
                if (entry.getKey() != null) {
                    map.put(String.valueOf(entry.getKey()), wrap(entry.getValue()));
                }
            }
        }
    }

    static Object wrap(Object o) {
        if (o == null || o == NULL) {
            return NULL;
        }
        if (o instanceof JSONObject || o instanceof JSONArray
                || o instanceof Boolean || o instanceof Number || o instanceof String) {
            return o;
        }
        if (o instanceof Collection) {
            return new JSONArray((Collection<?>) o);
        }
        if (o.getClass().isArray()) {
            try {
                return new JSONArray(o);
            } catch (JSONException ignored) {
            }
        }
        if (o instanceof Map) {
            return new JSONObject((Map<?, ?>) o);
        }
        return String.valueOf(o);
    }

    public int length() {
        return map.size();
    }

    public boolean has(String key) {
        return map.containsKey(key) && map.get(key) != NULL;
    }

    public Iterator<String> keys() {
        return map.keySet().iterator();
    }

    public JSONObject put(String name, Object value) throws JSONException {
        if (name == null) {
            throw new JSONException("Null key");
        }
        if (value == null) {
            map.remove(name);
        } else {
            map.put(name, wrap(value));
        }
        return this;
    }

    public JSONObject put(String name, boolean value) throws JSONException {
        return put(name, Boolean.valueOf(value));
    }

    public JSONObject put(String name, int value) throws JSONException {
        return put(name, Integer.valueOf(value));
    }

    public JSONObject put(String name, long value) throws JSONException {
        return put(name, Long.valueOf(value));
    }

    public JSONObject put(String name, double value) throws JSONException {
        return put(name, Double.valueOf(value));
    }

    public Object get(String name) throws JSONException {
        Object result = map.get(name);
        if (result == null) {
            throw new JSONException("No value for " + name);
        }
        return result;
    }

    public Object opt(String name) {
        return map.get(name);
    }

    public String getString(String name) throws JSONException {
        Object object = get(name);
        if (object == NULL) {
            throw new JSONException("Value is null for " + name);
        }
        return String.valueOf(object);
    }

    public String optString(String name) {
        return optString(name, "");
    }

    public String optString(String name, String fallback) {
        Object object = opt(name);
        if (object == null || object == NULL) {
            return fallback;
        }
        return String.valueOf(object);
    }

    public boolean getBoolean(String name) throws JSONException {
        Object object = get(name);
        if (object instanceof Boolean) {
            return (Boolean) object;
        }
        String s = String.valueOf(object);
        if ("true".equalsIgnoreCase(s)) return true;
        if ("false".equalsIgnoreCase(s)) return false;
        throw new JSONException("Not a boolean: " + name);
    }

    public boolean optBoolean(String name) {
        return optBoolean(name, false);
    }

    public boolean optBoolean(String name, boolean fallback) {
        try {
            return getBoolean(name);
        } catch (JSONException e) {
            return fallback;
        }
    }

    public double getDouble(String name) throws JSONException {
        Object object = get(name);
        if (object instanceof Number) {
            return ((Number) object).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(object));
        } catch (NumberFormatException e) {
            throw new JSONException("Not a double: " + name, e);
        }
    }

    public double optDouble(String name, double fallback) {
        try {
            return getDouble(name);
        } catch (JSONException e) {
            return fallback;
        }
    }

    public long getLong(String name) throws JSONException {
        Object object = get(name);
        if (object instanceof Number) {
            return ((Number) object).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(object));
        } catch (NumberFormatException e) {
            throw new JSONException("Not a long: " + name, e);
        }
    }

    public long optLong(String name) {
        return optLong(name, 0L);
    }

    public long optLong(String name, long fallback) {
        try {
            return getLong(name);
        } catch (JSONException e) {
            return fallback;
        }
    }

    public JSONArray getJSONArray(String name) throws JSONException {
        Object object = get(name);
        if (object instanceof JSONArray) {
            return (JSONArray) object;
        }
        throw new JSONException("Not a JSONArray: " + name);
    }

    public JSONArray optJSONArray(String name) {
        Object object = opt(name);
        return object instanceof JSONArray ? (JSONArray) object : null;
    }

    public JSONObject getJSONObject(String name) throws JSONException {
        Object object = get(name);
        if (object instanceof JSONObject) {
            return (JSONObject) object;
        }
        throw new JSONException("Not a JSONObject: " + name);
    }

    public JSONObject optJSONObject(String name) {
        Object object = opt(name);
        return object instanceof JSONObject ? (JSONObject) object : null;
    }

    @Override
    public String toString() {
        return toString(0, 0);
    }

    public String toString(int indentSpaces) {
        return toString(indentSpaces, 0);
    }

    String toString(int indentSpaces, int depth) {
        if (map.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            if (indentSpaces > 0) {
                sb.append('\n');
                indent(sb, (depth + 1) * indentSpaces);
            }
            sb.append(quote(entry.getKey()));
            sb.append(indentSpaces > 0 ? ": " : ":");
            sb.append(valueToString(entry.getValue(), indentSpaces, depth + 1));
        }
        if (indentSpaces > 0) {
            sb.append('\n');
            indent(sb, depth * indentSpaces);
        }
        sb.append('}');
        return sb.toString();
    }

    static void indent(StringBuilder sb, int count) {
        for (int i = 0; i < count; i++) {
            sb.append(' ');
        }
    }

    public static String quote(String s) {
        if (s == null) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        sb.append('"');
        return sb.toString();
    }

    static String valueToString(Object value, int indentSpaces, int depth) {
        if (value == null || value == NULL) {
            return "null";
        }
        if (value instanceof JSONObject) {
            return ((JSONObject) value).toString(indentSpaces, depth);
        }
        if (value instanceof JSONArray) {
            return ((JSONArray) value).toString(indentSpaces, depth);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return quote(String.valueOf(value));
    }
}

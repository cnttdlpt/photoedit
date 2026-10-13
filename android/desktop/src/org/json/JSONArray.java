package org.json;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Triển khai {@code org.json.JSONArray} thuần Java dùng cho bản máy tính Windows Desktop.
 */
public class JSONArray {

    private final List<Object> values = new ArrayList<>();

    public JSONArray() {
    }

    public JSONArray(String source) throws JSONException {
        JSONArray parsed = new JSONTokener(source).parseArray();
        this.values.addAll(parsed.values);
    }

    public JSONArray(Collection<?> copyFrom) {
        if (copyFrom != null) {
            for (Object item : copyFrom) {
                values.add(JSONObject.wrap(item));
            }
        }
    }

    public JSONArray(Object array) throws JSONException {
        if (array == null || !array.getClass().isArray()) {
            throw new JSONException("Not a primitive array");
        }
        int length = Array.getLength(array);
        for (int i = 0; i < length; i++) {
            put(JSONObject.wrap(Array.get(array, i)));
        }
    }

    public int length() {
        return values.size();
    }

    public JSONArray put(Object value) {
        values.add(JSONObject.wrap(value));
        return this;
    }

    public Object get(int index) throws JSONException {
        if (index < 0 || index >= values.size()) {
            throw new JSONException("Index " + index + " out of range [0.." + values.size() + ")");
        }
        return values.get(index);
    }

    public Object opt(int index) {
        if (index < 0 || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }

    public String getString(int index) throws JSONException {
        Object o = get(index);
        if (o == JSONObject.NULL) {
            throw new JSONException("Null at index " + index);
        }
        return String.valueOf(o);
    }

    public String optString(int index) {
        return optString(index, "");
    }

    public String optString(int index, String fallback) {
        Object o = opt(index);
        if (o == null || o == JSONObject.NULL) {
            return fallback;
        }
        return String.valueOf(o);
    }

    public JSONObject getJSONObject(int index) throws JSONException {
        Object o = get(index);
        if (o instanceof JSONObject) {
            return (JSONObject) o;
        }
        throw new JSONException("Not a JSONObject at index " + index);
    }

    @Override
    public String toString() {
        return toString(0, 0);
    }

    public String toString(int indentSpaces) {
        return toString(indentSpaces, 0);
    }

    String toString(int indentSpaces, int depth) {
        if (values.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            if (indentSpaces > 0) {
                sb.append('\n');
                JSONObject.indent(sb, (depth + 1) * indentSpaces);
            }
            sb.append(JSONObject.valueToString(values.get(i), indentSpaces, depth + 1));
        }
        if (indentSpaces > 0) {
            sb.append('\n');
            JSONObject.indent(sb, depth * indentSpaces);
        }
        sb.append(']');
        return sb.toString();
    }
}

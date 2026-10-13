package org.json;

/**
 * Bộ phân tích cú pháp JSON nhẹ dùng cho bản máy tính Windows Desktop (tương thích hoàn toàn với org.json của Android).
 */
final class JSONTokener {
    private final String src;
    private int pos;

    JSONTokener(String src) {
        this.src = src != null ? src : "";
        this.pos = 0;
        if (this.src.startsWith("\uFEFF")) {
            this.pos = 1;
        }
    }

    private void skipWhitespace() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                pos++;
            } else {
                break;
            }
        }
    }

    char nextClean() throws JSONException {
        skipWhitespace();
        if (pos >= src.length()) {
            return 0;
        }
        return src.charAt(pos++);
    }

    void back() {
        if (pos > 0) {
            pos--;
        }
    }

    Object nextValue() throws JSONException {
        char c = nextClean();
        switch (c) {
            case 0:
                throw new JSONException("Unexpected end of JSON input");
            case '{':
                back();
                return parseObject();
            case '[':
                back();
                return parseArray();
            case '"':
            case '\'':
                return nextString(c);
            default:
                back();
                return nextLiteralOrNumber();
        }
    }

    JSONObject parseObject() throws JSONException {
        JSONObject obj = new JSONObject();
        char first = nextClean();
        if (first != '{') {
            throw new JSONException("JSONObject text must begin with '{' at pos " + pos);
        }
        while (true) {
            char c = nextClean();
            if (c == 0) {
                throw new JSONException("JSONObject text must end with '}'");
            }
            if (c == '}') {
                return obj;
            }
            back();
            Object keyObj = nextValue();
            String key = String.valueOf(keyObj);
            char sep = nextClean();
            if (sep != ':') {
                throw new JSONException("Expected ':' after key at pos " + pos);
            }
            Object val = nextValue();
            obj.put(key, val);
            char after = nextClean();
            if (after == ',') {
                char peek = nextClean();
                if (peek == '}') {
                    return obj;
                }
                back();
            } else if (after == '}') {
                return obj;
            } else {
                throw new JSONException("Expected ',' or '}' at pos " + pos);
            }
        }
    }

    JSONArray parseArray() throws JSONException {
        JSONArray arr = new JSONArray();
        char first = nextClean();
        if (first != '[') {
            throw new JSONException("JSONArray text must begin with '[' at pos " + pos);
        }
        char c = nextClean();
        if (c == ']') {
            return arr;
        }
        back();
        while (true) {
            arr.put(nextValue());
            char after = nextClean();
            if (after == ',') {
                char peek = nextClean();
                if (peek == ']') {
                    return arr;
                }
                back();
            } else if (after == ']') {
                return arr;
            } else {
                throw new JSONException("Expected ',' or ']' at pos " + pos);
            }
        }
    }

    private String nextString(char quote) throws JSONException {
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos++);
            if (c == quote) {
                return sb.toString();
            }
            if (c == '\\') {
                if (pos >= src.length()) {
                    throw new JSONException("Unterminated escape sequence");
                }
                char esc = src.charAt(pos++);
                switch (esc) {
                    case '"':
                    case '\'':
                    case '\\':
                    case '/':
                        sb.append(esc);
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        if (pos + 4 > src.length()) {
                            throw new JSONException("Invalid unicode escape");
                        }
                        String hex = src.substring(pos, pos + 4);
                        pos += 4;
                        sb.append((char) Integer.parseInt(hex, 16));
                        break;
                    default:
                        sb.append(esc);
                        break;
                }
            } else {
                sb.append(c);
            }
        }
        throw new JSONException("Unterminated string at pos " + pos);
    }

    private Object nextLiteralOrNumber() throws JSONException {
        int start = pos;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (",:]}/\\\"[{;=# \t\r\n".indexOf(c) >= 0) {
                break;
            }
            pos++;
        }
        String token = src.substring(start, pos).trim();
        if (token.isEmpty()) {
            throw new JSONException("Missing value at pos " + pos);
        }
        if ("true".equalsIgnoreCase(token)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(token)) {
            return Boolean.FALSE;
        }
        if ("null".equalsIgnoreCase(token)) {
            return JSONObject.NULL;
        }
        try {
            if (token.indexOf('.') >= 0 || token.indexOf('e') >= 0 || token.indexOf('E') >= 0) {
                double d = Double.parseDouble(token);
                return Double.valueOf(d);
            } else {
                long l = Long.parseLong(token);
                if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                    return Integer.valueOf((int) l);
                }
                return Long.valueOf(l);
            }
        } catch (NumberFormatException ignored) {
            return token;
        }
    }
}

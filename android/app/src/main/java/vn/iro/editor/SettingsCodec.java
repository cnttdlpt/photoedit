package vn.iro.editor;
import org.json.*;
import java.lang.reflect.Field;

public final class SettingsCodec {
    public static JSONObject encode(Settings s)throws Exception{JSONObject j=new JSONObject();for(Field f:Settings.class.getFields())j.put(f.getName(),f.get(s));return j;}
    public static Settings decode(JSONObject j)throws Exception{
        Settings s=new Settings();for(Field f:Settings.class.getFields()){
            String key=f.getName();if(!j.has(key))continue;
            if(f.getType()==String.class){String v=j.getString(key);if(v.length()>300)throw new JSONException("Giá trị quá dài: "+key);f.set(s,v);continue;}
            double v=j.getDouble(key);double low=-4,high=4;
            switch(key){case "exposure":low=-3;high=3;break;case "red":case "blue":low=-9;high=9;break;case "temperature":low=-100;high=100;break;case "highlight":case "shadow":low=-2;high=4;break;case "clarity":low=-5;high=5;break;case "lutAmount":low=0;high=100;break;case "grain":low=0;high=30;break;case "grainSize":low=1;high=2;break;case "colorChrome":case "chromeBlue":low=0;high=2;break;case "dr":if(v!=100&&v!=200&&v!=400)throw new JSONException("DR không hợp lệ");low=100;high=400;break;}
            if(!Double.isFinite(v)||v<low||v>high)throw new JSONException("Ngoài phạm vi: "+key);
            if(f.getType()==int.class)f.setInt(s,(int)v);else f.setDouble(s,v);
        }return s;
    }
}

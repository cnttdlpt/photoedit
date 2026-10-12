package vn.iro.editor;
import org.json.*;
import java.util.*;
import java.util.regex.*;

/** Raw source values are retained, including ranges and capture-only settings. */
public final class Recipe {
    public String id,name,film,sourceUrl,sensors;
    public final LinkedHashMap<String,String> raw=new LinkedHashMap<>();
    public Recipe(JSONObject j)throws JSONException{
        id=j.optString("id","custom");name=j.optString("name","Recipe tùy chỉnh");film=j.optString("film","Original");sourceUrl=j.optString("sourceUrl","");
        JSONArray ss=j.optJSONArray("sensors");StringBuilder sb=new StringBuilder();if(ss!=null)for(int i=0;i<ss.length();i++){if(i>0)sb.append(" / ");sb.append(ss.optString(i));}sensors=sb.toString();
        JSONObject r=j.getJSONObject("rawSettings");if(r.length()>60)throw new JSONException("Recipe có quá nhiều trường");
        Iterator<String> keys=r.keys();while(keys.hasNext()){String k=keys.next();String v=r.getString(k);if(k.length()>80||v.length()>500)throw new JSONException("Thông số quá dài");raw.put(k,v);}
    }
    public JSONObject json()throws JSONException{JSONObject j=new JSONObject();j.put("id",id);j.put("name",name);j.put("film",film);j.put("sourceUrl",sourceUrl);j.put("sensors",new JSONArray(sensors.isEmpty()?new String[0]:sensors.split(" / ")));j.put("rawSettings",new JSONObject(raw));return j;}
    public String description(){StringBuilder b=new StringBuilder(name+"\n"+sensors+"\n\nFilm Simulation: "+film+"\n");for(Map.Entry<String,String> e:raw.entrySet())b.append(e.getKey()).append(": ").append(e.getValue()).append('\n');return b.toString();}
    public static double number(String text,double fallback){Matcher m=Pattern.compile("^[+−–-]?\\d+(?:\\.\\d+)?(?:/\\d+)?").matcher(text.trim());if(!m.find())return fallback;try{String v=m.group().replace('−','-').replace('–','-');String[] p=v.split("/");double n=Double.parseDouble(p[0]);return p.length==2?n/Double.parseDouble(p[1]):n;}catch(Exception e){return fallback;}}
    private static double bound(double v,double lo,double hi){return Double.isFinite(v)?Math.max(lo,Math.min(hi,v)):lo;}
    public Settings adjustments(List<String> notes){Settings s=new Settings();s.film=film;
        for(Map.Entry<String,String> e:raw.entrySet()){
            String key=e.getKey().toLowerCase(Locale.ROOT),v=e.getValue(),low=v.toLowerCase(Locale.ROOT);double n=number(v,0);
            if(low.matches(".*(?:\\bto\\b|\\bor\\b|sometimes|depending|usually|occasionally).*"))notes.add(e.getKey()+": có tùy chọn/range; xem giá trị nguồn.");
            switch(key){
                case "film simulation":s.film=v;break;
                case "highlight":case "highlights":case "highlight tone":s.highlight=bound(n,-2,4);break;
                case "shadow":case "shadows":case "shadow tone":s.shadow=bound(n,-2,4);break;
                case "highlight & shadow":s.highlight=bound(n,-2,4);s.shadow=bound(n,-2,4);break;
                case "color":case "saturation":s.color=bound(n,-4,4);break;
                case "sharpness":case "sharpeness":case "sharpening":s.sharpness=bound(n,-4,4);break;
                case "noise reduction":case "noise-reduction":case "high iso nr":s.nr=bound(n,-4,4);break;
                case "clarity":s.clarity=bound(n,-5,5);break;
                case "grain":case "grain effect":s.grain=low.contains("strong")?18:low.contains("weak")?8:0;s.grainSize=low.contains("large")?2:1;break;
                case "color chrome":case "color chrome effect":s.colorChrome=low.contains("strong")?2:low.contains("weak")?1:0;break;
                case "color chrome fx blue":case "color chrome effect blue":s.chromeBlue=low.contains("strong")?2:low.contains("weak")?1:0;break;
                case "dynamic range":case "dr":s.dr=low.contains("400")?400:low.contains("200")?200:100;if(low.contains("auto"))notes.add("DR Auto: giữ DR100 trong editor, không giả lập quyết định đo sáng của máy.");break;
                case "white balance":case "wb":
                    s.wb=v.split(",")[0];Matcher r=Pattern.compile("([+−–-]?\\d+)\\s*Red",Pattern.CASE_INSENSITIVE).matcher(v),b=Pattern.compile("([+−–-]?\\d+)\\s*Blue",Pattern.CASE_INSENSITIVE).matcher(v);
                    if(r.find())s.red=bound(number(r.group(1),0),-9,9);if(b.find())s.blue=bound(number(b.group(1),0),-9,9);
                    // Kelvin / named illuminants are metadata until source white point is known.
                    notes.add("WB "+s.wb+": giữ WB gốc của ảnh; chỉ áp dụng Red/Blue. Chỉnh Nhiệt độ nếu cần.");break;
                case "exposure compensation":case "exposure":
                    if(low.contains(" to ")||low.contains(" or ")){s.exposure=0;notes.add("Bù sáng là một khoảng: tự chọn EV, không tự lấy đầu khoảng.");}else s.exposure=bound(n,-3,3);break;
                case "iso":notes.add("ISO là thông số lúc chụp, không áp dụng lại lên JPEG.");break;
                default:notes.add(e.getKey()+": giữ để tham khảo, bộ xử lý chưa áp dụng.");
            }
        }return s;
    }
}

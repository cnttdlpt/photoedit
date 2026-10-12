package vn.iro.editor;

import android.content.ContentResolver;
import android.media.ExifInterface;
import android.net.Uri;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.Locale;

/** Only uses source metadata or user-entered text; never substitutes device/recipe values. */
final class PhotoInfo {
    String camera="",focal="",aperture="",shutter="",iso="",date="",author="";
    PhotoInfo copy(){PhotoInfo i=new PhotoInfo();i.camera=camera;i.focal=focal;i.aperture=aperture;i.shutter=shutter;i.iso=iso;i.date=date;i.author=author;return i;}
    String settings(){StringBuilder out=new StringBuilder();for(String value:new String[]{focal,aperture,shutter,iso})if(!value.trim().isEmpty()){if(out.length()>0)out.append("  ·  ");out.append(value);}return out.toString();}
    JSONObject json()throws Exception{JSONObject j=new JSONObject();j.put("camera",camera);j.put("focal",focal);j.put("aperture",aperture);j.put("shutter",shutter);j.put("iso",iso);j.put("date",date);j.put("author",author);return j;}
    static PhotoInfo from(JSONObject j){PhotoInfo i=new PhotoInfo();i.camera=clean(j.optString("camera"));i.focal=clean(j.optString("focal"));i.aperture=clean(j.optString("aperture"));i.shutter=clean(j.optString("shutter"));i.iso=clean(j.optString("iso"));i.date=clean(j.optString("date"));i.author=clean(j.optString("author"));return i;}
    static String clean(String value){value=value.replaceAll("[\\p{Cntrl}]"," ").trim();return value.substring(0,Math.min(100,value.length()));}
    private static String attr(ExifInterface e,String tag){String v=e.getAttribute(tag);return v==null?"":clean(v);}
    private static String number(double value){return String.format(Locale.ROOT,"%.1f",value).replaceFirst("\\.0$","");}
    static PhotoInfo read(ContentResolver resolver,Uri source){
        try(InputStream stream=resolver.openInputStream(source)){return stream==null?new PhotoInfo():fromExif(new ExifInterface(stream));}
        catch(Exception ignored){return new PhotoInfo();}
    }
    static PhotoInfo fromExif(ExifInterface e){
        PhotoInfo i=new PhotoInfo();
        try{String make=attr(e,ExifInterface.TAG_MAKE),model=attr(e,ExifInterface.TAG_MODEL);
            i.camera=model.toLowerCase(Locale.ROOT).startsWith(make.toLowerCase(Locale.ROOT))?model:(make+" "+model).trim();
            double focal=e.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,0);boolean equivalent=focal>0;if(!equivalent)focal=e.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH,0);
            if(focal>0&&Double.isFinite(focal))i.focal=number(focal)+" mm"+(equivalent?" (eq.)":"");
            double aperture=e.getAttributeDouble(ExifInterface.TAG_F_NUMBER,0);if(aperture>0&&Double.isFinite(aperture))i.aperture="f/"+number(aperture);
            double time=e.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME,0);if(time>0&&Double.isFinite(time))i.shutter=time<.5?"1/"+Math.round(1/time)+" s":number(time)+" s";
            String iso=attr(e,ExifInterface.TAG_ISO_SPEED_RATINGS);if(!iso.isEmpty()&&!iso.equals("0"))i.iso="ISO "+iso;
            i.date=attr(e,ExifInterface.TAG_DATETIME_ORIGINAL);if(i.date.isEmpty())i.date=attr(e,ExifInterface.TAG_DATETIME);
            if(i.date.length()>=10)i.date=i.date.substring(0,10).replace(':','.')+i.date.substring(10);
        }catch(Exception ignored){/* Missing EXIF is normal for screenshots and shared images. */}
        return i;
    }
}

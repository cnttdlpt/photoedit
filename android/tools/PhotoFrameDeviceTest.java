package vn.iro.editor;

import android.graphics.*;
import android.media.ExifInterface;
import java.io.*;

/** Run using app_process on an emulator; no APK or app installation required. */
public class PhotoFrameDeviceTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        File folder=new File(args[0]);folder.mkdirs();
        Bitmap photo=Bitmap.createBitmap(960,640,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(photo);Paint p=new Paint();
        p.setShader(new LinearGradient(0,0,960,640,new int[]{0xff173d48,0xff83b7a2,0xffe4c278},null,Shader.TileMode.CLAMP));canvas.drawRect(0,0,960,640,p);p.setShader(null);p.setColor(0xff264c46);canvas.drawRect(100,250,380,640,p);p.setColor(0xffe3cda0);canvas.drawCircle(700,180,68,p);
        File jpeg=new File(folder,"exif-source.jpg");try(OutputStream out=new FileOutputStream(jpeg)){photo.compress(Bitmap.CompressFormat.JPEG,100,out);}
        PhotoInfo blank=PhotoInfo.fromExif(new ExifInterface(jpeg.toString()));check(blank.camera.isEmpty()&&blank.iso.isEmpty()&&blank.date.isEmpty(),"Missing EXIF must stay empty");
        ExifInterface exif=new ExifInterface(jpeg.toString());exif.setAttribute(ExifInterface.TAG_MAKE,"FUJIFILM");exif.setAttribute(ExifInterface.TAG_MODEL,"X-T5");exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,"35");exif.setAttribute(ExifInterface.TAG_F_NUMBER,"2.8");exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME,"0.004");exif.setAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS,"400");exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL,"2026:10:09 16:45:30");exif.saveAttributes();
        PhotoInfo info=PhotoInfo.fromExif(new ExifInterface(jpeg.toString()));info.author="Nguyễn Văn Nam";
        check(info.camera.equals("FUJIFILM X-T5")&&info.focal.equals("35 mm (eq.)")&&info.aperture.equals("f/2.8")&&info.shutter.equals("1/250 s")&&info.iso.equals("ISO 400"),"EXIF formatting");
        check(info.date.equals("2026.10.09 16:45:30"),"Capture date");check(PhotoInfo.from(info.json()).author.equals(info.author),"Edit persistence");
        check(PhotoFrame.render(photo,info,0)==photo,"Disabled frame");
        for(int style=1;style<=3;style++){
            Bitmap frame=PhotoFrame.render(photo,info,style);
            check(frame.getWidth()==(style==2?1296:960),"Frame width");check(frame.getHeight()==(style==1?760:style==2?688:640),"Frame height");
            check(frame.getPixel(style==2?24:0,style==2?24:0)==photo.getPixel(0,0),"Photo preserved without crop");
            try(OutputStream out=new FileOutputStream(new File(folder,"style-"+style+".png"))){check(frame.compress(Bitmap.CompressFormat.PNG,100,out),"Export frame");}
            Bitmap read=BitmapFactory.decodeFile(new File(folder,"style-"+style+".png").toString());check(read.getWidth()==frame.getWidth()&&read.getHeight()==frame.getHeight(),"Saved output dimensions");read.recycle();frame.recycle();
        }
        for(int style=4;style<PhotoFrame.STYLES.length;style++){
            Bitmap frame=PhotoFrame.render(photo,info,style);
            check(frame.getWidth()>=photo.getWidth()&&frame.getHeight()>=photo.getHeight(),"Extra style dimensions "+style);
            check(!photo.isRecycled(),"Template must not recycle source "+style);
            try(OutputStream out=new FileOutputStream(new File(folder,"style-"+style+".png"))){check(frame.compress(Bitmap.CompressFormat.PNG,100,out),"Save extra template "+style);}
            frame.recycle();
        }
        Bitmap tiny=Bitmap.createBitmap(18,12,Bitmap.Config.ARGB_8888);Bitmap blurred=PhotoFrame.render(tiny,info,12);check(!tiny.isRecycled(),"Blur preserves tiny input");blurred.recycle();tiny.recycle();
        PhotoInfo copy=info.copy();copy.camera="Custom camera";check(!info.camera.equals(copy.camera),"Export snapshot isolated from edits");
        photo.recycle();System.out.println("PASS: EXIF missing/present, metadata edits, 30 frame render styles, preserved image, exported PNG and snapshot");
    }
}

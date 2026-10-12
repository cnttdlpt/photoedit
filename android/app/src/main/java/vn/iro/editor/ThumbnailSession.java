package vn.iro.editor;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** One dialog's previews. Images stay local; work is cancelled when the dialog closes. */
final class ThumbnailSession implements AutoCloseable {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final LruCache<String,Bitmap> thumbnails=new LruCache<>(64);
    // LUT parsing is considerably more expensive than rendering a thumbnail.
    private final LruCache<String,CubeLut> luts=new LruCache<>(2);
    private final Map<String,List<Target>> waiting=new HashMap<>();
    private final Set<String> failed=new HashSet<>();
    final Bitmap original;
    private volatile boolean closed;
    private static final class Target {
        final ImageView image;final TextView label;
        Target(ImageView image,TextView label){this.image=image;this.label=label;}
        void show(Bitmap bitmap,String message){image.setImageBitmap(bitmap);label.setText(message);}
    }
    ThumbnailSession(Bitmap source){
        if(source==null){original=null;return;}
        float scale=Math.min(1,192f/Math.max(source.getWidth(),source.getHeight()));
        original=Bitmap.createScaledBitmap(source,Math.max(1,Math.round(source.getWidth()*scale)),Math.max(1,Math.round(source.getHeight()*scale)),true);
    }
    void bind(ImageView image,TextView label,String key,Settings settings,File file,CubeLut direct){
        if(closed)return;
        Target target=new Target(image,label);
        if(original==null){label.setText("Mở ảnh để xem trước");return;}
        if(file==null&&direct==null){target.show(original,"Chưa có LUT · ảnh gốc");return;}
        Bitmap cached=thumbnails.get(key);if(cached!=null){target.show(cached,"Xem trước màu");return;}
        if(failed.contains(key)){target.show(original,"LUT lỗi · ảnh gốc");return;}
        // Keep the unprocessed image visible while the real color preview loads.
        target.show(original,"Đang tạo ảnh màu…");
        List<Target> existing=waiting.get(key);if(existing!=null){existing.add(target);return;}
        List<Target> targets=new ArrayList<>();targets.add(target);waiting.put(key,targets);Settings snapshot=settings.copy();
        worker.execute(()->{
            if(closed)return;
            Bitmap result=null;
            try{
                CubeLut lut=direct;
                if(lut==null){String fileKey=file.getAbsolutePath();lut=luts.get(fileKey);if(lut==null){try(Reader r=new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8)){lut=CubeLut.parse(r);}luts.put(fileKey,lut);}}
                if(closed)return;
                int w=original.getWidth(),h=original.getHeight();int[] input=new int[w*h];original.getPixels(input,0,w,0,0,w,h);
                result=Bitmap.createBitmap(ColorEngine.process(input,w,h,snapshot,lut),w,h,Bitmap.Config.ARGB_8888);
            }catch(Exception|OutOfMemoryError ignored){/* Per-item status stays visible without repeated toast messages. */}
            if(closed)return;final Bitmap rendered=result;
            main.post(()->{if(closed)return;List<Target> listeners=waiting.remove(key);if(rendered==null)failed.add(key);else thumbnails.put(key,rendered);if(listeners!=null)for(Target listener:listeners)listener.show(rendered==null?original:rendered,rendered==null?"LUT lỗi · ảnh gốc":"Xem trước màu");});
        });
    }
    @Override public void close(){closed=true;worker.shutdownNow();main.removeCallbacksAndMessages(null);waiting.clear();thumbnails.evictAll();}
}

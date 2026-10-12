package vn.iro.editor;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import android.text.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Native Android photo editor, gallery import and photo information frames. */
public class MainActivity extends Activity {
    private static final int OPEN_PHOTO=10,OPEN_LUT=11,OPEN_RECIPE=12,SAVE_IMAGE=13,SAVE_RECIPE=14;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final List<Recipe> recipes=new ArrayList<>();
    private final List<Runnable> controlSync=new ArrayList<>();
    private final List<String> recipeNotes=new ArrayList<>();
    private final Set<ThumbnailSession> thumbnailSessions=new HashSet<>();
    private Settings settings=new Settings(),exportSettings;
    private Recipe currentRecipe;
    private CubeLut activeLut,exportLut;
    private Uri imageUri,exportUri;
    private Bitmap preview,rendered;
    private ImageView image;
    private TextView status,profileLabel,recipeLabel;
    private LinearLayout root,controls;
    private ScrollView adjustmentPanel;
    private TextView emptyHint;
    private View emptyArtwork;
    private Button compareButton,adjustButton;
    private Future<?> renderTask;
    private int revision=0,loadRevision=0,lutRevision=0;
    private boolean original=false,dead=false,syncing=false,exportBusy=false;
    private String exportType="image/jpeg",exportRecipeJson;
    private int exportScale=1,exportQuality=100;
    private boolean exportFullSize=false;
    private PhotoInfo photoInfo=new PhotoInfo(),exportInfo;
    private int frameStyle=0,exportFrameStyle=0;
    private Bitmap frameSource,framePreview;
    private int frameVersion=0,cachedFrameVersion=-1,cachedFrameStyle=-1;
    private android.content.SharedPreferences prefs;
    private OnlineLutBrowser onlineLuts;

    @Override public void onCreate(Bundle state){super.onCreate(state);prefs=getSharedPreferences("iro",MODE_PRIVATE);
        try{settings=SettingsCodec.decode(new JSONObject(prefs.getString("settings","{}")));String rec=prefs.getString("recipe",null);if(rec!=null)currentRecipe=new Recipe(new JSONObject(rec));}catch(Exception ignored){settings=new Settings();}
        frameStyle=Math.max(0,Math.min(PhotoFrame.STYLES.length-1,prefs.getInt("frame.style",0)));
        onlineLuts=new OnlineLutBrowser(this,prefs);onlineLuts.checkOnOpen();
        buildUi();loadLibrary();loadBoundLut();String saved=prefs.getString("photo",null);if(saved!=null)loadPhoto(Uri.parse(saved));
    }
    private int dp(float v){return (int)(getResources().getDisplayMetrics().density*v+.5f);}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(7),0,dp(7));return t;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private Button button(String title,Runnable action){Button b=new Button(this);b.setText(title);b.setTextSize(13);b.setAllCaps(false);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setMinWidth(0);b.setMinimumWidth(0);b.setMinimumHeight(dp(48));b.setPadding(dp(12),dp(6),dp(12),dp(6));b.setTextColor(StudioStyle.inkStates());b.setBackgroundTintList(null);b.setBackground(StudioStyle.surface(StudioStyle.SURFACE,StudioStyle.LINE,dp(14)));b.setStateListAnimator(null);b.setOnClickListener(v->action.run());return b;}
    private void icon(Button b,String type,int tint,boolean above){StudioStyle.Icon d=new StudioStyle.Icon(type,tint,dp(21));b.setCompoundDrawablesWithIntrinsicBounds(above?null:d,above?d:null,null,null);b.setCompoundDrawablePadding(dp(6));}
    private void row(LinearLayout parent,Button... buttons){LinearLayout l=new LinearLayout(this);l.setPadding(0,dp(6),0,0);for(int i=0;i<buttons.length;i++){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);if(i>0)p.leftMargin=dp(8);l.addView(buttons[i],p);}parent.addView(l);}
    private void buildUi(){
        root=column();root.setBackgroundColor(StudioStyle.BG);root.setPadding(dp(16),dp(8),dp(16),dp(8));setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(dp(16)+insets.getSystemWindowInsetLeft(),dp(8)+insets.getSystemWindowInsetTop(),dp(16)+insets.getSystemWindowInsetRight(),dp(8)+insets.getSystemWindowInsetBottom());return insets;});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=text("iro.",28,StudioStyle.ACCENT);brand.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));brand.setPadding(0,0,0,0);header.addView(brand,new LinearLayout.LayoutParams(0,dp(48),1));
        Button frame=button("Khung",this::editPhotoFrame);frame.setContentDescription("Chèn khung thông tin ảnh");header.addView(frame,new LinearLayout.LayoutParams(-2,dp(48)));
        Button export=button("Xuất ảnh",this::chooseExport);export.setTextColor(0xff23301d);export.setBackground(StudioStyle.surface(StudioStyle.ACCENT,0,dp(16)));icon(export,"export",0xff23301d,false);header.addView(export,new LinearLayout.LayoutParams(-2,dp(48)));
        Button more=button("⋮",this::showMore);more.setTextSize(24);more.setBackground(StudioStyle.surface(StudioStyle.BG,0,dp(14)));more.setContentDescription("Thêm tùy chọn");header.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));header.setPadding(0,0,0,dp(6));root.addView(header);
        FrameLayout stage=new FrameLayout(this);stage.setBackground(StudioStyle.shape(0xff0d110e,StudioStyle.LINE,dp(22)));stage.setClipToOutline(true);root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription("Ảnh xem trước");stage.addView(image,new FrameLayout.LayoutParams(-1,-1));
        emptyArtwork=new StudioStyle.EmptyArtwork(this);stage.addView(emptyArtwork,new FrameLayout.LayoutParams(-1,-1));
        emptyHint=text("Một bức ảnh. Một cảm xúc.\n\nChạm để mở ảnh của bạn  ＋",16,StudioStyle.INK);emptyHint.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.BOTTOM);emptyHint.setPadding(dp(12),0,dp(12),dp(30));stage.addView(emptyHint,new FrameLayout.LayoutParams(-1,-1));emptyHint.setOnClickListener(v->choose(OPEN_PHOTO,"image/*"));
        compareButton=button("Xem gốc",()->{original=!original;showImage();});compareButton.setVisibility(View.GONE);FrameLayout.LayoutParams compareLayout=new FrameLayout.LayoutParams(-2,dp(48),Gravity.TOP|Gravity.END);compareLayout.setMargins(0,dp(8),dp(8),0);stage.addView(compareButton,compareLayout);
        status=text("Ảnh được xử lý trên thiết bị",11,0xffa4ad9c);status.setPadding(0,dp(4),0,dp(4));status.setMaxLines(1);status.setEllipsize(TextUtils.TruncateAt.END);root.addView(status);
        LinearLayout lookCard=new LinearLayout(this);lookCard.setGravity(Gravity.CENTER_VERTICAL);lookCard.setPadding(dp(12),0,dp(4),0);lookCard.setBackground(StudioStyle.shape(StudioStyle.SURFACE,StudioStyle.LINE,dp(14)));
        recipeLabel=text(currentRecipe==null?"Nguyên bản":currentRecipe.name,15,StudioStyle.INK);recipeLabel.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));recipeLabel.setMaxLines(1);recipeLabel.setEllipsize(TextUtils.TruncateAt.END);recipeLabel.setMinHeight(dp(48));recipeLabel.setGravity(Gravity.CENTER_VERTICAL);recipeLabel.setContentDescription("Màu đang chọn. Chạm để đổi màu");recipeLabel.setOnClickListener(v->showLibrary());lookCard.addView(recipeLabel,new LinearLayout.LayoutParams(0,-2,1));
        profileLabel=text("",11,StudioStyle.ACCENT);profileLabel.setPadding(dp(8),0,dp(8),0);profileLabel.setMinHeight(dp(48));profileLabel.setGravity(Gravity.CENTER);profileLabel.setOnClickListener(v->showLutOptions());lookCard.addView(profileLabel);root.addView(lookCard);
        adjustmentPanel=new ScrollView(this);adjustmentPanel.setVisibility(View.GONE);adjustmentPanel.setBackground(StudioStyle.shape(StudioStyle.SURFACE,0,dp(18)));controls=column();controls.setPadding(dp(16),dp(10),dp(16),dp(12));adjustmentPanel.addView(controls);LinearLayout.LayoutParams panelParams=new LinearLayout.LayoutParams(-1,panelHeight());panelParams.topMargin=dp(8);root.addView(adjustmentPanel,panelParams);buildControls();
        adjustButton=button("Chỉnh tay",()->{boolean open=adjustmentPanel.getVisibility()!=View.VISIBLE;adjustmentPanel.setVisibility(open?View.VISIBLE:View.GONE);adjustButton.setText(open?"Thu gọn":"Chỉnh tay");adjustButton.setSelected(open);adjustButton.setBackground(StudioStyle.surface(open?0xff34442b:StudioStyle.SURFACE,open?StudioStyle.ACCENT:StudioStyle.LINE,dp(14)));});
        Button open=button("Mở ảnh",()->choose(OPEN_PHOTO,"image/*")),looks=button("Chọn màu",this::showLibrary);icon(open,"photo",StudioStyle.INK,false);icon(looks,"film",StudioStyle.ACCENT,false);icon(adjustButton,"sliders",StudioStyle.INK,false);row(root,open,looks,adjustButton);
    }
    private int panelHeight(){return dp(Math.min(220,Math.max(96,getResources().getConfiguration().screenHeightDp/3)));}
    @Override public void onConfigurationChanged(android.content.res.Configuration config){super.onConfigurationChanged(config);if(adjustmentPanel!=null){ViewGroup.LayoutParams p=adjustmentPanel.getLayoutParams();p.height=panelHeight();adjustmentPanel.setLayoutParams(p);}}
    private void showMore(){
        String[] actions={"LUT của tôi","Thông số recipe","Sửa recipe","Lưu công thức…","Nhập công thức…","Đặt lại chỉnh sửa","Hướng dẫn & giới hạn","LUT trực tuyến","Khung thông tin ảnh"};
        new AlertDialog.Builder(this).setTitle("Tùy chọn").setItems(actions,(d,which)->{switch(which){
            case 8:editPhotoFrame();break;
            case 7:showOnlineLuts();break;
            case 0:showLutOptions();break;
            case 1:if(currentRecipe==null)message("Chưa chọn recipe");else showRecipe(currentRecipe);break;
            case 2:editRecipe();break;
            case 3:saveRecipe();break;
            case 4:choose(OPEN_RECIPE,"*/*");break;
            case 5:settings=new Settings();currentRecipe=null;recipeNotes.clear();original=false;recipeLabel.setText("Nguyên bản");syncControls();loadBoundLut();showImage();persist();break;
            case 6:new AlertDialog.Builder(this).setTitle("Chỉnh màu cùng IRO").setMessage("1. Mở ảnh.\n2. Chọn màu từ thư viện recipe.\n3. Mở Chỉnh tay nếu muốn tinh chỉnh, rồi Xuất ảnh.\n\nRecipe Fuji là thông số máy ảnh. Để có màu nền, gắn LUT sRGB phù hợp trong mục LUT của tôi. App mô phỏng hậu kỳ, không đảm bảo giống JPEG Fuji.\n\nWB tuyệt đối, ISO và một số thông số chỉ để tham khảo. Xem Thông số recipe để biết chi tiết.\n\nXem trước tối đa 1280 px; xuất tiêu chuẩn tối đa 12 MP, hoặc kích thước gốc / phóng 2× khi đủ bộ nhớ. Chưa hỗ trợ RAW. Ảnh không tải lên máy chủ.").setPositiveButton("Đóng",null).show();break;
        }}).show();
    }
    private ImageView thumbnail(){ImageView view=new ImageView(this);view.setScaleType(ImageView.ScaleType.CENTER_CROP);view.setBackground(StudioStyle.shape(0xff0d110e,StudioStyle.LINE,dp(12)));view.setClipToOutline(true);view.setImageDrawable(new StudioStyle.Icon("photo",StudioStyle.MUTED,dp(24)));return view;}
    private ThumbnailSession thumbnailSession(){ThumbnailSession session=new ThumbnailSession(preview);thumbnailSessions.add(session);return session;}
    private void closeThumbnails(ThumbnailSession session){session.close();thumbnailSessions.remove(session);}
    private void showOnlineLuts(){
        final String film=settings.film;
        onlineLuts.show(preview,file->{prefs.edit().putString("lut."+film,file.getName()).apply();if(settings.film.equals(film))loadBoundLut();message("Đã gắn LUT cho "+film);});
    }
    private void showLutOptions(){
        ThumbnailSession session=thumbnailSession();LinearLayout content=column();content.setPadding(dp(20),0,dp(20),dp(12));
        content.addView(text("Film: "+settings.film+"\n"+(activeLut==null?"Chưa có LUT màu nền.":"Đang dùng: "+activeLut.title),13,StudioStyle.INK));
        LinearLayout pair=new LinearLayout(this);LinearLayout before=column(),after=column();LinearLayout.LayoutParams first=new LinearLayout.LayoutParams(0,-2,1),second=new LinearLayout.LayoutParams(0,-2,1);second.leftMargin=dp(10);pair.addView(before,first);pair.addView(after,second);
        ImageView originalImage=thumbnail(),coloredImage=thumbnail();originalImage.setContentDescription("Ảnh gốc");coloredImage.setContentDescription("Ảnh xem trước với LUT và chỉnh tay");before.addView(originalImage,new LinearLayout.LayoutParams(-1,dp(150)));after.addView(coloredImage,new LinearLayout.LayoutParams(-1,dp(150)));before.addView(text("Ảnh gốc",11,StudioStyle.MUTED));TextView label=text("",11,StudioStyle.ACCENT);after.addView(label);content.addView(pair);
        if(session.original!=null)originalImage.setImageBitmap(session.original);session.bind(coloredImage,label,"active",settings,null,activeLut);
        content.addView(text("Xem trước trên ảnh của bạn. Nhập LUT .cube nhận ảnh sRGB; LUT được lưu riêng cho film đang chọn.",11,StudioStyle.MUTED));
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("LUT của tôi").setView(scroll).setPositiveButton("Nhập LUT",(d,w)->choose(OPEN_LUT,"*/*")).setNeutralButton("Bỏ LUT",(d,w)->{prefs.edit().remove(lutKey()).apply();activeLut=null;lutRevision++;updateProfile();requestRender();}).setNegativeButton("Đóng",null).create();dialog.setOnDismissListener(d->closeThumbnails(session));dialog.show();
    }
    private void buildControls(){
        slider("Độ sáng · EV",-3,3,1./3,s->s.exposure,(s,v)->s.exposure=v);
        slider("Độ đậm màu",-4,4,1,s->s.color,(s,v)->s.color=v);
        slider("Ấm / lạnh",-100,100,1,s->s.temperature,(s,v)->s.temperature=v);
        slider("Cường độ LUT (%)",0,100,1,s->s.lutAmount,(s,v)->s.lutAmount=v);
        LinearLayout basic=controls,advanced=column();advanced.setVisibility(View.GONE);
        Button expand=button("Thông số nâng cao ▸",()->{});expand.setOnClickListener(v->{boolean open=advanced.getVisibility()!=View.VISIBLE;advanced.setVisibility(open?View.VISIBLE:View.GONE);expand.setText(open?"Thu gọn nâng cao ▾":"Thông số nâng cao ▸");});basic.addView(expand);basic.addView(advanced);controls=advanced;
        slider("Vùng sáng · Highlight",-2,4,.5,s->s.highlight,(s,v)->s.highlight=v);
        slider("Vùng tối · Shadow",-2,4,.5,s->s.shadow,(s,v)->s.shadow=v);
        slider("WB Shift · Red",-9,9,1,s->s.red,(s,v)->s.red=v);
        slider("WB Shift · Blue",-9,9,1,s->s.blue,(s,v)->s.blue=v);
        slider("Sharpness",-4,4,1,s->s.sharpness,(s,v)->s.sharpness=v);
        slider("Noise Reduction · −4 = tắt",-4,4,1,s->s.nr,(s,v)->s.nr=v);
        slider("Grain · Weak ≈ 8 / Strong ≈ 18",0,30,1,s->s.grain,(s,v)->s.grain=v);
        slider("Grain Size · Small 1 / Large 2",1,2,1,s->s.grainSize,(s,v)->s.grainSize=v);
        slider("Clarity",-5,5,1,s->s.clarity,(s,v)->s.clarity=v);
        slider("Color Chrome · Off 0 / Weak 1 / Strong 2",0,2,1,s->s.colorChrome,(s,v)->s.colorChrome=v);
        slider("Color Chrome FX Blue",0,2,1,s->s.chromeBlue,(s,v)->s.chromeBlue=v);
        TextView d=text("Dynamic Range (nén highlight hậu kỳ)",12,0xffbbc6b0);controls.addView(d);
        Spinner dr=new Spinner(this);dr.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"DR100","DR200","DR400"}));controls.addView(dr);
        dr.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){if(syncing)return;int value=new int[]{100,200,400}[pos];if(settings.dr!=value){settings.dr=value;persist();requestRender();}}});
        controlSync.add(()->dr.setSelection(settings.dr==400?2:settings.dr==200?1:0));controls=basic;syncControls();
    }
    private void slider(String title,double lo,double hi,double step,ToDoubleFunction<Settings> read,BiConsumer<Settings,Double> write){
        TextView label=text("",12,0xffbbc6b0);controls.addView(label);SeekBar seek=new SeekBar(this);seek.setProgressTintList(android.content.res.ColorStateList.valueOf(StudioStyle.ACCENT));seek.setThumbTintList(android.content.res.ColorStateList.valueOf(StudioStyle.ACCENT));seek.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(StudioStyle.LINE));seek.setMax((int)Math.round((hi-lo)/step));controls.addView(seek,new LinearLayout.LayoutParams(-1,dp(48)));
        Runnable update=()->{double val=read.applyAsDouble(settings);label.setText(title+"  ·  "+String.format(Locale.ROOT,"%.2f",val));seek.setProgress((int)Math.round((val-lo)/step));};controlSync.add(update);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){persist();}public void onProgressChanged(SeekBar b,int n,boolean user){if(!user||syncing)return;double value=lo+n*step;write.accept(settings,value);label.setText(title+"  ·  "+String.format(Locale.ROOT,"%.2f",value));requestRender();}});
    }
    private void syncControls(){syncing=true;for(Runnable r:controlSync)r.run();syncing=false;}
    private void message(String m){if(!dead)Toast.makeText(this,m,Toast.LENGTH_LONG).show();}
    private void error(Exception e){runOnUiThread(()->{if(dead)return;status.setText("Lỗi: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));message(status.getText().toString());});}
    private void choose(int request,String mime){
        if(request==OPEN_PHOTO){
            // Let Android open Gallery, or offer installed gallery apps.
            Intent photos=new Intent(Intent.ACTION_PICK).setDataAndType(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,"image/*");
            photos.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try{startActivityForResult(photos,OPEN_PHOTO);return;}
            catch(android.content.ActivityNotFoundException unavailable){/* No gallery installed: use the image document provider. */}
        }
        Intent document=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(mime).addCategory(Intent.CATEGORY_OPENABLE);
        document.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try{startActivityForResult(document,request);}
        catch(android.content.ActivityNotFoundException unavailable){message("Không có ứng dụng chọn ảnh/tệp trên thiết bị.");}
    }
    private void createDocument(int request,String mime,String name){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType(mime).addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(i,request);}
    private static byte[] readLimited(InputStream in,int max)throws IOException{if(in==null)throw new IOException("Không đọc được file");try(InputStream stream=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=stream.read(buffer))!=-1){if(out.size()+n>max)throw new IOException("File vượt giới hạn "+max/1024/1024+" MB");out.write(buffer,0,n);}return out.toByteArray();}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null){if(request==SAVE_IMAGE){exportBusy=false;exportUri=null;exportLut=null;}return;}Uri uri=data.getData();
        if(request==OPEN_PHOTO){loadPhoto(uri,true);}
        else if(request==OPEN_LUT)importLut(uri);
        else if(request==OPEN_RECIPE)importRecipe(uri);
        else if(request==SAVE_IMAGE)exportImage(uri);
        else if(request==SAVE_RECIPE){final String payload=exportRecipeJson;executor.execute(()->{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("Không mở được file đích");out.write(payload.getBytes(StandardCharsets.UTF_8));runOnUiThread(()->message("Đã lưu recipe và chỉnh sửa JSON"));}catch(Exception e){error(e);}});}
    }
    private Bitmap decode(Uri uri,int edge,long maxPixels)throws IOException{
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(),uri),(decoder,info,src)->{
            int w=info.getSize().getWidth(),h=info.getSize().getHeight();double scale=Math.min(1,Math.sqrt((double)maxPixels/((double)w*h)));if(edge>0)scale=Math.min(scale,(double)edge/Math.max(w,h));
            decoder.setTargetSize(Math.max(1,(int)Math.floor(w*scale)),Math.max(1,(int)Math.floor(h*scale)));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
        });
    }
    private void loadPhoto(Uri uri){loadPhoto(uri,false);}
    private void loadPhoto(Uri uri,boolean importFromGallery){
        int token=++loadRevision;status.setText("Đang mở ảnh…");
        executor.execute(()->{File imported=null;try{
            Uri source=uri;
            // Gallery grants may expire. Keep an unchanged private copy for reopening/export.
            if(importFromGallery){
                File folder=new File(getFilesDir(),"imported-photos");
                if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Không tạo được nơi lưu ảnh");
                imported=File.createTempFile("photo-",".img",folder);
                try(InputStream in=getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(imported)){
                    if(in==null)throw new IOException("Không đọc được ảnh từ Bộ sưu tập");
                    byte[] buffer=new byte[32768];long total=0;int n;
                    while((n=in.read(buffer))!=-1){
                        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Đã hủy mở ảnh");
                        total+=n;if(total>100L*1024*1024)throw new IOException("Ảnh vượt giới hạn 100 MB");
                        out.write(buffer,0,n);
                    }
                }
                source=Uri.fromFile(imported);
            }
            Bitmap b=decode(source,1280,1638400);PhotoInfo readInfo=PhotoInfo.read(getContentResolver(),source);
            if(source.toString().equals(prefs.getString("frame.photo","")))try{readInfo=PhotoInfo.from(new JSONObject(prefs.getString("frame.info","{}")));}catch(Exception ignored){}
            final PhotoInfo loadedInfo=readInfo;final Uri selected=source;final File copy=imported;
            runOnUiThread(()->{
                if(dead||token!=loadRevision){b.recycle();if(copy!=null)copy.delete();return;}
                imageUri=selected;preview=b;rendered=null;original=false;photoInfo=loadedInfo;frameVersion++;frameSource=null;framePreview=null;
                prefs.edit().putString("photo",selected.toString()).apply();
                // Keep the active photo and any snapshot still used by an export.
                File[] old=new File(getFilesDir(),"imported-photos").listFiles();
                if(old!=null)for(File f:old)if(f.getName().startsWith("photo-")&&f.getName().endsWith(".img")&&!Uri.fromFile(f).equals(imageUri)&&!Uri.fromFile(f).equals(exportUri))f.delete();
                showImage();requestRender();
            });
        }catch(Exception e){if(imported!=null)imported.delete();error(e);}
        catch(OutOfMemoryError e){if(imported!=null)imported.delete();runOnUiThread(()->{if(!dead)message("Không đủ bộ nhớ để mở ảnh này");});}});
    }
    private void showImage(){if(preview==null)return;emptyHint.setVisibility(View.GONE);emptyArtwork.setVisibility(View.GONE);compareButton.setVisibility(View.VISIBLE);compareButton.setText(original?"Xem bản chỉnh":"Xem gốc");Bitmap base=original||rendered==null?preview:rendered;
        if(!original&&frameStyle>0){try{if(frameSource!=base||cachedFrameStyle!=frameStyle||cachedFrameVersion!=frameVersion){framePreview=PhotoFrame.render(base,photoInfo,frameStyle);frameSource=base;cachedFrameStyle=frameStyle;cachedFrameVersion=frameVersion;}image.setImageBitmap(framePreview);}catch(OutOfMemoryError e){image.setImageBitmap(base);message("Không đủ bộ nhớ xem khung ảnh");}}else image.setImageBitmap(base);status.setText(original?"Đang xem ảnh gốc":"Đang xem ảnh đã chỉnh");}
    private final Runnable delayedRender=()->startRender();
    private void requestRender(){revision++;if(renderTask!=null)renderTask.cancel(true);handler.removeCallbacks(delayedRender);handler.postDelayed(delayedRender,120);}
    private void startRender(){if(preview==null||dead)return;final int token=revision;final Bitmap src=preview;final Settings s=settings.copy();final CubeLut lut=activeLut;status.setText("Đang xử lý màu…");renderTask=executor.submit(()->{try{int w=src.getWidth(),h=src.getHeight();int[] p=new int[w*h];src.getPixels(p,0,w,0,0,w,h);int[] out=ColorEngine.process(p,w,h,s,lut);Bitmap b=Bitmap.createBitmap(out,w,h,Bitmap.Config.ARGB_8888);runOnUiThread(()->{if(dead||token!=revision)return;rendered=b;showImage();if(!original)status.setText(w+" × "+h+" · preview · "+(lut==null?"chưa áp dụng màu nền LUT":"LUT + chỉnh tay"));});}catch(CancellationException ignored){}catch(Exception e){error(e);}catch(OutOfMemoryError e){runOnUiThread(()->message("Không đủ bộ nhớ xử lý ảnh"));}});}
    private String lutKey(){return "lut."+settings.film;}
    private void updateProfile(){profileLabel.setText(activeLut!=null?"LUT ✓  ›":"＋ LUT  ›");profileLabel.setContentDescription(activeLut!=null?"LUT đang dùng: "+activeLut.title:"Chưa có LUT màu nền. Chạm để thêm LUT");recipeLabel.setCompoundDrawablesWithIntrinsicBounds(null,null,null,null);if(activeLut==null&&!settings.film.equals("Original"))profileLabel.setText("Thiếu LUT  ›");}
    private void loadBoundLut(){final int token=++lutRevision;String file=prefs.getString(lutKey(),null);activeLut=null;updateProfile();requestRender();if(file==null)return;executor.execute(()->{try(Reader r=new InputStreamReader(new FileInputStream(new File(getFilesDir(),file)),StandardCharsets.UTF_8)){CubeLut lut=CubeLut.parse(r);runOnUiThread(()->{if(dead||token!=lutRevision)return;activeLut=lut;updateProfile();requestRender();});}catch(Exception e){error(e);}});}
    private void importLut(Uri uri){final String key=lutKey();final int token=++lutRevision;status.setText("Đang kiểm tra LUT…");executor.execute(()->{try{byte[] bytes=readLimited(getContentResolver().openInputStream(uri),35*1024*1024);CubeLut lut=CubeLut.parse(new InputStreamReader(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8));String file="lut-"+UUID.randomUUID()+".cube";try(FileOutputStream out=new FileOutputStream(new File(getFilesDir(),file))){out.write(bytes);}prefs.edit().putString(key,file).apply();runOnUiThread(()->{if(dead)return;if(token==lutRevision&&key.equals(lutKey())){activeLut=lut;updateProfile();requestRender();}message("Đã lưu LUT cho "+key.substring(4));});}catch(Exception e){error(e);}});}
    private void loadLibrary(){executor.execute(()->{try{JSONObject j=new JSONObject(new String(readLimited(getAssets().open("recipes.json"),8*1024*1024),StandardCharsets.UTF_8));JSONArray list=j.getJSONArray("recipes");List<Recipe> parsed=new ArrayList<>();parsed.add(new Recipe(new JSONObject(new String(readLimited(getAssets().open("sample-recipe.json"),100000),StandardCharsets.UTF_8))));for(int i=0;i<list.length();i++)parsed.add(new Recipe(list.getJSONObject(i)));runOnUiThread(()->{if(dead)return;recipes.addAll(parsed);});}catch(Exception e){error(e);}});}
    private void showLibrary(){if(recipes.isEmpty()){message("Thư viện đang tải");return;}
        ThumbnailSession thumbnails=thumbnailSession();
        LinearLayout layout=column();layout.setPadding(dp(16),0,dp(16),0);EditText search=new EditText(this);search.setSingleLine();search.setHint("Tìm tên recipe hoặc film simulation");layout.addView(search);
        TreeSet<String> sensors=new TreeSet<>();for(Recipe r:recipes)if(!r.sensors.isEmpty())sensors.addAll(Arrays.asList(r.sensors.split(" / ")));List<String> options=new ArrayList<>();options.add("Tất cả cảm biến");options.addAll(sensors);
        Spinner sensor=new Spinner(this);sensor.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options));layout.addView(sensor);
        TextView count=text("",11,0xffa4ad9c);layout.addView(count);ListView list=new ListView(this);list.setDivider(null);list.setDividerHeight(dp(8));layout.addView(list,new LinearLayout.LayoutParams(-1,dp(380)));
        List<Recipe> filtered=new ArrayList<>();ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,new ArrayList<>()){@Override public View getView(int position,View reusable,ViewGroup parent){Recipe recipe=filtered.get(position);boolean selected=currentRecipe!=null&&currentRecipe.id.equals(recipe.id);LinearLayout card=new LinearLayout(MainActivity.this);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(8),dp(8),dp(8),dp(8));card.setBackground(StudioStyle.surface(selected?0xff303e29:StudioStyle.SURFACE,selected?StudioStyle.ACCENT:StudioStyle.LINE,dp(16)));ImageView symbol=thumbnail();symbol.setContentDescription("Minh họa màu "+recipe.name);card.addView(symbol,new LinearLayout.LayoutParams(dp(72),dp(76)));LinearLayout description=column();TextView name=text(recipe.name,15,StudioStyle.INK);name.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));name.setPadding(dp(8),0,0,dp(5));name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);description.addView(name);TextView detail=text(recipe.film+" · "+recipe.sensors,11,StudioStyle.MUTED);detail.setPadding(dp(8),0,0,0);detail.setMaxLines(2);description.addView(detail);TextView previewState=text("",10,StudioStyle.ACCENT);previewState.setPadding(dp(8),dp(5),0,0);description.addView(previewState);Settings recipeSettings=recipe.adjustments(new ArrayList<>());String lutFile=prefs.getString("lut."+recipeSettings.film,null);thumbnails.bind(symbol,previewState,recipe.id,recipeSettings,lutFile==null?null:new File(getFilesDir(),lutFile),null);card.addView(description,new LinearLayout.LayoutParams(0,-2,1));if(selected)card.addView(text("✓",18,StudioStyle.ACCENT));return card;}};list.setAdapter(adapter);
        Runnable filter=()->{String q=search.getText().toString().trim().toLowerCase(Locale.ROOT),ss=(String)sensor.getSelectedItem();filtered.clear();adapter.clear();for(Recipe r:recipes)if((r.name+" "+r.film).toLowerCase(Locale.ROOT).contains(q)&&(sensor.getSelectedItemPosition()==0||Arrays.asList(r.sensors.split(" / ")).contains(ss))){filtered.add(r);adapter.add(r.name+"\n"+r.film+" · "+r.sensors);}count.setText(filtered.size()+" / "+recipes.size()+" recipe · giữ một mục để xem thông số");};
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){filter.run();}public void afterTextChanged(Editable e){}});
        sensor.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> a){}public void onItemSelected(AdapterView<?> a,View v,int p,long i){filter.run();}});
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Chọn màu").setView(layout).setNegativeButton("Đóng",null).create();list.setOnItemClickListener((p,v,pos,id)->{Recipe r=filtered.get(pos);dialog.dismiss();applyRecipe(r);});list.setOnItemLongClickListener((p,v,pos,id)->{showRecipe(filtered.get(pos));return true;});dialog.setOnDismissListener(d->closeThumbnails(thumbnails));filter.run();dialog.show();
    }
    private String applicationNotes(Recipe r){List<String> notes=new ArrayList<>();r.adjustments(notes);return notes.isEmpty()?"":"\nCách áp dụng trong IRO:\n"+String.join("\n",notes)+"\n";}
    private void showRecipe(Recipe r){ScrollView scroll=new ScrollView(this);TextView text=text(r.description()+applicationNotes(r)+"\nNguồn: "+r.sourceUrl+"\n\nThông số này dành cho máy Fuji. App áp dụng gần đúng các chỉnh sửa hậu kỳ; màu nền cần LUT riêng. Các giá trị thiếu trong nguồn không được tự điền.",13,0xffe8ebe6);text.setTextIsSelectable(true);text.setPadding(dp(20),dp(10),dp(20),dp(10));scroll.addView(text);
        new AlertDialog.Builder(this).setTitle(r.name).setView(scroll).setPositiveButton("Dùng recipe",(d,w)->applyRecipe(r)).setNeutralButton("Mở nguồn",(d,w)->{Uri uri=Uri.parse(r.sourceUrl);if("https".equals(uri.getScheme())&&"fujixweekly.com".equals(uri.getHost()))startActivity(new Intent(Intent.ACTION_VIEW,uri));else message("Không có đường dẫn nguồn hợp lệ");}).setNegativeButton("Đóng",null).show();}
    private void applyRecipe(Recipe r){recipeNotes.clear();settings=r.adjustments(recipeNotes);currentRecipe=r;recipeLabel.setText(r.name);original=false;syncControls();loadBoundLut();persist();}
    private void editRecipe(){if(currentRecipe==null){message("Chọn recipe trước khi sửa các thông số nguồn");return;}try{
        Recipe edited=new Recipe(currentRecipe.json());LinearLayout form=column();form.setPadding(dp(18),0,dp(18),0);Map<String,EditText> fields=new LinkedHashMap<>();
        EditText name=new EditText(this);name.setText(edited.name);form.addView(text("Tên recipe",12,0xffa4ad9c));form.addView(name);EditText film=new EditText(this);film.setText(edited.film);form.addView(text("Film Simulation (khớp với LUT đã gắn)",12,0xffa4ad9c));form.addView(film);
        for(Map.Entry<String,String> e:edited.raw.entrySet()){form.addView(text(e.getKey(),12,0xffa4ad9c));EditText value=new EditText(this);value.setText(e.getValue());form.addView(value);fields.put(e.getKey(),value);}ScrollView scroll=new ScrollView(this);scroll.addView(form);
        new AlertDialog.Builder(this).setTitle("Sửa bản riêng của recipe").setView(scroll).setNegativeButton("Hủy",null).setPositiveButton("Áp dụng",(d,w)->{edited.id="custom-"+UUID.randomUUID();edited.name=name.getText().toString();edited.film=film.getText().toString();for(Map.Entry<String,EditText> e:fields.entrySet())edited.raw.put(e.getKey(),e.getValue().getText().toString());edited.raw.remove("Film Simulation");recipes.add(0,edited);applyRecipe(edited);message("Dùng Lưu JSON để lưu file bản chỉnh sửa.");}).show();
    }catch(Exception e){error(e);}}
    private void importRecipe(Uri uri){executor.execute(()->{try{JSONObject j=new JSONObject(new String(readLimited(getContentResolver().openInputStream(uri),8*1024*1024),StandardCharsets.UTF_8));
        if("IRO_ANDROID".equals(j.optString("app"))){Settings s=SettingsCodec.decode(j.getJSONObject("settings"));Recipe r=j.has("recipe")?new Recipe(j.getJSONObject("recipe")):null;runOnUiThread(()->{if(dead)return;settings=s;currentRecipe=r;recipeLabel.setText(r==null?"Recipe đã nhập":r.name);syncControls();loadBoundLut();persist();message("Đã nhập chỉnh sửa. LUT cần được nhập riêng.");});}
        else{JSONArray a=j.optJSONArray("recipes");if(a==null)a=new JSONArray().put(j);if(a.length()>1000)throw new JSONException("Tối đa 1000 recipe mỗi file");List<Recipe> imported=new ArrayList<>();for(int i=0;i<a.length();i++)imported.add(new Recipe(a.getJSONObject(i)));runOnUiThread(()->{if(dead)return;recipes.addAll(0,imported);if(imported.size()==1)applyRecipe(imported.get(0));message("Đã nhập "+imported.size()+" recipe vào phiên làm việc");});}
    }catch(Exception e){error(e);}});}
    private void saveRecipe(){try{JSONObject j=new JSONObject();j.put("app","IRO_ANDROID");j.put("version",1);j.put("settings",SettingsCodec.encode(settings));if(currentRecipe!=null)j.put("recipe",currentRecipe.json());j.put("lutNote","LUT must be imported separately for this film simulation");exportRecipeJson=j.toString(2);createDocument(SAVE_RECIPE,"application/json","iro-recipe.json");}catch(Exception e){error(e);}}
    private void persist(){try{android.content.SharedPreferences.Editor e=prefs.edit().putString("settings",SettingsCodec.encode(settings).toString());if(currentRecipe==null)e.remove("recipe");else e.putString("recipe",currentRecipe.json().toString());e.apply();}catch(Exception e){error(e);}}
    private void editPhotoFrame(){
        if(preview==null){message("Mở ảnh trước khi chèn thông tin");return;}
        final PhotoInfo draft=photoInfo.copy();final int[] style={frameStyle==0?1:frameStyle};
        LinearLayout form=column();form.setPadding(dp(16),0,dp(16),dp(12));
        Button picker=button("Chọn mẫu · "+PhotoFrame.STYLES[style[0]],()->{});form.addView(picker);
        Bitmap source=rendered==null?preview:rendered;float ratio=Math.min(1,640f/Math.max(source.getWidth(),source.getHeight()));final Bitmap small=Bitmap.createScaledBitmap(source,Math.max(1,Math.round(source.getWidth()*ratio)),Math.max(1,Math.round(source.getHeight()*ratio)),true);
        ImageView example=new ImageView(this);example.setScaleType(ImageView.ScaleType.FIT_CENTER);example.setContentDescription("Xem trước khung thông tin ảnh");form.addView(example,new LinearLayout.LayoutParams(-1,dp(200)));
        form.addView(text("Thông tin đọc từ ảnh gốc; mục trống là ảnh không có dữ liệu. Bạn có thể sửa hoặc để trống. Tiêu cự (eq.) là quy đổi 35 mm. Glass, Date và Signature chèn lên ảnh; các mẫu còn lại thêm viền ngoài. Date dùng ngày chụp; Signature ưu tiên chữ ký.",12,StudioStyle.MUTED));
        String[] labels={"Tên máy / điện thoại","Tiêu cự","Khẩu độ","Tốc độ chụp","ISO","Ngày giờ chụp","Tên tác giả / chữ ký"};
        String[] values={draft.camera,draft.focal,draft.aperture,draft.shutter,draft.iso,draft.date,draft.author};EditText[] fields=new EditText[values.length];
        final boolean[] open={true};Runnable redraw=()->{if(!open[0])return;draft.camera=PhotoInfo.clean(fields[0].getText().toString());draft.focal=PhotoInfo.clean(fields[1].getText().toString());draft.aperture=PhotoInfo.clean(fields[2].getText().toString());draft.shutter=PhotoInfo.clean(fields[3].getText().toString());draft.iso=PhotoInfo.clean(fields[4].getText().toString());draft.date=PhotoInfo.clean(fields[5].getText().toString());draft.author=PhotoInfo.clean(fields[6].getText().toString());example.setImageBitmap(PhotoFrame.render(small,draft,style[0]));};
        for(int i=0;i<values.length;i++){form.addView(text(labels[i],12,StudioStyle.MUTED));EditText input=new EditText(this);input.setSingleLine();input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100)});input.setText(values[i]);fields[i]=input;form.addView(input);input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){handler.removeCallbacks(redraw);handler.postDelayed(redraw,120);}public void afterTextChanged(Editable e){}});}
        picker.setOnClickListener(v->{redraw.run();FrameTemplatePicker.show(this,small,draft,style[0],n->{style[0]=n;picker.setText("Chọn mẫu · "+PhotoFrame.STYLES[n]);redraw.run();});});
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Khung thông tin ảnh").setView(scroll).setPositiveButton("Áp dụng",(d,n)->{redraw.run();photoInfo=draft.copy();frameStyle=style[0];frameVersion++;original=false;savePhotoFrame();showImage();}).setNeutralButton("Bỏ khung",(d,n)->{frameStyle=0;frameSource=null;framePreview=null;savePhotoFrame();showImage();}).setNegativeButton("Hủy",null).create();
        dialog.setOnDismissListener(d->{open[0]=false;handler.removeCallbacks(redraw);});dialog.show();redraw.run();
    }
    private void savePhotoFrame(){try{prefs.edit().putInt("frame.style",frameStyle).putString("frame.photo",imageUri.toString()).putString("frame.info",photoInfo.json().toString()).apply();}catch(Exception e){error(e);}}
    private void chooseExport(){
        if(imageUri==null){message("Mở ảnh trước khi xuất");return;}
        if(exportBusy){message("Đang xuất ảnh, vui lòng đợi");return;}
        LinearLayout form=column();form.setPadding(dp(20),0,dp(20),dp(12));
        form.addView(text("Độ phân giải",13,StudioStyle.INK));
        Spinner size=new Spinner(this);size.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Tiêu chuẩn · tối đa 12 MP","Giữ kích thước gốc","Phóng lớn 2× · gấp 4 số điểm ảnh"}));size.setSelection(2);form.addView(size);
        form.addView(text("Định dạng",13,StudioStyle.INK));
        Spinner format=new Spinner(this);format.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"JPEG · chất lượng 100%","JPEG · chất lượng 95%","PNG · nén không mất dữ liệu"}));form.addView(format);
        form.addView(text("2× tăng cả chiều rộng và chiều cao từ ảnh gốc, dùng nội suy, chưa phải AI phục hồi chi tiết. Nếu thiếu bộ nhớ, chọn kích thước gốc hoặc tiêu chuẩn.",12,StudioStyle.MUTED));
        form.addView(text(frameStyle==0?"Không chèn thông tin ảnh":"Kèm khung: "+PhotoFrame.STYLES[frameStyle]+". Kích thước file có thêm phần khung.",12,StudioStyle.MUTED));
        new AlertDialog.Builder(this).setTitle("Xuất ảnh đã chỉnh").setView(form).setPositiveButton("Tiếp tục",(d,n)->{
            int selected=size.getSelectedItemPosition(),type=format.getSelectedItemPosition();
            exportScale=selected==2?2:1;exportFullSize=selected>0;exportQuality=type==1?95:100;exportType=type==2?"image/png":"image/jpeg";
            exportSettings=settings.copy();exportLut=activeLut;exportUri=imageUri;exportInfo=photoInfo.copy();exportFrameStyle=frameStyle;exportBusy=true;
            try{createDocument(SAVE_IMAGE,exportType,"iro-"+System.currentTimeMillis()+(exportScale==2?"-2x":"")+(type==2?".png":".jpg"));}
            catch(Exception e){exportBusy=false;exportUri=null;exportLut=null;error(e);}
        }).setNegativeButton("Hủy",null).show();
    }
    private Bitmap prepareExport(Uri src,Settings s,CubeLut lut,int scale,boolean fullSize,PhotoInfo frameInfo,int style)throws IOException{
        Runtime runtime=Runtime.getRuntime();long used=runtime.totalMemory()-runtime.freeMemory();
        long available=Math.max(0,runtime.maxMemory()-used-24L*1024*1024);
        long pixelBudget=available/(24L+(style>0?14L:4L)*scale*scale);
        if(pixelBudget<1)throw new IOException("Không đủ bộ nhớ xuất ảnh. Hãy đóng ứng dụng khác rồi thử lại.");
        Bitmap decoded=ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(),src),(decoder,info,source)->{
            int[] size=ExportSizing.dimensions(info.getSize().getWidth(),info.getSize().getHeight(),scale,fullSize,pixelBudget);
            decoder.setTargetSize(size[0],size[1]);decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
        });
        Bitmap result=null;
        try{
            int w=decoded.getWidth(),h=decoded.getHeight();int[] input=new int[w*h];decoded.getPixels(input,0,w,0,0,w,h);decoded.recycle();decoded=null;
            int[] out=ColorEngine.process(input,w,h,s,lut);result=Bitmap.createBitmap(out,w,h,Bitmap.Config.ARGB_8888);
            if(scale>1){Bitmap enlarged=Bitmap.createScaledBitmap(result,w*scale,h*scale,true);result.recycle();result=enlarged;}
            if(style>0){Bitmap framed=PhotoFrame.render(result,frameInfo,style);result.recycle();result=framed;}
            Bitmap ready=result;result=null;return ready;
        }finally{if(decoded!=null)decoded.recycle();if(result!=null)result.recycle();}
    }
    private void exportImage(Uri destination){
        if(exportUri==null||exportSettings==null){exportBusy=false;message("Phiên xuất ảnh đã hết. Vui lòng xuất lại.");return;}
        final Uri src=exportUri;final Settings s=exportSettings;final CubeLut lut=exportLut;final String mime=exportType;
        final int scale=exportScale,quality=exportQuality;final boolean fullSize=exportFullSize;final PhotoInfo info=exportInfo;final int style=exportFrameStyle;
        status.setText(scale==2?"Đang chỉnh màu và phóng lớn 2×…":"Đang xuất ảnh…");
        executor.execute(()->{Bitmap result=null;try{
            result=prepareExport(src,s,lut,scale,fullSize,info,style);int w=result.getWidth(),h=result.getHeight();
            try(OutputStream stream=getContentResolver().openOutputStream(destination,"wt")){
                if(stream==null)throw new IOException("Không mở được nơi lưu");
                if(!result.compress(mime.equals("image/png")?Bitmap.CompressFormat.PNG:Bitmap.CompressFormat.JPEG,quality,stream))throw new IOException("Không nén được ảnh");
            }
            runOnUiThread(()->{if(dead)return;status.setText("Đã lưu ảnh "+w+" × "+h+" px");message(status.getText().toString());});
        }catch(Exception e){error(e);}
        catch(OutOfMemoryError e){runOnUiThread(()->{if(!dead)message("Không đủ bộ nhớ. Chọn kích thước gốc hoặc tiêu chuẩn và xuất lại.");});}
        finally{if(result!=null)result.recycle();runOnUiThread(()->{exportBusy=false;exportUri=null;exportLut=null;});}});
    }
    @Override protected void onPause(){persist();super.onPause();}
    @Override protected void onDestroy(){dead=true;if(onlineLuts!=null)onlineLuts.close();for(ThumbnailSession session:thumbnailSessions)session.close();thumbnailSessions.clear();handler.removeCallbacksAndMessages(null);executor.shutdownNow();super.onDestroy();}
}






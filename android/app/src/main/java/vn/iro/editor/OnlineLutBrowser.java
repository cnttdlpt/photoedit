package vn.iro.editor;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Lifecycle-bound network UI; automatic checks run only when the app is opened. */
final class OnlineLutBrowser implements AutoCloseable {
    private final Activity activity;private final SharedPreferences prefs;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Set<ThumbnailSession> sessions=new HashSet<>();
    private volatile boolean closed;private boolean busy;
    OnlineLutBrowser(Activity a,SharedPreferences p){activity=a;prefs=p;}
    private String repo(){return prefs.getString("online.repo",OnlineLuts.DEFAULT_REPO);}
    private String catalogKey(String repo){return "online.catalog."+repo;}
    private String installedKey(JSONObject item){return "online.saved."+item.optString("repo")+"/"+item.optString("path");}
    private JSONArray cached(String repo){try{return new JSONArray(prefs.getString(catalogKey(repo),"[]"));}catch(Exception ignored){return new JSONArray();}}
    private void post(Runnable r){main.post(()->{if(!closed)r.run();});}
    private void notice(String text){Toast.makeText(activity,text,Toast.LENGTH_LONG).show();}
    private int dp(int n){return (int)(n*activity.getResources().getDisplayMetrics().density+.5f);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(activity);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView text(String value){TextView t=new TextView(activity);t.setText(value);t.setTextSize(12);t.setPadding(dp(4),dp(6),dp(4),dp(6));return t;}
    private String state(JSONObject item){String saved=prefs.getString(installedKey(item),"");if(saved.isEmpty())return "Chưa tải";return saved.equals(item.optString("sha"))?"Đã tải · dùng offline":"Có bản cập nhật";}
    void checkOnOpen(){long now=System.currentTimeMillis();if(!prefs.getBoolean("online.auto",false)||now-prefs.getLong("online.checked",0)<86400000L||now-prefs.getLong("online.attempt",0)<3600000L)return;prefs.edit().putLong("online.attempt",now).apply();refresh(repo(),null);}
    private void refresh(String repository,Runnable complete){if(busy){notice("Đang kiểm tra nguồn LUT");return;}busy=true;
        worker.execute(()->{String result;try{JSONArray items=OnlineLuts.discover(repository);prefs.edit().putString(catalogKey(repository),items.toString()).putLong("online.checked",System.currentTimeMillis()).apply();int updated=0,failed=0;
            if(prefs.getBoolean("online.auto",false))for(int i=0;i<items.length()&&updated+failed<20;i++){if(closed)break;JSONObject item=items.getJSONObject(i);String old=prefs.getString(installedKey(item),"");if(old.isEmpty()||old.equals(item.getString("sha")))continue;try{OnlineLuts.download(activity.getFilesDir(),item);prefs.edit().putString(installedKey(item),item.getString("sha")).apply();updated++;}catch(Exception e){failed++;}}
            result="Tìm thấy "+items.length()+" LUT"+(updated>0?" · đã tải "+updated+" bản mới":"")+(failed>0?" · "+failed+" bản tải lỗi, giữ bản cũ":"");
        }catch(Exception e){result="Chưa cập nhật được: "+e.getMessage()+". Vẫn dùng được LUT đã tải.";}String message=result;post(()->{busy=false;prefs.edit().putString("online.status",message).apply();if(complete!=null){notice(message);complete.run();}});});
    }
    void show(Bitmap photo,Consumer<File> use){
        final String repository=repo();final boolean[] showing={true};ThumbnailSession thumbs=new ThumbnailSession(photo);sessions.add(thumbs);
        LinearLayout content=column();content.setPadding(dp(16),0,dp(16),dp(10));content.addView(text("Nguồn: "+repository+"\nẢnh không tải lên mạng. Tìm file .cube trong nguồn đã chọn."));
        EditText search=new EditText(activity);search.setHint("Tìm tên LUT");search.setSingleLine();content.addView(search);
        Switch auto=new Switch(activity);auto.setText("Tự kiểm tra / tải bản mới mỗi ngày");auto.setTextSize(12);auto.setMinHeight(dp(48));auto.setChecked(prefs.getBoolean("online.auto",false));auto.setOnCheckedChangeListener((v,on)->prefs.edit().putBoolean("online.auto",on).apply());content.addView(auto);content.addView(text("Chỉ chạy khi mở app; chỉ tự tải bản mới của LUT đã lưu. Không tự đổi LUT đang chỉnh."));
        TextView status=text(prefs.getString("online.status","Bấm Tìm / cập nhật để lấy danh sách."));content.addView(status);
        List<JSONObject> filtered=new ArrayList<>();ListView list=new ListView(activity);list.setDividerHeight(dp(6));content.addView(list,new LinearLayout.LayoutParams(-1,dp(290)));
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(activity,android.R.layout.simple_list_item_1,new ArrayList<>()){
            @Override public View getView(int index,View recycled,ViewGroup parent){JSONObject item=filtered.get(index);LinearLayout row=new LinearLayout(activity);row.setPadding(dp(8),dp(8),dp(8),dp(8));row.setBackground(StudioStyle.surface(StudioStyle.SURFACE,StudioStyle.LINE,dp(12)));ImageView image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImageDrawable(new StudioStyle.Icon("film",StudioStyle.MUTED,dp(24)));row.addView(image,new LinearLayout.LayoutParams(dp(64),dp(72)));LinearLayout words=column();TextView title=text(item.optString("name"));title.setTextSize(14);words.addView(title);TextView sub=text(state(item));words.addView(sub);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));try{File file=OnlineLuts.localFile(activity.getFilesDir(),item);if(file.isFile()){TextView previewLabel=text("");words.addView(previewLabel);thumbs.bind(image,previewLabel,item.getString("sha"),new Settings(),file,null);}}catch(Exception ignored){}return row;}
        };list.setAdapter(adapter);
        Runnable filter=()->{if(!showing[0])return;filtered.clear();adapter.setNotifyOnChange(false);adapter.clear();JSONArray items=cached(repository);String q=search.getText().toString().toLowerCase(Locale.ROOT);for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item!=null&&item.optString("name").toLowerCase(Locale.ROOT).contains(q)){filtered.add(item);adapter.add(item.optString("name"));}}adapter.notifyDataSetChanged();status.setText(filtered.size()+" LUT · "+prefs.getString("online.status","Danh sách đã lưu"));};
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){filter.run();}});
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("LUT trực tuyến").setView(content).setPositiveButton("Tìm / cập nhật",null).setNeutralButton("Đổi nguồn",null).setNegativeButton("Đóng",null).create();
        dialog.setOnDismissListener(d->{showing[0]=false;thumbs.close();sessions.remove(thumbs);});list.setOnItemClickListener((p,v,index,id)->details(filtered.get(index),()->{filter.run();},file->{dialog.dismiss();use.accept(file);}));dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{status.setText("Đang tìm LUT và kiểm tra phiên bản…");refresh(repository,filter);});dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{EditText input=new EditText(activity);input.setSingleLine();input.setText(repository);new AlertDialog.Builder(activity).setTitle("Nguồn GitHub công khai").setMessage("Nhập owner/repository. Nguồn mẫu dùng sRGB; nguồn khác cần kiểm tra hướng dẫn màu và giấy phép.").setView(input).setPositiveButton("Lưu",(d,w)->{try{String name=OnlineLuts.repository(input.getText().toString());prefs.edit().putString("online.repo",name).remove("online.checked").remove("online.status").apply();dialog.dismiss();show(photo,use);}catch(Exception e){notice(e.getMessage());}}).setNegativeButton("Hủy",null).show();});filter.run();
    }
    private void details(JSONObject item,Runnable reload,Consumer<File> use){
        String description=item.optString("path")+"\n"+state(item)+"\n\nĐầu vào: "+item.optString("input")+"\nGiấy phép kho: "+item.optString("license")+"\nNguồn: "+item.optString("repo")+"\n\nDùng LUT sẽ gắn vào film đang chọn. Nguồn chưa rõ đầu vào có thể không phù hợp JPEG.";
        AlertDialog d=new AlertDialog.Builder(activity).setTitle(item.optString("name")).setMessage(description).setPositiveButton("Tải / dùng LUT",null).setNeutralButton("Xem nguồn",(v,w)->activity.startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://github.com/"+item.optString("repo"))))).setNegativeButton("Đóng",null).create();d.show();d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);d.getButton(AlertDialog.BUTTON_POSITIVE).setText("Đang tải…");worker.execute(()->{try{File file=OnlineLuts.download(activity.getFilesDir(),item);prefs.edit().putString(installedKey(item),item.getString("sha")).apply();post(()->{if(!d.isShowing())return;d.dismiss();reload.run();use.accept(file);});}catch(Exception e){post(()->{notice(e.getMessage());if(d.isShowing()){d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);d.getButton(AlertDialog.BUTTON_POSITIVE).setText("Thử lại");}});}});});
    }
    @Override public void close(){closed=true;for(ThumbnailSession session:sessions)session.close();sessions.clear();worker.shutdownNow();main.removeCallbacksAndMessages(null);}
}

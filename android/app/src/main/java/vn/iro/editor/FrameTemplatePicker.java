package vn.iro.editor;

import android.app.*;
import android.graphics.Bitmap;
import android.view.*;
import android.widget.*;
import java.util.function.IntConsumer;

/** Small local thumbnail gallery; previews and exports use the exact same renderer. */
final class FrameTemplatePicker {
    static void show(Activity activity,Bitmap photo,PhotoInfo info,int selected,IntConsumer choose){
        float density=activity.getResources().getDisplayMetrics().density;
        LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(12,4,12,4);
        Spinner groups=new Spinner(activity);groups.setAdapter(new ArrayAdapter<String>(activity,android.R.layout.simple_spinner_dropdown_item,PhotoFrame.GROUPS));root.addView(groups);
        ScrollView scroll=new ScrollView(activity);LinearLayout rows=new LinearLayout(activity);rows.setOrientation(LinearLayout.VERTICAL);scroll.addView(rows);root.addView(scroll,new LinearLayout.LayoutParams(-1,(int)(Math.min(440,activity.getResources().getConfiguration().screenHeightDp*.55f)*density)));
        float scale=Math.min(1,220f/Math.max(photo.getWidth(),photo.getHeight()));Bitmap thumbnail=Bitmap.createScaledBitmap(photo,Math.max(1,Math.round(photo.getWidth()*scale)),Math.max(1,Math.round(photo.getHeight()*scale)),true);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle((PhotoFrame.STYLES.length-1)+" mẫu khung ảnh").setView(root).setNegativeButton("Đóng",null).setNeutralButton("Không dùng khung",(d,n)->choose.accept(0)).create();
        groups.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> p){}
            public void onItemSelected(AdapterView<?> parent,View v,int group,long id){
                rows.removeAllViews();LinearLayout row=null;int count=0;
                for(int style=1;style<PhotoFrame.STYLES.length;style++){
                    if(group==5?style<19:group!=0&&PhotoFrame.group(style)!=group)continue;
                    if(count++%2==0){row=new LinearLayout(activity);rows.addView(row,new LinearLayout.LayoutParams(-1,-2));}
                    final int chosen=style;LinearLayout card=new LinearLayout(activity);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(8,8,8,8);card.setBackground(StudioStyle.surface(StudioStyle.SURFACE,chosen==selected?StudioStyle.ACCENT:StudioStyle.LINE,12*density));
                    ImageView image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImageBitmap(PhotoFrame.render(thumbnail,info,style));card.addView(image,new LinearLayout.LayoutParams(-1,(int)(132*density)));
                    TextView name=new TextView(activity);name.setText((chosen==selected?"✓ ":"")+PhotoFrame.STYLES[style]);name.setTextSize(12);name.setTextColor(StudioStyle.INK);name.setMinHeight((int)(42*density));name.setGravity(Gravity.CENTER);card.addView(name);
                    card.setContentDescription("Chọn khung "+PhotoFrame.STYLES[style]);card.setOnClickListener(clicked->{dialog.dismiss();choose.accept(chosen);});
                    LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,-2,1);cell.setMargins(4,4,4,4);row.addView(card,cell);
                }
                if(count%2!=0&&row!=null)row.addView(new View(activity),new LinearLayout.LayoutParams(0,1,1));
            }
        });dialog.show();
    }
}

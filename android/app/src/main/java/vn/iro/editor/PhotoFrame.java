package vn.iro.editor;

import android.graphics.*;
import android.text.TextUtils;

/** Shared preview/export renderer. Frame styles add canvas; overlay preserves dimensions. */
final class PhotoFrame {
    static final String[] STYLES={"Không chèn thông tin","Classic · dải trắng","White · cột thông tin","Glass · chữ trên ảnh","Dark · dải đen","Centered · thông số giữa","Gallery · viền trắng rộng","Noir · viền đen rộng","Instant · ảnh lấy liền","Vintage · giấy kem","Film 35 · viền lỗ phim","Cinema · thanh đen","Blur · nền ảnh mờ","Palette · bảng màu ảnh","Postcard · bưu thiếp","Editorial · trang tạp chí","Double · viền kép","Date · dấu ngày màu cam","Signature · chữ ký tối giản","Float · ảnh nổi đổ bóng","Soft · viền bo tròn","Torn · giấy xé","Tape · băng dính scrapbook","Wood · khung gỗ","Gold · viền kim loại","Neon · ánh sáng viền","Notebook · giấy kẻ ô","Ticket · vé kỷ niệm","Blueprint · bản vẽ xanh","Arch · vòm triển lãm","Corners · góc giữ ảnh"};
    static final String[] GROUPS={"Tất cả","Thông số máy","Tối giản","Film / hoài cổ","Sáng tạo","Mẫu mới"};
    static int group(int style){if(style==19||style==20||style==30)return 2;if(style==21||style==22||style==23||style==27)return 3;if(style==1||style==2||style==4||style==5)return 1;if(style==3||style==6||style==7||style==16||style==18)return 2;if(style==8||style==9||style==10||style==11||style==17)return 3;return 4;}
    static Bitmap render(Bitmap photo,PhotoInfo info,int style){
        if(style<1||style>=STYLES.length)return photo;
        if(style>=19)return decorative(photo,info,style);
        if(style>3)return extended(photo,info,style);
        int w=photo.getWidth(),h=photo.getHeight(),pad=Math.max(1,Math.round(w*.025f));
        int footer=Math.max(1,Math.round(w*.125f)),side=Math.max(1,Math.round(w*.30f));
        int ow=style==2?w+side+pad*2:w,oh=style==1?h+footer:style==2?h+pad*2:h;
        Bitmap out=Bitmap.createBitmap(ow,oh,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);c.drawColor(Color.WHITE);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);c.drawBitmap(photo,style==2?pad:0,style==2?pad:0,p);
        String title=info.camera.isEmpty()?"PHOTO":info.camera;
        if(style==1){
            line(c,p,title,pad,h+footer*.37f,w*.43f,w*.028f,0xff202520,true);
            line(c,p,info.author,pad,h+footer*.70f,w*.43f,w*.018f,0xff687068,false);
            line(c,p,info.settings(),w*.49f,h+footer*.37f,w*.485f,w*.020f,0xff353b35,false);
            line(c,p,info.date,w*.49f,h+footer*.70f,w*.485f,w*.018f,0xff687068,false);
        }else if(style==2){
            float x=w+pad*2,width=side-pad,center=oh*.5f,unit=Math.min(w,h*1.7f);
            line(c,p,title,x,center-unit*.07f,width,unit*.032f,0xff202520,true);
            String[] rows={info.focal,join(info.aperture,info.shutter),info.iso,info.date,info.author};
            float y=center-unit*.025f;for(String row:rows)if(!row.isEmpty()){line(c,p,row,x,y,width,unit*.019f,0xff687068,false);y+=unit*.032f;}
        }else{
            float bar=Math.min(h*.35f,w*.15f),top=h-bar,unit=bar/.15f;
            p.setShader(new LinearGradient(0,top,0,h,0x08000000,0xc9000000,Shader.TileMode.CLAMP));c.drawRect(0,top,w,h,p);p.setShader(null);
            line(c,p,title,pad,top+bar*.39f,w-pad*2,unit*.031f,Color.WHITE,true);
            line(c,p,info.settings(),pad,top+bar*.65f,w-pad*2,unit*.020f,0xffeeeeee,false);
            line(c,p,join(info.date,info.author),pad,top+bar*.87f,w-pad*2,unit*.018f,0xffdddddd,false);
        }
        return out;
    }
    private static Bitmap extended(Bitmap photo,PhotoInfo info,int style){
        int w=photo.getWidth(),h=photo.getHeight();float unit=Math.min(w,h*1.7f);int pad=Math.max(1,Math.round(w*.05f)),foot=Math.max(1,Math.round(w*.15f));
        boolean overlay=style==17||style==18;
        if(style==4||style==5){pad=0;foot=Math.max(1,Math.round(w*.14f));}
        if(style==6||style==7)pad=Math.max(1,Math.round(w*.09f));
        if(style==8||style==9)foot=Math.max(1,Math.round(w*.23f));
        if(style==10)pad=Math.max(1,Math.round(w*.055f));
        if(style==11){pad=0;foot=Math.max(1,Math.round(w*.12f));}
        if(style==15){pad=Math.max(1,Math.round(w*.04f));foot=Math.max(1,Math.round(w*.20f));}
        if(overlay){pad=0;foot=0;}
        int top=style==11?foot:pad,ow=w+pad*2,oh=h+top+pad+foot;
        boolean dark=style==4||style==7||style==10||style==11||style==12;
        int bg=dark?0xff151716:(style==9||style==14?0xfff1eadb:Color.WHITE),ink=dark?0xfff2f0e7:0xff252b28,muted=dark?0xffb6bcb4:0xff68716a;
        Bitmap out=Bitmap.createBitmap(ow,oh,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);c.drawColor(bg);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        if(style==12){
            Bitmap tiny=Bitmap.createScaledBitmap(photo,18,Math.max(1,Math.round(18f*h/w)),true);
            p.setFilterBitmap(true);c.drawBitmap(tiny,null,new Rect(0,0,ow,oh),p);if(tiny!=photo)tiny.recycle();p.setColor(0x60000000);c.drawRect(0,0,ow,oh,p);
            p.setColor(0x55000000);c.drawRect(pad-w*.008f,top-w*.008f,pad+w+w*.008f,top+h+w*.018f,p);
        }
        p.setAlpha(255);c.drawBitmap(photo,pad,top,p);
        String title=info.camera.isEmpty()?"PHOTO":info.camera,details=info.settings(),bottom=join(info.date,info.author);
        if(overlay){float margin=w*.035f,base=h-unit*.035f;String text=style==17?info.date:(info.author.isEmpty()?title:info.author);
            p.setShadowLayer(unit*.006f,0,unit*.002f,0xaa000000);line(c,p,text,margin,base,w-margin*2,unit*(style==17?.036f:.028f),style==17?0xffffaa54:Color.WHITE,style==17);p.clearShadowLayer();return out;}
        float x=Math.max(pad,w*.025f),area=ow-x*2,y=top+h+foot*.35f;if(style==13)area-=w*.25f;
        if(style==8||style==9){line(c,p,info.author.isEmpty()?title:info.author,x,top+h+foot*.40f,area,w*.038f,ink,true);line(c,p,details,x,top+h+foot*.65f,area,w*.020f,muted,false);line(c,p,info.date,x,top+h+foot*.82f,area,w*.018f,muted,false);}
        else if(style==14){
            p.setColor(0xffb9ad98);p.setStrokeWidth(Math.max(1,w*.001f));c.drawLine(ow*.62f,top+h+foot*.15f,ow*.62f,oh-pad*.3f,p);
            line(c,p,title,x,y,ow*.55f,w*.030f,ink,true);line(c,p,info.author,x,y+foot*.27f,ow*.55f,w*.019f,muted,false);
            line(c,p,details,ow*.65f,y,ow*.31f,w*.017f,muted,false);line(c,p,info.date,ow*.65f,y+foot*.27f,ow*.31f,w*.017f,muted,false);
        }else if(style==15){line(c,p,"PHOTOGRAPH / "+title,x,top+h+foot*.30f,area,w*.032f,ink,true);p.setColor(ink);c.drawRect(x,top+h+foot*.43f,ow-x,top+h+foot*.436f,p);line(c,p,details,x,top+h+foot*.64f,area,w*.020f,muted,false);line(c,p,bottom,x,top+h+foot*.84f,area,w*.018f,muted,false);}
        else{
            boolean center=style==5||style==6||style==7||style==11||style==12||style==16;
            if(center){center(c,p,title,ow*.5f,y,area,w*.027f,ink,true);center(c,p,details,ow*.5f,y+foot*.25f,area,w*.019f,muted,false);center(c,p,bottom,ow*.5f,y+foot*.47f,area,w*.017f,muted,false);}
            else{line(c,p,title,x,y,area,w*.028f,ink,true);line(c,p,details,x,y+foot*.25f,area,w*.020f,muted,false);line(c,p,bottom,x,y+foot*.47f,area,w*.017f,muted,false);}
        }
        if(style==10){p.setColor(0xffdad4c5);float holeW=pad*.45f,holeH=w*.025f,step=Math.max(3,holeH*1.8f);for(float yy=top;yy<top+h-holeH;yy+=step){c.drawRoundRect(pad*.25f,yy,pad*.25f+holeW,yy+holeH,holeW*.2f,holeW*.2f,p);c.drawRoundRect(ow-pad*.70f,yy,ow-pad*.25f,yy+holeH,holeW*.2f,holeW*.2f,p);}}
        if(style==13){float swatch=w*.032f,gap=w*.007f,start=ow-pad-5*swatch-4*gap;for(int i=0;i<5;i++){p.setColor(photo.getPixel(Math.min(w-1,(i*2+1)*w/10),h/2));c.drawRect(start+i*(swatch+gap),top+h+foot*.2f,start+i*(swatch+gap)+swatch,top+h+foot*.2f+swatch,p);}}
        if(style==16){p.setColor(0xffbebeb5);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,w*.001f));float d=pad*.4f;c.drawRect(d,d,ow-d,oh-d,p);p.setStyle(Paint.Style.FILL);}
        return out;
    }
    private static Bitmap decorative(Bitmap photo,PhotoInfo info,int style){
        int w=photo.getWidth(),h=photo.getHeight();float pad=Math.max(1,w*.07f),foot=Math.max(1,w*.17f),top=style==29?pad*2.2f:pad;
        int ow=Math.round(w+pad*2),oh=Math.round(h+top+pad+foot);boolean dark=style==25||style==28;
        int bg=style==23?0xff9d7750:style==24?0xfff2eee5:style==25?0xff101122:style==28?0xff153553:style==26?0xfff7f3e7:style==21||style==22||style==27?0xffded6c7:0xffe9edf0;
        int ink=dark?0xffeef5ff:0xff2e3433,muted=dark?0xffb8cde2:0xff707572;
        Bitmap out=Bitmap.createBitmap(ow,oh,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);c.drawColor(bg);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        if(style==23){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,w*.0015f));for(int i=0;i<24;i++){float x=(i+.5f)*ow/24;p.setColor(i%2==0?0x30804524:0x25ffe6aa);Path grain=new Path();grain.moveTo(x,0);grain.cubicTo(x+pad*.4f,oh*.33f,x-pad*.3f,oh*.66f,x,oh);c.drawPath(grain,p);}p.setStyle(Paint.Style.FILL);}
        if(style==26||style==28){p.setColor(style==28?0x3048c8ff:0x284c8190);p.setStrokeWidth(Math.max(1,w*.001f));float step=Math.max(4,w*.035f);for(float x=0;x<ow;x+=step)c.drawLine(x,0,x,oh,p);for(float y=0;y<oh;y+=step)c.drawLine(0,y,ow,y,p);}
        RectF paper=new RectF(pad*.5f,top-pad*.5f,w+pad*1.5f,oh-pad*.45f);
        if(style==19){for(int i=10;i>0;i--){p.setColor(Color.argb(4,0,0,0));float d=pad*i*.035f;c.drawRoundRect(paper.left-d,paper.top+pad*.2f-d,paper.right+d,paper.bottom+pad*.2f+d,pad*.15f,pad*.15f,p);}}
        p.setColor(dark?0xff112334:Color.WHITE);
        if(style==21){Path torn=new Path();float step=Math.max(2,w*.022f);torn.moveTo(paper.left,paper.top);for(float x=paper.left;x<paper.right;x+=step)torn.lineTo(x,paper.top+pad*.09f*(float)Math.sin(x*1.3));torn.lineTo(paper.right,paper.bottom);for(float x=paper.right;x>paper.left;x-=step)torn.lineTo(x,paper.bottom+pad*.09f*(float)Math.sin(x*.7));torn.close();c.drawPath(torn,p);}
        else if(style==20)c.drawRoundRect(paper,pad*.42f,pad*.42f,p);
        else if(style==29){Path arch=new Path();arch.moveTo(paper.left,paper.bottom);arch.lineTo(paper.left,top);arch.cubicTo(paper.left,paper.top-pad*.8f,paper.right,paper.top-pad*.8f,paper.right,top);arch.lineTo(paper.right,paper.bottom);arch.close();c.drawPath(arch,p);}
        else c.drawRect(paper,p);
        if(style==25){p.setStyle(Paint.Style.STROKE);for(int i=8;i>0;i--){p.setStrokeWidth(Math.max(1,w*.0015f*i));p.setColor(Color.argb(14,105,220,255));c.drawRect(pad-pad*.12f,top-pad*.12f,pad+w+pad*.12f,top+h+pad*.12f,p);}p.setStrokeWidth(Math.max(1,w*.002f));p.setColor(0xffb7f0ff);c.drawRect(pad-pad*.12f,top-pad*.12f,pad+w+pad*.12f,top+h+pad*.12f,p);p.setStyle(Paint.Style.FILL);}
        p.setAlpha(255);c.drawBitmap(photo,pad,top,p);
        if(style==24){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,w*.008f));p.setShader(new LinearGradient(0,0,ow,oh,new int[]{0xff9c7637,0xffefd99b,0xffae8742},null,Shader.TileMode.CLAMP));c.drawRect(paper,p);p.setShader(null);p.setStyle(Paint.Style.FILL);}
        if(style==22){p.setColor(0xbde1c791);for(int side=0;side<2;side++){float cx=side==0?pad*1.15f:pad+w-pad*.15f;c.save();c.rotate(side==0?-24:23,cx,top);c.drawRect(cx-pad*.58f,top-pad*.22f,cx+pad*.58f,top+pad*.22f,p);c.restore();}}
        if(style==30){p.setColor(0xff3d4944);float len=pad*.55f;for(int x=0;x<2;x++)for(int y=0;y<2;y++){float cx=pad+x*w,cy=top+y*h,dx=x==0?1:-1,dy=y==0?1:-1;Path corner=new Path();corner.moveTo(cx,cy);corner.lineTo(cx+dx*len,cy);corner.lineTo(cx,cy+dy*len);corner.close();c.drawPath(corner,p);}}
        if(style==27){float y=top+h+foot*.12f;p.setColor(0xffa69c8a);p.setStrokeWidth(Math.max(1,w*.002f));for(float x=pad;x<pad+w;x+=Math.max(3,w*.024f))c.drawLine(x,y,Math.min(x+w*.012f,pad+w),y,p);p.setColor(bg);c.drawCircle(paper.left,y,pad*.18f,p);c.drawCircle(paper.right,y,pad*.18f,p);}
        String title=info.camera.isEmpty()?"PHOTO":info.camera;float baseline=top+h+foot*.39f,area=w;
        center(c,p,title,pad+w*.5f,baseline,area,w*.028f,ink,true);center(c,p,info.settings(),pad+w*.5f,baseline+foot*.24f,area,w*.019f,muted,false);center(c,p,join(info.date,info.author),pad+w*.5f,baseline+foot*.47f,area,w*.017f,muted,false);
        return out;
    }
    private static void center(Canvas c,Paint p,String text,float cx,float baseline,float width,float size,int color,boolean bold){
        p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));p.setTextSize(Math.max(1,size));float measured=Math.min(width,p.measureText(text));line(c,p,text,cx-measured/2,baseline,width,size,color,bold);
    }
    private static String join(String a,String b){return a.isEmpty()?b:b.isEmpty()?a:a+"  ·  "+b;}
    private static void line(Canvas c,Paint p,String text,float x,float baseline,float width,float size,int color,boolean bold){
        if(text.isEmpty())return;p.setColor(color);p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));p.setTextSize(Math.max(1,size));
        if(p.measureText(text)>width)p.setTextSize(Math.max(size*.65f,size*width/p.measureText(text)));
        android.text.TextPaint tp=new android.text.TextPaint(p);String fitted=TextUtils.ellipsize(text,tp,width,TextUtils.TruncateAt.END).toString();c.drawText(fitted,x,baseline,p);
    }
}

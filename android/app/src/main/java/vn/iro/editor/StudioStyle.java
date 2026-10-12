package vn.iro.editor;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.View;

/** Shared visual language. Decorative artwork is not a filter preview. */
final class StudioStyle {
    static final int BG=0xff121614, SURFACE=0xff1d2420, LINE=0xff354038,
        INK=0xfff2f0e7, MUTED=0xffa2afa4, ACCENT=0xffd7e9ae;
    static GradientDrawable shape(int fill,int stroke,float radius){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(radius);
        if(stroke!=0)d.setStroke(1,stroke);return d;
    }
    static Drawable surface(int fill,int stroke,float radius){
        return new RippleDrawable(ColorStateList.valueOf(0x26d7e9ae),shape(fill,stroke,radius),shape(0xffffffff,0,radius));
    }
    static ColorStateList inkStates(){return new ColorStateList(new int[][]{new int[]{android.R.attr.state_selected},new int[]{}},new int[]{ACCENT,INK});}
    static class Icon extends Drawable {
        final String kind;final int tint,size;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(String kind,int tint,int size){this.kind=kind;this.tint=tint;this.size=size;}
        public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}
        public void draw(Canvas c){c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/24f,getBounds().height()/24f);p.setColor(tint);p.setStrokeWidth(1.6f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStyle(Paint.Style.STROKE);
            if(kind.equals("photo")){c.drawRoundRect(3,4,21,20,3,3,p);c.drawCircle(8,9,1.5f,p);Path path=new Path();path.moveTo(4,17);path.lineTo(10,12);path.lineTo(14,16);path.lineTo(17,13);path.lineTo(21,17);c.drawPath(path,p);}
            else if(kind.equals("sliders")){for(int y=6;y<=18;y+=6){c.drawLine(3,y,21,y,p);p.setStyle(Paint.Style.FILL);c.drawCircle(y==12?15:9,y,2.6f,p);p.setStyle(Paint.Style.STROKE);}}
            else if(kind.equals("film")){c.drawRoundRect(4,3,20,21,2,2,p);c.drawLine(7,3,7,21,p);c.drawLine(17,3,17,21,p);for(int y=7;y<21;y+=5){c.drawLine(4,y,7,y,p);c.drawLine(17,y,20,y,p);}}
            else if(kind.equals("export")){c.drawLine(12,15,12,3,p);c.drawLine(7,8,12,3,p);c.drawLine(12,3,17,8,p);Path path=new Path();path.moveTo(4,14);path.lineTo(4,21);path.lineTo(20,21);path.lineTo(20,14);c.drawPath(path,p);}
            else if(kind.equals("compare")){c.drawCircle(12,12,9,p);c.drawLine(12,3,12,21,p);p.setStyle(Paint.Style.FILL);c.drawArc(3,3,21,21,90,180,true,p);}
            c.restore();
        }
        public void setAlpha(int alpha){p.setAlpha(alpha);}public void setColorFilter(ColorFilter filter){p.setColorFilter(filter);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    static class EmptyArtwork extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        EmptyArtwork(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();p.setShader(new LinearGradient(0,0,w,h,new int[]{0xff26362d,0xff141c18},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
            p.setColor(0x0cd7e9ae);p.setStrokeWidth(1);for(float x=24;x<w;x+=32)c.drawLine(x,0,x,h,p);for(float y=24;y<h;y+=32)c.drawLine(0,y,w,y,p);
            float side=Math.min(w*.38f,h*.33f);float cx=w/2,cy=h*.36f;c.save();c.rotate(-9,cx,cy);p.setColor(0xff556451);c.drawRoundRect(cx-side*.55f,cy-side*.6f,cx+side*.55f,cy+side*.7f,9,9,p);c.restore();
            p.setColor(0xffe4e4d4);c.drawRoundRect(cx-side*.55f,cy-side*.65f,cx+side*.55f,cy+side*.68f,8,8,p);
            p.setShader(new LinearGradient(cx,cy-side*.55f,cx,cy+side*.4f,new int[]{0xff8cafa0,0xff4c695a},null,Shader.TileMode.CLAMP));c.drawRect(cx-side*.46f,cy-side*.55f,cx+side*.46f,cy+side*.4f,p);p.setShader(null);
            p.setColor(0xffe5d5a0);c.drawCircle(cx+side*.20f,cy-side*.27f,side*.10f,p);Path hill=new Path();hill.moveTo(cx-side*.46f,cy+side*.1f);hill.lineTo(cx-side*.15f,cy-side*.17f);hill.lineTo(cx+side*.46f,cy+side*.4f);hill.lineTo(cx-side*.46f,cy+side*.4f);hill.close();p.setColor(0xff334e41);c.drawPath(hill,p);
        }
    }
}

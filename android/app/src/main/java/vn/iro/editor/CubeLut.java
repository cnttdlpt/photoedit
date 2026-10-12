package vn.iro.editor;
import java.io.*;
import java.util.*;

/** Standard .cube RGB ordering: red changes fastest. */
public final class CubeLut {
    public final int size;
    public final float[] values, min, max;
    public final String title;
    private CubeLut(int s,float[] v,float[] lo,float[] hi,String name){size=s;values=v;min=lo;max=hi;title=name;}
    public static CubeLut parse(Reader reader) throws IOException {
        BufferedReader r=new BufferedReader(reader);int size=0,count=0,lineNumber=0;
        float[] data=null,lo={0,0,0},hi={1,1,1};String title="LUT tùy chỉnh",line;
        try { while((line=r.readLine())!=null){
            if(++lineNumber>300000)throw new IOException("LUT vượt giới hạn số dòng");
            line=line.replaceFirst("#.*$","").trim();if(line.isEmpty())continue;
            String[] p=line.split("\\s+");
            switch(p[0]) {
                case "TITLE": title=line.substring(5).trim().replace("\"","");break;
                case "LUT_1D_SIZE": throw new IOException("Chỉ hỗ trợ LUT 3D .cube");
                case "LUT_3D_SIZE":
                    if(data!=null||p.length!=2)throw new IOException("Kích thước LUT bị lặp hoặc sai");
                    size=Integer.parseInt(p[1]);if(size<2||size>65)throw new IOException("LUT cần từ 2 đến 65 điểm mỗi chiều");
                    data=new float[size*size*size*3];break;
                case "DOMAIN_MIN":lo=triple(p);break;
                case "DOMAIN_MAX":hi=triple(p);break;
                default:
                    if(p.length!=3||data==null||count+3>data.length)throw new IOException("Dữ liệu LUT không khớp kích thước");
                    for(String n:p){float v=Float.parseFloat(n);if(!Float.isFinite(v))throw new IOException("Giá trị LUT không hữu hạn");data[count++]=v;}
            }
        }}catch(NumberFormatException e){throw new IOException("Số không hợp lệ ở dòng "+lineNumber,e);}
        if(data==null||count!=data.length)throw new IOException("LUT thiếu điểm màu");
        for(int i=0;i<3;i++)if(!Float.isFinite(lo[i])||!Float.isFinite(hi[i])||lo[i]>=hi[i])throw new IOException("Miền màu không hợp lệ");
        return new CubeLut(size,data,lo,hi,title);
    }
    private static float[] triple(String[] p)throws IOException{if(p.length!=4)throw new IOException("Miền LUT cần 3 giá trị");return new float[]{Float.parseFloat(p[1]),Float.parseFloat(p[2]),Float.parseFloat(p[3])};}
    /** Writes into caller-owned output to avoid allocating for every pixel. */
    public void sample(double r,double g,double b,double[] out){
        double vr=clamp((r-min[0])/(max[0]-min[0]))*(size-1),vg=clamp((g-min[1])/(max[1]-min[1]))*(size-1),vb=clamp((b-min[2])/(max[2]-min[2]))*(size-1);
        int ir=(int)vr,ig=(int)vg,ib=(int)vb;double fr=vr-ir,fg=vg-ig,fb=vb-ib;
        Arrays.fill(out,0);
        for(int z=0;z<2;z++)for(int y=0;y<2;y++)for(int x=0;x<2;x++){
            int i=(Math.min(ir+x,size-1)+Math.min(ig+y,size-1)*size+Math.min(ib+z,size-1)*size*size)*3;
            double w=(x==0?1-fr:fr)*(y==0?1-fg:fg)*(z==0?1-fb:fb);
            for(int k=0;k<3;k++)out[k]+=values[i+k]*w;
        }
    }
    private static double clamp(double x){return Math.max(0,Math.min(1,x));}
}

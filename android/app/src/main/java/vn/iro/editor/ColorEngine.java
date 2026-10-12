package vn.iro.editor;
import java.util.concurrent.CancellationException;

/** sRGB post-processing. Film color comes ONLY from an explicitly imported LUT. */
public final class ColorEngine {
    private ColorEngine(){}
    private static double clamp(double x){return Math.max(0,Math.min(1,x));}
    private static double linear(double x){return x<=.04045?x/12.92:Math.pow((x+.055)/1.055,2.4);}
    private static double gamma(double x){return x<=.0031308?12.92*x:1.055*Math.pow(x,1/2.4)-.055;}
    private static int byteValue(double x){return (int)Math.round(clamp(x)*255);}
    private static double luma(double r,double g,double b){return .2126*r+.7152*g+.0722*b;}
    public static int[] process(int[] pixels,int w,int h,Settings s,CubeLut lut) {
        if(w<1||h<1||(long)w*h!=pixels.length)throw new IllegalArgumentException("Invalid image dimensions");
        int[] out=new int[pixels.length];double[] rgb=new double[3],mapped=new double[3];double ev=Math.pow(2,s.exposure);
        for(int i=0;i<pixels.length;i++) {
            if((i&16383)==0&&Thread.currentThread().isInterrupted())throw new CancellationException();
            int p=pixels[i];rgb[0]=((p>>>16)&255)/255.;rgb[1]=((p>>>8)&255)/255.;rgb[2]=(p&255)/255.;
            // White-balance shift on an already developed image is an approximation.
            rgb[0]=gamma(Math.max(0,linear(rgb[0])*ev*Math.exp(s.red*.023+s.temperature*.002)));
            rgb[1]=gamma(Math.max(0,linear(rgb[1])*ev));
            rgb[2]=gamma(Math.max(0,linear(rgb[2])*ev*Math.exp(s.blue*.023-s.temperature*.002)));
            if(lut!=null){lut.sample(rgb[0],rgb[1],rgb[2],mapped);for(int k=0;k<3;k++)rgb[k]+=(mapped[k]-rgb[k])*s.lutAmount/100;}
            double lum=luma(rgb[0],rgb[1],rgb[2]),shadow=Math.pow(1-clamp(lum),2),high=Math.pow(clamp(lum),2);
            double max=Math.max(rgb[0],Math.max(rgb[1],rgb[2])),min=Math.min(rgb[0],Math.min(rgb[1],rgb[2]));
            double chroma=max-min,blueWeight=Math.max(0,rgb[2]-Math.max(rgb[0],rgb[1]));
            // DR is highlight compression here, not the camera's capture-level DR mode.
            double tone=s.highlight*.035*high-s.shadow*.035*shadow-(s.dr-100)/300.*.10*Math.pow(clamp(lum),3);
            int gx=(i%w)/(int)Math.max(1,s.grainSize),gy=(i/w)/(int)Math.max(1,s.grainSize);
            int hash=(gx*374761393+gy*668265263)^0x5a1f1234;hash=(hash^(hash>>>13))*1274126177;
            double noise=(((hash>>>8)&65535)/65535.-.5)*s.grain/255.;
            for(int k=0;k<3;k++)rgb[k]=lum+(rgb[k]-lum)*(1+s.color*.08)+tone-s.colorChrome*.012*chroma-s.chromeBlue*.04*blueWeight+noise;
            out[i]=(p&0xff000000)|(byteValue(rgb[0])<<16)|(byteValue(rgb[1])<<8)|byteValue(rgb[2]);
        }
        if(s.nr>-4||s.sharpness!=0||s.clarity!=0){
            int[] src=out.clone();double noiseAmount=(s.nr+4)/8*.65;
            for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){
                if((x&255)==0&&Thread.currentThread().isInterrupted())throw new CancellationException();
                int i=y*w+x,p=src[i],result=p&0xff000000;
                for(int shift=0;shift<=16;shift+=8){
                    double v=(p>>>shift)&255,avg=(((src[i-1]>>>shift)&255)+((src[i+1]>>>shift)&255)+((src[i-w]>>>shift)&255)+((src[i+w]>>>shift)&255))/4.;
                    double filtered=v+(avg-v)*noiseAmount+(v-avg)*s.sharpness*.18;
                    if(s.clarity!=0&&x>=4&&x<w-4&&y>=4&&y<h-4){double broad=(((src[i-4]>>>shift)&255)+((src[i+4]>>>shift)&255)+((src[i-4*w]>>>shift)&255)+((src[i+4*w]>>>shift)&255))/4.;filtered+=(v-broad)*s.clarity*.06;}
                    result|=byteValue(filtered/255.)<<shift;
                }out[i]=result;
            }
        }return out;
    }
}

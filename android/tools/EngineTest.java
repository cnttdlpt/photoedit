package vn.iro.editor;
import java.io.*;
import java.util.*;

public class EngineTest {
    static int checks=0;
    static void check(boolean condition,String name){checks++;if(!condition)throw new AssertionError(name);}
    static final String IDENTITY="TITLE \"Identity\"\nLUT_3D_SIZE 2\n0 0 0\n1 0 0\n0 1 0\n1 1 0\n0 0 1\n1 0 1\n0 1 1\n1 1 1\n";
    static void invalid(String text){try{CubeLut.parse(new StringReader(text));throw new AssertionError("Invalid LUT accepted");}catch(IOException good){checks++;}}
    public static void main(String[] args)throws Exception{
        CubeLut lut=CubeLut.parse(new StringReader(IDENTITY));double[] out=new double[3];
        lut.sample(.25,.67,.9,out);check(Math.abs(out[0]-.25)<1e-6&&Math.abs(out[1]-.67)<1e-6&&Math.abs(out[2]-.9)<1e-6,"trilinear identity");
        lut.sample(-10,3,0,out);check(Arrays.equals(out,new double[]{0,1,0}),"clamp outside domain");
        invalid(IDENTITY+"0 0 0");invalid(IDENTITY.replace("1 1 1\n","NaN 1 1\n"));invalid("LUT_1D_SIZE 2");invalid("DOMAIN_MIN 1 0 0\n"+IDENTITY);invalid(IDENTITY.replace("LUT_3D_SIZE 2","LUT_3D_SIZE 66"));
        int[] p={0x00124398,0xffabcdef,0xff020301,0x80526284,0xff000000,0xffffffff,0xff808080,0xff8fa5bb,0xff413721};
        check(Arrays.equals(p,ColorEngine.process(p,3,3,new Settings(),null)),"neutral preserves every byte and alpha");
        check(Arrays.equals(p,ColorEngine.process(p,3,3,new Settings(),lut)),"identity LUT preserves pixels");
        Settings s=new Settings();s.film="Classic Chrome";check(Arrays.equals(p,ColorEngine.process(p,3,3,s,null)),"no fake film transform when profile is absent");
        s.exposure=1;int result=ColorEngine.process(new int[]{0xff808080},1,1,s,null)[0];check((result&255)>170&&(result&255)<180,"EV is applied in linear light");
        s=new Settings();s.grain=18;check(Arrays.equals(ColorEngine.process(p,3,3,s,null),ColorEngine.process(p,3,3,s,null)),"grain deterministic between renders");
        s=new Settings();s.nr=4;int[] noisy={0xff000000,0xff000000,0xff000000,0xff000000,0xffffffff,0xff000000,0xff000000,0xff000000,0xff000000};check((ColorEngine.process(noisy,3,3,s,null)[4]&255)<255,"NR smooths a center impulse");
        Thread.currentThread().interrupt();try{ColorEngine.process(p,3,3,new Settings(),null);throw new AssertionError("render not cancelled");}catch(java.util.concurrent.CancellationException expected){checks++;}finally{Thread.interrupted();}
        System.out.println("PASS: "+checks+" native engine checks");
    }
}

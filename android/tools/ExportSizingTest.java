package vn.iro.editor;
public class ExportSizingTest {
    public static void main(String[] args){
        int[] original=ExportSizing.dimensions(2000,1500,2,true,4000000);
        if(original[0]*2!=4000||original[1]*2!=3000)throw new AssertionError("2x dimensions");
        int[] standard=ExportSizing.dimensions(8000,6000,1,false,50000000);
        if(standard[0]!=4000||standard[1]!=3000)throw new AssertionError("12 MP standard");
        int[] small=ExportSizing.dimensions(400,300,1,false,1000000);
        if(small[0]!=400||small[1]!=300)throw new AssertionError("No unwanted enlargement");
        boolean rejected=false;try{ExportSizing.dimensions(6000,4000,2,true,10000000);}catch(IllegalArgumentException e){rejected=true;}
        if(!rejected)throw new AssertionError("Never silently downscale full-size export");
        rejected=false;try{ExportSizing.dimensions(50000,50000,2,true,Long.MAX_VALUE);}catch(IllegalArgumentException e){rejected=true;}
        if(!rejected)throw new AssertionError("Overflow protection");
        int[] limited=ExportSizing.dimensions(4000,3000,1,false,3000000);
        if(limited[0]!=2000||limited[1]!=1500)throw new AssertionError("Memory limit");
        System.out.println("PASS: 6 export sizing checks");
    }
}

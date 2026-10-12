package vn.iro.editor;

/** Checks source and output sizes before allocating full-resolution image buffers. */
final class ExportSizing {
    static int[] dimensions(int w,int h,int scale,boolean fullSize,long pixelBudget){
        if(w<1||h<1||(scale!=1&&scale!=2)||pixelBudget<1)throw new IllegalArgumentException("Kích thước xuất không hợp lệ");
        long pixels=(long)w*h;
        if(fullSize){
            if(pixels>pixelBudget||(long)w*scale>Integer.MAX_VALUE||(long)h*scale>Integer.MAX_VALUE||pixels>Integer.MAX_VALUE/(scale*scale))
                throw new IllegalArgumentException("Không đủ bộ nhớ cho độ phân giải này. Chọn kích thước gốc hoặc tiêu chuẩn; app không tự giảm kích thước ở chế độ 2×.");
            return new int[]{w,h};
        }
        double ratio=Math.min(1,Math.sqrt((double)Math.min(12000000L,pixelBudget)/pixels));
        return new int[]{Math.max(1,(int)Math.floor(w*ratio)),Math.max(1,(int)Math.floor(h*ratio))};
    }
}

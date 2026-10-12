package vn.iro.editor;

/** Editor adjustments, not a reimplementation of Fujifilm's proprietary camera pipeline. */
public final class Settings {
    public double exposure=0, highlight=0, shadow=0, color=0, red=0, blue=0,
        temperature=0, sharpness=0, nr=-4, grain=0, grainSize=1, clarity=0,
        colorChrome=0, chromeBlue=0, lutAmount=100;
    public int dr=100;
    public String film="Original", wb="As shot";
    public Settings copy() {
        Settings s=new Settings();
        s.exposure=exposure;s.highlight=highlight;s.shadow=shadow;s.color=color;
        s.red=red;s.blue=blue;s.temperature=temperature;s.sharpness=sharpness;s.nr=nr;
        s.grain=grain;s.grainSize=grainSize;s.clarity=clarity;s.colorChrome=colorChrome;
        s.chromeBlue=chromeBlue;s.lutAmount=lutAmount;s.dr=dr;s.film=film;s.wb=wb;
        return s;
    }
}

# IRO Studio Android Proguard Rules

# Bảo toàn các lớp Model được phản chiếu qua Reflection / JSON Codec
-keepclassmembers class vn.iro.editor.Settings {
    public <fields>;
}
-keep class vn.iro.editor.Settings { *; }
-keep class vn.iro.editor.Recipe { *; }
-keep class vn.iro.editor.PhotoInfo { *; }
-keep class vn.iro.editor.CubeLut { *; }
-keep class vn.iro.editor.CloudSyncService { *; }
-keep class vn.iro.editor.GeminiAutoService { *; }

# Bảo vệ các thành phần Android cốt lõi
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

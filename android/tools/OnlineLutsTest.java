package vn.iro.editor;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;

public class OnlineLutsTest {
    public static void main(String[] args)throws Exception {
        if(!OnlineLuts.repository("https://github.com/owner/repo/").equals("owner/repo"))throw new AssertionError("Repository URL");
        for(String bad:new String[]{"https://example.com/a/b","owner/../file","owner/..","owner/repo?token=abc","http://github.com/a/b"}){
            boolean rejected=false;try{OnlineLuts.repository(bad);}catch(IOException e){rejected=true;}
            if(!rejected)throw new AssertionError("Accepted invalid repository: "+bad);
        }
        if(!OnlineLuts.blobHash("hello\n".getBytes(StandardCharsets.UTF_8)).equals("ce013625030ba8dba906f756967f9e9ca394464a"))throw new AssertionError("Git blob checksum");
        if(args.length==2){
            byte[] data=Files.readAllBytes(Path.of(args[0]));String expected=Files.readString(Path.of(args[1])).trim();
            if(!OnlineLuts.blobHash(data).equals(expected))throw new AssertionError("Downloaded checksum");
            CubeLut.parse(new InputStreamReader(new ByteArrayInputStream(data),StandardCharsets.UTF_8));
            System.out.println("PASS: live-source LUT checksum and 3D parser");
        }
        System.out.println("PASS: repository validation and Git checksum");
    }
}

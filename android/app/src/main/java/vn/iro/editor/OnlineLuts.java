package vn.iro.editor;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Public GitHub discovery only; never sends photos, never executes downloaded files. */
final class OnlineLuts {
    static final String DEFAULT_REPO="t0saki/lumix-original-looks";
    static final int MAX_LUT=35*1024*1024;
    static String repository(String input)throws IOException{
        String s=input.trim().replaceFirst("^https://github\\.com/","").replaceFirst("/+$","");
        if(!s.matches("[A-Za-z0-9_-]{1,100}/[A-Za-z0-9_.-]{1,100}")||s.endsWith("/..")||s.endsWith("/."))throw new IOException("Nhập owner/repository hoặc URL kho GitHub công khai.");return s;
    }
    static byte[] fetch(String url,int limit)throws IOException{
        HttpURLConnection connection=null;
        try{
            URL u=new URL(url);if(!u.getProtocol().equals("https")||!(u.getHost().equals("api.github.com")||u.getHost().equals("raw.githubusercontent.com")))throw new IOException("Nguồn tải không được hỗ trợ");
            connection=(HttpURLConnection)u.openConnection();connection.setConnectTimeout(15000);connection.setReadTimeout(20000);connection.setInstanceFollowRedirects(false);connection.setRequestProperty("User-Agent","IRO-Android-LUTs");
            int status=connection.getResponseCode();if(status==403||status==429)throw new IOException("GitHub giới hạn lượt tải. Hãy thử lại sau.");if(status!=200)throw new IOException("Nguồn trả HTTP "+status);
            if(connection.getContentLengthLong()>limit)throw new IOException("File vượt giới hạn dung lượng");
            try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;long deadline=System.nanoTime()+60000000000L;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Đã hủy");if(System.nanoTime()>deadline)throw new IOException("Nguồn tải quá chậm");if(out.size()+n>limit)throw new IOException("File vượt giới hạn dung lượng");out.write(buffer,0,n);}return out.toByteArray();}
        }finally{if(connection!=null)connection.disconnect();}
    }
    static JSONObject json(String url)throws Exception{return new JSONObject(new String(fetch(url,8*1024*1024),StandardCharsets.UTF_8));}
    static String segment(String s)throws Exception{return URLEncoder.encode(s,"UTF-8").replace("+","%20");}
    static String path(String s)throws Exception{StringJoiner joined=new StringJoiner("/");for(String part:s.split("/"))joined.add(segment(part));return joined.toString();}
    static JSONArray discover(String requested)throws Exception{
        String repo=repository(requested),base="https://api.github.com/repos/"+repo;
        JSONObject metadata=json(base);String branch=metadata.getString("default_branch");
        JSONObject commit=json(base+"/commits/"+segment(branch));String revision=commit.getString("sha");
        JSONObject tree=json(base+"/git/trees/"+revision+"?recursive=1");if(tree.optBoolean("truncated"))throw new IOException("Kho quá lớn để liệt kê đầy đủ; hãy dùng kho nhỏ hơn.");
        JSONArray nodes=tree.getJSONArray("tree"),result=new JSONArray();String license="Chưa xác định";JSONObject info=metadata.optJSONObject("license");if(info!=null)license=info.optString("spdx_id",license);
        for(int i=0;i<nodes.length();i++){JSONObject node=nodes.getJSONObject(i);String file=node.optString("path"),sha=node.optString("sha");if(!node.optString("type").equals("blob")||!file.toLowerCase(Locale.ROOT).endsWith(".cube")||node.optLong("size")>MAX_LUT||!sha.matches("[0-9a-f]{40}"))continue;
            if(result.length()>=1000)throw new IOException("Kho có hơn 1000 LUT; dùng kho nhỏ hơn.");
            JSONObject item=new JSONObject();item.put("repo",repo);item.put("path",file);item.put("name",file.substring(file.lastIndexOf('/')+1).replaceFirst("(?i)\\.cube$",""));item.put("sha",sha);item.put("revision",revision);item.put("bytes",node.optLong("size"));item.put("license",license);item.put("input",repo.equals(DEFAULT_REPO)?"sRGB / LUMIX Standard (theo tác giả)":"Chưa xác định — xem hướng dẫn nguồn");result.put(item);
        }return result;
    }
    static String blobHash(byte[] data)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-1");digest.update(("blob "+data.length+"\0").getBytes(StandardCharsets.US_ASCII));byte[] hash=digest.digest(data);StringBuilder b=new StringBuilder();for(byte v:hash)b.append(String.format(Locale.ROOT,"%02x",v&255));return b.toString();}
    static File localFile(File directory,JSONObject item)throws Exception{String hash=item.getString("sha");if(!hash.matches("[0-9a-f]{40}"))throw new IOException("Mã file không hợp lệ");return new File(directory,"online-"+hash+".cube");}
    static File download(File directory,JSONObject item)throws Exception{
        String repo=repository(item.getString("repo")),revision=item.getString("revision");if(!revision.matches("[0-9a-f]{40}"))throw new IOException("Phiên bản nguồn không hợp lệ");
        File target=localFile(directory,item);if(target.isFile())return target;
        byte[] data=fetch("https://raw.githubusercontent.com/"+repo+"/"+revision+"/"+path(item.getString("path")),MAX_LUT);
        if(!blobHash(data).equals(item.getString("sha")))throw new IOException("Checksum LUT không khớp nguồn");
        CubeLut.parse(new InputStreamReader(new ByteArrayInputStream(data),StandardCharsets.UTF_8));
        File temp=File.createTempFile("lut-download-",".tmp",directory);
        try{try(FileOutputStream out=new FileOutputStream(temp)){out.write(data);out.getFD().sync();}if(!temp.renameTo(target))throw new IOException("Không lưu được LUT");}finally{if(temp.exists())temp.delete();}return target;
    }
}

package com.mibox.os;

import java.util.*;
import java.util.regex.*;

/** Pure Java playlist model: independent of Android UI and player. */
public final class Playlist {
    public static final class Channel {
        public final String name, url, group, referrer, agent;
        public Channel(String n,String u,String g,String r,String a) { name=n;url=u;group=g;referrer=r;agent=a; }
    }
    private static String attr(String line,String key) {
        Matcher m=Pattern.compile(Pattern.quote(key)+"=\"([^\"]*)\"").matcher(line);
        return m.find()?m.group(1):"";
    }
    public static List<Channel> parse(String text) {
        List<Channel> result=new ArrayList<>(); Set<String> seen=new HashSet<>();
        String name="",group="",ref="",agent="";
        for(String raw:text.replace("\uFEFF", "").split("\\r?\\n")) {
            String line=raw.trim();
            if(line.startsWith("#EXTINF:")) {
                boolean quoted=false; int comma=-1;
                for(int i=0;i<line.length();i++){if(line.charAt(i)=='"')quoted=!quoted; if(line.charAt(i)==','&&!quoted){comma=i;break;}}
                name=comma>=0?line.substring(comma+1).trim():"";
                group=attr(line,"group-title");ref=attr(line,"http-referrer");agent=attr(line,"http-user-agent");
            } else if(line.startsWith("#EXTVLCOPT:http-referrer="))ref=line.substring(line.indexOf('=')+1);
            else if(line.startsWith("#EXTVLCOPT:http-user-agent="))agent=line.substring(line.indexOf('=')+1);
            else if(line.startsWith("http://")||line.startsWith("https://")) {
                if(!name.isEmpty()&&!group.toLowerCase(Locale.ROOT).contains("xxx")&&seen.add(line))result.add(new Channel(name,line,group,ref,agent));
                name="";group="";ref="";agent="";
            }
        }
        return result;
    }
}

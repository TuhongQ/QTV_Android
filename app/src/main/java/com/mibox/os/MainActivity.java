package com.mibox.os;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.net.Uri;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.text.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;

public class MainActivity extends Activity {
    private static final int PICK_VIDEO=4101, PICK_PLAYLIST=4102;
    private final int BG=0xff08111f, PANEL=0xff101d2d, PANEL2=0xff15263a, TEXT=0xfff5f7fb, MUTED=0xff9eafc4, ACCENT=0xffc9f56a, CYAN=0xff5bd6e8, PURPLE=0xffa98bff, ORANGE=0xffffb45c, PINK=0xffff789c;
    private final String[] sourceNames={"东云 IPTV · 聚合","中文","English","粤语","日本語","한국어","Français","Español","Deutsch","العربية","少儿","纪录片","音乐","全球 Free TV","公共广播台"};
    private final String[] sourcePaths={"dongyubin_github","languages/zho","languages/eng","languages/yue","languages/jpn","languages/kor","languages/fra","languages/spa","languages/deu","languages/ara","categories/kids","categories/documentary","categories/music","github_free_tv","github_public_broadcasters"};
    private final String[] sourceUrls={"bundle:dongyubin","https://iptv-org.github.io/iptv/languages/zho.m3u","https://iptv-org.github.io/iptv/languages/eng.m3u","https://iptv-org.github.io/iptv/languages/yue.m3u","https://iptv-org.github.io/iptv/languages/jpn.m3u","https://iptv-org.github.io/iptv/languages/kor.m3u","https://iptv-org.github.io/iptv/languages/fra.m3u","https://iptv-org.github.io/iptv/languages/spa.m3u","https://iptv-org.github.io/iptv/languages/deu.m3u","https://iptv-org.github.io/iptv/languages/ara.m3u","https://iptv-org.github.io/iptv/categories/kids.m3u","https://iptv-org.github.io/iptv/categories/documentary.m3u","https://iptv-org.github.io/iptv/categories/music.m3u","https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8","https://raw.githubusercontent.com/freecasthub/public-iptv/main/playlist.m3u"};
    private final String[][] dongyubinFeeds={
        {"zbefine","https://raw.githubusercontent.com/zbefine/iptv/main/iptv.m3u"},{"Vamoschuck","https://raw.githubusercontent.com/vamoschuck/TV/main/M3U"},
        {"YueChan","https://testingcf.jsdelivr.net/gh/YueChan/Live@main/IPTV.m3u"},{"BigBigGrandG","https://raw.githubusercontent.com/BigBigGrandG/IPTV-URL/release/Gather.m3u"},
        {"Kimentanm","https://raw.githubusercontent.com/Kimentanm/aptv/master/m3u/iptv.m3u"},{"YanG-1989","https://raw.githubusercontent.com/YanG-1989/m3u/main/Gather.m3u"},
        {"EPG.PW","https://epg.pw/test_channels.m3u"},{"香港频道","https://epg.pw/test_channels_hong_kong.m3u"},{"台湾频道","https://epg.pw/test_channels_taiwan.m3u"},
        {"新加坡频道","https://epg.pw/test_channels_singapore.m3u"},{"马来西亚频道","https://epg.pw/test_channels_malaysia.m3u"}
    };
    private final ExecutorService io=Executors.newFixedThreadPool(8);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private android.content.SharedPreferences prefs;
    private final List<Playlist.Channel> channels=new ArrayList<>(), visible=new ArrayList<>(), favorites=new ArrayList<>();
    private final Map<String,Boolean> availability=new ConcurrentHashMap<>();
    private final Set<String> blocked=Collections.synchronizedSet(new HashSet<>());
    private final List<Playlist.Channel> recent=new ArrayList<>();
    private ExecutorService testIo;
    private Playlist.Channel current;
    private LinearLayout root,header,body,browser,controls;
    private FrameLayout stage,videoArea;
    private VideoView video;
    private TextView status,heading,sourceStatus;
    private EditText search;
    private ListView list;
    private ChannelAdapter adapter;
    private Button fullButton,favoriteButton,pauseButton,sourceButton,videoFullButton;
    private boolean full=false,onlyFavorites=false,onlyRecent=false,onlyAvailable=false,prepared=false,paused=false,large=false,destroyed=false,inBackground=false;
    private int source=0,loadGeneration=0,playGeneration=0,testGeneration=0;
    private Runnable timeout,hideControls;
    private final Runnable hide=()->{if(full){controls.setVisibility(View.GONE);if(videoFullButton!=null)videoFullButton.setVisibility(View.GONE);}};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING); prefs=getSharedPreferences("mibox",MODE_PRIVATE);large=prefs.getBoolean("large",false);source=prefs.getInt("source",0);if(!prefs.getBoolean("sourceIndexV11",false)){if(prefs.contains("source"))source=Math.min(source+1,sourcePaths.length-1);prefs.edit().putBoolean("sourceIndexV11",true).putInt("source",source).apply();}
        if(source<0||source>=sourcePaths.length)source=0;
        loadFavorites();loadRecent();loadBlocked();buildUI();sourceButton.requestFocus();loadSource(false);
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(String text,int size,int color){TextView t=new TextView(this);t.setText(text);t.setTextSize(size+(large?3:0));t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private GradientDrawable bg(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));return d;}
    private GradientDrawable gradient(int start,int end,float radius){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{start,end});d.setCornerRadius(dp(radius));return d;}
    private Button button(String text,Runnable action){
        Button b=new Button(this);b.setText(text);b.setTextSize(large?16:13);b.setTextColor(TEXT);b.setAllCaps(false);b.setMinHeight(dp(40));b.setMinimumWidth(dp(44));b.setPadding(dp(10),dp(2),dp(10),dp(2));
        StateListDrawable states=new StateListDrawable();states.addState(new int[]{android.R.attr.state_pressed},bg(0xff53682e));states.addState(new int[]{android.R.attr.state_focused},bg(0xff53682e));states.addState(new int[]{},bg(0xff283345));b.setBackground(states);
        b.setOnClickListener(v->action.run());return b;
    }
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private void addButton(LinearLayout row,Button b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(40));p.setMargins(dp(2),dp(2),dp(2),dp(2));row.addView(b,p);}
    private void buildUI(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackground(gradient(0xff050914,0xff15102a,0));setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(dp(10)+insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop()+dp(2),dp(10)+insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()+dp(2));return insets;});
        // —— 紧凑头部：小 logo + QTV 单行标题 ——
        header=row();header.setPadding(dp(2),dp(2),0,dp(6));TextView mark=label("Q",14,0xff07111f);mark.setGravity(Gravity.CENTER);mark.setTypeface(null,Typeface.BOLD);mark.setBackground(gradient(ACCENT,CYAN,8));header.addView(mark,new LinearLayout.LayoutParams(dp(30),dp(30)));TextView brand=label("QTV",18,TEXT);brand.setTypeface(null,Typeface.BOLD);brand.setGravity(Gravity.CENTER_VERTICAL);header.addView(brand,new LinearLayout.LayoutParams(-2,-2));TextView tagline=label("家庭影音中心",11,MUTED);tagline.setGravity(Gravity.CENTER_VERTICAL);tagline.setPadding(dp(9),dp(3),0,0);header.addView(tagline,new LinearLayout.LayoutParams(0,-2,1));addButton(header,button("＋ 媒体",this::openVideoMenu));addButton(header,button("⋯ 更多",this::more));root.addView(header);
        body=new LinearLayout(this);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        // —— 视频舞台 ——
        stage=new FrameLayout(this);stage.setBackground(gradient(0xff02050b,0xff10182b,16));videoArea=new FrameLayout(this);stage.addView(videoArea,new FrameLayout.LayoutParams(-1,-1));
        status=label("选一个频道，开始看电视",15,TEXT);status.setGravity(Gravity.CENTER);status.setPadding(dp(10),dp(10),dp(10),dp(10));stage.addView(status,new FrameLayout.LayoutParams(-1,-1));
        controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);controls.setPadding(dp(4),0,dp(4),dp(2));controls.setBackgroundColor(0xdd101720);
        heading=label("欢迎回家 · 点击频道播放",13,TEXT);heading.setMaxLines(1);heading.setEllipsize(TextUtils.TruncateAt.END);controls.addView(heading,new LinearLayout.LayoutParams(-1,dp(24)));
        HorizontalScrollView sc=new HorizontalScrollView(this);sc.setHorizontalScrollBarEnabled(false);LinearLayout buttons=row();sc.addView(buttons);controls.addView(sc);
        addButton(buttons,button("上一台",()->next(-1)));pauseButton=button("暂停",this::togglePause);addButton(buttons,pauseButton);addButton(buttons,button("下一台",()->next(1)));
        favoriteButton=button("☆ 收藏",this::toggleFavorite);addButton(buttons,favoriteButton);fullButton=button("全屏",()->setFull(!full));addButton(buttons,fullButton);
        stage.addView(controls,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        stage.setOnClickListener(v->showControls());
        // —— 频道浏览器（主体）——
        browser=new LinearLayout(this);browser.setPadding(dp(10),dp(6),dp(10),dp(2));browser.setBackground(gradient(0xff0d1928,0xff17132c,14));browser.setOrientation(LinearLayout.VERTICAL);
        HorizontalScrollView filterScroll=new HorizontalScrollView(this);filterScroll.setHorizontalScrollBarEnabled(false);LinearLayout filters=row();sourceButton=button(sourceNames[source]+" ▾",this::chooseSource);addButton(filters,sourceButton);addButton(filters,button("全部",()->{onlyFavorites=false;onlyRecent=false;onlyAvailable=false;refreshList();}));addButton(filters,button("★ 收藏",()->{onlyFavorites=true;onlyRecent=false;onlyAvailable=false;refreshList();}));addButton(filters,button("最近",()->{onlyRecent=true;onlyFavorites=false;onlyAvailable=false;refreshList();}));filterScroll.addView(filters);browser.addView(filterScroll,new LinearLayout.LayoutParams(-1,dp(48)));
        search=new EditText(this);search.setSingleLine(true);search.setShowSoftInputOnFocus(false);search.setFocusable(false);search.setTextSize(large?16:13);search.setTextColor(TEXT);search.setHintTextColor(MUTED);search.setHint("⌕  点这里搜索频道");search.setPadding(dp(12),0,dp(10),0);search.setBackground(bg(PANEL2));search.setOnClickListener(v->searchDialog());browser.addView(search,new LinearLayout.LayoutParams(-1,dp(40)));
        sourceStatus=label("正在准备频道…",11,MUTED);browser.addView(sourceStatus,new LinearLayout.LayoutParams(-1,dp(20)));
        list=new ListView(this);list.setDividerHeight(dp(5));list.setCacheColorHint(BG);list.setBackgroundColor(Color.TRANSPARENT);adapter=new ChannelAdapter();list.setAdapter(adapter);browser.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        list.setOnItemClickListener((p,v,pos,id)->{hideKeyboard();play(visible.get(pos));});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int c){refreshList();}public void afterTextChanged(Editable e){}});
        arrange();root.requestApplyInsets();
    }
    private boolean isTouchDevice(){return getPackageManager().hasSystemFeature("android.hardware.touchscreen");}
    private void arrange(){
        if(full){header.setVisibility(View.GONE);browser.setVisibility(View.GONE);body.setOrientation(LinearLayout.VERTICAL);stage.setLayoutParams(new LinearLayout.LayoutParams(-1,-1));stage.setVisibility(View.VISIBLE);return;}
        header.setVisibility(View.VISIBLE);browser.setVisibility(View.VISIBLE);stage.setVisibility(View.VISIBLE);
        detachIfNeeded();
        android.util.DisplayMetrics m=getResources().getDisplayMetrics();
        boolean portrait=m.heightPixels>=m.widthPixels;
        if(isTouchDevice()&&portrait){
            // 手机竖屏：视频按屏幕宽 16:9 置顶，频道列表吃掉其余全部空间
            body.setOrientation(LinearLayout.VERTICAL);
            int stageH=(int)((m.widthPixels-dp(20))*9f/16f+0.5f);
            body.addView(stage,new LinearLayout.LayoutParams(-1,stageH));
            body.addView(browser,new LinearLayout.LayoutParams(-1,0,1));
        }else{
            // 手机横屏（清单强制 landscape）/电视：视频收窄，频道区占大头
            body.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams stp;
            if(isTouchDevice()){
                int availH=m.heightPixels-dp(92);
                int ideal=(int)(availH*16f/9f+0.5f);
                int cap=(int)(m.widthPixels*0.52f);   // 视频最宽占屏 52%，频道列表保底空间
                stp=new LinearLayout.LayoutParams(Math.min(ideal,cap),-1);
                stp.rightMargin=dp(8);
            }else{
                stp=new LinearLayout.LayoutParams(0,-1,1.55f);
                stp.rightMargin=dp(12);
            }
            body.addView(stage,stp);
            body.addView(browser,new LinearLayout.LayoutParams(0,-1,1));
        }
    }
    private void detachIfNeeded(){if(stage!=null&&stage.getParent()==body)body.removeView(stage);if(browser!=null&&browser.getParent()==body)body.removeView(browser);}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);arrange();if(full)applyImmersive();}
    private void openVideoMenu(){
        final String[] items={"手机内存 / U盘 / SD卡","导入 M3U 频道清单","服务器视频地址（MP4 / M3U8）"};
        new AlertDialog.Builder(this).setTitle("媒体与播放列表").setItems(items,(d,w)->{if(w==0)openFilePicker();else if(w==1)openPlaylistPicker();else openServerVideo();}).show();
    }
    private void openFilePicker(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("video/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);try{startActivityForResult(i,PICK_VIDEO);}catch(ActivityNotFoundException e){Intent fallback=new Intent(Intent.ACTION_GET_CONTENT);fallback.setType("video/*");try{startActivityForResult(fallback,PICK_VIDEO);}catch(Exception ignored){message("没有找到可用的文件管理器");}}
    }
    private void openServerVideo(){
        EditText input=new EditText(this);input.setSingleLine(true);input.setTextSize(18);input.setTextColor(TEXT);input.setHintTextColor(MUTED);input.setHint("http://服务器/电影.mp4 或 .m3u8");input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);input.setPadding(dp(12),0,dp(12),0);
        new AlertDialog.Builder(this).setTitle("打开服务器视频").setMessage("填写服务器上的直接视频地址。支持 MP4、M3U8；SMB/NAS 文件夹请先在系统文件管理器中添加网络存储，再从“手机内存 / U盘 / SD卡”选择。入门示例：http://192.168.1.10:8080/movie.mp4").setView(input).setPositiveButton("播放",(d,w)->{String u=input.getText().toString().trim();if(u.startsWith("http://")||u.startsWith("https://"))play(new Playlist.Channel("服务器视频",u,"服务器","",""));else message("请输入 http 或 https 地址");}).setNegativeButton("返回",null).show();
    }
    private void openPlaylistPicker(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"audio/x-mpegurl","audio/mpegurl","application/vnd.apple.mpegurl","text/plain"});try{startActivityForResult(i,PICK_PLAYLIST);}catch(Exception e){message("无法打开文件选择器");}}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();if(requestCode==PICK_VIDEO){try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}String name=uri.getLastPathSegment();if(name==null||name.isEmpty())name="本地视频";play(new Playlist.Channel(name,uri.toString(),"手机本地","",""));}else if(requestCode==PICK_PLAYLIST){try(InputStream in=getContentResolver().openInputStream(uri)){String text=read(in);List<Playlist.Channel> imported=Playlist.parse(text);if(imported.isEmpty()){message("没有读取到有效频道");return;}channels.clear();channels.addAll(imported);source=0;sourceButton.setText("自定义清单 ▾");onlyFavorites=false;onlyRecent=false;refreshList();sourceStatus.setText("已导入 "+imported.size()+" 条频道");message("播放列表导入完成");}catch(Exception e){message("读取播放列表失败");}}}
    private void applyImmersive(){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);}
    private void setFull(boolean value){
        full=value;hideKeyboard();fullButton.setText(full?"退出全屏":"全屏");
        if(full)applyImmersive();else getWindow().getDecorView().setSystemUiVisibility(0);arrange();showControls();
    }
    private void showControls(){controls.setVisibility(View.VISIBLE);if(videoFullButton!=null)videoFullButton.setVisibility(View.VISIBLE);handler.removeCallbacks(hide);if(full)handler.postDelayed(hide,5000);}
    private void hideKeyboard(){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);search.clearFocus();}
    private void chooseSource(){
        new AlertDialog.Builder(this).setTitle("选择频道分类").setSingleChoiceItems(sourceNames,source,(d,which)->{source=which;onlyFavorites=false;onlyRecent=false;search.setText("");prefs.edit().putInt("source",source).apply();sourceButton.setText(sourceNames[source]+" ▾");d.dismiss();loadSource(false);}).setNegativeButton("返回",null).show();
    }
    private void searchDialog(){EditText input=new EditText(this);input.setSingleLine(true);input.setText(search.getText());input.setTextSize(18);input.setHint("输入频道名称");new AlertDialog.Builder(this).setTitle("搜索频道").setView(input).setPositiveButton("搜索",(d,w)->{search.setText(input.getText().toString());refreshList();}).setNeutralButton("清除",(d,w)->{search.setText("");refreshList();}).setNegativeButton("返回",null).show();}
    private String read(InputStream stream)throws IOException{
        try(InputStream input=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=input.read(b))!=-1){out.write(b,0,n);if(out.size()>16*1024*1024)throw new IOException("清单太大");}return out.toString("UTF-8");}
    }
    private String fetchText(String url)throws IOException{
        HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();conn.setConnectTimeout(12000);conn.setReadTimeout(20000);conn.setInstanceFollowRedirects(true);conn.setRequestProperty("User-Agent","MiBoxOS-Android/1.1");
        try{if(conn.getResponseCode()!=200)throw new IOException("HTTP "+conn.getResponseCode());return read(conn.getInputStream());}finally{conn.disconnect();}
    }
    private String mergeDongyubin(){
        List<Future<String>> tasks=new ArrayList<>();for(String[] feed:dongyubinFeeds)tasks.add(io.submit(()->{try{return fetchText(feed[1]);}catch(Exception e){return "";}}));
        Set<String> seen=new HashSet<>();StringBuilder merged=new StringBuilder("#EXTM3U\n");
        for(int i=0;i<tasks.size();i++)try{String raw=tasks.get(i).get(25,TimeUnit.SECONDS);for(Playlist.Channel c:Playlist.parse(raw)){if(!seen.add(c.url))continue;String group="东云/"+dongyubinFeeds[i][0]+(c.group.isEmpty()?"":" · "+c.group);merged.append("#EXTINF:-1 group-title=\"").append(group.replace("\"", "'")).append("\" tvg-logo=\"").append("\",").append(c.name.replace("\n"," ")).append("\n");if(!c.referrer.isEmpty())merged.append("#EXTVLCOPT:http-referrer=").append(c.referrer).append("\n");if(!c.agent.isEmpty())merged.append("#EXTVLCOPT:http-user-agent=").append(c.agent).append("\n");merged.append(c.url).append("\n");}}catch(Exception ignored){}
        if(seen.isEmpty())throw new RuntimeException("在线列表暂时无法更新");return merged.toString();
    }
    private void loadSource(boolean refresh){
        int generation=++loadGeneration;++testGeneration;if(testIo!=null)testIo.shutdownNow();availability.clear();onlyAvailable=false;String path=sourcePaths[source];String url=sourceUrls[source];sourceStatus.setText(refresh?"正在更新频道…":"正在读取频道…");channels.clear();refreshList();
        io.execute(()->{
            File cached=new File(getFilesDir(),path.replace('/','_')+".m3u");String text="";
            try{if(cached.exists())text=read(new FileInputStream(cached));else text=read(getAssets().open("playlists/"+path.replace('/','_')+".m3u"));}catch(Exception ignored){}
            final List<Playlist.Channel> bundled=Playlist.parse(text);
            runOnUiThread(()->{if(!destroyed&&generation==loadGeneration){channels.clear();channels.addAll(bundled);refreshList();sourceStatus.setText(bundled.size()+" 条频道 · 已保存清单");}});
            if(!refresh&&!bundled.isEmpty())return;
            try{
                String fetched=path.equals("dongyubin_github")?mergeDongyubin():fetchText(url);
                List<Playlist.Channel> parsed=Playlist.parse(fetched);if(parsed.isEmpty())throw new IOException("没有找到频道");
                try(FileOutputStream out=new FileOutputStream(cached,false)){out.write(fetched.getBytes("UTF-8"));}
                runOnUiThread(()->{if(!destroyed&&generation==loadGeneration){channels.clear();channels.addAll(parsed);refreshList();sourceStatus.setText(parsed.size()+" 条频道 · 刚刚更新");}});
            }catch(Exception e){runOnUiThread(()->{if(!destroyed&&generation==loadGeneration)sourceStatus.setText("更新失败 · 保留 "+channels.size()+" 条频道");});}
        });
    }
    private void refreshList(){
        String q=search==null?"":search.getText().toString().trim().toLowerCase(Locale.ROOT);visible.clear();
        List<Playlist.Channel> base=onlyRecent?recent:(onlyFavorites?favorites:channels);
        for(Playlist.Channel c:base)if(!blocked.contains(c.url)&&(c.name+" "+c.group).toLowerCase(Locale.ROOT).contains(q)&&(!onlyAvailable||Boolean.TRUE.equals(availability.get(c.url))))visible.add(c);
        if(adapter!=null)adapter.notifyDataSetChanged();
        if(sourceStatus!=null)sourceStatus.setText((onlyRecent?"最近播放 · ":onlyFavorites?"我的收藏 · ":onlyAvailable?"可用源 · ":"频道 · ")+visible.size()+(visible.isEmpty()?"   没有结果":""));
    }
    private boolean probe(Playlist.Channel c){
        HttpURLConnection conn=null;
        try{if(Thread.currentThread().isInterrupted())return false;conn=(HttpURLConnection)new URL(c.url).openConnection();conn.setConnectTimeout(4000);conn.setReadTimeout(4000);conn.setInstanceFollowRedirects(true);conn.setRequestProperty("User-Agent","MiBoxOS/1.0");if(!c.referrer.isEmpty())conn.setRequestProperty("Referer",c.referrer);conn.setRequestMethod("GET");int code=conn.getResponseCode();if(code<200||code>=400)return false;try(InputStream in=conn.getInputStream()){byte[] b=new byte[512];return in.read(b)>=0;}}catch(Exception e){return false;}finally{if(conn!=null)conn.disconnect();}
    }
    private void testConnections(){
        if(channels.isEmpty()){message("当前分类还没有频道");return;}final int generation=++testGeneration;final List<Playlist.Channel> snapshot=new ArrayList<>(channels);availability.clear();sourceStatus.setText("正在并行测试 0 / "+snapshot.size()+"…");if(testIo!=null)testIo.shutdownNow();testIo=Executors.newFixedThreadPool(8);AtomicInteger done=new AtomicInteger(),good=new AtomicInteger();
        for(Playlist.Channel c:snapshot)testIo.execute(()->{if(generation!=testGeneration||Thread.currentThread().isInterrupted())return;boolean ok=probe(c);if(generation!=testGeneration)return;availability.put(c.url,ok);if(ok)good.incrementAndGet();int progress=done.incrementAndGet();if(progress%8==0||progress==snapshot.size()){int working=good.get();runOnUiThread(()->{if(!destroyed&&generation==testGeneration){refreshList();sourceStatus.setText(progress==snapshot.size()?"测试完成 · 可用 "+working+" / "+snapshot.size():"正在并行测试 "+progress+" / "+snapshot.size()+" · 可用 "+working);}});}});
    }
    private void removeBroken(){
        if(availability.isEmpty()){message("请先测试当前频道源");return;}int added=0;for(Playlist.Channel c:channels)if(Boolean.FALSE.equals(availability.get(c.url))&&blocked.add(c.url))added++;for(Iterator<Playlist.Channel> it=favorites.iterator();it.hasNext();)if(blocked.contains(it.next().url))it.remove();saveBlocked();saveFavorites();refreshList();message("已隐藏 "+added+" 个失效源，重启后仍有效");
    }
    private class ChannelAdapter extends BaseAdapter{
        public int getCount(){return visible.size();}public Object getItem(int p){return visible.get(p);}public long getItemId(int p){return p;}
        public View getView(int p,View old,android.view.ViewGroup parent){
            Playlist.Channel c=visible.get(p);LinearLayout l=new LinearLayout(MainActivity.this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);l.setPadding(dp(10),dp(6),dp(10),dp(6));l.setMinimumHeight(dp(52));l.setBackground(bg(current!=null&&current.url.equals(c.url)?0xff2a4525:PANEL2));
            int[] colors={CYAN,PURPLE,ORANGE,PINK,ACCENT};int color=colors[((c.group+c.name).hashCode()&0x7fffffff)%colors.length];TextView badge=label(c.name.length()>3?c.name.substring(0,3):c.name,10,BG);badge.setGravity(Gravity.CENTER);badge.setTypeface(null,Typeface.BOLD);badge.setBackground(bg(color));l.addView(badge,new LinearLayout.LayoutParams(dp(38),dp(38)));
            LinearLayout text=new LinearLayout(MainActivity.this);text.setOrientation(LinearLayout.VERTICAL);text.setPadding(dp(10),0,dp(4),0);text.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));TextView n=label((isFavorite(c)?"★  ":"")+c.name,14,TEXT);n.setMaxLines(1);n.setEllipsize(TextUtils.TruncateAt.END);text.addView(n);text.addView(label(c.group.isEmpty()?"点击播放":"LIVE  ·  "+c.group,10,MUTED));l.addView(text);TextView play=label("›",24,MUTED);play.setGravity(Gravity.CENTER);l.addView(play,new LinearLayout.LayoutParams(dp(22),dp(38)));return l;
        }
    }
    private void play(Playlist.Channel c){
        if(c==null||c.url==null||c.url.trim().isEmpty()){message("频道地址无效");return;}rememberRecent(c);
        current=c;int token=++playGeneration;prepared=false;paused=false;
        handler.removeCallbacks(hide);if(timeout!=null)handler.removeCallbacks(timeout);
        if(video!=null){try{video.stopPlayback();}catch(Exception ignored){}}try{videoArea.removeAllViews();}catch(Exception ignored){}
        video=new VideoView(this);video.setBackgroundColor(Color.TRANSPARENT);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER);videoArea.addView(video,p);
        video.setOnClickListener(v->showControls());
        videoFullButton=button("⛶  全屏",()->setFull(!full));videoFullButton.setTextSize(large?18:15);videoFullButton.setAlpha(.94f);FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(dp(116),dp(52),Gravity.TOP|Gravity.RIGHT);fp.setMargins(0,dp(14),dp(14),0);videoArea.addView(videoFullButton,fp);videoFullButton.bringToFront();
        heading.setText(c.name);favoriteButton.setText(isFavorite(c)?"★ 已收藏":"☆ 收藏");pauseButton.setText("暂停");status.setText("正在连接 "+c.name+"…");status.setVisibility(View.VISIBLE);controls.setVisibility(View.VISIBLE);adapter.notifyDataSetChanged();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        video.setOnPreparedListener(mp->{if(token!=playGeneration||destroyed)return;prepared=true;handler.removeCallbacks(timeout);status.setVisibility(View.GONE);mp.setOnInfoListener((m,what,extra)->{if(what==701){status.setText("正在缓冲…");status.setVisibility(View.VISIBLE);}if(what==702||what==3){status.setVisibility(View.GONE);}return false;});if(!inBackground){video.start();showControls();}android.util.Log.i("MiBoxTV","Prepared "+c.name);});
        video.setOnErrorListener((mp,what,extra)->{if(token==playGeneration)failed("暂时无法播放此频道\n请点“重试”或“下一台”");android.util.Log.w("MiBoxTV","Playback error "+what+"/"+extra);return true;});
        video.setOnCompletionListener(mp->{if(token==playGeneration)failed("直播已结束\n可以重试或选择其他频道");});
        timeout=()->{if(token==playGeneration&&!prepared){video.stopPlayback();failed("连接超时\n请检查网络，或选择其他频道");}};handler.postDelayed(timeout,20000);
        Map<String,String> headers=new HashMap<>();if(!c.referrer.isEmpty())headers.put("Referer",c.referrer);if(!c.agent.isEmpty())headers.put("User-Agent",c.agent);
        try{video.setVideoURI(Uri.parse(c.url),headers);}catch(Exception e){failed("此频道地址暂不支持");}
    }
    private void failed(String text){prepared=false;if(timeout!=null)handler.removeCallbacks(timeout);status.setText(text);status.setVisibility(View.VISIBLE);controls.setVisibility(View.VISIBLE);handler.removeCallbacks(hide);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    private void togglePause(){if(video==null||current==null){message("请先选择频道");return;}if(!prepared){play(current);return;}paused=!paused;if(paused){video.pause();pauseButton.setText("播放");getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}else{video.start();pauseButton.setText("暂停");getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}showControls();}
    private void next(int delta){List<Playlist.Channel> pool=new ArrayList<>(visible);if(pool.isEmpty()){message("先选择有频道的分类");return;}int index=-1;for(int i=0;i<pool.size();i++)if(current!=null&&pool.get(i).url.equals(current.url)){index=i;break;}int target=index<0?0:(index+delta)%pool.size();if(target<0)target+=pool.size();try{play(pool.get(target));}catch(Exception e){android.util.Log.e("MiBoxTV","换台失败",e);message("换台失败，请稍后重试");}}
    private boolean isFavorite(Playlist.Channel c){for(Playlist.Channel f:favorites)if(c.url.equals(f.url))return true;return false;}
    private void toggleFavorite(){if(current==null){message("先选择一个频道");return;}if(isFavorite(current)){for(Iterator<Playlist.Channel> it=favorites.iterator();it.hasNext();)if(it.next().url.equals(current.url))it.remove();}else favorites.add(current);saveFavorites();favoriteButton.setText(isFavorite(current)?"★ 已收藏":"☆ 收藏");refreshList();}
    private void loadFavorites(){try{JSONArray a=new JSONArray(prefs.getString("favorites","[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);favorites.add(new Playlist.Channel(o.getString("name"),o.getString("url"),o.optString("group"),o.optString("ref"),o.optString("agent")));}}catch(Exception ignored){}}
    private void saveFavorites(){JSONArray a=new JSONArray();for(Playlist.Channel c:favorites){try{JSONObject o=new JSONObject();o.put("name",c.name);o.put("url",c.url);o.put("group",c.group);o.put("ref",c.referrer);o.put("agent",c.agent);a.put(o);}catch(Exception ignored){}}prefs.edit().putString("favorites",a.toString()).apply();}
    private void rememberRecent(Playlist.Channel c){for(Iterator<Playlist.Channel> it=recent.iterator();it.hasNext();)if(it.next().url.equals(c.url))it.remove();recent.add(0,c);while(recent.size()>20)recent.remove(recent.size()-1);saveChannels("recent",recent);}
    private void loadRecent(){loadChannels("recent",recent);}
    private void saveChannels(String key,List<Playlist.Channel> items){JSONArray a=new JSONArray();for(Playlist.Channel c:items){try{JSONObject o=new JSONObject();o.put("name",c.name);o.put("url",c.url);o.put("group",c.group);o.put("ref",c.referrer);o.put("agent",c.agent);a.put(o);}catch(Exception ignored){}}prefs.edit().putString(key,a.toString()).apply();}
    private void loadChannels(String key,List<Playlist.Channel> target){try{JSONArray a=new JSONArray(prefs.getString(key,"[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);target.add(new Playlist.Channel(o.getString("name"),o.getString("url"),o.optString("group"),o.optString("ref"),o.optString("agent")));}}catch(Exception ignored){}}
    private void loadBlocked(){try{JSONArray a=new JSONArray(prefs.getString("blocked","[]"));for(int i=0;i<a.length();i++)blocked.add(a.getString(i));}catch(Exception ignored){}}
    private void saveBlocked(){JSONArray a=new JSONArray();synchronized(blocked){for(String url:blocked)a.put(url);}prefs.edit().putString("blocked",a.toString()).apply();}
    private void restoreBlocked(){int count=blocked.size();blocked.clear();saveBlocked();refreshList();message("已恢复 "+count+" 个被清理的源");}
    private void more(){
        String[] items={"频道维护",large?"标准字体":"大字模式","打开直播网址","恢复播放列表","关于 QTV"};
        new AlertDialog.Builder(this).setTitle("更多功能").setItems(items,(d,w)->{if(w==0)channelTools();if(w==1){prefs.edit().putBoolean("large",!large).apply();recreate();}if(w==2)openUrl();if(w==3)loadSource(false);if(w==4)about();}).show();
    }
    private void channelTools(){String[] items={"更新频道列表","并行测试直播源",onlyAvailable?"显示全部频道":"仅显示可用频道","清除测试失败源","恢复被清理的源","重试当前频道"};new AlertDialog.Builder(this).setTitle("频道维护").setItems(items,(d,w)->{if(w==0)loadSource(true);if(w==1)testConnections();if(w==2){onlyAvailable=!onlyAvailable;onlyFavorites=false;onlyRecent=false;refreshList();}if(w==3)removeBroken();if(w==4)restoreBlocked();if(w==5){if(current!=null)play(current);else message("请先选择频道");}}).setNegativeButton("返回",null).show();}
    private void about(){new AlertDialog.Builder(this).setTitle("QTV 1.2").setMessage("电视与手机通用版 · 支持触屏、遥控器、鼠标和 USB 键盘。\n\n功能：网络直播、公开频道聚合、导入播放列表、本地视频、收藏、最近播放、源测速与清理、全屏播放和大字模式。\n\n频道地址由第三方维护，播放可用性与内容授权由相应服务提供方负责。商用发行请配置已获授权的频道清单。").setPositiveButton("知道了",null).show();}
    private void openUrl(){
        EditText input=new EditText(this);input.setHint("https://…/直播.m3u8");input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this).setTitle("打开直播网址").setView(input).setPositiveButton("播放",(d,w)->{String url=input.getText().toString().trim();if(url.startsWith("https://")||url.startsWith("http://"))play(new Playlist.Channel("自选直播",url,"自选","",""));else message("请输入 http 或 https 直播地址");}).setNegativeButton("返回",null).show();
    }
    private void message(String m){Toast.makeText(this,m,Toast.LENGTH_SHORT).show();}
    @Override public void onBackPressed(){if(full){setFull(false);return;}new AlertDialog.Builder(this).setMessage("退出 QTV？").setPositiveButton("退出",(d,w)->finish()).setNegativeButton("继续看",null).show();}
    @Override public boolean onKeyDown(int key,android.view.KeyEvent e){if(full&&(key==android.view.KeyEvent.KEYCODE_DPAD_UP||key==android.view.KeyEvent.KEYCODE_DPAD_DOWN)){next(key==android.view.KeyEvent.KEYCODE_DPAD_UP?-1:1);return true;}if(full&&(key==android.view.KeyEvent.KEYCODE_DPAD_CENTER||key==android.view.KeyEvent.KEYCODE_ENTER)&&controls.getVisibility()!=View.VISIBLE){showControls();fullButton.requestFocus();return true;}return super.onKeyDown(key,e);}
    @Override protected void onPause(){super.onPause();inBackground=true;if(video!=null)video.pause();getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    @Override protected void onResume(){super.onResume();inBackground=false;if(video!=null&&prepared&&!paused){video.start();getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}}
    @Override protected void onDestroy(){destroyed=true;++testGeneration;if(testIo!=null)testIo.shutdownNow();handler.removeCallbacksAndMessages(null);if(video!=null)try{video.stopPlayback();}catch(Exception ignored){}io.shutdownNow();super.onDestroy();}
}

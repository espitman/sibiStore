using System;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.UI;

namespace Sibi.Store.VR {
[Serializable] public class StoreAction { public string command, value, label; public bool enabled; }
[Serializable] public class StoreHost { public string name, url, id; }
[Serializable] public class StoreVersion { public string name, filename, sizeLabel; public long code, size; public int minSdk; public string[] abis; }
[Serializable] public class StoreDownload { public string hash, state, title, error; public long bytes, total; public int percent; }
[Serializable] public class StoreEntry { public string packageName, title, icon, availability, versionName; public long versionCode, size; public StoreVersion availableVersion; public StoreVersion[] versions; public StoreDownload download; public StoreAction primaryAction; public StoreAction[] actions; }
[Serializable] public class StoreStorage { public long bytes; public int files; public string label; public bool clearing; }
[Serializable] public class StoreSettings { public bool deleteAfterInstall; }
[Serializable] public class StoreSnapshot { public int schemaVersion; public bool ready, connected, loading; public StoreHost host; public StoreHost[] hosts; public StoreEntry[] apps; public StoreDownload[] downloads; public StoreStorage storage; public StoreSettings settings; public string error, message; }

public sealed class StoreApp : MonoBehaviour {
    static readonly Color Gold = new Color(1,.757f,.027f), Ink = new Color(.025f,.025f,.03f), Card = new Color(.075f,.075f,.085f), Muted = new Color(.65f,.65f,.69f);
    Font font; Canvas canvas; RectTransform body; Text connection, status, notice;
    StoreSnapshot state = new StoreSnapshot(); SpatialPointers pointers; OVRCameraRig rig; ScrollRect activeScroll;
    string page = "Library", query = "", selected = "", keyboard = "", typed = "", previous = "";
    bool confirmClear; float nextPoll; AndroidJavaObject bridge;
    readonly Dictionary<string,Texture2D> icons = new Dictionary<string,Texture2D>();
    [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)] static void Bootstrap() {
        if (FindFirstObjectByType<StoreApp>() == null) new GameObject("Sibi Store VR").AddComponent<StoreApp>();
    }
    void Start() {
        font = Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf");
        new GameObject("Event System",typeof(EventSystem));
        var cameraObject = new GameObject("Quest rig"); cameraObject.transform.SetParent(transform);
        rig = cameraObject.AddComponent<OVRCameraRig>();
        var manager = cameraObject.AddComponent<OVRManager>(); manager.isInsightPassthroughEnabled = false; manager.launchSimultaneousHandsControllersOnStartup = true; manager.SimultaneousHandsAndControllersEnabled = true;
        rig.centerEyeAnchor.GetComponent<Camera>().clearFlags = CameraClearFlags.SolidColor;
        rig.centerEyeAnchor.GetComponent<Camera>().backgroundColor = new Color(.012f,.014f,.022f);
        var left = new GameObject("Left tracked hand"); left.transform.SetParent(rig.leftHandAnchor,false);
        var right = new GameObject("Right tracked hand"); right.transform.SetParent(rig.rightHandAnchor,false);
        left.SetActive(false);right.SetActive(false);
        var lh = left.AddComponent<OVRHand>(); var rh = right.AddComponent<OVRHand>();
        // Configure the serialized hand type before Awake runs.
        ConfigureHand(lh, false); ConfigureHand(rh,true); left.SetActive(true); right.SetActive(true);
        BuildPanel(rig.centerEyeAnchor.GetComponent<Camera>());
        pointers=gameObject.AddComponent<SpatialPointers>();pointers.canvas=canvas;pointers.eye=canvas.worldCamera;pointers.leftController=rig.leftControllerAnchor;pointers.rightController=rig.rightControllerAnchor;pointers.leftHand=lh;pointers.rightHand=rh;
        Recenter(); Render();
#if UNITY_ANDROID && !UNITY_EDITOR
        try { using(var player=new AndroidJavaClass("com.unity3d.player.UnityPlayer")) using(var activity=player.GetStatic<AndroidJavaObject>("currentActivity")) bridge=new AndroidJavaObject("com.sibi.store.vr.StoreBridge",activity); }
        catch(Exception e){ notice.text="Could not start Android store: "+e.Message; }
#endif
    }
    void BuildPanel(Camera eye) {
        var root = new GameObject("Store panel",typeof(RectTransform),typeof(Canvas),typeof(GraphicRaycaster)); root.transform.SetParent(transform);
        canvas = root.GetComponent<Canvas>(); canvas.renderMode=RenderMode.WorldSpace; canvas.worldCamera=eye;
        root.GetComponent<RectTransform>().sizeDelta=new Vector2(1200,800); root.transform.localScale=Vector3.one*.0015f;
        Image(root.transform,"Background",0,0,1200,800,Ink);
        Label(root.transform,"sibi",32,25,150,56,40,Color.white); Label(root.transform,"store / VR",120,34,220,46,27,Gold);
        connection=Label(root.transform,"Finding your Mac…",600,36,560,40,20,Muted);
        string[] pages={"Library","Downloads","Connect Mac","Settings"};
        for(int i=0;i<pages.Length;i++){string name=pages[i];Button(root.transform,name,24,130+i*82,190,64,()=>{page=name;selected="";keyboard="";Render();});}
        Button(root.transform,"Recenter",24,626,190,64,Recenter);
        Label(root.transform,"Point + pinch\nor use trigger\nDrag to scroll",30,474,180,116,20,Muted);
        body=Rect(root.transform,"Content",244,110,932,570);
        notice=Label(root.transform,"",256,688,900,52,18,Gold);
        status=Label(root.transform,"Starting input…",256,750,900,30,17,Muted);
    }
#if UNITY_EDITOR
    public void Preview(Camera eye, string snapshot, string previewPage) {
        font=Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf");
        state=JsonUtility.FromJson<StoreSnapshot>(snapshot); page=previewPage;
        BuildPanel(eye); connection.text="Preview fixture • Offline"; status.text="Editor layout preview"; Render();
    }
#endif
    void ConfigureHand(OVRHand hand,bool right) {
        // OVRHand's serialized HandType is private in some Core SDK versions.
        var field=typeof(OVRHand).GetField("HandType",System.Reflection.BindingFlags.Public|System.Reflection.BindingFlags.NonPublic|System.Reflection.BindingFlags.Instance);
        if(field==null) throw new MissingFieldException("OVRHand.HandType");
        field.SetValue(hand,right ? OVRHand.Hand.HandRight : OVRHand.Hand.HandLeft);
        typeof(OVRHand).GetField("_pointerPoseRoot",System.Reflection.BindingFlags.NonPublic|System.Reflection.BindingFlags.Instance)?.SetValue(hand,rig.trackingSpace);
    }
    void Recenter(){if(canvas==null || rig==null)return;var eye=rig.centerEyeAnchor;var forward=Vector3.ProjectOnPlane(eye.forward,Vector3.up).normalized;if(forward.sqrMagnitude<.1f)forward=Vector3.forward;canvas.transform.position=eye.position+forward*1.65f;canvas.transform.rotation=Quaternion.LookRotation(forward,Vector3.up);}
    void Update(){
        if(status!=null && pointers!=null)status.text=pointers.Status;
        if(Time.unscaledTime<nextPoll)return;nextPoll=Time.unscaledTime+.35f;
        if(bridge==null)return;
        try{var json=bridge.Call<string>("snapshot");if(json==previous)return;state=JsonUtility.FromJson<StoreSnapshot>(json);if(state.schemaVersion!=1)throw new Exception("Unsupported store data version");
            connection.text=state.connected?"●  "+(state.host?.name??"Mac connected"):"○  Mac offline";
            notice.text=state.error??state.message??"";
            // Keep pressed controls alive through their release/drag sequence.
            if(pointers!=null && pointers.HasInteraction)return;
            previous=json;Render(true);
        }catch(Exception e){notice.text="Store error: "+e.Message;}
    }
    void Send(string command,string value=""){bridge?.Call("command",command,value);}
    void Render(bool preserveScroll=false){
        var position=activeScroll!=null?activeScroll.content.anchoredPosition:Vector2.zero;activeScroll=null;
        RenderBody();
        if(preserveScroll && activeScroll!=null){Canvas.ForceUpdateCanvases();activeScroll.content.anchoredPosition=position;}
    }
    void RenderBody(){
        foreach(Transform child in body)Destroy(child.gameObject);
        if(keyboard!=""){Keyboard();return;}
        Label(body,page=="Library"&&selected!=""?"App details":page,0,0,720,54,32,Color.white);
        if(page=="Library" && selected!=""){Details();return;}
        if(page=="Library"){
            Button(body,string.IsNullOrEmpty(query)?"Search apps":query,0,65,710,58,()=>{keyboard="search";typed=query;Render();});Button(body,"Refresh",730,65,184,58,()=>Send("refresh"));
            var entries=Array.FindAll(state.apps??Array.Empty<StoreEntry>(),a=>(a.title+" "+a.packageName).IndexOf(query,StringComparison.OrdinalIgnoreCase)>=0);
            if(entries.Length==0){Label(body,state.connected?"No VR apps in your library":"Connect to your Mac",14,185,850,56,28,Color.white);Label(body,state.connected?"Add Quest APKs to the selected folder on your Mac.":"Choose Connect Mac to find your store on this network.",14,250,850,82,22,Muted);return;}
            var list=Scroll(body,0,145,914,410,entries.Length*116);
            for(int i=0;i<entries.Length;i++){var app=entries[i];int y=i*116;Image(list,"Card",0,y,896,104,Card);AppIcon(list,app,14,y+16);Button(list,app.title,98,y+6,555,51,()=>{selected=app.packageName;Render();},false);Label(list,app.versionName+"  ·  "+Size(app.size),110,y+58,500,31,18,Muted);var a=app.primaryAction;if(a!=null)Button(list,a.label,677,y+21,204,62,()=>Send(a.command,a.value),true,a.enabled);}
        }else if(page=="Connect Mac"){
            Button(body,"Find Macs",0,68,270,62,()=>Send("discover"));Button(body,"Enter address",292,68,270,62,()=>{keyboard="address";typed="";Render();});
            var hosts=state.hosts??Array.Empty<StoreHost>();if(hosts.Length==0)Label(body,"Looking for Sibi Store on your network…\nKeep the Mac store open on the same Wi-Fi.",12,180,890,100,24,Muted);
            var list=Scroll(body,0,150,914,400,Math.Max(400,hosts.Length*112));for(int i=0;i<hosts.Length;i++){var host=hosts[i];int y=i*112;Label(list,host.name,12,y+4,650,46,24,Color.white);Label(list,host.url,12,y+48,640,32,18,Muted);Button(list,"Connect",704,y+15,190,64,()=>Send("connect",JsonUtility.ToJson(host)));}
        }else if(page=="Settings"){
            Label(body,"Downloaded APKs",10,75,800,40,26,Color.white);Label(body,(state.storage?.label??"0 KB")+"  ·  "+(state.storage?.files??0)+" files",10,123,850,35,22,Muted);
            var enabled=state.settings?.deleteAfterInstall??true;Button(body,"Delete after successful install: "+(enabled?"ON":"OFF"),0,183,900,70,()=>Send("deleteAfterInstall",(!enabled).ToString().ToLower()));
            Label(body,"Files are removed only after installation is confirmed.",10,272,870,48,21,Muted);
            Button(body,confirmClear?"Confirm: clear downloaded files":"Clear downloaded files",0,340,650,68,()=>{if(confirmClear){Send("clearDownloads");confirmClear=false;}else confirmClear=true;Render();},false,!(state.storage?.clearing??false));
            if(confirmClear)Button(body,"Keep files",670,340,230,68,()=>{confirmClear=false;Render();});
            Label(body,"Active downloads and files being installed are kept.",10,430,870,50,21,Muted);
        }else{
            var downloads=state.downloads??Array.Empty<StoreDownload>();if(downloads.Length==0)Label(body,"No downloads yet",10,135,850,70,28,Muted);
            var list=Scroll(body,0,70,914,490,Math.Max(490,downloads.Length*156));
            for(int i=0;i<downloads.Length;i++){var d=downloads[i];int y=i*156;Label(list,d.title??"APK download",8,y,630,46,25,Color.white);Label(list,$"{d.state}  ·  {d.percent}%  ·  {Size(d.bytes)} / {Size(d.total)}",8,y+48,620,38,19,Muted);Label(list,d.error??"",8,y+88,615,44,17,Gold);if(d.state=="downloading"||d.state=="queued")Button(list,"Pause",646,y+3,245,56,()=>Send("pause",d.hash));else if(d.state=="paused"||d.state=="failed")Button(list,"Resume",646,y+3,245,56,()=>Send("resume",d.hash),true,state.connected);Button(list,"Cancel",646,y+70,245,56,()=>Send("cancel",d.hash),false);}
        }
    }
    void Details(){var app=Array.Find(state.apps??Array.Empty<StoreEntry>(),a=>a.packageName==selected);if(app==null){selected="";Render();return;}Button(body,"Back",722,0,190,56,()=>{selected="";Render();},false);AppIcon(body,app,8,87);Label(body,app.title,110,80,780,58,32,Color.white);Label(body,app.packageName,110,141,780,38,18,Muted);Label(body,"Version "+app.versionName+"  ·  "+Size(app.size),8,215,890,42,25,Muted);var actions=app.actions??Array.Empty<StoreAction>();for(int i=0;i<actions.Length;i++){var a=actions[i];Button(body,a.label,i*290,280,274,64,()=>Send(a.command,a.value),i==0,a.enabled);}Label(body,"Version history",8,377,800,44,26,Color.white);var versions=app.versions??Array.Empty<StoreVersion>();var list=Scroll(body,0,431,914,130,Math.Max(130,versions.Length*62));for(int i=0;i<versions.Length;i++){var v=versions[i];Label(list,$"{v.name}   ·   {v.sizeLabel}   ·   API {v.minSdk}+",10,i*62,890,52,22,Muted);}}
    void Keyboard(){Label(body,keyboard=="search"?"Search VR apps":"Mac address",0,0,910,50,30,Color.white);Label(body,typed+"|",8,66,900,62,26,Gold);string[] rows={"1234567890","qwertyuiop","asdfghjkl","zxcvbnm.:-"};for(int r=0;r<rows.Length;r++)for(int c=0;c<rows[r].Length;c++){string key=rows[r][c].ToString();Button(body,key,c*91,150+r*76,80,65,()=>{typed+=key;Render();},false);}Button(body,"Space",0,467,225,68,()=>{typed+=" ";Render();},false);Button(body,"Delete",236,467,218,68,()=>{if(typed.Length>0)typed=typed.Substring(0,typed.Length-1);Render();},false);Button(body,"Cancel",465,467,218,68,()=>{keyboard="";Render();},false);Button(body,"Done",694,467,218,68,()=>{if(keyboard=="search")query=typed;else Send("connect",typed);keyboard="";Render();});}
    RectTransform Rect(Transform parent,string name,float x,float y,float w,float h){var go=new GameObject(name,typeof(RectTransform));go.transform.SetParent(parent,false);var r=go.GetComponent<RectTransform>();r.anchorMin=r.anchorMax=new Vector2(0,1);r.pivot=new Vector2(0,1);r.anchoredPosition=new Vector2(x,-y);r.sizeDelta=new Vector2(w,h);return r;}
    Image Image(Transform parent,string name,float x,float y,float w,float h,Color color){var r=Rect(parent,name,x,y,w,h);var image=r.gameObject.AddComponent<Image>();image.color=color;return image;}
    Text Label(Transform parent,string text,float x,float y,float w,float h,int size,Color color){var r=Rect(parent,"Label",x,y,w,h);var t=r.gameObject.AddComponent<Text>();t.font=font;t.fontSize=size;t.text=text;t.color=color;t.raycastTarget=false;t.supportRichText=false;t.verticalOverflow=VerticalWrapMode.Truncate;return t;}
    Button Button(Transform parent,string title,float x,float y,float w,float h,Action click,bool primary=true,bool enabled=true){var image=Image(parent,title,x,y,w,h,primary?Gold:Card);var button=image.gameObject.AddComponent<Button>();button.interactable=enabled;var colors=button.colors;colors.highlightedColor=new Color(1,1,.75f);colors.pressedColor=new Color(.75f,.75f,.75f);colors.disabledColor=new Color(.38f,.38f,.38f);button.colors=colors;button.onClick.AddListener(()=>click());var label=Label(image.transform,title,12,0,w-24,h,23,primary?Ink:Color.white);label.alignment=TextAnchor.MiddleCenter;return button;}
    RectTransform Scroll(Transform parent,float x,float y,float w,float h,float contentHeight){var viewport=Image(parent,"Viewport",x,y,w,h,Ink);viewport.gameObject.AddComponent<Mask>().showMaskGraphic=false;var scroll=viewport.gameObject.AddComponent<ScrollRect>();var content=Rect(viewport.transform,"Items",0,0,w,Math.Max(h,contentHeight));scroll.viewport=viewport.rectTransform;scroll.content=content;scroll.horizontal=false;scroll.vertical=true;scroll.movementType=ScrollRect.MovementType.Clamped;scroll.scrollSensitivity=35;activeScroll=scroll;return content;}
    void AppIcon(Transform parent,StoreEntry app,float x,float y){if(string.IsNullOrEmpty(app.icon)){Label(parent,"VR",x,y,76,76,29,Gold);return;}try{if(!icons.TryGetValue(app.icon,out var tex)){var split=app.icon.IndexOf(',');if(split<0)return;var bytes=Convert.FromBase64String(app.icon.Substring(split+1));tex=new Texture2D(2,2);if(!tex.LoadImage(bytes)){Destroy(tex);return;}icons[app.icon]=tex;}var r=Rect(parent,"App icon",x,y,76,76);var raw=r.gameObject.AddComponent<RawImage>();raw.texture=tex;raw.raycastTarget=false;}catch{Label(parent,"VR",x,y,76,76,29,Gold);}}
    static string Size(long bytes)=>bytes>=1048576?(bytes/1048576)+" MB":(bytes/1024)+" KB";
    void OnApplicationPause(bool paused){if(bridge!=null)Send(paused?"lifecyclePause":"lifecycleResume");}
    void OnDestroy(){if(bridge!=null){bridge.Call("close");bridge.Dispose();}foreach(var texture in icons.Values)Destroy(texture);}
}
}

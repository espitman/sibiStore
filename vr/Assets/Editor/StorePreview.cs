using System.IO;
using Sibi.Store.VR;
using UnityEditor.SceneManagement;
using UnityEngine;

public static class StorePreview {
    public static void Capture() {
        var routine=Frames();
        UnityEditor.EditorApplication.CallbackFunction tick=null;
        tick=()=>{if(!routine.MoveNext()){UnityEditor.EditorApplication.update-=tick;UnityEditor.EditorApplication.Exit(0);}};
        UnityEditor.EditorApplication.update+=tick;
    }
    static System.Collections.IEnumerator Frames() {
        Directory.CreateDirectory("test-results");
        foreach(var page in new[]{"Library","Downloads","Connect Mac","Settings","Incoming-request"}) {
            EditorSceneManager.NewScene(NewSceneSetup.EmptyScene,NewSceneMode.Single);
            var camera=new GameObject("Preview camera").AddComponent<Camera>();
            camera.transform.position=new Vector3(0,0,-2);camera.orthographic=true;camera.orthographicSize=.6f;camera.clearFlags=CameraClearFlags.SolidColor;
            var target=new RenderTexture(1200,800,24);camera.targetTexture=target;
            var app=new GameObject("Offline preview").AddComponent<StoreApp>();app.enabled=false;
            var snapshot="{\"schemaVersion\":1,\"connected\":true,\"apps\":[{\"packageName\":\"preview.only\",\"title\":\"Quest sample · preview only\",\"versionName\":\"1.0\",\"size\":52428800,\"primaryAction\":{\"label\":\"Download\",\"enabled\":true}}],\"storage\":{\"label\":\"50 MB\",\"files\":1},\"settings\":{\"deleteAfterInstall\":true}}";
            if(page=="Incoming-request") snapshot=snapshot.Substring(0,snapshot.Length-1)+",\"peerRequests\":[{\"id\":\"preview-request\",\"senderName\":\"Living room phone\",\"summary\":\"Holiday video.mp4 + 1 more · 125 MB\"}]}";
            app.Preview(camera,snapshot,page=="Incoming-request"?"Library":page);
            yield return null; yield return null;
            Canvas.ForceUpdateCanvases();camera.Render();RenderTexture.active=target;
            var image=new Texture2D(1200,800,TextureFormat.RGB24,false);image.ReadPixels(new Rect(0,0,1200,800),0,0);image.Apply();
            File.WriteAllBytes("test-results/"+page.Replace(" ","-")+".png",image.EncodeToPNG());
            RenderTexture.active=null;camera.targetTexture=null;Object.DestroyImmediate(image);Object.DestroyImmediate(target);
        }
        EditorSceneManager.NewScene(NewSceneSetup.EmptyScene,NewSceneMode.Single);
    }
}

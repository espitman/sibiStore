using System;
using System.Reflection;
using NUnit.Framework;
using Sibi.Store.VR;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.UI;

public class PointerTests {
    GameObject root; SpatialPointers adapter; Transform source; object left,right; MethodInfo feed; int clicks;
    [SetUp] public void Setup(){
        clicks=0;
        root=new GameObject("Pointer test");
        var es=new GameObject("Events",typeof(EventSystem));es.transform.SetParent(root.transform);
        var cameraObject=new GameObject("Camera",typeof(Camera));cameraObject.transform.SetParent(root.transform);
        var camera=cameraObject.GetComponent<Camera>();camera.pixelRect=new Rect(0,0,1200,800);
        var panel=new GameObject("Canvas",typeof(RectTransform),typeof(Canvas),typeof(GraphicRaycaster));panel.transform.SetParent(root.transform);
        panel.transform.position=new Vector3(0,0,2);panel.GetComponent<RectTransform>().sizeDelta=new Vector2(2,2);
        var canvas=panel.GetComponent<Canvas>();canvas.renderMode=RenderMode.WorldSpace;canvas.worldCamera=camera;
        var buttonObject=new GameObject("Button",typeof(RectTransform),typeof(Image),typeof(Button));buttonObject.transform.SetParent(panel.transform,false);buttonObject.GetComponent<RectTransform>().sizeDelta=new Vector2(1,1);
        buttonObject.GetComponent<Button>().onClick.AddListener(()=>clicks++);
        adapter=root.AddComponent<SpatialPointers>();adapter.canvas=canvas;adapter.eye=camera;
        source=new GameObject("Source").transform;source.SetParent(root.transform);
        var type=typeof(SpatialPointers).GetNestedType("Pointer",BindingFlags.NonPublic);
        left=Activator.CreateInstance(type,new object[]{-1});right=Activator.CreateInstance(type,new object[]{-2});
        feed=typeof(SpatialPointers).GetMethod("Feed",BindingFlags.Instance|BindingFlags.NonPublic);
        Canvas.ForceUpdateCanvases();camera.Render();Tick(left,true,false);Tick(right,true,false);
        var data=(PointerEventData)type.GetField("data").GetValue(left);
        Debug.Log($"Pointer QA screen={data.position} hit={data.pointerEnter} canvas={canvas.pixelRect} camera={camera.pixelRect} graphicCount={GraphicRegistry.GetGraphicsForCanvas(canvas).Count}");
    }
    void Tick(object pointer,bool tracked,bool down)=>feed.Invoke(adapter,new object[]{pointer,source,tracked,down,0f});
    [TearDown] public void Cleanup()=>UnityEngine.Object.DestroyImmediate(root);
    [Test] public void ReleaseOutsideCannotActivateButton(){
        Tick(left,true,true);source.rotation=Quaternion.Euler(0,60,0);Tick(left,true,false);Assert.That(clicks,Is.Zero);
        source.rotation=Quaternion.identity;Tick(left,true,false);Tick(left,true,true);Tick(left,true,false);Assert.That(clicks,Is.EqualTo(1));
    }
    [Test] public void HandsSuppressControllersAcrossBriefTrackingLoss(){
        var select=typeof(SpatialPointers).GetMethod("PreferHands",BindingFlags.Instance|BindingFlags.NonPublic);
        bool Hands(bool tracked,float time)=>(bool)select.Invoke(adapter,new object[]{tracked,time});
        Assert.That(Hands(false,0),Is.False);
        Tick(left,true,true);
        Assert.That(Hands(true,1),Is.True);
        Tick(left,false,true); // Switching mode cancels the controller press.
        Assert.That(Hands(false,1.2f),Is.True);
        Assert.That(Hands(false,1.6f),Is.False);
        Tick(left,true,true);Tick(left,true,false);
        Assert.That(clicks,Is.Zero);
        Tick(left,true,true);Tick(left,true,false);
        Assert.That(clicks,Is.EqualTo(1));
    }
    [Test] public void LostTrackingRequiresReleaseBeforeNewPress(){
        Tick(left,true,true);Tick(left,false,true);Tick(left,true,true);Tick(left,true,false);Assert.That(clicks,Is.Zero);
        Tick(left,true,true);Tick(left,true,false);Assert.That(clicks,Is.EqualTo(1));
    }
    [Test] public void TwoSourcesCannotActivateTheSameControlTwice(){
        Tick(left,true,true);Tick(right,true,true);Tick(right,true,false);Assert.That(clicks,Is.Zero);
        Tick(left,true,false);Assert.That(clicks,Is.EqualTo(1));
    }
    [Test] public void TwoSourcesCanHoldDifferentControlsAtOnce(){
        var original=adapter.canvas.transform.Find("Button").gameObject;
        var second=UnityEngine.Object.Instantiate(original,adapter.canvas.transform);
        original.GetComponent<RectTransform>().sizeDelta=new Vector2(.8f,1);
        second.GetComponent<RectTransform>().sizeDelta=new Vector2(.8f,1);
        original.GetComponent<RectTransform>().anchoredPosition=new Vector2(-.5f,0);
        second.GetComponent<RectTransform>().anchoredPosition=new Vector2(.5f,0);
        int secondClicks=0;second.GetComponent<Button>().onClick=new Button.ButtonClickedEvent();second.GetComponent<Button>().onClick.AddListener(()=>secondClicks++);
        Canvas.ForceUpdateCanvases();adapter.eye.Render();
        source.rotation=Quaternion.LookRotation(new Vector3(-.5f,0,2));Tick(left,true,true);
        source.rotation=Quaternion.LookRotation(new Vector3(.5f,0,2));Tick(right,true,true);
        Tick(right,true,false);Assert.That(secondClicks,Is.EqualTo(1));Assert.That(clicks,Is.Zero);
        source.rotation=Quaternion.LookRotation(new Vector3(-.5f,0,2));Tick(left,true,false);Assert.That(clicks,Is.EqualTo(1));
    }
    [Test] public void WindowGrabContinuesOutsideBoundsAndTrackingLossReleasesIt(){
        var handle=adapter.canvas.transform.Find("Button").gameObject.AddComponent<PanelGrabHandle>();
        handle.Initialize(adapter.canvas.transform);adapter.windowHandle=handle;
        var before=adapter.canvas.transform.position;
        Tick(left,true,true);Assert.That(handle.IsHeld,Is.True);
        Tick(left,true,true);Assert.That(Vector3.Distance(before,adapter.canvas.transform.position),Is.LessThan(.001f));
        source.rotation=Quaternion.Euler(0,60,0);Tick(left,true,true);
        Assert.That(Vector3.Distance(before,adapter.canvas.transform.position),Is.GreaterThan(1));
        var moved=adapter.canvas.transform.position;
        Tick(right,true,true);Assert.That(adapter.canvas.transform.position,Is.EqualTo(moved));
        Tick(left,false,true);Assert.That(handle.IsHeld,Is.False);Assert.That(clicks,Is.Zero);
        Tick(left,true,true);Assert.That(handle.IsHeld,Is.False);
    }
    [Test] public void OnlyTheOwnerCanMoveOrReleaseWindow(){
        var handle=root.AddComponent<PanelGrabHandle>();handle.Initialize(adapter.canvas.transform);
        var ray=new Ray(Vector3.zero,Vector3.forward);var before=adapter.canvas.transform.position;
        Assert.That(handle.TryBegin(-1,ray),Is.True);Assert.That(handle.TryBegin(-2,ray),Is.False);
        handle.Move(-2,new Ray(Vector3.right,Vector3.forward));handle.End(-2);
        Assert.That(handle.IsHeld,Is.True);Assert.That(adapter.canvas.transform.position,Is.EqualTo(before));
        handle.Move(-1,new Ray(Vector3.right,Vector3.forward));Assert.That(adapter.canvas.transform.position,Is.EqualTo(before+Vector3.right));
        handle.End(-1);Assert.That(handle.IsHeld,Is.False);
    }
    [Test] public void RotationKeepsCenterAndClampsYawAndPitch(){
        var handle=root.AddComponent<PanelGrabHandle>();handle.Initialize(adapter.canvas.transform,PanelManipulationMode.Rotate,20,10);
        var before=adapter.canvas.transform.position;
        Assert.That(handle.TryBegin(-1,new Ray(Vector3.zero,Vector3.forward)),Is.True);
        handle.Move(-1,new Ray(Vector3.zero,Quaternion.Euler(-30,45,0)*Vector3.forward));
        Assert.That(Vector3.Distance(adapter.canvas.transform.position,before),Is.LessThan(.0001f));
        var forward=adapter.canvas.transform.forward;
        Assert.That(Vector3.Angle(Vector3.ProjectOnPlane(forward,Vector3.up),Vector3.forward),Is.EqualTo(20).Within(.1f));
        Assert.That(Mathf.Asin(forward.y)*Mathf.Rad2Deg,Is.EqualTo(10).Within(.1f));
    }
    [Test] public void MoveAndRotateHandlesSharePanelOwnership(){
        var move=root.AddComponent<PanelGrabHandle>();move.Initialize(adapter.canvas.transform);
        var rotate=root.AddComponent<PanelGrabHandle>();rotate.Initialize(adapter.canvas.transform,PanelManipulationMode.Rotate);
        var ray=new Ray(Vector3.zero,Vector3.forward);
        Assert.That(move.TryBegin(-1,ray),Is.True);
        Assert.That(rotate.TryBegin(-2,ray),Is.False);
        rotate.End(-1);Assert.That(move.IsHeld,Is.True);
        move.End(-1);Assert.That(rotate.TryBegin(-2,ray),Is.True);
    }
    [Test] public void TrackingLossCancelsRotationAndRequiresRelease(){
        var handle=adapter.canvas.transform.Find("Button").gameObject.AddComponent<PanelGrabHandle>();
        handle.Initialize(adapter.canvas.transform,PanelManipulationMode.Rotate);adapter.windowHandle=handle;
        Tick(left,true,true);Assert.That(handle.IsHeld,Is.True);
        source.rotation=Quaternion.Euler(0,20,0);Tick(left,true,true);
        var rotated=adapter.canvas.transform.rotation;Assert.That(Quaternion.Angle(Quaternion.identity,rotated),Is.GreaterThan(1));
        Tick(left,false,true);Assert.That(handle.IsHeld,Is.False);
        Tick(left,true,true);Assert.That(handle.IsHeld,Is.False);
        Assert.That(Quaternion.Angle(adapter.canvas.transform.rotation,rotated),Is.LessThan(.01f));
        source.rotation=Quaternion.LookRotation(adapter.canvas.transform.position-source.position);
        Canvas.ForceUpdateCanvases();adapter.eye.Render();
        Tick(left,true,false);Tick(left,true,true);Assert.That(handle.IsHeld,Is.True);
    }
}

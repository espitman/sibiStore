using System.Collections.Generic;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.UI;

namespace Sibi.Store.VR {
// Each input source owns its press/drag state, so the other hand can keep interacting.
public sealed class SpatialPointers : MonoBehaviour {
    public Canvas canvas;
    public Camera eye;
    public Transform leftController, rightController;
    public OVRHand leftHand, rightHand;
    public PanelGrabHandle windowHandle;
    public string Status { get; private set; } = "Starting hand and controller tracking…";
    readonly Pointer[] pointers = { new Pointer(-101), new Pointer(-102), new Pointer(-103), new Pointer(-104) };
    public bool HasInteraction => System.Array.Exists(pointers, p => p.held);
    readonly List<RaycastResult> hits = new List<RaycastResult>();
    float nextEnable; bool multimodalEnabled;
    float handsLastTracked = float.NegativeInfinity;
    readonly Dictionary<GameObject,int> owners = new Dictionary<GameObject,int>();
    sealed class Pointer {
        public readonly int id;
        public PointerEventData data;
        public bool held;
        public bool awaitRelease = true;
        public LineRenderer ray;
        public PanelGrabHandle grab;
        public Pointer(int id) { this.id = id; }
    }
    void Update() {
        if (canvas == null || eye == null || EventSystem.current == null) return;
        if (Time.unscaledTime > nextEnable) {
            nextEnable = Time.unscaledTime + 3;
            multimodalEnabled = OVRInput.EnableSimultaneousHandsAndControllers();
        }
        bool lc = OVRInput.GetControllerPositionTracked(OVRInput.Controller.LTouch);
        bool rc = OVRInput.GetControllerPositionTracked(OVRInput.Controller.RTouch);
        bool lh = Valid(leftHand), rh = Valid(rightHand);
        // A brief tracking dropout must not wake controllers lying nearby.
        bool handsActive = PreferHands((leftHand != null && leftHand.IsTracked) || (rightHand != null && rightHand.IsTracked), Time.unscaledTime);
        lc &= !handsActive; rc &= !handsActive;
        Feed(pointers[0], leftController, lc, OVRInput.Get(OVRInput.Axis1D.PrimaryIndexTrigger, OVRInput.Controller.LTouch) > .65f, OVRInput.Get(OVRInput.Axis2D.PrimaryThumbstick, OVRInput.Controller.LTouch).y);
        Feed(pointers[1], rightController, rc, OVRInput.Get(OVRInput.Axis1D.PrimaryIndexTrigger, OVRInput.Controller.RTouch) > .65f, OVRInput.Get(OVRInput.Axis2D.PrimaryThumbstick, OVRInput.Controller.RTouch).y);
        Feed(pointers[2], leftHand != null ? leftHand.PointerPose : null, lh, lh && leftHand.GetFingerIsPinching(OVRHand.HandFinger.Index), 0);
        Feed(pointers[3], rightHand != null ? rightHand.PointerPose : null, rh, rh && rightHand.GetFingerIsPinching(OVRHand.HandFinger.Index), 0);
        Status = handsActive ? "Hand input • Controllers inactive" : $"Controller input • {(lc ? "L " : "")}{(rc ? "R" : "")}{(!lc && !rc ? "not tracked" : "")}";
    }
    bool PreferHands(bool tracked,float now){
        if(tracked)handsLastTracked=now;
        return tracked || now-handsLastTracked<.5f;
    }
    static bool Valid(OVRHand h) => h != null && h.IsTracked && h.IsDataHighConfidence && h.IsPointerPoseValid && !h.IsSystemGestureInProgress;
    void Feed(Pointer p, Transform source, bool tracked, bool down, float scroll) {
        if (p.data == null) p.data = new PointerEventData(EventSystem.current) { pointerId = p.id, button = PointerEventData.InputButton.Left };
        if (p.ray == null) {
            var go = new GameObject("Pointer " + p.id); go.transform.SetParent(transform);
            p.ray = go.AddComponent<LineRenderer>(); p.ray.positionCount = 2; p.ray.startWidth = .002f; p.ray.endWidth = .001f;
            p.ray.material = new Material(Shader.Find("Sprites/Default")); p.ray.startColor = p.ray.endColor = new Color(1,.76f,.03f,.85f);
        }
        var d = p.data;
        if (!tracked || source == null) { Cancel(p); p.awaitRelease = true; p.ray.enabled = false; return; }
        if (p.awaitRelease) { if (!down) p.awaitRelease = false; down = false; }
        var ray = new Ray(source.position, source.forward);
        if(p.grab!=null){
            var captured=p.grab;
            if(down)p.grab.Move(p.id,ray,Mathf.Abs(scroll)>.18f?scroll*Time.unscaledDeltaTime*.8f:0);
            else {p.grab.End(p.id);p.grab=null;p.held=false;}
            p.ray.enabled=true;p.ray.SetPosition(0,source.position);p.ray.SetPosition(1,captured.GrabPoint);
            return;
        }
        if(PanelGrabHandle.IsPanelHeld(canvas.transform)){Cancel(p);p.awaitRelease=true;p.ray.enabled=false;return;}
        var plane = new Plane(canvas.transform.forward, canvas.transform.position);
        bool intersects = plane.Raycast(ray, out var distance) && distance > 0 && distance < 8;
        var point = ray.GetPoint(intersects ? distance : 2);
        p.ray.enabled = true; p.ray.SetPosition(0, source.position); p.ray.SetPosition(1, point);
        var screen = (Vector2)eye.WorldToScreenPoint(point);
        d.delta = screen - d.position; d.position = screen;
        hits.Clear();
        if (intersects) canvas.GetComponent<GraphicRaycaster>().Raycast(d, hits);
        d.pointerCurrentRaycast = hits.Count > 0 ? hits[0] : new RaycastResult();
        var target = d.pointerCurrentRaycast.gameObject;
        if (target != d.pointerEnter) {
            if (d.pointerEnter != null) ExecuteEvents.ExecuteHierarchy(d.pointerEnter, d, ExecuteEvents.pointerExitHandler);
            d.pointerEnter = target;
            if (target != null) ExecuteEvents.ExecuteHierarchy(target, d, ExecuteEvents.pointerEnterHandler);
        }
        if (down && !p.held) {
            var handle=target!=null?target.GetComponentInParent<PanelGrabHandle>():null;
            if(handle!=null){
                Cancel(p);
                if(handle.TryBegin(p.id,ray))p.grab=handle;
                p.held=true;return;
            }
            d.pressPosition = screen; d.pointerPressRaycast = d.pointerCurrentRaycast; d.eligibleForClick = target != null; d.useDragThreshold = true;
            var press = ExecuteEvents.GetEventHandler<IPointerDownHandler>(target) ?? ExecuteEvents.GetEventHandler<IPointerClickHandler>(target);
            if (press != null && owners.TryGetValue(press,out var owner) && owner != p.id) { p.held = down; return; }
            if (press != null) owners[press] = p.id;
            d.pointerPress = ExecuteEvents.ExecuteHierarchy(target, d, ExecuteEvents.pointerDownHandler) ?? ExecuteEvents.GetEventHandler<IPointerClickHandler>(target);
            d.rawPointerPress = target;
            d.pointerDrag = ExecuteEvents.GetEventHandler<IDragHandler>(target);
            if (d.pointerDrag != null) ExecuteEvents.Execute(d.pointerDrag, d, ExecuteEvents.initializePotentialDrag);
        }
        if (down && d.pointerDrag != null) {
            if (!d.dragging && (screen - d.pressPosition).sqrMagnitude > 100) {
                if (owners.TryGetValue(d.pointerDrag,out var dragOwner) && dragOwner != p.id) { Cancel(p); p.awaitRelease = true; return; }
                owners[d.pointerDrag] = p.id;
                d.dragging = true; d.eligibleForClick = false;
                ExecuteEvents.Execute(d.pointerDrag, d, ExecuteEvents.beginDragHandler);
            }
            if (d.dragging) ExecuteEvents.Execute(d.pointerDrag, d, ExecuteEvents.dragHandler);
        }
        if (down && (screen-d.pressPosition).sqrMagnitude > 100) d.eligibleForClick = false;
        if (!down && p.held) {
            if (d.pointerPress != null) ExecuteEvents.Execute(d.pointerPress, d, ExecuteEvents.pointerUpHandler);
            if (d.eligibleForClick && d.pointerPress == ExecuteEvents.GetEventHandler<IPointerClickHandler>(target)) ExecuteEvents.Execute(d.pointerPress, d, ExecuteEvents.pointerClickHandler);
            if (d.dragging && d.pointerDrag != null) ExecuteEvents.Execute(d.pointerDrag, d, ExecuteEvents.endDragHandler);
            ReleaseOwnership(p);
            d.pointerPress = null; d.pointerDrag = null; d.eligibleForClick = false; d.dragging = false;
        }
        if (Mathf.Abs(scroll) > .18f && target != null) { d.scrollDelta = new Vector2(0,scroll * Time.unscaledDeltaTime * 12); ExecuteEvents.ExecuteHierarchy(target,d,ExecuteEvents.scrollHandler); }
        p.held = down;
    }
    void ReleaseOwnership(Pointer p) {
        var keys = new List<GameObject>();
        foreach (var pair in owners) if(pair.Value == p.id) keys.Add(pair.Key);
        foreach (var key in keys) owners.Remove(key);
    }
    void Cancel(Pointer p) {
        if(p.grab!=null){p.grab.End(p.id);p.grab=null;}
        ReleaseOwnership(p);
        var d = p.data; if (d == null) return;
        if (d.pointerPress != null) ExecuteEvents.Execute(d.pointerPress,d,ExecuteEvents.pointerUpHandler);
        if (d.dragging && d.pointerDrag != null) ExecuteEvents.Execute(d.pointerDrag,d,ExecuteEvents.endDragHandler);
        if (d.pointerEnter != null) ExecuteEvents.ExecuteHierarchy(d.pointerEnter,d,ExecuteEvents.pointerExitHandler);
        d.pointerPress = null; d.pointerDrag = null; d.pointerEnter = null; d.dragging = false; d.eligibleForClick = false; p.held = false;
    }
    void OnDisable() { foreach (var p in pointers) { Cancel(p); p.awaitRelease = true; if(p.ray != null) p.ray.enabled = false; } }
    void OnDestroy() { foreach (var p in pointers) if(p.ray != null) Destroy(p.ray.material); }
}
}

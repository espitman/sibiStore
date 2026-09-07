using UnityEngine;

namespace Sibi.Store.VR {
// Captures a world-space ray, so dragging continues beyond the old panel bounds.
public sealed class PanelGrabHandle : MonoBehaviour {
    Transform panel;
    int? owner;
    float distance;
    Vector3 offset;
    public bool IsHeld => owner.HasValue;
    public Vector3 GrabPoint => panel!=null?panel.position-offset:Vector3.zero;
    public void Initialize(Transform target) { panel=target; }
    public bool TryBegin(int pointer, Ray ray) {
        if(panel==null || owner.HasValue)return false;
        if(!new Plane(panel.forward,panel.position).Raycast(ray,out distance) || distance<=0)return false;
        owner=pointer;offset=panel.position-ray.GetPoint(distance);
        return true;
    }
    public void Move(int pointer, Ray ray, float depthDelta=0) {
        if(owner!=pointer)return;
        if(depthDelta!=0)distance=Mathf.Clamp(distance+depthDelta,.65f,4f);
        panel.position=ray.GetPoint(distance)+offset;
    }
    public void End(int pointer) { if(owner==pointer)owner=null; }
    void OnDisable() { owner=null; }
}
}

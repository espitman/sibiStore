using UnityEngine;

namespace Sibi.Store.VR {
public enum PanelManipulationMode { Move, Rotate }

// Shared by every handle targeting the same panel so move and rotate are mutually exclusive.
sealed class PanelManipulationState : MonoBehaviour {
    public int? owner;
    public PanelGrabHandle handle;
}

// Captures a world-space ray, so manipulation continues beyond the old handle bounds.
public sealed class PanelGrabHandle : MonoBehaviour {
    Transform panel;
    PanelManipulationState state;
    PanelManipulationMode mode;
    float distance;
    Vector3 offset;
    Vector3 startRayAngles;
    Vector3 startPanelAngles;
    float yawLimit = 55f;
    float pitchLimit = 35f;
    public bool IsHeld => state != null && state.handle == this && state.owner.HasValue;
    public Vector3 GrabPoint => panel != null ? (mode == PanelManipulationMode.Rotate ? panel.position : panel.position-offset) : Vector3.zero;
    public PanelManipulationMode Mode => mode;
    public void Initialize(Transform target, PanelManipulationMode manipulationMode = PanelManipulationMode.Move, float maxYaw = 55f, float maxPitch = 35f) {
        panel=target;mode=manipulationMode;yawLimit=Mathf.Max(0,maxYaw);pitchLimit=Mathf.Max(0,maxPitch);
        state=panel!=null?panel.GetComponent<PanelManipulationState>():null;
        if(panel!=null && state==null)state=panel.gameObject.AddComponent<PanelManipulationState>();
    }
    public bool TryBegin(int pointer, Ray ray) {
        if(panel==null || state==null || state.owner.HasValue)return false;
        if(!new Plane(panel.forward,panel.position).Raycast(ray,out distance) || distance<=0)return false;
        state.owner=pointer;state.handle=this;offset=panel.position-ray.GetPoint(distance);
        startRayAngles=DirectionAngles(ray.direction);startPanelAngles=panel.eulerAngles;
        return true;
    }
    public void Move(int pointer, Ray ray, float depthDelta=0) {
        if(!Owns(pointer))return;
        if(mode==PanelManipulationMode.Rotate){
            var current=DirectionAngles(ray.direction);
            var yaw=Mathf.Clamp(Mathf.DeltaAngle(startRayAngles.y,current.y),-yawLimit,yawLimit);
            var pitch=Mathf.Clamp(Mathf.DeltaAngle(startRayAngles.x,current.x),-pitchLimit,pitchLimit);
            panel.rotation=Quaternion.Euler(startPanelAngles.x+pitch,startPanelAngles.y+yaw,startPanelAngles.z);
            return;
        }
        if(depthDelta!=0)distance=Mathf.Clamp(distance+depthDelta,.65f,4f);
        panel.position=ray.GetPoint(distance)+offset;
    }
    public void End(int pointer) { if(Owns(pointer)){state.owner=null;state.handle=null;} }
    public static bool IsPanelHeld(Transform target) {
        var shared=target!=null?target.GetComponent<PanelManipulationState>():null;
        return shared!=null && shared.owner.HasValue;
    }
    bool Owns(int pointer)=>state!=null && state.handle==this && state.owner==pointer;
    static Vector3 DirectionAngles(Vector3 direction){
        direction.Normalize();
        return new Vector3(-Mathf.Asin(direction.y)*Mathf.Rad2Deg,Mathf.Atan2(direction.x,direction.z)*Mathf.Rad2Deg,0);
    }
    void OnDisable() { if(state!=null && state.handle==this){state.owner=null;state.handle=null;} }
}
}

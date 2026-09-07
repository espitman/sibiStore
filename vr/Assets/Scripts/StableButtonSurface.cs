using System.Collections.Generic;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.UI;

namespace Sibi.Store.VR {
// Keep large flat fills constant. Focus uses an outline and aggregates all rays,
// instead of competing full-surface ColorTint tweens for each hand/controller.
public sealed class StableButtonSurface : MonoBehaviour, IPointerEnterHandler, IPointerExitHandler {
    readonly HashSet<int> hovering=new HashSet<int>();
    Button button; Image surface; Outline outline; Color color;
    public void Initialize(Button target,Color fill){
        button=target;surface=target.GetComponent<Image>();color=fill;
        button.transition=Selectable.Transition.None;
        outline=gameObject.AddComponent<Outline>();outline.effectColor=Color.white;outline.effectDistance=new Vector2(2,-2);outline.useGraphicAlpha=true;
        Apply();
    }
    public void OnPointerEnter(PointerEventData data){hovering.Add(data.pointerId);Apply();}
    public void OnPointerExit(PointerEventData data){hovering.Remove(data.pointerId);Apply();}
    void Update(){Apply();}
    void Apply(){
        if(button==null || outline==null)return;
        bool enabled=button.IsInteractable();
        var handle=GetComponent<PanelGrabHandle>();
        outline.enabled=enabled && (hovering.Count>0 || (handle!=null && handle.IsHeld));
        surface.color=enabled?color:color*new Color(.38f,.38f,.38f,1);
    }
    void OnDisable(){hovering.Clear();if(outline!=null)outline.enabled=false;}
}
}

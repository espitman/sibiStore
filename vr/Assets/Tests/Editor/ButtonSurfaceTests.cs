using NUnit.Framework;
using Sibi.Store.VR;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.UI;

public class ButtonSurfaceTests {
    [Test] public void MultiplePointersKeepOneStableFillAndSharedFocus(){
        var root=new GameObject("Button",typeof(RectTransform),typeof(Image),typeof(Button));
        var events=new GameObject("Events",typeof(EventSystem));
        try {
            var fill=new Color(1,.757f,.027f);var behavior=root.AddComponent<StableButtonSurface>();behavior.Initialize(root.GetComponent<Button>(),fill);
            var left=new PointerEventData(events.GetComponent<EventSystem>()){pointerId=-1};var right=new PointerEventData(events.GetComponent<EventSystem>()){pointerId=-2};
            behavior.OnPointerEnter(left);behavior.OnPointerEnter(right);behavior.OnPointerExit(left);
            Assert.That(root.GetComponent<Outline>().enabled,Is.True);
            Assert.That(root.GetComponent<Image>().color,Is.EqualTo(fill));
            Assert.That(root.GetComponent<Button>().transition,Is.EqualTo(Selectable.Transition.None));
            behavior.OnPointerExit(right);Assert.That(root.GetComponent<Outline>().enabled,Is.False);
            Assert.That(root.GetComponent<Image>().color,Is.EqualTo(fill));
        } finally {Object.DestroyImmediate(root);Object.DestroyImmediate(events);}
    }
}

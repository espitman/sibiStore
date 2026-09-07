using System.IO;
using System.Xml;
using UnityEditor.Android;

// Unity emits a separate launcher manifest. Keep its branding consistent with
// the library manifest instead of accepting the default Unity launcher icon.
public sealed class StoreAndroidManifest : IPostGenerateGradleAndroidProject {
    public int callbackOrder => 10000;
    public void OnPostGenerateGradleAndroidProject(string path) {
        var manifest=Path.GetFullPath(Path.Combine(path,"../launcher/src/main/AndroidManifest.xml"));
        var xml=new XmlDocument();xml.Load(manifest);
        var app=(XmlElement)xml.SelectSingleNode("/manifest/application");
        const string android="http://schemas.android.com/apk/res/android";
        app.SetAttribute("icon",android,"@drawable/ic_store");
        app.SetAttribute("label",android,"@string/app_name");
        app.SetAttribute("usesCleartextTraffic",android,"true");
        app.SetAttribute("networkSecurityConfig",android,"@xml/sibi_store_network_security_config");
        xml.Save(manifest);
    }
}

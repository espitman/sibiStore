using System;
using System.IO;
using System.Linq;
using UnityEditor;
using UnityEditor.Build;
using UnityEditor.Build.Reporting;
using UnityEditor.SceneManagement;
using UnityEditor.XR.Management;
using UnityEditor.XR.Management.Metadata;
using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.XR.Management;
using UnityEngine.XR.OpenXR;

public static class SibiBuild {
    public static void Prepare() {
        PlayerSettings.companyName="Sibi";PlayerSettings.productName="Sibi Store VR";
        PlayerSettings.SetApplicationIdentifier(NamedBuildTarget.Android,"com.sibi.store.vr");
        PlayerSettings.bundleVersion="0.1.4";PlayerSettings.Android.bundleVersionCode=5;
        PlayerSettings.Android.minSdkVersion=AndroidSdkVersions.AndroidApiLevel29;
        PlayerSettings.Android.targetSdkVersion=AndroidSdkVersions.AndroidApiLevel35;
        PlayerSettings.Android.targetArchitectures=AndroidArchitecture.ARM64;
        PlayerSettings.SetScriptingBackend(NamedBuildTarget.Android,ScriptingImplementation.IL2CPP);
        PlayerSettings.SetManagedStrippingLevel(NamedBuildTarget.Android,ManagedStrippingLevel.Low);
        PlayerSettings.SetApiCompatibilityLevel(NamedBuildTarget.Android,ApiCompatibilityLevel.NET_Standard);
        PlayerSettings.SetUseDefaultGraphicsAPIs(BuildTarget.Android,false);
        PlayerSettings.SetGraphicsAPIs(BuildTarget.Android,new[]{GraphicsDeviceType.Vulkan});
        PlayerSettings.colorSpace=ColorSpace.Linear;
        var quality=new SerializedObject(AssetDatabase.LoadAllAssetsAtPath("ProjectSettings/QualitySettings.asset")[0]);
        var levels=quality.FindProperty("m_QualitySettings");
        for(int i=0;i<levels.arraySize;i++)levels.GetArrayElementAtIndex(i).FindPropertyRelative("antiAliasing").intValue=4;
        quality.ApplyModifiedPropertiesWithoutUndo();QualitySettings.antiAliasing=4;
        PlayerSettings.Android.useCustomKeystore=false;
        PlayerSettings.Android.applicationEntry=AndroidApplicationEntry.Activity;
        PlayerSettings.defaultInterfaceOrientation=UIOrientation.LandscapeLeft;
        // OpenXR's Input System and the store's explicit pointer adapter coexist.
        var settings=new SerializedObject(AssetDatabase.LoadAllAssetsAtPath("ProjectSettings/ProjectSettings.asset")[0]);
        settings.FindProperty("m_ShowUnitySplashScreen").boolValue=false;
        settings.FindProperty("m_ShowUnitySplashLogo").boolValue=false;
        settings.ApplyModifiedPropertiesWithoutUndo();
        var input=settings.FindProperty("activeInputHandler");if(input!=null){input.intValue=2;settings.ApplyModifiedPropertiesWithoutUndo();}
        Directory.CreateDirectory("Assets/XR");
        if(!EditorBuildSettings.TryGetConfigObject(XRGeneralSettings.k_SettingsKey,out XRGeneralSettingsPerBuildTarget perTarget)) {
            perTarget=AssetDatabase.LoadAssetAtPath<XRGeneralSettingsPerBuildTarget>("Assets/XR/PerTargetSettings.asset");
            if(perTarget==null){perTarget=ScriptableObject.CreateInstance<XRGeneralSettingsPerBuildTarget>();AssetDatabase.CreateAsset(perTarget,"Assets/XR/PerTargetSettings.asset");}
            EditorBuildSettings.AddConfigObject(XRGeneralSettings.k_SettingsKey,perTarget,true);
        }
        var xr=perTarget.SettingsForBuildTarget(BuildTargetGroup.Android);
        if(xr==null){
            xr=ScriptableObject.CreateInstance<XRGeneralSettings>();xr.name="Android Settings";
            AssetDatabase.AddObjectToAsset(xr,perTarget);
            perTarget.SetSettingsForBuildTarget(BuildTargetGroup.Android,xr);
        }
        if(xr.Manager==null){
            var manager=ScriptableObject.CreateInstance<XRManagerSettings>();manager.name="Android XR Manager";
            AssetDatabase.AddObjectToAsset(manager,perTarget);xr.Manager=manager;
        }
        EditorUtility.SetDirty(perTarget);EditorUtility.SetDirty(xr);EditorUtility.SetDirty(xr.Manager);
        // General settings must be sub-assets: XR Management migrates standalone
        // general-settings assets on reload, which otherwise loses the mapping.
        PlayerSettings.SetPreloadedAssets(PlayerSettings.GetPreloadedAssets().Where(a=>a!=null && (!(a is XRGeneralSettings) || a==xr)).ToArray());
        if(AssetDatabase.LoadMainAssetAtPath("Assets/XR/AndroidXRSettings.asset")!=null)AssetDatabase.DeleteAsset("Assets/XR/AndroidXRSettings.asset");
        xr.InitManagerOnStart=true;
        if(!XRPackageMetadataStore.AssignLoader(xr.Manager,"UnityEngine.XR.OpenXR.OpenXRLoader",BuildTargetGroup.Android))throw new Exception("Could not configure OpenXR loader");
        UnityEditor.XR.OpenXR.Features.FeatureHelpers.RefreshFeatures(BuildTargetGroup.Android);
        var openxr=OpenXRSettings.GetSettingsForBuildTargetGroup(BuildTargetGroup.Android);
        bool meta=false;
        foreach(var feature in openxr.GetFeatures<UnityEngine.XR.OpenXR.Features.OpenXRFeature>()){
            var name=feature.GetType().FullName;
            if(name=="Meta.XR.MetaXRFeature" || name.EndsWith("OculusTouchControllerProfile") || name.EndsWith("MetaQuestTouchPlusControllerProfile")){feature.enabled=true;EditorUtility.SetDirty(feature);if(name=="Meta.XR.MetaXRFeature")meta=true;}
        }
        EditorUtility.SetDirty(openxr);
        if(!meta)throw new Exception("Meta XR OpenXR feature was not registered");
        var config=OVRProjectConfig.CachedProjectConfig;
        config.handTrackingSupport=OVRProjectConfig.HandTrackingSupport.ControllersAndHands;
        config.targetDeviceTypes=new System.Collections.Generic.List<OVRProjectConfig.DeviceType>{OVRProjectConfig.DeviceType.Quest3};
        OVRProjectConfig.CommitProjectConfig(config);
        var graphics=new SerializedObject(AssetDatabase.LoadAllAssetsAtPath("ProjectSettings/GraphicsSettings.asset")[0]);
        var shaders=graphics.FindProperty("m_AlwaysIncludedShaders");
        foreach(var shaderName in new[]{"UI/Default","Sprites/Default"}){
            var shader=Shader.Find(shaderName);if(shader==null)throw new Exception("Missing shader: "+shaderName);
            bool found=false;for(int i=0;i<shaders.arraySize;i++)if(shaders.GetArrayElementAtIndex(i).objectReferenceValue==shader)found=true;
            if(!found){int index=shaders.arraySize;shaders.InsertArrayElementAtIndex(index);shaders.GetArrayElementAtIndex(index).objectReferenceValue=shader;}
        }
        graphics.ApplyModifiedPropertiesWithoutUndo();
        Directory.CreateDirectory("Assets/Scenes");
        var scene=EditorSceneManager.NewScene(NewSceneSetup.EmptyScene,NewSceneMode.Single);
        EditorSceneManager.SaveScene(scene,"Assets/Scenes/Store.unity");
        EditorBuildSettings.scenes=new[]{new EditorBuildSettingsScene("Assets/Scenes/Store.unity",true)};
        var devAgent=AssetDatabase.LoadMainAssetAtPath("Assets/Resources/DevAgentSettings.asset");
        if(devAgent!=null){var local=new SerializedObject(devAgent);var enabled=local.FindProperty("enabled");if(enabled!=null)enabled.boolValue=false;var token=local.FindProperty("accessToken");if(token!=null)token.stringValue="";var address=local.FindProperty("serverAddress");if(address!=null)address.stringValue="127.0.0.1";local.ApplyModifiedPropertiesWithoutUndo();}
        AssetDatabase.SaveAssets();
        Debug.Log("Sibi Store VR: OpenXR, Meta XR, ARM64, hands and controllers configured.");
    }
    public static void VerifyConfiguration() {
        var xr=XRGeneralSettingsPerBuildTarget.XRGeneralSettingsForBuildTarget(BuildTargetGroup.Android);
        if(xr==null || !xr.InitManagerOnStart || xr.Manager==null || !xr.Manager.activeLoaders.Any(l=>l.GetType().FullName=="UnityEngine.XR.OpenXR.OpenXRLoader"))throw new Exception("Persisted Android OpenXR loader is missing");
        var settings=OpenXRSettings.GetSettingsForBuildTargetGroup(BuildTargetGroup.Android);
        if(!settings.GetFeatures<UnityEngine.XR.OpenXR.Features.OpenXRFeature>().Any(f=>f.GetType().FullName=="Meta.XR.MetaXRFeature" && f.enabled))throw new Exception("Persisted Meta XR feature is disabled");
        Debug.Log("Persisted Android OpenXR configuration verified after reload.");
    }
    public static void Build() {
        Prepare();
        UnityEditor.Android.AndroidExternalToolsSettings.jdkRootPath=Environment.GetEnvironmentVariable("SIBI_VR_JDK");
        UnityEditor.Android.AndroidExternalToolsSettings.sdkRootPath=Environment.GetEnvironmentVariable("ANDROID_HOME");
        UnityEditor.Android.AndroidExternalToolsSettings.ndkRootPath=Environment.GetEnvironmentVariable("SIBI_VR_NDK");
        Directory.CreateDirectory("Builds");
        var report=BuildPipeline.BuildPlayer(new BuildPlayerOptions { scenes=EditorBuildSettings.scenes.Select(s=>s.path).ToArray(),locationPathName="Builds/sibi-store-vr-unsigned.apk",target=BuildTarget.Android,options=BuildOptions.None });
        if(report.summary.result!=BuildResult.Succeeded)throw new Exception("VR build failed: "+report.summary.result);
    }
}

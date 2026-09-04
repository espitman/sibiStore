// Generate disposable AVD metadata; user AVDs and their disk images are never modified.
const fs = require('node:fs');
const path = require('node:path');
const home=path.resolve(__dirname,'../test-results/avds');
const tv=process.argv[2]==='tv';
const name=tv?'SibiStore_TV':'SibiStore_Phone';
const folder=path.join(home,`${name}.avd`);
fs.mkdirSync(folder,{recursive:true});
fs.writeFileSync(path.join(home,`${name}.ini`),`avd.ini.encoding=UTF-8\npath=${folder}\ntarget=android-36\n`);
const config={AvdId:name,'avd.ini.displayname':name,'avd.ini.encoding':'UTF-8','abi.type':'arm64-v8a','hw.cpu.arch':'arm64','hw.cpu.ncore':'2','hw.ramSize':'2048','hw.gpu.enabled':'yes','hw.gpu.mode':'swiftshader_indirect','hw.lcd.width':tv?'1920':'1080','hw.lcd.height':tv?'1080':'2400','hw.lcd.density':tv?'320':'420','hw.initialOrientation':tv?'landscape':'portrait','hw.keyboard':'yes','hw.dPad':tv?'yes':'no','hw.mainKeys':tv?'yes':'no','hw.device.name':tv?'tv_1080p':'medium_phone','hw.device.manufacturer':'Generic','disk.dataPartition.size':'3G','image.sysdir.1':`system-images/android-36/${tv?'google-tv':'google_apis_playstore'}/arm64-v8a/`,'tag.id':tv?'google-tv':'google_apis_playstore','target':'android-36','fastboot.forceColdBoot':'yes','showDeviceFrame':'no','hw.audioInput':'no','hw.camera.back':'none','hw.camera.front':'none','PlayStore.enabled':'false'};
fs.writeFileSync(path.join(folder,'config.ini'),Object.entries(config).map(([k,v])=>`${k}=${v}`).join('\n')+'\n');

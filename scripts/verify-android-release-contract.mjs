import fs from "node:fs";

const gradle=fs.readFileSync("android/app/build.gradle","utf8");
const html=fs.readFileSync("android/app/src/main/assets/index.html","utf8");

const versionName=gradle.match(/versionName\s+'([^']+)'/)?.[1];
const versionCode=Number(gradle.match(/versionCode\s+(\d+)/)?.[1]||0);
const appBuild=html.match(/const APP_BUILD='([^']+)'/)?.[1];
const appVersionCode=Number(html.match(/const APP_VERSION_CODE=(\d+);/)?.[1]||0);

const failures=[];
const fail=(message)=>failures.push(message);

if(!versionName)fail("Gradle versionName is missing");
if(!Number.isSafeInteger(versionCode)||versionCode<1)fail("Gradle versionCode is invalid");
if(appBuild!==versionName)fail("Bundled APP_BUILD does not match Gradle versionName");
if(appVersionCode!==versionCode)fail("Bundled APP_VERSION_CODE does not match Gradle versionCode");

const required=[
  "async function fetchReleaseMetadata()",
  "const endpoint=INSTALL_ORIGIN+'?release=1&t='+Date.now();",
  "meta.channel!=='customer'",
  "meta.productionMoneyEnabled!==false",
  "meta.betaSigning!==true",
  "Untrusted Android release identity",
  "66a1431469d7a67450a3d28ce7adfcf11295eb274be305add681613bc1ad4d55",
  "versionCode>=86",
  "new app features need a newer APK.",
  "INSTALL_ORIGIN+'?release=1&t='",
  "meta.versionCode>installed.versionCode",
  "newerAndroidRelease(meta,installed)",
  "function trustedApkDownload(value)",
  "u.protocol!=='https:'",
  "u.origin!==PUBLIC_ORIGIN",
  "function androidApkDownload(meta)",
  "https://raw.githubusercontent.com/festusakona40-code/Predict-UG-Core/",
  "sourceArtifactCommit",
  "refreshAppInterface",
  "location.href=androidApkDownload(meta)",
  "setTimeout(()=>checkForAppUpdate(false),600)"
];
for(const marker of required){
  if(!html.includes(marker))fail("Missing updater contract marker: "+marker);
}

if(/location\.reload\(\)\s*;?\s*}\s*async function runAppCheck/.test(html)){
  fail("Update button regressed to reload-only behavior");
}

if(html.includes("PUBLIC_ORIGIN+'/downloads/latest.json?t='")){
  fail("Release discovery must not depend on Render's stale metadata");
}

if(failures.length){
  for(const message of failures)console.error("release-contract: "+message);
  process.exit(1);
}

console.log(JSON.stringify({
  status:"passed",
  versionName,
  versionCode,
  updater:"verified-owned-apk-route"
},null,2));

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
  "INSTALL_ORIGIN+'?release=1&t='",
  "latestCode>APP_VERSION_CODE",
  "function trustedApkDownload(value)",
  "u.protocol==='https:'",
  "u.host==='predict-ug-app.onrender.com'",
  "u.pathname.startsWith('/downloads/')",
  "setTimeout(()=>checkForAppUpdate(false),600)"
];
for(const marker of required){
  if(!html.includes(marker))fail("Missing updater contract marker: "+marker);
}

if(/location\.reload\(\)\s*;?\s*}\s*async function runAppCheck/.test(html)){
  fail("Update button regressed to reload-only behavior");
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

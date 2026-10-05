import fs from "node:fs";
import crypto from "node:crypto";
import { execFileSync } from "node:child_process";

const fail = (message) => {
  console.error("public-export-guard: " + message);
  process.exitCode = 1;
};

const manifest = JSON.parse(fs.readFileSync("PUBLIC_EXPORT_MANIFEST.json", "utf8"));
const provenance = JSON.parse(fs.readFileSync("SOURCE_PROVENANCE.json", "utf8"));

if (manifest.schema_version !== 1) fail("unsupported manifest schema");
if (manifest.policy !== "allowlist_only") fail("export policy must remain allowlist_only");
if (manifest.source_repository !== "festusakona40-code/Predict-Ug") fail("unexpected private source repository");
if (manifest.public_repository !== "festusakona40-code/Predict-UG-Core") fail("unexpected public mirror repository");
if (manifest.guarantees?.production_money_enabled !== false) fail("public mirror must never enable production money");
if (manifest.guarantees?.production_deployments_from_public_repo !== false) fail("public mirror must never own production deployments");
if (manifest.guarantees?.production_signing_material_allowed !== false) fail("production signing material must remain forbidden");
if (manifest.guarantees?.provider_credentials_allowed !== false) fail("provider credentials must remain forbidden");

if (provenance.source_repository !== manifest.source_repository) fail("provenance source repository disagrees with export manifest");
if (provenance.source_commit !== manifest.source_commit) fail("provenance source commit disagrees with export manifest");
if (provenance.production_money_enabled !== false) fail("provenance must record production_money_enabled=false");

const tracked = execFileSync("git", ["ls-files", "-z"], { encoding: "utf8" })
  .split("\0")
  .filter(Boolean)
  .sort();

const exact = new Set(Object.keys(manifest.exact_copy_files || {}));
const transformed = new Set(Object.keys(manifest.transformed_public_files || {}));
const maintained = new Set(manifest.public_maintained_files || []);
const allowed = new Set([...exact, ...transformed, ...maintained]);

for (const file of tracked) {
  if (!allowed.has(file)) fail("tracked file is outside the explicit public allowlist: " + file);
}

for (const file of allowed) {
  if (!tracked.includes(file)) fail("allowlisted tracked file is missing: " + file);
}

const gitBlobSha1 = (content) => {
  const body = Buffer.isBuffer(content) ? content : Buffer.from(content);
  const header = Buffer.from("blob " + body.length + "\0");
  return crypto.createHash("sha1").update(header).update(body).digest("hex");
};

for (const [file, expected] of Object.entries(manifest.exact_copy_files || {})) {
  const actual = gitBlobSha1(fs.readFileSync(file));
  if (actual !== expected) {
    fail(file + " drifted from private-source blob " + expected + " (actual " + actual + ")");
  }
}

for (const prefix of manifest.forbidden_prefixes || []) {
  for (const file of tracked) {
    if (file.startsWith(prefix)) fail("forbidden private path crossed into public mirror: " + file);
  }
}

const textFiles = tracked.filter((file) => {
  try {
    const stat = fs.statSync(file);
    return stat.size <= 5_000_000;
  } catch {
    return false;
  }
});

const forbiddenTokens = (manifest.forbidden_patterns || []).filter((x) =>
  !x.startsWith(".") || x.includes("PRIVATE KEY")
);
for (const file of textFiles) {
  let content;
  try { content = fs.readFileSync(file, "utf8"); } catch { continue; }
  for (const token of forbiddenTokens) {
    if (content.includes(token)) fail("forbidden private/security marker found in " + file + ": " + token);
  }
}

const forbiddenSuffixes = [".pem", ".p12", ".pfx", ".jks", ".keystore"];
for (const file of tracked) {
  if (file === ".env" || file.startsWith(".env.") || forbiddenSuffixes.some((suffix) => file.endsWith(suffix))) {
    fail("secret-bearing file type is forbidden: " + file);
  }
}

const gradle = fs.readFileSync("android/app/build.gradle", "utf8");
for (const marker of ["merchantImplementation", "releaseUpload", "PREDICT_UG_UPLOAD_STORE_FILE", "applicationIdSuffix \".merchant\""]) {
  if (gradle.includes(marker)) fail("consumer Gradle projection regained private merchant/signing behavior: " + marker);
}
if (!gradle.includes("consumer { dimension \"mode\" }")) fail("consumer Gradle projection is missing the consumer flavor");

const baseManifest = fs.readFileSync("android/app/src/main/AndroidManifest.xml", "utf8");
for (const marker of ["android.permission.RECEIVE_SMS", "android.permission.READ_SMS", "MerchantSmsReceiver"]) {
  if (baseManifest.includes(marker)) fail("consumer manifest regained forbidden merchant capability: " + marker);
}

if (!fs.readFileSync("android/app/src/main/assets/index.html", "utf8").includes("Predict UG")) {
  fail("consumer app marker missing from exported frontend");
}

if (!process.exitCode) {
  console.log(
    "public-export-guard: passed (" +
    exact.size + " exact private-source blobs, " +
    transformed.size + " transformed files, " +
    maintained.size + " public-maintained files)"
  );
}

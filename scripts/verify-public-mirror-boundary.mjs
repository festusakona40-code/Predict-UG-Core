import fs from "node:fs";
import { execFileSync } from "node:child_process";

const policy = JSON.parse(fs.readFileSync("MIRROR_POLICY.json", "utf8"));
const provenance = JSON.parse(fs.readFileSync("SOURCE_PROVENANCE.json", "utf8"));

const fail = (message) => {
  console.error("public-mirror-boundary: " + message);
  process.exitCode = 1;
};

const tracked = execFileSync("git", ["ls-files", "-z"], { encoding: "utf8" })
  .split("\0")
  .filter(Boolean)
  .sort();

const isAllowed = (rel) =>
  policy.public_allowed_exact_files.includes(rel) ||
  policy.public_allowed_prefixes.some((prefix) => rel.startsWith(prefix));

const isForbiddenPath = (rel) =>
  policy.forbidden_prefixes.some((prefix) => rel === prefix.replace(/\/$/, "") || rel.startsWith(prefix)) ||
  policy.forbidden_filename_extensions.some((ext) => rel.toLowerCase().endsWith(ext));

const metadataFiles = new Set(["MIRROR_POLICY.json", "PUBLIC_EXPORT_MANIFEST.json"]);
const privateMarkerControlFiles = new Set(policy.control_files_allowed_to_name_private_markers || []);

for (const rel of tracked) {
  if (!isAllowed(rel)) fail("unexpected tracked file outside allowlist: " + rel);
  if (isForbiddenPath(rel)) fail("forbidden path or file type: " + rel);

  if (!metadataFiles.has(rel)) {
    let text = "";
    try { text = fs.readFileSync(rel, "utf8"); } catch { continue; }

    for (const marker of policy.secret_content_patterns || []) {
      if (text.includes(marker)) fail("forbidden secret marker in " + rel + ": " + marker);
    }

    if (!privateMarkerControlFiles.has(rel)) {
      for (const marker of policy.private_source_markers || []) {
        if (text.includes(marker)) fail("forbidden private-source marker in " + rel + ": " + marker);
      }
    }
  }
}

for (const rel of policy.public_allowed_exact_files) {
  if (!tracked.includes(rel)) fail("required allowlisted tracked file missing: " + rel);
}

if (policy.production_money_allowed !== false) {
  fail("public mirror policy must keep production_money_allowed=false");
}
if (provenance.source_repository !== policy.canonical_private_repository) {
  fail("provenance source repository does not match canonical private repository");
}
if (provenance.public_repository !== policy.public_ci_repository) {
  fail("provenance public repository does not match this CI mirror");
}
if (provenance.source_visibility !== "private") {
  fail("provenance must identify the canonical source as private");
}
if (!/^[0-9a-f]{40}$/.test(String(provenance.source_commit || ""))) {
  fail("provenance source_commit must be a full 40-character Git commit");
}
if (provenance.mirror_policy_version !== policy.policy_version) {
  fail("provenance mirror_policy_version does not match MIRROR_POLICY.json");
}
if (provenance.production_money_enabled !== false) {
  fail("public mirror provenance must keep production_money_enabled=false");
}

const exclusions = Array.isArray(provenance.private_components_excluded)
  ? provenance.private_components_excluded
  : [];
const hasExclusion = (base) => exclusions.some((x) => x === base || x === base + "/**" || x.startsWith(base + "/"));
for (const item of [
  "supabase",
  "android/app/src/merchant",
  "production signing material",
  "provider credentials"
]) {
  if (!hasExclusion(item)) fail("provenance missing required private exclusion: " + item);
}

if (!process.exitCode) {
  console.log("public-mirror-boundary: passed policy " + policy.policy_version + " for " + tracked.length + " tracked files");
}

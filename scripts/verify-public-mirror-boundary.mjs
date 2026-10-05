import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const policy = JSON.parse(fs.readFileSync(path.join(root, "MIRROR_POLICY.json"), "utf8"));
const provenance = JSON.parse(fs.readFileSync(path.join(root, "SOURCE_PROVENANCE.json"), "utf8"));

const fail = (message) => {
  console.error("public-mirror-boundary: " + message);
  process.exitCode = 1;
};

const norm = (p) => p.split(path.sep).join("/");
const ignoredPrefixes = [".git/", "node_modules/", "android/.gradle/", "android/app/build/"];
const metadataFiles = new Set(["MIRROR_POLICY.json"]);

function walk(dir) {
  const out = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const absolute = path.join(dir, entry.name);
    const rel = norm(path.relative(root, absolute));
    if (ignoredPrefixes.some((p) => rel === p.slice(0, -1) || rel.startsWith(p))) continue;
    if (entry.isDirectory()) out.push(...walk(absolute));
    else if (entry.isFile()) out.push(rel);
  }
  return out;
}

const isAllowed = (rel) =>
  policy.public_allowed_exact_files.includes(rel) ||
  policy.public_allowed_prefixes.some((prefix) => rel.startsWith(prefix));

const isForbiddenPath = (rel) =>
  policy.forbidden_prefixes.some((prefix) => rel === prefix.replace(/\/$/, "") || rel.startsWith(prefix)) ||
  policy.forbidden_filename_extensions.some((ext) => rel.toLowerCase().endsWith(ext));

for (const rel of walk(root)) {
  if (!isAllowed(rel)) fail("unexpected file outside allowlist: " + rel);
  if (isForbiddenPath(rel)) fail("forbidden path or file type: " + rel);

  if (!metadataFiles.has(rel)) {
    const bytes = fs.readFileSync(path.join(root, rel));
    if (!bytes.includes(0)) {
      const text = bytes.toString("utf8");
      for (const marker of policy.forbidden_content_patterns) {
        if (text.includes(marker)) fail("forbidden content marker in " + rel + ": " + marker);
      }
    }
  }
}

for (const rel of policy.public_allowed_exact_files) {
  if (!fs.existsSync(path.join(root, rel))) fail("required allowlisted file missing: " + rel);
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

const requiredExclusions = [
  "supabase/migrations",
  "android/app/src/merchant",
  "production signing material",
  "provider credentials"
];
for (const item of requiredExclusions) {
  if (!Array.isArray(provenance.private_components_excluded) ||
      !provenance.private_components_excluded.includes(item)) {
    fail("provenance missing required private exclusion: " + item);
  }
}

if (!process.exitCode) {
  console.log("public-mirror-boundary: passed policy " + policy.policy_version);
}

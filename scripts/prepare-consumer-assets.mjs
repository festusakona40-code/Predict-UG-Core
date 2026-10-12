import { copyFileSync, existsSync, readFileSync } from "node:fs";

import { Script } from "node:vm";

const source = "node_modules/@supabase/supabase-js/dist/umd/supabase.js";
const target = "android/app/src/main/assets/supabase.js";
if (!existsSync(source)) {
  throw new Error("Missing @supabase/supabase-js UMD bundle. Run npm ci first.");
}
copyFileSync(source, target);
console.log("Prepared local Supabase browser bundle for consumer APK.");

// Validate the SDK and classic app scripts in one global scope before packaging.
const html = readFileSync("android/app/src/main/assets/index.html", "utf8");
const scripts = [...html.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/g)]
  .filter(([, attrs]) => !/\bsrc=|type=["']module/.test(attrs))
  .map(([, , code]) => code);
new Script(readFileSync(source, "utf8") + "\n" + scripts.join("\n"));
console.log("Consumer SDK/app startup scope: passed.");

import { copyFileSync, existsSync } from "node:fs";

const source = "node_modules/@supabase/supabase-js/dist/umd/supabase.js";
const target = "android/app/src/main/assets/supabase.js";
if (!existsSync(source)) {
  throw new Error("Missing @supabase/supabase-js UMD bundle. Run npm ci first.");
}
copyFileSync(source, target);
console.log("Prepared local Supabase browser bundle for consumer APK.");

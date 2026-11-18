import { cpSync, existsSync } from "node:fs";
import { basename, join } from "node:path";

const cwd = process.cwd();
const appName = basename(cwd);

const standaloneDir = join(cwd, ".next", "standalone", "apps", appName);
const publicSrc = join(cwd, "public");
const publicDest = join(standaloneDir, "public");
const staticSrc = join(cwd, ".next", "static");
const staticDest = join(standaloneDir, ".next", "static");

if (existsSync(publicSrc)) {
  cpSync(publicSrc, publicDest, { recursive: true });
  console.log(`Copied public/ to ${publicDest}`);
}

if (existsSync(staticSrc)) {
  cpSync(staticSrc, staticDest, { recursive: true });
  console.log(`Copied .next/static/ to ${staticDest}`);
}

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const distDir = path.resolve(__dirname, '../dist');
const targetDir = path.resolve(__dirname, '../../../backend/static/admin');

if (!fs.existsSync(distDir)) {
  console.error(`[sync-to-backend] Error: Dist directory does not exist: ${distDir}`);
  process.exit(1);
}

console.log(`[sync-to-backend] Clean removal and synchronizing ${distDir} -> ${targetDir}...`);

// 1. Clean removal step
if (fs.existsSync(targetDir)) {
  try {
    fs.rmSync(targetDir, { recursive: true, force: true });
  } catch (err) {
    console.warn(`[sync-to-backend] Warning during clean removal: ${err.message}`);
  }
}
fs.mkdirSync(targetDir, { recursive: true });

// 2. File copying using fs.cpSync(distDir, targetDir, { recursive: true, force: true })
try {
  fs.cpSync(distDir, targetDir, { recursive: true, force: true });
} catch (err) {
  console.error(`[sync-to-backend] Error during fs.cpSync: ${err.message}`);
  throw err;
}

// 3. Verification of index.html in destination
const targetIndexHtml = path.join(targetDir, 'index.html');
if (!fs.existsSync(targetIndexHtml)) {
  console.error(`[sync-to-backend] Verification failed: ${targetIndexHtml} missing.`);
  process.exit(1);
}

const indexContent = fs.readFileSync(targetIndexHtml, 'utf8');
const assetMatches = indexContent.match(/src="[^"]*assets\/([^"]+)"|href="[^"]*assets\/([^"]+)"/g) || [];
for (const match of assetMatches) {
  const assetName = match.replace(/.*assets\//, '').replace(/["'].*/, '');
  const assetFile = path.join(targetDir, 'assets', assetName);
  if (!fs.existsSync(assetFile)) {
    console.error(`[sync-to-backend] Verification warning: Referenced asset not found: ${assetName}`);
  }
}

console.log('[sync-to-backend] Admin Web assets successfully synchronized with fs.cpSync.');

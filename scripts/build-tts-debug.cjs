// Standalone, debug-signed APK with an embedded JS bundle (no Metro server).
const fs = require('fs');
const { execFileSync } = require('child_process');
const path = require('path');
const root = path.resolve(__dirname, '..');
const run = (cmd, args, cwd = root) =>
  execFileSync(cmd, args, { cwd, stdio: 'inherit', env: process.env });
run('pnpm', ['run', 'generate:env:debug']);
run('pnpm', [
  'exec',
  'expo',
  'prebuild',
  '--platform',
  'android',
  '--no-install',
]);
// Expo can emit WebP launchers after the app plugin installs PNG variants.
// Android rejects two extensions for the same resource name.
for (const variant of ['main', 'debug', 'preRelease']) {
  const res = path.join(root, 'android/app/src', variant, 'res');
  if (!fs.existsSync(res)) continue;
  for (const dir of fs
    .readdirSync(res)
    .filter(name => name.startsWith('mipmap-'))) {
    const folder = path.join(res, dir);
    for (const file of fs
      .readdirSync(folder)
      .filter(name => name.endsWith('.webp'))) {
      if (fs.existsSync(path.join(folder, file.replace(/\.webp$/, '.png')))) {
        fs.unlinkSync(path.join(folder, file));
      }
    }
  }
}
const gradle = path.join(root, 'android/app/build.gradle');
const main = path.join(
  root,
  'android/app/src/main/java/com/rajarsheechatterjee/LNReader/MainApplication.kt',
);
const patch = (file, marker, replacement) => {
  const source = fs.readFileSync(file, 'utf8');
  if (source.includes(replacement)) return;
  if (!source.includes(marker))
    throw new Error(`Build template changed: ${file}`);
  fs.writeFileSync(file, source.replace(marker, replacement));
};
patch(gradle, 'react {', 'react {\n    debuggableVariants = []');
patch(
  main,
  'context = applicationContext,',
  'context = applicationContext,\n      useDevSupport = false,',
);
// The development launcher intercepts app startup even with dev support off.
// Exclude development-only modules from this generated standalone build.
const settingsGradle = path.join(root, 'android/settings.gradle');
patch(
  settingsGradle,
  'expoAutolinking.useExpoModules()',
  `expoAutolinking.exclude = ['@expo/dom-webview', 'expo-dev-client', 'expo-dev-launcher', 'expo-dev-menu', 'expo-dev-menu-interface']
expoAutolinking.useExpoModules()`,
);
run(
  './gradlew',
  [
    ':app:assembleDebug',
    '-PreactNativeArchitectures=arm64-v8a',
    '--max-workers=4',
  ],
  path.join(root, 'android'),
);

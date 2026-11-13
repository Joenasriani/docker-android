'use strict';
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');
const root = path.resolve(__dirname, '..');
const source = JSON.parse(fs.readFileSync(path.join(root, 'upstream/SOURCE.json')));
const www = path.join(root, 'www');
fs.rmSync(www, {recursive: true, force: true});
fs.mkdirSync(www, {recursive: true});
for (const [name, record] of Object.entries(source.files)) {
  const raw = fs.readFileSync(path.join(root, 'upstream/runtime', name));
  if (crypto.createHash('sha256').update(raw).digest('hex') !== record.sha256) throw Error('Frozen source mismatch: ' + name);
  // Joe's Android-only correction: no backing page or payment links in the app.
  if (name === 'invest/index.html') continue;
  let output = raw;
  if (name.endsWith('.html')) {
    const prefix = name.includes('/') ? '../' : './';
    // Native safe-area handling reserves the system bars, preserving original layout.
    output = Buffer.from(raw.toString().replace('viewport-fit=cover', 'viewport-fit=contain')
      .replace('<head>', '<head>\n<script src="' + prefix + 'android-runtime.js"></script>'));
    if (name === 'ending/index.html') {
      const html = output.toString();
      const withoutCTA = html.replace(/<a id="invest"[^>]*>[\s\S]*?<\/a>/, '');
      if (withoutCTA === html) throw Error('Could not locate the authorized Android-only backing CTA removal');
      output = Buffer.from(withoutCTA);
    }
  }
  const target = path.join(www, name);
  fs.mkdirSync(path.dirname(target), {recursive: true});
  fs.writeFileSync(target, output);
}
fs.copyFileSync(path.join(root, 'android-shell/android-runtime.js'), path.join(www, 'android-runtime.js'));
fs.cpSync(path.join(root, 'android-shell/licenses'), path.join(www, 'licenses'), {recursive: true});
fs.copyFileSync(path.join(root, 'docs/privacy-policy.txt'), path.join(www, 'privacy-policy.txt'));
console.log('Prepared 24 accepted runtime files, Android integration and notices; backing CTA/page excluded. Frozen snapshot retains all 25 source files.');

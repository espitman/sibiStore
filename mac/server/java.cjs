const { execFile } = require('node:child_process');
const { promisify } = require('node:util');
const path = require('node:path');
const run = promisify(execFile);

// Select Java per invocation; never change the user's JAVA_HOME or PATH.
async function signerCommand(toolDir, { platform = process.platform, arch = process.arch, execute = run } = {}) {
  if (platform !== 'darwin' || arch !== 'arm64') {
    return { file: path.join(toolDir, 'apksigner'), args: [] };
  }
  try {
    const { stdout } = await execute('/usr/libexec/java_home', ['-a', 'arm64', '-F'], { timeout: 10000 });
    const home = stdout.trim();
    if (!path.isAbsolute(home)) throw new Error('Invalid Java home');
    const java = path.join(home, 'bin/java');
    // Validate before launching: an Intel-only runtime must never start via Rosetta.
    await execute('/usr/bin/lipo', [java, '-verify_arch', 'arm64'], { timeout: 10000 });
    return { file: '/usr/bin/arch', args: ['-arm64', java, '-Xmx1024M', '-jar', path.join(toolDir, 'lib/apksigner.jar')] };
  } catch (cause) {
    throw new Error('Sibi Store requires an Apple Silicon Java runtime to verify APK signatures. Install an ARM64 JDK, then scan again.', { cause });
  }
}
module.exports = { signerCommand };

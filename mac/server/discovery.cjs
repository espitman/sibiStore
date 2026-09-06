const { spawn } = require('node:child_process');

// Let mDNSResponder own interfaces, address records and network changes on macOS.
// The child registration lives exactly as long as this HTTP server.
function advertise({ name, port, serverId, onError = () => {}, spawnProcess = spawn }) {
  let stopped = false;
  const child = spawnProcess('/usr/bin/dns-sd', [
    '-R', name, '_sibistore._tcp', 'local.', String(port), `serverId=${serverId}`, 'api=1',
  ], { stdio: ['ignore', 'pipe', 'pipe'] });
  let diagnostic = '';
  child.stdout.on('data', () => {});
  child.stderr.on('data', chunk => { diagnostic = (diagnostic + chunk).slice(-1000); });
  child.on('error', error => { if (!stopped) onError(`Bonjour: ${error.message}`); });
  const exited = new Promise(resolve => child.once('close', (code, signal) => {
    if (!stopped) onError(`Bonjour registration stopped (${signal || code})${diagnostic ? `: ${diagnostic.trim()}` : ''}`);
    resolve();
  }));
  return {
    async close() {
      if (stopped) return exited;
      stopped = true;
      if (child.exitCode === null && child.signalCode === null) child.kill('SIGTERM');
      await exited;
    },
  };
}

module.exports = { advertise };

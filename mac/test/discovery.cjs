const { spawn }=require('node:child_process');
// Apple's native browser is independent of the server's advertising library.
const child=spawn('/usr/bin/dns-sd',['-B','_sibistore._tcp','local.']);
let output='';
const timer=setTimeout(()=>{child.kill();console.error(output || 'No mDNS service found');process.exitCode=1;},12000);
child.stdout.on('data',chunk=>{output+=chunk; if(output.includes('Sibi Store')){clearTimeout(timer);console.log(output.trim());child.kill();}});
child.on('error',e=>{clearTimeout(timer);console.error(e);process.exitCode=1;});

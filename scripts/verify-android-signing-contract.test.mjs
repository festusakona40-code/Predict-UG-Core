import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,writeFileSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';

// The accepted certificate is the one already pinned by installed customer APKs.
const trusted='66a1431469d7a67450a3d28ce7adfcf11295eb274be305add681613bc1ad4d55';
function check(text){
 const dir=mkdtempSync(join(tmpdir(),'predict-ug-signer-'));
 try{
  const path=join(dir,'SIGNING_CERT.txt');writeFileSync(path,text);
  return spawnSync(process.execPath,['scripts/verify-android-release-contract.mjs','--signing-cert',path],{encoding:'utf8'});
 }finally{rmSync(dir,{recursive:true,force:true});}
}
test('existing customer signing identity is accepted regardless of hex case',()=>{
 const r=check('Signer #1 certificate SHA-256 digest: '+trusted.toUpperCase()+'\n');
 assert.equal(r.status,0,r.stdout+r.stderr);
});
test('a newly generated debug certificate cannot be published as an upgrade',()=>{
 const r=check('Signer #1 certificate SHA-256 digest: '+'0'.repeat(64)+'\n');
 assert.notEqual(r.status,0);assert.match(r.stderr,/signing certificate/);
});
test('missing, malformed or ambiguous signing proof fails closed',()=>{
 for(const value of ['', 'Signer #1 certificate SHA-256 digest: bad\n',
   'Signer #1 certificate SHA-256 digest: '+trusted+'\nSigner #1 certificate SHA-256 digest: '+trusted+'\n']){
  const r=check(value);assert.notEqual(r.status,0);assert.match(r.stderr,/signing certificate/);
 }
});
test('a trusted first signer cannot conceal an additional foreign signer',()=>{
 const r=check('Signer #1 certificate SHA-256 digest: '+trusted+'\nSigner #2 certificate SHA-256 digest: '+'0'.repeat(64)+'\n');
 assert.notEqual(r.status,0);assert.match(r.stderr,/signing certificate/);
});

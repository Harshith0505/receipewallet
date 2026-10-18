import http from 'node:http';
import {hostedRequest} from './api/index.mjs';

if((process.env.AUDIT_BEARER_TOKEN||'').length<32) {
 throw new Error('Configure AUDIT_BEARER_TOKEN in .env.audit.local before starting the audit server.');
}
const server=http.createServer((req,res)=>Promise.resolve(hostedRequest(req,res)).catch(()=>{
 if(!res.headersSent) res.writeHead(500,{'Content-Type':'application/json'});
 res.end(JSON.stringify({error:'Unable to complete this request.'}));
}));
server.requestTimeout=100000;
server.listen(Number(process.env.AUDIT_PORT||4243),'127.0.0.1',()=>{
 console.log(`Token-protected audit backend: http://127.0.0.1:${server.address().port}`);
});

import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const script=fs.readFileSync(new URL('../app/src/main/assets/player_bridge.js',import.meta.url),'utf8');
function fixture({host='e54144713.at.baijiayun.com',provider=true,video=null}={}) {
    const handlers={}; const wrapper={}; const target={};
    const app={isConnected:true,contains:e=>e===wrapper||e===target};
    function BPlayer(){}
    BPlayer.instances={};
    const p=new BPlayer();
    Object.assign(p,{container:app,template:{container:wrapper},isCanplay:true,currentTime:42,duration:120,paused:false,pauseCalls:0,seekCalls:[],pause(){this.paused=true;this.pauseCalls++},seek(time,...args){this.currentTime=time;this.seekCalls.push([time,...args]);return Promise.resolve(time)}});
    p.controller={player:p,showCalls:0,show(){this.showCalls++}};
    if(provider)BPlayer.instances.main=p;
    const document={hidden:false,getElementById:id=>id==='app'?app:null,querySelector:q=>q==='video'?video:null,querySelectorAll:q=>video?[video]:[],addEventListener:(name,fn)=>(handlers[name]??=[]).push(fn)};
    const context={location:{protocol:'https:',hostname:host,port:''},document,BPlayer,Date,Promise,WeakMap};
    context.window=context;vm.createContext(context);vm.runInContext(script,context);
    return {context,bridge:context.__coursePlaybackBridge,p,app,target,document,fire:(name,event={})=>handlers[name]?.forEach(fn=>fn(event))};
}
const disallowed=fixture({host:'evil.test'});assert.equal(disallowed.bridge,undefined);
const f=fixture();assert.equal(f.bridge.position(0),42);
f.bridge.pause();assert.equal(f.p.paused,true);assert.equal(f.p.pauseCalls,1);
f.p.paused=false;f.document.hidden=true;f.fire('visibilitychange');assert.equal(f.p.paused,true);assert.equal(f.bridge.position(0),null);
f.p.paused=false;f.document.hidden=false;f.fire('visibilitychange');assert.equal(f.p.paused,true);
f.fire('touchstart',{touches:[{clientX:10,clientY:20}]});f.fire('touchend',{changedTouches:[{clientX:11,clientY:21}],target:f.target});assert.equal(f.p.controller.showCalls,1);
f.fire('touchstart',{touches:[{clientX:10,clientY:20}]});f.fire('touchend',{changedTouches:[{clientX:80,clientY:21}],target:f.target});assert.equal(f.p.controller.showCalls,1);
const seek=fixture();assert.equal(seek.bridge.position(60),null);await Promise.resolve();assert.equal(seek.bridge.position(60),60);assert.deepEqual(seek.p.seekCalls,[[60,true,false]]);seek.p.currentTime=65;assert.equal(seek.bridge.position(60),65);assert.equal(seek.p.seekCalls.length,1);
const clamp=fixture();clamp.bridge.position(999);await Promise.resolve();assert.equal(clamp.bridge.position(999),119.5);
const metadata=fixture();metadata.p.isCanplay=false;assert.equal(metadata.bridge.position(25),null);assert.equal(metadata.p.seekCalls.length,0);
const wrongRoot=fixture();wrongRoot.p.container={};assert.equal(wrongRoot.bridge.position(0),null);wrongRoot.bridge.pause();assert.equal(wrongRoot.p.pauseCalls,0);
const missing=fixture({provider:false});assert.equal(missing.bridge.position(10),null);
const video={readyState:1,currentTime:0,duration:100,seeking:false,paused:false,pause(){this.paused=true}};
const html=fixture({provider:false,video});assert.equal(html.bridge.position(30),30);video.currentTime=35;assert.equal(html.bridge.position(30),35);html.bridge.pause();assert.equal(video.paused,true);
const noMetadata=fixture({provider:false,video:{readyState:0}});assert.equal(noMetadata.bridge.position(30),null);
console.log('Player bridge passed: trusted host, exact provider instance, pause, background/resume pause, touch show, swipe preservation, position, one-time seek, clamp, metadata wait, unavailable fallback, HTML5 fallback.');

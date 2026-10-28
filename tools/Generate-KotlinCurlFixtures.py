"""Optional offline fixture export from the verified C port. Not part of Android build/runtime."""
from pathlib import Path
import argparse, ctypes as C, gzip, hashlib, json, math, random, struct
ROOT=Path(__file__).resolve().parents[1]
ap=argparse.ArgumentParser();ap.add_argument('--source',type=Path,required=True);a=ap.parse_args()
P=a.source;dest=ROOT/'ioscurl/src/test/resources';dest.mkdir(parents=True,exist_ok=True)
class Values(C.Structure):_fields_=[(n,C.c_double)for n in ('time','radius','angle')]
class Rect(C.Structure):_fields_=[(n,C.c_double)for n in ('x','y','w','h')]
class Config(C.Structure):_fields_=[(n,C.c_double)for n in ('width','height','initial_x','initial_y')]+[(n,C.c_int32)for n in ('spine','direction','requested_type','double_sided')]
class Input(C.Structure):_fields_=[('values',Values)]+[(n,C.c_double)for n in ('width','height','scale','start_angle','end_angle')]+[(n,C.c_int32)for n in ('front_enabled','back_enabled')]+[('colors',(C.c_double*4)*4),('options',C.c_uint32)]+[(n,Rect)for n in ('surface_rect','target_bounds','clip_rect','shadow_bounds')]
class Vertex(C.Structure):_fields_=[('raw',C.c_uint8*64)]
class Batch(C.Structure):_fields_=[(n,C.c_uint32)for n in ('primitive','mode','blend_function','blend_enabled','vertex_count','index_count')]+[('vertices',C.POINTER(Vertex)),('indices',C.POINTER(C.c_uint16)),('clip',Rect)]
class Frame(C.Structure):_fields_=[(n,C.c_uint32)for n in ('width','height','batch_count')]+[('batches',C.POINTER(Batch)),('in_state',C.c_double*13),('out_state',C.c_uint8*0x120),('arena',C.c_void_p)]
# The verified external C reference keeps its research filename, independent of the ioscurl module.
d=C.CDLL(str(P/'pagecurl.dll'))
d.pc_render_input_defaults.argtypes=[C.POINTER(Input)];d.pc_render.argtypes=[C.POINTER(Input),C.POINTER(Frame)];d.pc_render_frame_free.argtypes=[C.POINTER(Frame)]
d.pc_control_update.argtypes=[C.POINTER(Config),C.c_double,C.c_double,C.POINTER(Values)]
report=json.loads((P/'render-c-validation.json').read_text());cases=report['fixtures']
with gzip.open(dest/'mesh-golden.bin.gz','wb')as f:
 def wr(fmt,*v):f.write(struct.pack('>'+fmt,*v))
 wr('i',len(cases))
 for case in cases:
  i=Input();d.pc_render_input_defaults(C.byref(i));i.values=Values(case['time_value'],case['radius'],case['angle']);i.width=case['width'];i.height=case['height'];i.scale=case['scale'];i.front_enabled=case.get('front',True);i.back_enabled=case.get('back',True)
  if 'target_bounds'in case:i.options|=2;i.target_bounds=Rect(*case['target_bounds'])
  for atom,rgba in case.get('colors',{}).items():
   for k,v in enumerate(rgba):i.colors[[0xf7,0xea,0xeb,0x106].index(int(atom))][k]=v
  wr('8d2i',i.width,i.height,i.scale,i.values.time,i.values.radius,i.values.angle,i.start_angle,i.end_angle,i.front_enabled,i.back_enabled)
  wr('16d',*(v for color in i.colors for v in color));wr('i',i.options)
  for r in (i.surface_rect,i.target_bounds,i.clip_rect,i.shadow_bounds):wr('4d',r.x,r.y,r.w,r.h)
  out=Frame();status=d.pc_render(C.byref(i),C.byref(out));assert status==0,(case,status)
  wr('3i',out.width,out.height,out.batch_count)
  for j in range(out.batch_count):
   b=out.batches[j];wr('6i',b.primitive,b.mode,b.blend_function,b.blend_enabled,b.vertex_count,b.index_count)
   raw=C.string_at(b.vertices,b.vertex_count*64)
   # Only shader-consumed attributes: padding can hold scratch/address residues.
   attributes=b''.join(raw[k:k+24]+raw[k+40:k+48]for k in range(0,len(raw),64))
   f.write(hashlib.sha256(attributes).digest())
   wr('i',b.index_count if b.indices else -1)
   if b.indices:wr(str(b.index_count)+'H',*b.indices[:b.index_count])
  d.pc_render_frame_free(C.byref(out))
rng=random.Random(20261027)
with gzip.open(dest/'control-golden.bin.gz','wb')as f:
 controls=[]
 for spine in (1,2,4,8,16,32):
  for direction in (0,1):
   for kind in (0,1):
    for k in range(60):
     w,h=rng.choice([(320.,458.5),(640.,917.),(392.,852.)]);ix=rng.uniform(0,w);iy=rng.uniform(0,h);x=rng.uniform(-w*.1,w*1.1);y=rng.uniform(-h*.1,h*1.1)
     c=Config(w,h,ix,iy,spine,direction,kind,spine in (4,8));out=Values();d.pc_control_update(C.byref(c),x,y,C.byref(out));controls.append((c,x,y,out))
 f.write(struct.pack('>i',len(controls)))
 for c,x,y,v in controls:f.write(struct.pack('>4d4i2d3d',c.width,c.height,c.initial_x,c.initial_y,c.spine,c.direction,c.requested_type,c.double_sided,x,y,v.time,v.radius,v.angle))
with gzip.open(dest/'math-golden.bin.gz','wb')as f:
 names=('pc_apple_sinf','pc_apple_cosf','pc_apple_tanf','pc_apple_acosf','pc_apple_expf');f.write(struct.pack('>i',len(names)*2000))
 for index,name in enumerate(names):
  fn=getattr(d,name);fn.argtypes=[C.c_float];fn.restype=C.c_float
  for k in range(2000):
   x=rng.uniform(-1,1)if index==3 else rng.uniform(-8,8)if index<3 else rng.uniform(-12,4)
   x=C.c_float(x).value;y=fn(x);f.write(struct.pack('>iff',index,x,y))
class Point(C.Structure):_fields_=[('x',C.c_double),('y',C.c_double)]
class Sample(C.Structure):_fields_=[('start',Point),('end',Point),('dt',C.c_double)]
class Pan(C.Structure):_fields_=[(n,C.c_int32)for n in ('state','tracked_pointer','initialized','orientation')]+[(n,C.c_double)for n in ('width','height','sloppiness','last_time')]+[(n,Point)for n in ('first','last','last_raw')]+[(n,Sample)for n in ('sample','previous')]+[(n,C.c_int32)for n in ('can_handle','current_valid','incoming0','incoming1','manual_uncommitted','incoming_unknown')]
class PanResult(C.Structure):_fields_=[(n,C.c_int32)for n in ('state','begin_direction','end_direction','command_begin')]+[(n,Point)for n in ('location','translation','velocity')]+[('suggested_velocity',C.c_double)]
class Tap(C.Structure):_fields_=[(n,C.c_int32)for n in ('state','active_pointer','active_count')]+[(n,C.c_double)for n in ('deadline','sloppiness')]+[(n,Point)for n in ('start','last')]
d.pc_pan_init.argtypes=[C.POINTER(Pan),C.c_double,C.c_double,C.c_int32];d.pc_pan_event.argtypes=[C.POINTER(Pan),C.c_int32,C.c_int32,C.c_double,C.c_double,C.c_double,C.POINTER(PanResult)]
d.pc_tap_init.argtypes=[C.POINTER(Tap),C.c_double];d.pc_tap_event.argtypes=[C.POINTER(Tap),C.c_int32,C.c_int32,C.c_double,C.c_double,C.c_double,C.c_int32]
pan_events=0
with gzip.open(dest/'pan-golden.bin.gz','wb')as f:
 f.write(struct.pack('>i',240))
 for trace in range(240):
  o=trace%2;sloppy=[.5,1.,1.5][trace%3];gates=[int(rng.random()>.1)for _ in range(6)];gates[4]=int(trace%7==0)
  p=Pan();d.pc_pan_init(C.byref(p),320,458.5,o);p.sloppiness=sloppy
  for name,value in zip(('can_handle','current_valid','incoming0','incoming1','manual_uncommitted','incoming_unknown'),gates):setattr(p,name,value)
  f.write(struct.pack('>2did6ii',320.,458.5,o,sloppy,*gates,9))
  x,y=rng.uniform(0,320),rng.uniform(0,458.5);time=0
  for k in range(9):
   time+=rng.choice([.0005,.016,.05,.2]);kind=0 if k==0 else 2 if k==8 else 0 if k==2 and trace%5==0 else 1
   pointer=2 if kind==0 and k!=0 else 1
   if k>0:x+=rng.uniform(-85,85);y+=rng.uniform(-85,85)
   r=PanResult();d.pc_pan_event(C.byref(p),kind,pointer,x,y,time,C.byref(r))
   f.write(struct.pack('>2i3d4i7d',kind,pointer,x,y,time,r.state,r.begin_direction,r.end_direction,r.command_begin,r.location.x,r.location.y,r.translation.x,r.translation.y,r.velocity.x,r.velocity.y,r.suggested_velocity));pan_events+=1
with gzip.open(dest/'tap-golden.bin.gz','wb')as f:
 f.write(struct.pack('>i',120))
 for n in range(120):
  t=Tap();sloppy=[.5,1.,1.5][n%3];d.pc_tap_init(C.byref(t),sloppy)
  events=[(0,1,300.,220.,0.),(1,1,300.+(n%5)*15,220.,.2),(2,1,300.,220.,[.3,1.,1.5,1.6][n%4])]
  f.write(struct.pack('>di',sloppy,len(events)))
  for kind,pointer,x,y,time in events:
   result=d.pc_tap_event(C.byref(t),kind,pointer,x,y,time,n%2)
   f.write(struct.pack('>2i3d2i',kind,pointer,x,y,time,n%2,result))
d.pc_control_timing.argtypes=[C.c_int32,C.c_int32,C.c_int32,C.POINTER(C.c_double)];d.pc_timing_solve.argtypes=[C.c_double,C.POINTER(C.c_double),C.c_double];d.pc_timing_solve.restype=C.c_double
with gzip.open(dest/'timing-golden.bin.gz','wb')as f:
 f.write(struct.pack('>i',4*2*3*4*9))
 for ty in (0,1,3,4):
  for ending in (0,1):
   for prop in range(3):
    points=(C.c_double*4)();d.pc_control_timing(ty,ending,prop,points)
    for duration in (.0001,.25,.4,1.8):
     for progress in (-.1,0.,.001,.1,.5,.9,.999,1.,1.1):
      result=d.pc_timing_solve(progress,points,duration);f.write(struct.pack('>3i3d',ty,ending,prop,duration,progress,result))
class TransitionState(C.Structure):_fields_=[('id',C.c_uint64)]+[(n,C.c_int32)for n in ('type','direction','curl_state','will_complete','finished','active','completion_count','completed_set','double_sided')]+[('begin_time',C.c_double)]
class Decision(C.Structure):_fields_=[(n,C.c_int32)for n in ('accepted','queued','began','scheduled','assertion','type')]+[('delay_ns',C.c_int64),('previous_id',C.c_uint64)]
d.pc_transition_decide.argtypes=[C.POINTER(Config),C.POINTER(TransitionState),C.c_int32,C.c_int32,C.c_int32,C.c_double,C.c_double,C.POINTER(Decision)]
with gzip.open(dest/'transition-golden.bin.gz','wb')as f:
 f.write(struct.pack('>i',960))
 for n in range(960):
  spine=(1,2,4,8,16,32)[n%6];direction=n%2;kind=(0,1)[(n//2)%2];c=Config(320.,458.5,300.,120.,spine,direction,kind,spine in (4,8))
  old=TransitionState();old.id=n+1;old.type=(0,1,3,4)[(n//4)%4];old.direction=(n//16)%2;old.curl_state=(1,2,4,5)[(n//32)%4]
  present=n%7!=0;ac=int(present);pending=int(n%11==0);manual=int(n%13==0);elapsed=(n%5)*.03;delay=.1
  out=Decision();d.pc_transition_decide(C.byref(c),C.byref(old)if present else None,ac,pending,manual,elapsed,delay,C.byref(out))
  f.write(struct.pack('>4i4i3i2d6i2q',spine,direction,kind,int(c.double_sided),int(present),old.type,old.direction,old.curl_state,ac,pending,manual,elapsed,delay,out.accepted,out.queued,out.began,out.scheduled,out.assertion,out.type,out.delay_ns,out.previous_id))
d.pc_bridge_call.argtypes=[C.c_char_p,C.c_char_p];d.pc_bridge_call.restype=C.c_char_p
def src(s):return int(s[-2])+(2 if s.startswith('incoming')else 0)
with gzip.open(dest/'layout-golden.bin.gz','wb')as f:
 f.write(struct.pack('>i',20))
 for spine in (1,2,4,8,16,32):
  for direction in (0,1):
   for ds in ((1,)if spine in (4,8)else (0,1)):
    config=dict(width=320,height=458.5,spine=spine,direction=direction,requestedType=0,doubleSided=bool(ds),initial=[300,120])
    data=json.loads(d.pc_bridge_call(b'/api/metadata',json.dumps({'config':config}).encode()))['layout']
    f.write(struct.pack('>3ii',spine,direction,ds,len(data['passes'])))
    for p in data['passes']:
     f.write(struct.pack('>i4d3i',src(p['source']),*p['pageFrame'],int(p['filter']['inputFrontEnabled']),int(p['filter']['inputBackEnabled']),int(p['contentSublayerTransform']is not None)))
    for pages in (data['staticUnderlayBottomToTop'],data['staticSpreads']['outgoing'],data['staticSpreads']['incoming']):
     f.write(struct.pack('>i',len(pages)))
     for p in pages:f.write(struct.pack('>i4d',src(p['source']),*p['frame']))
(dest/'provenance.json').write_text(json.dumps({'source':'Verified C11 port of iOS12.5.7 16H81 offline recovery','C_sha256':hashlib.sha256((P/'pagecurl.dll').read_bytes()).hexdigest(),'meshFrames':len(cases),'controlUpdates':len(controls),'mathSamples':10000,'panEvents':pan_events,'tapEvents':360,'timingSamples':864,'layouts':20,'transitionDecisions':960,'files':{p.name:hashlib.sha256(p.read_bytes()).hexdigest()for p in dest.glob('*.gz')}},indent=2)+'\n')
print((dest/'provenance.json').read_text())

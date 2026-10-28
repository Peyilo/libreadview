"""Build-time C11-to-Kotlin transcription of the verified curl mesh kernel.

Usage: python tools/Port-CurlKernel.py --source <research>/cport
Only ordinary Kotlin expressions and managed arrays ship in the application.
No C compiler, firmware, ARM decoder, native library or generator is used at runtime.
Basic blocks retain source labels so numerical parity failures can be traced.
Requires pycparser at regeneration time only. Generated files are checked in.
"""
import argparse, hashlib, json, re
from pathlib import Path
from pycparser import c_parser, c_ast as A
from CurlKernelReadability import refine_kernel, normalize_kernel_source

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'ioscurl/src/main/kotlin/org/peyilo/ioscurl/mesh/kernel'
parser = argparse.ArgumentParser()
parser.add_argument('--source', type=Path, required=True)
args = parser.parse_args()
source = (args.source / 'pc_render_body.inc').read_text()
source = re.sub(r'/\*.*?\*/', '', source, flags=re.S)
source = re.sub(r'UINT64_C\(([^)]+)\)', r'\1ULL', source)
prefix = '''typedef unsigned long long uint64_t; typedef unsigned int uint32_t;
typedef unsigned short uint16_t; typedef unsigned char uint8_t;
typedef long long int64_t; typedef int int32_t; typedef unsigned long size_t;
typedef unsigned long uintptr_t;
typedef union {double d[2];float f[4];uint64_t u64[2];uint32_t u32[4];uint16_t h[8];} PCVec;
void kernel(){'''
ast = c_parser.CParser().parse(prefix + source + '}').ext[-1].body

def casttype(node):
    if isinstance(node, (A.Typename, A.TypeDecl)): return casttype(node.type)
    if isinstance(node, A.PtrDecl): return 'L'
    if isinstance(node, A.IdentifierType):
        return {'float':'F','double':'D','PCVec':'V'}.get(' '.join(node.names), 'L')
    raise ValueError(type(node))

class Emit:
    def __init__(self):
        self.types = {**{'x'+str(i):'L' for i in range(31)}, 'sp':'L',
                      **{'v'+str(i):'V' for i in range(32)}, **dict.fromkeys('NZCV','B'),
                      **dict.fromkeys(['clipped','k'],'L')}
    def typ(self,n):
        if isinstance(n,A.ID): return self.types.get(n.name,'L')
        if isinstance(n,A.Constant): return ('F' if n.value.lower().endswith('f') else 'D') if n.type in ('float','double','long double') else 'L'
        if isinstance(n,A.Cast): return casttype(n.to_type)
        if isinstance(n,A.ArrayRef):
            if isinstance(n.name,A.StructRef): return {'d':'D','f':'F','h':'L','u32':'L','u64':'L'}[n.name.field.name]
            return 'D'
        if isinstance(n,A.StructRef): return 'L'
        if isinstance(n,A.UnaryOp): return 'B' if n.op=='!' else self.typ(n.expr)
        if isinstance(n,A.BinaryOp):
            if n.op in ('<','>','<=','>=','==','!=','&&','||'): return 'B'
            a,b=self.typ(n.left),self.typ(n.right)
            return 'D' if 'D' in (a,b) else 'F' if 'F' in (a,b) else 'L'
        if isinstance(n,A.TernaryOp): return self.typ(n.iftrue)
        if isinstance(n,A.FuncCall):
            fn=n.name.name
            if fn in ('fma','fabs','sqrt','floor','ceil','fmax','fmin','pc_ld_d','pc_get_scale'): return 'D'
            if fn in ('fmaf','fabsf','sqrtf','floorf','ceilf','fmaxf','fminf','pc_ld_f','pc_half_float','pc_displace','pc_apple_expf','pc_apple_acosf'): return 'F'
            return 'L'
        if isinstance(n,A.Assignment): return self.typ(n.lvalue)
        raise ValueError(('type',type(n),n))
    def coerce(self,s,t,w):
        if not w or t==w:return s
        if w=='B': return f'({s} != '+('0.0' if t=='D' else '0f' if t=='F' else '0L')+')'
        if t=='B': return f'(if ({s}) '+('1.0 else 0.0)' if w=='D' else '1f else 0f)' if w=='F' else '1L else 0L)')
        return f'({s}).'+{'D':'toDouble()','F':'toFloat()','L':'toLong()'}[w]
    def expr(self,n,w=None):
        t=self.typ(n)
        if isinstance(n,A.ID):
            s={'INFINITY':'Double.POSITIVE_INFINITY','NAN':'Double.NaN',
               'pc_fade_weights':'FADE_WEIGHTS','pc_fade_steps':'FADE_STEPS',
               'pc_clamp_steps':'CLAMP_STEPS','pc_clamp_steps2':'CLAMP_STEPS2'}.get(n.name,n.name)
        elif isinstance(n,A.Constant):
            v=n.value
            if t in ('D','F'):
                v=re.sub(r'[fFlL]$','',v)
                a=float.fromhex(v) if 'p' in v.lower() else float(v)
                s=repr(a)
                if t=='F':s+='f'
            else:
                v=re.sub(r'[uUlL]+$','',v);a=int(v,0) if v!='0' else 0
                if a>=2**63:a-=2**64
                s=str(a)+'L'
        elif isinstance(n,A.Cast):
            s=self.expr(n.expr,t)
            c=' '.join(n.to_type.type.type.names) if isinstance(n.to_type.type,A.TypeDecl) else ''
            if c=='uint32_t':s=f'({s} and 0xffffffffL)'
            elif c=='int32_t':s=f'({s}).toInt().toLong()'
        elif isinstance(n,A.ArrayRef):
            idx=self.expr(n.subscript,'L')+'.toInt()'
            if isinstance(n.name,A.StructRef):s=f'{self.expr(n.name.name)}.{n.name.field.name}({idx})'
            else:s=f'{self.expr(n.name)}[{idx}]'
        elif isinstance(n,A.UnaryOp):
            if n.op=='&':s='0L' if isinstance(n.expr,A.ID) and n.expr.name in 'NZCV' else self.expr(n.expr)
            elif n.op=='!':s='!('+self.expr(n.expr,'B')+')'
            elif n.op=='~':s='('+self.expr(n.expr,'L')+').inv()'
            elif n.op in ('-','+'):s=n.op+'('+self.expr(n.expr)+')'
            else: raise ValueError(('unary',n.op))
        elif isinstance(n,A.BinaryOp):
            op=n.op
            if op in ('&&','||'):a,b=self.expr(n.left,'B'),self.expr(n.right,'B')
            elif op in ('<','>','<=','>=','==','!='):
                lt,rt=self.typ(n.left),self.typ(n.right)
                target='B' if lt==rt=='B' else 'D' if 'D' in (lt,rt) else 'F' if 'F' in (lt,rt) else 'L'
                a,b=self.expr(n.left,target),self.expr(n.right,target)
            elif op in ('<<','>>'):
                a,b=self.expr(n.left,'L'),self.expr(n.right,'L')+'.toInt()'
                # C signed right shifts occur only after an explicit int cast.
                op='shr' if op=='>>' and isinstance(n.left,A.Cast) else 'ushr' if op=='>>' else 'shl'
            else:a,b=self.expr(n.left,t),self.expr(n.right,t)
            op={'&':'and','|':'or','^':'xor'}.get(op,op)
            s=f'({a} {op} {b})'
        elif isinstance(n,A.TernaryOp):s=f'(if ({self.expr(n.cond,"B")}) {self.expr(n.iftrue,t)} else {self.expr(n.iffalse,t)})'
        elif isinstance(n,A.FuncCall):
            fn=n.name.name;params=n.args.exprs if n.args else []
            names={'fma':'fma','fmaf':'fma','fabs':'abs','fabsf':'abs','sqrt':'sqrt','sqrtf':'sqrtFloat',
                   'floor':'floor','ceil':'ceil','floorf':'floor','ceilf':'ceil','fmax':'max','fmin':'min','fmaxf':'max','fminf':'min',
                   'pc_half_float':'halfFloat','pc_float_half':'floatHalf',
                   'pc_apple_acosf':'AppleMath.acos','pc_apple_expf':'AppleMath.exp'}
            if fn in ('pc_fcmp','pc_subflags','pc_addflags'):params=params[:2]+(params[2:3] if fn!='pc_fcmp' else [])
            if fn=='pc_next_clip':params=params[1:2]
            if fn=='pc_capture_draw':params=params[1:]
            fname=names.get(fn,fn.removeprefix('pc_'))
            if fn in ('fma','fmaf','fabs','fabsf','sqrt','sqrtf','floor','ceil','floorf','ceilf','fmax','fmin','fmaxf','fminf'):
                s=fname+'('+', '.join(self.expr(p,t) for p in params)+')'
            else:
                sig={'pc_st_d':'LD','pc_st_f':'LF','pc_st_u8':'LL','pc_st_u16':'LL','pc_st_u32':'LL','pc_st_u64':'LL',
                     'pc_fcmp':'DD','pc_subflags':'LLL','pc_addflags':'LLL','pc_float_half':'F','pc_half_float':'L',
                     'pc_displace':'DDDLDD','pc_line_clip':'LLDDDDDDDD','pc_strip_emit':'LLLLLDDDDDDDDL',
                     'pc_apple_acosf':'F','pc_apple_expf':'F','pc_sincos':'DL'}.get(fn)
                s=fname+'('+', '.join(self.expr(p,sig[i] if sig else None) for i,p in enumerate(params))+')'
        else:raise ValueError(('expr',type(n),n))
        return self.coerce(s,t,w)
    def stmt(self,n):
        if isinstance(n,A.EmptyStatement):return []
        if isinstance(n,A.Assignment):
            if isinstance(n.rvalue,A.Assignment):
                return self.stmt(n.rvalue)+self.stmt(A.Assignment(n.op,n.lvalue,n.rvalue.lvalue))
            t=self.typ(n.lvalue);v=self.expr(n.rvalue,t)
            if isinstance(n.lvalue,A.ArrayRef) and isinstance(n.lvalue.name,A.StructRef):
                left=n.lvalue;return [f'{self.expr(left.name.name)}.{left.name.field.name}({self.expr(left.subscript,"L")}.toInt(), {v})']
            if t=='V':return [f'{self.expr(n.lvalue)}.set({v})']
            return [f'{self.expr(n.lvalue)} {n.op} {v}']
        if isinstance(n,A.Decl):
            t=casttype(n.type);self.types[n.name]=t
            v=self.expr(n.init,t) if n.init else {'D':'0.0','F':'0f','L':'0L','V':'KernelVector()'}[t]
            if t=='V':v+='.copy()'
            return [f'var {n.name} = {v}']
        if isinstance(n,A.Compound):
            return ['run {']+['    '+l for x in n.block_items or [] for l in self.stmt(x)]+['}']
        if isinstance(n,A.For):
            # Only fixed SIMD lane loops exist in the transcription.
            decl=n.init.decls[0];self.types[decl.name]='L'
            return [f'for ({decl.name} in 0L until {self.expr(n.cond.right,"L")}) {{']+['    '+l for l in self.stmt(n.stmt)]+['}']
        if isinstance(n,A.If):
            out=['if ('+self.expr(n.cond,'B')+') {']+['    '+l for l in self.stmt(n.iftrue)]+['}']
            if n.iffalse:out[-1]+=' else {';out+=['    '+l for l in self.stmt(n.iffalse)]+['}']
            return out
        if isinstance(n,A.FuncCall):
            fn=n.name.name;p=n.args.exprs
            if fn in ('memcpy','memmove'):
                # Vector copies preserve union bits. Scratch copies may overlap.
                def vec(n):return isinstance(n,A.UnaryOp) and n.op=='&'
                if vec(p[0]):return [f'{self.expr(p[0].expr)}.load(memory, {self.expr(p[1])})']
                if vec(p[1]):return [f'{self.expr(p[1].expr)}.store(memory, {self.expr(p[0])})']
                return ['memory.copy('+', '.join(self.expr(x) for x in p)+')']
            return [self.expr(n)]
        if isinstance(n,A.Return):return ['return -'+str(int(n.expr.name.removeprefix('PC_RENDER_') in ('INVALID','CAPACITY'))+1)]
        raise ValueError(('stmt',type(n),n))

e=Emit();blocks=[{'name':'entry','body':[],'term':None}];labels={};current=blocks[0]
def new(name=None):
    global current
    current={'name':name or 'fallthrough'+str(len(blocks)),'body':[],'term':None};blocks.append(current)
for n in ast.block_items:
    if isinstance(n,A.Label):
        if current['body'] or current['term']:new(n.name)
        current['name']=n.name;labels[n.name]=current
        n=n.stmt
    if isinstance(n,A.Goto):current['term']=('go',n.name);new()
    elif isinstance(n,A.If) and isinstance(n.iftrue,A.Goto):current['term']=('if',n.cond,n.iftrue.name);new()
    elif isinstance(n,A.Return):current['term']=('return',n);new()
    else:current['body'].append(n)
if not blocks[-1]['body'] and not blocks[-1]['term']:blocks.pop()
for i,b in enumerate(blocks):b['id']=i
DEST.mkdir(parents=True,exist_ok=True)
header='''// Generated from the verified C11 curl geometry; regenerate with tools/Port-CurlKernel.py.
// Direct Kotlin arithmetic and managed scratch storage. No machine instructions are executed.
@file:Suppress("UNUSED_VARIABLE", "UNUSED_VALUE", "NAME_SHADOWING", "RedundantConversion")
package org.peyilo.ioscurl.mesh.kernel
import kotlin.math.*
import org.peyilo.ioscurl.math.AppleMath
import org.peyilo.ioscurl.math.BinaryMath.fma
'''
groups=[]
for offset in range(0,len(blocks),48):
    lines=[header]
    for b in blocks[offset:offset+48]:
        i=b['id'];lines += [f'// C source block: {b["name"]}',f'internal fun CurlMeshKernel.block{i}(): Int {{']
        code=[l for n in b['body'] for l in e.stmt(n)]
        term=b['term'];nxt=i+1
        if term and term[0]=='go':code+=[f'return {labels[term[1]]["id"]}']
        elif term and term[0]=='if':code+=[f'return if ({e.expr(term[1],"B")}) {labels[term[2]]["id"]} else {nxt}']
        elif term:code+=e.stmt(term[1])
        else:code+=[f'return {nxt}']
        lines+=['    '+l for l in code]+['}','']
    filename=f'KernelBlocks{offset//48+1}.kt';(DEST/filename).write_text('\n'.join(lines),encoding='utf-8');groups.append(filename)
dispatch='\n'.join(f'                {b["id"]} -> block{b["id"]}()' for b in blocks)
(DEST/'KernelFlow.kt').write_text(header+'''\n/** Fixed algorithm control flow, not an instruction/opcode interpreter. */
internal fun CurlMeshKernel.execute() {
    var block = 0
    var remaining = 100000
    while (block >= 0) {
        check(--remaining > 0) { "Curl geometry iteration limit" }
        block = when (block) {
'''+dispatch+'''
            else -> error("Unknown geometry block $block")
        }
    }
    check(block == -1) { "Invalid curl geometry" }
}
''',encoding='utf-8')
groups = refine_kernel(DEST)
for name in groups:
    path = DEST / name
    path.write_text(normalize_kernel_source(path.read_text(encoding='utf-8')), encoding='utf-8')
manifest={'source':'iOS12.5.7 16H81 offline recovery -> verified C11 -> managed Kotlin',
          'sourceSHA256':hashlib.sha256((args.source/'pc_render_body.inc').read_bytes()).hexdigest(),
          'blocks':len(blocks),'generatedFiles':groups,
          'naming':'Reaching-definition live ranges and semantic geometry stages; shared bitwise scratch storage'}
(DEST/'provenance.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(json.dumps(manifest))
print('Run ./gradlew.bat :app:ioscurl:formatKotlin after regeneration.')

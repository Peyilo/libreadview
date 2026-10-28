"""Give the recovered kernel value live ranges and control-flow steps readable names.

This is a build-time source transformation, not a runtime interpreter. Value views
share the original scratch slots, preserving even the unused vector lanes. A slot
is deliberately NOT given one global semantic name: it has different jobs in
different live ranges. Reaching definitions identify those independent jobs.
"""
from collections import defaultdict
from pathlib import Path
import re
import struct

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'ioscurl/src/main/kotlin/org/peyilo/ioscurl/mesh/kernel'

# Names describe the operation, not the original machine address / basic-block ID.
STEP_GROUPS = [
    (0, 'KernelProjection.kt', 'projection', '''
prepareMaterialsAndBounds advanceProjectedCorner projectPageCorner prepareBreakpointSort
advanceBreakpointSort beginBreakpointInsertion compareBreakpointInsertion shiftBreakpointForInsertion
finishBreakpointInsertion selectBreakpointInsertion storeSortedBreakpoint finishBreakpointSort
useTwoCurveEndpoints deriveAdaptiveSampleRange skipEmptySampleRange prepareAdaptiveSamples
evaluateAdaptiveSample preserveNegativeSample appendPositiveSample advanceAdaptiveSample
prepareSampleMerge selectSampleMergeSource takeAdaptiveSample inspectCornerSample compareCornerSample
takeCornerSample advanceAdaptiveSource takeProjectedCorner retainAdaptiveEndpoint advanceCornerSource
appendMergedSample drainAdaptiveSource prepareSampleCompaction compactSampleGap inspectSampleGap
prepareVisibleSampleRange findVisibleRangeStart prepareVisibleRangeEnd inspectVisibleRangeEnd
scanVisibleRangeEnd finishVisibleSampleRange'''),
    (41, 'KernelSurfaceSampling.kt', 'surface', '''
prepareSurfaceStrips sampleSurfaceCrossSection selectSurfaceLighting useLinearSurfaceLighting
useUnlitSurfaceCrossSection useCurvedSurfaceLighting evaluateSurfaceLighting projectSurfaceCrossSection
checkSurfaceShadowRange recordSurfaceShadowSample appendSurfaceStrip prepareBackStripIndices
prepareBackIndexReversal beginBackIndexReversal reverseBackStripIndices finishBackIndexReversal
prepareBackStripColor copyBackStripColor'''),
    (59, 'KernelShadowTopology.kt', 'shadow', '''
prepareShadowBreakpoints inspectShadowStart beginShadowStartSamples inspectShadowStartSample
appendShadowStartSample finishShadowStartSamples skipDisabledShadow skipEmptyShadow
finishShadowStartRange inspectShadowEnd beginShadowEndSamples inspectShadowEndSample
appendShadowEndSample finishShadowEndSamples retainShadowEnd clampShadowEnd
prepareShadowEdgeIntersections useEmptyShadowEdges appendFirstShadowEdge inspectSecondShadowEdge
selectShadowMergeOrientation inspectShadowMergeAvailability skipShadowEdgeMerge
appendSecondShadowEdge compareShadowEdges swapShadowEdges selectTwoShadowEdges selectOneShadowEdge
finishShadowMergeOrientation prepareShadowEdgeMerge inspectShadowMergeSource takeShadowEdge advanceShadowEdge inspectShadowMergeOrder
compareShadowMergeValues retainShadowMergeValue takeShadowBoundary retainShadowBoundary
advanceShadowBoundary appendShadowMergedValue drainShadowMerge inspectShadowSegmentCount
prepareShadowSegmentIndices beginShadowSegmentSearch inspectShadowSegment advanceShadowSegmentSearch
useUnmatchedShadowSegment matchShadowSegment inspectShadowSegmentEnd inspectShadowSegmentOrientation
selectShadowSegmentOrientation chooseShadowSegmentVertex inspectShadowSegmentCase advanceShadowSegmentCase
prepareShadowSegment prepareNextShadowSegment inspectShadowSegmentClass inspectShadowSegmentBoundary
appendShadowSegmentIndex finishShadowSegmentIndex inspectShadowEndpointClass selectShadowEndpointVertex
storeShadowEndpointVertex inspectShadowTerminalSegment advanceShadowTerminalSegment
finishShadowTopology'''),
    (125, 'KernelPenumbra.kt', 'penumbra', '''
prepareShadowCrossSections sampleShadowCrossSection preparePenumbraStrips beginPenumbraSegment
findPenumbraSegment advancePenumbraSearch useUnmatchedPenumbraSegment matchPenumbraSegment
inspectPenumbraSegmentEnd inspectPenumbraSegmentCase selectPenumbraSegmentCase
calculatePenumbraDirection validatePenumbraDirection validatePenumbraDirectionMagnitude
useFallbackPenumbraDirection selectPenumbraEndpoint calculateAdjacentPenumbraDirection
validateAdjacentPenumbraDirection validateAdjacentPenumbraMagnitude useAdjacentPenumbraDirection
useTerminalPenumbraDirection beginLowerPenumbraFade sampleLowerPenumbraFade copyLowerFadeJoin
appendLowerPenumbraFade beginUpperPenumbraFade sampleUpperPenumbraFade copyUpperFadeJoin
appendUpperPenumbraFade advancePenumbraSegment finishPenumbraSegments prepareEmptyPenumbra
inspectPenumbraAxis inspectPenumbraAxisMagnitude preparePenumbraEndpointLookup
findPenumbraEndpoint inspectPenumbraTerminalEndpoint inspectPenumbraEndpointKind
useInteriorPenumbraEndpoint useFirstPenumbraEndpoint useLastPenumbraEndpoint readPenumbraEndpoint
advancePenumbraEndpointLookup finishPenumbraEndpointLookup preparePenumbraEndCap
advancePenumbraEndCap appendPenumbraEndCap finishPenumbraEndCap preparePenumbraCorners
preparePenumbraCornerRotation beginPenumbraCorner appendPenumbraCornerVertex
advancePenumbraCornerRing advancePenumbraCorner finishPenumbraCorners'''),
    (180, 'KernelFaceSampling.kt', 'face', '''
inspectFrontFace inspectFrontSampleRange skipEmptyFrontSamples skipDisabledFrontFace
prepareFrontSamples sampleFrontRange inspectFirstFrontEdge useEmptyFrontEdges appendFirstFrontEdge
inspectSecondFrontEdge selectFrontMergeOrientation inspectFrontMergeAvailability skipFrontEdgeMerge
appendSecondFrontEdge compareFrontEdges swapFrontEdges selectTwoFrontEdges selectOneFrontEdge
finishFrontEdgeSelection prepareFrontEdgeMerge inspectFrontMergeSource takeFrontEdge
advanceFrontEdge inspectFrontMergeOrder compareFrontMergeValues retainFrontMergeValue
takeFrontBoundary retainFrontBoundary advanceFrontBoundary appendFrontMergedValue
drainFrontMerge prepareFrontCrossSections beginFrontCrossSections appendFrontCrossSection
finishFrontCrossSections'''),
    (215, 'KernelDrawing.kt', 'draw', '''
prepareDrawState inspectOutputTransform useAffineOutputScale measureOutputScale applyOutputScale
beginClipIteration prepareDrawBuffers inspectSurfaceDraw drawSurfaceStrips inspectBackDraw
inspectBackShadowDraw prepareShadowDrawLoop inspectShadowDrawSegment prepareShadowDrawSegment
drawShadowSegment inspectShadowEndCap drawShadowEndCap inspectShadowCorner drawShadowCorner
advanceShadowDrawSegment drawBackFace drawBackFaceFringe drawBackFaceEdges
advanceClipIteration finishDrawClips restoreDrawBlendFlags resetDrawState completeGeometry rejectGeometry'''),
]

def step_table():
    result = {}
    for first, filename, phase, names in STEP_GROUPS:
        for offset, name in enumerate(names.split()):
            result[first + offset] = (filename, phase, name)
    assert set(result) == set(range(244)), sorted(set(range(244)) ^ set(result))
    assert len({v[2] for v in result.values()}) == 244
    return result

def float_literal(value):
    """Shortest decimal with exactly the same IEEE binary32 bits."""
    bits = struct.pack('>f', value)
    value = struct.unpack('>f', bits)[0]
    for digits in range(1, 10):
        candidate = format(value, f'.{digits}g')
        if struct.pack('>f', float(candidate)) == bits:
            candidate = re.sub(r'e([+-]?)0+(\d+)', r'e\1\2', candidate)
            return candidate + 'f'
    raise AssertionError(value)

LITERAL = re.compile(r'(?<![\w.])(?:\d+\.\d+|\.\d+|\d+[eE][-+]?\d+)(?:[eE][-+]?\d+)?[fF]?')

def readable_literals(text):
    def replace(match):
        old = match.group()
        value = float(old.rstrip('fF'))
        if old[-1:] in ('f', 'F'):
            return float_literal(value)
        # A widened float must remain widened: changing it to e.g. 0.4 changes
        # binary64 comparisons and FMA results. State that precision boundary.
        try:
            widened = struct.unpack('>f', struct.pack('>f', value))[0]
        except OverflowError:
            return old
        if len(old) > 13 and widened == value and not value.is_integer():
            return float_literal(value) + '.toDouble()'
        return '0' + old if old.startswith('.') else old
    return LITERAL.sub(replace, text)

def matching(text, start):
    depth = 1
    for pos in range(start + 1, len(text)):
        if text[pos] == '(':
            depth += 1
        elif text[pos] == ')':
            depth -= 1
            if depth == 0:
                return pos
    raise ValueError(text[start:start + 100])

def arguments(text):
    start = 0
    depth = 0
    result = []
    for pos, char in enumerate(text):
        depth += (char == '(') - (char == ')')
        if char == ',' and depth == 0:
            result.append((start, pos))
            start = pos + 1
    result.append((start, len(text)))
    return result

REG = re.compile(r'\b(?:x(?:[0-9]|[12][0-9]|30)|v(?:[0-9]|[12][0-9]|3[01]))\b')

def simplify_color_operations(body):
    unpack = r'run\s*\{\s*var t = (v\d+)\.copy\(\)\s*for \(k in 0L until 4L\)\s*\{\s*(v\d+)\.f\(k\.toInt\(\), halfFloat\(t\.h\(k\.toInt\(\)\)\)\)\s*}\s*}'
    pack = r'run\s*\{\s*var t = (v\d+)\.copy\(\)\s*for \(k in 0L until 4L\)\s*\{\s*(v\d+)\.h\(k\.toInt\(\), floatHalf\(t\.f\(k\.toInt\(\)\)\)\)\s*}\s*}'
    multiply = r'run\s*\{\s*var ta = (v\d+)\.copy\(\)\s*var tb = (v\d+)\.copy\(\)\s*var tc = v\d+\.copy\(\)\s*for \(k in 0L until 4L\)\s*\{\s*(v\d+)\.f\(k\.toInt\(\), \(ta\.f\(k\.toInt\(\)\) \* tb\.f\(k\.toInt\(\)\)\)\)\s*}\s*}'
    body = re.sub(unpack, r'\2.unpackHalfColorFrom(\1)', body)
    body = re.sub(pack, r'\2.packHalfColorFrom(\1)', body)
    return re.sub(multiply, r'\3.multiplyColorsFrom(\1, \2)', body)

def analyze(body, number):
    definitions = []
    receivers = set()
    for match in re.finditer(r'\b(x\d+)\s*=\s*(?!=)', body):
        end = body.find('\n', match.end())
        # Scalar expressions can span lines; the definition becomes available
        # only after its entire RHS has been evaluated.
        balance = body[match.end():end].count('(') - body[match.end():end].count(')')
        while balance > 0:
            next_end = body.find('\n', end + 1)
            if next_end < 0: next_end = len(body)
            balance += body[end:next_end].count('(') - body[end:next_end].count(')')
            end = next_end
        definitions.append((end, match.start(), match[1], body[match.end():end].strip()))
        receivers.add(match.start())
    for match in re.finditer(r'\b(v\d+)\.(d|f|h|u32|u64|set|load|clear|unpackHalfColorFrom|packHalfColorFrom|multiplyColorsFrom)\(', body):
        end = matching(body, match.end() - 1)
        args = arguments(body[match.end():end])
        if match[2] in ('set', 'load', 'clear', 'unpackHalfColorFrom', 'packHalfColorFrom', 'multiplyColorsFrom') or len(args) == 2:
            definitions.append((end + 1, match.start(), match[1], body[match.start():end + 1]))
            receivers.add(match.start())
    definitions.sort()
    events = [(p, 'read', (p, m.group())) for m in REG.finditer(body)
              if (p := m.start()) not in receivers]
    events += [(end, 'write', (pos, reg, expr, (number, pos, reg))) for end, pos, reg, expr in definitions]
    return sorted(events, key=lambda x: (x[0], x[1]))

CALL_ROLES = {
    'capture_draw': ['renderContextAddress', 'primitiveKind', 'drawIndexCount', 'indexBufferAddress',
                     'vertexBufferAddress', 'vertexBufferOffset', 'drawVertexCount', 'attributePlanes'],
    'strip_emit': ['centerStripAddress', 'lowerStripAddress', 'upperStripAddress', 'stripIndex',
                   'packedColorAddress'],
    'line_clip': ['clipStartAddress', 'clipEndAddress'],
    'apply_point': ['pointTransformAddress', 'transformedPointAddress'],
    'get_scale': ['outputTransformAddress', 'horizontalScaleAddress', 'verticalScaleAddress'],
    'next_clip': ['clipRectangleAddress'],
    'displace': [None, None, None, 'sineOutputAddress'],
    'sincos': [None, 'rotationMatrixAddress'],
}

def refine_kernel(directory=DEST):
    steps = step_table()
    blocks = {}
    originals = sorted(directory.glob('KernelBlocks*.kt'))
    for path in originals:
        text = path.read_text(encoding='utf-8')
        matches = list(re.finditer(r'// C source block: ([^\n]+)\ninternal fun CurlMeshKernel\.block(\d+)\(\): Int \{', text))
        for index, match in enumerate(matches):
            end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
            body = text[match.end():end].rstrip()
            assert body.endswith('}')
            blocks[int(match[2])] = (match[1], simplify_color_operations(body[:-1]))
    assert len(blocks) == 244, len(blocks)
    registers = ['x' + str(i) for i in range(31)] + ['v' + str(i) for i in range(32)]
    initial = {r: (-1, 0, r) for r in registers}
    events = {n: analyze(b, n) for n, (_, b) in blocks.items()}
    successors = {}
    for n, (_, body) in blocks.items():
        terminal = re.findall(r'return[^\n]*', body)[-1]
        successors[n] = [int(x) for x in re.findall(r'(?<![-\w])\d+(?![\w])', terminal)
                         if int(x) in blocks] if 'if' not in terminal else [int(x) for x in re.findall(r'(?:\)|else)\s*(\d+)\b', terminal)]
    predecessors = defaultdict(list)
    for n, targets in successors.items():
        for target in targets: predecessors[target].append(n)
    incoming = {n: {r: set() for r in registers} for n in blocks}
    outgoing = {n: {r: set() for r in registers} for n in blocks}
    changed = True
    while changed:
        changed = False
        for n in sorted(blocks):
            current = {r: set().union(*(outgoing[p][r] for p in predecessors[n])) for r in registers}
            if n == 0:
                for r in registers: current[r].add(initial[r])
            incoming[n] = {r: set(v) for r, v in current.items()}
            for _, kind, event in events[n]:
                if kind == 'write': current[event[1]] = {event[3]}
            if current != outgoing[n]: outgoing[n] = current; changed = True
    parent = {}
    def find(x):
        parent.setdefault(x, x)
        if parent[x] != x: parent[x] = find(parent[x])
        return parent[x]
    def union(items):
        items = sorted(items)
        if not items: return
        root = find(items[0])
        for item in items[1:]: parent[find(item)] = root
    for n in blocks:
        current = {r: set(v) for r, v in incoming[n].items()}
        for _, kind, event in events[n]:
            if kind == 'read': union(current[event[1]])
            else: current[event[1]] = {event[3]}; find(event[3])
    definitions = defaultdict(list)
    uses = {}
    for n in blocks:
        current = {r: set(v) for r, v in incoming[n].items()}
        for _, kind, event in events[n]:
            if kind == 'write':
                definitions[find(event[3])].append((n, event[0], event[1], event[2]))
                current[event[1]] = {event[3]}
            elif current[event[1]]:
                uses[n, event[0]] = find(next(iter(current[event[1]])))
    suggestions = defaultdict(list)
    for reg, role in zip(registers[:5], ['contextArgument', 'controlInputAddress', 'materialInputAddress',
                                      'sourceSurfaceAddress', 'outputTransformArgument']):
        suggestions[find(initial[reg])].append(role)
    for n, (_, body) in blocks.items():
        for function, roles in CALL_ROLES.items():
            for match in re.finditer(r'\b' + function + r'\(', body):
                end = matching(body, match.end() - 1)
                for (start, stop), role in zip(arguments(body[match.end():end]), roles):
                    if not role: continue
                    arg = body[match.end() + start:match.end() + stop]
                    token = re.fullmatch(r'\s*(x\d+)\s*', arg)
                    if token:
                        key = uses.get((n, match.end() + start + token.start(1)))
                        if key: suggestions[key].append(role)
    names = {}
    occupied = set()
    views = defaultdict(set)
    for (number, pos), key in uses.items():
        _, body = blocks[number]
        token = REG.match(body, pos)
        member = re.match(r'\.(d|f|h|u32|u64)\(', body[token.end():])
        if member:
            start = token.end() + member.end()
            stop = matching(body, start - 1)
            arg = arguments(body[start:stop])[0]
            views[key].add((member[1], body[start + arg[0]:start + arg[1]].strip()))
        else: views[key].add(None)
    for key, defs in definitions.items():
        for _, _, reg, expr in defs:
            if reg.startswith('v'):
                member = re.match(r'v\d+\.(d|f|h|u32|u64)\(', expr)
                if member:
                    stop = matching(expr, member.end() - 1)
                    arg = arguments(expr[member.end():stop])[0]
                    views[key].add((member[1], expr[member.end() + arg[0]:member.end() + arg[1]].strip()))
                else: views[key].add(None)
    scalar_views = {}
    for key, kinds in views.items():
        if key[2].startswith('v') and len(kinds) == 1 and None not in kinds:
            method, lane = next(iter(kinds))
            if lane in ('0', '0L.toInt()'): scalar_views[key] = method
    # Consistent semantic identities of the inputs and stable context aliases.
    special = {(0, 'x19'): 'renderContextAddress', (0, 'x20'): 'materialAddress', (0, 'x21'): 'controlAddress'}
    field_roles = {0: 'curlProgress', 8: 'curlAngle', 16: 'curlRadius', 24: 'minimumCurlAngle', 32: 'maximumCurlAngle',
                   40: 'curlNormalX', 48: 'curlNormalY', 56: 'curlTangentX', 64: 'curlTangentY'}
    material_roles = {136: 'pageLeft', 144: 'pageTop', 152: 'pageWidth', 160: 'pageHeight',
                      168: 'sourceLeft', 176: 'sourceTop', 184: 'sourceWidth', 192: 'sourceHeight',
                      200: 'projectedNormalX', 208: 'projectedNormalY', 216: 'projectedTangentX', 224: 'projectedTangentY',
                      232: 'frontMaterialColor', 240: 'frontColorMultiplier', 248: 'backMaterialColor',
                      256: 'backLightingColor', 272: 'shadowMaterialColor'}
    for key in sorted(parent, key=lambda x: (x[0], x[1], x[2])):
        key = find(key)
        if key in names: continue
        defs = sorted(definitions[key])
        first = defs[0] if defs else (0, 0, key[2], '')
        n, _, reg, expr = first
        phase = steps[n][1]
        candidates = [special[n, reg]] if (n, reg) in special else []
        candidates += suggestions[key]
        if re.search(r'ld_u64\(\(x19 \+ 8L\)\)', expr): candidates.insert(0, 'drawStateAddress')
        if n == 0 and re.search(r'ld_u64\(\(x0 \+ 224L\)\)', expr): candidates.insert(0, 'layerStateAddress')
        if n == 0 and reg.startswith('v'):
            load_expr = re.sub(r'\(\(x([12]) \+ (\d+)L\) \+ (\d+)L\)',
                              lambda m: f'(x{m[1]} + {int(m[2]) + int(m[3])}L)', expr)
            load = re.search(r'ld_d\(\(?x([12]) \+ (\d+)L', load_expr)
            if load:
                role = (field_roles if load[1] == '1' else material_roles).get(int(load[2]))
                if role: candidates.insert(0, role)
        if n == 0 and re.search(r'ld_d\(\(x8 \+ 32L\)\)', expr): candidates.insert(0, 'layerColorMultiplier')
        if n == 0 and '1.5707963267948966' in expr: candidates.insert(0, 'halfPiRadians')
        if n == 0 and re.search(r'ld_d\(\(sp \+ 512L\)\)', expr): candidates.insert(0, 'curlRadius')
        if n == 0 and re.search(r'ld_d\(\(sp \+ 536L\)\)', expr): candidates.insert(0, 'normalX')
        if n == 0 and re.search(r'ld_d\(\(sp \+ 528L\)\)', expr): candidates.insert(0, 'normalY')
        if candidates:
            base = candidates[0]
        elif reg.startswith('x'):
            base = phase + ('Address' if re.search(r'\bsp\b|ld_u64|\+ \d+L', expr) else 'Index' if re.search(r'0L|1L|subflags', expr) else 'Bits')
        else:
            base = phase + ('Color' if re.search(r'halfFloat|floatHalf|\.h\(|Color', expr) else 'Rotation' if 'sincos' in expr else 'Value')
        name = base
        counter = 2
        while name in occupied:
            name = base + str(counter); counter += 1
        occupied.add(name); names[key] = name
    # Replacements touch identifiers only. Semantic views alias the same physical
    # slot, so partial vector writes and branch joins retain the original bits.
    files = defaultdict(list)
    header = '''// Generated algorithm stages; regenerate with tools/Port-CurlKernel.py.
@file:Suppress("UNUSED_VARIABLE", "UNUSED_VALUE", "NAME_SHADOWING", "RedundantConversion")
package org.peyilo.ioscurl.mesh.kernel
import kotlin.math.*
import org.peyilo.ioscurl.math.AppleMath
import org.peyilo.ioscurl.math.BinaryMath.fma
'''
    for n, (label, body) in blocks.items():
        replacements = [(pos, pos + len(reg), names[key]) for (number, pos), key in uses.items()
                        if number == n for reg in [re.match(REG, body[pos:]).group()]]
        for _, kind, event in events[n]:
            if kind == 'write': replacements.append((event[0], event[0] + len(event[1]), names[find(event[3])]))
        for start, end, name in sorted(replacements, reverse=True): body = body[:start] + name + body[end:]
        scalar_names = {names[key]: method for key, method in scalar_views.items()}
        changes = []
        for match in re.finditer(r'\b(\w+)\.(d|f|h|u32|u64)\(', body):
            if match[1] not in scalar_names: continue
            end = matching(body, match.end() - 1)
            args = arguments(body[match.end():end])
            if len(args) == 1:
                changes.append((match.start(), end + 1, match[1]))
            else:
                changes.append((match.start(), match.end() + args[1][0], match[1] + ' = '))
                changes.append((end, end + 1, ''))
        for start, end, value in sorted(changes, reverse=True): body = body[:start] + value + body[end:]
        body = re.sub(r'\b(\d+)L\.toInt\(\)', r'\1', body)
        body = re.sub(r'\b([NZCV])\b', lambda m: {'N':'negativeResult','Z':'zeroResult','C':'unsignedCarry','V':'arithmeticOverflow'}[m[1]], body)
        body = re.sub(r'\bsp\b', 'scratchBase', body)
        # Only return targets are branch IDs; numeric geometry inputs stay intact.
        def target(number):
            name = steps[int(number)][2]
            return 'KernelStep.' + name[0].upper() + name[1:]
        changes = []
        for match in re.finditer(r'\breturn if\s*\(', body):
            end = matching(body, match.end() - 1)
            targets = re.match(r'\s*(\d+)\s+else\s+(\d+)', body[end + 1:])
            assert targets, body[match.start():end + 100]
            for group in (1, 2):
                changes.append((end + 1 + targets.start(group), end + 1 + targets.end(group), target(targets[group])))
        for start, end, value in sorted(changes, reverse=True): body = body[:start] + value + body[end:]
        body = re.sub(r'\breturn (\d+)\b', lambda m: 'return ' + target(m[1]), body)
        body = re.sub(r'return -1\b', 'return KernelStep.Complete', body)
        body = re.sub(r'return -2\b', 'return KernelStep.Invalid', body)
        filename, _, function = steps[n]
        files[filename].append(f'// Reference step {n}: {label}\ninternal fun CurlMeshKernel.{function}(): KernelStep {{' + readable_literals(body) + '}\n')
    for filename, bodies in files.items():
        (directory / filename).write_text(header + '\n'.join(bodies), encoding='utf-8')
    values = [header, '''/** Semantic views of live values. Views of disjoint live ranges intentionally
 * share storage; these slot indices describe memory reuse, not algorithm constants.
 * Keep these generated bindings synchronized with the stage data-flow analysis.
 */''']
    for key, name in names.items():
        reg = key[2]; index = int(reg[1:])
        if reg.startswith('x'):
            values.append(f'internal var CurlMeshKernel.{name}: Long\n    get() = integerValue({index})\n    set(value) {{ integerValue({index}, value) }}\n')
        elif key in scalar_views:
            method = scalar_views[key]
            kind = 'Double' if method == 'd' else 'Float' if method == 'f' else 'Long'
            values.append(f'internal var CurlMeshKernel.{name}: {kind}\n    get() = vectorValue({index}).{method}(0)\n    set(value) {{ vectorValue({index}).{method}(0, value) }}\n')
        else:
            values.append(f'internal val CurlMeshKernel.{name}: KernelVector get() = vectorValue({index})\n')
    (directory / 'KernelValues.kt').write_text('\n'.join(values), encoding='utf-8')
    enum = ',\n'.join('    ' + v[2][0].upper() + v[2][1:] for _, v in sorted(steps.items()))
    dispatch = '\n'.join('            KernelStep.' + v[2][0].upper() + v[2][1:] + ' -> ' + v[2] + '()' for _, v in sorted(steps.items()))
    flow = header + '\ninternal enum class KernelStep {\n' + enum + ',\n    Complete,\n    Invalid\n}\n'
    flow += '''
/** Explicit geometry stages; labels preserve the reference algorithm's branch ordering. */
internal fun CurlMeshKernel.execute() {
    var step = KernelStep.PrepareMaterialsAndBounds
    var remaining = MAX_GEOMETRY_STEPS
    while (step != KernelStep.Complete) {
        check(--remaining > 0) { "Curl geometry iteration limit" }
        step = when (step) {
''' + dispatch + '''
            KernelStep.Complete -> KernelStep.Complete
            KernelStep.Invalid -> error("Invalid curl geometry")
        }
    }
}
private const val MAX_GEOMETRY_STEPS = 100_000
'''
    (directory / 'KernelFlow.kt').write_text(flow, encoding='utf-8')
    for path in originals: path.unlink()
    return list(files) + ['KernelValues.kt', 'KernelFlow.kt']

KERNEL_IDENTIFIERS = {
    'ld_d':'readDouble', 'ld_f':'readFloat', 'ld_u64':'readLongBits',
    'ld_u32':'readUnsignedWord', 'ld_u8':'readUnsignedByte',
    'st_d':'writeDouble', 'st_f':'writeFloat', 'st_u64':'writeLongBits',
    'st_u32':'writeWord', 'st_u16':'writeHalfWord', 'st_u8':'writeByte',
    'fcmp':'compareFloatingPoint', 'subflags':'updateSubtractionFlags',
    'addflags':'updateAdditionFlags', 'displace':'curlDisplacement',
    'line_clip':'clipProjectedLine', 'strip_emit':'appendSurfaceStrip',
    'apply_point':'transformPointInPlace', 'get_scale':'measureTransformScale',
    'sincos':'writeRotationMatrix', 'next_clip':'nextClipRectangle', 'capture_draw':'appendDrawBatch',
}

COLOR_VALUE_NAMES = {
    'projectionColor':'frontBaseRgba', 'projectionColor2':'layerTintRgba',
    'projectionColor3':'frontTintRgba', 'projectionColor4':'packedFrontTint',
    'projectionColor5':'frontMultiplierRgba', 'projectionColor6':'frontSecondaryTintRgba',
    'projectionColor7':'packedFrontSecondaryTint', 'projectionColor8':'backLightingRgba',
    'projectionColor9':'backLightingTintRgba', 'projectionColor10':'packedBackLightingTint',
    'projectionColor11':'backBaseRgba', 'projectionColor12':'backTintRgba',
    'projectionColor13':'shadowBaseRgba', 'projectionColor14':'shadowTintRgba',
    'projectionColor15':'packedShadowTint',
}

def normalize_kernel_source(text):
    text = re.sub(r'\b(' + '|'.join(sorted(COLOR_VALUE_NAMES, key=len, reverse=True)) + r')\b', lambda m: COLOR_VALUE_NAMES[m[1]], text)
    # Method names of the kernel itself; AppleMath.sincos is a different function.
    text = re.sub(r'(?<=CurlMeshKernel\.)(' + '|'.join(KERNEL_IDENTIFIERS) + r')\b', lambda m: KERNEL_IDENTIFIERS[m[1]], text)
    text = re.sub(r'(?<![\w.])(' + '|'.join(KERNEL_IDENTIFIERS) + r')\b', lambda m: KERNEL_IDENTIFIERS[m[1]], text)
    views = dict(d='double', f='float', h='halfBits', u64='longBits', u32='wordBits')
    text = re.sub(r'\.(d|f|h|u64|u32)\(', lambda m: '.' + views[m[1]] + '(', text)
    text = re.sub(r'\bfun (d|f|h|u64|u32)\(', lambda m: 'fun ' + views[m[1]] + '(', text)
    # Internal calls inside KernelVector use the same bit views without a receiver.
    text = re.sub(r'(?<![\w.])(d|f|h|u64|u32)\(', lambda m: views[m[1]] + '(', text)
    text = text.replace('0xffffffffL', 'KernelLayout.WORD_MASK')
    text = text.replace('0x3c003c003c003c00L', 'KernelLayout.HALF_RGBA_WHITE')
    text = re.sub(r'(?<![\w.])4575657221408423936L\b', 'KernelLayout.POSITION_Z_ZERO_W_ONE', text)
    text = re.sub(r'(?<![\w.])144115188075855871L\b', 'KernelLayout.VERTEX_PAIR_INDEX_MASK', text)
    text = text.replace('0.00392156862745098', 'KernelTuning.NORMALIZED_CHANNEL_SCALE_DOUBLE')
    text = text.replace('0.0078125', 'KernelTuning.SHADOW_PLANE_Z')
    # Data-record offsets are meaningful only relative to their owning record.
    offsets = {
        r'(?:materialInputAddress|materialAddress)': ('Material', {0:'FRONT_ENABLED',1:'BACK_ENABLED',8:'INVERSE_TRANSFORM',88:'INVERSE_Z_SCALE',136:'PAGE_BOUNDS',168:'SOURCE_BOUNDS',200:'NORMAL_X',208:'NORMAL_Y',216:'TANGENT_X',224:'TANGENT_Y',232:'FRONT_COLOR',240:'FRONT_MULTIPLIER',248:'BACK_COLOR',256:'BACK_SHADE',264:'LIGHTING_ENABLED',272:'SHADOW_COLOR',280:'OPACITY'}),
        r'(?:controlInputAddress|controlAddress)': ('Control', {0:'TIME',8:'ANGLE',16:'RADIUS',24:'START_ANGLE',32:'END_ANGLE',40:'NORMAL_X',48:'NORMAL_Y',56:'TANGENT_X',64:'TANGENT_Y',72:'SHADOW_BOUNDS'}),
        r'(?:contextArgument|renderContextAddress\d*)': ('Context', {0:'CALLBACK_TABLE',8:'DRAW_STATE',224:'LAYER_STATE',232:'FRAME_COUNT',552:'ACTIVE_SURFACE'}),
        r'drawStateAddress\d*': ('DrawState', {16:'PROGRAM',416:'BLEND_FUNCTION',417:'FLAGS'}),
    }
    for base, (record, fields) in offsets.items():
        text = re.sub(r'\b(' + base + r') \+ (\d+)L\b',
            lambda m: m[1] + ' + KernelLayout.' + record + '.' + fields[int(m[2])]
            if int(m[2]) in fields else m[0], text)
    text = re.sub(r'for \(k in 0L until ([24])L\)', r'for (lane in 0 until \1)', text)
    text = text.replace('k.toInt()', 'lane')
    text = re.sub(r'internal (var|val) CurlMeshKernel\.', r'internal inline \1 CurlMeshKernel.', text)
    if 'import kotlin.math.*' in text:
        functions = ['abs','atan2','ceil','cos','floor','hypot','max','min','PI','pow','round','sin','sqrt','tan']
        text = text.replace('import kotlin.math.*', '\n'.join('import kotlin.math.' + name for name in functions if re.search(r'\b' + name + r'\b', text)))
    return readable_literals(text)

if __name__ == '__main__':
    print('\n'.join(refine_kernel()))

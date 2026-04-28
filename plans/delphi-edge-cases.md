# Delphi Edge Cases — TreeSitterExcavationSite

Original Spring4D-driven catalogue of Delphi dependency-extraction issues. All seven items
have been resolved or verified by `plans/fix-delphi-edge-cases.md`. Kept as a historical
record of what was investigated and what each resolution looks like.

---

## 1. `uses Unit in 'path'` clause silently dropped — **Verified clean**

`tree-sitter-pascal` 0.10.2 emits the `in '<path>'` clause as ERROR sibling nodes inside
`declUses`, but the leading `moduleName` is parsed cleanly. Existing `ImportExtractor` walks
`moduleName` children, so both modules are captured.

**Resolution:** regression test
`DelphiDependencyTest.ImportExtraction.should extract module names from uses in path form`.
No code change.

---

## 2. Forward declarations produce duplicate — **Fixed**

A forward `TFoo = class;` parses as a `declType` whose `declClass` body has no `kEnd` token.
`DeclarationExtractor.isForwardDeclaration()` filters these so the full definition is the
unique entry for the type name.

**Resolution:** filter in `DeclarationExtractor.extract()` + regression test
`DelphiDependencyTest.DeclarationExtraction.should emit a single declaration for a
forward-declared class with a later full definition`.

---

## 3. Operator implementation comment is factually wrong — **Fixed**

`class operator TAny.Implicit(…)` is parsed with the method-name segment as a regular
`identifier` (not `operatorName`). `qualifiedNameClassPrefix` only inspects the LHS to find
the owning class, so operators bind correctly.

**Resolution:** rewrote the misleading KDoc on `collectDefProcsByClass` and the matching
"Accepted v1 limitations" entry in `plans/add-delphi-dependency-support.md`. Added regression
test `DelphiDependencyTest.UsedTypeExtraction.should bind class operator defProc bodies to
their declaring class`.

---

## 4. `dispinterface` declarations silently skipped — **Verified clean**

`dispinterface` is parsed as `declIntf` with a `kDispInterface` keyword (no separate
`declDispIntf` node). The existing `DECL_INTF -> DeclarationType.INTERFACE` mapping already
covers it.

**Resolution:** regression test
`DelphiDependencyTest.DeclarationExtraction.should classify dispinterface as INTERFACE`.
No code change.

---

## 5. Nested type declarations intentionally excluded — **Implemented**

Removed `isNestedInsideDeclaration()` and added `findParentPath()` mirroring Kotlin/C#.
Nested types now ship with `parentPath = [outer-class-chain]`. Outer classes still leak the
inner classes' types via the existing recursive traversal — same convention as Kotlin/C# in
TSE today.

**Resolution:** new `DelphiDependencyTest.NestedDeclarations` group, plus a `TOuter`/`TInner`
fixture in `delphi_sample.pas`. The dependencies golden serializer was extended to render
`parentPath` so the nested structure is visible.

---

## 6. Records implementing interfaces — **Verified clean**

`record(IInterface)` parses as `declClass` (kRecord keyword) with a `typeref` inheritance
child; the existing inheritance walk picks it up.

**Resolution:** regression test
`DelphiDependencyTest.UsedTypeExtraction.should capture interfaces implemented by a record`.
No code change.

---

## 7. `{$IFDEF}` inside uses clauses — **Verified clean**

The parser keeps a single `declUses` node and emits `pp` directive siblings; every
`moduleName` is reachable.

**Resolution:** regression test
`DelphiDependencyTest.ImportExtraction.should extract all uses entries even when interrupted
by IFDEF directives`.
No code change.

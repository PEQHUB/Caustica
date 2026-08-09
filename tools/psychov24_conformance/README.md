# PsychoV24 conformance comparator

The repository has no shader-execution harness, so this tool does not add one to the game.
Use the smallest available external/reference and production shader harnesses to emit two CSV
files, then compare them:

```text
python tools/psychov24_conformance/compare_vectors.py reference.csv candidate.csv
```

Each file must contain the header below and identical rows in identical order:

```text
case,class,input_r,input_g,input_b,output_r,output_g,output_b
```

The comparator rejects mismatched case classes and input vectors before comparing outputs.

Use `class=extreme` for gamut-boundary or other extreme vectors; all other rows use the
ordinary `2e-5` channel tolerance. The comparator reports vector count, maximum error,
worst case/channel, non-finite count, and tolerance failures. It deliberately does not
perform GPU readback, alter runtime code, or claim numerical proof without both captures.

#!/usr/bin/env python3
"""Compare Test24 reference and production vector CSV files.

Each file contains: case, class, input_r, input_g, input_b, output_r, output_g, output_b.
The files must contain the same cases in the same order. ``class`` is ``extreme`` for
gamut-boundary cases; all other rows use the ordinary tolerance.
"""

import argparse
import csv
import math
import sys
from pathlib import Path


ORDINARY_TOLERANCE = 2e-5
EXTREME_TOLERANCE = 1e-4
CHANNELS = ("r", "g", "b")


def read_vectors(path: Path):
    with path.open(newline="", encoding="utf-8") as stream:
        rows = list(csv.DictReader(stream))
    required = {"case", "class", "input_r", "input_g", "input_b", "output_r", "output_g", "output_b"}
    if not rows or not required.issubset(rows[0]):
        raise ValueError(f"{path}: expected vector CSV columns {', '.join(sorted(required))}")
    return rows


def number(row, name, path):
    try:
        return float(row[name])
    except (KeyError, ValueError) as error:
        raise ValueError(f"{path}: invalid {name} in case {row.get('case', '?')}") from error


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reference", type=Path, help="CSV emitted by the pinned Test24 reference")
    parser.add_argument("candidate", type=Path, help="CSV emitted by the production shader harness")
    args = parser.parse_args()

    reference = read_vectors(args.reference)
    candidate = read_vectors(args.candidate)
    if len(reference) != len(candidate):
        raise ValueError(f"vector count differs: reference={len(reference)} candidate={len(candidate)}")

    worst = None
    failures = 0
    nonfinite = 0
    for ref, actual in zip(reference, candidate):
        if ref["case"] != actual["case"]:
            raise ValueError(f"case order differs: {ref['case']} != {actual['case']}")
        if ref["class"].strip().lower() != actual["class"].strip().lower():
            raise ValueError(f"classification differs in case {ref['case']}")
        for field in ref:
            if field.startswith(("input_", "param_", "parameter_")):
                expected_input = number(ref, field, args.reference)
                observed_input = number(actual, field, args.candidate)
                if expected_input != observed_input:
                    raise ValueError(f"{field} differs in case {ref['case']}")
        limit = EXTREME_TOLERANCE if ref["class"].lower() == "extreme" else ORDINARY_TOLERANCE
        input_vector = tuple(number(ref, f"input_{channel}", args.reference) for channel in CHANNELS)
        for channel in CHANNELS:
            expected = number(ref, f"output_{channel}", args.reference)
            observed = number(actual, f"output_{channel}", args.candidate)
            if not math.isfinite(expected) or not math.isfinite(observed):
                nonfinite += 1
                continue
            error = abs(observed - expected)
            if worst is None or error > worst[0]:
                worst = (error, ref["case"], channel, input_vector, expected, observed)
            if error > limit:
                failures += 1

    print(f"vector count: {len(reference)}")
    assert worst is not None
    print(f"maximum absolute error: {worst[0]:.9g}")
    print(f"worst input vector: {worst[3]}")
    print(f"worst output channel: {worst[2]}")
    print(f"worst reference/candidate: {worst[4]:.9g}/{worst[5]:.9g}")
    print(f"NaN/Inf count: {nonfinite}")
    print(f"tolerance failures: {failures}")
    return 1 if failures or nonfinite else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except ValueError as error:
        print(error, file=sys.stderr)
        sys.exit(2)

#!/usr/bin/env python3
"""Turns a JMH JSON result of bench.BindingBenchmark into Markdown tables.

For every (fixture, op, parser) it lists each implementation with throughput, error,
delta versus the baseline, gc.alloc.rate.norm and its delta, and the ratio to Jackson.
A throughput delta is marked significant only when the two 99.9% confidence intervals
reported by JMH do not overlap.
"""
import json
import sys
from collections import OrderedDict

FIXTURE_BYTES = {}


def main(path, baseline="baseline"):
    data = json.load(open(path))
    groups = OrderedDict()
    for r in data:
        p = r["params"]
        key = (p["fixture"], p["op"], p.get("parser", "sax"))
        m = r["primaryMetric"]
        alloc = r.get("secondaryMetrics", {}).get("gc.alloc.rate.norm", {})
        groups.setdefault(key, OrderedDict())[p["impl"]] = {
            "score": m["score"], "err": m["scoreError"],
            "ci": m.get("scoreConfidence", [m["score"] - m["scoreError"], m["score"] + m["scoreError"]]),
            "alloc": alloc.get("score"),
            "forks": r.get("forks"), "wi": r.get("warmupIterations"), "mi": r.get("measurementIterations"),
        }
    first = data[0]
    out = []
    out.append("JMH %s, JDK %s (%s), forks=%s, warmup=%sx%s, measurement=%sx%s\n" % (
        first.get("jmhVersion"), first.get("jdkVersion"), first.get("vmName"), first.get("forks"),
        first.get("warmupIterations"), first.get("warmupTime"),
        first.get("measurementIterations"), first.get("measurementTime")))
    for (fixture, op, parser), impls in groups.items():
        if op == "marshal" or "jackson" in impls and len(impls) == 1:
            title = "%s / %s" % (fixture, op)
        else:
            title = "%s / %s (JAXB input: %s)" % (fixture, op, parser)
        out.append("### " + title + "\n")
        out.append("| impl | ops/s | error | vs baseline | B/op | B/op vs baseline | vs Jackson (ops/s) |")
        out.append("|---|---:|---:|---:|---:|---:|---:|")
        base = impls.get(baseline)
        jack = impls.get("jackson")
        for name, v in impls.items():
            d = ""
            da = ""
            if base and name != baseline:
                d = "%+.1f%%" % (100.0 * (v["score"] / base["score"] - 1))
                lo, hi = v["ci"]
                blo, bhi = base["ci"]
                if lo > bhi or hi < blo:
                    d += " (sig.)"
                else:
                    d += " (n.s.)"
                if v["alloc"] is not None and base["alloc"]:
                    da = "%+.1f%%" % (100.0 * (v["alloc"] / base["alloc"] - 1))
            j = ""
            if jack and name != "jackson":
                j = "%+.1f%%" % (100.0 * (v["score"] / jack["score"] - 1))
            alloc = "%.1f" % v["alloc"] if v["alloc"] is not None else "-"
            out.append("| %s | %.0f | %.0f | %s | %s | %s | %s |" % (name, v["score"], v["err"], d, alloc, da, j))
        out.append("")
    print("\n".join(out))


if __name__ == "__main__":
    main(*sys.argv[1:])

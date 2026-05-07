# SHACL-DS: Dataset-level SHACL Validation

SHACL-DS is an extension of the W3C SHACL standard that supports **RDF dataset** validation.

This repository contains two things:

1. A **use case on the ERA/RINF Knowledge Graph** that demonstrates and evaluates SHACL-DS
   against standard SHACL on a real-world large-scale dataset (`data/`, `benchmark/`).
2. A **implementation of SHACL-DS** (SHACL for Datasets), built on top of the TopBraid SHACL API (`TopbraidProcessor/`).


---

## Repository Structure

```
.
├── jars/
│   └── benchmark.jar         # Self-contained benchmark runner
├── data/
│   ├── download-dataset.sh   # Download the ERA/RINF dataset (Linux/Mac)
│   ├── download-dataset.bat  # Download the ERA/RINF dataset (Windows)
│   ├── SHACL.ttl             # Standard SHACL shapes
│   ├── SHACL-DS-TARGET.trig  # SHACL-DS shapes using a Target Graph Strategy
│   ├── SHACL-DS-TARGET-EXTRA.trig  # SHACL-DS shapes using a Target Graph Strategy and extra shapes graphs
│   ├── SHACL-DS-COMBO.trig         # SHACL-DS shapes using a Target Graph Combination Strateg
│   └── SHACL-DS-COMBO-EXTRA.trig   # SHACL-DS shapes using a Target Graph Combination Strategy and extra shapes graphs
├── benchmark/                # Source code of the benchmark runner
├── TopbraidProcessor/        # Source code of the SHACL-DS implementation
└── README.md
```

---

## Prerequisites

- **Java 21** or later (`java -version` to check)
- **curl** and **unzip** / **tar** to download the dataset (standard on most systems)

No Maven installation is required to run the benchmark — use the pre-built `jars/benchmark.jar`.

---

## Step 1 — Download the Dataset

The ERA/RINF dataset (~547 MB compressed) is hosted on Zenodo and must be downloaded
separately before running the use case.

**Linux / Mac:**
```bash
cd data
chmod +x download-dataset.sh
./download-dataset.sh
```

**Windows:**
```bat
cd data
download-dataset.bat
```

This downloads `RINF-dump-20260217.nq` into the `data/` folder. The script is idempotent:
it skips the download if the file already exists.

---

## Step 2 — Run the Use Case

Run from the repository root. The use case compares multiple validation strategies on
the same dataset.

**Linux / Mac:**
```bash
java -Xmx40g -jar jars/benchmark.jar \
  --dataset              data/RINF-dump-20260217.nq \
  --shacl                data/SHACL.ttl \
  --shacl-full           data/SHACL.ttl \
  --shacl-ds-tg          data/SHACL-DS-TARGET.trig \
  --shacl-ds-tg-extra    data/SHACL-DS-TARGET-EXTRA.trig \
  --shacl-ds-combo       data/SHACL-DS-COMBO.trig \
  --shacl-ds-combo-extra data/SHACL-DS-COMBO-EXTRA.trig \
  --warmup  1 \
  --measure 10 \
  --output  bench-output/
```

**Windows:**
```bat
java -Xmx40g -jar jars\benchmark.jar ^
  --dataset              data\RINF-dump-20260217.nq ^
  --shacl                data\SHACL.ttl ^
  --shacl-full           data\SHACL.ttl ^
  --shacl-ds-tg          data\SHACL-DS-TARGET.trig ^
  --shacl-ds-tg-extra    data\SHACL-DS-TARGET-EXTRA.trig ^
  --shacl-ds-combo       data\SHACL-DS-COMBO.trig ^
  --shacl-ds-combo-extra data\SHACL-DS-COMBO-EXTRA.trig ^
  --warmup  1 ^
  --measure 10 ^
  --output  bench-output\
```

> **Memory:** The ERA/RINF dataset is large. At least 40 GB of heap (`-Xmx40g`) is
> recommended. Increase further if you encounter OutOfMemoryError.
>
> **Runtime warning:** Each validation run per configuration takes approximately **10 minutes**
> on the full ERA/RINF dataset. With the default settings (1 warmup + 10 measured rounds,
> 6 configurations), the complete execution takes **over 10 hours**. Use `--warmup 0 --measure 1 --shacl-ds-tg data/SHACL-DS-TARGET.trig`
> for a quick smoke test.

### Arguments

| Argument | Description |
|---|---|
| `--dataset` | Path to the RDF dataset (NQuads) to validate |
| `--shacl` | Standard SHACL shapes — merges the 44 operator graphs and ERA reference graphs |
| `--shacl-full` | Standard SHACL shapes — merges all 56 named graphs |
| `--shacl-ds-tg` | SHACL-DS shapes using the Target Graph Strategy |
| `--shacl-ds-tg-extra` | SHACL-DS shapes using the Target Graph Strategy with extra shapes graphs |
| `--shacl-ds-combo` | SHACL-DS shapes using the Combination Strategy |
| `--shacl-ds-combo-extra` | SHACL-DS shapes using the Combination Strategy with extra shapes graphs |
| `--warmup N` | Number of warmup rounds per configuration (default: 1, results discarded) |
| `--measure N` | Number of measured rounds per configuration (default: 10) |
| `--output dir` | Directory for per-run validation reports (default: `bench-output`) |

All shape arguments are optional — only the provided configurations are executed.

---

/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  See the NOTICE file distributed with this work for additional
 *  information regarding copyright ownership.
 */
package shaclDS;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.topbraid.shacl.ds.DatasetValidationEngineConfiguration;
import org.topbraid.shacl.ds.TargetGraphSelector;
import org.topbraid.shacl.ds.TargetGraphSelector.CombinationPair;
import org.topbraid.shacl.validation.ValidationUtil;
import org.topbraid.shacl.vocabulary.SH;

/**
 * Benchmarks the six SHACL validation configurations used in the ISWC paper
 * on a shared, pre-loaded data dataset.
 *
 * <h3>Configurations</h3>
 * <ol>
 *   <li><b>SHACL</b> — standard merged-model SHACL baseline: merges the 44 operator
 *       graphs and the four ERA reference graphs (ontology, skos, countries, borders).</li>
 *   <li><b>SHACL-full</b> — standard merged-model SHACL on all 56 named graphs.</li>
 *   <li><b>SHACL-DS Target Strategy</b> — validates via {@code shds:targetGraphPattern}.</li>
 *   <li><b>SHACL-DS Target Strategy EXTRA</b> — same engine, extended shapes dataset.</li>
 *   <li><b>SHACL-DS Combination Strategy</b> — validates via {@code shds:targetGraphCombination}.</li>
 *   <li><b>SHACL-DS Combination Strategy EXTRA</b> — same engine, extended shapes dataset.</li>
 * </ol>
 *
 * <h3>Fair comparison design</h3>
 * <ul>
 *   <li>The data dataset is loaded once before any timing begins and reused by all runs.</li>
 *   <li>Shapes are loaded fresh on every run (included in that run's load time).</li>
 *   <li>Warmup rounds (default 5) are executed in random order; their results are discarded.</li>
 *   <li>{@code System.gc()} is called between every run to reduce GC interference.</li>
 *   <li>Measure rounds (default 10) are executed in a freshly shuffled random order each
 *       round so no configuration benefits from a consistent position.</li>
 * </ul>
 *
 * <h3>Tracked timings</h3>
 * <ul>
 *   <li>{@code loadNs} — time to parse the shapes file/dataset from disk.</li>
 *   <li>{@code mergeNs} — time to merge data graphs (SHACL and SHACL-full only).</li>
 *   <li>{@code viewCreationNs} — time to resolve combinations and build combined models
 *       (Combination Strategy only; measured as a pre-pass).</li>
 *   <li>{@code validationNs} — wall time of the complete validation call.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   DSBenchmark --dataset            RINF-dump-20260217.nq
 *               [--shacl             SHACL.ttl]
 *               [--shacl-full        SHACL.ttl]
 *               [--shacl-ds-tg       SHACL-DS-TARGET.trig]
 *               [--shacl-ds-tg-extra SHACL-DS-TARGET-EXTRA.trig]
 *               [--shacl-ds-combo    SHACL-DS-COMBO.trig]
 *               [--shacl-ds-combo-extra SHACL-DS-COMBO-EXTRA.trig]
 *               [--warmup  1]
 *               [--measure 10]
 *               [--output  ./bench-output]
 * }</pre>
 *
 * @author Davan Chiem Dao
 */
public class DSBenchmark {

    // -------------------------------------------------------------------------
    // Internal types
    // -------------------------------------------------------------------------

    private enum Approach {
        SHACL,
        SHACL_FULL,
        SHACL_DS_TG,
        SHACL_DS_TG_EXTRA,
        SHACL_DS_COMBO,
        SHACL_DS_COMBO_EXTRA
    }

    private record RunResult(
        Approach approach,
        int      round,
        long     loadNs,
        long     mergeNs,        // SHACL / SHACL_FULL only; 0 for others
        long     viewCreationNs, // Combination Strategy only; 0 for others
        long     validationNs,
        int      violations,
        long     peakHeapBytes   // peak JVM heap during validation
    ) {}

    // -------------------------------------------------------------------------
    // Graphs excluded from the SHACL baseline merge
    // (duplicate ontology/skos versions, shapes graphs, borders, metadata)
    // -------------------------------------------------------------------------

    private static final Set<String> BASELINE_EXCLUDED_GRAPHS = Set.of(
        "http://data.europa.eu/949/graph/rinf/skos",
        "http://data.europa.eu/949/graph/v3-1-5/skos",
        "http://data.europa.eu/949/graph/v3-1-5/ontology",
        "http://data.europa.eu/949/graph/v3-1-5/shacl",
        "http://data.europa.eu/949/graph/rinf/shacl",
        "http://data.europa.eu/949/graph/shacl",
        "http://data.europa.eu/949/graph/rinf/ontology",
        "http://data.europa.eu/949/graph/rinf/dataset"
    );

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    private String datasetPath;
    private String shaclPath;
    private String shaclFullPath;
    private String shaclDsTgPath;
    private String shaclDsTgExtraPath;
    private String shaclDsComboPath;
    private String shaclDsComboExtraPath;
    private int    warmupRounds  = 1;
    private int    measureRounds = 10;
    private String outputDir     = "bench-output";

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        try {
            DSBenchmark bench = new DSBenchmark();
            bench.parseArgs(args);
            bench.run();
        } catch (Exception e) {
            System.err.println("Benchmark failed: " + e);
            System.exit(1);
        }
    }

    // -------------------------------------------------------------------------
    // Benchmark runner
    // -------------------------------------------------------------------------

    private void run() throws Exception {
        List<Approach> activeApproaches = activeApproaches();
        validate(activeApproaches);

        Files.createDirectories(Paths.get(outputDir));

        // Load dataset once — not included in per-approach timings
        log("Loading dataset: %s", datasetPath);
        long t0 = System.nanoTime();
        Dataset dataDataset = DatasetFactory.create();
        RDFDataMgr.read(dataDataset, datasetPath);
        long datasetLoadNs = System.nanoTime() - t0;
        log("Dataset loaded in %.3f ms", datasetLoadNs / 1e6);
        log("");

        Random rng = new Random(42);

        // --- Warmup (discarded) ---
        log("Warmup (%d rounds per configuration, random order)...", warmupRounds);
        List<Approach> order = new ArrayList<>(activeApproaches);
        for (int round = 0; round < warmupRounds; round++) {
            Collections.shuffle(order, rng);
            for (Approach approach : order) {
                gc();
                runApproach(approach, dataDataset, round, false);
            }
        }
        log("Warmup complete.");
        log("");

        // --- Measurement ---
        log("Measuring (%d rounds per configuration, random order)...", measureRounds);
        List<RunResult> results = new ArrayList<>();
        order = new ArrayList<>(activeApproaches);
        for (int round = 0; round < measureRounds; round++) {
            Collections.shuffle(order, rng);
            for (Approach approach : order) {
                gc();
                RunResult result = runApproach(approach, dataDataset, round, round == 0);
                results.add(result);
                log("  [%-24s] round %2d  load=%7.2f ms  %svalidation=%7.2f ms  violations=%d",
                    approach, round + 1,
                    result.loadNs() / 1e6,
                    formatExtra(result),
                    result.validationNs() / 1e6,
                    result.violations());
            }
        }

        // --- Summary statistics ---
        log("");
        log("=".repeat(90));
        log("Dataset load time (once):  %.3f ms", datasetLoadNs / 1e6);
        log("");
        for (Approach approach : activeApproaches) {
            printStats(approach, results);
        }
    }

    // -------------------------------------------------------------------------
    // Per-approach runners
    // -------------------------------------------------------------------------

    private RunResult runApproach(Approach approach, Dataset dataDataset,
            int round, boolean saveReport) throws IOException {
        return switch (approach) {
            case SHACL               -> runShaclMerge(approach, dataDataset, round, saveReport, shaclPath,     true);
            case SHACL_FULL          -> runShaclMerge(approach, dataDataset, round, saveReport, shaclFullPath,  false);
            case SHACL_DS_TG         -> runShaclDsTg(approach,   dataDataset, round, saveReport, shaclDsTgPath);
            case SHACL_DS_TG_EXTRA   -> runShaclDsTg(approach,   dataDataset, round, saveReport, shaclDsTgExtraPath);
            case SHACL_DS_COMBO      -> runShaclDsCombo(approach, dataDataset, round, saveReport, shaclDsComboPath);
            case SHACL_DS_COMBO_EXTRA-> runShaclDsCombo(approach, dataDataset, round, saveReport, shaclDsComboExtraPath);
        };
    }

    /**
     * Merged-model SHACL. When {@code baselineOnly} is true, graphs in
     * {@link #BASELINE_EXCLUDED_GRAPHS} are skipped (SHACL baseline).
     * When false, all named graphs are merged (SHACL-full).
     */
    private RunResult runShaclMerge(Approach approach, Dataset dataDataset,
            int round, boolean saveReport, String shapesPath, boolean baselineOnly) throws IOException {
        long t0 = System.nanoTime();
        Model shapesModel = ModelFactory.createDefaultModel();
        RDFDataMgr.read(shapesModel, shapesPath);
        long loadNs = System.nanoTime() - t0;

        long t1 = System.nanoTime();
        Model merged = ModelFactory.createDefaultModel();
        Iterator<String> names = dataDataset.listNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!baselineOnly || !BASELINE_EXCLUDED_GRAPHS.contains(name)) {
                merged.add(dataDataset.getNamedModel(name));
            }
        }
        long mergeNs = System.nanoTime() - t1;

        AtomicLong peak = startMemoryMonitor();
        long t2 = System.nanoTime();
        Resource report = ValidationUtil.validateModel(merged, shapesModel, false);
        long validationNs = System.nanoTime() - t2;
        long peakHeap = stopMemoryMonitor(peak);

        int violations = countViolations(report);
        if (saveReport) saveReport(report, approach.name().toLowerCase());
        return new RunResult(approach, round, loadNs, mergeNs, 0L, validationNs, violations, peakHeap);
    }

    /**
     * SHACL-DS Target Strategy: validates via {@code shds:targetGraph} /
     * {@code shds:targetGraphPattern} declarations.
     */
    private RunResult runShaclDsTg(Approach approach, Dataset dataDataset,
            int round, boolean saveReport, String shapesPath) throws IOException {
        long t0 = System.nanoTime();
        Dataset shapesDataset = DatasetFactory.create();
        RDFDataMgr.read(shapesDataset, shapesPath);
        long loadNs = System.nanoTime() - t0;

        AtomicLong peak = startMemoryMonitor();
        long t1 = System.nanoTime();
        Resource report = ValidationUtil.validateDataset(shapesDataset, dataDataset,
            new DatasetValidationEngineConfiguration().setValidateShapes(false));
        long validationNs = System.nanoTime() - t1;
        long peakHeap = stopMemoryMonitor(peak);

        int violations = countViolations(report);
        if (saveReport) saveReport(report, approach.name().toLowerCase());
        return new RunResult(approach, round, loadNs, 0L, 0L, validationNs, violations, peakHeap);
    }

    /**
     * SHACL-DS Combination Strategy: validates via {@code shds:targetGraphCombination}.
     * View creation is measured as a pre-pass and subtracted from the reported
     * validation time so the two costs are reported separately.
     */
    private RunResult runShaclDsCombo(Approach approach, Dataset dataDataset,
            int round, boolean saveReport, String shapesPath) throws IOException {
        long t0 = System.nanoTime();
        Dataset shapesDataset = DatasetFactory.create();
        RDFDataMgr.read(shapesDataset, shapesPath);
        long loadNs = System.nanoTime() - t0;

        // View creation pre-pass (isolation measurement)
        long t1 = System.nanoTime();
        List<CombinationPair> combPairs =
            TargetGraphSelector.resolveGraphCombinations(shapesDataset, dataDataset);
        for (CombinationPair cp : combPairs) {
            cp.combination().toModel(dataDataset);
        }
        long viewCreationNs = System.nanoTime() - t1;

        AtomicLong peak = startMemoryMonitor();
        long t2 = System.nanoTime();
        Resource report = ValidationUtil.validateDataset(shapesDataset, dataDataset,
            new DatasetValidationEngineConfiguration().setValidateShapes(false));
        long validationNs = System.nanoTime() - t2;
        long peakHeap = stopMemoryMonitor(peak);

        int violations = countViolations(report);
        if (saveReport) saveReport(report, approach.name().toLowerCase());
        return new RunResult(approach, round, loadNs, 0L, viewCreationNs, validationNs - viewCreationNs, violations, peakHeap);
    }

    // -------------------------------------------------------------------------
    // Report I/O
    // -------------------------------------------------------------------------

    private static int countViolations(Resource report) {
        if (report == null) return -1;
        return report.listProperties(SH.result).toList().size();
    }

    private void saveReport(Resource report, String label) throws IOException {
        if (report == null) return;
        String fileName = String.format("%s/%s-report.ttl", outputDir, label);
        try (FileOutputStream fos = new FileOutputStream(fileName)) {
            RDFDataMgr.write(fos, report.getModel(), RDFFormat.TURTLE_FLAT);
        }
    }

    // -------------------------------------------------------------------------
    // Statistics
    // -------------------------------------------------------------------------

    private static void printStats(Approach approach, List<RunResult> allResults) {
        List<RunResult> results = allResults.stream()
            .filter(r -> r.approach() == approach)
            .toList();

        log("--- %s (%d measured runs) ---", approach, results.size());

        printMetricStats("Load (ms)",
            results.stream().mapToDouble(r -> r.loadNs() / 1e6).toArray());

        if (approach == Approach.SHACL || approach == Approach.SHACL_FULL) {
            printMetricStats("Merge (ms)",
                results.stream().mapToDouble(r -> r.mergeNs() / 1e6).toArray());
        }
        if (approach == Approach.SHACL_DS_COMBO || approach == Approach.SHACL_DS_COMBO_EXTRA) {
            printMetricStats("View creation (ms)",
                results.stream().mapToDouble(r -> r.viewCreationNs() / 1e6).toArray());
        }

        printMetricStats("Validation (ms)",
            results.stream().mapToDouble(r -> r.validationNs() / 1e6).toArray());
        printMetricStats("Peak heap (MB)",
            results.stream().mapToDouble(r -> r.peakHeapBytes() / (1024.0 * 1024.0)).toArray());
        printMetricStats("Violations",
            results.stream().mapToDouble(RunResult::violations).toArray());

        log("");
    }

    private static void printMetricStats(String label, double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);

        double mean   = Arrays.stream(sorted).average().orElse(0.0);
        double median = percentile(sorted, 50);
        double min    = sorted[0];
        double max    = sorted[sorted.length - 1];
        double p95    = percentile(sorted, 95);
        double stddev = stddev(sorted, mean);

        log("  %-22s  mean=%9.3f  median=%9.3f  min=%9.3f  max=%9.3f  p95=%9.3f  stddev=%9.3f",
            label, mean, median, min, max, p95, stddev);
    }

    private static double percentile(double[] sorted, double p) {
        if (sorted.length == 0) return 0.0;
        double index = (p / 100.0) * (sorted.length - 1);
        int lo = (int) index;
        int hi = Math.min(lo + 1, sorted.length - 1);
        return sorted[lo] + (index - lo) * (sorted[hi] - sorted[lo]);
    }

    private static double stddev(double[] values, double mean) {
        double sum = 0.0;
        for (double v : values) sum += (v - mean) * (v - mean);
        return Math.sqrt(sum / values.length);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String formatExtra(RunResult r) {
        if (r.mergeNs() > 0)        return String.format("merge=%7.2f ms  ", r.mergeNs() / 1e6);
        if (r.viewCreationNs() > 0) return String.format("view=%7.2f ms   ", r.viewCreationNs() / 1e6);
        return "                 ";
    }

    private List<Approach> activeApproaches() {
        List<Approach> active = new ArrayList<>();
        if (shaclPath             != null) active.add(Approach.SHACL);
        if (shaclFullPath         != null) active.add(Approach.SHACL_FULL);
        if (shaclDsTgPath         != null) active.add(Approach.SHACL_DS_TG);
        if (shaclDsTgExtraPath    != null) active.add(Approach.SHACL_DS_TG_EXTRA);
        if (shaclDsComboPath      != null) active.add(Approach.SHACL_DS_COMBO);
        if (shaclDsComboExtraPath != null) active.add(Approach.SHACL_DS_COMBO_EXTRA);
        return active;
    }

    private void validate(List<Approach> activeApproaches) {
        if (datasetPath == null) {
            System.err.println("--dataset is required.");
            System.exit(1);
        }
        if (activeApproaches.isEmpty()) {
            System.err.println("At least one shapes path flag is required.");
            System.exit(1);
        }
    }

    private static void gc() {
        System.gc();
        System.gc();
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // -------------------------------------------------------------------------
    // Peak memory monitor
    // -------------------------------------------------------------------------

    /** Starts a background thread that polls JVM heap usage every 50 ms.
     *  Returns an AtomicLong that accumulates the peak observed value. */
    private static AtomicLong startMemoryMonitor() {
        AtomicLong peak = new AtomicLong(0);
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                long used = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
                peak.updateAndGet(current -> Math.max(current, used));
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
            }
        });
        t.setDaemon(true);
        t.start();
        return peak;
    }

    /** Interrupts the monitor thread and returns the peak heap value in bytes. */
    private static long stopMemoryMonitor(AtomicLong peak) {
        // The daemon thread will stop on its own; just return the captured peak.
        return peak.get();
    }

    // -------------------------------------------------------------------------
    // Logging
    // -------------------------------------------------------------------------

    private static void log(String fmt, Object... args) {
        System.out.println(args.length == 0 ? fmt : String.format(fmt, args));
    }

    // -------------------------------------------------------------------------
    // Argument parsing
    // -------------------------------------------------------------------------

    private void parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--dataset"                -> datasetPath          = args[++i];
                case "--shacl"                  -> shaclPath            = args[++i];
                case "--shacl-full"             -> shaclFullPath        = args[++i];
                case "--shacl-ds-tg"            -> shaclDsTgPath        = args[++i];
                case "--shacl-ds-tg-extra"      -> shaclDsTgExtraPath   = args[++i];
                case "--shacl-ds-combo"         -> shaclDsComboPath     = args[++i];
                case "--shacl-ds-combo-extra"   -> shaclDsComboExtraPath = args[++i];
                case "--warmup"                 -> warmupRounds         = Integer.parseInt(args[++i]);
                case "--measure"                -> measureRounds        = Integer.parseInt(args[++i]);
                case "--output"                 -> outputDir            = args[++i];
                default -> {
                    System.err.printf("Unknown argument: %s%n", args[i]);
                    System.exit(1);
                }
            }
        }
    }
}

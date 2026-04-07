import java.io.*;
import java.math.BigDecimal;
import java.util.*;

/**
 * Benchmarker for BinCoveringSolver.
 *
 * Usage:
 *   java BenchmarkBinCover input_folder_path
 *
 * Notes:
 * - Put this file in the same folder/package as bin_cover.java
 * - It uses the existing BinCoveringSolver class directly
 * - It validates every produced solution
 */
public class BenchmarkBinCover {

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java BenchmarkBinCover input_folder_path");
            System.exit(1);
        }

        String inputFolder = args[0];
        File inputDir = new File(inputFolder);

        if (!inputDir.exists() || !inputDir.isDirectory()) {
            throw new IllegalArgumentException("Input folder does not exist or is not a directory.");
        }

        File[] files = inputDir.listFiles();
        if (files == null) {
            throw new IOException("Could not read input folder.");
        }
        Arrays.sort(files, Comparator.comparing(File::getName));

        List<Instance> instances = new ArrayList<>();
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            instances.add(new Instance(file.getName(), readFile(file.getAbsolutePath())));
        }

        if (instances.isEmpty()) {
            throw new IllegalArgumentException("No input files found in: " + inputFolder);
        }

        // ----------------------------
        // Parameter sets to compare
        // ----------------------------
        List<SolverParams> configs = new ArrayList<>();
        configs.add(new SolverParams("n4", "0.71", "0.20", "0.14", "0.15"));
        configs.add(new SolverParams("n1", "0.69", "0.20", "0.14", "0.15"));
        configs.add(new SolverParams("n2", "0.70", "0.20", "0.13", "0.15"));
        configs.add(new SolverParams("n3", "0.70", "0.20", "0.15", "0.15"));
        configs.add(new SolverParams("n5", "0.70", "0.18", "0.14", "0.15"));
        configs.add(new SolverParams("n6", "0.70", "0.22", "0.14", "0.15"));
        configs.add(new SolverParams("n7", "0.70", "0.20", "0.14", "0.14"));
        configs.add(new SolverParams("n8", "0.70", "0.20", "0.14", "0.16"));

        System.out.println("Loaded instances: " + instances.size());
        System.out.println();

        // ----------------------------
        // Warm-up for JVM/JIT
        // ----------------------------
        int warmupRounds = 2;
        for (int w = 0; w < warmupRounds; w++) {
            for (SolverParams params : configs) {
                runBenchmark(instances, params, false);
            }
        }

        // ----------------------------
        // Measured runs
        // ----------------------------
        List<BenchmarkResult> results = new ArrayList<>();
        Map<String, int[]> perConfigScores = new LinkedHashMap<>();

        for (SolverParams params : configs) {
            BenchmarkResult result = runBenchmark(instances, params, true);
            results.add(result);

            int[] scores = solveAll(instances, params);
            perConfigScores.put(params.name, scores);
        }

        // ----------------------------
        // Compare vs baseline
        // ----------------------------
        String baselineName = configs.get(0).name;
        int[] baselineScores = perConfigScores.get(baselineName);

        for (BenchmarkResult result : results) {
            int[] currentScores = perConfigScores.get(result.params.name);

            for (int i = 0; i < currentScores.length; i++) {
                if (currentScores[i] > baselineScores[i]) {
                    result.winsAgainstBaseline++;
                } else if (currentScores[i] == baselineScores[i]) {
                    result.tiesAgainstBaseline++;
                } else {
                    result.lossesAgainstBaseline++;
                }
            }
        }

        // Rank by quality first, then by runtime
        results.sort((a, b) -> {
            int cmp = Long.compare(b.totalCoveredBins, a.totalCoveredBins);
            if (cmp != 0) return cmp;
            return Long.compare(a.totalNanos, b.totalNanos);
        });

        // ----------------------------
        // Print summary
        // ----------------------------
        System.out.println("=== BENCHMARK RESULTS ===");
        System.out.println();

        for (BenchmarkResult r : results) {
            System.out.println(r.params);
            System.out.println("  total covered bins: " + r.totalCoveredBins);
            System.out.println("  avg covered bins:   " + format4(r.avgCoveredBins()));
            System.out.println("  total time (ms):    " + format3(r.totalNanos / 1_000_000.0));
            System.out.println("  avg time/file (ms): " + format6(r.avgMillis()));
            System.out.println("  vs baseline:        wins=" + r.winsAgainstBaseline
                    + ", ties=" + r.tiesAgainstBaseline
                    + ", losses=" + r.lossesAgainstBaseline);
            System.out.println();
        }

        // Optional: print per-file best score
        System.out.println("=== PER-FILE BEST SCORE SUMMARY ===");
        int filesImprovedOverBaseline = 0;
        int filesEqualToBaseline = 0;
        int filesWorseThanBest = 0;

        for (int i = 0; i < baselineScores.length; i++) {
            int best = Integer.MIN_VALUE;
            for (int[] scores : perConfigScores.values()) {
                best = Math.max(best, scores[i]);
            }

            if (best > baselineScores[i]) {
                filesImprovedOverBaseline++;
            } else if (best == baselineScores[i]) {
                filesEqualToBaseline++;
            } else {
                filesWorseThanBest++;
            }
        }

        System.out.println("files where some config beats baseline: " + filesImprovedOverBaseline);
        System.out.println("files where baseline is best or tied best: " + filesEqualToBaseline);
        System.out.println();
    }

    private static BenchmarkResult runBenchmark(List<Instance> instances, SolverParams params, boolean measureTime) {
        BenchmarkResult result = new BenchmarkResult(params);

        for (Instance instance : instances) {
            BinCoveringSolver solver = new BinCoveringSolver(
                    params.largeItemThreshold,
                    params.largeReuseMinLoad,
                    params.largeItemMaxOvershoot,
                    params.largeItemMinBinLoad
            );

            long start = 0L;
            if (measureTime) {
                start = System.nanoTime();
            }

            List<List<Integer>> solution = solver.solve(instance.items);

            long end = 0L;
            if (measureTime) {
                end = System.nanoTime();
                result.totalNanos += (end - start);
            }

            if (!validateSolution(instance.items, solution)) {
                throw new IllegalStateException("Invalid solution for file: " + instance.name
                        + " with params: " + params.name);
            }

            result.totalCoveredBins += solution.size();
            result.totalFiles++;
        }

        return result;
    }

    private static int[] solveAll(List<Instance> instances, SolverParams params) {
        int[] scores = new int[instances.size()];

        for (int i = 0; i < instances.size(); i++) {
            BinCoveringSolver solver = new BinCoveringSolver(
                    params.largeItemThreshold,
                    params.largeReuseMinLoad,
                    params.largeItemMaxOvershoot,
                    params.largeItemMinBinLoad
            );

            List<List<Integer>> solution = solver.solve(instances.get(i).items);

            if (!validateSolution(instances.get(i).items, solution)) {
                throw new IllegalStateException("Invalid solution for file: " + instances.get(i).name
                        + " with params: " + params.name);
            }

            scores[i] = solution.size();
        }

        return scores;
    }

    private static List<BigDecimal> readFile(String path) throws IOException {
        List<String> tokens = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    tokens.add(line);
                }
            }
        }

        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("Input file is empty: " + path);
        }

        int n = Integer.parseInt(tokens.get(0));
        if (tokens.size() != n + 1) {
            throw new IllegalArgumentException("Expected " + n + " item values, got "
                    + (tokens.size() - 1) + " in file: " + path);
        }

        List<BigDecimal> items = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            items.add(new BigDecimal(tokens.get(i)));
        }

        return items;
    }

    private static boolean validateSolution(List<BigDecimal> items, List<List<Integer>> solution) {
        boolean[] used = new boolean[items.size() + 1];

        for (List<Integer> bin : solution) {
            BigDecimal total = BigDecimal.ZERO;

            for (int idx : bin) {
                if (idx < 1 || idx > items.size()) {
                    return false;
                }
                if (used[idx]) {
                    return false;
                }
                used[idx] = true;
                total = total.add(items.get(idx - 1));
            }

            if (total.compareTo(BigDecimal.ONE) < 0) {
                return false;
            }
        }

        return true;
    }

    private static String format3(double x) {
        return String.format(Locale.US, "%.3f", x);
    }

    private static String format4(double x) {
        return String.format(Locale.US, "%.4f", x);
    }

    private static String format6(double x) {
        return String.format(Locale.US, "%.6f", x);
    }
}

class SolverParams {
    final String name;
    final BigDecimal largeItemThreshold;
    final BigDecimal largeReuseMinLoad;
    final BigDecimal largeItemMaxOvershoot;
    final BigDecimal largeItemMinBinLoad;

    SolverParams(
            String name,
            String largeItemThreshold,
            String largeReuseMinLoad,
            String largeItemMaxOvershoot,
            String largeItemMinBinLoad
    ) {
        this.name = name;
        this.largeItemThreshold = new BigDecimal(largeItemThreshold);
        this.largeReuseMinLoad = new BigDecimal(largeReuseMinLoad);
        this.largeItemMaxOvershoot = new BigDecimal(largeItemMaxOvershoot);
        this.largeItemMinBinLoad = new BigDecimal(largeItemMinBinLoad);
    }

    @Override
    public String toString() {
        return name
                + " [threshold=" + largeItemThreshold.toPlainString()
                + ", reuseMinLoad=" + largeReuseMinLoad.toPlainString()
                + ", maxOvershoot=" + largeItemMaxOvershoot.toPlainString()
                + ", minBinLoad=" + largeItemMinBinLoad.toPlainString()
                + "]";
    }
}

class BenchmarkResult {
    final SolverParams params;
    long totalCoveredBins = 0;
    long totalFiles = 0;
    long totalNanos = 0;
    long winsAgainstBaseline = 0;
    long tiesAgainstBaseline = 0;
    long lossesAgainstBaseline = 0;

    BenchmarkResult(SolverParams params) {
        this.params = params;
    }

    double avgCoveredBins() {
        if (totalFiles == 0) return 0.0;
        return (double) totalCoveredBins / totalFiles;
    }

    double avgMillis() {
        if (totalFiles == 0) return 0.0;
        return (totalNanos / 1_000_000.0) / totalFiles;
    }
}

class Instance {
    final String name;
    final List<BigDecimal> items;

    Instance(String name, List<BigDecimal> items) {
        this.name = name;
        this.items = items;
    }
}
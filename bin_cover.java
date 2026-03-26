import java.io.*;
import java.math.BigDecimal;
import java.util.*;

/**
 * Online 1D bin covering solver.
 *
 * Heuristic:
 * 1) If the incoming item can cover one or more open bins, place it into the fullest such bin.
 * 2) Otherwise place it into the fullest unfinished bin.
 * 3) Large items are protected: if no promising unfinished bin exists, start a new bin.
 *
 * Output:
 *   first line: number of covered bins
 *   next lines: item indices in each covered bin
 */
public class bin_cover {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: java bin_cover input_folder_path output_folder_path");
            System.exit(1);
        }

        String input_folder = args[0];
        String output_folder = args[1];

        File inputDir = new File(input_folder);
        File outputDir = new File(output_folder);

        if (!inputDir.exists() || !inputDir.isDirectory()) {
            throw new IllegalArgumentException("Input folder does not exist or is not a directory.");
        }

        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        File[] files = inputDir.listFiles();
        Arrays.sort(files, Comparator.comparing(File::getName)); // sort input files by name for consistency
        if (files == null) {
            throw new IOException("Could not read input folder.");
        }

        for (File inputFile : files) {
            if (!inputFile.isFile()) {
                continue;
            }

            BinCoveringSolver solver = new BinCoveringSolver(
                    new BigDecimal("0.5"),
                    new BigDecimal("0.25")
            );

            List<BigDecimal> items = readInstance(inputFile.getAbsolutePath());
            List<List<Integer>> solution = solver.solve(items);

            String outputFilePath = new File(
                    output_folder,
                    inputFile.getName().replace("input", "output")
            ).getAbsolutePath();

            writeSolution(outputFilePath, solution);
        }
    }

    private static List<BigDecimal> readInstance(String path) throws IOException {
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
            throw new IllegalArgumentException("Input file is empty.");
        }

        int n = Integer.parseInt(tokens.get(0));
        if (tokens.size() != n + 1) {
            throw new IllegalArgumentException("Expected " + n + " item values, got " + (tokens.size() - 1));
        }

        List<BigDecimal> items = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            items.add(new BigDecimal(tokens.get(i)));
        }

        return items;
    }

    private static void writeSolution(String path, List<List<Integer>> coveredBins) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(path))) {
            out.println(coveredBins.size());
            for (List<Integer> bin : coveredBins) {
                for (int i = 0; i < bin.size(); i++) {
                    if (i > 0) out.print(" ");
                    out.print(bin.get(i));
                }
                out.println();
            }
        }
    }
}

class BinCoveringSolver {
    private static final BigDecimal ONE = BigDecimal.ONE;

    private final BigDecimal largeItemThreshold;
    private final BigDecimal largeReuseMinLoad;

    private final List<Bin> bins = new ArrayList<>();
    private final List<Integer> openBins = new ArrayList<>();
    private final List<Integer> coveredBins = new ArrayList<>();

    public BinCoveringSolver(BigDecimal largeItemThreshold, BigDecimal largeReuseMinLoad) {
        this.largeItemThreshold = largeItemThreshold;
        this.largeReuseMinLoad = largeReuseMinLoad;
    }

    public List<List<Integer>> solve(List<BigDecimal> items) {
        // Initialize state for a new instance
        bins.clear();
        openBins.clear();
        coveredBins.clear();

        for (int i = 0; i < items.size(); i++) {
            int itemIndex = i + 1; // 1-based indexing for output
            BigDecimal itemSize = items.get(i);
            placeItem(itemIndex, itemSize);
        }

        List<List<Integer>> result = new ArrayList<>();
        for (int binId : coveredBins) {
            result.add(new ArrayList<>(bins.get(binId).itemIndices));
        }
        return result;
    }

    private void placeItem(int itemIndex, BigDecimal itemSize) {
        Integer chosenBinId = chooseBin(itemIndex, itemSize);

        if (chosenBinId == null) {
            chosenBinId = openNewBin();
        }

        Bin bin = bins.get(chosenBinId);
        bin.itemIndices.add(itemIndex);
        bin.total = bin.total.add(itemSize);

        if (!bin.covered && bin.total.compareTo(ONE) >= 0) {
            bin.covered = true;
            coveredBins.add(chosenBinId);
            openBins.remove(Integer.valueOf(chosenBinId));
        }
    }

    private Integer chooseBin(int itemIndex, BigDecimal itemSize) {
        Integer bestAcceptedCoverBin = null;
        BigDecimal bestAcceptedOvershoot = null;

        Integer bestForcedCoverBin = null;
        BigDecimal bestForcedOvershoot = null;

        Integer bestFillBin = null;
        BigDecimal bestFillScore = null;

        BigDecimal coverLimit = coverOvershootLimit(itemSize);
        BigDecimal targetLoad = targetLoadAfterPlacement(itemSize);

        for (int binId : openBins) {
            Bin bin = bins.get(binId);

            if (!canPlace(bin, itemSize, itemIndex)) {
                continue;
            }

            BigDecimal newTotal = bin.total.add(itemSize);

            if (newTotal.compareTo(ONE) >= 0) {
                BigDecimal overshoot = newTotal.subtract(ONE);

                // Keep track of the minimum-overshoot cover no matter what
                if (bestForcedCoverBin == null || overshoot.compareTo(bestForcedOvershoot) < 0) {
                    bestForcedCoverBin = binId;
                    bestForcedOvershoot = overshoot;
                }

                // Accept cover only if overshoot is small enough
                if (overshoot.compareTo(coverLimit) <= 0) {
                    if (bestAcceptedCoverBin == null || overshoot.compareTo(bestAcceptedOvershoot) < 0) {
                        bestAcceptedCoverBin = binId;
                        bestAcceptedOvershoot = overshoot;
                    }
                }
            } else {
                // Non-cover placement: prefer bins whose new load is close to a target
                BigDecimal score = fillScore(bin.total, itemSize, newTotal, targetLoad);

                if (bestFillBin == null || score.compareTo(bestFillScore) > 0) {
                    bestFillBin = binId;
                    bestFillScore = score;
                }
            }
        }

        // First priority: good cover with limited overshoot
        if (bestAcceptedCoverBin != null) {
            return bestAcceptedCoverBin;
        }

        // If too many bins are open, force the least-wasteful cover
        if (openBins.size() >= 12 && bestForcedCoverBin != null) {
            return bestForcedCoverBin;
        }

        // Large items are often better as anchors for new bins
        if (itemSize.compareTo(new BigDecimal("0.75")) >= 0) {
            if (bestFillBin != null) {
                Bin fillBin = bins.get(bestFillBin);
                BigDecimal newTotal = fillBin.total.add(itemSize);

                // Only reuse an existing bin if it creates a promising partial bin
                if (newTotal.compareTo(new BigDecimal("0.80")) >= 0
                        && newTotal.compareTo(new BigDecimal("0.98")) <= 0) {
                    return bestFillBin;
                }
            }
            return null; // open new bin
        }

        // For smaller items, use the best non-cover fit if it looks reasonable
        if (bestFillBin != null && bestFillScore.compareTo(new BigDecimal("-0.30")) > 0) {
            return bestFillBin;
        }

        // Otherwise, if a cover exists at all, use the least wasteful one
        if (bestForcedCoverBin != null) {
            return bestForcedCoverBin;
        }

        return null;
    }

    private BigDecimal coverOvershootLimit(BigDecimal itemSize) {
        if (itemSize.compareTo(new BigDecimal("0.10")) <= 0) {
            return new BigDecimal("0.20");
        }
        if (itemSize.compareTo(new BigDecimal("0.30")) <= 0) {
            return new BigDecimal("0.12");
        }
        if (itemSize.compareTo(new BigDecimal("0.60")) <= 0) {
            return new BigDecimal("0.08");
        }
        if (itemSize.compareTo(new BigDecimal("0.75")) <= 0) {
            return new BigDecimal("0.05");
        }
        return new BigDecimal("0.03");
    }

    private BigDecimal targetLoadAfterPlacement(BigDecimal itemSize) {
        if (itemSize.compareTo(new BigDecimal("0.75")) >= 0) {
            return new BigDecimal("0.85");
        }
        if (itemSize.compareTo(new BigDecimal("0.50")) >= 0) {
            return new BigDecimal("0.90");
        }
        if (itemSize.compareTo(new BigDecimal("0.20")) >= 0) {
            return new BigDecimal("0.96");
        }
        return new BigDecimal("0.995");
    }

    private BigDecimal fillScore(BigDecimal currentLoad,
                                BigDecimal itemSize,
                                BigDecimal newTotal,
                                BigDecimal targetLoad) {
        // Higher is better

        // Prefer getting close to the target load
        BigDecimal distance = newTotal.subtract(targetLoad).abs();
        BigDecimal score = distance.negate();

        // Small bonus for strengthening an existing bin instead of making many weak bins
        if (currentLoad.compareTo(BigDecimal.ZERO) > 0) {
            score = score.add(new BigDecimal("0.05"));
        }

        // Penalize leaving bins in an awkward middle state
        if (newTotal.compareTo(new BigDecimal("0.45")) >= 0 &&
            newTotal.compareTo(new BigDecimal("0.70")) <= 0) {
            score = score.subtract(new BigDecimal("0.08"));
        }

        return score;
    }
    /**
     * Hook for extra rules.
     * Override this logic if your bins have additional constraints.
     */
    protected boolean canPlace(Bin bin, BigDecimal itemSize, int itemIndex) {
        return true;
    }

    private int openNewBin() {
        int id = bins.size();
        bins.add(new Bin());
        openBins.add(id);
        return id;
    }
}

class Bin {
    List<Integer> itemIndices = new ArrayList<>();
    BigDecimal total = BigDecimal.ZERO;
    boolean covered = false;
}
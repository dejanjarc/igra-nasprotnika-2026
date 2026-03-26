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
        Integer bestCoverBin = null;
        BigDecimal bestCoverLoad = null;

        Integer bestFillBin = null;
        BigDecimal bestFillLoad = null;

        for (int binId : openBins) {
            Bin bin = bins.get(binId);

            if (!canPlace(bin, itemSize, itemIndex)) {
                continue;
            }

            BigDecimal newTotal = bin.total.add(itemSize);

            if (newTotal.compareTo(ONE) >= 0) {
                // Best cover: choose the fullest bin that becomes covered
                if (bestCoverBin == null || bin.total.compareTo(bestCoverLoad) > 0) {
                    bestCoverBin = binId;
                    bestCoverLoad = bin.total;
                }
            } else {
                // Best unfinished fill: choose the fullest unfinished bin
                if (bestFillBin == null || bin.total.compareTo(bestFillLoad) > 0) {
                    bestFillBin = binId;
                    bestFillLoad = bin.total;
                }
            }
        }

        if (bestCoverBin != null) {
            return bestCoverBin;
        }

        // Large item protection:
        // use a large item to strengthen an existing bin only if that bin already looks promising
        if (itemSize.compareTo(largeItemThreshold) >= 0) {
            if (bestFillBin != null && bestFillLoad.compareTo(largeReuseMinLoad) >= 0) {
                return bestFillBin;
            }
            return null;
        }

        if (bestFillBin != null) {
            return bestFillBin;
        }

        return null;
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
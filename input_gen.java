import java.io.*;
import java.math.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.nio.file.*;

public class input_gen {
    public static void main(String[] args) throws IOException{
        if (args.length != 2) {
            System.err.println("Usage: java input_gen n_generated_input_files max_input_size");
            System.exit(1);
        }

        int n = Integer.parseInt(args[0]); // number of input files to generate
        int max_input_size = Integer.parseInt(args[1]); // maximum number of items in each generated input file
        int current_input_file_index = 0;

        // Create input folder if it doesn't exist
        String input_folder_path = "./generated_inputs";
        Path input_folder = Paths.get(input_folder_path);
        if (!Files.exists(input_folder)){
            Files.createDirectory(input_folder);
        }

        for (int file_index = 0; file_index < n; file_index++) {
            List<BigDecimal> items = new ArrayList<>(); // list to hold generated item values
            int num_items = ThreadLocalRandom.current().nextInt(1, max_input_size + 1); // random number of items between 1 and max_input_size

            for(int item_index = 0; item_index < num_items; item_index++) {
                // Generate a random item value between 0.000000000000001 and 1.000000000000000
                long scaledValue = ThreadLocalRandom.current().nextLong(1, 1_000_000_000_000_001L);
                BigDecimal item = BigDecimal.valueOf(scaledValue, 15);
                items.add(item);
            }

            String input_file_name = "input_" + current_input_file_index + ".txt";

            try (PrintWriter out = new PrintWriter(new FileWriter(new File(input_folder_path, input_file_name)))) {
                out.println(items.size());
                for (BigDecimal item : items) {
                    out.println(item.toPlainString());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            current_input_file_index++;
        }
        
    }
}
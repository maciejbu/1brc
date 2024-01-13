package dev.morling.onebrc;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class CalculateAverage_maciej {
    private static final String FILE = "./measurements.txt";

    public static void main(String[] args) throws IOException {
        long start = System.nanoTime();
        Path p = Path.of(args.length == 1 ? args[0] : FILE);
        OutputStreamWriter writer = new OutputStreamWriter(System.out);
        run(p, writer);
        writer.flush();
        long end = System.nanoTime();
        long diffMs = TimeUnit.NANOSECONDS.toMillis(end - start);
        System.out.println("Took: " + diffMs);
    }

    private enum ReadingMode {NAME, TEMPERATURE};

    public static void run(Path inputPath, Writer writer) {
        try (var file = FileChannel.open(inputPath, StandardOpenOption.READ);
             var arena = Arena.ofConfined()) {
            var memorySegment = file.map(FileChannel.MapMode.READ_ONLY, 0, file.size(), arena);
            String name = null;
            int sbPosition = 0;
            long temperatureBuffer = 0;
            byte[] sb = new byte[100];
            Map<String, Stats> data = new HashMap<>();
            var mode = ReadingMode.NAME;
            long byteSize = memorySegment.byteSize();
            boolean negative = false;
            for (long i = 0; i < byteSize; i++) {
                byte b = memorySegment.get(ValueLayout.JAVA_BYTE, i);
                if (b == ';') {
                    mode = ReadingMode.TEMPERATURE;
                    name = new String(sb, 0, sbPosition);
                    sbPosition = 0;
                } else if (b == '\n') {
                    mode = ReadingMode.NAME;
                    var stats = data.get(name);

                    if(negative) {
                        temperatureBuffer *= -1;
                    }

                    if (stats == null) {
                        stats = new Stats();
                        stats.count = 1;
                        stats.sum = temperatureBuffer;
                        stats.min = temperatureBuffer;
                        stats.max = temperatureBuffer;
                        data.put(name, stats);
                    } else {
                        stats.count++;
                        stats.min = Math.min(stats.min, temperatureBuffer);
                        stats.max = Math.max(stats.max, temperatureBuffer);
                        stats.sum = stats.sum + temperatureBuffer;
                    }
                    temperatureBuffer = 0;
                    negative = false;
                } else if(b == '.') {
                    if(mode == ReadingMode.NAME) {
                        sb[sbPosition] = b;
                        sbPosition++;
                    }
                } else if(b == '-') {
                    if(mode == ReadingMode.NAME) {
                        sb[sbPosition] = b;
                        sbPosition++;
                    } else {
                        negative = true;
                    }
                } else {
                    if(mode == ReadingMode.TEMPERATURE) {
                        temperatureBuffer *= 10;
                        int digit = b - (byte) '0';
                        temperatureBuffer += digit;
                    } else {
                        sb[sbPosition] = b;
                        sbPosition++;
                    }
                }
            }

            writer.write("{");
            List<String> list = data.keySet().stream().sorted().toList();
            int size = list.size();
            for (String key : list) {
                size--;
                Stats stats = data.get(key);
                writer.write(key);
                writer.write("=");
                writer.write(String.format("%.1f", stats.min / 10.0));
                writer.write("/");
                writer.write(String.format("%.1f", 0.1 * stats.sum / stats.count));
                writer.write("/");
                writer.write(String.format("%.1f", stats.max / 10d));
                if (size != 0) {
                    writer.write(", ");
                }
            }
            ;
            writer.write("}\n");


        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    private static class Stats {
        private long min;
        private long max;
        private long count;
        private long sum;
    }
}

package dev.morling.onebrc;

import java.io.*;
import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    private enum ReadingMode {NAME, TEMPERATURE}

    public static void run(Path inputPath, Writer writer) {
        try (var file = FileChannel.open(inputPath, StandardOpenOption.READ);
             var arena = Arena.ofConfined()) {
            var memorySegment = file.map(FileChannel.MapMode.READ_ONLY, 0, file.size(), arena);
            long temperatureBuffer = 0;
            MyString stringBuffer = new MyString();
            Map<MyString, Stats> data = new HashMap<>();
            var mode = ReadingMode.NAME;
            long byteSize = memorySegment.byteSize();
            boolean negative = false;
            for (long i = 0; i < byteSize; i++) {
                byte b = memorySegment.get(ValueLayout.JAVA_BYTE, i);
                if (b == ';') {
                    mode = ReadingMode.TEMPERATURE;
                } else if (b == '\n') {
                    mode = ReadingMode.NAME;
                    var stats = data.get(stringBuffer);
                    if(negative) {
                        temperatureBuffer *= -1;
                    }

                    if (stats == null) {
                        stats = new Stats();
                        stats.count = 1;
                        stats.sum = temperatureBuffer;
                        stats.min = temperatureBuffer;
                        stats.max = temperatureBuffer;
                        data.put(stringBuffer.copy(), stats);
                    } else {
                        stats.count++;
                        stats.min = Math.min(stats.min, temperatureBuffer);
                        stats.max = Math.max(stats.max, temperatureBuffer);
                        stats.sum = stats.sum + temperatureBuffer;
                    }
                    temperatureBuffer = 0;
                    negative = false;
                    stringBuffer.reset();
                } else if(b == '.') {
                    if(mode == ReadingMode.NAME) {
                        stringBuffer.append(b);
                    }
                } else if(b == '-') {
                    if(mode == ReadingMode.NAME) {
                        stringBuffer.append(b);
                    } else {
                        negative = true;
                    }
                } else {
                    if(mode == ReadingMode.TEMPERATURE) {
                        temperatureBuffer *= 10;
                        int digit = b - (byte) '0';
                        temperatureBuffer += digit;
                    } else {
                        stringBuffer.append(b);
                    }
                }
            }

            writer.write("{");

            Map<String, Stats> result = new TreeMap<>(data.entrySet().stream().collect(Collectors.toMap(me -> me.getKey().toString(), Map.Entry::getValue)));
            int size = result.size();
            for (String key : result.keySet().stream().sorted().toList()) {
                size--;
                Stats stats = result.get(key);
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

    private static class MyString {
        byte[] data;
        int size;

        MyString() {
            this.data = new byte[100];
            this.size = 0;
        }

        MyString(byte[] data) {
            this.data = data;
            size = data.length;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MyString that = (MyString) o;
            return size == that.size && Arrays.equals(data, 0, size,  that.data, 0, that.size);
        }

        @Override
        public int hashCode() {
            return switch (data.length) {
                case 0 -> 1;
                case 1 -> 31 + (int)data[0];
                //default -> ArraysSupport.vectorizedHashCode(data, 0, size, 1, ArraysSupport.T_BYTE);
               default -> {
                    int result = 1;
                    for (int i = 0; i < size; i++) {
                        result = 31 * result + data[i];
                    }
                    yield result;
                }
            };
        }

        public void append(byte b) {
            data[size] = b;
            size++;
        }

        public void reset() {
            this.size = 0;
        }

        public MyString copy() {
            return new MyString(Arrays.copyOf(this.data, this.size));
        }

        @Override
        public String toString() {
            return new String(data, 0, size, StandardCharsets.UTF_8);
        }
    }
}

package info.kgeorgiy.ja.tkachenko.walk;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Walk {
    public static void main(String[] args) {

        try {
            checkArgs(args);
            Path in = inputPath(args[0]);
            Path out = outputPath(args[1]);
            walk(in, out);
        } catch (WalkException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static void checkArgs(String[] args) throws WalkException {
        if (args == null || args.length != 2 || args[0] == null || args[1] == null) {
            throw new WalkException("Invalid program parameters. " +
                    "The correct run format is \"java Walk <input file> <output file>\"");
        }
    }

    private static Path inputPath(String inputFileName) throws WalkException {
        try {
            return Paths.get(inputFileName);
        } catch (InvalidPathException e) {
            throw new WalkException("Path " + inputFileName + " is invalid", e);
        }
    }

    private static Path outputPath(String outputFileName) throws WalkException {
        try {
            Path path = Paths.get(outputFileName);
            if (Files.notExists(path)) {
                if (path.getParent() != null) {
                    Files.createDirectories(path.getParent());
                    return path;
                } else {
                    throw new WalkException("Parent directory of path " + outputFileName + " does not exist.");
                }
            }
            return path;

        } catch (InvalidPathException e) {
            throw new WalkException("Path " + outputFileName + " is invalid", e);
        } catch (IOException e) {
            throw new WalkException("Could not create directory", e);
        }
    }

    private static void walk(Path inputPath, Path outputPath) throws WalkException {
        try (BufferedReader bufferedReader = Files.newBufferedReader(inputPath, StandardCharsets.UTF_8)) {
            try (BufferedWriter bufferedWriter = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    try {
                        Path path = Paths.get(line);
                        long hash = hashPJW(path);
                        writeHash(bufferedWriter, hash, line);
                    } catch (InvalidPathException e) {
                        System.err.println("Invalid path " + line + ": " + e.getMessage());
                        writeHash(bufferedWriter, 0, line);
                    } catch (IOException e) {
                        System.err.println("Error while scanning file " + line + ": " + e.getMessage());
                        writeHash(bufferedWriter, 0, line);
                    }
                }
            } catch (SecurityException e) {
                throw new WalkException("Not enough rights to write to output file", e);
            } catch (FileNotFoundException e) {
                throw new WalkException("Output file not found", e);
            } catch (IOException e) {
                throw new WalkException("Error while opening output file", e);
            }
        } catch (SecurityException e) {
            throw new WalkException("Not enough rights to read from input file", e);
        } catch (FileNotFoundException e) {
            throw new WalkException("Input file not found", e);
        } catch (IOException e) {
            throw new WalkException("Error while opening input file", e);
        }
    }

    private static void writeHash(BufferedWriter bw, long hash, String line) throws IOException {
        bw.write(String.format("%016x", hash) + " " + line + '\n');
    }


    private static long hashPJW(final Path path) throws IOException { // like ELF hash but for 64 bit
        try (InputStream inputStream = Files.newInputStream(path)) {
            int buffSize = 2048;
            byte[] buff = new byte[buffSize];
            long hash = 0, high, length;
            while ((length = inputStream.read(buff)) != -1) {
                for (int i = 0; i < length; i++) {
                    hash = (hash << 8) + (buff[i] & 0xff);
                    high = hash & 0xFF00_0000_0000_0000L;
                    if (high != 0) {
                        hash ^= high >> 48;
                        hash &= ~high;
                    }
                }
            }
            return hash;
        }
    }
}

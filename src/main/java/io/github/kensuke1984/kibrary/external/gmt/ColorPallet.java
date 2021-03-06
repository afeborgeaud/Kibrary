package io.github.kensuke1984.kibrary.external.gmt;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.*;

/**
 * Color pallet for GMT.
 *
 * @author Kensuke Konishi
 * @version 0.0.6
 */
public interface ColorPallet {

    /**
     * If there is no min value, then the range is [-max, max]
     *
     * @param args [write file name](Option min value of range) [max value of
     *             range]
     * @throws IOException if an I/O error occurs
     */
    static void main(String[] args) throws IOException {
        if (args.length < 2)
            throw new IllegalArgumentException("Usage: [write file name] (Option min value) [max value of range]");
        Path path = Paths.get(args[0]);
        if (2 < args.length) oobayashi(Double.parseDouble(args[1]), Double.parseDouble(args[2]))
                .write(path, StandardOpenOption.CREATE_NEW);
        else oobayashi(Double.parseDouble(args[1])).write(path, StandardOpenOption.CREATE_NEW);
    }

    /**
     * write the color pallet
     *
     * @param min min value of range
     * @param max max value of range
     * @return Color pallete by Oobayashi
     */
    static ColorPallet oobayashi(double min, double max) {
        if (max <= min) throw new IllegalArgumentException("Input values are invalid");
        // -red blue+
        ColorPallet oobayashi = new ColorPallet() {
            private final int[][] rgb =
                    {{129, 14, 30}, {158, 15, 9}, {218, 20, 7}, {241, 80, 29}, {244, 129, 17}, {247, 220, 100},
                            {247, 238, 159}, {245, 247, 198}, {253, 253, 253}, {236, 246, 247}, {212, 237, 239},
                            {117, 201, 222}, {64, 172, 197}, {44, 144, 169}, {59, 80, 129}, {30, 46, 110},
                            {17, 46, 85},};

            @Override
            public String toRGB(double value) {
                double range = max - min;
                for (int i = 0; i < 17; i++) {
                    double start = min + i / 17.0 * range;
                    double end = min + (i + 1.0) / 17.0 * range;
                    if (start <= value && value < end) return rgb[i][0] + "/" + rgb[i][1] + "/" + rgb[i][2];
                    // System.out.println(cpt[i]);
                }
                throw new IllegalArgumentException("Input value: " + value + " is out of range.");
            }

            @Override
            public void write(Path outPath, OpenOption... options) throws IOException {
                try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, options))) {
                    // -red blue+
                    double range = max - min;
                    for (int i = 0; i < 17; i++) {
                        double start = min + i / 17.0 * range;
                        double end = min + (i + 1.0) / 17.0 * range;
                        String rgbPart = " " + rgb[i][0] + " " + rgb[i][1] + "  " + rgb[i][2] + " ";
                        pw.println(start + rgbPart + end + rgbPart);
                    }
                    pw.println("F " + rgb[0][0] + " " + rgb[0][1] + "  " + rgb[0][2]);
                    pw.println("B " + rgb[16][0] + " " + rgb[16][1] + "  " + rgb[16][2]);
                    pw.println("N 0 0 0");
                }

            }
        };

        return oobayashi;
    }

    /**
     * write the color pallet
     *
     * @param max max value of range
     * @return Color pallet by Oobayashi
     */
    static ColorPallet oobayashi(double max) {
        return oobayashi(-max, max);
    }

    /**
     * @param outPath {@link Path} of an write file. This is supposed to be used
     *                GMT color pallet.
     * @param options for write
     * @throws IOException if an I/O error occurs
     */
    void write(Path outPath, OpenOption... options) throws IOException;

    /**
     * @param value target value for the RGB
     * @return R/G/B can be directly used in -G option
     */
    String toRGB(double value);

}

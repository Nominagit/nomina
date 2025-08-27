package util;

import dev.brachtendorf.jimagehash.hash.Hash;
import dev.brachtendorf.jimagehash.hashAlgorithms.DifferenceHash;
import dev.brachtendorf.jimagehash.hashAlgorithms.PerceptiveHash;

import java.awt.image.BufferedImage;
import java.math.BigInteger;

public final class HashUtil {

    private static final PerceptiveHash P_HASH = new PerceptiveHash(64);
    private static final DifferenceHash D_HASH = new DifferenceHash(64, DifferenceHash.Precision.Double);

    private HashUtil() {}

    public static BigInteger pHash64(BufferedImage img) {
        Hash h = P_HASH.hash(img);
        return h.getHashValue(); // 64-bit packed value
    }

    public static BigInteger dHash64(BufferedImage img) {
        Hash h = D_HASH.hash(img);
        return h.getHashValue();
    }

    public static int hammingDistance(BigInteger a, BigInteger b) {
        // XOR → bits that differ become 1
        BigInteger xor = a.xor(b);

        // Count the number of 1 bits
        return xor.bitCount();
    }

    // Check similarity based on threshold (0.0 to 1.0)
    public static boolean isSimilar(BigInteger a, BigInteger b, double threshold) {
        int distance = hammingDistance(a, b);

        // Max bits = max number of bits in either BigInteger
        int maxBits = Math.max(a.bitLength(), b.bitLength());

        if (maxBits == 0) return true; // both zero → identical

        double similarity = 1.0 - ((double) distance / maxBits);

        return similarity >= threshold;
    }
}

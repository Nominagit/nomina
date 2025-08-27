import dev.brachtendorf.jimagehash.hash.Hash;
import dev.brachtendorf.jimagehash.hashAlgorithms.AverageHash;
import dev.brachtendorf.jimagehash.hashAlgorithms.DifferenceHash;
import dev.brachtendorf.jimagehash.hashAlgorithms.PerceptiveHash;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import util.HashUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigInteger;

public class Main {
    public static void main(String[] args) throws Exception {

        // 1️⃣ Load images from desktop
        BufferedImage img1 = ImageIO.read(new File("C:\\Users\\VaheA\\Desktop\\gucci.jpg"));
        BufferedImage img2 = ImageIO.read(new File("C:\\Users\\VaheA\\Desktop\\channel.jpg"));

        // 2️⃣ Initialize hash algorithms
        PerceptiveHash pHash = new PerceptiveHash(128);    // robust
        DifferenceHash dHash = new DifferenceHash(128, DifferenceHash.Precision.Triple);

        // 3️⃣ Compute hashes
        Hash pHash1 = pHash.hash(img1);
        Hash pHash2 = pHash.hash(img2);

        Hash dHash1 = dHash.hash(img1);
        Hash dHash2 = dHash.hash(img2);

        // Optional: get BigInteger values
        BigInteger pHashValue1 = pHash1.getHashValue();
        BigInteger pHashValue2 = pHash2.getHashValue();

        BigInteger dHashValue1 = dHash1.getHashValue();
        BigInteger dHashValue2 = dHash2.getHashValue();

        System.out.println("pHash1: " + pHashValue1);
        System.out.println("pHash2: " + pHashValue2);
        System.out.println("dHash1: " + dHashValue1);
        System.out.println("dHash2: " + dHashValue2);

        double similarityP = 1.0 - pHash1.normalizedHammingDistance(pHash2);
        double similarityD = 1.0 - dHash1.normalizedHammingDistance(dHash2);

        System.out.println("PerceptiveHash similarity: " + (similarityP * 100) + "%");
        System.out.println("DifferenceHash similarity: " + (similarityD * 100) + "%");

        double threshold = 0.5;
        if (similarityP >= threshold || similarityD >= threshold) {
            System.out.println("Images are considered similar.");
        } else {
            System.out.println("Images are different.");
        }
    }
}

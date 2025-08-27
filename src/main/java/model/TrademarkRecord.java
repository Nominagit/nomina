package model;

import java.math.BigInteger;
import java.util.Map;

public class TrademarkRecord {
    private int id;                     // Auto-increment ID
    private String fullId;              // prefix + suffix
    private String markName;            // extracted from JSON
    private Map<String, String> data;   // Full scraped data
    private String imagePath;           // Local file path to logo

    // ✅ Store multiple hashes
    private BigInteger perceptiveHash;  // From pHash (robust)
    private BigInteger differenceHash;  // From dHash (resizing robustness)

    public TrademarkRecord(int id, String fullId, String markName,
                           Map<String, String> data, String imagePath,
                           BigInteger perceptiveHash, BigInteger differenceHash) {
        this.id = id;
        this.fullId = fullId;
        this.markName = markName;
        this.data = data;
        this.imagePath = imagePath;
        this.perceptiveHash = perceptiveHash;
        this.differenceHash = differenceHash;
    }

    public int getId() {
        return id;
    }

    public String getFullId() {
        return fullId;
    }

    public String getMarkName() {
        return markName;
    }

    public Map<String, String> getData() {
        return data;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public BigInteger getPerceptiveHash() {
        return perceptiveHash;
    }

    public void setPerceptiveHash(BigInteger perceptiveHash) {
        this.perceptiveHash = perceptiveHash;
    }

    public BigInteger getDifferenceHash() {
        return differenceHash;
    }

    public void setDifferenceHash(BigInteger differenceHash) {
        this.differenceHash = differenceHash;
    }

    @Override
    public String toString() {
        return "TrademarkRecord{" +
                "id=" + id +
                ", fullId='" + fullId + '\'' +
                ", markName='" + markName + '\'' +
                ", data=" + data +
                ", imagePath='" + imagePath + '\'' +
                ", perceptiveHash=" + perceptiveHash +
                ", differenceHash=" + differenceHash +
                '}';
    }
}

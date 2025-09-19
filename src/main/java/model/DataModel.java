package model;

import java.math.BigInteger;
import java.util.Map;

public class DataModel {
    private int id;                     // Auto-increment ID
    private String fullId;              // prefix + suffix
    private String markName;            // extracted from JSON

    private String type;
    private String link;
    private Map<String, String> data;   // Full scraped data
    private String imagePath;           // Local file path to logo
    private BigInteger perceptiveHash;  // From pHash (robust)
    private BigInteger differenceHash;  // From dHash (resizing robustness)

    public DataModel(int id, String fullId, String markName,
                     Map<String, String> data, String imagePath, String link,
                     BigInteger perceptiveHash, BigInteger differenceHash) {
        this.id = id;
        this.fullId = fullId;
        this.markName = markName;
        this.data = data;
        this.link = link;
        this.imagePath = imagePath;
        this.perceptiveHash = perceptiveHash;
        this.differenceHash = differenceHash;
    }

    public DataModel() {
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

    public void setFullId(String fullId) {
        this.fullId = fullId;
    }

    public void setMarkName(String markName) {
        this.markName = markName;
    }

    public void setData(Map<String, String> data) {
        this.data = data;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return "DataModel{" +
                "id=" + id +
                ", fullId='" + fullId + '\'' +
                ", markName='" + markName + '\'' +
                ", link='" + link + '\'' +
                ", data=" + data +
                ", imagePath='" + imagePath + '\'' +
                ", perceptiveHash=" + perceptiveHash +
                ", differenceHash=" + differenceHash +
                '}';
    }
}

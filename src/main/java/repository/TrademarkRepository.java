package repository;

import model.TrademarkRecord;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TrademarkRepository {
    private final List<TrademarkRecord> mockTable = new ArrayList<>();
    private int idCounter = 1; // simulates auto-increment

    public TrademarkRecord addToDatabase(String fullId,
                                         String markName,
                                         Map<String, String> data,
                                         String imagePath,
                                         BigInteger perceptiveHash,
                                         BigInteger differenceHash) {
        TrademarkRecord record = new TrademarkRecord(
                idCounter++,
                fullId,
                markName,
                data,
                imagePath,
                perceptiveHash,
                differenceHash
        );
        mockTable.add(record);
        return record;
    }

    public List<TrademarkRecord> getAll() {
        return new ArrayList<>(mockTable);
    }
}

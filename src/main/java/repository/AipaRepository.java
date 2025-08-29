package repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import model.AipaRecord;

import java.math.BigDecimal;
import java.sql.*;

public class AipaRepository {
    private final Connection connection;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AipaRepository(String url, String user, String password) throws SQLException {
        this.connection = DriverManager.getConnection(url, user, password);
    }

    public void addToDatabase(AipaRecord record) {
        String jsonData;
        try {
            jsonData = objectMapper.writeValueAsString(record.getData());
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize record data to JSON", e);
        }

        String sql = "INSERT INTO aipa " +
                "(full_id, mark_name, data, image_path, perceptive_hash, difference_hash) " +
                "VALUES (?, ?, ?::jsonb, ?, ?, ?) RETURNING id";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, record.getFullId());
            stmt.setString(2, record.getMarkName());
            stmt.setString(3, jsonData);
            stmt.setString(4, record.getImagePath() == null ? "" : record.getImagePath());
            stmt.setBigDecimal(5, record.getPerceptiveHash() == null ? null : new BigDecimal(record.getPerceptiveHash()));
            stmt.setBigDecimal(6, record.getDifferenceHash() == null ? null : new BigDecimal(record.getDifferenceHash()));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    System.out.println("Inserted record with id = " + id);
                } else {
                    throw new SQLException("Insert failed, no ID returned");
                }
            }
        } catch (SQLException e) {
            System.out.println("Failed to add record to Aipa: " + e.getMessage());
        }
    }

}

package repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import model.DataModel;

import java.math.BigDecimal;
import java.sql.*;

public class AipaRepository {
    private final Connection connection;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AipaRepository(String url, String user, String password) throws SQLException {
        this.connection = DriverManager.getConnection(url, user, password);
    }

    public void addToDatabase(DataModel record) {
        String jsonData;
        String sql;
        try {
            jsonData = objectMapper.writeValueAsString(record.getData());
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize record data to JSON", e);
        }

        String sqlAipa = "INSERT INTO aipa " +
                "(full_id, mark_name, data, image_path, perceptive_hash, difference_hash, link) " +
                "VALUES (?, ?, ?::jsonb, ?, ?, ?, ?) RETURNING id";

        String sqlWipo = "INSERT INTO wipo " +
                "(full_id, mark_name, data, image_path, perceptive_hash, difference_hash, link) " +
                "VALUES (?, ?, ?::jsonb, ?, ?, ?, ?) RETURNING id";

        if(record.getType().equals("aipa")) {
            sql = sqlAipa;
        } else {
            sql = sqlWipo;
        }

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, record.getFullId());
            stmt.setString(2, record.getMarkName());
            stmt.setString(3, jsonData);
            stmt.setString(4, record.getImagePath() == null ? "" : record.getImagePath());
            stmt.setBigDecimal(5, record.getPerceptiveHash() == null ? null : new BigDecimal(record.getPerceptiveHash()));
            stmt.setBigDecimal(6, record.getDifferenceHash() == null ? null : new BigDecimal(record.getDifferenceHash()));
            stmt.setString(7, record.getLink());

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

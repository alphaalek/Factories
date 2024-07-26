package dk.superawesome.factories.mechanics.db;

import dk.superawesome.factories.mechanics.MechanicStorageContext;
import dk.superawesome.factories.util.db.Query;
import dk.superawesome.factories.util.db.Types;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class MechanicController {

    private final DatabaseConnection connection;

    public MechanicController(DatabaseConnection connection) {
        this.connection = connection;
    }

    public MechanicStorageContext findAt(Location loc) {
        return new MechanicStorageContext(this, loc);
    }

    public MechanicStorageContext create(Location loc, BlockFace rot, String type) throws SQLException {
        Query query = new Query(
                "INSERT INTO mechanics (location, rotation, type) " +
                "VALUES (?, ?, ?)"
                )
                .add(Types.LOCATION.convert(loc))
                .add(rot.name())
                .add(type);

        query.execute(this.connection);
        return new MechanicStorageContext(this, loc);
    }

    public boolean hasData(Location loc) throws SQLException {
        Query query = new Query(
                "SELECT * from mechanics " +
                "WHERE location = ? " +
                "LIMIT BY 1"
                )
                .add(Types.LOCATION.convert(loc));

        try (ResultSet result = query.executeQuery(this.connection)) {
            return result != null && result.next();
        }
    }

    public String getData(Location loc) throws SQLException {
        Query query = new Query(
                "SELECT data from mechanics " +
                "WHERE location = ? " +
                "LIMIT BY 1"
                )
                .add(Types.LOCATION.convert(loc));

        try (ResultSet result = query.executeQuery(this.connection)) {
            return Optional.ofNullable(result.getString("data"))
                    .orElse("");
        }
    }

    public void setData(Location loc, String data) throws SQLException {
        Query query = new Query(
                "UPDATE mechanics " +
                "SET data = ? " +
                "WHERE location = ? " +
                "LIMIT BY 1"
                )
                .add(data)
                .add(Types.LOCATION.convert(loc));

        query.executeUpdate(this.connection);
    }
}

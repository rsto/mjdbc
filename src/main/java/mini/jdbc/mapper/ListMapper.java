package mini.jdbc.mapper;

import mini.jdbc.DbMapper;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class ListMapper implements DbMapper<List> {

    @NotNull
    private final DbMapper elementMapper;

    public ListMapper(@NotNull DbMapper elementMapper) {
        this.elementMapper = elementMapper;
    }

    @NotNull
    @Override
    public List map(ResultSet r) throws SQLException {
        ArrayList res = new ArrayList();
        do {
            //noinspection unchecked
            res.add(elementMapper.map(r));
        } while (r.next());
        return res;
    }
}

package fr.maxlego08.essentials.storage.database.repositeries;

import fr.maxlego08.essentials.api.EssentialsPlugin;
import fr.maxlego08.essentials.api.dto.KitDTO;
import fr.maxlego08.essentials.storage.database.Repository;
import fr.maxlego08.sarah.DatabaseConnection;

import java.util.List;

public class KitRepository extends Repository {

    public KitRepository(EssentialsPlugin plugin, DatabaseConnection connection) {
        super(plugin, connection, "kits");
    }

    public void upsert(String key, String displayName, long cooldown, List<String> actions, List<String> items) {
        upsert(table -> {
            table.string("name", key).primary();
            table.string("displayName", displayName);
            table.bigInt("cooldown", cooldown);
            table.string("actions", actions.toString());
            table.string("items", items.toString());
        });
    }

    public List<KitDTO> select() {
        return select(KitDTO.class, table -> {
        });
    }

    public void delete(String key) {
        delete(table -> {
            table.string("name", key);
        });
    }
}

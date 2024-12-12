package fr.maxlego08.essentials.migrations;

import fr.maxlego08.sarah.database.Migration;

public class CreateKitTableMigration extends Migration {

    @Override
    public void up() {
        create("%prefix%kits", table -> {
            table.string("name", 255).primary();
            table.string("displayName", 255);
            table.bigInt("cooldown");
            table.longText("actions");
            table.longText("items");
            table.timestamps();
        });
    }
}

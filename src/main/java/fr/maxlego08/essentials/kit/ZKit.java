package fr.maxlego08.essentials.kit;

import fr.maxlego08.essentials.api.EssentialsPlugin;
import fr.maxlego08.essentials.api.commands.Permission;
import fr.maxlego08.essentials.api.kit.Kit;
import fr.maxlego08.essentials.zutils.utils.ZUtils;
import fr.maxlego08.menu.MenuItemStack;
import fr.maxlego08.menu.api.requirement.Action;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ZKit extends ZUtils implements Kit {

    private final EssentialsPlugin plugin;
    private final String displayName;
    private final String name;
    private final long cooldown;
    private final List<Action> actions;
    private List<ItemStack> items;

    public ZKit(EssentialsPlugin plugin, String displayName, String name, long cooldown, List<ItemStack> items, List<Action> actions) {
        this.plugin = plugin;
        this.displayName = displayName;
        this.name = name;
        this.cooldown = cooldown;
        this.items = items;
        this.actions = actions;
    }

    @Override
    public long getCooldown() {
        return this.cooldown;
    }

    @Override
    public List<ItemStack> getItems() { return this.items; }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDisplayName() {
        return this.displayName;
    }

    @Override
    public void give(Player player) {
        this.items.forEach(item -> this.plugin.give(player, item));
    }

    @Override
    public void setItems(List<ItemStack> menuItemStacks) { this.items = menuItemStacks; }

    @Override
    public List<Action> getActions() {
        return actions;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(Permission.ESSENTIALS_KIT_.asPermission(this.getName()));
    }
}

package fr.maxlego08.essentials.kit;

import com.google.gson.*;
import fr.maxlego08.essentials.ZEssentialsPlugin;
import fr.maxlego08.essentials.api.commands.Permission;
import fr.maxlego08.essentials.api.dto.KitDTO;
import fr.maxlego08.essentials.api.kit.Kit;
import fr.maxlego08.essentials.api.kit.KitDisplay;
import fr.maxlego08.essentials.api.kit.KitStorage;
import fr.maxlego08.essentials.api.messages.Message;
import fr.maxlego08.essentials.api.user.User;
import fr.maxlego08.essentials.module.ZModule;
import fr.maxlego08.essentials.zutils.utils.TimerBuilder;
import fr.maxlego08.menu.MenuItemStack;
import fr.maxlego08.menu.api.requirement.Action;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.menu.loader.MenuItemStackLoader;
import fr.maxlego08.menu.zcore.utils.loader.Loader;
import fr.maxlego08.menu.zcore.utils.nms.ItemStackUtils;
import org.apache.logging.log4j.util.Strings;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permissible;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class KitModule extends ZModule {

    private final List<Kit> kits = new ArrayList<>();
    private final Gson gson = new GsonBuilder().create();
    private final KitStorage storage = KitStorage.YAML;
    private final KitDisplay display = KitDisplay.IN_LINE;
    private String kitFirstJoin;

    public KitModule(ZEssentialsPlugin plugin) {
        super(plugin, "kits");
        this.copyAndUpdate = false;
    }

    @Override
    public void loadConfiguration() {
        super.loadConfiguration();

        this.loadKits();

        this.loadInventory("kits");
        this.loadInventory("kit_preview");
    }

    public boolean exist(String name) {
        return this.getKit(name).isPresent();
    }

    public Optional<Kit> getKit(String name) {
        return this.kits.stream().filter(e -> e.getName().equalsIgnoreCase(name)).findFirst();
    }

    private void loadKits() {

        this.kits.clear();

        if (storage == KitStorage.YAML) {
            this.loadKitsFromYaml();
        } else {
            this.loadKitsFromDatabase();
        }
    }

    private void loadKitsFromYaml() {
        YamlConfiguration configuration = getConfiguration();
        File file = new File(getFolder(), "config.yml");

        ConfigurationSection configurationSection = configuration.getConfigurationSection("kits");
        if (configurationSection == null) return;

        Loader<MenuItemStack> loader = new MenuItemStackLoader(this.plugin.getInventoryManager());

        for (String key : configurationSection.getKeys(false)) {

            String path = "kits." + key + ".";
            String name = configuration.getString(path + "name");
            long cooldown = configuration.getLong(path + "cooldown");

            ConfigurationSection configurationSectionItems = configuration.getConfigurationSection(path + "items");
            if (configurationSectionItems == null) continue;

            List<ItemStack> items = new ArrayList<>();

            for (String itemName : configurationSectionItems.getKeys(false)) {
                String base64 = configuration.getString(path + "items." + itemName + ".base64");
                if (base64 == null) continue;
                items.add(ItemStackUtils.deserializeItemStack(base64));
            }

            if (this.exist(name)) {
                this.plugin.getLogger().severe("Kit " + name + " already exist !");
                return;
            }

            List<Action> actions = this.plugin.getButtonManager().loadActions((List<Map<String, Object>>) configuration.getList(path + "actions", new ArrayList<>()), path, file);
            Kit kit = new ZKit(plugin, name, key, cooldown, items, actions);
            this.kits.add(kit);
            this.plugin.getLogger().info("Register kit: " + name);
        }
    }

    private void loadKitsFromDatabase() {
        List<KitDTO> kits = plugin.getStorageManager().getStorage().getKits();

        for (KitDTO kitDTO : kits) {
            List<String> itemStrings = parseBracketedList(kitDTO.items());
            List<ItemStack> items = itemStrings.stream()
                    .map(ItemStackUtils::deserializeItemStack)
                    .toList();

            List<MenuItemStack> menuItemStacks = items.stream()
                    .map(item -> MenuItemStack.fromItemStack(plugin.getInventoryManager(), item))
                    .toList();

            List<String> actionStrings = parseBracketedList(kitDTO.actions());
            List<Action> actions = actionStrings.stream()
                    .map(actionStr -> gson.fromJson(actionStr, Action.class))
                    .toList();

            Kit kit = new ZKit(
                    plugin,
                    kitDTO.displayName(),
                    kitDTO.name(),
                    kitDTO.cooldown(),
                    items,
                    actions
            );

            if (this.exist(kit.getName())) {
                plugin.getLogger().severe("Kit " + kit.getName() + " already exists!");
                continue;
            }

            this.kits.add(kit);
            plugin.getLogger().info("Registered kit: " + kitDTO.name());
        }
    }

    private List<String> parseBracketedList(String input) {
        if (input == null || input.length() < 2) {
            return Collections.emptyList();
        }
        String trimmed = input.substring(1, input.length() - 1).trim();
        if (trimmed.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(trimmed.split(", "));
    }


    public void saveKits() {
        if (storage == KitStorage.YAML) {
            saveKitsToYaml();
        } else {
            saveKitsToDatabase();
        }
    }

    private void saveKitsToYaml() {
        YamlConfiguration configuration = getConfiguration();
        File file = new File(getFolder(), "config.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }


        ConfigurationSection configurationSection = configuration.getConfigurationSection("kits.");
        if (configurationSection != null) {
            configurationSection.getKeys(true).forEach(key -> configurationSection.set(key, null));
        }

        this.kits.forEach(kit -> {

            String path = "kits." + kit.getName() + ".";
            configuration.set(path + "name", kit.getDisplayName());

            if (kit.getCooldown() > 0) configuration.set(path + "cooldown", kit.getCooldown());
            else configuration.set(path + "cooldown", null);
            AtomicInteger atomicInteger = new AtomicInteger(1);
            kit.getItems().forEach(item -> {
                String base64 = ItemStackUtils.serializeItemStack(item);
                configuration.set(path + "items.item" + atomicInteger.getAndIncrement() + ".base64", base64);
            });
        });

        try {
            configuration.save(file);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    private void saveKitsToDatabase() {
        this.kits.forEach(kit -> {
            List<String> items = new ArrayList<>();
            kit.getItems().forEach(item -> {
                String base64 = ItemStackUtils.serializeItemStack(item);
                items.add(base64);
            });
            List<String> actions = new ArrayList<>();
            kit.getActions().forEach(action -> actions.add(gson.toJson(action)));
            plugin.getStorageManager().getStorage().createKit(kit.getName(), kit.getDisplayName(), kit.getCooldown(), actions, items);
        });
    }

    public List<Kit> getKits(Permissible permissible) {
        return this.kits.stream().filter(kit -> permissible.hasPermission(Permission.ESSENTIALS_KIT_.asPermission(kit.getName()))).toList();
    }

    public boolean giveKit(User user, Kit kit, boolean bypassCooldown) {

        long cooldown = kit.getCooldown();
        if (cooldown != 0 && !bypassCooldown && !user.hasPermission(Permission.ESSENTIALS_KIT_BYPASS_COOLDOWN)) {
            if (user.isKitCooldown(kit)) {
                long milliSeconds = user.getKitCooldown(kit) - System.currentTimeMillis();
                message(user, Message.COOLDOWN, "%cooldown%", TimerBuilder.getStringTime(milliSeconds));
                return false;
            }
        }

        kit.give(user.getPlayer());

        if (cooldown != 0 && !bypassCooldown && !user.hasPermission(Permission.ESSENTIALS_KIT_BYPASS_COOLDOWN)) {
            user.addKitCooldown(kit, cooldown);
        }

        kit.getActions().forEach(action -> action.preExecute(user.getPlayer(), null, this.plugin.getInventoryManager().getFakeInventory(), new Placeholders()));

        return true;
    }

    public void sendInLine(CommandSender sender) {
        List<String> homesAsString = kits.stream().map(kit -> getMessage(Message.COMMAND_KIT_INFORMATION_IN_LINE_INFO_AVAILABLE, "%name%", kit.getName())).toList();
        message(sender, Message.COMMAND_KIT_INFORMATION_IN_LINE, "%kits%", Strings.join(homesAsString, ','));
    }

    public void showKits(User user) {

        if (display != KitDisplay.INVENTORY) {

            List<Kit> kits = getKits(user.getPlayer());

            if (display == KitDisplay.IN_LINE) {
                List<String> homesAsString = kits.stream().map(kit -> {

                    long cooldown = kit.getCooldown();
                    long milliSeconds = 0;
                    if (cooldown != 0 && !user.hasPermission(Permission.ESSENTIALS_KIT_BYPASS_COOLDOWN) && user.isKitCooldown(kit)) {
                        milliSeconds = user.getKitCooldown(kit) - System.currentTimeMillis();
                    }

                    return getMessage(milliSeconds != 0 ? Message.COMMAND_KIT_INFORMATION_IN_LINE_INFO_UNAVAILABLE : Message.COMMAND_KIT_INFORMATION_IN_LINE_INFO_AVAILABLE, "%name%", kit.getName(), "%time%", TimerBuilder.getStringTime(milliSeconds));
                }).toList();
                message(user, Message.COMMAND_KIT_INFORMATION_IN_LINE, "%kits%", Strings.join(homesAsString, ','));
            } else {
                message(user, Message.COMMAND_KIT_INFORMATION_MULTI_LINE_HEADER);
                kits.forEach(kit -> {

                    long cooldown = kit.getCooldown();
                    long milliSeconds = 0;
                    if (cooldown != 0 && !user.hasPermission(Permission.ESSENTIALS_KIT_BYPASS_COOLDOWN) && user.isKitCooldown(kit)) {
                        milliSeconds = user.getKitCooldown(kit) - System.currentTimeMillis();
                    }

                    message(user, milliSeconds != 0 ? Message.COMMAND_KIT_INFORMATION_MULTI_LINE_CONTENT_UNAVAILABLE : Message.COMMAND_KIT_INFORMATION_MULTI_LINE_CONTENT_AVAILABLE, "%name%", kit.getName(), "%time%", TimerBuilder.getStringTime(milliSeconds));
                });
                message(user, Message.COMMAND_KIT_INFORMATION_MULTI_LINE_FOOTER);
            }
        } else {

            this.plugin.openInventory(user.getPlayer(), "kits");
        }
    }

    public void openKitEditor(Player player, Kit kit) {
        InventoryHolder inventoryHolder = new KitInventoryHolder(player, kit);
        player.openInventory(inventoryHolder.getInventory());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof KitInventoryHolder inventoryHolder) {

            Kit kit = inventoryHolder.getKit();
            List<ItemStack> items = new ArrayList<>();
            for (ItemStack itemStack : event.getInventory().getContents()) {
                if (itemStack != null) {
                    items.add(itemStack);
                }
            }
            kit.setItems(items);
            this.saveKits();
            message(event.getPlayer(), Message.COMMAND_KIT_EDITOR_SAVE, "%kit%", kit.getName());
        }
    }

    @EventHandler
    public void onConnect(PlayerJoinEvent event) {
        User user = this.getUser(event.getPlayer());

        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (user.isFirstJoin()) {
                this.getKit(kitFirstJoin).ifPresent(kit -> this.giveKit(user, kit, true));
            }
        }, 20L);
    }

    public void createKit(Player player, String kitName, long cooldown) {

        Kit kit = new ZKit(plugin, kitName, kitName, cooldown, new ArrayList<>(), new ArrayList<>());
        this.kits.add(kit);
        this.saveKits();

        message(player, Message.COMMAND_KIT_CREATE, "%kit%", kit.getName());
    }

    public void deleteKit(Player player, Kit kit) {

        this.kits.remove(kit);
        this.saveKits();

        message(player, Message.COMMAND_KIT_DELETE, "%kit%", kit.getName());
    }

    public List<String> getKitNames() {
        List<String> kitNames = Arrays.asList("warrior", "archer", "mage", "healer", "miner", "builder", "scout", "assassin", "knight", "ranger", "alchemist", "blacksmith", "explorer", "thief", "fisherman", "farmer", "necromancer", "paladin", "berserker", "enchanter");
        return kitNames.stream().filter(name -> this.kits.stream().noneMatch(kit -> kit.getName().equalsIgnoreCase(name))).toList();
    }
}

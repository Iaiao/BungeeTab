package mc.iaiao.bungeetab;

import com.comphenix.packetwrapper.WrapperPlayServerPlayerInfo;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

public class BungeeTab extends JavaPlugin implements Listener {
    // player -> server -> tracked player name and uuid
    private final HashMap<Player, HashMap<String, HashMap<String, UUID>>> trackedPlayers = new HashMap<>();
    private final GetPlayers playerGetter = new GetPlayers();
    private Set<String> servers;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        servers = getConfig().getConfigurationSection("servers").getKeys(false);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", playerGetter);
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(new PacketAdapter.AdapterParameteters().plugin(this).serverSide().types(PacketType.Play.Server.PLAYER_INFO)) {
            @Override
            public void onPacketSending(PacketEvent event) {
                WrapperPlayServerPlayerInfo packet = new WrapperPlayServerPlayerInfo(event.getPacket());
                switch (packet.getAction()) {
                    case ADD_PLAYER:
                    case REMOVE_PLAYER:
                        packet.setData(packet.getData()
                                .stream()
                                .map(data -> {
                                    String server = getServer(data.getProfile().getName());
                                    return new PlayerInfoData(
                                            data.getProfile(),
                                            0,
                                            EnumWrappers.NativeGameMode.SURVIVAL,
                                            WrappedChatComponent.fromText(
                                                    ChatColor.translateAlternateColorCodes('&', getConfig().getString(
                                                            (server == null ? "here" : "servers." + server) + ".name"
                                                    ).replaceAll("\\{name}", data.getProfile().getName()))
                                            )
                                    );
                                }).collect(Collectors.toList()));
                        break;
                }
            }
        });
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            Optional<? extends Player> oPlayer = getServer().getOnlinePlayers().stream().findAny();
            oPlayer.ifPresent(sender -> {
                for (String server : servers) {
                    List<String> players = playerGetter.get(server, sender);
                    if (players == null) continue;
                    for (Player player : getServer().getOnlinePlayers()) {
                        List<String> toRemove = trackedPlayers
                                .get(player)
                                .get(server)
                                .keySet()
                                .stream()
                                .filter(name -> !players.contains(name))
                                .collect(Collectors.toList());
                        toRemove.forEach(name -> {
                            UUID uuid = trackedPlayers.get(player).get(server).remove(name);
                            if (uuid == null) return;
                            WrapperPlayServerPlayerInfo packet = new WrapperPlayServerPlayerInfo();
                            packet.setAction(EnumWrappers.PlayerInfoAction.REMOVE_PLAYER);
                            packet.setData(Collections.singletonList(new PlayerInfoData(
                                    new WrappedGameProfile(uuid, name),
                                    0,
                                    EnumWrappers.NativeGameMode.SURVIVAL,
                                    WrappedChatComponent.fromText(name)
                            )));
                            packet.sendPacket(player);
                        });
                        List<String> toAdd = players
                                .stream()
                                .filter(name -> !trackedPlayers.get(player).get(server).containsKey(name))
                                .collect(Collectors.toList());
                        toAdd.forEach(name -> {
                            UUID uuid = UUID.randomUUID();
                            trackedPlayers.get(player).get(server).put(name, uuid);
                            WrapperPlayServerPlayerInfo packet = new WrapperPlayServerPlayerInfo();
                            packet.setAction(EnumWrappers.PlayerInfoAction.ADD_PLAYER);
                            packet.setData(Collections.singletonList(new PlayerInfoData(
                                    new WrappedGameProfile(uuid, name),
                                    0,
                                    EnumWrappers.NativeGameMode.SURVIVAL,
                                    WrappedChatComponent.fromText(name)
                            )));
                            packet.sendPacket(player);
                        });
                    }
                }
            });
        }, 20, 20);
        getServer().getOnlinePlayers().forEach(this::trackPlayer);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        trackPlayer(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        unTrackPlayer(event.getPlayer());
    }

    private void trackPlayer(Player player) {
        trackedPlayers.put(player, new HashMap<>());
        servers.forEach(name -> trackedPlayers.get(player).put(name, new HashMap<>()));
    }

    private void unTrackPlayer(Player player) {
        trackedPlayers.remove(player);
    }

    private String getServer(String player) {
        return playerGetter.playersOnServer.get(player);
    }
}

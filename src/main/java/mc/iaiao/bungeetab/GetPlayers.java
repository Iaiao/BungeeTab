package mc.iaiao.bungeetab;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.*;

public class GetPlayers implements PluginMessageListener {
    private final BlockingMap<String, List<String>> players = new BlockingMap<>();
    final HashMap<String, String> playersOnServer = new HashMap<>();
    
    public List<String> get(String server, Player sender) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("PlayerList");
        out.writeUTF(server);
        sender.sendPluginMessage(BungeeTab.getPlugin(BungeeTab.class), "BungeeCord", out.toByteArray());
        return players.get(server);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] bytes) {
        if (channel.equals("BungeeCord")) {
            ByteArrayDataInput in = ByteStreams.newDataInput(bytes);
            if(in.readUTF().equals("PlayerList")) {
                String server = in.readUTF();
                String pls = in.readUTF();
                List<String> pl = pls.isEmpty() ? Collections.emptyList() : Arrays.asList(pls.split(", "));
                players.put(server, pl);
                pl.forEach(playerName -> playersOnServer.put(playerName, server));
            }
        }
    }
}

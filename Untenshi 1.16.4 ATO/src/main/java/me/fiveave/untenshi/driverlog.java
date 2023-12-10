package me.fiveave.untenshi;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static me.fiveave.untenshi.main.*;

class driverlog implements CommandExecutor, TabCompleter {

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (sender.isOp()) {
                if (args.length == 1) {
                    String drivername = args[0];
                    Player p = Bukkit.getPlayer(drivername);
                    untenshi ld = driver.get(p);
                    LocalDateTime myDateObj = LocalDateTime.now();
                    DateTimeFormatter myFormatObj = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss:SS");
                    String timestamp = myDateObj.format(myFormatObj);
                    if (ld != null && ld.isPlaying()) {
                        startlog(sender, ld, timestamp);
                    } else {
                        sender.sendMessage(utshead + ChatColor.RED + "Driver does not exist or is not driving.");
                    }
                } else {
                    sender.sendMessage(pureutstitle + ChatColor.YELLOW + "[" + getlang("usage") + " " + ChatColor.GOLD + "/utslogger <player>" + ChatColor.YELLOW + "]");
                }
            } else {
                cmds.noPerm(sender);
            }
        } catch (Exception e) {
            sender.sendMessage(utshead + ChatColor.RED + getlang("error"));
            e.printStackTrace();
        }
        return true;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> ta = new ArrayList<>();
        List<String> result = new ArrayList<>();
        List<String> driverlist = new ArrayList<>();
        for (Player p : driver.keySet()) {
            driverlist.add(p.getName());
        }
        if (args.length == 1) {
            ta.addAll(driverlist);
        } else {
            ta.add("");
        }
        for (String a : ta) {
            if (a.toLowerCase().startsWith(args[args.length - 1].toLowerCase())) {
                result.add(a);
            }
        }
        return result;
    }

    public void startlog(CommandSender sender, untenshi ld, String timestamp) throws IOException {
        create(ld, timestamp);
        logging(sender, ld, timestamp);
        sender.sendMessage(utshead + ChatColor.GREEN + "Starting logging " + ld.getP().getName());
    }

    public void logging(CommandSender sender, untenshi ld, String timestamp) {
        if (ld.isPlaying()) {
            LocalDateTime myDateObj = LocalDateTime.now();
            DateTimeFormatter myFormatObj = DateTimeFormatter.ofPattern("HH:mm:ss.SS");
            String timestampnow = myDateObj.format(myFormatObj);
            String s = String.format("%s,%1.2f,%d,%d,%d,%d", timestampnow, ld.getSpeed(), ld.getMascon(), ld.getSpeedlimit(), ld.getSignallimit(), ld.getPoints());
            try {
                write(ld, timestamp, s);
                Bukkit.getScheduler().runTaskLater(plugin, () -> logging(sender, ld, timestamp), tickdelay);
            } catch (Exception e) {
                sender.sendMessage(utshead + ChatColor.RED + "Ending logging " + ld.getP().getName());
                throw new RuntimeException(e);
            }
        } else {
            sender.sendMessage(utshead + ChatColor.RED + "Ending logging " + ld.getP().getName());
        }
    }

    public void create(untenshi ld, String timestamp) throws IOException {
        File dir = new File(plugin.getDataFolder().getPath() + "/driverlogs");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        File file = new File(dir, ld.getP().getUniqueId() + "_" + timestamp.replaceAll(":", "_") + ".csv");
        if (!file.exists() && file.createNewFile()) {
            BufferedWriter bw = new BufferedWriter(new FileWriter(file));
            bw.write("t,v,notch,splim,silim,pts");
            bw.close();
        }
    }

    public void write(untenshi ld, String timestamp, String s) {
        File dir = new File(plugin.getDataFolder().getPath() + "/driverlogs");
        if (dir.exists()) {
            File file = new File(dir, ld.getP().getUniqueId() + "_" + timestamp.replaceAll(":", "_") + ".csv");
            if (file.exists()) {
                try {
                    BufferedWriter bw = new BufferedWriter(new FileWriter(file, true));
                    bw.newLine();
                    bw.write(s);
                    bw.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}

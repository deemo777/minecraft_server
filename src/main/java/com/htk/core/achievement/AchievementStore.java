package com.htk.core.achievement;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** First completion and title ownership are committed together before any reward is delivered. */
final class AchievementStore {
    static final String NORMAL="cloud_walker",FIRST="sky_opener";
    private final File file;
    private UUID first;
    private String firstName;
    private final Map<UUID,String> completed=new LinkedHashMap<>(),selected=new HashMap<>();
    AchievementStore(File file) throws Exception {
        this.file=file;
        if(!file.exists())return;
        var yaml=new YamlConfiguration();yaml.load(file);
        if(yaml.contains("first.uuid")) {
            first=UUID.fromString(yaml.getString("first.uuid"));firstName=yaml.getString("first.name",first.toString());
        }
        var players=yaml.getConfigurationSection("players");
        if(players!=null)for(String key:players.getKeys(false)) {
            UUID id=UUID.fromString(key);
            completed.put(id,players.getString(key+".name",key));
            String title=players.getString(key+".title");
            if(title!=null)selected.put(id,title);
        }
        if(first!=null && !completed.containsKey(first))throw new IOException("First achiever has no completion record");
    }
    UUID first(){return first;}
    String firstName(){return firstName;}
    boolean completed(UUID id){return completed.containsKey(id);}
    boolean owns(UUID id,String title){return completed(id) && (NORMAL.equals(title) || FIRST.equals(title) && id.equals(first));}
    String selected(UUID id){String title=selected.get(id);return owns(id,title)?title:null;}
    boolean complete(UUID id,String name) throws IOException {
        if(completed(id))return false;
        UUID previousFirst=first;String previousName=firstName;
        boolean winner=first==null;
        completed.put(id,name);
        if(winner){first=id;firstName=name;}
        selected.put(id,winner?FIRST:NORMAL);
        try{save();}catch(IOException failure){completed.remove(id);selected.remove(id);first=previousFirst;firstName=previousName;throw failure;}
        return winner;
    }
    void select(UUID id,String title) throws IOException {
        if(title!=null && !owns(id,title))throw new IllegalArgumentException("Title not owned");
        String previous=selected.get(id);
        if(title==null)selected.remove(id);else selected.put(id,title);
        try{save();}catch(IOException failure){if(previous==null)selected.remove(id);else selected.put(id,previous);throw failure;}
    }
    private void save() throws IOException {
        var yaml=new YamlConfiguration();
        if(first!=null){yaml.set("first.uuid",first.toString());yaml.set("first.name",firstName);}
        completed.forEach((id,name)->{yaml.set("players."+id+".name",name);yaml.set("players."+id+".title",selected.get(id));});
        Files.createDirectories(file.toPath().toAbsolutePath().getParent());
        Path temporary=file.toPath().resolveSibling(file.getName()+".tmp");yaml.save(temporary.toFile());
        try{Files.move(temporary,file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
    }
}

package com.htk.core.npc;

import com.htk.core.ServerCore;
import com.htk.core.common.Glyphs;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import java.util.*;
import java.util.function.*;

/** 기존 NPC 태그와 위치를 보존하면서 주민을 전용 모델로 전환. */
public final class NpcModelService implements Listener {
    private final ServerCore plugin;
    private final NamespacedKey roleKey,yawKey,visualKey;
    private final List<Function<Entity,String>> legacy=new ArrayList<>();
    public NpcModelService(ServerCore plugin) {
        this.plugin=plugin;roleKey=new NamespacedKey(plugin,"modeled_npc_role");
        yawKey=new NamespacedKey(plugin,"modeled_npc_yaw");visualKey=new NamespacedKey(plugin,"modeled_npc_visual");
        Bukkit.getPluginManager().registerEvents(this,plugin);
    }
    public void registerLegacy(Function<Entity,String> resolver) {
        legacy.add(resolver);
        for(World w:Bukkit.getWorlds())for(Chunk c:w.getLoadedChunks())repair(c);
    }
    public Interaction spawn(Location location,String role,Consumer<Interaction> tags) {
        Interaction root=location.getWorld().spawn(location,Interaction.class,e->{
            tags.accept(e);e.getPersistentDataContainer().set(roleKey,PersistentDataType.STRING,role);
            e.getPersistentDataContainer().set(yawKey,PersistentDataType.FLOAT,location.getYaw());
            e.setInteractionWidth(.9f);e.setInteractionHeight(2.25f);e.setResponsive(true);
            e.setInvulnerable(true);e.setPersistent(true);e.setSilent(true);
        });
        if(!root.isValid())return null;
        if(!ensure(root)){root.remove();return null;}
        return root;
    }
    public void face(Entity root,float yaw) {
        root.getPersistentDataContainer().set(yawKey,PersistentDataType.FLOAT,yaw);ensure(root);
    }
    public void remove(Entity root) {
        for(Entity e:root.getWorld().getNearbyEntities(root.getLocation(),1,1,1))
            if(root.getUniqueId().toString().equals(e.getPersistentDataContainer().get(visualKey,PersistentDataType.STRING)))e.remove();
        root.remove();
    }
    private boolean ensure(Entity root) {
        String role=root.getPersistentDataContainer().get(roleKey,PersistentDataType.STRING);if(role==null)return false;
        String id=root.getUniqueId().toString();
        var found=root.getWorld().getNearbyEntities(root.getLocation(),1,1,1).stream()
            .filter(e->e instanceof ItemDisplay && id.equals(e.getPersistentDataContainer().get(visualKey,PersistentDataType.STRING)))
            .map(e->(ItemDisplay)e).toList();
        ItemDisplay display;
        if(found.isEmpty()) {
            Location at=root.getLocation();at.setYaw(0);at.setPitch(0);
            display=at.getWorld().spawn(at,ItemDisplay.class,e->{
                e.getPersistentDataContainer().set(visualKey,PersistentDataType.STRING,id);
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);e.setPersistent(true);
                e.setInvulnerable(true);e.setSilent(true);e.setGravity(false);e.setViewRange(1.5f);
            });
            if(!display.isValid())return false;
        }else { display=found.getFirst();for(int i=1;i<found.size();i++)found.get(i).remove(); }
        ItemStack item=new ItemStack(Material.PAPER);var meta=item.getItemMeta();meta.setItemModel(Glyphs.model("npc_"+role));item.setItemMeta(meta);display.setItemStack(item);
        Location at=root.getLocation();at.setYaw(0);at.setPitch(0);display.teleport(at);
        float yaw=root.getPersistentDataContainer().getOrDefault(yawKey,PersistentDataType.FLOAT,0f);
        display.setBillboard(Display.Billboard.FIXED);
        display.setTransformation(new Transformation(new Vector3f(0,.5625f,0),com.htk.core.npc.NpcFacing.rotation(yaw),new Vector3f(1.125f),new Quaternionf()));
        return true;
    }
    private void repair(Chunk chunk) {
        for(Entity entity:chunk.getEntities()) {
            if(entity instanceof Villager) {
                String role=null;for(var resolver:legacy){role=resolver.apply(entity);if(role!=null)break;}
                if(role!=null) {
                    Interaction next=spawn(entity.getLocation(),role,e->entity.getPersistentDataContainer().copyTo(e.getPersistentDataContainer(),true));
                    if(next!=null)entity.remove();else plugin.getLogger().warning("NPC 모델 전환이 차단됐습니다: "+entity.getUniqueId());
                }
            }else if(entity instanceof Interaction && entity.getPersistentDataContainer().has(roleKey,PersistentDataType.STRING))ensure(entity);
            else if(entity instanceof ItemDisplay) {
                String id=entity.getPersistentDataContainer().get(visualKey,PersistentDataType.STRING);
                if(id!=null)try{Entity root=Bukkit.getEntity(UUID.fromString(id));if(root==null || !root.getPersistentDataContainer().has(roleKey,PersistentDataType.STRING))entity.remove();}
                catch(IllegalArgumentException e){entity.remove();}
            }
        }
    }
    @EventHandler public void chunkLoad(ChunkLoadEvent e) {
        Bukkit.getScheduler().runTask(plugin,()->{if(e.getChunk().isLoaded())repair(e.getChunk());});
    }
}

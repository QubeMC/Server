package org.cloudburstmc.server.entity;

import co.aikar.timings.Timing;
import co.aikar.timings.Timings;
import co.aikar.timings.TimingsHistory;
import com.google.common.collect.Iterables;
import com.nukkitx.math.GenericMath;
import com.nukkitx.math.vector.Vector2f;
import com.nukkitx.math.vector.Vector3f;
import com.nukkitx.math.vector.Vector3i;
import com.nukkitx.nbt.NbtMap;
import com.nukkitx.nbt.NbtMapBuilder;
import com.nukkitx.nbt.NbtType;
import com.nukkitx.protocol.bedrock.BedrockPacket;
import com.nukkitx.protocol.bedrock.data.entity.EntityData;
import com.nukkitx.protocol.bedrock.data.entity.EntityDataMap;
import com.nukkitx.protocol.bedrock.data.entity.EntityLinkData;
import com.nukkitx.protocol.bedrock.packet.*;
import com.spotify.futures.CompletableFutures;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.api.block.Block;
import org.cloudburstmc.api.block.BlockCategory;
import org.cloudburstmc.api.block.BlockState;
import org.cloudburstmc.api.block.BlockTypes;
import org.cloudburstmc.api.entity.Attribute;
import org.cloudburstmc.api.entity.Entity;
import org.cloudburstmc.api.entity.EntityType;
import org.cloudburstmc.api.entity.Rideable;
import org.cloudburstmc.api.entity.misc.LightningBolt;
import org.cloudburstmc.api.entity.vehicle.Vehicle;
import org.cloudburstmc.api.event.Event;
import org.cloudburstmc.api.event.entity.*;
import org.cloudburstmc.api.event.player.PlayerInteractEvent;
import org.cloudburstmc.api.event.player.PlayerTeleportEvent;
import org.cloudburstmc.api.item.ItemStack;
import org.cloudburstmc.api.level.Location;
import org.cloudburstmc.api.level.gamerule.GameRules;
import org.cloudburstmc.api.player.GameMode;
import org.cloudburstmc.api.player.Player;
import org.cloudburstmc.api.potion.Effect;
import org.cloudburstmc.api.potion.EffectType;
import org.cloudburstmc.api.potion.EffectTypes;
import org.cloudburstmc.api.util.AxisAlignedBB;
import org.cloudburstmc.api.util.Direction;
import org.cloudburstmc.api.util.SimpleAxisAlignedBB;
import org.cloudburstmc.api.util.data.CardinalDirection;
import org.cloudburstmc.api.util.data.MountType;
import org.cloudburstmc.server.CloudServer;
import org.cloudburstmc.server.block.behavior.BlockBehaviorLiquid;
import org.cloudburstmc.server.block.behavior.BlockBehaviorNetherPortal;
import org.cloudburstmc.server.entity.data.SyncedEntityData;
import org.cloudburstmc.server.level.CloudLevel;
import org.cloudburstmc.server.level.EnumLevel;
import org.cloudburstmc.server.level.chunk.CloudChunk;
import org.cloudburstmc.server.math.MathHelper;
import org.cloudburstmc.server.math.NukkitMath;
import org.cloudburstmc.server.network.NetworkUtils;
import org.cloudburstmc.server.player.CloudPlayer;
import org.cloudburstmc.server.potion.CloudEffect;
import org.cloudburstmc.server.registry.CloudBlockRegistry;
import org.cloudburstmc.server.registry.EntityRegistry;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.nukkitx.protocol.bedrock.data.entity.EntityData.*;
import static com.nukkitx.protocol.bedrock.data.entity.EntityFlag.*;
import static org.cloudburstmc.api.block.BlockTypes.*;

/**
 * @author MagicDroidX
 */
@Log4j2
public abstract class BaseEntity implements Entity {

    protected final Set<CloudPlayer> hasSpawned = ConcurrentHashMap.newKeySet();

    protected final Reference2ObjectOpenHashMap<EffectType, Effect> effects = new Reference2ObjectOpenHashMap<>();
    protected final List<Entity> passengers = new ArrayList<>();
    private final long runtimeId = EntityRegistry.get().newEntityId();
    protected final SyncedEntityData data = new SyncedEntityData(this::onDataChange);
    private final EntityType<?> type;
    public CloudChunk chunk;
    public List<Block> blocksAround = new ArrayList<>();
    public List<Block> collisionBlockStates = new ArrayList<>();
    public NbtMap tag;
    public float highestPosition;
    public boolean firstMove = true;
    protected Vector3f position = Vector3f.ZERO;
    protected Vector3f lastPosition = Vector3f.ZERO;
    protected Vector3f motion = Vector3f.ZERO;
    protected Vector3f lastMotion = Vector3f.ZERO;
    protected float yaw;
    protected float pitch;
    protected float lastYaw;
    protected float lastPitch;
    protected float pitchDelta;
    protected float yawDelta;
    protected float entityCollisionReduction = 0; // Higher than 0.9 will result a fast collisions
    public boolean onGround;
    public boolean inBlock = false;
    public boolean positionChanged;
    public boolean motionChanged;
    public int deadTicks = 0;
    public boolean keepMovement = false;
    public float fallDistance = 0;
    public int ticksLived = 0;
    public int lastUpdate;
    public int maxFireTicks;
    public int fireTicks = 0;
    public int inPortalTicks = 0;
    public float scale = 1;
    protected AxisAlignedBB boundingBox;
    public boolean isCollided = false;
    public boolean isCollidedHorizontally = false;
    public boolean isCollidedVertically = false;
    public int noDamageTicks;
    public boolean justCreated;
    public boolean fireProof;
    public boolean invulnerable;
    protected CloudLevel level;
    public boolean closed = false;
    protected Entity vehicle;
    protected EntityDamageEvent lastDamageCause = null;
    protected int age = 0;
    protected float health = 20;
    protected float absorption = 0;
    protected float ySize = 0;
    protected boolean isStatic = false;
    protected CloudServer server;
    protected Timing timing;
    protected boolean isPlayer = false;
    private int maxHealth = 20;
    private volatile boolean initialized;

    public BaseEntity(EntityType<?> type, Location location) {
        this.type = type;
        if (this instanceof CloudPlayer) {
            return;
        }

        this.init(location);
    }

    public float getHeight() {
        return 0;
    }

    public float getEyeHeight() {
        return this.getHeight() / 2 + 0.1f;
    }

    public float getWidth() {
        return 0;
    }

    public float getLength() {
        return 0;
    }

    protected float getStepHeight() {
        return 0;
    }

    public boolean canCollide() {
        return true;
    }

    public float getGravity() {
        return 0;
    }

    public float getDrag() {
        return 0;
    }

    protected float getBaseOffset() {
        return 0;
    }

    protected void initEntity() {
        this.data.setFlag(HAS_COLLISION, true);
        this.data.setShort(AIR_SUPPLY, 400);
        this.data.setShort(MAX_AIR_SUPPLY, 400);
        this.data.setLong(LEASH_HOLDER_EID, -1);
        this.data.setFloat(SCALE, 1f);
        this.data.setFloat(BOUNDING_BOX_HEIGHT, this.getHeight());
        this.data.setFloat(BOUNDING_BOX_WIDTH, this.getWidth());
        this.data.setInt(HEALTH, (int) this.getHealth());

        this.scheduleUpdate();
    }

    public EntityType<?> getType() {
        return type;
    }

    @Override
    public CloudLevel getLevel() {
        return level;
    }

    public CloudChunk getChunk() {
        return chunk;
    }

    @Override
    public float getX() {
        return this.position.getX();
    }

    @Override
    public float getY() {
        return this.position.getY();
    }

    @Override
    public float getZ() {
        return this.position.getZ();
    }

    @Override
    public float getPitch() {
        return pitch;
    }

    @Override
    public float getYaw() {
        return yaw;
    }

   // @Override
    public void loadAdditionalData(NbtMap tag) {
        this.tag = tag;

        tag.listenForList("Pos", NbtType.FLOAT, list -> {
            this.setPosition(Vector3f.from(list.get(0), list.get(1), list.get(2)));
        });
        tag.listenForList("Rotation", NbtType.FLOAT, list -> {
            this.setRotation(list.get(0), list.get(1));
        });
        tag.listenForList("Motion", NbtType.FLOAT, list -> {
            this.setMotion(Vector3f.from(list.get(0), list.get(1), list.get(2)));
        });

//        this.highestPosition = this.y + this.namedTag.getFloat("FallDistance");
        tag.listenForFloat("FallDistance", this::setFallDistance);

        tag.listenForShort("Fire", this::setOnFire);

        tag.listenForShort("Air", this::setAir);

        tag.listenForBoolean("OnGround", this::setOnGround);

        tag.listenForBoolean("Invulnerable", this::setInvulnerable);
        tag.listenForFloat("scale", this::setScale);

        if (tag.containsKey("ActiveEffects")) {
            List<NbtMap> effects = tag.getList("ActiveEffects", NbtType.COMPOUND);
            for (NbtMap e : effects) {
                this.addEffect(CloudEffect.fromNBT(e));
            }
        }

        tag.listenForString("CustomName", this::setNameTag);
        tag.listenForBoolean("CustomNameVisible", this::setNameTagVisible);
        tag.listenForBoolean("CustomNameAlwaysVisible", this::setNameTagAlwaysVisible);
    }

   // @Override
    public void saveAdditionalData(NbtMapBuilder tag) {
        if (this.tag != null && !this.tag.isEmpty()) {
            tag.putAll(this.tag);
        }

        if (this.hasNameTag()) {
            tag.putString("CustomName", this.getNameTag());
            tag.putBoolean("CustomNameVisible", this.isNameTagVisible());
            tag.putBoolean("CustomNameAlwaysVisible", this.isNameTagAlwaysVisible());
        }

        if (!(this instanceof CloudPlayer)) {
            tag.putString("identifier", this.type.getIdentifier().toString());
        }

        tag.putList("Pos", NbtType.FLOAT, Arrays.asList(
                this.position.getX(),
                this.position.getY(),
                this.position.getZ())
        );

        tag.putList("Motion", NbtType.FLOAT, Arrays.asList(
                this.motion.getX(),
                this.motion.getY(),
                this.motion.getZ())
        );

        tag.putList("Rotation", NbtType.FLOAT, Arrays.asList(
                this.yaw,
                this.pitch)
        );

        tag.putFloat("FallDistance", this.fallDistance);
        tag.putShort("Fire", (short) this.fireTicks);
        tag.putShort("Air", this.data.getShort(AIR_SUPPLY));
        tag.putBoolean("OnGround", this.onGround);
        tag.putBoolean("Invulnerable", this.invulnerable);
        tag.putFloat("Scale", this.scale);

        if (!this.effects.isEmpty()) {
            List<NbtMap> list = new ArrayList<>();
            for (Effect effect : this.effects.values()) {
                list.add(((CloudEffect)effect).createTag());
            }

            tag.putList("ActiveEffects", NbtType.COMPOUND, list);
        }
    }

    public SyncedEntityData getData() {
        return this.data;
    }

    public boolean hasNameTag() {
        return !this.getNameTag().isEmpty();
    }

    public String getNameTag() {
        return this.data.getString(NAMETAG);
    }

    public void setNameTag(String name) {
        this.data.setString(NAMETAG, name);
    }

    public boolean isNameTagVisible() {
        return this.data.getFlag(CAN_SHOW_NAME);
    }

    public void setNameTagVisible(boolean value) {
        this.data.setFlag(CAN_SHOW_NAME, value);
    }

    public void setNameTagVisible() {
        this.setNameTagVisible(true);
    }

    public boolean isNameTagAlwaysVisible() {
        return this.data.getByte(NAMETAG_ALWAYS_SHOW) == 1;
    }

    public void setNameTagAlwaysVisible(boolean value) {
        this.data.setByte(NAMETAG_ALWAYS_SHOW, value ? 1 : 0);
    }

    public void setNameTagAlwaysVisible() {
        this.setNameTagAlwaysVisible(true);
    }

    public String getScoreTag() {
        return this.data.getString(SCORE_TAG);
    }

    public void setScoreTag(String score) {
        this.data.setString(SCORE_TAG, score);
    }

    public boolean isImmobile() {
        return this.data.getFlag(NO_AI);
    }

    public void setImmobile(boolean value) {
        this.data.setFlag(NO_AI, value);
    }

    public void setImmobile() {
        this.setImmobile(true);
    }

    public boolean canClimb() {
        return this.data.getFlag(CAN_CLIMB);
    }

    public void setCanClimb() {
        this.setCanClimb(true);
    }

    public void setCanClimb(boolean value) {
        this.data.setFlag(CAN_CLIMB, value);
    }

    public boolean canClimbWalls() {
        return this.data.getFlag(WALL_CLIMBING);
    }

    public void setCanClimbWalls() {
        this.setCanClimbWalls(true);
    }

    public void setCanClimbWalls(boolean value) {
        this.data.setFlag(WALL_CLIMBING, value);
    }

    public float getScale() {
        return this.scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
        this.data.setFloat(SCALE, this.scale);
        this.recalculateBoundingBox();
    }

    public short getAir() {
        return this.data.getShort(AIR_SUPPLY);
    }

    public void setAir(short air) {
        this.data.setShort(AIR_SUPPLY, air);
    }

    public boolean isInvulnerable() {
        return invulnerable;
    }

    public void setInvulnerable(boolean invulnerable) {
        this.invulnerable = invulnerable;
    }

    public List<Entity> getPassengers() {
        return passengers;
    }

    public Entity getPassenger() {
        return Iterables.getFirst(this.passengers, null);
    }

    public boolean isPassenger(Entity entity) {
        return this.passengers.contains(entity);
    }

    public boolean isControlling(Entity entity) {
        return this.passengers.indexOf(entity) == 0;
    }

    public boolean hasControllingPassenger() {
        return !this.passengers.isEmpty() && isControlling(this.passengers.get(0));
    }

    public Entity getVehicle() {
        return vehicle;
    }

    @Override
    public Map<EffectType, Effect> getEffects() {
        return effects;
    }

    @Override
    public void removeAllEffects() {
        for (Effect effect : this.effects.values()) {
            this.removeEffect(effect.getType());
        }
    }

    @Deprecated
    @Override
    public void removeEffect(int effectId) {
        removeEffect(NetworkUtils.effectFromLegacy((byte) effectId));
    }

    @Override
    public void removeEffect(EffectType type) {
        if (this.effects.containsKey(type)) {
            Effect effect = this.effects.remove(type);
            effect.remove(this);

            this.recalculateEffectColor();
        }
    }

    @Deprecated
    @Override
    public Effect getEffect(int effectId) {
        EffectType type = NetworkUtils.effectFromLegacy((byte) effectId);
        return this.effects.getOrDefault(type, null);
    }

    @Nullable
    @Override
    public CloudEffect getEffect(EffectType type) {
        return (CloudEffect) this.effects.getOrDefault(type, null);
    }

    @Deprecated
    @Override
    public boolean hasEffect(int effectId) {
        return this.hasEffect(NetworkUtils.effectFromLegacy((byte) effectId));
    }

    @Override
    public boolean hasEffect(EffectType type) {
        return this.effects.containsKey(type);
    }

    @Override
    public void addEffect(Effect effect) {
        if (effect == null) {
            return; //here add null means add nothing
        }

        effect.add(this);

        this.effects.put(effect.getType(), effect);

        this.recalculateEffectColor();

        if (effect.getType() == EffectTypes.HEALTH_BOOST) {
            this.setHealth(this.getHealth() + 4 * (effect.getAmplifier() + 1));
        }

    }

    public void recalculateBoundingBox() {
        float height = this.getHeight() * this.scale;
        float radius = (this.getWidth() * this.scale) / 2;
        this.boundingBox.setBounds(this.position.getX() - radius, this.position.getY(), this.position.getZ() - radius,
                this.position.getX() + radius, this.position.getY() + height, this.position.getZ() + radius);

        this.data.setFloat(BOUNDING_BOX_HEIGHT, this.getHeight());
        this.data.setFloat(BOUNDING_BOX_WIDTH, this.getWidth());
    }

    protected void recalculateEffectColor() {
        int[] color = new int[3];
        int count = 0;
        boolean ambient = true;
        for (Effect effect : this.effects.values()) {
            if (effect.isVisible()) {
                int[] c = effect.getColor();
                color[0] += c[0] * (effect.getAmplifier() + 1);
                color[1] += c[1] * (effect.getAmplifier() + 1);
                color[2] += c[2] * (effect.getAmplifier() + 1);
                count += effect.getAmplifier() + 1;
                if (!effect.isAmbient()) {
                    ambient = false;
                }
            }
        }

        if (count > 0) {
            int r = (color[0] / count) & 0xff;
            int g = (color[1] / count) & 0xff;
            int b = (color[2] / count) & 0xff;

            this.data.setInt(EFFECT_COLOR, (r << 16) + (g << 8) + b);
            this.data.setByte(EFFECT_AMBIENT, ambient ? 1 : 0);
        } else {
            this.data.setInt(EFFECT_COLOR, 0);
            this.data.setByte(EFFECT_AMBIENT, 0);
        }
    }

    protected final void init(Location location) {
        if (location == null) {
            throw new IllegalArgumentException("Invalid garbage Location given to Entity");
        }

        if (this.initialized) {
            // We've already initialized this entity
            return;
        }
        this.initialized = true;

        this.timing = Timings.getEntityTiming(this.getType());

        this.isPlayer = this instanceof CloudPlayer;

        this.justCreated = true;

        this.chunk = (CloudChunk) location.getLevel().getLoadedChunk(location.getPosition());
        this.level = (CloudLevel) location.getLevel();
        this.server = (CloudServer) location.getLevel().getServer();

        this.boundingBox = new SimpleAxisAlignedBB(0, 0, 0, 0, 0, 0);

        this.level.getChunkFuture(location.getChunkX(), location.getChunkZ()).whenComplete((chunk1, throwable) -> {
            if (throwable == null) {
                this.chunk = chunk1;
                chunk1.addEntity(this);
            }
        });
        this.level.addEntity(this);

        this.initEntity();

        this.lastUpdate = this.server.getTick();
        this.getServer().getEventManager().fire(new EntitySpawnEvent(this));

        this.scheduleUpdate();
    }

    //@Override
    public NbtMap getTag() {
        return tag;
    }

    public String getName() {
        if (this.hasNameTag()) {
            return this.getNameTag();
        } else {
            // FIXME: 04/01/2020 Use language files
            return EntityRegistry.get().getLegacyName(this.type.getIdentifier());
        }
    }

    @Override
    public void spawnTo(Player player) {
        this.spawnTo(((CloudPlayer) player));
    }

    public void spawnTo(CloudPlayer player) {
        if (!player.isChunkInView(this.chunk.getX(), this.chunk.getZ()) || !this.getViewers().add(player)) {
            // out of range or already spawned
            return;
        }

        player.sendPacket(createAddEntityPacket());

        if (this.vehicle != null) {
            this.vehicle.spawnTo(player);

            SetEntityLinkPacket packet = new SetEntityLinkPacket();
            packet.setEntityLink(new EntityLinkData(this.vehicle.getUniqueId(),
                    this.getUniqueId(), EntityLinkData.Type.RIDER, true, false));

            player.sendPacket(packet);
        }
    }

    protected BedrockPacket createAddEntityPacket() {
        AddEntityPacket addEntity = new AddEntityPacket();
        addEntity.setIdentifier(this.getType().getIdentifier().toString());
        addEntity.setUniqueEntityId(this.getUniqueId());
        addEntity.setRuntimeEntityId(this.getUniqueId());
        addEntity.setPosition(this.getPosition());
        addEntity.setRotation(Vector3f.from(this.pitch, this.yaw, this.yaw));
        addEntity.setMotion(this.getMotion());
        addEntity.setBodyRotation(this.getYaw());
        this.data.putAllIn(addEntity.getMetadata());

        for (int i = 0; i < this.passengers.size(); i++) {
            addEntity.getEntityLinks().add(new EntityLinkData(this.getUniqueId(),
                    this.passengers.get(i).getUniqueId(), i == 0 ? EntityLinkData.Type.RIDER : EntityLinkData.Type.PASSENGER, false, false));
        }
        return addEntity;
    }

    public Set<CloudPlayer> getViewers() {
        return hasSpawned;
    }

    public void sendPotionEffects(CloudPlayer player) {
        for (Effect effect : this.effects.values()) {
            MobEffectPacket pk = new MobEffectPacket();
            pk.setRuntimeEntityId(this.getRuntimeId());
            pk.setEffectId(NetworkUtils.effectToNetwork(effect.getType()));
            pk.setAmplifier(effect.getAmplifier());
            pk.setParticles(effect.isVisible());
            pk.setDuration(effect.getDuration());
            pk.setEvent(MobEffectPacket.Event.ADD);

            player.sendPacket(pk);
        }
    }

    private void onDataChange(EntityDataMap changeSet) {
        this.sendDataToViewers(changeSet);

        if (this.isPlayer) {
            SetEntityDataPacket packet = new SetEntityDataPacket();
            packet.setRuntimeEntityId(this.getRuntimeId());
            packet.getMetadata().putAll(changeSet);
            ((CloudPlayer) this).sendPacket(packet);
        }
    }

    public void sendData(CloudPlayer player) {
        SetEntityDataPacket packet = new SetEntityDataPacket();
        packet.setRuntimeEntityId(this.getRuntimeId());
        this.data.putAllIn(packet.getMetadata());
        player.sendPacket(packet);
    }

    private void sendDataToViewers(EntityDataMap map) {
        SetEntityDataPacket packet = new SetEntityDataPacket();
        packet.setRuntimeEntityId(this.getRuntimeId());
        packet.getMetadata().putAll(map);

        CloudServer.broadcastPacket(this.getViewers(), packet);
    }

    public void sendData(CloudPlayer player, EntityData... data) {
        SetEntityDataPacket packet = new SetEntityDataPacket();
        packet.setRuntimeEntityId(this.getRuntimeId());

        for (EntityData entityData : data) {
            packet.getMetadata().put(entityData, this.data.get(entityData));
        }

        player.sendPacket(packet);
    }

    public void sendFlags(CloudPlayer player) {
        SetEntityDataPacket packet = new SetEntityDataPacket();
        packet.setRuntimeEntityId(this.getRuntimeId());

        this.data.putFlagsIn(packet.getMetadata());

        player.sendPacket(packet);
    }

    @Override
    public void despawnFrom(Player player) {
        this.despawnFrom((CloudPlayer) player);
    }

    public void despawnFrom(CloudPlayer player) {
        if (this.hasSpawned.remove(player)) {
            RemoveEntityPacket packet = new RemoveEntityPacket();
            packet.setUniqueEntityId(this.getUniqueId());
            player.sendPacket(packet);
        }
    }

    public boolean attack(EntityDamageEvent source) {
        if (hasEffect(EffectTypes.FIRE_RESISTANCE)
                && (source.getCause() == EntityDamageEvent.DamageCause.FIRE
                || source.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK
                || source.getCause() == EntityDamageEvent.DamageCause.LAVA)) {
            return false;
        }

        getServer().getEventManager().fire(source);
        if (source.isCancelled()) {
            return false;
        }
        if (this.absorption > 0) {  // Damage Absorption
            this.setAbsorption(Math.max(0, this.getAbsorption() + source.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION)));
        }
        setLastDamageCause(source);
        setHealth(getHealth() - source.getFinalDamage());
        return true;
    }

    public boolean attack(float damage) {
        return this.attack(new EntityDamageEvent(this, EntityDamageEvent.DamageCause.CUSTOM, damage));
    }

    public void heal(EntityRegainHealthEvent source) {
        ((CloudServer)this.server).getEventManager().fire(source);
        if (source.isCancelled()) {
            return;
        }
        this.setHealth(this.getHealth() + source.getAmount());
    }

    public void heal(float amount) {
        this.heal(new EntityRegainHealthEvent(this, amount, EntityRegainHealthEvent.CAUSE_REGEN));
    }

    public float getHealth() {
        return health;
    }

    public void setHealth(float health) {
        if (this.health == health) {
            return;
        }

        if (health < 1) {
            if (this.isAlive()) {
                this.kill();
            }
        } else if (health <= this.getMaxHealth() || health < this.health) {
            this.health = health;
        } else {
            this.health = this.getMaxHealth();
        }

        this.data.setInt(HEALTH, (int) this.health);
    }

    public boolean isAlive() {
        return this.health > 0;
    }

    public boolean isClosed() {
        return closed;
    }

    public EntityDamageEvent getLastDamageCause() {
        return lastDamageCause;
    }

    public void setLastDamageCause(EntityDamageEvent type) {
        this.lastDamageCause = type;
    }

    public int getMaxHealth() {
        return maxHealth + (this.hasEffect(EffectTypes.HEALTH_BOOST) ? 4 * (this.getEffect(EffectTypes.HEALTH_BOOST).getAmplifier() + 1) : 0);
    }

    public void setMaxHealth(int maxHealth) {
        this.maxHealth = maxHealth;
    }

    public boolean canCollideWith(Entity entity) {
        return !this.justCreated && this != entity;
    }

    protected boolean checkObstruction(Vector3f pos) {
        return this.checkObstruction(pos.getX(), pos.getY(), pos.getZ());
    }

    protected boolean checkObstruction(float x, float y, float z) {
        if (this.level.getCollisionCubes(this, this.getBoundingBox(), false).length == 0) {
            return false;
        }

        float motionX = this.motion.getX();
        float motionY = this.motion.getY();
        float motionZ = this.motion.getZ();

        int i = NukkitMath.floorDouble(x);
        int j = NukkitMath.floorDouble(y);
        int k = NukkitMath.floorDouble(z);

        float diffX = x - i;
        float diffY = y - j;
        float diffZ = z - k;

        CloudBlockRegistry registry = CloudBlockRegistry.get();

        if (!this.level.getBlockState(i, j, k).inCategory(BlockCategory.TRANSPARENT)) {
            boolean flag = this.level.getBlockState(i - 1, j, k).inCategory(BlockCategory.TRANSPARENT);
            boolean flag1 = this.level.getBlockState(i + 1, j, k).inCategory(BlockCategory.TRANSPARENT);
            boolean flag2 = this.level.getBlockState(i, j - 1, k).inCategory(BlockCategory.TRANSPARENT);
            boolean flag3 = this.level.getBlockState(i, j + 1, k).inCategory(BlockCategory.TRANSPARENT);
            boolean flag4 = this.level.getBlockState(i, j, k - 1).inCategory(BlockCategory.TRANSPARENT);
            boolean flag5 = this.level.getBlockState(i, j, k + 1).inCategory(BlockCategory.TRANSPARENT);

            int direction = -1;
            float limit = 9999;

            if (flag) {
                limit = diffX;
                direction = 0;
            }

            if (flag1 && 1 - diffX < limit) {
                limit = 1 - diffX;
                direction = 1;
            }

            if (flag2 && diffY < limit) {
                limit = diffY;
                direction = 2;
            }

            if (flag3 && 1 - diffY < limit) {
                limit = 1 - diffY;
                direction = 3;
            }

            if (flag4 && diffZ < limit) {
                limit = diffZ;
                direction = 4;
            }

            if (flag5 && 1 - diffZ < limit) {
                direction = 5;
            }

            float force = new Random().nextFloat() * 0.2f + 0.1f;

            if (direction == 0) {
                motionX = -force;
            } else if (direction == 1) {
                motionX = force;
            } else if (direction == 2) {
                motionY = -force;
            } else if (direction == 3) {
                motionY = force;
            } else if (direction == 4) {
                motionZ = -force;
            } else if (direction == 5) {
                motionZ = force;
            }
            this.motion = Vector3f.from(motionX, motionY, motionZ);
            return true;
        }

        return false;
    }

    public boolean entityBaseTick() {
        return this.entityBaseTick(1);
    }

    public boolean entityBaseTick(int tickDiff) {
        try (Timing ignored = Timings.entityBaseTickTimer.startTiming()) {

            if (!this.isPlayer) {
                this.blocksAround = null;
                this.collisionBlockStates = null;
            }
            this.justCreated = false;

            if (!this.isAlive()) {
                this.removeAllEffects();
                this.despawnFromAll();
                if (!this.isPlayer) {
                    this.close();
                }
                return false;
            }
            if (vehicle != null && !vehicle.isAlive() && vehicle instanceof Rideable) {
                this.mount((Rideable) vehicle);
            }

            updatePassengers();

            if (!this.effects.isEmpty()) {
                for (Effect effect : this.effects.values()) {
                    if (effect.canTick()) {
                        effect.applyEffect(this);
                    }
                    effect.setDuration(effect.getDuration() - tickDiff);

                    if (effect.getDuration() <= 0) {
                        this.removeEffect(effect.getType());
                    }
                }
            }

            boolean hasUpdate = false;

            this.checkBlockCollision();

            if (this.position.getY() <= -16 && this.isAlive()) {
                if (this instanceof CloudPlayer) {
                    CloudPlayer player = (CloudPlayer) this;
                    if (player.getGamemode() != GameMode.CREATIVE)
                        this.attack(new EntityDamageEvent(this, EntityDamageEvent.DamageCause.VOID, 10));
                } else {
                    this.attack(new EntityDamageEvent(this, EntityDamageEvent.DamageCause.VOID, 10));
                    hasUpdate = true;
                }
            }

            if (this.fireTicks > 0) {
                if (this.fireProof) {
                    this.fireTicks -= 4 * tickDiff;
                    if (this.fireTicks < 0) {
                        this.fireTicks = 0;
                    }
                } else {
                    if (!this.hasEffect(EffectTypes.FIRE_RESISTANCE) && ((this.fireTicks % 20) == 0 || tickDiff > 20)) {
                        this.attack(new EntityDamageEvent(this, EntityDamageEvent.DamageCause.FIRE_TICK, 1));
                    }
                    this.fireTicks -= tickDiff;
                }
                if (this.fireTicks <= 0) {
                    this.extinguish();
                } else if (!this.fireProof && (!(this instanceof CloudPlayer) || !((CloudPlayer) this).isSpectator())) {
                    this.data.setFlag(ON_FIRE, true);
                    hasUpdate = true;
                }
            }

            if (this.noDamageTicks > 0) {
                this.noDamageTicks -= tickDiff;
                if (this.noDamageTicks < 0) {
                    this.noDamageTicks = 0;
                }
            }

            if (this.inPortalTicks == 80) {
                EntityPortalEnterEvent ev = new EntityPortalEnterEvent(this, EntityPortalEnterEvent.PortalType.NETHER);
                getServer().getEventManager().fire(ev);

                if (!ev.isCancelled()) {
                    Location newLoc = EnumLevel.moveToNether(this.getLocation());
                    if (newLoc != null) {
                        List<CompletableFuture<CloudChunk>> chunksToLoad = new ArrayList<>();
                        for (int x = -1; x < 2; x++) {
                            for (int z = -1; z < 2; z++) {
                                int chunkX = (newLoc.getChunkX()) + x, chunkZ = (newLoc.getChunkZ()) + z;
                                chunksToLoad.add(((CloudLevel) newLoc.getLevel()).getChunkFuture(chunkX, chunkZ));
                            }
                        }
                        CompletableFutures.allAsList(chunksToLoad).whenComplete((chunks, throwable) -> {
                            if (chunks == null || throwable != null) {
                                return;
                            }

                            this.teleport(newLoc.add(1.5f, 1, 0.5f));
                            BlockBehaviorNetherPortal.spawnPortal(newLoc.getPosition(), newLoc.getLevel());
                        });
                    }
                }
            }

            this.age += tickDiff;
            this.ticksLived += tickDiff;
            TimingsHistory.activatedEntityTicks++;

            return hasUpdate;
        }
    }

    public void updateMovement() {
        float diffPosition = this.position.distanceSquared(this.lastPosition);
        double diffRotation = (this.yaw - this.lastYaw) * (this.yaw - this.lastYaw) + (this.pitch - this.lastPitch) * (this.pitch - this.lastPitch);

        float diffMotion = this.motion.distanceSquared(this.lastMotion);

        if (diffPosition > 0.0001 || diffRotation > 1.0) { //0.2 ** 2, 1.5 ** 2
            this.lastPosition = this.position;

            this.lastYaw = this.yaw;
            this.lastPitch = this.pitch;

            this.addMovement(this.position.getX(), this.position.getY() + this.getBaseOffset(), this.position.getZ(),
                    this.yaw, this.pitch, this.yaw);
        }

        if (diffMotion > 0.0025 || (diffMotion > 0.0001 && this.getMotion().lengthSquared() <= 0.0001)) { //0.05 ** 2
            this.lastMotion = this.motion;

            this.addMotion(this.motion);
        }
    }

    public void addMovement(double x, double y, double z, double yaw, double pitch, double headYaw) {
        this.level.addEntityMovement(this, x, y, z, yaw, pitch, headYaw);
    }

    public void addMotion(Vector3f motion) {
        SetEntityMotionPacket packet = new SetEntityMotionPacket();
        packet.setRuntimeEntityId(this.getRuntimeId());
        packet.setMotion(motion);

        CloudServer.broadcastPacket(this.hasSpawned, packet);
    }

    public Vector3f getDirectionVector() {
        double y = -Math.sin(Math.toRadians(this.getPitch()));
        double xz = Math.cos(Math.toRadians(this.getPitch()));
        double x = -xz * Math.sin(Math.toRadians(this.getYaw()));
        double z = xz * Math.cos(Math.toRadians(this.getYaw()));
        return GenericMath.normalizeSafe(Vector3f.from(x, y, z));
    }

    public Vector2f getDirectionPlane() {
        return Vector2f.from(-Math.cos(Math.toRadians(this.yaw) - Math.PI / 2), -Math.sin(Math.toRadians(this.yaw) - Math.PI / 2)).normalize();
    }

    public Direction getHorizontalDirection() {
        return Direction.fromHorizontalIndex(NukkitMath.floorDouble((this.yaw * 4.0F / 360.0F) + 0.5D) & 3);
    }

    public CardinalDirection getCardinalDirection() {
        return CardinalDirection.values()[NukkitMath.floorDouble((((this.yaw + 180) % 360) / 22.5))];
    }

    public boolean onUpdate(int currentTick) {
        if (this.closed) {
            return false;
        }

        if (!this.isAlive()) {
            ++this.deadTicks;
            if (this.deadTicks >= 10) {
                this.despawnFromAll();
                if (!this.isPlayer) {
                    this.close();
                }
            }
            return this.deadTicks < 10;
        }

        int tickDiff = currentTick - this.lastUpdate;

        if (tickDiff <= 0) {
            return false;
        }

        this.lastUpdate = currentTick;

        boolean hasUpdate = this.entityBaseTick(tickDiff);

        this.updateMovement();

        this.data.update();

        return hasUpdate;
    }

    /**
     * Mount or dismounts an Entity from a/into vehicle
     *
     * @param vehicle The target Entity
     * @return {@code true} if the mounting successful
     */
    @Override
    public boolean mount(Entity vehicle, MountType mode) {
        checkNotNull(vehicle, "The target of the mounting entity can't be null");

        if (this.vehicle != null && !this.vehicle.dismount(this)) {
            return false;
        }

        // Entity entering a vehicle
        EntityVehicleEnterEvent ev = new EntityVehicleEnterEvent(this, (Vehicle) vehicle);
        ((CloudServer)server).getEventManager().fire(ev);
        if (ev.isCancelled()) {
            return false;
        }

        // Add variables to entity
        this.vehicle = vehicle;

        vehicle.onMount(this); // Flags have to be set before
//        this.data.setFlag(RIDING, true);
        this.data.update(); // force any data that needs to be sent
        broadcastLinkPacket(vehicle, EntityLinkData.Type.byId(mode.ordinal()));

        return true;
    }

    public boolean dismount(Entity vehicle) {
        if (this.vehicle == null) {
            // Not in a vehicle
            return false;
        }

        // Run the events
        EntityVehicleExitEvent event = new EntityVehicleExitEvent(this, (Vehicle) vehicle);
        ((CloudServer)server).getEventManager().fire(event);
        if (event.isCancelled()) {
            return false;
        }

        broadcastLinkPacket(vehicle, EntityLinkData.Type.REMOVE);

        // Refurbish the entity
        this.vehicle = null;
//        vehicle.setFlag(RIDING, false);
        vehicle.onDismount(this);

        this.setSeatPosition(Vector3f.ZERO);
        updatePassengerPosition(vehicle);
        this.data.setFlag(MOVING, true);

        return true;
    }

    @Override
    public void onMount(Entity passenger) {
        checkArgument(passenger.getVehicle() == this, "passenger is not in this vehicle");
        checkArgument(this.passengers.add(this), "passenger is already mounted to this vehicle");
        passenger.setSeatPosition(this.getMountedOffset(passenger));
        this.updatePassengerPosition(passenger);
    }

    @Override
    public void onDismount(Entity passenger) {
        checkArgument(passenger.getVehicle() != this, "passenger is still mounted");
        checkArgument(this.passengers.remove(this), "passenger is not in this vehicle");
        passenger.setSeatPosition(Vector3f.ZERO);
    }

    protected void broadcastLinkPacket(Entity vehicle, EntityLinkData.Type type) {
        SetEntityLinkPacket packet = new SetEntityLinkPacket();
        packet.setEntityLink(new EntityLinkData(getUniqueId(), vehicle.getUniqueId(), type, false, false));

        CloudServer.broadcastPacket(((BaseEntity) vehicle).getViewers(), packet);
    }

    public void updatePassengers() {
        if (this.passengers.isEmpty()) {
            return;
        }

        for (Entity passenger : new ArrayList<>(this.passengers)) {
            if (!passenger.isAlive()) {
                dismount(passenger);
                continue;
            }

            updatePassengerPosition(passenger);
        }
    }

    protected void updatePassengerPosition(Entity passenger) {
        passenger.setPosition(this.getPosition().add(passenger.getSeatPosition()));
    }

    public Vector3f getSeatPosition() {
        return this.data.getVector3f(RIDER_SEAT_POSITION);
    }

    public void setSeatPosition(Vector3f pos) {
        this.data.setVector3f(RIDER_SEAT_POSITION, pos);
    }

    public Vector3f getMountedOffset(Entity entity) {
        return Vector3f.from(0f, getHeight() * 0.75f, 0f);
    }

    public final void scheduleUpdate() {
        this.level.scheduleEntityUpdate(this);
    }

    @Override
    public int getNoDamageTicks() {
        return noDamageTicks;
    }

    public void setNoDamageTicks(int noDamageTicks) {
        this.noDamageTicks = noDamageTicks;
    }

    @Override
    public int getFireTicks() {
        return fireTicks;
    }

    @Override
    public void setOnFire(int seconds) {
        int ticks = seconds * 20;
        if (ticks > this.fireTicks) {
            this.fireTicks = ticks;
        }
    }

    public float getAbsorption() {
        return absorption;
    }

    public void setAbsorption(float absorption) {
        if (absorption != this.absorption) {
            this.absorption = absorption;
            if (this instanceof CloudPlayer)
                ((CloudPlayer) this).setAttribute(Attribute.getAttribute(Attribute.ABSORPTION).setValue(absorption));
        }
    }

    public Direction getDirection() {
        double rotation = this.yaw % 360;
        if (rotation < 0) {
            rotation += 360.0;
        }
        if ((0 <= rotation && rotation < 45) || (315 <= rotation && rotation < 360)) {
            return Direction.SOUTH;
        } else if (45 <= rotation && rotation < 135) {
            return Direction.WEST;
        } else if (135 <= rotation && rotation < 225) {
            return Direction.NORTH;
        } else if (225 <= rotation && rotation < 315) {
            return Direction.EAST;
        } else {
            return null;
        }
    }

    public void extinguish() {
        this.fireTicks = 0;
        this.data.setFlag(ON_FIRE, false);
    }

    public boolean canTriggerWalking() {
        return true;
    }

    @Override
    public float getHighestPosition() {
        return highestPosition;
    }

    @Override
    public void setHighestPosition(float highestPosition) {
        this.highestPosition = highestPosition;
    }

    public void setFallDistance(float fallDistance) {
        this.fallDistance = fallDistance;
        this.highestPosition = this.position.getY() + fallDistance;
    }

    public void resetFallDistance() {
        this.highestPosition = 0;
    }

    protected void updateFallState(boolean onGround) {
        if (onGround) {
            fallDistance = this.highestPosition - this.getY();

            if (fallDistance > 0) {
                // check if we fell into at least 1 block of water
                if (this instanceof EntityLiving) {
                    var liquid = this.level.getBlock(this.position.toInt()).getLiquid().getType();

                    if (liquid != WATER && liquid != FLOWING_WATER) {
                        this.fall(fallDistance);
                    }
                }
                this.resetFallDistance();
            }
        }
    }

    public AxisAlignedBB getBoundingBox() {
        return this.boundingBox;
    }

    public void fall(float fallDistance) {
        if (this.hasEffect(EffectTypes.SLOW_FALLING)) {
            return;
        }

        if (this.isPlayer && !level.getGameRules().get(GameRules.FALL_DAMAGE)) {
            return;
        }

        float damage = (float) Math.floor(fallDistance - 3 - (this.hasEffect(EffectTypes.JUMP_BOOST) ? this.getEffect(EffectTypes.JUMP_BOOST).getAmplifier() + 1 : 0));

        if (damage > 0) {
            this.attack(new EntityDamageEvent(this, EntityDamageEvent.DamageCause.FALL, damage));
        }

        if (fallDistance > 0.75) {
            Block down = this.level.getBlock(Direction.DOWN.getUnitVector().add(this.getPosition().toInt()));

            if (down.getState().getType() == FARMLAND) {
                Event ev;

                if (this instanceof CloudPlayer) {
                    ev = new PlayerInteractEvent((Player)this, null, down, null, PlayerInteractEvent.Action.PHYSICAL);
                } else {
                    ev = new EntityInteractEvent(this, down);
                }

                this.server.getEventManager().fire(ev);
                if (ev.isCancelled()) {
                    return;
                }
                this.level.setBlockState(down.getPosition(), CloudBlockRegistry.get().getBlock(BlockTypes.DIRT), false, true);
            }
        }
    }

    public void handleLavaMovement() {
        //todo
    }

    public void onCollideWithPlayer(EntityHuman entityPlayer) {

    }

    @Override
    public void onEntityCollision(Entity entity) {
        if (entity.getVehicle() != this && !entity.getPassengers().contains(this)) {
            double dx = entity.getX() - this.getX();
            double dy = entity.getZ() - this.getZ();
            double dz = NukkitMath.getDirection(dx, dy);

            if (dz >= 0.009999999776482582D) {
                dz = MathHelper.sqrt((float) dz);
                dx /= dz;
                dy /= dz;
                double d3 = 1.0D / dz;

                if (d3 > 1.0D) {
                    d3 = 1.0D;
                }

                dx *= d3;
                dy *= d3;
                dx *= 0.05000000074505806;
                dy *= 0.05000000074505806;
                dx *= 1F + entityCollisionReduction;

                if (this.vehicle == null) {
                    this.motion = this.motion.sub(dx, 0, dy);
                }
            }
        }
    }

    public void onStruckByLightning(LightningBolt lightningBolt) {
        if (this.attack(new EntityDamageByEntityEvent(lightningBolt, this, EntityDamageEvent.DamageCause.LIGHTNING, 5))) {
            if (this.fireTicks < 8 * 20) {
                this.setOnFire(8);
            }
        }
    }

    public boolean onInteract(Player player, ItemStack item, Vector3f clickedPos) {
        return onInteract(player, item);
    }

    public boolean onInteract(Player player, ItemStack item) {
        return false;
    }

    protected boolean switchLevel(CloudLevel targetLevel) {
        checkNotNull(targetLevel, "targetLevel");
        if (this.closed) {
            return false;
        }

        EntityLevelChangeEvent ev = new EntityLevelChangeEvent(this, this.level, targetLevel);
        this.server.getEventManager().fire(ev);
        if (ev.isCancelled()) {
            return false;
        }

        this.level.removeEntity(this);
        if (this.chunk != null) {
            this.chunk.removeEntity(this);
        }
        this.despawnFromAll();

        this.level = targetLevel;
        this.level.addEntity(this);
        this.chunk = null;

        return true;
    }

    public Vector3f getPosition() {
        return this.position;
    }

    public Location getLocation() {
        return Location.from(this.position, this.yaw, this.pitch, this.level);
    }

    public boolean isInsideOfWater() {
        float y = this.getY() + this.getEyeHeight();
        Block block = this.level.getLoadedBlock(this.position.getFloorX(), NukkitMath.floorFloat(y), this.position.getFloorZ());

        if (block == null) {
            return false;
        }

        BlockState state = block.getLiquid();
        var blockType = state.getType();

        float percent;

        if (blockType == WATER || blockType == FLOWING_WATER) {
            percent = BlockBehaviorLiquid.getFluidHeightPercent(state);
        } else {
            return false;
        }

        double f = (block.getPosition().getY() + 1) - (percent - 0.1111111);
        return y < f;
    }

    public boolean isInsideOfSolid() {
        double y = this.getY() + this.getEyeHeight();
        Vector3i pos = Vector3i.from(this.getX(), y, this.getZ());
        BlockState state = this.level.getBlockState(pos);

        if (state == null) {
            return true;
        }

        AxisAlignedBB bb = state.getBehavior().getBoundingBox(pos, state);

        return bb != null && state.inCategory(BlockCategory.SOLID) && !state.inCategory(BlockCategory.TRANSPARENT) && bb.intersectsWith(this.getBoundingBox());

    }

    public boolean isInsideOfFire() {
        for (Block block : this.getCollisionBlocks()) {
            if (block.getState().getType() == FIRE) {
                return true;
            }
        }

        return false;
    }

    public boolean fastMove(float dx, float dy, float dz) {
        if (dx == 0 && dy == 0 && dz == 0) {
            return true;
        }

        try (Timing ignored = Timings.entityMoveTimer.startTiming()) {
            AxisAlignedBB newBB = this.boundingBox.getOffsetBoundingBox(dx, dy, dz);

            if (server.getAllowFlight() || !this.level.hasCollision(this, newBB, false)) {
                this.boundingBox = newBB;
            }

            this.position = Vector3f.from(
                    (this.boundingBox.getMinX() + this.boundingBox.getMaxX()) / 2,
                    this.boundingBox.getMinY() - this.ySize,
                    (this.boundingBox.getMinZ() + this.boundingBox.getMaxZ()) / 2
            );

            this.checkChunks();

            if (!this.onGround || dy != 0) {
                AxisAlignedBB bb = this.boundingBox.clone();
                bb.setMinY(bb.getMinY() - 0.75f);

                this.onGround = this.level.getCollisionBlocks(bb).length > 0;
            }
            this.isCollided = this.onGround;
            this.updateFallState(this.onGround);
            return true;
        }
    }

    public boolean move(Vector3f d) {
        return move(d.getX(), d.getY(), d.getZ());
    }

    public boolean move(float dx, float dy, float dz) {
        if (dx == 0 && dz == 0 && dy == 0) {
            return true;
        }

        if (this.keepMovement) {
            this.boundingBox.offset(dx, dy, dz);
            this.setPosition(this.position = Vector3f.from((this.boundingBox.getMinX() + this.boundingBox.getMaxX()) / 2, this.boundingBox.getMinY(), (this.boundingBox.getMinZ() + this.boundingBox.getMaxZ()) / 2));
            this.onGround = this.isPlayer;
        } else {

            try (Timing ignored = Timings.entityMoveTimer.startTiming()) {
                this.ySize *= 0.4;

                float movX = dx;
                float movY = dy;
                float movZ = dz;

                AxisAlignedBB axisalignedbb = this.boundingBox.clone();

                AxisAlignedBB[] list = this.level.getCollisionCubes(this, this.level.getTickRate() > 1 ? this.boundingBox.getOffsetBoundingBox(dx, dy, dz) : this.boundingBox.addCoord(dx, dy, dz), false, true);

                for (AxisAlignedBB bb : list) {
                    dy = bb.calculateYOffset(this.boundingBox, dy);
                }

                this.boundingBox.offset(0, dy, 0);

                boolean fallingFlag = (this.onGround || (dy != movY && movY < 0));

                for (AxisAlignedBB bb : list) {
                    dx = bb.calculateXOffset(this.boundingBox, dx);
                }

                this.boundingBox.offset(dx, 0, 0);

                for (AxisAlignedBB bb : list) {
                    dz = bb.calculateZOffset(this.boundingBox, dz);
                }

                this.boundingBox.offset(0, 0, dz);

                if (this.getStepHeight() > 0 && fallingFlag && this.ySize < 0.05 && (movX != dx || movZ != dz)) {
                    float cx = dx;
                    float cy = dy;
                    float cz = dz;
                    dx = movX;
                    dy = this.getStepHeight();
                    dz = movZ;

                    AxisAlignedBB axisalignedbb1 = this.boundingBox.clone();

                    this.boundingBox.setBB(axisalignedbb);

                    list = this.level.getCollisionCubes(this, this.boundingBox.addCoord(dx, dy, dz), false);

                    for (AxisAlignedBB bb : list) {
                        dy = bb.calculateYOffset(this.boundingBox, dy);
                    }

                    this.boundingBox.offset(0, dy, 0);

                    for (AxisAlignedBB bb : list) {
                        dx = bb.calculateXOffset(this.boundingBox, dx);
                    }

                    this.boundingBox.offset(dx, 0, 0);

                    for (AxisAlignedBB bb : list) {
                        dz = bb.calculateZOffset(this.boundingBox, dz);
                    }

                    this.boundingBox.offset(0, 0, dz);

                    this.boundingBox.offset(0, 0, dz);

                    if ((cx * cx + cz * cz) >= (dx * dx + dz * dz)) {
                        dx = cx;
                        dy = cy;
                        dz = cz;
                        this.boundingBox.setBB(axisalignedbb1);
                    } else {
                        this.ySize += 0.5;
                    }

                }

                this.position = Vector3f.from(
                        (this.boundingBox.getMinX() + this.boundingBox.getMaxX()) / 2,
                        this.boundingBox.getMinY() - this.ySize,
                        (this.boundingBox.getMinZ() + this.boundingBox.getMaxZ()) / 2
                );

                this.checkChunks();

                this.checkGroundState(movX, movY, movZ, dx, dy, dz);
                this.updateFallState(this.onGround);

                if (movX != dx) {
                    this.motion = Vector3f.from(0, this.motion.getY(), this.motion.getZ());
                }

                if (movY != dy) {
                    this.motion = Vector3f.from(this.motion.getX(), 0, this.motion.getZ());
                }

                if (movZ != dz) {
                    this.motion = Vector3f.from(this.motion.getX(), this.motion.getY(), 0);
                }

                //TODO: vehicle collision events (first we need to spawn them!)
            }
        }
        return true;
    }

    protected void checkGroundState(double movX, double movY, double movZ, double dx, double dy, double dz) {
        this.isCollidedVertically = movY != dy;
        this.isCollidedHorizontally = (movX != dx || movZ != dz);
        this.isCollided = (this.isCollidedHorizontally || this.isCollidedVertically);
        this.onGround = (movY != dy && movY < 0);
    }

    public List<Block> getBlocksAround() {
        if (this.blocksAround == null) {
            int minX = NukkitMath.floorDouble(this.boundingBox.getMinX());
            int minY = NukkitMath.floorDouble(this.boundingBox.getMinY());
            int minZ = NukkitMath.floorDouble(this.boundingBox.getMinZ());
            int maxX = NukkitMath.ceilDouble(this.boundingBox.getMaxX());
            int maxY = NukkitMath.ceilDouble(this.boundingBox.getMaxY());
            int maxZ = NukkitMath.ceilDouble(this.boundingBox.getMaxZ());

            this.blocksAround = new ArrayList<>();

            for (int z = minZ; z <= maxZ; ++z) {
                for (int x = minX; x <= maxX; ++x) {
                    for (int y = minY; y <= maxY; ++y) {
                        this.blocksAround.add(this.level.getBlock(x, y, z));
                    }
                }
            }
        }

        return this.blocksAround;
    }

    public List<Block> getCollisionBlocks() {
        if (this.collisionBlockStates == null) {
            this.collisionBlockStates = new ArrayList<>();

            for (Block b : getBlocksAround()) {
                if (b.getState().getBehavior().collidesWithBB(b, this.getBoundingBox(), true)) {
                    this.collisionBlockStates.add(b);
                }
            }
        }

        return this.collisionBlockStates;
    }

    /**
     * Returns whether this entity can be moved by currents in liquids.
     *
     * @return boolean
     */
    public boolean canBeMovedByCurrents() {
        return true;
    }

    protected void checkBlockCollision() {
        Vector3f vector = Vector3f.ZERO;
        boolean portal = false;

        for (Block block : this.getCollisionBlocks()) {
            var state = block.getState();
            if (state.getType() == PORTAL) {
                portal = true;
                continue;
            }

            var behavior = state.getBehavior();
            behavior.onEntityCollide(block, this);
            vector = behavior.addVelocityToEntity(block, vector, this);
        }

        if (portal) {
            if (this.inPortalTicks < 80) {
                this.inPortalTicks = 80;
            } else {
                this.inPortalTicks++;
            }
        } else {
            this.inPortalTicks = 0;
        }

        if (vector.lengthSquared() > 0) {
            vector = vector.normalize();
            double d = 0.014d;
            this.motion = this.motion.add(vector.mul(d));
        }
    }

    public boolean setPositionAndRotation(Vector3f pos, float yaw, float pitch) {
        if (this.setPosition(pos)) {
            this.setRotation(yaw, pitch);
            return true;
        }

        return false;
    }

    public void setRotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.scheduleUpdate();
    }

    public boolean canTriggerPressurePlate() {
        return true;
    }

    public boolean canPassThrough() {
        return true;
    }

    protected void checkChunks() {
        Vector3f pos = this.getPosition();
        if (this.chunk == null || (this.chunk.getX() != pos.getFloorX() >> 4 || this.chunk.getZ() != pos.getFloorZ() >> 4)) {
            if (this.chunk != null) {
                this.chunk.removeEntity(this);
            }
            this.chunk = this.level.getLoadedChunk(pos);
            if (chunk == null) {
                return; // Converter will throw NPE otherwise.
            }

            if (!this.justCreated) {
                Set<CloudPlayer> loaders = chunk.getPlayerLoaders();
                for (Player player : this.hasSpawned) {
                    if (!loaders.contains(player)) {
                        this.despawnFrom(player);
                    } else {
                        loaders.remove(player);
                    }
                }

                for (Player player : loaders) {
                    this.spawnTo(player);
                }
            }

            if (this.chunk == null) {
                return;
            }

            this.chunk.addEntity(this);
        }
    }

    public boolean setPosition(Vector3f pos) {
        checkNotNull(pos, "position");
        if (this.closed) {
            return false;
        }

        this.position = pos;

        this.recalculateBoundingBox();

        this.checkChunks();

        return true;
    }

    public Vector3f getMotion() {
        return this.motion;
    }

    public boolean setMotion(Vector3f motion) {
        if (!this.justCreated) {
            EntityMotionEvent ev = new EntityMotionEvent(this, motion);
            this.server.getEventManager().fire(ev);
            if (ev.isCancelled()) {
                return false;
            }
        }

        this.motion = motion;

        if (!this.justCreated) {
            this.updateMovement();
        }

        return true;
    }

    public boolean isOnGround() {
        return onGround;
    }

    @Override
    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
    }

    public void kill() {
        this.health = 0;
        this.scheduleUpdate();

        for (Entity passenger : new ArrayList<>(this.passengers)) {
            passenger.dismount(this);
        }
    }

    public boolean teleport(Vector3f pos) {
        return this.teleport(pos, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    public boolean teleport(Vector3f pos, PlayerTeleportEvent.TeleportCause cause) {
        return this.teleport(Location.from(pos, this.yaw, this.pitch, this.level), cause);
    }

    public boolean teleport(Location location) {
        return this.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    public boolean teleport(Location location, PlayerTeleportEvent.TeleportCause cause) {
        float yaw = location.getYaw();
        float pitch = location.getPitch();

        Location from = this.getLocation();
        Location to = location;
        if (cause != null) {
            EntityTeleportEvent ev = new EntityTeleportEvent(this, from, to);
            this.server.getEventManager().fire(ev);
            if (ev.isCancelled()) {
                return false;
            }
            to = ev.getTo();
        }

        if (from.getLevel() != to.getLevel() && !this.switchLevel((CloudLevel) to.getLevel())) {
            return false;
        }

        this.ySize = 0;

        this.setMotion(Vector3f.ZERO);

        if (this.setPositionAndRotation(to.getPosition(), yaw, pitch)) {
            this.resetFallDistance();
            this.onGround = true;

            this.updateMovement();

            return true;
        }

        return false;
    }

    public long getUniqueId() {
        return this.runtimeId;
    }

    public long getRuntimeId() {
        return this.runtimeId;
    }

    public void respawnToAll() {
        for (Player player : this.hasSpawned) {
            this.spawnTo(player);
        }
        this.hasSpawned.clear();
    }

    public void spawnToAll() {
        if (this.chunk == null || this.closed) {
            return;
        }

        for (Player player : this.level.getChunkPlayers(this.chunk.getX(), this.chunk.getZ())) {
            if (player.isOnline()) {
                this.spawnTo(player);
            }
        }
    }

    public void despawnFromAll() {
        for (Player player : this.hasSpawned) {
            this.despawnFrom(player);
        }
    }

    public void close() {
        if (!this.closed) {
            this.closed = true;
            this.server.getEventManager().fire(new EntityDespawnEvent(this));
            this.despawnFromAll();
            if (this.chunk != null) {
                this.chunk.removeEntity(this);
            }

            if (this.level != null) {
                this.level.removeEntity(this);
            }
        }
    }

    @Nullable
    @Override
    public Entity getOwner() {
        if (this.data.contains(OWNER_EID)) {
            return this.level.getEntity(this.data.getLong(OWNER_EID));
        }
        return null;
    }

    @Override
    public void setOwner(@Nullable Entity entity) {
        this.data.setLong(OWNER_EID, entity == null ? -1 : entity.getUniqueId());
    }

    @Override
    public CloudServer getServer() {
        return server;
    }

    @Override
    public String toString() {
        return "Entity(type=" + type.getIdentifier() + ", id=" + getUniqueId() + ")";
    }
}

package com.community.servercore.neoforge;

import com.community.servercore.ServerCoreRuntime;
import com.community.servercore.staff.StaffRole;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Protects Gaming Castle infrastructure from griefing while leaving the actual
 * Survival build territory open to normal gameplay.
 */
final class NeoForgeCityProtectionEvents {
    private static final List<ProtectedRegion> PROTECTED_REGIONS = List.of(
            new ProtectedRegion("Gaming Castle", -265, -25, -70, 185),
            new ProtectedRegion("Market District", 1440, 1560, -60, 60),
            new ProtectedRegion("Duels Arena", -1565, -1435, -65, 65),
            new ProtectedRegion("Staff Lounge", -52, 52, -1552, -1444),
            // Only the Survival landing/return infrastructure is protected.
            new ProtectedRegion("Survival Landing", -20, 20, 1468, 1540));

    private final Supplier<ServerCoreRuntime> runtimeSupplier;

    NeoForgeCityProtectionEvents(Supplier<ServerCoreRuntime> runtimeSupplier) {
        this.runtimeSupplier = runtimeSupplier;
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)
                || !isOverworld(player.level())
                || !isProtected(event.getPos())
                || canEdit(player)) {
            return;
        }
        event.setCanceled(true);
        deny(player, "break blocks");
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level)
                || !isOverworld(level)
                || !isProtected(event.getPos())) {
            return;
        }

        Entity placer = event.getEntity();
        if (placer instanceof ServerPlayer player && canEdit(player)) {
            return;
        }

        event.setCanceled(true);
        if (placer instanceof ServerPlayer player) {
            deny(player, "place blocks");
        }
    }

    @SubscribeEvent
    public void onMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level) || !isOverworld(level)) {
            return;
        }
        boolean protectedPlacement = event.getReplacedBlockSnapshots().stream()
                .anyMatch(snapshot -> isProtected(snapshot.getPos()));
        if (!protectedPlacement) {
            return;
        }

        Entity placer = event.getEntity();
        if (placer instanceof ServerPlayer player && canEdit(player)) {
            return;
        }
        event.setCanceled(true);
        if (placer instanceof ServerPlayer player) {
            deny(player, "place blocks");
        }
    }

    @SubscribeEvent
    public void onToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (!(event.getLevel() instanceof Level level)
                || !isOverworld(level)
                || !isProtected(event.getPos())) {
            return;
        }
        if (event.getPlayer() instanceof ServerPlayer player && canEdit(player)) {
            return;
        }
        event.setCanceled(true);
        if (event.getPlayer() instanceof ServerPlayer player) {
            deny(player, "modify protected blocks");
        }
    }

    @SubscribeEvent
    public void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        if (event.getLevel() instanceof Level level
                && isOverworld(level)
                && (isProtected(event.getPos()) || isProtected(event.getLiquidPos()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getLevel() instanceof Level level
                && isOverworld(level)
                && isProtected(event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate event) {
        if (!isOverworld(event.getLevel())) {
            return;
        }
        event.getAffectedBlocks().removeIf(NeoForgeCityProtectionEvents::isProtected);
        event.getAffectedEntities().removeIf(entity -> isProtected(entity.blockPosition()));
    }

    @SubscribeEvent
    public void onPiston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof Level level) || !isOverworld(level)) {
            return;
        }
        // Vanilla pistons can move a chain of up to 12 blocks. Protect the border
        // from pistons positioned just outside the protected rectangle as well.
        for (int distance = 0; distance <= 13; distance++) {
            if (isProtected(event.getPos().relative(event.getDirection(), distance))) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public void onMobGriefing(EntityMobGriefingEvent event) {
        Entity entity = event.getEntity();
        if (isOverworld(entity.level()) && isProtected(entity.blockPosition())) {
            event.setCanGrief(false);
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !isOverworld(player.level())
                || canEdit(player)) {
            return;
        }

        BlockPos clicked = event.getPos();
        BlockPos adjacent = event.getFace() == null ? clicked : clicked.relative(event.getFace());
        if (!isProtected(clicked) && !isProtected(adjacent)) {
            return;
        }

        ItemStack stack = event.getItemStack();
        if (!isGriefCapableItem(stack)) {
            return;
        }

        event.setCancellationResult(InteractionResult.FAIL);
        event.setCanceled(true);
        deny(player, "use that item here");
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        protectDecorationInteraction(event.getEntity(), event.getTarget(), event);
    }

    @SubscribeEvent
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        protectDecorationInteraction(event.getEntity(), event.getTarget(), event);
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !isOverworld(player.level())
                || canEdit(player)
                || !isProtectedDecoration(event.getTarget())
                || !isProtected(event.getTarget().blockPosition())) {
            return;
        }
        event.setCanceled(true);
        deny(player, "damage protected decorations");
    }

    private void protectDecorationInteraction(
            Entity actor,
            Entity target,
            net.neoforged.bus.api.ICancellableEvent event) {
        if (!(actor instanceof ServerPlayer player)
                || !isOverworld(player.level())
                || canEdit(player)
                || !isProtectedDecoration(target)
                || !isProtected(target.blockPosition())) {
            return;
        }
        event.setCanceled(true);
        deny(player, "modify protected decorations");
    }

    private boolean canEdit(ServerPlayer player) {
        ServerCoreRuntime current = runtimeSupplier.get();
        return current != null && isStaff(current, player);
    }

    private static boolean isGriefCapableItem(ItemStack stack) {
        return stack.getItem() instanceof BucketItem
                || stack.is(Items.FLINT_AND_STEEL)
                || stack.is(Items.FIRE_CHARGE)
                || stack.is(Items.ARMOR_STAND)
                || stack.is(Items.ITEM_FRAME)
                || stack.is(Items.GLOW_ITEM_FRAME)
                || stack.is(Items.PAINTING);
    }

    private static boolean isProtectedDecoration(Entity entity) {
        return entity instanceof HangingEntity || entity instanceof ArmorStand;
    }

    private static boolean isOverworld(Level level) {
        return Level.OVERWORLD.equals(level.dimension());
    }

    private static boolean isProtected(BlockPos pos) {
        return regionAt(pos) != null;
    }

    private static ProtectedRegion regionAt(BlockPos pos) {
        for (ProtectedRegion region : PROTECTED_REGIONS) {
            if (region.contains(pos)) {
                return region;
            }
        }
        return null;
    }

    private static boolean isStaff(ServerCoreRuntime runtime, ServerPlayer player) {
        for (StaffRole role : StaffRole.values()) {
            if (runtime.roleStore().has(player.getUUID(), role.permission())) {
                return true;
            }
        }
        return false;
    }

    private static void deny(ServerPlayer player, String action) {
        ProtectedRegion region = regionAt(player.blockPosition());
        String area = region == null ? "Gaming Castle infrastructure" : region.name();
        player.displayClientMessage(
                Component.literal(area + " is protected. Only staff can " + action + "."),
                true);
    }

    private record ProtectedRegion(String name, int minX, int maxX, int minZ, int maxZ) {
        private boolean contains(BlockPos pos) {
            return pos.getX() >= minX
                    && pos.getX() <= maxX
                    && pos.getZ() >= minZ
                    && pos.getZ() <= maxZ;
        }
    }
}

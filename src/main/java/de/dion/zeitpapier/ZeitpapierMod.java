package de.dion.zeitpapier;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(ZeitpapierMod.MOD_ID)
public final class ZeitpapierMod {
    public static final String MOD_ID = "zeitpapier";
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> ZEITPAPIER = ITEMS.register("zeitpapier", ZeitpapierItem::new);
    private double fractionalTicks;

    public ZeitpapierMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modBus);
        modBus.addListener(this::addToCreativeTab);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(ZEITPAPIER.get());
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("zeitpapier")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("geben").executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                if (!player.addItem(new ItemStack(ZEITPAPIER.get()))) player.drop(new ItemStack(ZEITPAPIER.get()), false);
                ctx.getSource().sendSuccess(() -> Component.literal("Du hast das Zeitpapier erhalten."), false);
                return 1;
            }))
            .then(Commands.literal("set")
                .then(Commands.argument("tag_sekunden", IntegerArgumentType.integer(5, 86400))
                    .then(Commands.argument("nacht_sekunden", IntegerArgumentType.integer(5, 86400))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            if (!player.getMainHandItem().is(ZEITPAPIER.get()) && !player.getOffhandItem().is(ZEITPAPIER.get())) {
                                ctx.getSource().sendFailure(Component.literal("Halte zuerst das Zeitpapier in einer Hand."));
                                return 0;
                            }
                            int day = IntegerArgumentType.getInteger(ctx, "tag_sekunden");
                            int night = IntegerArgumentType.getInteger(ctx, "nacht_sekunden");
                            TimeSettings data = TimeSettings.get(ctx.getSource().getServer().overworld());
                            data.set(day, night);
                            ctx.getSource().sendSuccess(() -> Component.literal("Gespeichert: Tag " + day + " Sekunden, Nacht " + night + " Sekunden."), true);
                            return 1;
                        }))))
            .then(Commands.literal("info").executes(ctx -> {
                TimeSettings data = TimeSettings.get(ctx.getSource().getServer().overworld());
                ctx.getSource().sendSuccess(() -> Component.literal("Aktuell: Tag " + data.daySeconds() + " Sekunden, Nacht " + data.nightSeconds() + " Sekunden."), false);
                return 1;
            }))
            .then(Commands.literal("normal").executes(ctx -> {
                TimeSettings data = TimeSettings.get(ctx.getSource().getServer().overworld());
                data.set(600, 600);
                ctx.getSource().sendSuccess(() -> Component.literal("Normaler Minecraft-Zyklus wiederhergestellt: 10 Minuten Tag, 10 Minuten Nacht."), true);
                return 1;
            })));
    }

    @SubscribeEvent
    public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ServerLevel level = event.getServer().overworld();
        if (!level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) return;
        TimeSettings settings = TimeSettings.get(level);
        long phaseTime = Math.floorMod(level.getDayTime(), 24000L);
        int desiredSeconds = phaseTime < 12000L ? settings.daySeconds() : settings.nightSeconds();
        double targetAdvance = 12000.0 / (desiredSeconds * 20.0);
        fractionalTicks += targetAdvance - 1.0;
        long correction = (long) fractionalTicks;
        if (correction != 0L) {
            level.setDayTime(level.getDayTime() + correction);
            fractionalTicks -= correction;
        }
    }

    private static final class ZeitpapierItem extends Item {
        private ZeitpapierItem() { super(new Item.Properties().stacksTo(1)); }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide && player.isShiftKeyDown() && player instanceof ServerPlayer serverPlayer) {
                if (!serverPlayer.hasPermissions(2)) {
                    serverPlayer.sendSystemMessage(Component.literal("Nur Admins/Operatoren duerfen das Zeitpapier benutzen."));
                } else {
                    TimeSettings data = TimeSettings.get(serverPlayer.getServer().overworld());
                    serverPlayer.sendSystemMessage(Component.literal("Zeitpapier: Tag " + data.daySeconds() + " s, Nacht " + data.nightSeconds() + " s."));
                    serverPlayer.sendSystemMessage(Component.literal("Aendern: /zeitpapier set <Tag-Sekunden> <Nacht-Sekunden>"));
                }
                return InteractionResultHolder.success(stack);
            }
            return InteractionResultHolder.pass(stack);
        }
    }
}

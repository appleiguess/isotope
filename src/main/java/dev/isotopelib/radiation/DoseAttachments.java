package dev.isotopelib.radiation;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import dev.isotopelib.Isotope;

import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Dose and dose rates of a living entity. A dose saved as goingnuclear:dose_msv is read as isotope:dose_msv: the old name is a
 * registry alias, so a world from before the split keeps its doses and is saved under the new name from then on.
 */
public final class DoseAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Isotope.MODID);

    /** Total dose, mSv. */
    public static final Supplier<AttachmentType<Float>> DOSE_MSV = ATTACHMENTS.register("dose_msv",
            () -> AttachmentType.builder(() -> 0f).serialize(Codec.FLOAT).sync(ByteBufCodecs.FLOAT).copyOnDeath().build());

    /** Dose rate around the entity, in microsieverts per second, before protection. */
    public static final Supplier<AttachmentType<Float>> AMBIENT_USV = ATTACHMENTS.register("ambient_usv",
            () -> AttachmentType.builder(() -> 0f).sync(ByteBufCodecs.FLOAT).build());

    /** Dose rate the entity takes, in microsieverts per second, after protection. */
    public static final Supplier<AttachmentType<Float>> EFFECTIVE_USV = ATTACHMENTS.register("effective_usv",
            () -> AttachmentType.builder(() -> 0f).sync(ByteBufCodecs.FLOAT).build());

    static {
        for (String name : new String[]{"dose_msv", "ambient_usv", "effective_usv"})
            ATTACHMENTS.addAlias(Isotope.legacyId(name), Isotope.id(name));
    }

    private DoseAttachments() {
    }
}

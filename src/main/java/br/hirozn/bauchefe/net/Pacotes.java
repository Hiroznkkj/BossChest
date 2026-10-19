package br.hirozn.bauchefe.net;

import br.hirozn.bauchefe.BauChefe;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Pacotes entre PC e servidor. */
public final class Pacotes {
	private Pacotes() {}

	private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> tipo(String n) {
		return new CustomPacketPayload.Type<>(BauChefe.id(n));
	}

	/** PC -> servidor: Shift+V apertado. */
	public record Abrir() implements CustomPacketPayload {
		public static final Type<Abrir> TYPE = tipo("abrir");
		public static final StreamCodec<ByteBuf, Abrir> CODEC = StreamCodec.unit(new Abrir());
		@Override public Type<Abrir> type() { return TYPE; }
	}

	/** PC -> servidor: sacar 'qtd' do item. */
	public record Sacar(ItemStack item, int qtd) implements CustomPacketPayload {
		public static final Type<Sacar> TYPE = tipo("sacar");
		public static final StreamCodec<RegistryFriendlyByteBuf, Sacar> CODEC = StreamCodec.composite(
				ItemStack.STREAM_CODEC, Sacar::item, ByteBufCodecs.VAR_INT, Sacar::qtd, Sacar::new);
		@Override public Type<Sacar> type() { return TYPE; }
	}

	/** PC -> servidor: guardar o inventário (menos a barra). */
	public record Guardar() implements CustomPacketPayload {
		public static final Type<Guardar> TYPE = tipo("guardar");
		public static final StreamCodec<ByteBuf, Guardar> CODEC = StreamCodec.unit(new Guardar());
		@Override public Type<Guardar> type() { return TYPE; }
	}

	public record Linha(ItemStack item, long qtd) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Linha> CODEC = StreamCodec.composite(
				ItemStack.STREAM_CODEC, Linha::item, ByteBufCodecs.VAR_LONG, Linha::qtd, Linha::new);
	}

	/** Servidor -> PC: o estoque da rede (abre ou atualiza a tela). 'aviso' aparece no rodapé. */
	public record Estoque(List<Linha> linhas, int baus, String aviso) implements CustomPacketPayload {
		public static final Type<Estoque> TYPE = tipo("estoque");
		public static final StreamCodec<RegistryFriendlyByteBuf, Estoque> CODEC = StreamCodec.composite(
				Linha.CODEC.apply(ByteBufCodecs.collection(ArrayList::new)), Estoque::linhas,
				ByteBufCodecs.VAR_INT, Estoque::baus,
				ByteBufCodecs.STRING_UTF8, Estoque::aviso,
				Estoque::new);
		@Override public Type<Estoque> type() { return TYPE; }
	}

	/**
	 * Servidor -> PC: o Márcio (id da entidade) falou; o PC desenha o balão e toca a voz ('som' vazio = sem áudio).
	 * 'humor' (Falas.humor) é o jeito de falar: o corpo acompanha (cabisbaixo, animado, bravo...).
	 */
	public record Fala(int villager, String texto, String som, int humor) implements CustomPacketPayload {
		public static final Type<Fala> TYPE = tipo("fala");
		public static final StreamCodec<ByteBuf, Fala> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Fala::villager, ByteBufCodecs.STRING_UTF8, Fala::texto, ByteBufCodecs.STRING_UTF8, Fala::som,
				ByteBufCodecs.VAR_INT, Fala::humor, Fala::new);
		@Override public Type<Fala> type() { return TYPE; }
	}

	/**
	 * Servidor -> PC: reação procedural do Márcio. 'alvo' é o id de uma entidade (o jogador do hi five) ou -1;
	 * x, y, z é de onde veio a coisa (o raio do susto). Tipos em Reacao.SUSTO...
	 */
	public record Reacao(int villager, int tipo, int alvo, double x, double y, double z) implements CustomPacketPayload {
		public static final int SUSTO = 1, OFERECE = 2, BATE = 3, LIVRO = 4, GUARDA = 5;
		public static final Type<Reacao> TYPE = Pacotes.tipo("reacao");
		public static final StreamCodec<ByteBuf, Reacao> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Reacao::villager, ByteBufCodecs.VAR_INT, Reacao::tipo, ByteBufCodecs.VAR_INT, Reacao::alvo,
				ByteBufCodecs.DOUBLE, Reacao::x, ByteBufCodecs.DOUBLE, Reacao::y, ByteBufCodecs.DOUBLE, Reacao::z, Reacao::new);
		@Override public Type<Reacao> type() { return TYPE; }
	}

	/** Servidor -> PC: o Márcio começou uma animação especial (AnimMarcio). */
	public record Anim(int villager, int anim) implements CustomPacketPayload {
		public static final Type<Anim> TYPE = tipo("anim");
		public static final StreamCodec<ByteBuf, Anim> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Anim::villager, ByteBufCodecs.VAR_INT, Anim::anim, Anim::new);
		@Override public Type<Anim> type() { return TYPE; }
	}
}

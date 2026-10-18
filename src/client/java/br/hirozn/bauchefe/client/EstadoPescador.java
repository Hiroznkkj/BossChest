package br.hirozn.bauchefe.client;

/** Campos extras no VillagerRenderState (via mixin) para animar o pescador. */
public interface EstadoPescador {
	boolean bauchefe$pescador();
	int bauchefe$fase();
	float bauchefe$tempo();
	void bauchefe$set(boolean pescador, int fase, float tempo);
}

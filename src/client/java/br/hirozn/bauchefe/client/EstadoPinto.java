package br.hirozn.bauchefe.client;

/** Campos extras no ChickenRenderState (via mixin) para animar o Pinto do Márcio. */
public interface EstadoPinto {
	/** Sentado no baú do barco do Márcio. */
	boolean bauchefe$sentado();
	/** Ticks desde que começou o carinho, ou -1. */
	float bauchefe$carinho();
	float bauchefe$fase();
	void bauchefe$set(boolean sentado, float carinho, float fase);
}

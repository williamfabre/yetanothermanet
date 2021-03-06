package ara.manet.communication;

import ara.manet.positioning.PositionProtocol;
import ara.util.Message;
import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.Network;
import peersim.core.Node;
import peersim.core.Protocol;
import peersim.edsim.EDProtocol;
import peersim.edsim.EDSimulator;

public class EmitterProtocolImpl implements Emitter {

	private static final String PAR_LATENCY = "latency";
	private static final String PAR_SCOPE = "scope";
	private static final String PAR_VARIANCE = "variance";

	private final int my_pid; // protocol du node
	private final int latency; // Latence parametrique
	private final int scope; // visibilite� d'un node
	private final boolean variance; // variance sur la latence avec une loi de Poisson

	/**
	 * Constructeur permettant de recuperer les parametres de config.
	 * 
	 * @param prefix prefix
	 */
	public EmitterProtocolImpl(String prefix) {

		String tmp[] = prefix.split("\\.");
		my_pid = Configuration.lookupPid(tmp[tmp.length - 1]);

		this.latency = Configuration.getInt(prefix + "." + PAR_LATENCY);
		this.scope = Configuration.getInt(prefix + "." + PAR_SCOPE);
		this.variance = Configuration.getBoolean(prefix + "." + PAR_VARIANCE);
	}

	public Object clone() {

		EmitterProtocolImpl emp = null;
		try {
			emp = (EmitterProtocolImpl) super.clone();
		} catch (CloneNotSupportedException e) {
		}

		return emp;
	}

	/**
	 * Cette methode recupere le bon protocole et fait une delivrance de partir du
	 * host, du pid du message et du message
	 * 
	 * @param host Noeud host
	 * @param msg  Message a dlivrer
	 */
	public void deliver(Node host, Message msg) {

		Protocol p = (Protocol) host.getProtocol(msg.getPid());
		((EDProtocol) p).processEvent(host, msg.getPid(), msg);

	}

	/**
	 * Cette methode gere la reception d'un message. Le message est recu sur phost
	 * si pemitter est non null et dans le scope.
	 * 
	 * @param host Noeud qui recoit le message
	 * @param msg  Message recu
	 */
	public void recvMsg(Node host, Message msg) {

		Node emitter = null;

		// Recherche dans tout le network a qui appartient ce message, c'est l'emitter.
		for (int i = 0; i < Network.size(); i++) {
			if (Network.get(i).getID() == msg.getIdSrc()) {
				emitter = Network.get(i);
				break;
			}
		}

		// Si l'�mitter est null probl�me.
		if (emitter == null) {
			return;
		}

		// Si c'est moi meme alors je delivre.
		if (emitter.getID() == host.getID()) {
			deliver(host, msg);
			return;
		}

		// Recuperation de la position pour savoir si je peux delivrer le message
		// La distance entre pemitter doit etre dans le scope de phost poour la
		// delivrance.

		
		int position_pid = Configuration.lookupPid("position");
		PositionProtocol pemitter = (PositionProtocol) emitter.getProtocol(position_pid);
		PositionProtocol phost = (PositionProtocol) host.getProtocol(position_pid);
		double distance = pemitter.getCurrentPosition().distance(phost.getCurrentPosition());

		if (distance <= getScope()) {
			if (msg.getIdDest() == host.getID() || msg.getIdDest() == ALL)
				deliver(host, msg);
			return;
		}
	}

	/**
	 * Cette methode gere l'emission d'un message. Le message est recu sur phost si
	 * pemitter est non null et dans le scope.
	 * 
	 * @param host Noeud qui recoit le message
	 * @param msg  Message recu
	 */
	@Override
	public void emit(Node host, Message msg) {

		Node dest = null;
		boolean broadcast = false;
		double distance = 0;

		if (msg.getIdDest() == ALL)
			broadcast = true;

		int position_pid = Configuration.lookupPid("position");
		//PositionProtocol phost = (PositionProtocol) host.getProtocol(position_pid);
		// System.out.println(host.getID() + " host " + phost.getCurrentPosition());

		for (int i = 0; i < Network.size(); i++) {

			dest = Network.get(i);

			if (dest.getID() == msg.getIdDest() || broadcast) {

				//PositionProtocol pdest = (PositionProtocol) dest.getProtocol(position_pid);

				// System.out.println(dest.getID() + " dest " + pdest.getCurrentPosition());
				//distance = phost.getCurrentPosition().distance(pdest.getCurrentPosition());

				if (distance <= getScope()) {

					EDSimulator.add(getLatency(), msg, dest, my_pid);
				}

				if (!broadcast)
					return;
			}
		}
	}

	/**
	 * @return Renvoie la latence selon une loi de poisson. Si variance est vraie.
	 */
	@Override
	public int getLatency() {

		if (this.variance) {

			return CommonState.r.nextPoisson(latency);
		}
		return this.latency;
	}

	/**
	 * @return le scope du current node.
	 */
	@Override
	public int getScope() {

		return this.scope;
	}
	

	/**
	 * Permet la gestion d'evenement de type Message sur le protocol d'emission
	 * 
	 * @param host  node
	 * @param pid   protocol
	 * @param event evenement
	 */
	public void processEvent(Node host, int pid, Object event) {

		if (pid != my_pid) {

			throw new RuntimeException("Receive Event for wrong protocol");
		}

		if (event instanceof Message) {

			Message msg = (Message) event;
			recvMsg(host, msg);
			return;
		}

		throw new RuntimeException("Receive unknown Event");
	}
}

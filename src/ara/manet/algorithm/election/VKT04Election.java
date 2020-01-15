package ara.manet.algorithm.election;

import java.util.ArrayList;
import java.util.List;

import ara.manet.Monitorable;
import ara.manet.communication.EmitterProtocolImpl;
import ara.manet.detection.NeighborProtocolImpl;
import ara.manet.detection.NeighborhoodListener;
import ara.util.AckMessage;
import ara.util.ElectionDynamicMessage;
import ara.util.LeaderMessage;
import ara.util.ProbeMessage;
import ara.util.ReplyMessage;
import peersim.config.Configuration;
import peersim.core.Network;
import peersim.core.Node;
import peersim.edsim.EDSimulator;
import peersim.util.ExtendedRandom;
	
public class VKT04Election implements ElectionProtocol, Monitorable, NeighborhoodListener {

	private static final long ALL = -2; // Broadcast == true
	
	private static final String PAR_PERIODE_LEADER = "periode_leader";
	private static final String PAR_PERIODE_NEIGHBOR = "periode_neighbor";
	private static final String PAR_TIMER = "timer";
	private static final String PAR_SCOPE = "scope";
	public static final String leader_event = "LEADEREVENT";
	public static final String leader_event_print = "LEADEREVENT_PRINT";
	
	private int my_pid; 							// protocol
	
	private final int periode_leader;				// duree entre deux elections 

	private final int periode_neighbor;				// delay avant declenchement timer 
													// entre deux check de mes voisins

	private final long timer_event;					// Tant qu'il est armee, les noeuds de la liste 
													// des neighbors sont consideres comme voisins
													// apres timer seconde ils disparaissent de la liste.
	
	private int scope;								// visibilit� d'un node
	
	private List<Long> neighbors;					// Liste de voisins.
	private List<Integer> values; 					// Valeur n�cessaire pour les leader protocol.
	private List<Long> neighbors_ack;				// permet de compter le nombre de ack				
	private int desirability; 						// desirabilit� du noeud									(-1 si inconnu)
	private long parent; 							// permet de conna�tre son p�re et remonter dans l'arbre 	(-1 si inconnu)
	private long id_leader;							// id du leader actuel, -1 si aucun leader.					(-1 si inconnu)
	private long desirability_leader;				// desirabilit� du noeud leader								(-1 si inconnu)
	private long potential_leader;					// id du leader potentiel, -1 si aucun leader.				(-1 si inconnu)
	private long desirability_potential_leader;		// d�sirabilit� du leader potentiel, -1 si aucun leader.	(-1 si inconnu)
	
	// new variables for dynamic protocol
	private long is_electing;		// Variable qui dit si ce noeud est en train de faire une �l�ction.			(0 si inconnu)
	private long ack_2_parent;		// Variable qui dit si ce noeud a envoy� son ack � son p�re.				(0 si inconnu)
	private long source_election;	// Noeud d'o� provient l'�lection dans laquelle je suis.					(-1 si inconnu)
	private long ieme_election;		// indique pour ce node la ieme election qu'il lance.						(0 si inconnu)
									// utile pour differencier les elections et choisir parmi elles.			
									// Plus un node lance d'election plus il a de chance d'en lancer.
									// Soit i,j Node� : (i.ieme_election(), i.getID()) > (j.ieme_election(), j.getID())
									// <=> i.ieme_election() > j.ieme_election() ||
									// (i.ieme_election() == j.ieme_election()) &&  (i.getID() >  j.getID())
	private long ieme_election_max;	// La plus grande election � laquelle j'ai particip�.						(0 si inconnu)
	
	private int state;								// 0 : leader_known
													// 1 : leader_unknown
													// 2 : leader_isMe
	
public VKT04Election(String prefix) {
		
		String tmp[] = prefix.split("\\.");
		my_pid = Configuration.lookupPid(tmp[tmp.length - 1]);
		
		this.periode_leader = Configuration.getInt(prefix + "." + PAR_PERIODE_LEADER);
		this.periode_neighbor = Configuration.getInt(prefix + "." + PAR_PERIODE_NEIGHBOR);
		this.timer_event = Configuration.getInt(prefix + "." + PAR_TIMER);
		this.scope = Configuration.getInt(prefix + "." + PAR_SCOPE);
		
		// Creation de liste privees.
		neighbors = new ArrayList<Long>(); 		// Liste des voisins
		values = new ArrayList<Integer>(); 		// liste des valeurs
		neighbors_ack = new ArrayList<Long>(); 	// liste noeuds qui ont ack
		parent = -1;
		id_leader = -1;
		desirability_leader = -1;
		potential_leader = -1;
		desirability_potential_leader = -1;
		state = 1;
		is_electing = 0;
		ack_2_parent = 0;
		source_election = -1;
		ieme_election = 0;
		ieme_election_max = 0;	

	}
	
	public Object clone() {
		VKT04Election vkt = null;
		try {
			vkt = (VKT04Election) super.clone();
			vkt.neighbors = new ArrayList<Long>(); 		// Liste des voisins
			vkt.values = new ArrayList<Integer>(); 		// liste des valeurs
			vkt.neighbors_ack = new ArrayList<Long>(); 	// liste noeuds qui ont ack
			vkt.parent = -1;
			vkt.id_leader = -1;
			vkt.desirability_leader = -1;
			vkt.potential_leader = -1;
			vkt.desirability_potential_leader = -1;
			vkt.state = 1;
			
			vkt.is_electing = 0;
			vkt.ack_2_parent = 0;
			vkt.source_election = -1;
			vkt.ieme_election = 0;
			vkt.ieme_election_max = 0;	
		} catch (CloneNotSupportedException e) {
		}
		return vkt;
	}
	
	/**
	 * Fonction utilis� par la classe d'initialisation qui est appel�e
	 * en d�but de programme pour tous les noeuds.
	 * Elle a pour but d'initialis� la d�sirability du node avec son ID en param�tre.
	 * 
	 * @param node le node en lui m�me
	 */
	public void initialisation(Node node) {
		//ExtendedRandom my_random = new ExtendedRandom(10);
		//this.desirability = (int) (my_random.nextInt(1000) / (node.getID() + 1));
		this.desirability = node.getIndex();
		
		// TODO initialiser le leader � m�me au d�part? id_leader ?
		// Trouves tes voisins
		EDSimulator.add(periode_neighbor, timer_event, node, my_pid);
	}
	
	
	/*****************************Election******************************/	
	
	/**
	 * Partie �lection statique, va lancer une nouvelle �lection
	 * avec la liste statique des neouds.
	 * 
	 * @param host
	 */
	void VKT04StaticElectionTrigger(Node host) {

		// R�cup�ration du protocol de communication
		int emitter_pid = Configuration.lookupPid("emit");
		EmitterProtocolImpl emp = (EmitterProtocolImpl) host.getProtocol((emitter_pid));
		
		// D�but d'une demande d'�l�ction globale, mise � jour du node
		// pour d�buter une �l�ction
		this.state = 1;
		this.parent = -1;
		this.id_leader = -1;
		this.desirability_leader = -1;
		this.desirability_potential_leader = desirability;
		this.potential_leader = host.getID();
		
		is_electing = 1;									// je suis passe en mode election.
		ack_2_parent = 1;									// je n'ai pas besoin d'attendre un ack.
		source_election = host.getID();						// je suis le createur de cette election.
		ieme_election++;									// Pour calculer la priorite de mon election.
		ieme_election_max = Math.max(ieme_election_max, ieme_election);
		
		ElectionDynamicMessage edm = new ElectionDynamicMessage(host.getID(), ALL, host.getID(), ieme_election, my_pid);
		emp.emit(host, edm);
		
		// Ajouter de la variance pour ne pas que les noeuds lance tout le temps des �lections
		// exactement en m�me temps.
		// EDSimulator.add(periode_leader, leader_event, host, my_pid);
	}
	
	/**
	 * TODO
	 * 
	 * @param host
	 * @param event
	 */
	private void recvElectionDynamicMsg(Node host, ElectionDynamicMessage event) {
		
		int emitter_pid = Configuration.lookupPid("emit");
		EmitterProtocolImpl emp = (EmitterProtocolImpl) host.getProtocol((emitter_pid));
		
		ElectionDynamicMessage em = (ElectionDynamicMessage)event;
		
		// Si je n'ai pas de parent j'ajoute l'envoyeur comme mon p�re
		// le ack message attendra que j'ai re�u une r�ponse de tous
		// mes fils.
		if (this.parent == -1) {
			
			if (em.getIdSrc() != host.getID()) {
				// Ce noeud est mon p�re
				this.parent = em.getIdSrc();

				// Je ne dois pas attendre mon p�re
				neighbors_ack.remove(this.parent);
			
				// Propagation aux fils
				for (Long neinei : neighbors_ack) {
					
					Node dest = Network.get(neinei.intValue()); // TODO ??????
					if(dest.getID() == parent) { continue; } // Skip l'id du pere
					ElectionDynamicMessage em_propagation = new ElectionDynamicMessage(host.getID(), dest.getID(), em.getSource_election(), ieme_election, my_pid);
					emp.emit(host, em_propagation);
				}
			}
		} else {
			
			// J'ai d�j� un parent, r�ponse immediate de la valeur potentielle
			AckMessage am = new AckMessage(host.getID(), em.getIdSrc(), my_pid, potential_leader, desirability_potential_leader);
			emp.emit(host, am);
		}
		return;
	}

	
	/**
	 * TODO 
	 * 
	 * @param host
	 * @param event
	 */
	private void recvAckMsg(Node host, AckMessage event) {

		int emitter_pid = Configuration.lookupPid("emit");
		EmitterProtocolImpl emp = (EmitterProtocolImpl) host.getProtocol((emitter_pid));
		
		AckMessage am = (AckMessage)event;
		
		// Mise a jour de mon noeud leader si le leader que 
		// j'ai est moins d�sirable.
		if (am.getMostValuedNodeDesirability() > this.desirability_potential_leader) {
			this.potential_leader = am.getMostValuedNode();
			this.desirability_potential_leader = am.getMostValuedNodeDesirability();
		}
		
		// J'ai re�u un ack de ce node c'est bon !
		neighbors_ack.remove(am.getIdSrc()); // remove is empty safe.
		// Je suis une feuille ou il n'y avait qu'un fils � attendre
		if (neighbors_ack.isEmpty()) {

			// Fin de l'�lection je suis le noeud de d�part TODO??
			// je dois maintenant propager ma valeur. TODO ??
			if (parent == -1) {
				id_leader = potential_leader;
				desirability_leader = desirability_potential_leader;
				
				if (id_leader == host.getID()) {
					state = 2; 		// 2 : leader_isMe
				} else {
					state = 0;		// 0 : leader_known
				}

				// Broadcast du message de leader
				LeaderMessage lm_broadcast = new LeaderMessage(host.getID(), ALL, my_pid, id_leader, desirability_leader);
				emp.emit(host, lm_broadcast);
			} else {
				
				// Envoie d'un ack � mon p�re, je suis une feuille
				AckMessage am_to_father = new AckMessage(host.getID(), parent, my_pid, potential_leader, desirability_potential_leader);
				emp.emit(host, am_to_father);
			}
		}
		
	}	
	
	/**
	 * TODO 
	 * 
	 * @param host
	 * @param event
	 */
	private void recvLeaderlMsg(Node host, LeaderMessage event) {
		int emitter_pid = Configuration.lookupPid("emit");
		EmitterProtocolImpl emp = (EmitterProtocolImpl) host.getProtocol((emitter_pid));
		
		LeaderMessage lm = (LeaderMessage)event;
		
		if (state == 1) { // 1 : leader_unknown

			if (lm.getMostValuedNode() == host.getID()) {
				state = 2; 		// 2 : leader_isMe
				id_leader = host.getID();
				desirability_leader = desirability;
			} else {
				state = 0;		// 0 : leader_known
				id_leader = lm.getMostValuedNode();
				desirability_leader = lm.getMostValuedNodeDesirability();
			}
			
			LeaderMessage lm_propagate = new LeaderMessage(host.getID(), ALL, my_pid, id_leader, desirability_leader);
			emp.emit(host, lm_propagate);
		}	
	}
	
	/**
	 * TODO 
	 * 
	 * @param host
	 * @param event
	 */
	private void recvProbeMessage(Node Host, ProbeMessage event) {
		// Je dois repondre a cette personne avec un message de type reply
		return;
	}
	
	/**
	 * TODO 
	 * 
	 * @param host
	 * @param event
	 */
	private void recvReplyMessage(Node Host, ReplyMessage event) {
		// Arme a nouveau le timer pour preparer un nouveau Probe Message
		return;
	}


	/********************************ELECTION PROTOCOL**********************************/
	@Override
	public long getIDLeader() {
		return id_leader;
	}

	@Override
	public int getValue() {
		return this.desirability;
	}
	
	/*****************************NEIGHBORHOOD Listener******************************/
	/**
	 * TODO
	 */
	@Override
	public void newNeighborDetected(Node host, long id_new_neighbor) {
		int neighbor_pid = Configuration.lookupPid("neighbor");
		NeighborProtocolImpl np = (NeighborProtocolImpl) host.getProtocol(neighbor_pid);
	}
	
	/**
	 * TODO
	 */
	@Override
	public void lostNeighborDetected(Node host, long id_lost_neighbor) {
		int neighbor_pid = Configuration.lookupPid("neighbor");
		NeighborProtocolImpl np = (NeighborProtocolImpl) host.getProtocol(neighbor_pid);
	}
	
	/********************************MONITORABLE**********************************/
	/* MONITORABLE : impl�mente l'interface Monitorable pour afficher sur le moniteur 
	 * graphique l'�tat de chaque noeud : on peut diff�rencier dans 
	 * cet algorithme trois �tats : 
	 * 
	 * * leader inconnu,
	 * * leader connu, 
	 * * �tre le leader.
	 */
	
	/* permet d'obtenir le nombre d'�tat applicatif du noeud */
	public int nbState() {
		return 3;
	}

	/* permet d'obtenir l'�tat courant du noeud */
	@Override
	public  int getState(Node host) {
		return state;
	}

	/*
	 * permet d'obtenir une liste de chaine de caract�re, affichable en colonne �
	 * cot� du noeud sur un moniteur graphique
	 */
	public List<String> infos(Node host) {
		List<String> res = new ArrayList<String>();
		res.add("Node" + host.getID() + " Boss["+ getIDLeader() + "]");
		res.add(" PBoss["+ potential_leader + "]" + "\n Val(" + getValue() + ")");
		return res;
	}
	
	
	
	/********************************ProcessEvent**********************************/	
	@Override
	public void processEvent(Node host, int pid, Object event) {
		
		if (pid != my_pid) {
			throw new RuntimeException("Receive Event for wrong protocol");
		}
		
		// Gestion de la r�ception d'un message de type ElectionMessage
		if (event instanceof ElectionDynamicMessage) {
			recvElectionDynamicMsg(host, (ElectionDynamicMessage) event);
			return;
		}
		
		// Gestion de la r�ception d'un message de type LeaderMessage
		if (event instanceof LeaderMessage) {
			recvLeaderlMsg(host, (LeaderMessage) event);
			return;
		}

		// Gestion de la r�ception d'un message de type AckMessage
		if (event instanceof AckMessage) {
			recvAckMsg(host, (AckMessage) event);
			return;
		}

		// Gestion de la reception d'un message de type ProbeMessage
		if (event instanceof ProbeMessage) {
			recvProbeMessage(host, (ProbeMessage) event);
			return;
		}
		
		// Gestion de la reception d'un message de type ProbeMessage
		if (event instanceof ReplyMessage) {
			recvReplyMessage(host, (ReplyMessage) event);
			return;
		}
		
		
		// Je dois verifier la liste de mes voisins a chaque periode de temps
		// nommee timer.
		if (event.equals(timer_event)) {
			//staticDetection(host);
			return;
		}
		
		// Ev�nement p�riodique d'�lections.		
		if (event instanceof String) {
			String ev = (String) event;

			if (ev.equals(leader_event)) {
				VKT04StaticElectionTrigger(host);
				return;
			}
		}
		
		// Ev�nement p�riodique d'affichage d'�lections.
		if (event instanceof String) {
			String ev = (String) event;

			if (ev.equals(leader_event_print)) {
				// Traitement si necessaire
				return;
			}
		}
		
		throw new RuntimeException("Receive unknown Event");
	}

}

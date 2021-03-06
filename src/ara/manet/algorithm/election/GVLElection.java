package ara.manet.algorithm.election;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import ara.manet.Monitorable;
import ara.manet.communication.EmitterProtocolImpl;
import ara.manet.communication.WrapperEmitter;
import ara.manet.detection.NeighborProtocolImpl;
import ara.manet.detection.NeighborhoodListener;
import ara.util.EditMessage;
import ara.util.Knowledge;
import ara.util.KnowledgeMessage;
import ara.util.Peer;
import ara.util.View;
import peersim.config.Configuration;
import peersim.core.Node;

public class GVLElection implements Cloneable, Monitorable, ElectionProtocol, NeighborhoodListener {

	private static final long ALL = -2;

	private static final String PAR_PERIODE = "periode";

	private final int my_pid;
	private int value;
	private final int periode;
	private Knowledge knowledge;
	private long leader;
	private long id;

	// perseem constructor
	public GVLElection(String prefix) {

		String tmp[] = prefix.split("\\.");
		my_pid = Configuration.lookupPid(tmp[tmp.length - 1]);
		this.periode = Configuration.getInt(prefix + "." + PAR_PERIODE);
	}

	public Object clone() {
		GVLElection p = null;
		try {
			p = (GVLElection) super.clone();
			p.knowledge = new Knowledge();
		} catch (CloneNotSupportedException e) {
		}
		return p;
	}
	
	private WrapperEmitter getEmitterProtocol(Node host) {
		
		int emitter_pid = Configuration.lookupPid("emit");
		EmitterProtocolImpl emp = (EmitterProtocolImpl) host.getProtocol((emitter_pid));
		WrapperEmitter wm = new WrapperEmitter((EmitterProtocolImpl) emp);
		
		return wm;
	}
	
	/**
	 * This function initialize a knowledge of current peer
	 * 
	 * @param node - current peer
	 */
	public void initialisation(Node node) {
		knowledge = new Knowledge();

		// ExtendedRandom my_random = new ExtendedRandom(10);
		// this.value = (int) (my_random.nextInt(1000) / (node.getID() + 1));
		this.value = (int) node.getID();

		long id = node.getID();
		leader = id;
		Peer p = new Peer(id, value);
		int clock = 0;

		View v = new View(/* p, clock */);
		v.updateViewAdd((Peer) p.clone());
		v.setClock(clock);

		this.knowledge.setView(0, v);
		this.knowledge.setPosition(node.getID());
		this.id = node.getID();

	}

	/**
	 * This function add a new neighbor to knowledge of peer, increment peeer's
	 * clock and broadcast the updated knowledge
	 * 
	 * @param host            - current peer who detects a new neighbor
	 * @param id_new_neighbor - id of a new neighbor
	 */
	@Override
	public void newNeighborDetected(Node host, long id_new_neighbor) {

		// create new peer
		// get neighbors value
		/*
		 * int neighbor_pid = Configuration.lookupPid("neighbor"); NeighborProtocolImpl
		 * np = (NeighborProtocolImpl) host.getProtocol(neighbor_pid);
		 */
		int neighbors_value = (int) id_new_neighbor; // np.getNeighborValue(id_new_neighbor);

		Peer j = new Peer(id_new_neighbor, neighbors_value);

		// add new neighbor in list of host and clock++
		// if (j.getId() != host.getID()) {
		// System.out.println(host.getID() + "add" + id_new_neighbor);
		this.knowledge.updateMyViewAdd(j);
		// if (host.getID() == 67) {
		// System.out.println(host.getID() + " Added " + id_new_neighbor +
		// knowledge.toString());
		// }

		// Broadcast knowledge
		KnowledgeMessage knowlmsg = new KnowledgeMessage(host.getID(), ALL, my_pid, (Knowledge) knowledge.clone()); // clone?
		//int emitter_pid = Configuration.lookupPid("emit");
		//EmitterProtocolImpl emp = (EmitterProtocolImpl) host.getProtocol((emitter_pid));
		WrapperEmitter wm = this.getEmitterProtocol(host);
		
		//emp.emit(host, knowlmsg);
		wm.processEvent(host, my_pid, knowlmsg);
		// }

	}

	/**
	 * This function delete a disconnected neighbor from knowledge of peer,
	 * increment peeer's clock and broadcast the updated knowledge
	 * 
	 * @param host            - current peer who detects a disconnection of neighbor
	 * @param id_new_neighbor - id of a lost neighbor
	 */
	@Override
	public void lostNeighborDetected(Node host, long id_lost_neighbor) {

		// get neighbors value
		/*
		 * int neighbor_pid = Configuration.lookupPid("neighbor"); NeighborProtocolImpl
		 * np = (NeighborProtocolImpl) host.getProtocol(neighbor_pid);
		 */
		int neighbors_value = (int) id_lost_neighbor; // np.getNeighborValue(id_lost_neighbor);

		// Create the edit message
		// Peer j this is disconnected node here
		Peer j = new Peer(id_lost_neighbor, neighbors_value);
		int clock = knowledge.getLastClock(0);

		EditMessage edmsg = new EditMessage(host.getID(), ALL, my_pid);
		edmsg.setAutor(host.getID());
		edmsg.setUpdates(null, j); // null = added, j == lost
		edmsg.setClock(clock, clock + 1);

		// Remove j from the knowledge of host and clock++
		// But its id stays in positions list
		this.knowledge.updateMyViewRemove(j);
		// if (host.getID() == 67) {
		//System.out.println(host.getID() + " Lost " + id_lost_neighbor + knowledge.toString());
		// }

		// Broadcast edit
		//int emitter_pid = Configuration.lookupPid("emit");
		//EmitterProtocolImpl emp = (EmitterProtocolImpl) host.getProtocol((emitter_pid));
		WrapperEmitter wm = this.getEmitterProtocol(host);
		
		//emp.emit(host, edmsg);
		wm.processEvent(host, my_pid, edmsg);
		// }

	}

	@Override
	public long getIDLeader() {

		// max value in all reachable peer from i
		int max_val = -1;
		long idLeader = 0;

		if (!knowledge.getKnowledge().elementAt(0).getNeighbors().isEmpty()) {

			Vector<Peer> connexe = new Vector<Peer>();
			Vector<Peer> visited = new Vector<Peer>();

			Vector<Peer> q = knowledge.getKnowledge().elementAt(0).getNeighbors();

			for (Peer p : q) {

				connexe.add(p);

				if (p.getValue() > max_val) {
					max_val = p.getValue();
					idLeader = p.getId();
				}
			}

			for (int i = 0; i < connexe.size(); i++) {

				Peer p = connexe.elementAt(i);
				visited.add(p);

				// profondeur

				if (knowledge.getPosition().contains(p.getId())) {

					int pos = knowledge.getPosition().indexOf(p.getId());

					if (knowledge.getKnowledge().size() > pos && knowledge.getKnowledge().elementAt(pos) != null) {

						Vector<Peer> tmp = knowledge.getKnowledge().elementAt(pos).getNeighbors();

						for (int j = 0; j < tmp.size(); j++) {

							Peer kn = tmp.elementAt(j);

							// {if (!visited.contains(kn)) {
							boolean visit = false;
							boolean added = false;

							for (Peer per : visited) {

								if (per.getId() == kn.getId()) {
									visit = true;
									break;
								}
							}

							int k = 0;
							for (Peer per : connexe) {

								if (k > i && per.getId() == kn.getId()) {
									added = true;
									break;
								}
								k++;
							}

							if (!visit && !added) {
								// System.out.println(my_id + " elt " + kn.getId() + " " + i + " " + j + " " +
								// tmp.size() + " " + size);
								connexe.add(kn);
							}

						}
					}
				} // end profondeur

				if (p.getValue() > max_val) {
					idLeader = p.getId();
					max_val = p.getValue();
				}
				connexe.setElementAt(null, i);
				/*
				 * size = 0;
				 * 
				 * for (int k = 0; k < connexe.size(); k++) { if (connexe.elementAt(k) != null)
				 * size++; }
				 */
				// System.out.println(connexe);
				// System.out.println(visited);

			}
		}

		// System.out.println(knowledge.toString() + " LEDER " + idLeader);
		return idLeader;

	}

	@Override
	public int getValue() {

		return this.value;
	}

	// updates hosts knowledge using the information from new peer j
	public EditMessage updateKnowledge(Node host, long j, Knowledge j_knowledge) {

		// create edit msg
		EditMessage edmsg = new EditMessage(host.getID(), ALL, my_pid);

		// examine every peers view from knowledge of j
		for (Long peerId : j_knowledge.getPosition()) {

			// get view of peerId from j_knowledge
			View j_view = j_knowledge.getView(j_knowledge.getPosition().indexOf(peerId));

			if (j_view != null) {// if it exists (?)

				// test if host knows already the peer (in position list)
				if (knowledge.getPosition().contains(peerId)) { // contains (?)

					// get peers position
					int i_position = knowledge.getPosition().indexOf(peerId);

					// test if host has some info (a view) about peer
					if (knowledge.getView(i_position) != null) {

						// get view of peerId from hosts knowledge
						View i_view = knowledge.getView(knowledge.getPosition().indexOf(peerId));

						// test who has the most recent info
						// if j has the recent info create edit msg
						if (j_view.getClock() > i_view.getClock()) {

							// Create Edit Message
							edmsg.setAutor(peerId);
							Vector<Peer> added = new Vector();
							Vector<Peer> removed = new Vector();

							// Connected peers to add
							for (Peer p : j_view.getNeighbors()) {

								boolean found = false;
								for (Peer p1 : i_view.getNeighbors()) {

									if (p1.getId() == p.getId()) {
										found = true;
										break;
									}
								}

								if (!found) { // contains (?)
									added.add(p);

								}
								// System.out.println(host.getID() + " A : " + added);
							}

							// Lost peers to remove
							for (Peer p : i_view.getNeighbors()) {

								boolean found = false;
								for (Peer p1 : j_view.getNeighbors()) {

									if (p1.getId() == p.getId()) {
										found = true;
										break;
									}
								}
								if (!found) { // contains (?)
									removed.add(p);

								}
								// System.out.println(host.getID() + " R : " + removed);
							}
							edmsg.setUpdates(added, removed);
							edmsg.setClock(i_view.getClock(), j_view.getClock());

							// update the information in hosts knowledge about the peer
							// System.out.println("KM, known, set, host=" + host.getID() + " : " +
							// knowledge.toString());
							knowledge.setView(i_position, j_view);
						}
					}
				} else { // absolutely new neighbor

					// Create Edit message
					edmsg.setAutor(peerId);
					edmsg.setUpdates(j_view.getNeighbors(), null);
					edmsg.setClock(0, j_view.getClock());

					knowledge.setPosition(peerId);
					int i_position = knowledge.getPosition().indexOf(peerId);
					// System.out.println("KM, new, set, host=" + host.getID() + " : " +
					// knowledge.toString());
					knowledge.setView(i_position, j_view);
				}
			}
		}

		return edmsg;

	}

	private void recvKnowlMsg(Node host, KnowledgeMessage msg) {
		// if (host.getID() == 67)
		// System.out.println("K" + knowledge.toString());

		// Create edit message
		EditMessage edmsg = updateKnowledge(host, msg.getIdSrc(), msg.getKnowledge());

		// Broadcast edit
		if (!edmsg.empty()) {
			// System.out.println ("SEM " + edmsg.toString());

			//int emitter_pid = Configuration.lookupPid("emit");
			//EmitterProtocolImpl emp = (EmitterProtocolImpl) host.getProtocol((emitter_pid));
			WrapperEmitter wm = this.getEmitterProtocol(host);
			
			//emp.emit(host, edmsg);
			wm.processEvent(host, my_pid, edmsg);
		}
	}

	private void recvEditMsg(Node host, EditMessage msg) {
		// if ((msg.getIdSrc() == 30 && host.getID() == 67) || (msg.getIdSrc() == 5 &&
		 //host.getID() == 67)) {
		//System.out.println("EM, host=" + host.getID() + " from " + msg.getIdSrc() + "\n " + msg.toString());
		 //}

		int p = 0;
		boolean kno_updated = false;

		// check all changes from edit msg
		for (Long source : msg.getAutors()) {

			boolean updated = false;

			// do we have smth to add ?
			if (!msg.addedIsEmpty(p)) {

				int i_position = -1;
				boolean contains = false;
				boolean found = false;

				// do we know the source ?
				for (long i : knowledge.getPosition()) {

					if (i == source) {
						found = true;
						break;
					}
				}

				// known source => find the position
				if (found) {
					contains = true;
					i_position = knowledge.getPosition().indexOf(source);
				}

				// unknown source or vide (without info)
				if (!contains || knowledge.getView(i_position) == null) {

					// old clock from msg = 0 ?
					if (msg.getOldClock(p) == 0) {

						knowledge.updateOneView(source, msg.getAdded(p), 0);
						updated = true;

						 if ((msg.getIdSrc() == 30 && host.getID() == 67)
						 || (msg.getIdSrc() == 5 && host.getID() == 67)) {

						//System.out.println("EM, update, new, host=" + host.getID() + " : " + knowledge.toString());
						 }
					}

				} else {// known source

					// same time ?
					if (msg.getOldClock(p) == knowledge.getLastClock(i_position)) {

						knowledge.updateOneView(source, msg.getAdded(p), msg.getOldClock(p));
						updated = true;

						 //if ((msg.getIdSrc() == 30 && host.getID() == 67)
						 //|| (msg.getIdSrc() == 5 && host.getID() == 67)) {
						//System.out.println(
							//	"EM, update, known, same t, host=" + host.getID() + " : " + knowledge.toString());
						 //}
					}
				}
			} // end added msg

			// do we have smth to remove ?
			if (!msg.removedIsEmpty(p)) {

				int i_position = -1;
				boolean contains = false;
				boolean found = false;

				// do we know the source ?
				for (long i : knowledge.getPosition()) {
					if (i == source) {
						found = true;
						break;
					}
				}

				// known source => find the position
				if (found) {
					contains = true;
					i_position = knowledge.getPosition().indexOf(source);
				}

				// known source and not vide (with info)
				if (contains && knowledge.getView(i_position) != null) {

					// same time ?
					if (msg.getOldClock(p) == knowledge.getLastClock(i_position)) {

						knowledge.updateOneViewRem(source, (Vector<Peer>) msg.getRemoved(p).clone());
						updated = true;

						// if ((msg.getIdSrc() == 30 && host.getID() == 67)
						// || (msg.getIdSrc() == 5 && host.getID() == 67)) {
						//System.out.println("EM, remove, same t, host=" + host.getID() + " : " + knowledge.toString());
						// }
					}
				}
			} // end rm msg

			// test if knowledge of source is known
			if (knowledge.getPosition().contains(source)) {
				int i_position = knowledge.getPosition().indexOf(source);

				// test if knowledge of source is not empty
				if (knowledge.getView(i_position).getNeighbors().size() != 0) {

					// update clock of source
					if (updated) {
						knowledge.updateOneClock(source, msg.getNewClock(p));

						kno_updated = true;
					}
				}
			}
			p++; // next source
		} // all changes checked

		// test if knowledge was updated
		if (kno_updated) {

			// broadcast msg
			//int emitter_pid = Configuration.lookupPid("emit");
			//EmitterProtocolImpl emp = (EmitterProtocolImpl) host.getProtocol((emitter_pid));
			WrapperEmitter wm = this.getEmitterProtocol(host);
			//emp.emit(host, msg);
			wm.processEvent(host, my_pid, msg);
		}
	}

	@Override
	public void processEvent(Node host, int pid, Object event) {

		if (pid != my_pid) {
			throw new RuntimeException("Receive Event for wrong protocol");
		}

		if (event instanceof KnowledgeMessage) {
			recvKnowlMsg(host, (KnowledgeMessage) event);
			return;
		}

		if (event instanceof EditMessage) {
			recvEditMsg(host, (EditMessage) event);
			return;
		}
	}

	@Override
	public int nbState() {
		return 2;
	}

	@Override
	public int getState(Node host) {
		if (leader == host.getID())
			return 1;
		else
			return 0;
	}

	@Override
	public List<String> infos(Node host) {
		List<String> res = new ArrayList<String>();
		leader = getIDLeader();
		res.add("Node" + host.getID() + " Boss[" + leader + "]");
		// res.add(knowledge.toString());
		return res;
	}

}

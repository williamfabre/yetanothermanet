package ara.util;

import java.util.Vector;

public class KnowledgeMessage extends Message {
	
	private Knowledge knowledge;
	
	public KnowledgeMessage(long idsrc, long iddest, int pid, Knowledge k) {
		super(idsrc, iddest, pid);
		this.knowledge = (Knowledge)k.clone();
	}
	
	public Knowledge getKnowledge() {
		return knowledge;
	}
}

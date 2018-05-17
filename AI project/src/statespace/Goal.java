package statespace;

public class Goal extends Element {

	private Pos pos;
		
	public boolean inWorkingProcess = false;

	public int priority;
	
	public Goal(char label, Pos pos) {
		super(label);
		this.pos = pos;
	}

	public Pos getPos() {
		return pos;
	}
	
	@Override
	public String toString() {
		return "" + label;
	}
}
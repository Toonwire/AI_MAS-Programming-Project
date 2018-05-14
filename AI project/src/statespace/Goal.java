package statespace;

public class Goal extends Element {

	private Pos pos;
		
	public boolean inWorkingProcess = false;

	public Alley alley;

	public int priority;
	
	public Goal(char label, Pos pos) {
		super(label);
		this.pos = pos;
	}

	public Pos getPos() {
		return pos;
	}
	
//	public int getDistanceToGoal(Point p) {
//		return Math.abs(p.x - pos.row) + Math.abs(p.y - pos.col);
//	}
}